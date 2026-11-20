package com.nuvio.tv.core.image

import android.app.ActivityManager
import android.content.Context
import com.nuvio.tv.core.player.BufferSizing

private val TMDB_ARTWORK_URL_PATTERN = Regex("""^https?://image\.tmdb\.org/t/p/(original|w(\d+))/(.+)$""")

/** Shared with the image mapper so disk lookups use the fetched TMDB URL. */
fun sizedHomeArtworkUrl(data: String, width: Int?, height: Int? = null): String {
    if (width == null || width !in 1..780) return data
    val match = TMDB_ARTWORK_URL_PATTERN.matchEntire(data) ?: return data
    val currentBucket = match.groupValues[1]
    val currentWidth = match.groupValues[2].toIntOrNull() ?: Int.MAX_VALUE
    val path = match.groupValues[3]
    val isPortrait = height == null || height > width

    val targetWidth = when {
        width <= 342 && isPortrait -> 342
        width <= 500 && isPortrait -> 500
        else -> 780
    }
    if (currentWidth <= targetWidth) return data
    if (!isPortrait && currentBucket != "original" && currentWidth <= 780) return data
    return "https://image.tmdb.org/t/p/w$targetWidth/$path"
}

const val HOME_POSTER_BITMAP_PARALLELISM = 4

private const val MIB = 1024L * 1024
private const val LARGE_MEMORY_BOX_TOTAL_BYTES = 3_072L * MIB
private const val LARGE_BOX_ARTWORK_CACHE_BYTES = 176L * MIB

/** Boxes above 3 GB hold several rows more of decoded posters; smaller boxes keep the heap-based budget. */
fun homeArtworkMemoryCacheBytes(heapBudgetBytes: Long, totalRamBytes: Long): Long =
    if (totalRamBytes > LARGE_MEMORY_BOX_TOTAL_BYTES) {
        maxOf(heapBudgetBytes, minOf(LARGE_BOX_ARTWORK_CACHE_BYTES, totalRamBytes / 16))
    } else heapBudgetBytes

/**
 * Hardware-tiered networking, decoding, catalog loading, and poster prefetch parameters
 * so higher-RAM TV boxes load aggressively while <= 2.3 GB boxes stay within FD/RAM limits.
 */
data class HomeImageLoadingProfile(
    val isLowRam: Boolean,
    val okHttpMaxRequests: Int,
    val okHttpMaxRequestsPerHost: Int,
    val connectionPoolMaxIdle: Int,
    val bitmapDecodeParallelism: Int,
    val catalogConcurrency: Int,
    val eagerCatalogRows: Int,
    val dripAheadRows: Int,
    val lazyPrefetchAheadModern: Int,
    val lazyPrefetchAheadClassic: Int,
    val verticalPrefetchRows: Int,
    val verticalPrefetchItems: Int,
    val horizontalPrefetchAhead: Int,
    val horizontalPrefetchBehind: Int
) {
    companion object {
        private val LOW_RAM = HomeImageLoadingProfile(
            isLowRam = true,
            okHttpMaxRequests = 32,
            okHttpMaxRequestsPerHost = 16,
            connectionPoolMaxIdle = 12,
            bitmapDecodeParallelism = 4,
            catalogConcurrency = 4,
            eagerCatalogRows = 4,
            dripAheadRows = 3,
            lazyPrefetchAheadModern = 3,
            lazyPrefetchAheadClassic = 2,
            verticalPrefetchRows = 2,
            verticalPrefetchItems = 6,
            horizontalPrefetchAhead = 8,
            horizontalPrefetchBehind = 2
        )

        private val STANDARD = HomeImageLoadingProfile(
            isLowRam = false,
            okHttpMaxRequests = 48,
            okHttpMaxRequestsPerHost = 32,
            connectionPoolMaxIdle = 20,
            bitmapDecodeParallelism = 6,
            catalogConcurrency = 6,
            eagerCatalogRows = 6,
            dripAheadRows = 6,
            lazyPrefetchAheadModern = 4,
            lazyPrefetchAheadClassic = 3,
            verticalPrefetchRows = 3,
            verticalPrefetchItems = 8,
            horizontalPrefetchAhead = 12,
            horizontalPrefetchBehind = 2
        )

        fun forDevice(totalRamBytes: Long): HomeImageLoadingProfile =
            if (BufferSizing.isSmallBox(totalRamBytes)) LOW_RAM else STANDARD

        fun forContext(context: Context): HomeImageLoadingProfile {
            val totalRamBytes = runCatching {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                if (activityManager == null) {
                    0L
                } else {
                    val memoryInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memoryInfo)
                    memoryInfo.totalMem
                }
            }.getOrDefault(0L)
            return forDevice(totalRamBytes)
        }
    }
}
