package com.nuvio.tv.ui.screens.player

import android.os.Debug
import android.os.SystemClock
import java.io.File

/**
 * Garbage collection and memory counters appended to the 10 s BUFFER log line.
 * GC figures are the change since the previous line; they are left out when that line is
 * too old (first line, or after a pause). Reads /proc, so call it off the main thread.
 */
internal object PlaybackRuntimeCounters {

    data class GcTotals(
        val count: Long,
        val timeMs: Long,
        val blockingCount: Long,
        val blockingTimeMs: Long,
        val allocatedBytes: Long
    )

    const val MAX_DELTA_GAP_MS = 20_000L
    private const val MB = 1024L * 1024L

    private var previous: GcTotals? = null
    private var previousAtMs: Long = 0L

    @Synchronized
    fun logFields(): String {
        val status = runCatching { File("/proc/self/status").readText() }.getOrNull()
        return fields(readGcTotals(), status, SystemClock.elapsedRealtime())
    }

    @Synchronized
    internal fun fields(totals: GcTotals?, procStatus: String?, nowMs: Long): String {
        val parts = ArrayList<String>(7)
        val before = previous
        if (totals != null && before != null && nowMs - previousAtMs in 0..MAX_DELTA_GAP_MS) {
            parts += "gc=${(totals.count - before.count).coerceAtLeast(0)}"
            parts += "gcMs=${(totals.timeMs - before.timeMs).coerceAtLeast(0)}"
            parts += "blockingGc=${(totals.blockingCount - before.blockingCount).coerceAtLeast(0)}"
            parts += "blockingGcMs=${(totals.blockingTimeMs - before.blockingTimeMs).coerceAtLeast(0)}"
            parts += "allocMb=${(totals.allocatedBytes - before.allocatedBytes).coerceAtLeast(0) / MB}"
        }
        previous = totals
        previousAtMs = nowMs
        procStatusKb(procStatus, "VmRSS")?.let { parts += "rssMb=${it / 1024}" }
        procStatusKb(procStatus, "VmSwap")?.let { parts += "swapMb=${it / 1024}" }
        return if (parts.isEmpty()) "" else parts.joinToString(prefix = ", ")
    }

    internal fun procStatusKb(procStatus: String?, key: String): Long? {
        if (procStatus == null) return null
        val prefix = "$key:"
        val line = procStatus.lineSequence().firstOrNull { it.startsWith(prefix) } ?: return null
        return line.substring(prefix.length).trim().substringBefore(' ').toLongOrNull()
    }

    private fun readGcTotals(): GcTotals? = runCatching {
        GcTotals(
            count = stat("art.gc.gc-count") ?: return null,
            timeMs = stat("art.gc.gc-time") ?: return null,
            blockingCount = stat("art.gc.blocking-gc-count") ?: 0L,
            blockingTimeMs = stat("art.gc.blocking-gc-time") ?: 0L,
            allocatedBytes = stat("art.gc.bytes-allocated") ?: 0L
        )
    }.getOrNull()

    private fun stat(name: String): Long? = Debug.getRuntimeStat(name)?.toLongOrNull()

    internal fun resetForTest() {
        previous = null
        previousAtMs = 0L
    }
}
