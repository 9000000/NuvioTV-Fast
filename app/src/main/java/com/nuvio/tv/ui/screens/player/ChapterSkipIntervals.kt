package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.repository.SkipInterval

/**
 * Derive skip intervals from container chapters (MKV/Matroska).
 *
 * Chapter titles are matched case-insensitively against known patterns:
 * - Opening: "op" (word boundary), "opening"
 * - Ending:  "ed" (word boundary), "ending", "credits"
 * - Recap:   "recap", "previously"
 * - Intro:   "intro" - treated as opening only when no dedicated OP/Opening chapter exists,
 *            because some releases label the pre-opening segment as "Intro" and the actual
 *            opening as "OP" or "Opening".
 */
internal object ChapterSkipIntervals {

    private const val PROVIDER = "chapters"

    // Minimum chapter duration to consider (avoid 0-length or tiny chapters)
    private const val MIN_DURATION_SEC = 5.0

    private val OPENING_PATTERN = Regex("\\b(op|opening)\\b", RegexOption.IGNORE_CASE)
    private val ENDING_PATTERN = Regex("\\b(ed|ending|credits)\\b", RegexOption.IGNORE_CASE)
    private val RECAP_PATTERN = Regex("\\b(recap|previously)\\b", RegexOption.IGNORE_CASE)
    private val INTRO_PATTERN = Regex("\\bintro\\b", RegexOption.IGNORE_CASE)

    fun fromChapters(
        chapters: List<PlayerChapter>,
        durationMs: Long
    ): List<SkipInterval> {
        if (chapters.size < 2 || durationMs <= 0L) return emptyList()

        val classified = chapters.mapIndexed { index, chapter ->
            val endMs = if (index + 1 < chapters.size) {
                chapters[index + 1].startMs
            } else {
                durationMs
            }
            ClassifiedChapter(
                chapter = chapter,
                endMs = endMs,
                type = classifyTitle(chapter.title)
            )
        }

        // If both "intro" and a dedicated "opening" exist, demote "intro" to null (pre-opening content)
        val hasExplicitOpening = classified.any { it.type == ChapterType.OPENING }
        val resolved = if (hasExplicitOpening) {
            classified.map { c ->
                if (c.type == ChapterType.INTRO) c.copy(type = null) else c
            }
        } else {
            // No explicit opening: promote "intro" to opening
            classified.map { c ->
                if (c.type == ChapterType.INTRO) c.copy(type = ChapterType.OPENING) else c
            }
        }

        return resolved.mapNotNull { c ->
            val skipType = c.type?.skipIntervalType ?: return@mapNotNull null
            val startSec = c.chapter.startMs / 1000.0
            val endSec = c.endMs / 1000.0
            if (endSec - startSec < MIN_DURATION_SEC) return@mapNotNull null
            SkipInterval(
                startTime = startSec,
                endTime = endSec,
                type = skipType,
                provider = PROVIDER
            )
        }
    }

    private fun classifyTitle(title: String?): ChapterType? {
        if (title.isNullOrBlank()) return null
        // Check opening/ending first (more specific), then intro (fallback)
        if (OPENING_PATTERN.containsMatchIn(title)) return ChapterType.OPENING
        if (ENDING_PATTERN.containsMatchIn(title)) return ChapterType.ENDING
        if (RECAP_PATTERN.containsMatchIn(title)) return ChapterType.RECAP
        if (INTRO_PATTERN.containsMatchIn(title)) return ChapterType.INTRO
        return null
    }

    private enum class ChapterType(val skipIntervalType: String) {
        OPENING("intro"),
        ENDING("outro"),
        RECAP("recap"),
        INTRO("intro"),
    }

    private data class ClassifiedChapter(
        val chapter: PlayerChapter,
        val endMs: Long,
        val type: ChapterType?
    )
}

internal fun mergeSkipIntervals(
    apiIntervals: List<SkipInterval>,
    chapterIntervals: List<SkipInterval>
): List<SkipInterval> {
    if (chapterIntervals.isEmpty()) return apiIntervals
    if (apiIntervals.isEmpty()) return chapterIntervals
    val chosen = LinkedHashMap<String, SkipInterval>()
    // Chapters first (priority)
    for (interval in chapterIntervals) {
        val category = skipIntervalCategory(interval.type) ?: continue
        chosen.putIfAbsent(category, interval)
    }
    // API fills gaps
    for (interval in apiIntervals) {
        val category = skipIntervalCategory(interval.type) ?: continue
        chosen.putIfAbsent(category, interval)
    }
    // Keep any API intervals with unknown categories (post-credits, movie-credits, etc.)
    val uncategorized = apiIntervals.filter { skipIntervalCategory(it.type) == null }
    return chosen.values.toList() + uncategorized
}

private fun skipIntervalCategory(type: String): String? = when (type.lowercase()) {
    "intro", "op", "mixed-op" -> "opening"
    "outro", "ed", "mixed-ed", "credits", "ending" -> "ending"
    "recap" -> "recap"
    else -> null
}
