package com.nuvio.tv.core.tts

/**
 * Pure timing budget, speech duration estimation, bounds clamping, and adaptive speed
 * utilities for synchronizing Google Offline TTS output with subtitle timestamps.
 */
object SubtitleTtsTimingCalculator {
    const val MIN_MAX_AUTO_SPEED = 1.4f
    const val MAX_MAX_AUTO_SPEED = 3.0f
    const val DEFAULT_MAX_AUTO_SPEED = 2.5f

    const val MIN_BASE_SPEED = 0.7f
    const val MIN_UI_BASE_SPEED = 0.8f
    const val MAX_BASE_SPEED = 2.0f
    const val DEFAULT_BASE_SPEED = 1.4f

    const val MIN_PLAYBACK_SPEED = 0.25f
    const val MAX_PLAYBACK_SPEED = 4.0f
    const val DEFAULT_PLAYBACK_SPEED = 1.0f

    const val MIN_PITCH = 0.6f
    const val MAX_PITCH = 1.5f
    const val DEFAULT_PITCH = 1.0f

    const val MIN_VOLUME = 0.0f
    const val MAX_VOLUME = 1.0f
    const val DEFAULT_MOVIE_VOLUME = 0.25f
    const val DEFAULT_VOICE_VOLUME = 1.0f

    val MOVIE_VOLUME_STEPS: List<Float> = listOf(0.00f, 0.10f, 0.20f, 0.25f, 0.30f, 0.40f, 0.50f, 0.70f, 0.85f, 1.00f)
    val VOICE_VOLUME_STEPS: List<Float> = listOf(0.20f, 0.40f, 0.60f, 0.75f, 0.85f, 1.00f)
    val BASE_SPEED_STEPS: List<Float> = listOf(0.80f, 0.90f, 1.00f, 1.10f, 1.20f, 1.30f, 1.40f, 1.50f, 1.75f, 2.00f)
    val PITCH_STEPS: List<Float> = listOf(0.60f, 0.70f, 0.80f, 0.90f, 1.00f, 1.10f, 1.20f, 1.30f, 1.40f, 1.50f)
    val MAX_AUTO_SPEED_STEPS: List<Float> = listOf(1.40f, 1.60f, 1.80f, 2.00f, 2.10f, 2.25f, 2.50f, 2.75f, 3.00f)

    /**
     * Advances [current] to the next strictly greater step in [options], wrapping to the first
     * step when at or above the maximum option. Safe even when [current] does not match a step.
     */
    fun nextSteppedOption(options: List<Float>, current: Float): Float {
        if (options.isEmpty()) return current
        return options.firstOrNull { it > current + 0.005f } ?: options.first()
    }

    const val MIN_SPEECH_BUDGET_MS = 250L
    const val SAFETY_BREATH_MARGIN_MS = 80L

    private const val BASE_UTTERANCE_OVERHEAD_MS = 140L
    private const val MS_PER_WORD = 220L
    private const val BASE_CHARS_PER_WORD = 4
    private const val MS_PER_EXTRA_CHAR = 18L
    private const val CLAUSE_PAUSE_MS = 120L
    private const val TERMINAL_PAUSE_MS = 140L

    private val whitespaceRegex = Regex("\\s+")
    private val clausePauseChars = setOf(',', ';', ':')
    private val terminalPauseChars = setOf('.', '!', '?', '…')
    private val dashPauseChars = setOf('–', '—')

    /**
     * Resolves the available real-time speech budget in milliseconds for [context].
     *
     * Prioritizes `nextCueStartMs` over `endMs` so speech can continue naturally through any
     * trailing silence before the next subtitle appears, adjusts for elapsed cue time and
     * [SubtitleCuePlaybackContext.playbackSpeed], and returns `null` when no valid slot end exists.
     */
    fun resolveSpeechBudgetMs(context: SubtitleCuePlaybackContext?): Long? {
        if (context == null) return null
        val currentCue = context.currentCue
        val effectiveStartMs = maxOf(currentCue.startMs, context.positionMs)
        val slotEndMs = currentCue.nextCueStartMs?.takeIf { it > effectiveStartMs }
            ?: currentCue.endMs?.takeIf { it > effectiveStartMs }
            ?: return null

        val rawWindowMs = slotEndMs - effectiveStartMs
        val safePlaybackSpeed = clampFinite(
            value = context.playbackSpeed,
            fallback = DEFAULT_PLAYBACK_SPEED,
            min = MIN_PLAYBACK_SPEED,
            max = MAX_PLAYBACK_SPEED,
        )
        val realTimeWindowMs = (rawWindowMs.toDouble() / safePlaybackSpeed.toDouble()).toLong()
        return (realTimeWindowMs - SAFETY_BREATH_MARGIN_MS).coerceAtLeast(MIN_SPEECH_BUDGET_MS)
    }

