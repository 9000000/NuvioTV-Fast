package com.nuvio.tv.ui.screens.player

internal data class PlaybackSkippedFrameSample(val count: Int, val increasedAtMs: Long?)

/**
 * Video frames skipped during normal play, for the current renderer. Frames passed over to reach a start, seek or
 * source switch point count as skipped too, so counting pauses at [onReset] until the next [onFirstFrame], or until
 * [FRAMES_TO_SETTLE] frames have been shown (a seek to the current position reports no first frame). Skips up to
 * the reset are kept, so the count also covers play while nobody was sampling.
 * [scope] is the renderer's counters object. Call on the player's application thread.
 */
internal class PlaybackSkippedFrameTally {
    private var scope: Any? = null
    private var last: Int? = null
    private var renderedAtReset = 0
    private var count = 0
    private var increasedAtMs: Long? = null

    fun onReset(scope: Any?, rendered: Int?, skipped: Int?, nowMs: Long) {
        val previous = last
        if (scope != null && scope === this.scope && previous != null && skipped != null && skipped > previous) {
            count += skipped - previous
            increasedAtMs = nowMs
        }
        last = null
        renderedAtReset = rendered?.coerceAtLeast(0) ?: 0
    }

    fun onFirstFrame(scope: Any?, skipped: Int?) {
        if (scope == null || skipped == null) return
        if (scope !== this.scope) startScope(scope)
        last = skipped.coerceAtLeast(0)
    }

    fun sample(scope: Any?, skipped: Int?, rendered: Int?, nowMs: Long): PlaybackSkippedFrameSample? {
        if (scope == null || skipped == null) return null
        if (scope !== this.scope) {
            startScope(scope)
            last = null
            renderedAtReset = 0
        }
        val current = skipped.coerceAtLeast(0)
        val previous = last
        if (previous == null) {
            if ((rendered ?: 0) - renderedAtReset >= FRAMES_TO_SETTLE) last = current
        } else {
            if (current > previous) {
                count += current - previous
                increasedAtMs = nowMs
            }
            last = current
        }
        return PlaybackSkippedFrameSample(count, increasedAtMs)
    }

    private fun startScope(scope: Any) {
        this.scope = scope
        count = 0
        increasedAtMs = null
    }

    companion object {
        const val FRAMES_TO_SETTLE = 12
    }
}
