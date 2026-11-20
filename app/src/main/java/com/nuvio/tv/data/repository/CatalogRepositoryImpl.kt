package com.nuvio.tv.data.repository

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.network.safeApiCall
import com.nuvio.tv.core.poster.CustomPosterScreen
import com.nuvio.tv.core.poster.patternForScreen
import com.nuvio.tv.core.poster.withCustomPosterUrls
import com.nuvio.tv.data.mapper.toDomainOrNull
import com.nuvio.tv.data.mediaserver.ServerCatalog
import com.nuvio.tv.data.mediaserver.messageRes
import com.nuvio.tv.data.mediaserver.serverFailure
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.remote.api.AddonApi
import com.nuvio.tv.data.remote.dto.CatalogResponseDto
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.repository.CatalogRepository
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLEncoder
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AddonApi,
    private val layoutPreferenceDataStore: LayoutPreferenceDataStore,
    private val serverCatalog: ServerCatalog
) : CatalogRepository {
    companion object {
        private const val TAG = "CatalogRepository"

        // In-memory freshness cache: short TTL, drop-stale, skip-network-on-hit.
        private const val CATALOG_CACHE_TTL_MS = 90_000L
        private const val CATALOG_CACHE_MAX_ENTRIES = 64
        private const val SEARCH_CACHE_MAX_ENTRIES = 48

        private const val DISK_CACHE_DIR_NAME = "catalog_rows"
        private const val LEGACY_DISK_CACHE_FILE_NAME = "catalog_rows_v1.json"

        private data class CacheEntry(val row: CatalogRow, val storedAtMs: Long)

        private val catalogCache: MutableMap<String, CacheEntry> = lruCache(CATALOG_CACHE_MAX_ENTRIES)
        private val searchCache: MutableMap<String, CacheEntry> = lruCache(SEARCH_CACHE_MAX_ENTRIES)

        private fun lruCache(maxEntries: Int): MutableMap<String, CacheEntry> =
            Collections.synchronizedMap(
                object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
                    override fun removeEldestEntry(eldest: Map.Entry<String, CacheEntry>): Boolean =
                        size > maxEntries
                }
            )

        private fun cacheFor(extraArgs: Map<String, String>): MutableMap<String, CacheEntry> =
            if (extraArgs.containsKey("search")) searchCache else catalogCache
    }

    private val moshi: Moshi = Moshi.Builder().build()
    private val catalogResponseAdapter = moshi.adapter(CatalogResponseDto::class.java)

    @Volatile private var cachedPosterPattern: String? = null
    @Volatile private var cachedEnabledScreens: Set<CustomPosterScreen>? = null

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val diskStore: CatalogDiskStore? by lazy {
        runCatching {
            val cacheDir = context.cacheDir
            CatalogDiskStore(
                dir = File(cacheDir, DISK_CACHE_DIR_NAME),
                legacyFiles = listOf(File(cacheDir, LEGACY_DISK_CACHE_FILE_NAME)),
                scope = ioScope
            )
        }.getOrNull()
    }

    init {
        ioScope.launch { runCatching { diskStore?.warm() } }
        ioScope.launch {
            try {
                layoutPreferenceDataStore.customPosterUrlPattern.collect { pattern ->
                    cachedPosterPattern = pattern
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
        ioScope.launch {
            try {
                layoutPreferenceDataStore.customPosterEnabledScreens.collect { screens ->
                    cachedEnabledScreens = screens
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }

    override fun getCatalog(
        addonBaseUrl: String,
        addonId: String,
        addonName: String,
        catalogId: String,
        catalogName: String,
        type: String,
        skip: Int,
        skipStep: Int,
        extraArgs: Map<String, String>,
        supportsSkip: Boolean,
        posterScreen: CustomPosterScreen
    ): Flow<NetworkResult<CatalogRow>> = flow {
        emit(NetworkResult.Loading)

        val posterPattern = cachedPosterPattern
            ?: layoutPreferenceDataStore.customPosterUrlPattern.first().also { cachedPosterPattern = it }
        val enabledScreens = cachedEnabledScreens
            ?: layoutPreferenceDataStore.customPosterEnabledScreens.first().also { cachedEnabledScreens = it }
        val effectivePosterPattern = patternForScreen(posterPattern, posterScreen, enabledScreens)

        if (ServerCatalog.isServerAddon(addonBaseUrl)) {
            val row = try {
                serverCatalog.catalog(addonBaseUrl, addonId, addonName, catalogId, catalogName, type, skip, extraArgs)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                emit(NetworkResult.Error(context.getString(error.serverFailure().messageRes())))
                return@flow
            }
            emit(NetworkResult.Success(row))
            return@flow
        }

        val url = buildCatalogUrl(addonBaseUrl, type, catalogId, skip, extraArgs)
        fun CatalogRow.withPosterPattern(): CatalogRow =
            if (effectivePosterPattern.isBlank()) this else copy(items = items.withCustomPosterUrls(effectivePosterPattern))

        // 1) In-memory freshness cache hit within TTL: serve and skip network
        val memoryCache = cacheFor(extraArgs)
        val cached = memoryCache[url]
        if (cached != null) {
            if (System.currentTimeMillis() - cached.storedAtMs <= CATALOG_CACHE_TTL_MS) {
                Log.d(TAG, "Catalog cache hit type=$type catalogId=$catalogId skip=$skip url=$url")
                emit(NetworkResult.Success(cached.row.withPosterPattern()))
                return@flow
            } else {
                memoryCache.remove(url)
            }
        }

        // 2) Disk first-paint cache (first page of a row only). Serve stale immediately, then revalidate over network.
        val diskEligible = skip == 0 && extraArgs.isEmpty()
        var servedDiskRow: CatalogRow? = null
        if (diskEligible) {
            val diskRow = runCatching {
                readDiskRow(
                    url = url,
                    addonBaseUrl = addonBaseUrl,
                    addonId = addonId,
                    addonName = addonName,
                    catalogId = catalogId,
                    catalogName = catalogName,
                    type = type,
                    skip = skip,
                    skipStep = skipStep,
                    supportsSkip = supportsSkip
                )
            }.getOrNull()?.withPosterPattern()
            if (diskRow != null) {
                Log.d(TAG, "Catalog disk hit (serving stale, revalidating) type=$type catalogId=$catalogId url=$url")
                servedDiskRow = diskRow
                emit(NetworkResult.Success(diskRow))
            }
        }

        Log.d(
            TAG,
            "Fetching catalog addonId=$addonId addonName=$addonName type=$type catalogId=$catalogId skip=$skip skipStep=$skipStep supportsSkip=$supportsSkip url=$url"
        )

        when (val result = safeApiCall(context) { api.getCatalog(url) }) {
            is NetworkResult.Success -> {
                val catalogRow = buildCatalogRow(
                    data = result.data,
                    addonBaseUrl = addonBaseUrl,
                    addonId = addonId,
                    addonName = addonName,
                    catalogId = catalogId,
                    catalogName = catalogName,
                    type = type,
                    skip = skip,
                    skipStep = skipStep,
                    supportsSkip = supportsSkip,
                    extraArgs = extraArgs
                )
                Log.d(
                    TAG,
                    "Catalog fetch success addonId=$addonId type=$type catalogId=$catalogId items=${catalogRow.items.size}"
                )

                memoryCache[url] = CacheEntry(catalogRow, System.currentTimeMillis())
                if (diskEligible) {
                    val data = result.data
                    runCatching {
                        diskStore?.put(url, data.metas.size) { sink -> catalogResponseAdapter.toJson(sink, data) }
                    }
                }

                val shownRow = catalogRow.withPosterPattern()
                if (servedDiskRow == null || servedDiskRow != shownRow) {
                    emit(NetworkResult.Success(shownRow))
                }
            }
            is NetworkResult.Error -> {
                Log.w(
                    TAG,
                    "Catalog fetch failed addonId=$addonId type=$type catalogId=$catalogId code=${result.code} message=${result.message} url=$url"
                )
                // If we already served from disk, do not emit Error to disrupt user's view
                if (servedDiskRow == null) {
                    emit(result)
                }
            }
            NetworkResult.Loading -> { /* Already emitted */ }
        }
    }.flowOn(Dispatchers.Default)

    private fun buildCatalogRow(
        data: CatalogResponseDto,
        addonBaseUrl: String,
        addonId: String,
        addonName: String,
        catalogId: String,
        catalogName: String,
        type: String,
        skip: Int,
        skipStep: Int,
        supportsSkip: Boolean,
        extraArgs: Map<String, String>
    ): CatalogRow {
        val rawItemCount = data.metas.size
        val merged = LinkedHashMap<String, MetaPreview>(rawItemCount)
        data.metas.forEach { dto ->
            val item = dto?.toDomainOrNull(type, addonBaseUrl) ?: return@forEach
            val prior = merged[item.id]
            merged[item.id] = if (prior == null) {
                item
            } else {
                prior.copy(
                    poster = prior.poster ?: item.poster,
                    background = prior.background ?: item.background,
                    logo = prior.logo ?: item.logo,
                    description = prior.description ?: item.description,
                    releaseInfo = prior.releaseInfo ?: item.releaseInfo,
                    imdbRating = prior.imdbRating ?: item.imdbRating,
                    landscapePoster = prior.landscapePoster ?: item.landscapePoster,
                    behaviorHints = prior.behaviorHints ?: item.behaviorHints
                )
            }
        }
        val items = merged.values.toList()
        return CatalogRow(
            addonId = addonId,
            addonName = addonName,
            addonBaseUrl = addonBaseUrl,
            catalogId = catalogId,
            catalogName = catalogName,
            type = ContentType.fromString(type),
            rawType = type,
            items = items,
            isLoading = false,
            hasMore = supportsSkip && rawItemCount > 0,
            currentPage = if (skipStep > 0) skip / skipStep else 0,
            supportsSkip = supportsSkip,
            skipStep = skipStep,
            nextSkip = if (supportsSkip && rawItemCount > 0) skip + rawItemCount else skip,
            extraArgs = extraArgs
        )
    }

    private suspend fun readDiskRow(
        url: String,
        addonBaseUrl: String,
        addonId: String,
        addonName: String,
        catalogId: String,
        catalogName: String,
        type: String,
        skip: Int,
        skipStep: Int,
        supportsSkip: Boolean
    ): CatalogRow? {
        val dto = diskStore?.read(url) { source -> catalogResponseAdapter.fromJson(source) } ?: return null
        return buildCatalogRow(
            data = dto,
            addonBaseUrl = addonBaseUrl,
            addonId = addonId,
            addonName = addonName,
            catalogId = catalogId,
            catalogName = catalogName,
            type = type,
            skip = skip,
            skipStep = skipStep,
            supportsSkip = supportsSkip,
            extraArgs = emptyMap()
        )
    }

    suspend fun clearCaches() {
        catalogCache.clear()
        searchCache.clear()
        runCatching { diskStore?.clear() }
    }

    private fun buildCatalogUrl(
        baseUrl: String,
        type: String,
        catalogId: String,
        skip: Int,
        extraArgs: Map<String, String>
    ): String {
        val trimmedBase = baseUrl.trimEnd('/')
        val queryStart = trimmedBase.indexOf('?')
        val basePath = if (queryStart >= 0) trimmedBase.substring(0, queryStart).trimEnd('/') else trimmedBase
        val baseQuery = if (queryStart >= 0) trimmedBase.substring(queryStart) else ""

        val catalogPath = if (extraArgs.isEmpty()) {
            if (skip > 0) {
                "$basePath/catalog/$type/$catalogId/skip=$skip.json"
            } else {
                "$basePath/catalog/$type/$catalogId.json"
            }
        } else {
            val allArgs = LinkedHashMap<String, String>()
            allArgs.putAll(extraArgs)

            if (!allArgs.containsKey("skip") && skip > 0) {
                allArgs["skip"] = skip.toString()
            }

            val encodedArgs = allArgs.entries.joinToString("&") { (key, value) ->
                "${encodeArg(key)}=${encodeArg(value)}"
            }

            "$basePath/catalog/$type/$catalogId/$encodedArgs.json"
        }

        return catalogPath + baseQuery
    }

    private fun encodeArg(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
