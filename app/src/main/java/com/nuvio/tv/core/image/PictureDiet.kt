package com.nuvio.tv.core.image

import com.nuvio.tv.core.player.BufferSizing

/** About three Home screens of posters plus one full-screen backdrop, in full colour. */
const val SMALL_BOX_PICTURE_CACHE_BYTES = 40L * 1024 * 1024

private const val FALLBACK_MAX_BITMAP_SIDE = 1920
private const val MIN_MAX_BITMAP_SIDE = 1280

/** Only boxes under 2.3 GB give up decoded pictures; bigger boxes keep today's budget. */
fun pictureMemoryCacheBytes(heapBudgetBytes: Long, totalRamBytes: Long): Long =
    if (BufferSizing.isSmallBox(totalRamBytes)) minOf(heapBudgetBytes, SMALL_BOX_PICTURE_CACHE_BYTES)
    else homeArtworkMemoryCacheBytes(heapBudgetBytes, totalRamBytes)

/** Nothing is drawn larger than the screen, so its longer side bounds every decode whatever the source size. */
fun pictureMaxBitmapSide(screenWidthPx: Int, screenHeightPx: Int): Int {
    val side = maxOf(screenWidthPx, screenHeightPx)
    return if (side <= 0) FALLBACK_MAX_BITMAP_SIDE else side.coerceAtLeast(MIN_MAX_BITMAP_SIDE)
}
