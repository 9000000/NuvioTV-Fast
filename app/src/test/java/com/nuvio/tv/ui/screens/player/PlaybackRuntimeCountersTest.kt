package com.nuvio.tv.ui.screens.player

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlaybackRuntimeCountersTest {

    private val status = """
        Name:	com.nuvio.tv.test
        VmPeak:	 4194304 kB
        VmRSS:	  421888 kB
        RssAnon:	  300000 kB
        VmSwap:	   38400 kB
        Threads:	97
    """.trimIndent()

    private fun totals(count: Long, timeMs: Long, blocking: Long, blockingMs: Long, allocMb: Long) =
        PlaybackRuntimeCounters.GcTotals(count, timeMs, blocking, blockingMs, allocMb * 1024L * 1024L)

    @Before fun setUp() = PlaybackRuntimeCounters.resetForTest()
    @After fun tearDown() = PlaybackRuntimeCounters.resetForTest()

    @Test fun readsKilobytesFromProcStatus() {
        assertEquals(421888L, PlaybackRuntimeCounters.procStatusKb(status, "VmRSS"))
        assertEquals(38400L, PlaybackRuntimeCounters.procStatusKb(status, "VmSwap"))
        assertNull(PlaybackRuntimeCounters.procStatusKb(status, "VmPTE"))
        assertNull(PlaybackRuntimeCounters.procStatusKb(null, "VmRSS"))
        assertNull(PlaybackRuntimeCounters.procStatusKb("VmRSS:\tlots kB", "VmRSS"))
    }

    @Test fun firstLineHasMemoryOnly() {
        assertEquals(", rssMb=412, swapMb=37", PlaybackRuntimeCounters.fields(totals(10, 200, 1, 30, 500), status, 1_000L))
    }

    @Test fun laterLinesCarryTheChangeSinceThePreviousOne() {
        PlaybackRuntimeCounters.fields(totals(10, 200, 1, 30, 500), status, 1_000L)
        assertEquals(
            ", gc=3, gcMs=45, blockingGc=1, blockingGcMs=120, allocMb=96, rssMb=412, swapMb=37",
            PlaybackRuntimeCounters.fields(totals(13, 245, 2, 150, 596), status, 11_000L)
        )
        assertEquals(
            ", gc=0, gcMs=0, blockingGc=0, blockingGcMs=0, allocMb=0, rssMb=412, swapMb=37",
            PlaybackRuntimeCounters.fields(totals(13, 245, 2, 150, 596), status, 21_000L)
        )
    }

    @Test fun aLongGapStartsOver() {
        PlaybackRuntimeCounters.fields(totals(10, 200, 1, 30, 500), status, 1_000L)
        val afterPause = PlaybackRuntimeCounters.fields(totals(40, 900, 3, 90, 2000), status, 1_000L + 60_000L)
        assertEquals(", rssMb=412, swapMb=37", afterPause)
        assertEquals(
            ", gc=1, gcMs=5, blockingGc=0, blockingGcMs=0, allocMb=4, rssMb=412, swapMb=37",
            PlaybackRuntimeCounters.fields(totals(41, 905, 3, 90, 2004), status, 71_000L)
        )
    }

    @Test fun missingSourcesLeaveTheirFieldsOut() {
        assertEquals("", PlaybackRuntimeCounters.fields(null, null, 1_000L))
        PlaybackRuntimeCounters.fields(totals(1, 1, 0, 0, 1), null, 2_000L)
        assertEquals(
            ", gc=1, gcMs=1, blockingGc=0, blockingGcMs=0, allocMb=1",
            PlaybackRuntimeCounters.fields(totals(2, 2, 0, 0, 2), null, 12_000L)
        )
        assertEquals(", rssMb=412", PlaybackRuntimeCounters.fields(null, "VmRSS:\t421888 kB", 22_000L))
    }

    @Test fun counterResetNeverGoesNegative() {
        PlaybackRuntimeCounters.fields(totals(10, 200, 1, 30, 500), null, 1_000L)
        assertEquals(
            ", gc=0, gcMs=0, blockingGc=0, blockingGcMs=0, allocMb=0",
            PlaybackRuntimeCounters.fields(totals(2, 20, 0, 0, 10), null, 11_000L)
        )
    }
}
