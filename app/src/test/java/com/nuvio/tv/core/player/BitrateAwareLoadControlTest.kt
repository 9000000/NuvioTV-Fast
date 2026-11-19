package com.nuvio.tv.core.player

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.DefaultAllocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

@UnstableApi
class BitrateAwareLoadControlTest {

    private val mb = 1024L * 1024L
    private val playerId = PlayerId("test")
    private val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)

    private fun params(bufferedMs: Long, speed: Float = 1f) = LoadControl.Parameters(
        playerId, Timeline.EMPTY, MediaPeriodId(Any()), 0L, bufferedMs * 1000L, speed,
        true, false, C.TIME_UNSET, C.TIME_UNSET
    )

    private fun loadControl(budgetMb: Long = 20) = BitrateAwareLoadControl(
        minBufferMs = 15_000,
        maxBufferMs = 45_000,
        bufferForPlaybackMs = 5_000,
        bufferForPlaybackAfterRebufferMs = 3_000,
        prioritizeTimeOverSizeThresholds = true,
        backBufferDurationMs = 0,
        retainBackBufferFromKeyframe = true,
        budgetBytes = budgetMb * mb,
        allocator = allocator,
    ).also {
        it.onPrepared(playerId)
        it.onTracksSelected(params(0), TrackGroupArray.EMPTY, arrayOf<ExoTrackSelection?>())
    }

    private fun fillMb(count: Long) =
        repeat((count * mb / C.DEFAULT_BUFFER_SEGMENT_SIZE).toInt()) { allocator.allocate() }

    @Test
    fun `time comes first a little past the budget`() {
        val lc = loadControl()
        fillMb(22)
        assertTrue(lc.shouldContinueLoading(params(bufferedMs = 10_000)))
    }

    @Test
    fun `loading stops a quarter past the budget once playback can start`() {
        val lc = loadControl()
        fillMb(26)
        assertFalse(lc.shouldContinueLoading(params(bufferedMs = 6_000)))
        assertTrue(lc.shouldStartPlayback(params(bufferedMs = 6_000, speed = 2f)))
    }

    @Test
    fun `loading goes on past the ceiling while too little is buffered to play`() {
        val lc = loadControl()
        fillMb(26)
        assertTrue(lc.shouldContinueLoading(params(bufferedMs = 2_000)))
    }

    @Test
    fun `a lowered budget lowers the ceiling`() {
        val lc = loadControl(budgetMb = 40)
        fillMb(26)
        assertTrue(lc.shouldContinueLoading(params(bufferedMs = 6_000)))
        lc.setBudgetBytesOverride(20 * mb)
        assertFalse(lc.shouldContinueLoading(params(bufferedMs = 6_000)))
    }

    @Test
    fun `the ceiling stays below the allocator's 2 GiB int limit on 8 GB boxes`() {
        val intLimit = Int.MAX_VALUE.toLong()
        val lc = loadControl(budgetMb = 2000)
        assertEquals(2000 * mb, lc.ceilingBytes())
        assertTrue(lc.ceilingBytes() < intLimit)
        lc.setBudgetBytesOverride(2500 * mb)
        assertEquals(2000 * mb, lc.ceilingBytes())
        lc.setBudgetBytesOverride(1000 * mb)
        assertEquals(1250 * mb, lc.ceilingBytes())
    }

    private class FakeInternalPlayer(@JvmField val backBufferDurationUs: Long)

    @Test
    fun `back buffer starts at configured value and lowers to 0 on override including internalPlayer`() {
        val lc = BitrateAwareLoadControl(
            minBufferMs = 15_000,
            maxBufferMs = 50_000,
            bufferForPlaybackMs = 2_500,
            bufferForPlaybackAfterRebufferMs = 5_000,
            prioritizeTimeOverSizeThresholds = true,
            backBufferDurationMs = 15_000,
            retainBackBufferFromKeyframe = true,
            budgetBytes = 100L * 1024L * 1024L
        )

        assertEquals(15_000_000L, lc.getBackBufferDurationUs(PlayerId.UNSET))

        val fakeInternal = FakeInternalPlayer(backBufferDurationUs = 15_000_000L)
        val fakePlayer = object : ExoPlayer by (Proxy.newProxyInstance(
            ExoPlayer::class.java.classLoader,
            arrayOf(ExoPlayer::class.java)
        ) { _, method, _ ->
            if (method.name == "getPlaybackLooper") null else null
        } as ExoPlayer) {
            @Suppress("unused")
            private val internalPlayer = fakeInternal
        }

        lc.setBackBufferDurationOverrideMs(0, fakePlayer)

        assertEquals(0L, lc.getBackBufferDurationUs(PlayerId.UNSET))
        val updatedInternalUs = FakeInternalPlayer::class.java
            .getDeclaredField("backBufferDurationUs")
            .apply { isAccessible = true }
            .getLong(fakeInternal)
        assertEquals(0L, updatedInternalUs)
    }

    @Test
    fun `setBudgetBytesOverride updates active loadingStates after onTracksSelected`() {
        val initialBudget = 100 * 1024 * 1024
        val shrunkBudget = 55 * 1024 * 1024
        val lc = BitrateAwareLoadControl(
            minBufferMs = 15_000,
            maxBufferMs = 50_000,
            bufferForPlaybackMs = 2_500,
            bufferForPlaybackAfterRebufferMs = 5_000,
            prioritizeTimeOverSizeThresholds = true,
            backBufferDurationMs = 15_000,
            retainBackBufferFromKeyframe = true,
            budgetBytes = initialBudget.toLong()
        )

        val playerId = PlayerId.UNSET
        lc.onPrepared(playerId)
        val params = LoadControl.Parameters(
            playerId,
            Timeline.EMPTY,
            MediaSource.MediaPeriodId(Any()),
            /* playbackPositionUs= */ 0L,
            /* bufferedDurationUs= */ 0L,
            /* playbackSpeed= */ 1f,
            /* playWhenReady= */ true,
            /* rebuffering= */ false,
            /* targetLiveOffsetUs= */ C.TIME_UNSET,
            /* lastRebufferRealtimeMs= */ C.TIME_UNSET
        )
        lc.onTracksSelected(params, TrackGroupArray.EMPTY, emptyArray<ExoTrackSelection?>())

        val calcTotalMethod = DefaultLoadControl::class.java
            .getDeclaredMethod("calculateTotalTargetBufferBytes")
            .apply { isAccessible = true }

        assertEquals(initialBudget, calcTotalMethod.invoke(lc) as Int)

        lc.setBudgetBytesOverride(shrunkBudget.toLong())

        assertEquals(shrunkBudget, calcTotalMethod.invoke(lc) as Int)
    }
}
