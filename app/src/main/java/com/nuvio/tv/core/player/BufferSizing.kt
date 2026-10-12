package com.nuvio.tv.core.player

import android.app.ActivityManager
import android.content.Context

/**
 * Automatic playback buffer size: one size per box class, lowered at each player build to what the box has free.
 */
object BufferSizing {
    private const val MB = 1024L * 1024L
    private const val GB = 1024L * MB

    // Automatic buffer per box, by the total memory Android reports. The 1 GB and 1.5 GB sizes are scaled from 2 GB.
    const val BUFFER_1GB_BOX_MB = 100
    const val BUFFER_1_5GB_BOX_MB = 125
    const val BUFFER_2GB_BOX_MB = 150
    const val BUFFER_3GB_BOX_MB = 500
    const val BUFFER_4GB_BOX_MB = 1000
    const val BUFFER_6GB_BOX_MB = 1600
    const val BUFFER_8GB_BOX_MB = 2000

    /** Same edge as the 2 GB size above: boxes sold as 2 GB or less. */
    val SMALL_BOX_RAM_BYTES: Long = (2.3 * GB).toLong()

    const val SMALL_BOX_CONNECTIONS = 2
    const val NATIVE_MEMORY_CONNECTIONS = 4

    // Kept free above Android's low-memory line, so the box does not start killing apps and swapping.
    const val SYSTEM_MARGIN_MB = 100
    // Still to come once the player is built: video surface and decoder (about 140 MB of graphics for 4K video
    // output on 2 GB boxes), the native allocator's 32 MB arena, and the image cache filling up again.
    const val VIDEO_OUTPUT_RESERVE_MB = 140
    const val NATIVE_ARENA_RESERVE_MB = 32
    const val IMAGE_CACHE_RESERVE_MB = 40
    /** About 5 s of a 4K remux at 80 Mbit/s. */
    const val MIN_SIZED_BUFFER_MB = 50

    /** The bitrate the back buffer is sized for, a 4K remux at about 80 Mbit/s. */
    private const val BACK_BUFFER_BYTES_PER_SECOND = 10L * MB
    /** Buffer ahead that the back buffer always leaves room for. */
    const val MIN_AHEAD_MS = 5_000L

    fun autoBufferMbForRam(totalMem: Long): Int = when {
        totalMem <= 0L -> BUFFER_2GB_BOX_MB
        totalMem < 1.15 * GB -> BUFFER_1GB_BOX_MB
        totalMem < 1.45 * GB -> BUFFER_1_5GB_BOX_MB
        totalMem < 2.3 * GB -> BUFFER_2GB_BOX_MB
        totalMem < 3.2 * GB -> BUFFER_3GB_BOX_MB
        totalMem < 4.8 * GB -> BUFFER_4GB_BOX_MB
        totalMem < 6.8 * GB -> BUFFER_6GB_BOX_MB
        else -> BUFFER_8GB_BOX_MB
    }

    fun isSmallBox(totalMem: Long): Boolean = totalMem <= 0L || totalMem < SMALL_BOX_RAM_BYTES

    /** Parallel connections when the user has not picked a number. */
    fun defaultConnectionCount(totalMem: Long, nativeMemory: Boolean, fallback: Int): Int = when {
        !nativeMemory -> fallback
        isSmallBox(totalMem) -> SMALL_BOX_CONNECTIONS
        else -> NATIVE_MEMORY_CONNECTIONS
    }

    data class FreeMemory(val availBytes: Long, val thresholdBytes: Long, val lowMemory: Boolean) {
        val availMb: Long get() = availBytes / MB
        val thresholdMb: Long get() = thresholdBytes / MB
    }

    fun read(context: Context): FreeMemory? = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        if (info.availMem <= 0L) null else FreeMemory(info.availMem, info.threshold.coerceAtLeast(0L), info.lowMemory)
    }.getOrNull()

    /**
     * MB the buffer can take before the box drops below its low-memory line plus [SYSTEM_MARGIN_MB].
     * [outsideBudgetMb] is memory the player holds outside this budget (the parallel chunk window);
     * [heldByPlayerMb] is buffer memory already held that the new player takes over or frees.
     */
    fun spareMb(free: FreeMemory, outsideBudgetMb: Int, heldByPlayerMb: Int): Long =
        free.availMb - free.thresholdMb - SYSTEM_MARGIN_MB - VIDEO_OUTPUT_RESERVE_MB -
            NATIVE_ARENA_RESERVE_MB - IMAGE_CACHE_RESERVE_MB - outsideBudgetMb + heldByPlayerMb

    /** The box's size, lowered to what is free now, never below [MIN_SIZED_BUFFER_MB] (or the box's size if smaller). */
    fun sizedBudgetMb(boxMb: Int, free: FreeMemory?, outsideBudgetMb: Int, heldByPlayerMb: Int): Int {
        if (free == null) return boxMb
        val floor = minOf(MIN_SIZED_BUFFER_MB, boxMb)
        if (free.lowMemory) return floor
        return spareMb(free, outsideBudgetMb, heldByPlayerMb).coerceIn(floor.toLong(), boxMb.toLong()).toInt()
    }

    /**
     * Every byte of the back buffer counts against the target, and it moves with playback instead of shrinking.
     * At [BACK_BUFFER_BYTES_PER_SECOND] it takes at most a third of [targetBytes] and leaves [MIN_AHEAD_MS] ahead.
     */
    fun backBufferMsForTarget(backBufferMs: Int, targetBytes: Long): Int {
        if (backBufferMs <= 0) return 0
        val aheadBytes = MIN_AHEAD_MS * BACK_BUFFER_BYTES_PER_SECOND / 1000L
        val backBytes = minOf(targetBytes / 3, targetBytes - aheadBytes).coerceAtLeast(0L)
        return backBufferMs.toLong().coerceAtMost(backBytes * 1000L / BACK_BUFFER_BYTES_PER_SECOND).toInt()
    }

    fun describe(boxMb: Int, sizedMb: Int, free: FreeMemory?, outsideBudgetMb: Int, heldByPlayerMb: Int): String =
        if (free == null) "boxMb=$boxMb sizedMb=$sizedMb free=unknown"
        else "boxMb=$boxMb sizedMb=$sizedMb availMb=${free.availMb} thresholdMb=${free.thresholdMb} " +
            "lowMemory=${free.lowMemory} windowMb=$outsideBudgetMb heldMb=$heldByPlayerMb"
}
