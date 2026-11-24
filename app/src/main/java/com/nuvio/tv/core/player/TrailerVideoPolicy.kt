package com.nuvio.tv.core.player

import android.content.Context

/**
 * Trailers render through a TextureView on top of the home UI, so 4K trailers consume
 * significantly more decoder and GPU memory than 1080p playback. Trailers therefore default
 * to 1080p across all devices; users can still opt into 4K via the "4K Trailers" setting.
 */
object TrailerVideoPolicy {
    const val DEFAULT_TRAILER_HEIGHT = 1080
    const val MAX_TRAILER_HEIGHT_WITHOUT_4K = DEFAULT_TRAILER_HEIGHT

    /** Default for the "4K trailers" setting: always off so trailers target 1080p by default. */
    fun default4kTrailers(@Suppress("UNUSED_PARAMETER") context: Context? = null): Boolean = false

    /** Tallest trailer video to play; [Int.MAX_VALUE] means no cap. */
    fun maxTrailerVideoHeight(allow4k: Boolean): Int =
        if (allow4k) Int.MAX_VALUE else MAX_TRAILER_HEIGHT_WITHOUT_4K

    /** Widest 16:9 trailer video matching [maxHeight]; [Int.MAX_VALUE] means no cap. */
    fun maxTrailerVideoWidth(maxHeight: Int): Int =
        if (maxHeight == Int.MAX_VALUE) Int.MAX_VALUE else maxHeight * 16 / 9
}
