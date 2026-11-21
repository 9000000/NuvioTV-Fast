package com.nuvio.tv.core.tts

/**
 * Pure helper for calculating [SubtitleCuePlaybackContext] across different subtitle renderers
 * (ExoPlayer Sidecar, Embedded Tracks, and MPV).
 */
object SubtitleTimelineTimingHelper {

    /**
     * Constructs timing context from discrete start and end timestamps in milliseconds.
     */
    fun createTimingContext(
        rawText: String = "",
        startMs: Long,
        endMs: Long,
        playbackSpeed: Float = 1.0f,
        nextCueStartMs: Long? = null,
        positionMs: Long = startMs
    ): SubtitleCuePlaybackContext {
        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = endMs.coerceAtLeast(safeStart)
        val safePos = positionMs.coerceAtLeast(safeStart)
        val safeSpeed = if (playbackSpeed > 0.05f) playbackSpeed else 1.0f

        val cue = TimedSpeechCue(
            rawText = rawText,
            startMs = safeStart,
            endMs = safeEnd,
            nextCueStartMs = nextCueStartMs
        )
        return SubtitleCuePlaybackContext(
            currentCue = cue,
            playbackSpeed = safeSpeed,
            positionMs = safePos
        )
    }

    /**
     * Constructs timing context from MPV property seconds (`sub-start`, `sub-end`, `sub-delay`, `speed`).
     * Returns null if start/end are missing, NaN, or non-positive duration.
     */
    fun fromMpvProperties(
        rawText: String = "",
        subStartSec: Double?,
        subEndSec: Double?,
        subDelaySec: Double = 0.0,
        speed: Double = 1.0,
        positionSec: Double? = null
    ): SubtitleCuePlaybackContext? {
        if (subStartSec == null || subEndSec == null) return null
        if (subStartSec.isNaN() || subEndSec.isNaN() || subDelaySec.isNaN()) return null
        if (subEndSec <= subStartSec) return null

        val safeSpeed = if (!speed.isNaN() && speed > 0.05) speed.toFloat() else 1.0f
        val startMs = ((subStartSec + subDelaySec) * 1000.0).toLong().coerceAtLeast(0L)
        val endMs = ((subEndSec + subDelaySec) * 1000.0).toLong().coerceAtLeast(startMs)
        val posMs = positionSec
            ?.takeIf { !it.isNaN() && it >= 0.0 }
            ?.let { (it * 1000.0).toLong().coerceAtLeast(startMs) }
            ?: startMs

        val cue = TimedSpeechCue(
            rawText = rawText,
            startMs = startMs,
            endMs = endMs
        )
        return SubtitleCuePlaybackContext(
            currentCue = cue,
            playbackSpeed = safeSpeed,
            positionMs = posMs
        )
    }
}
