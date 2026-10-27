package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSkipIntervalsTest {

    private val durationMs = 24 * 60_000L // 24 minutes

    private fun chapters(vararg entries: Pair<Long, String?>): List<PlayerChapter> =
        PlayerChapters.normalize(entries.map { (ms, title) -> PlayerChapter(ms, title) })

    @Test
    fun `OP chapter produces intro interval`() {
        val chapters = chapters(
            0L to "Prologue",
            90_000L to "OP",
            180_000L to "Episode",
            1_320_000L to "ED"
        )
        val intervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
        val opening = intervals.find { it.type == "intro" }
        assertEquals(90.0, opening!!.startTime, 0.01)
        assertEquals(180.0, opening.endTime, 0.01)
        assertEquals("chapters", opening.provider)
    }

    @Test
    fun `Opening chapter produces intro interval`() {
        val chapters = chapters(
            0L to "Cold Open",
            120_000L to "Opening",
            210_000L to "Part A"
        )
        val intervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
        val opening = intervals.find { it.type == "intro" }
        assertEquals(120.0, opening!!.startTime, 0.01)
        assertEquals(210.0, opening.endTime, 0.01)
    }

    @Test
    fun `ED chapter produces outro interval`() {
        val chapters = chapters(
            0L to "Episode",
            1_300_000L to "ED",
            1_390_000L to "Preview"
        )
        val intervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
        val ending = intervals.find { it.type == "outro" }
        assertEquals(1_300.0, ending!!.startTime, 0.01)
        assertEquals(1_390.0, ending.endTime, 0.01)
    }

    @Test
    fun `Recap chapter produces recap interval`() {
        val chapters = chapters(
            0L to "Previously on...",
            60_000L to "OP",
            150_000L to "Episode"
        )
        val intervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
        val recap = intervals.find { it.type == "recap" }
        assertEquals(0.0, recap!!.startTime, 0.01)
        assertEquals(60.0, recap.endTime, 0.01)
    }

    @Test
    fun `Intro alone treated as opening when no OP exists`() {
        val chapters = chapters(
            0L to "Intro",
            90_000L to "Episode",
            1_320_000L to "Credits"
        )
        val intervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
        val opening = intervals.find { it.type == "intro" }
        assertEquals(0.0, opening!!.startTime, 0.01)
        assertEquals(90.0, opening.endTime, 0.01)
    }

    @Test
    fun `Intro demoted when OP also exists`() {
        val chapters = chapters(
            0L to "Intro",
            60_000L to "OP",
            150_000L to "Episode",
            1_320_000L to "ED"
        )
        val intervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
        // "Intro" should NOT produce a skip interval
        val intros = intervals.filter { it.type == "intro" }
        assertEquals(1, intros.size)
        // The one "intro" should be the OP, not the pre-opening "Intro"
        assertEquals(60.0, intros[0].startTime, 0.01)
        assertEquals(150.0, intros[0].endTime, 0.01)
    }

    @Test
    fun `Intro demoted when Opening also exists`() {
        val chapters = chapters(
            0L to "Intro",
            45_000L to "Opening Theme",
            135_000L to "Part 1"
        )
        val intervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
        val intros = intervals.filter { it.type == "intro" }
        assertEquals(1, intros.size)
        assertEquals(45.0, intros[0].startTime, 0.01)
    }

    @Test
    fun `chapters without known titles produce no intervals`() {
        val chapters = chapters(
            0L to "Chapter 1",
            300_000L to "Chapter 2",
            600_000L to "Chapter 3"
        )
        val intervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
        assertTrue(intervals.isEmpty())
    }

    @Test
    fun `fewer than two chapters produce no intervals`() {
        val single = listOf(PlayerChapter(0L, "OP"))
        val intervals = ChapterSkipIntervals.fromChapters(single, durationMs)
        assertTrue(intervals.isEmpty())
    }

    @Test
    fun `case insensitive matching`() {
        val chapters = chapters(
            0L to "RECAP",
            60_000L to "opening",
            150_000L to "episode",
            1_300_000L to "Ending Credits"
        )
        val intervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
        assertEquals(3, intervals.size)
        assertTrue(intervals.any { it.type == "recap" })
        assertTrue(intervals.any { it.type == "intro" })
        assertTrue(intervals.any { it.type == "outro" })
    }

    @Test
    fun `Credits matched as ending`() {
        val chapters = chapters(
            0L to "Movie",
            5_400_000L to "Credits"
        )
        val movieDuration = 6_000_000L
        val intervals = ChapterSkipIntervals.fromChapters(chapters, movieDuration)
        val ending = intervals.find { it.type == "outro" }
        assertEquals(5_400.0, ending!!.startTime, 0.01)
    }

    @Test
    fun `last chapter uses duration as end time`() {
        val chapters = chapters(
            0L to "Episode",
            1_300_000L to "ED"
        )
        val intervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
        val ending = intervals.find { it.type == "outro" }
        assertEquals(durationMs / 1000.0, ending!!.endTime, 0.01)
    }

    @Test
    fun `very short chapters are filtered out`() {
        val chapters = chapters(
            0L to "Episode",
            1_300_000L to "OP",  // only 2 seconds long
            1_302_000L to "Part B"
        )
        val intervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
        // 2 seconds < 5 second minimum
        assertTrue(intervals.none { it.type == "intro" })
    }
}
