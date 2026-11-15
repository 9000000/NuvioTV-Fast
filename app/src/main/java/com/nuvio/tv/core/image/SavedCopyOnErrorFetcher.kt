package com.nuvio.tv.core.image

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.network.HttpException
import coil3.network.httpHeaders
import coil3.request.Options
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.FileSystem

/** Failures where the saved copy is still the right picture: no network, a timeout or a busy server. */
internal fun isTransientImageFailure(error: Throwable): Boolean = when (error) {
    is java.io.IOException -> true
    is HttpException -> error.response.code >= 500 || error.response.code == 408 || error.response.code == 429
    else -> false
}

/**
 * Shows a saved picture at once, also one due for an online check, and checks it afterwards ([showSavedFirst]).
 * Coil drops a saved copy whose online check fails; this shows it instead of an empty tile.
 */
class SavedCopyOnErrorFetcher internal constructor(
    private val delegate: Fetcher,
    private val showSavedFirst: (suspend () -> FetchResult?)? = null,
    private val openSavedCopy: () -> FetchResult?,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        showSavedFirst?.invoke()?.let { return it }
        return try {
            delegate.fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isTransientImageFailure(e)) throw e
            currentCoroutineContext().ensureActive()
            val saved = runCatching { openSavedCopy() }.getOrNull() ?: throw e
            savedCopiesServed.incrementAndGet()
            saved
        }
    }

    class Factory internal constructor(
        private val network: Fetcher.Factory<Uri>,
        private val checks: SavedImageChecks,
        private val isFresh: suspend (DiskCache.Snapshot, FileSystem, String, Options) -> Boolean,
    ) : Fetcher.Factory<Uri> {
        constructor(network: Fetcher.Factory<Uri>) : this(network, SavedImageChecks.shared, ::savedImageIsFresh)

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val fetcher = network.create(data, options, imageLoader) ?: return null
            if (!options.diskCachePolicy.readEnabled) return fetcher
            val url = data.toString()
            val key = options.diskCacheKey ?: url
            val openSaved: () -> FetchResult? = {
                imageLoader.diskCache?.let { cache ->
                    cache.openSnapshot(key)?.let { snapshot ->
                        SourceFetchResult(
                            source = ImageSource(snapshot.data, cache.fileSystem, key, snapshot),
                            mimeType = null,
                            dataSource = DataSource.DISK,
                        )
                    }
                }
            }
            val showSavedFirst: suspend () -> FetchResult? = savedFirst@{
                if (onlineCheckRequested(options.httpHeaders["Cache-Control"])) return@savedFirst null
                val cache = imageLoader.diskCache ?: return@savedFirst null
                val due = withContext(Dispatchers.IO) {
                    cache.openSnapshot(key)?.use { !isFresh(it, cache.fileSystem, url, options) }
                } ?: return@savedFirst null
                if (!due) return@savedFirst null
                val saved = openSaved() ?: return@savedFirst null
                checks.schedule(key) {
                    val before = savedStamp(cache, key)
                    val checked = network.create(data, options, imageLoader)?.fetch()
                    (checked as? SourceFetchResult)?.source?.close()
                    if (savedStamp(cache, key) != before) notifyArtworkChanged(url)
                }
                savedCopiesServedBeforeCheck.incrementAndGet()
                saved
            }
            return SavedCopyOnErrorFetcher(fetcher, showSavedFirst, openSaved)
        }
    }

    companion object {
        val savedCopiesServed = AtomicInteger()
        val savedCopiesServedBeforeCheck = AtomicInteger()
    }
}

/** A request that asks the server first (no-cache, max-age=0) never gets the saved copy ahead of the check. */
internal fun onlineCheckRequested(cacheControl: String?): Boolean {
    val value = cacheControl?.lowercase() ?: return false
    return "no-cache" in value || "max-age=0" in value
}

/** Size and time of the saved file: a 304 leaves both alone, a new picture changes them. */
private fun savedStamp(cache: DiskCache, key: String): Pair<Long?, Long?>? =
    cache.openSnapshot(key)?.use { snapshot ->
        cache.fileSystem.metadata(snapshot.data).let { it.size to it.lastModifiedAtMillis }
    }

/** Cards ask for the original TMDB size; the request that was checked may be a smaller one. */
private fun notifyArtworkChanged(url: String) {
    ImageInvalidationBus.notifyInvalidated(url)
    val original = url.replace(Regex("/t/p/w\\d+/"), "/t/p/original/")
    if (original != url) ImageInvalidationBus.notifyInvalidated(original)
}

/** Online checks of saved pictures run in the background, two at a time, at most once per picture every 10 minutes. */
internal class SavedImageChecks(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis,
    parallel: Int = 2,
) {
    private val slots = Semaphore(parallel)
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val lastCheck = ConcurrentHashMap<String, Long>()

    fun schedule(key: String, check: suspend () -> Unit) {
        val last = lastCheck[key]
        if (last != null && now() - last < COOLDOWN_MS) return
        if (!running.add(key)) return
        scope.launch {
            try {
                slots.withPermit { runCatching { check() } }
            } finally {
                lastCheck[key] = now()
                running.remove(key)
            }
        }
    }

    companion object {
        const val COOLDOWN_MS = 10L * 60 * 1000
        val shared = SavedImageChecks()
    }
}
