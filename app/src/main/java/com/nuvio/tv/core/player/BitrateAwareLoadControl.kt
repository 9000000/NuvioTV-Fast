package com.nuvio.tv.core.player

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.nuvio.tv.core.player.thumbnail.PlaybackBufferReserve

/**
 * DefaultLoadControl with a byte target tied to the device memory budget. It is
 * built with the full budget and tightened at runtime, through the override
 * setter below, only for streams that really are DV7.
 */
@UnstableApi
class BitrateAwareLoadControl(
    minBufferMs: Int,
    maxBufferMs: Int,
    bufferForPlaybackMs: Int,
    bufferForPlaybackAfterRebufferMs: Int,
    prioritizeTimeOverSizeThresholds: Boolean,
    backBufferDurationMs: Int,
    retainBackBufferFromKeyframe: Boolean,
    /** Memory ceiling in bytes. */
    budgetBytes: Long,
    allocator: DefaultAllocator = DefaultAllocator(/* trimOnReset= */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE, 64)
) : DefaultLoadControl(
    allocator,
    minBufferMs,
    maxBufferMs,
    bufferForPlaybackMs,
    bufferForPlaybackAfterRebufferMs,
    /* targetBufferBytes= */ C.LENGTH_UNSET,
    prioritizeTimeOverSizeThresholds,
    backBufferDurationMs,
    retainBackBufferFromKeyframe
), PlaybackBufferReserve, BufferBudgetControl {

    override val bufferAllocator: DefaultAllocator = allocator

    // Effective back buffer (µs) when >= 0, else the constructed value.
    @Volatile
    private var backBufferOverrideUs: Long = -1L

    /**
     * Set the back buffer at runtime; negative restores the constructed value.
     *
     * Media3's `ExoPlayerImplInternal` caches `backBufferDurationUs` in a field during its
     * constructor and does not re-poll [getBackBufferDurationUs] mid-playback. When [player]
     * is provided, we also update `ExoPlayerImplInternal.backBufferDurationUs` so the new
     * back-buffer duration takes effect immediately for the active session.
     */
    fun setBackBufferDurationOverrideMs(ms: Int, player: ExoPlayer? = null) {
        backBufferOverrideUs = if (ms < 0) -1L else ms.toLong() * 1000L
        if (player != null) {
            val effectiveUs = getBackBufferDurationUs(PlayerId.UNSET)
            applyBackBufferToInternalPlayer(player, effectiveUs)
        }
    }

    // Effective byte budget when >= 0, else the constructed budget.
    @Volatile
    private var budgetBytesOverride: Long = -1L

    /**
     * Set the byte budget at runtime; negative restores the constructed budget.
     *
     * Also updates any already-created `PlayerLoadingState.targetBufferBytes` entries in
     * [DefaultLoadControl] and refreshes the allocator target size so overrides applied at
     * first frame (after `onTracksSelected`) take effect immediately.
     */
    fun setBudgetBytesOverride(bytes: Long, player: ExoPlayer? = null) {
        budgetBytesOverride = if (bytes < 0L) -1L else bytes.coerceAtMost(MAX_BUDGET_BYTES)
        val looper = runCatching { player?.playbackLooper }.getOrNull()
        if (looper != null && looper.thread.isAlive && Thread.currentThread() !== looper.thread) {
            Handler(looper).post { applyBudgetToActiveLoadingStates() }
        } else {
            applyBudgetToActiveLoadingStates()
        }
    }

    override fun getBackBufferDurationUs(playerId: PlayerId): Long {
        val override = backBufferOverrideUs
        return if (override >= 0L) override else super.getBackBufferDurationUs(playerId)
    }

    private val budgetBytes: Long = budgetBytes.coerceAtMost(MAX_BUDGET_BYTES)

    override fun calculateTargetBufferBytes(
        trackSelectionArray: Array<out ExoTrackSelection?>
    ): Int {
        // Target = the memory budget. Time (Max Buffer Duration) is the real limit; this is
        // just the memory cap. Sizing from advertised bitrate starved variable-bitrate peaks,
        // so let high-bitrate fill up to the budget and low-bitrate stop at the time limit.
        return currentTargetBufferBytes()
    }

    private fun currentTargetBufferBytes(): Int {
        return effectiveBudgetBytes().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    @Volatile
    private var pressureCapBytes: Long = -1L

    override val uncappedBudgetBytes: Long
        get() = if (budgetBytesOverride >= 0L) budgetBytesOverride else budgetBytes

    override fun setPressureCapBytes(bytes: Long) {
        pressureCapBytes = if (bytes < 0L) -1L else bytes
    }

    override fun allocatorTargetBytes(): Int =
        (effectiveBudgetBytes() - reserveCore.reservedBytes).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun effectiveBudgetBytes(): Long {
        val cap = pressureCapBytes
        val budget = uncappedBudgetBytes
        return if (cap >= 0L) minOf(budget, cap) else budget
    }

    // Seek thumbnail read-ahead reserve
    private val reserveCore = ReadAheadReserve(bufferAllocator, ::effectiveBudgetBytes,
        bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)

    override val readAheadBudgetBytes: Long get() = reserveCore.readAheadBudgetBytes
    override val bufferedBytes: Long get() = reserveCore.bufferedBytes
    override val footprintBytes: Long get() = reserveCore.footprintBytes
    override val reservedBytes: Long get() = reserveCore.reservedBytes
    override val cancelledByRebuffer: Boolean get() = reserveCore.cancelledByRebuffer
    override val lastStallRealtimeMs: Long get() = reserveCore.lastStallRealtimeMs
    override fun reserve(bytes: Long) = reserveCore.reserve(bytes)
    override fun giveBack(): Long = reserveCore.giveBack()
    override fun release() = reserveCore.release()
    override fun yieldLoading(on: Boolean) = reserveCore.yieldLoading(on)

    // Time before size lets the buffer pass the budget until minBufferMs is met. Once enough is
    // buffered to start playback, it stops a quarter over the budget instead.
    private val minAheadUs: Long = maxOf(bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs) * 1000L

    internal fun ceilingBytes(): Long {
        val budget = effectiveBudgetBytes()
        return budget + budget / 4
    }

    private fun overCeiling(parameters: LoadControl.Parameters): Boolean =
        parameters.bufferedDurationUs >= minAheadUs && bufferAllocator.totalBytesAllocated >= ceilingBytes()

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean =
        if (reserveCore.holdsLoading(parameters) || overCeiling(parameters)) false
        else super.shouldContinueLoading(parameters)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
        super.shouldStartPlayback(parameters) || overCeiling(parameters)

    private fun applyBudgetToActiveLoadingStates() {
        runCatching {
            val targetBytes = currentTargetBufferBytes()
            val loadingStatesField = DefaultLoadControl::class.java.getDeclaredField("loadingStates").apply {
                isAccessible = true
            }
            val statesMap = loadingStatesField.get(this) as? Map<*, *> ?: return
            for (state in statesMap.values) {
                if (state == null) continue
                val targetField = state.javaClass.getDeclaredField("targetBufferBytes").apply {
                    isAccessible = true
                }
                targetField.setInt(state, targetBytes)
            }
            val updateAllocatorMethod = DefaultLoadControl::class.java.getDeclaredMethod("updateAllocator").apply {
                isAccessible = true
            }
            updateAllocatorMethod.invoke(this)
        }
    }

    private fun applyBackBufferToInternalPlayer(player: ExoPlayer, effectiveBackBufferUs: Long) {
        runCatching {
            val internalPlayerField = findFieldInHierarchy(player.javaClass, "internalPlayer") ?: return
            internalPlayerField.isAccessible = true
            val internalPlayer = internalPlayerField.get(player) ?: return
            val backBufferField = findFieldInHierarchy(internalPlayer.javaClass, "backBufferDurationUs") ?: return
            backBufferField.isAccessible = true
            val looper = runCatching { player.playbackLooper }.getOrNull()
            if (looper != null && looper.thread.isAlive && Thread.currentThread() !== looper.thread) {
                Handler(looper).post {
                    runCatching { backBufferField.setLong(internalPlayer, effectiveBackBufferUs) }
                }
            } else {
                backBufferField.setLong(internalPlayer, effectiveBackBufferUs)
            }
        }
    }

    private fun findFieldInHierarchy(clazz: Class<*>, name: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    companion object {
        // The allocator counts its bytes in an int, which wraps at 2 GiB and then reads as "not full".
        // 1,600 MB keeps the ceiling (a quarter over) at 2,000 MB.
        const val MAX_BUDGET_BYTES: Long = 1600L * 1024L * 1024L
    }
}
