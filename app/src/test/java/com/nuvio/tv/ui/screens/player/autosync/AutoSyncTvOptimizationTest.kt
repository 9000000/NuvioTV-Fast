package com.nuvio.tv.ui.screens.player.autosync

import com.nuvio.tv.ui.screens.player.isLocalOrTorrentAutoSyncSource
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncTvOptimizationTest {

    private fun sampleResult(
        confident: Boolean = true,
        alignmentScale: Double = 1.0,
        alignmentInterceptMs: Double = 1_500.0,
        targetCoverage: Double = 0.92,
        referenceCoverage: Double = 0.85,
        averageGroupCost: Double = 0.14,
        medianGroupResidualMs: Double = 180.0,
    ): AutoSyncTimelineRetimeResult = AutoSyncTimelineRetimeResult(
        cues = listOf(
            AutoSyncRetimedCue(
                originalStartTimeMs = 10_000L,
                originalEndTimeMs = 12_000L,
                startTimeMs = (10_000L * alignmentScale + alignmentInterceptMs).toLong(),
                endTimeMs = (12_000L * alignmentScale + alignmentInterceptMs).toLong(),
            ),
            AutoSyncRetimedCue(
                originalStartTimeMs = 3_600_000L,
                originalEndTimeMs = 3_602_000L,
                startTimeMs = (3_600_000L * alignmentScale + alignmentInterceptMs).toLong(),
                endTimeMs = (3_602_000L * alignmentScale + alignmentInterceptMs).toLong(),
            ),
        ),
        groups = emptyList(),
        targetCoverage = targetCoverage,
        referenceCoverage = referenceCoverage,
        skippedTargetCues = 0,
        skippedReferenceCues = 0,
        longestTargetSkipRun = 0,
        averageGroupCost = averageGroupCost,
        oneToOneGroups = 100,
        oneToTwoGroups = 0,
        twoToOneGroups = 0,
        oneToThreeGroups = 0,
        threeToOneGroups = 0,
        twoToTwoGroups = 0,
        confident = confident,
        alignmentScale = alignmentScale,
        alignmentInterceptMs = alignmentInterceptMs,
        medianGroupResidualMs = medianGroupResidualMs,
    )

    @Test
    fun `sanity guard accepts normal delay shifts and high-confidence large shifts`() {
        assertTrue(sampleResult(alignmentInterceptMs = 3_500.0).passesTvSanityGuard())
        assertTrue(
            sampleResult(
                alignmentInterceptMs = 45_000.0,
                targetCoverage = 0.93,
                referenceCoverage = 0.86,
                averageGroupCost = 0.15,
                medianGroupResidualMs = 200.0,
            ).passesTvSanityGuard(),
        )
    }

    @Test
    fun `sanity guard rejects weak large shifts, extreme scale drift, and unconfident results`() {
        assertFalse(sampleResult(confident = false).passesTvSanityGuard())

        // Large 40s shift with poor coverage / high residual should be blocked
        assertFalse(
            sampleResult(
                alignmentInterceptMs = 40_000.0,
                targetCoverage = 0.72,
                referenceCoverage = 0.55,
                averageGroupCost = 0.32,
                medianGroupResidualMs = 950.0,
            ).passesTvSanityGuard(),
        )

        // > 5% speed distortion without exceptional fit should be blocked
        assertFalse(
            sampleResult(
                alignmentScale = 1.08,
                alignmentInterceptMs = 0.0,
                targetCoverage = 0.88,
                referenceCoverage = 0.80,
                averageGroupCost = 0.22,
            ).passesTvSanityGuard(),
        )
    }

    @Test
    fun `isLocalOrTorrentAutoSyncSource detects torrserver and local streams`() {
        assertTrue(isLocalOrTorrentAutoSyncSource("https://cdn.example.com/movie.mkv", isTorrentStream = true))
        assertTrue(isLocalOrTorrentAutoSyncSource("http://127.0.0.1:8090/stream/movie.mkv?link=abc", isTorrentStream = false))
        assertTrue(isLocalOrTorrentAutoSyncSource("http://localhost:8090/stream/movie.mkv", isTorrentStream = false))
        assertTrue(isLocalOrTorrentAutoSyncSource("http://192.168.1.55:8090/stream/movie.mkv", isTorrentStream = false))
        assertFalse(isLocalOrTorrentAutoSyncSource("https://real-debrid.com/d/abc123/movie.mkv", isTorrentStream = false))
    }

    @Test
    fun `autoSyncDispatcher executes off the caller thread on dedicated worker`() = runBlocking {
        val callerThread = Thread.currentThread().name
        val workerThread = withContext(AutomaticSubtitleSync.autoSyncDispatcher) {
            Thread.currentThread().name
        }
        assertTrue("Expected dedicated NuvioAutoSyncWorker thread, got $workerThread", workerThread == "NuvioAutoSyncWorker")
        assertTrue("Expected worker thread to differ from caller thread", workerThread != callerThread)
    }
}
