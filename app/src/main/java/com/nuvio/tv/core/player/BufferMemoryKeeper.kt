package com.nuvio.tv.core.player

import android.content.ComponentCallbacks2
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Gives idle buffer memory back to the box and lowers the buffer budget while the box is short of memory.
 * All work runs on its own thread, never on the playback thread.
 */
@UnstableApi
class BufferMemoryKeeper(
    val control: BufferBudgetControl,
    private val freeMemory: () -> BufferSizing.FreeMemory?,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val worker: Worker = SharedWorker,
) {
    interface Worker {
        fun run(task: () -> Unit)
        fun runLater(delayMs: Long, task: () -> Unit)
    }

    @Volatile var pressureLevel: Int = LEVEL_NONE
        private set
    @Volatile private var lastPressureAtMs = 0L
    @Volatile private var levelSetAtMs = 0L
    @Volatile private var lastSeekAtMs = 0L
    @Volatile private var seekTrimQueued = false
    @Volatile private var detached = false

    private val trimListener: (Int) -> Unit = ::onTrimMemory

    fun attach() {
        MemoryPressure.addListener(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, trimListener)
    }

    fun detach() {
        detached = true
        MemoryPressure.removeListener(trimListener)
    }

    fun onTrimMemory(level: Int) {
        if (detached) return
        val wanted = when {
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> LEVEL_NONE
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> LEVEL_CRITICAL
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> LEVEL_LOW
            else -> LEVEL_NONE
        }
        worker.run {
            if (wanted > LEVEL_NONE) raise(wanted, "trim:$level") else trimIdle("trim:$level")
        }
    }

    /** A seek drops the buffer it leaves; what the new position does not refill is given back. */
    fun onSeek() {
        if (detached) return
        lastSeekAtMs = clock()
        if (seekTrimQueued) return
        seekTrimQueued = true
        worker.runLater(SEEK_SETTLE_MS) { seekTrim() }
    }

    private fun seekTrim() {
        val sinceSeek = clock() - lastSeekAtMs
        if (sinceSeek < SEEK_SETTLE_MS) {
            worker.runLater(SEEK_SETTLE_MS - sinceSeek) { seekTrim() }
            return
        }
        seekTrimQueued = false
        trimIdle("seek")
    }

    /** The budget went down (for example DV7 conversion): the allocator's pool follows. */
    fun onBudgetLowered() {
        if (detached) return
        worker.run { applyTarget(); trimIdle("budget") }
    }

    /** About every 10 s while playing: Android 14 and later no longer sends RUNNING_LOW to visible apps. */
    fun onTick() {
        if (detached) return
        worker.run { check() }
    }

    internal fun check() {
        if (detached) return
        val free = freeMemory()
        val now = clock()
        if (free != null && free.lowMemory) {
            val level = if (pressureLevel == LEVEL_LOW && now - levelSetAtMs >= ESCALATE_AFTER_MS) LEVEL_CRITICAL
                else maxOf(pressureLevel, LEVEL_LOW)
            raise(level, "lowMemory availMb=${free.availMb} thresholdMb=${free.thresholdMb}")
            return
        }
        if (pressureLevel == LEVEL_NONE || now - lastPressureAtMs < RESTORE_QUIET_MS) {
            if (pressureLevel > LEVEL_NONE) trimIdle("pressure")
            return
        }
        val next = pressureLevel - 1
        val addedMb = (capBytes(next) - capBytes(pressureLevel)) / MB
        val roomMb = free?.let { it.availMb - it.thresholdMb - BufferSizing.SYSTEM_MARGIN_MB } ?: 0L
        if (free == null || roomMb < addedMb) {
            lastPressureAtMs = now
            return
        }
        pressureLevel = next
        lastPressureAtMs = now
        levelSetAtMs = now
        applyTarget()
        Log.i(TAG, "BUFFER_MEMORY: pressure eased level=$next capMb=${capOrBudgetMb()} availMb=${free.availMb}")
    }

    private fun raise(level: Int, reason: String) {
        if (detached) return
        lastPressureAtMs = clock()
        val changed = level > pressureLevel
        if (changed) {
            pressureLevel = level
            levelSetAtMs = lastPressureAtMs
            applyTarget()
        }
        val freed = trimIdle(null)
        if (changed || freed > 0) {
            Log.i(TAG, "BUFFER_MEMORY: pressure level=$pressureLevel reason=$reason capMb=${capOrBudgetMb()} " +
                "uncappedMb=${control.uncappedBudgetBytes / MB} freedMb=${freed / MB}")
        }
    }

    internal fun capBytes(level: Int): Long {
        val budget = control.uncappedBudgetBytes
        val share = when (level) {
            LEVEL_LOW -> LOW_SHARE_PERCENT
            LEVEL_CRITICAL -> CRITICAL_SHARE_PERCENT
            else -> return budget
        }
        val floor = minOf(budget, BufferSizing.MIN_SIZED_BUFFER_MB * MB)
        return (budget * share / 100).coerceAtLeast(floor)
    }

    private fun capOrBudgetMb(): Long = capBytes(pressureLevel) / MB

    private fun applyTarget() {
        control.setPressureCapBytes(if (pressureLevel == LEVEL_NONE) -1L else capBytes(pressureLevel))
        control.bufferAllocator.setTargetBufferSize(control.allocatorTargetBytes())
    }

    /** Frees idle allocations beyond [KEEP_IDLE_BYTES], a step at a time so the loader never waits long. */
    internal fun trimIdle(reason: String?): Long {
        if (detached) return 0L
        val allocator = control.bufferAllocator
        var freed = 0L
        for (step in 0 until MAX_TRIM_STEPS) {
            val dropped = synchronized(allocator) {
                val idle = allocator.availableBytes
                if (idle <= KEEP_IDLE_BYTES) return@synchronized 0
                val drop = minOf(idle - KEEP_IDLE_BYTES, TRIM_STEP_BYTES)
                val before = allocator.memoryFootprint
                allocator.setTargetBufferSize(allocator.totalBytesAllocated + idle - drop)
                allocator.trim()
                allocator.setTargetBufferSize(control.allocatorTargetBytes())
                before - allocator.memoryFootprint
            }
            if (dropped <= 0) break
            freed += dropped
        }
        if (reason != null && freed > 0) {
            Log.i(TAG, "BUFFER_MEMORY: gave back idle buffer reason=$reason freedMb=${freed / MB} " +
                "heldMb=${allocator.totalBytesAllocated / MB}")
        }
        return freed
    }

    private object SharedWorker : Worker {
        private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "buffer-memory").apply { isDaemon = true }
        }

        override fun run(task: () -> Unit) {
            executor.execute { runCatching(task) }
        }

        override fun runLater(delayMs: Long, task: () -> Unit) {
            executor.schedule({ runCatching(task) }, delayMs, TimeUnit.MILLISECONDS)
        }
    }

    companion object {
        private const val TAG = "BufferMemory"
        private const val MB = 1024L * 1024L
        const val LEVEL_NONE = 0
        const val LEVEL_LOW = 1
        const val LEVEL_CRITICAL = 2
        const val LOW_SHARE_PERCENT = 60L
        const val CRITICAL_SHARE_PERCENT = 35L
        const val SEEK_SETTLE_MS = 2_000L
        /** Pressure eases one level per minute without a new signal, if the box has room for it. */
        const val RESTORE_QUIET_MS = 60_000L
        /** Still short of memory this long after the first cut: cut further. */
        const val ESCALATE_AFTER_MS = 30_000L
        const val KEEP_IDLE_BYTES = 2 * 1024 * 1024
        /** 256 allocations of 64 KB per step. */
        const val TRIM_STEP_BYTES = 16 * 1024 * 1024
        private const val MAX_TRIM_STEPS = 256
    }
}
