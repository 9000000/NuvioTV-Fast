package com.nuvio.tv

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Build
import android.os.StrictMode
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.nuvio.tv.core.image.CustomPosterFallbackInterceptor
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.gif.GifDecoder
import coil3.gif.AnimatedImageDecoder
import coil3.svg.SvgDecoder
import coil3.request.crossfade
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.maxBitmapSize
import coil3.bitmapFactoryMaxParallelism

import okio.Path.Companion.toOkioPath
import com.nuvio.tv.core.diagnostics.SentryInitializer
import com.nuvio.tv.core.image.StaleWhileRevalidateCacheStrategy
import com.nuvio.tv.core.runtime.PluginRuntimeHooks
import com.nuvio.tv.core.sync.androidtv.AndroidTvChannelSyncService
import com.nuvio.tv.core.network.IPv4FirstDns
import com.nuvio.tv.core.network.NoDelaySocketFactory
import com.nuvio.tv.core.network.imageNetworkFetcherFactory
import com.nuvio.tv.data.local.ImagePerformancePreferences
import com.nuvio.tv.data.local.SentrySettingsDataStore
import com.nuvio.tv.data.mediaserver.ServerUserStateProjection
import com.nuvio.tv.data.simkl.SimklAnimeIdPreferenceHolder
import com.nuvio.tv.ui.screens.settings.MemoryBudget
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class NuvioApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var androidTvChannelSyncService: AndroidTvChannelSyncService
    @Inject lateinit var sentrySettingsDataStore: SentrySettingsDataStore
    @Inject lateinit var imagePerformancePreferences: ImagePerformancePreferences
    @Inject lateinit var simklAnimeIdPreferenceHolder: SimklAnimeIdPreferenceHolder
    @Inject lateinit var serverUserStateProjection: ServerUserStateProjection

    companion object {
        /**
         * Shared cookie jar for CloudStream extension HTTP requests.
         * Accessible so the player's OkHttpClient can share cookies
         * obtained during scraping (e.g., session tokens needed for playback).
         */
        val extensionCookieJar: CookieJar = object : CookieJar {
            private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val hostCookies = store[url.host] ?: return emptyList()
                synchronized(hostCookies) {
                    return hostCookies.filter { cookie ->
                        cookie.expiresAt > System.currentTimeMillis()
                    }
                }
            }

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val hostCookies = store.getOrPut(url.host) { mutableListOf() }
                synchronized(hostCookies) {
                    cookies.forEach { newCookie ->
                        hostCookies.removeAll { it.name == newCookie.name }
                        hostCookies.add(newCookie)
                    }
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        // Before super.onCreate(): Hilt creates injected services there, and the budget is read once.
        MemoryBudget.init(this)
        com.nuvio.tv.core.network.ArcDns.init(this)
        super.onCreate()
        com.nuvio.tv.features.livetv.LiveTvStorage.init(this)
        SentryInitializer.start(this, sentrySettingsDataStore)
        PluginRuntimeHooks.onApplicationCreate(this)
        androidTvChannelSyncService.start()
        serverUserStateProjection.start()
        // Load locale synchronously so it's available before Activity.attachBaseContext.
        // SharedPreferences reads are fast (cached in memory after first access).
        val tag = getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            .getString("locale_tag", null)
        LocaleCache.localeTag = tag ?: ""
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            val memoryCache = SingletonImageLoader.get(this).memoryCache ?: return
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                memoryCache.clear()
            } else if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                memoryCache.trimToSize(memoryCache.size / 2)
            }
        } catch (_: Exception) { }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            SingletonImageLoader.get(this).memoryCache?.clear()
        } catch (_: Exception) { }
    }

    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun newImageLoader(context: android.content.Context): ImageLoader {
        val imageOkHttpClient by lazy {
            val imageDispatcher = okhttp3.Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 16
            }
            OkHttpClient.Builder()
                .eventListenerFactory(com.nuvio.tv.core.image.ArtworkDiagnostics.networkEvents)
                .dispatcher(imageDispatcher)
                .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
                .dns(IPv4FirstDns())
                .socketFactory(NoDelaySocketFactory)
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    try {
                        chain.proceed(chain.request())
                    } catch (e: java.net.SocketTimeoutException) {
                        chain.withConnectTimeout(3, TimeUnit.SECONDS)
                            .withReadTimeout(4, TimeUnit.SECONDS)
                            .proceed(chain.request())
                    }
                }
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
                .also {
                    com.nuvio.tv.core.network.ArcDns.onChange(it.connectionPool::evictAll)
                }
        }

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val diskDecodeSlots = com.nuvio.tv.core.image.homePosterDiskDecodeSlots(
            totalRamBytes = memoryInfo.totalMem,
            lowRamDevice = activityManager.isLowRamDevice,
            cores = Runtime.getRuntime().availableProcessors()
        )

        return ImageLoader.Builder(this)
            .eventListenerFactory(com.nuvio.tv.core.image.ArtworkDiagnostics.imageEvents)
            .components {
                add(CustomPosterFallbackInterceptor())
                add(com.nuvio.tv.core.image.HomePosterLoadingInterceptor(diskDecodeSlots))
                // Size-aware TMDB downscaler: rewrite multi-MB original/w780/w500 poster URLs to w342/w500 matching target
                add(coil3.map.Mapper<String, String> { data, options ->
                    val w = (options.size.width as? coil3.size.Dimension.Pixels)?.px
                    val h = (options.size.height as? coil3.size.Dimension.Pixels)?.px
                    com.nuvio.tv.core.image.sizedHomeArtworkUrl(data, w, h).takeIf { it != data }
                })
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
                add(
                    com.nuvio.tv.core.image.SavedCopyOnErrorFetcher.Factory(
                        imageNetworkFetcherFactory(
                            callFactory = { imageOkHttpClient },
                            cacheStrategy = { com.nuvio.tv.core.image.imageCacheStrategy },
                        )
                    )
                )
            }
            .memoryCache {
                val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
                val cachePercent = when {
                    totalRamMb <= 2048 -> 0.15
                    totalRamMb <= 3072 -> 0.20
                    else -> 0.25
                }
                val budgetHeapBytes = MemoryBudget.heapMb * 1024L * 1024L
                val heapBudget = if (budgetHeapBytes < Runtime.getRuntime().maxMemory()) {
                    (budgetHeapBytes * cachePercent).toLong()
                } else {
                    MemoryCache.Builder()
                        .maxSizePercent(context, cachePercent)
                        .build().maxSize
                }
                com.nuvio.tv.core.image.ArtworkMemoryCache(
                    com.nuvio.tv.core.image.pictureMemoryCacheBytes(heapBudget, memoryInfo.totalMem)
                )
            }
            .diskCache {
                val freeStorage = runCatching { cacheDir.usableSpace }.getOrDefault(0L)
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(com.nuvio.tv.core.image.imageDiskCacheBytes(memoryInfo.totalMem, freeStorage))
                    .build()
            }
            .crossfade(false)
            .precision(coil3.size.Precision.INEXACT)
            .allowHardware(false)
            .allowRgb565(imagePerformancePreferences.rgb565Enabled)
            .maxBitmapSize(
                com.nuvio.tv.core.image.pictureMaxBitmapSide(
                    resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels
                ).let { coil3.size.Size(it, it) }
            )
            .bitmapFactoryMaxParallelism(com.nuvio.tv.core.image.HOME_POSTER_BITMAP_PARALLELISM)
            .build()
    }
}
