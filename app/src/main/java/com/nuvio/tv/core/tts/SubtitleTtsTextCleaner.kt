package com.nuvio.tv.core.tts

import java.util.Locale

/**
 * Cleans subtitle cue text for speech synthesis, deduplicates repeated lines within a cue,
 * extracts rolling-caption suffixes, and computes fallback length-based speeds.
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
    private val suffixBoundaryChars = setOf(' ', '\t', ',', '.', ';', ':', '!', '?', '-', '–', '—', '…')

    /**
     * Result of checking whether [currentLine] is a rolling-caption extension of [previousLine].
     */
    internal sealed interface PrefixExtensionResult {
        /** [currentLine] does not extend [previousLine] at a word boundary. */
        data object NotExtension : PrefixExtensionResult

        /** [currentLine] only added trailing punctuation/whitespace to [previousLine]. */
        data object PunctuationOnly : PrefixExtensionResult

        /** [currentLine] appended new spoken text [appendedText] after [previousLine]. */
        data class Appended(val appendedText: String) : PrefixExtensionResult
    }

    /**
     * Cleans [rawText] into individual non-blank spoken lines, stripping ASS override tags,
     * HTML tags, SDH annotations, musical notes, and speaker dashes, while deduplicating
     * identical lines within the same cue frame (preserving first-appearance order).
     */
    fun cleanLines(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()

        val rawLines = rawText
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
            .replace(assOverrideRegex, "")
            .replace(htmlTagRegex, "")
            .replace(bracketSdhRegex, " ")
            .replace(parenSdhRegex, " ")
            .replace(musicalNotesRegex, " ")
            .lines()

        val seenKeys = HashSet<String>(rawLines.size)
        val result = ArrayList<String>(rawLines.size)
        for (line in rawLines) {
            val cleaned = line
                .replace(leadingSpeakerDashRegex, "")
                .replace(multiWhitespaceRegex, " ")
                .trim()
            if (cleaned.isEmpty()) continue
            if (seenKeys.add(normalizeLineKey(cleaned))) {
                result.add(cleaned)
            }
        }
        return result
    }

    /**
     * Strips ASS override tags, HTML tags, SDH annotations, musical notes, and speaker dashes,
     * deduplicating identical lines within the same cue before joining them with spaces.
     */
    fun clean(rawText: String): String {
        val lines = cleanLines(rawText)
        if (lines.isEmpty()) return ""
        return lines
            .joinToString(" ")
            .replace(leadingSpeakerDashRegex, "")
            .replace(multiWhitespaceRegex, " ")
            .trim()
    }

    /**
     * Normalized deduplication key for a cleaned line (trimmed, collapsed whitespace, lowercase).
     */
    internal fun normalizeLineKey(cleanedLine: String): String =
        cleanedLine
            .trim()
            .replace(multiWhitespaceRegex, " ")
            .lowercase(Locale.ROOT)

    /**
     * Detects whether [currentLine] is a cumulative rolling-caption continuation of [previousLine]
     * at a clean word/punctuation boundary, preventing mid-word splits (e.g. "An" vs "Anh").
     */
    internal fun extractAppendedSuffix(
        previousLine: String,
        currentLine: String,
    ): PrefixExtensionResult {
        val prevBase = previousLine.trim().trimEnd('.', '…').trim()
        val currTrimmed = currentLine.trim()
        if (prevBase.length < 2 || currTrimmed.length <= prevBase.length) {
            return PrefixExtensionResult.NotExtension
        }
        if (!currTrimmed.startsWith(prevBase, ignoreCase = true)) {
            return PrefixExtensionResult.NotExtension
        }
        val boundaryChar = currTrimmed[prevBase.length]
        if (boundaryChar !in suffixBoundaryChars) {
            return PrefixExtensionResult.NotExtension
        }
        val suffix = currTrimmed
            .substring(prevBase.length)
            .trimStart { it in suffixBoundaryChars }
            .trim()
        if (suffix.isEmpty() || suffix.none { it.isLetterOrDigit() }) {
            return PrefixExtensionResult.PunctuationOnly
        }
        return PrefixExtensionResult.Appended(suffix)
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
