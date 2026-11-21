package com.nuvio.tv.core.tts

/**
 * Cleans subtitle cue text for speech synthesis and computes fallback length-based speeds.
 */
object SubtitleTtsTextCleaner {
    private const val SHORT_TEXT_MAX_CHARS = 30
    private const val MEDIUM_TEXT_MAX_CHARS = 60
    private const val LONG_TEXT_MAX_CHARS = 90

    private const val SHORT_TEXT_MULTIPLIER = 1.0f
    private const val MEDIUM_TEXT_MULTIPLIER = 1.12f
    private const val LONG_TEXT_MULTIPLIER = 1.25f
    private const val VERY_LONG_TEXT_MULTIPLIER = 1.38f

    private const val MIN_AUTO_EFFECTIVE_SPEED = 0.8f

    private val assOverrideRegex = Regex("\\{[^}]*\\}")
    private val htmlTagRegex = Regex("<[^>]*>")
    private val bracketSdhRegex = Regex("\\[[^\\]]*\\]")
    private val parenSdhRegex = Regex("\\([^)]*\\)")
    private val musicalNotesRegex = Regex("[♪♫]+")
    private val leadingSpeakerDashRegex = Regex("^\\s*[-–—]+\\s*")
    private val multiWhitespaceRegex = Regex("\\s+")

    /**
     * Strips ASS override tags, HTML tags, SDH annotations, musical notes, and speaker dashes.
     */
    fun clean(rawText: String): String {
        if (rawText.isBlank()) return ""

        val normalizedLines = rawText
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
            .replace(assOverrideRegex, "")
            .replace(htmlTagRegex, "")
            .replace(bracketSdhRegex, " ")
            .replace(parenSdhRegex, " ")
            .replace(musicalNotesRegex, " ")
            .lines()
            .map { line ->
                line.replace(leadingSpeakerDashRegex, "").trim()
            }
            .filter { it.isNotEmpty() }

        return normalizedLines
            .joinToString(" ")
            .replace(leadingSpeakerDashRegex, "")
            .replace(multiWhitespaceRegex, " ")
            .trim()
    }

    /**
     * Ensures synthesized text ends with terminal punctuation so TTS prosody
     * lands naturally instead of truncating the final syllable.
     */
    fun ensureSentenceEndingPunctuation(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        return when (trimmed.last()) {
            '.', '!', '?', '…', ':', ';' -> trimmed
            else -> "$trimmed."
        }
    }

    /**
     * Calculates an effective TTS speech speed based on [cleanText] length when timing metadata
     * is unavailable, clamped between [MIN_AUTO_EFFECTIVE_SPEED] and [maxAutoSpeed].
     */
    fun calculateEffectiveSpeed(
        cleanText: String,
        baseSpeed: Float,
        autoSpeedEnabled: Boolean,
        maxAutoSpeed: Float = SubtitleTtsTimingCalculator.DEFAULT_MAX_AUTO_SPEED,
    ): Float {
        val safeBaseSpeed = SubtitleTtsTimingCalculator.clampBaseSpeed(baseSpeed)
        if (!autoSpeedEnabled) {
            return safeBaseSpeed
        }
        val upperBound = SubtitleTtsTimingCalculator.resolveSafeMaxSpeed(safeBaseSpeed, maxAutoSpeed)
        val length = cleanText.length
        val multiplier = when {
            length <= SHORT_TEXT_MAX_CHARS -> SHORT_TEXT_MULTIPLIER
            length <= MEDIUM_TEXT_MAX_CHARS -> MEDIUM_TEXT_MULTIPLIER
            length <= LONG_TEXT_MAX_CHARS -> LONG_TEXT_MULTIPLIER
            else -> VERY_LONG_TEXT_MULTIPLIER
        }
        return (safeBaseSpeed * multiplier).coerceIn(MIN_AUTO_EFFECTIVE_SPEED, upperBound)
    }
}