    /**
     * Estimates the natural spoken duration in milliseconds at `1.0f` speed for [cleanText].
     *
     * Uses whitespace-separated word counts, extra character duration for longer words,
     * punctuation pause bonuses, and base utterance overhead.
     */
    fun estimateNaturalDurationMs(cleanText: String): Long {
        val trimmed = cleanText.trim()
        if (trimmed.isEmpty()) return 0L

        val words = trimmed.split(whitespaceRegex)
        var wordsDurationMs = 0L
        for (word in words) {
            val coreLength = word.count { it.isLetterOrDigit() }
            val effectiveLength = if (coreLength > 0) coreLength else word.length
            val extraChars = (effectiveLength - BASE_CHARS_PER_WORD).coerceAtLeast(0)
            wordsDurationMs += MS_PER_WORD + (extraChars * MS_PER_EXTRA_CHAR)
        }

        return BASE_UTTERANCE_OVERHEAD_MS + wordsDurationMs + countPunctuationPauseMs(trimmed)
    }

    /**
     * Calculates the target speech speed to fit [cleanText] within [budgetMs].
     *
     * Keeps [baseSpeed] untouched when auto-speed is disabled or when estimated duration already
     * fits within [budgetMs], and falls back to length-based scaling when [budgetMs] is `null`.
     */
    fun calculateTimingAwareSpeed(
        cleanText: String,
        budgetMs: Long?,
        baseSpeed: Float,
        autoSpeedEnabled: Boolean,
        maxAutoSpeed: Float = DEFAULT_MAX_AUTO_SPEED,
    ): Float {
        val safeBaseSpeed = clampBaseSpeed(baseSpeed)
        if (!autoSpeedEnabled) {
            return safeBaseSpeed
        }

        val safeMaxSpeed = resolveSafeMaxSpeed(safeBaseSpeed, maxAutoSpeed)
        if (budgetMs == null) {
            return SubtitleTtsTextCleaner.calculateEffectiveSpeed(
                cleanText = cleanText,
                baseSpeed = safeBaseSpeed,
                autoSpeedEnabled = true,
                maxAutoSpeed = safeMaxSpeed,
            )
        }

        val naturalDurationMs = estimateNaturalDurationMs(cleanText)
        if (naturalDurationMs <= 0L) {
            return safeBaseSpeed
        }
        if (budgetMs <= 0L) {
            return safeMaxSpeed
        }

        val estimatedAtBaseMs = naturalDurationMs.toFloat() / safeBaseSpeed
        if (estimatedAtBaseMs <= budgetMs.toFloat()) {
            return safeBaseSpeed
        }

        val requiredSpeed = safeBaseSpeed * (estimatedAtBaseMs / budgetMs.toFloat())
        return requiredSpeed.coerceIn(safeBaseSpeed, safeMaxSpeed)
    }

    /** Clamps [value] into `[min, max]` if finite, otherwise returns [fallback]. */
    fun clampFinite(value: Float, fallback: Float, min: Float, max: Float): Float =
        if (value.isFinite()) value.coerceIn(min, max) else fallback

    /** Clamps base TTS speech speed into `[MIN_BASE_SPEED, MAX_BASE_SPEED]`. */
    fun clampBaseSpeed(value: Float): Float =
        clampFinite(value, DEFAULT_BASE_SPEED, MIN_BASE_SPEED, MAX_BASE_SPEED)

    /** Clamps max auto-pacing TTS speed into `[MIN_MAX_AUTO_SPEED, MAX_MAX_AUTO_SPEED]`. */
    fun clampMaxAutoSpeed(value: Float): Float =
        clampFinite(value, DEFAULT_MAX_AUTO_SPEED, MIN_MAX_AUTO_SPEED, MAX_MAX_AUTO_SPEED)

    /** Clamps TTS pitch into `[MIN_PITCH, MAX_PITCH]`. */
    fun clampPitch(value: Float): Float =
        clampFinite(value, DEFAULT_PITCH, MIN_PITCH, MAX_PITCH)

