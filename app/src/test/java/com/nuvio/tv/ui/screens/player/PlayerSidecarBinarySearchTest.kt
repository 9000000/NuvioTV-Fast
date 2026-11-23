package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerSidecarBinarySearchTest {

    private fun cue(text: String): Cue = Cue.Builder().setText(text).build()

    @Test
    fun `binary search with prefix max matches linear scan across 2500 cues with overlaps`() {
        val list = ArrayList<CuesWithTiming>(2505)
        // Add a long overlapping sign cue spanning 200 seconds at the start
        list.add(CuesWithTiming(listOf(cue("LONG_SIGN")), 5_000_000L, 200_000_000L))
        for (i in 0 until 2500) {
            val startUs = i * 2_000_000L
            val durationUs = if (i % 7 == 0) 5_500_000L else 1_600_000L
            list.add(CuesWithTiming(listOf(cue("Line $i")), startUs, durationUs))
        }
        // Add an open-ended cue near the end (duration unset)
        list.add(CuesWithTiming(listOf(cue("OPEN_END")), 4_995_000_000L, C.TIME_UNSET))
        list.sortBy { it.startTimeUs }

        val prefixMax = buildSidecarPrefixMaxEndUs(list)
        val probePositions = listOf(
            0L,
            500_000L,
            5_000_000L,
            6_000_000L,
            100_000_000L,
            204_999_999L,
            205_000_000L,
            1_500_000_000L,
            2_500_500_000L,
            4_994_000_000L,
            4_996_000_000L,
            5_200_000_000L,
        )

        for (posUs in probePositions) {
            val linearCues = collectActiveSidecarCues(list, posUs, prefixMaxEndUs = null)
            val indexedCues = collectActiveSidecarCues(list, posUs, prefixMaxEndUs = prefixMax)
            assertEquals(
                "Active cues mismatch at posUs=$posUs",
                linearCues.map { it.text?.toString() },
                indexedCues.map { it.text?.toString() },
            )

            val linearTimed = collectActiveSidecarTimedCues(list, posUs, prefixMaxEndUs = null)
            val indexedTimed = collectActiveSidecarTimedCues(list, posUs, prefixMaxEndUs = prefixMax)
            assertEquals(
                "Active timed cues mismatch at posUs=$posUs",
                linearTimed.map { it.startTimeUs to it.cues.firstOrNull()?.text?.toString() },
                indexedTimed.map { it.startTimeUs to it.cues.firstOrNull()?.text?.toString() },
            )
        }
    }

    @Test
    fun `findNextSidecarCueStartMs skips blank cues and returns exact next cue`() {
        val cues = listOf(
            CuesWithTiming(listOf(cue("First")), 1_000_000L, 2_000_000L),
            CuesWithTiming(listOf(cue("   ")), 3_500_000L, 500_000L),
            CuesWithTiming(listOf(cue("Second")), 4_500_000L, 1_500_000L),
            CuesWithTiming(listOf(cue("Third")), 8_000_000L, 2_000_000L),
        )

        assertEquals(4_500L, findNextSidecarCueStartMs(cues, earliestStartUs = 1_000_000L, positionUs = 2_000_000L))
        assertEquals(8_000L, findNextSidecarCueStartMs(cues, earliestStartUs = 4_500_000L, positionUs = 5_000_000L))
        assertNull(findNextSidecarCueStartMs(cues, earliestStartUs = 8_000_000L, positionUs = 8_500_000L))
    }
}
