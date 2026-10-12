package com.nuvio.tv.core.player

import android.content.ComponentCallbacks2
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocation
import androidx.media3.exoplayer.upstream.DefaultAllocator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferMemoryKeeperTest {

    private val mb = 1024L * 1024L
    private val segment = C.DEFAULT_BUFFER_SEGMENT_SIZE
    private val playerId = PlayerId("test")
    private val allocator = DefaultAllocator(true, segment, 0, false)
    private var now = 1_000_000L
    private var free: BufferSizing.FreeMemory? = BufferSizing.FreeMemory(800 * mb, 130 * mb, false)
    private val delayed = mutableListOf<() -> Unit>()

    private val worker = object : BufferMemoryKeeper.Worker {
        override fun run(task: () -> Unit) = task()
        override fun runLater(delayMs: Long, task: () -> Unit) {
            delayed += task
        }
    }

    private fun params(bufferedMs: Long) = LoadControl.Parameters(
        playerId, Timeline.EMPTY, MediaPeriodId(Any()), 0L, bufferedMs * 1000L, 1f,
        true, false, C.TIME_UNSET, C.TIME_UNSET
    )

    private fun customControl(budgetMb: Long = 100) = BitrateAwareLoadControl(
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

    private fun performanceControl(
        budgetMb: Int = 100,
        minBufferMs: Int = 15_000,
        maxBufferMs: Int = 45_000,
        backBufferMs: Int = 0,
    ) = ReservableLoadControl(
        allocator = allocator,
        minBufferMs = minBufferMs,
        maxBufferMs = maxBufferMs,
        bufferForPlaybackMs = 3_000,
        bufferForPlaybackAfterRebufferMs = 3_000,
        targetBufferBytes = budgetMb * 1024 * 1024,
        prioritizeTimeOverSizeThresholds = false,
        backBufferDurationMs = backBufferMs,
        retainBackBufferFromKeyframe = true,
    ).also {
        it.onPrepared(playerId)
        it.onTracksSelected(params(0), TrackGroupArray.EMPTY, arrayOf<ExoTrackSelection?>())
    }

    private fun keeper(control: BufferBudgetControl) =
        BufferMemoryKeeper(control, { free }, { now }, worker)

    private fun take(megabytes: Long): List<Allocation> =
        List((megabytes * mb / segment).toInt()) { allocator.allocate() }

    private fun giveBack(allocations: List<Allocation>) = allocations.forEach(allocator::release)

    @Test
    fun `a seek gives back what the new position does not use`() {
        val k = keeper(customControl())
        val old = take(60)
        giveBack(old)
        take(10)
        assertEquals(60 * mb, allocator.memoryFootprint.toLong())
        k.onSeek()
        now += 1_000
        k.onSeek()
        assertEquals(1, delayed.size)
        now += BufferMemoryKeeper.SEEK_SETTLE_MS
        delayed.removeAt(0)()
        assertEquals(10 * mb, allocator.totalBytesAllocated.toLong())
        assertTrue(allocator.availableBytes <= BufferMemoryKeeper.KEEP_IDLE_BYTES)
    }

    @Test
    fun `a seek that is still settling waits`() {
        val k = keeper(customControl())
        giveBack(take(40))
        k.onSeek()
        now += 500
        delayed.removeAt(0)()
        assertEquals(40 * mb, allocator.availableBytes.toLong())
        assertEquals(1, delayed.size)
        now += BufferMemoryKeeper.SEEK_SETTLE_MS
        delayed.removeAt(0)()
        assertTrue(allocator.availableBytes <= BufferMemoryKeeper.KEEP_IDLE_BYTES)
    }

    @Test
    fun `running low lowers the budget and the idle pool`() {
        val lc = customControl(budgetMb = 100)
        val k = keeper(lc)
        val held = take(70)
        giveBack(held.take(320))
        k.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(BufferMemoryKeeper.LEVEL_LOW, k.pressureLevel)
        assertEquals(60 * mb, k.capBytes(k.pressureLevel))
        assertTrue(allocator.availableBytes <= BufferMemoryKeeper.KEEP_IDLE_BYTES)
        // Capped at 60 MB, loading stops a quarter over it (75 MB).
        take(30)
        assertFalse(lc.shouldContinueLoading(params(bufferedMs = 6_000)))
        assertEquals(100 * mb, lc.uncappedBudgetBytes)
    }

    @Test
    fun `critical cuts deeper but not below the floor`() {
        val k = keeper(customControl(budgetMb = 120))
        k.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(BufferMemoryKeeper.LEVEL_CRITICAL, k.pressureLevel)
        assertEquals(50 * mb, k.capBytes(BufferMemoryKeeper.LEVEL_CRITICAL))
        val big = keeper(customControl(budgetMb = 1000))
        assertEquals(350 * mb, big.capBytes(BufferMemoryKeeper.LEVEL_CRITICAL))
    }

    @Test
    fun `hiding the ui only gives idle memory back`() {
        val k = keeper(customControl())
        giveBack(take(30))
        k.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(BufferMemoryKeeper.LEVEL_NONE, k.pressureLevel)
        assertTrue(allocator.availableBytes <= BufferMemoryKeeper.KEEP_IDLE_BYTES)
    }

    @Test
    fun `performance mode stops loading at the lowered budget once 5 s are ahead, and may start playing`() {
        val lc = performanceControl(budgetMb = 100)
        val k = keeper(lc)
        take(65)
        assertTrue(lc.shouldContinueLoading(params(bufferedMs = 6_000)))
        k.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertTrue(lc.shouldContinueLoading(params(bufferedMs = 2_000)))
        assertFalse(lc.shouldContinueLoading(params(bufferedMs = 6_000)))
        assertTrue(lc.shouldStartPlayback(params(bufferedMs = 6_000)))
    }

    @Test
    fun `a 12 s back buffer at 80 Mbit s in a small target never stops loading before 5 s are ahead`() {
        // Native memory and Custom playback buffers on (managed) on a 2 GB box with apps in the background: the
        // target is sized to 62 MB, the back buffer is 12 s, about 120 MB of an 80 Mbit/s remux, and Android
        // reports memory pressure.
        val lc = performanceControl(budgetMb = 62, minBufferMs = 200_000, maxBufferMs = 280_000, backBufferMs = 12_000)
        val k = keeper(lc)
        take(120)
        k.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(50 * mb, k.capBytes(k.pressureLevel))
        // media3 warns below 500 ms ahead, and its log cannot print in unit tests.
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
        try {
            for (aheadMs in listOf(0L, 400L, 2_000L, 3_000L, 4_900L)) {
                assertTrue("$aheadMs ms ahead", lc.shouldContinueLoading(params(bufferedMs = aheadMs)))
            }
        } finally {
            androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_ALL)
        }
        take(50)
        assertFalse(lc.shouldContinueLoading(params(bufferedMs = 5_000)))
        assertTrue(lc.shouldStartPlayback(params(bufferedMs = 5_000)))
        // Without pressure the full target alone does not stop it either.
        val calm = performanceControl(budgetMb = 62, minBufferMs = 200_000, maxBufferMs = 280_000, backBufferMs = 12_000)
        assertTrue(calm.shouldContinueLoading(params(bufferedMs = 2_000)))
        assertFalse(calm.shouldContinueLoading(params(bufferedMs = 5_000)))
        // The back buffer the app builds for this target is cut to leave 5 s ahead.
        assertEquals(1_200, BufferSizing.backBufferMsForTarget(12_000, 62 * mb))
    }

    @Test
    fun `the budget comes back once the box has room again`() {
        val lc = customControl(budgetMb = 100)
        val k = keeper(lc)
        k.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        now += BufferMemoryKeeper.RESTORE_QUIET_MS - 1
        k.check()
        assertEquals(BufferMemoryKeeper.LEVEL_CRITICAL, k.pressureLevel)
        now += 1
        k.check()
        assertEquals(BufferMemoryKeeper.LEVEL_LOW, k.pressureLevel)
        now += BufferMemoryKeeper.RESTORE_QUIET_MS
        k.check()
        assertEquals(BufferMemoryKeeper.LEVEL_NONE, k.pressureLevel)
        take(110)
        assertTrue(lc.shouldContinueLoading(params(bufferedMs = 6_000)))
    }

    @Test
    fun `no room means the budget stays down`() {
        val k = keeper(customControl(budgetMb = 100))
        k.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        free = BufferSizing.FreeMemory(250 * mb, 130 * mb, false)
        now += BufferMemoryKeeper.RESTORE_QUIET_MS
        k.check()
        assertEquals(BufferMemoryKeeper.LEVEL_LOW, k.pressureLevel)
    }

    @Test
    fun `android saying memory is low has the same effect, and cuts deeper if it lasts`() {
        val k = keeper(customControl(budgetMb = 100))
        free = BufferSizing.FreeMemory(200 * mb, 130 * mb, true)
        k.onTick()
        assertEquals(BufferMemoryKeeper.LEVEL_LOW, k.pressureLevel)
        now += 10_000
        k.onTick()
        assertEquals(BufferMemoryKeeper.LEVEL_LOW, k.pressureLevel)
        now += BufferMemoryKeeper.ESCALATE_AFTER_MS
        k.onTick()
        assertEquals(BufferMemoryKeeper.LEVEL_CRITICAL, k.pressureLevel)
    }

    @Test
    fun `trim signals reach the player through the app's one callback`() {
        val k = keeper(customControl())
        k.attach()
        try {
            MemoryPressure.dispatch(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
            assertEquals(BufferMemoryKeeper.LEVEL_NONE, k.pressureLevel)
            MemoryPressure.dispatch(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            assertEquals(BufferMemoryKeeper.LEVEL_LOW, k.pressureLevel)
        } finally {
            k.detach()
        }
        MemoryPressure.dispatch(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        assertEquals(BufferMemoryKeeper.LEVEL_LOW, k.pressureLevel)
    }

    @Test
    fun `a lowered budget shrinks the pool`() {
        val lc = customControl(budgetMb = 100)
        val k = keeper(lc)
        giveBack(take(80))
        lc.setBudgetBytesOverride(40 * mb)
        k.onBudgetLowered()
        assertTrue(allocator.availableBytes <= BufferMemoryKeeper.KEEP_IDLE_BYTES)
    }
}