    /** Clamps normalized audio volume into `[MIN_VOLUME, MAX_VOLUME]`. */
    fun clampVolume(value: Float, fallback: Float = MAX_VOLUME): Float =
        clampFinite(value, fallback, MIN_VOLUME, MAX_VOLUME)

    /** Resolves an upper speed bound guaranteed to be at least [safeBaseSpeed]. */
    fun resolveSafeMaxSpeed(safeBaseSpeed: Float, maxAutoSpeed: Float): Float {
        val clampedMaxAuto = clampMaxAutoSpeed(maxAutoSpeed)
        return maxOf(safeBaseSpeed, clampedMaxAuto)
    }

    /**
     * Estimates spoken duration in milliseconds for [cleanText] at [speechSpeed].
     */
    fun estimateSpokenDurationMs(cleanText: String, speechSpeed: Float): Long {
        val naturalMs = estimateNaturalDurationMs(cleanText)
        if (naturalMs <= 0L) return 0L
        val safeSpeed = clampFinite(
            value = speechSpeed,
            fallback = DEFAULT_BASE_SPEED,
            min = MIN_BASE_SPEED,
            max = MAX_MAX_AUTO_SPEED,
        )
        return (naturalMs.toDouble() / safeSpeed.toDouble()).toLong().coerceAtLeast(150L)
    }

    /**
     * Computes an adaptive carry-over wait cap when `waitForCueCompletion` is enabled.
     *
     * Avoids waiting the full [MAX_CARRY_OVER_MS] when the active utterance has already exceeded
     * its estimated speech length (e.g. Google TTS trailing silence or delayed `onDone`), while
     * preventing a long carry-over from starving the queued cue's own budget.
     */
    fun computeDynamicCarryOverMs(
        elapsedCurrentSpeechMs: Long,
        estimatedCurrentSpeechMs: Long,
        pendingCueBudgetMs: Long?,
        maxCarryOverMs: Long = MAX_CARRY_OVER_MS,
    ): Long {
        val safeMax = maxCarryOverMs.coerceAtLeast(MIN_CARRY_OVER_TAIL_MS)
        val safeElapsed = elapsedCurrentSpeechMs.coerceAtLeast(0L)
        val safeEstimated = estimatedCurrentSpeechMs.coerceAtLeast(0L)
        val remainingCurrentMs = (safeEstimated - safeElapsed).coerceAtLeast(0L)
        if (remainingCurrentMs == 0L) {
            return MIN_CARRY_OVER_TAIL_MS.coerceAtMost(safeMax)
        }
        val desiredCarryOverMs = (remainingCurrentMs + MIN_CARRY_OVER_TAIL_MS).coerceAtMost(safeMax)
        val budgetCapMs = pendingCueBudgetMs
            ?.takeIf { it > 0L }
            ?.let { (it * 0.55).toLong().coerceAtLeast(MIN_PENDING_BUDGET_CARRY_OVER_MS) }
            ?: safeMax
        return minOf(desiredCarryOverMs, budgetCapMs, safeMax).coerceAtLeast(MIN_CARRY_OVER_TAIL_MS)
    }

    private const val MIN_CARRY_OVER_TAIL_MS = 220L
    private const val MIN_PENDING_BUDGET_CARRY_OVER_MS = 350L

    private fun countPunctuationPauseMs(text: String): Long {
        var pauseMs = 0L
        var previousWasTerminal = false
        for (index in text.indices) {
            val ch = text[index]
            when {
                ch in terminalPauseChars -> {
                    if (!previousWasTerminal) {
                        pauseMs += TERMINAL_PAUSE_MS
                        previousWasTerminal = true
                    }
                }
                ch in dashPauseChars || (ch == '-' && isStandaloneDash(text, index)) -> {
                    pauseMs += TERMINAL_PAUSE_MS
                    previousWasTerminal = false
                }
                ch in clausePauseChars -> {
                    pauseMs += CLAUSE_PAUSE_MS
                    previousWasTerminal = false
                }
                else -> {
                    previousWasTerminal = false
                }
            }
        }
        return pauseMs
    }

    private fun isStandaloneDash(text: String, index: Int): Boolean {
        val prevIsSpace = index == 0 || text[index - 1].isWhitespace()
        val nextIsSpace = index == text.lastIndex || text[index + 1].isWhitespace()
        return prevIsSpace || nextIsSpace
    }
}
