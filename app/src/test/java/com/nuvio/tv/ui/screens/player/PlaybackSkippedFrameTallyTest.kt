package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSkippedFrameTallyTest {
    private val renderer = Any()

    private fun playing(tally: PlaybackSkippedFrameTally, skipped: Int = 0) =
        tally.apply { onReset(renderer, 0, null, 0); onFirstFrame(renderer, skipped) }

    @Test fun `skips during normal play are counted`() {
        val tally = playing(PlaybackSkippedFrameTally())
        assertEquals(PlaybackSkippedFrameSample(0, null), tally.sample(renderer, 0, 100, 1_000))
        assertEquals(PlaybackSkippedFrameSample(2, 2_000), tally.sample(renderer, 2, 150, 2_000))
        assertEquals(PlaybackSkippedFrameSample(2, 2_000), tally.sample(renderer, 2, 200, 3_000))
        assertEquals(PlaybackSkippedFrameSample(5, 4_000), tally.sample(renderer, 5, 250, 4_000))
    }

    @Test fun `frames passed over at the start are left out`() {
        val tally = PlaybackSkippedFrameTally()
        tally.onReset(renderer, 0, null, 0)
        assertEquals(0, tally.sample(renderer, 40, 0, 500)!!.count)
        tally.onFirstFrame(renderer, 48)
        assertEquals(PlaybackSkippedFrameSample(0, null), tally.sample(renderer, 48, 30, 1_000))
        assertEquals(PlaybackSkippedFrameSample(1, 2_000), tally.sample(renderer, 49, 60, 2_000))
    }

    @Test fun `frames passed over to reach a seek point are left out`() {
        val tally = playing(PlaybackSkippedFrameTally())
        tally.sample(renderer, 3, 500, 1_000)
        tally.onReset(renderer, 500, skipped = 3, nowMs = 1_500)
        assertEquals(PlaybackSkippedFrameSample(3, 1_000), tally.sample(renderer, 60, 501, 2_000))
        tally.onFirstFrame(renderer, 75)
        assertEquals(PlaybackSkippedFrameSample(3, 1_000), tally.sample(renderer, 75, 520, 3_000))
        assertEquals(PlaybackSkippedFrameSample(4, 4_000), tally.sample(renderer, 76, 544, 4_000))
    }

    @Test fun `a seek and its first frame between two samples add nothing`() {
        val tally = playing(PlaybackSkippedFrameTally())
        tally.sample(renderer, 0, 100, 1_000)
        tally.onReset(renderer, 110, skipped = 0, nowMs = 1_500)
        tally.onFirstFrame(renderer, 30)
        assertEquals(0, tally.sample(renderer, 30, 120, 2_000)!!.count)
    }

    @Test fun `holding seek keeps counting paused until a frame is shown`() {
        val tally = playing(PlaybackSkippedFrameTally())
        repeat(5) { step ->
            tally.onReset(renderer, 200, null, 0)
            assertEquals(0, tally.sample(renderer, 20 * (step + 1), 200, 1_000L * step)!!.count)
        }
        tally.onFirstFrame(renderer, 130)
        assertEquals(0, tally.sample(renderer, 130, 230, 9_000)!!.count)
    }

    @Test fun `a seek without a first frame resumes once frames are shown again`() {
        val tally = playing(PlaybackSkippedFrameTally())
        tally.onReset(renderer, 300, null, 0)
        val settle = PlaybackSkippedFrameTally.FRAMES_TO_SETTLE
        assertEquals(0, tally.sample(renderer, 4, 300 + settle - 1, 1_000)!!.count)
        assertEquals(0, tally.sample(renderer, 4, 300 + settle, 2_000)!!.count)
        assertEquals(PlaybackSkippedFrameSample(2, 3_000), tally.sample(renderer, 6, 330, 3_000))
    }

    @Test fun `paused after a seek stays paused`() {
        val tally = playing(PlaybackSkippedFrameTally())
        tally.onReset(renderer, 300, null, 0)
        assertEquals(0, tally.sample(renderer, 25, 301, 1_000)!!.count)
        assertEquals(0, tally.sample(renderer, 25, 301, 60_000)!!.count)
    }

    @Test fun `a new renderer starts a new count and waits for its first frame`() {
        val tally = playing(PlaybackSkippedFrameTally())
        tally.sample(renderer, 0, 10, 1_000)
        tally.sample(renderer, 7, 20, 2_000)
        val next = Any()
        tally.onReset(next, 0, null, 2_500)
        assertEquals(PlaybackSkippedFrameSample(0, null), tally.sample(next, 9, 0, 3_000))
        tally.onFirstFrame(next, 11)
        assertEquals(PlaybackSkippedFrameSample(1, 4_000), tally.sample(next, 12, 40, 4_000))
    }

    @Test fun `a new renderer seen first by a sample settles on shown frames`() {
        val tally = playing(PlaybackSkippedFrameTally())
        tally.sample(renderer, 5, 10, 1_000)
        val next = Any()
        assertEquals(PlaybackSkippedFrameSample(0, null), tally.sample(next, 30, 0, 2_000))
        assertEquals(0, tally.sample(next, 33, PlaybackSkippedFrameTally.FRAMES_TO_SETTLE, 3_000)!!.count)
        assertEquals(1, tally.sample(next, 34, 40, 4_000)!!.count)
    }

    @Test fun `a first frame shown again without a reset rebases`() {
        val tally = playing(PlaybackSkippedFrameTally())
        tally.sample(renderer, 2, 10, 1_000)
        tally.onFirstFrame(renderer, 9)
        assertEquals(PlaybackSkippedFrameSample(2, 1_000), tally.sample(renderer, 9, 20, 2_000))
    }

    @Test fun `missing counters are unavailable and keep the count`() {
        val tally = playing(PlaybackSkippedFrameTally())
        tally.sample(renderer, 3, 10, 1_000)
        assertNull(tally.sample(null, 3, 10, 2_000))
        assertNull(tally.sample(renderer, null, 10, 2_000))
        assertEquals(PlaybackSkippedFrameSample(3, 1_000), tally.sample(renderer, 3, 20, 3_000))
    }

    @Test fun `a counter going backwards is not counted`() {
        val tally = playing(PlaybackSkippedFrameTally(), skipped = 10)
        assertEquals(0, tally.sample(renderer, 4, 10, 1_000)!!.count)
        assertEquals(1, tally.sample(renderer, 5, 20, 2_000)!!.count)
    }

    @Test fun `skips up to a seek are kept when nobody sampled them`() {
        val tally = playing(PlaybackSkippedFrameTally())
        tally.sample(renderer, 2, 100, 1_000)
        tally.onReset(renderer, 900, skipped = 9, nowMs = 50_000)
        assertEquals(PlaybackSkippedFrameSample(9, 50_000), tally.sample(renderer, 70, 901, 51_000))
        tally.onFirstFrame(renderer, 80)
        assertEquals(PlaybackSkippedFrameSample(10, 52_000), tally.sample(renderer, 81, 930, 52_000))
    }

    @Test fun `a second reset while paused adds nothing`() {
        val tally = playing(PlaybackSkippedFrameTally())
        tally.onReset(renderer, 100, skipped = 0, nowMs = 1_000)
        tally.onReset(renderer, 100, skipped = 40, nowMs = 2_000)
        assertEquals(PlaybackSkippedFrameSample(0, null), tally.sample(renderer, 40, 100, 3_000))
    }

    @Test fun `a reset from another renderer adds nothing`() {
        val tally = playing(PlaybackSkippedFrameTally())
        tally.onReset(Any(), 0, skipped = 25, nowMs = 1_000)
        assertEquals(PlaybackSkippedFrameSample(0, null), tally.sample(renderer, 25, 0, 2_000))
    }
}
