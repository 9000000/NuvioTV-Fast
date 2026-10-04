package com.nuvio.tv.core.player

import android.content.Context
import com.nuvio.tv.ui.screens.player.NuvioExoPlayerPerformanceHelper

/**
 * Trailers render through a TextureView on top of the home UI, so a 4K trailer costs a few hundred
 * MB of decoder and GPU memory that full-screen playback (SurfaceView) never pays. Devices with
 * little physical RAM (Fire TV sticks and similar) get trailers capped at 1080p; the rest keep 4K.
 */
object TrailerVideoPolicy {
    const val LOW_RAM_MAX_TRAILER_HEIGHT = 1080

    // 2 GB devices report ~1.8-1.95 GB and 3 GB devices ~2.8 GB, so this splits them cleanly.
    private const val LOW_RAM_THRESHOLD_BYTES = 2_560L * 1024L * 1024L

    /** Tallest trailer video this device should play; [Int.MAX_VALUE] means no cap. */
    fun maxTrailerVideoHeight(context: Context): Int {
        val totalRam = NuvioExoPlayerPerformanceHelper.getDevicePhysicalRamBytes(context)
        return if (totalRam in 1 until LOW_RAM_THRESHOLD_BYTES) LOW_RAM_MAX_TRAILER_HEIGHT else Int.MAX_VALUE
    }
}
