package com.nuvio.tv.core.image

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
