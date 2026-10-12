package com.nuvio.tv.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferSizingTest {

    private val mb = 1024L * 1024L
    private val gb = 1024L * mb

    private fun free(availMb: Long, thresholdMb: Long, low: Boolean = false) =
        BufferSizing.FreeMemory(availMb * mb, thresholdMb * mb, low)

    @Test
    fun `one size per box`() {
        assertEquals(150, BufferSizing.autoBufferMbForRam(0L))
        assertEquals(100, BufferSizing.autoBufferMbForRam((0.9 * gb).toLong()))
        assertEquals(125, BufferSizing.autoBufferMbForRam((1.3 * gb).toLong()))
        assertEquals(150, BufferSizing.autoBufferMbForRam((1.9 * gb).toLong()))
        assertEquals(500, BufferSizing.autoBufferMbForRam((2.8 * gb).toLong()))
        assertEquals(1000, BufferSizing.autoBufferMbForRam((3.8 * gb).toLong()))
        assertEquals(1600, BufferSizing.autoBufferMbForRam((5.6 * gb).toLong()))
        assertEquals(2000, BufferSizing.autoBufferMbForRam(8 * gb))
    }

    @Test
    fun `small boxes default to two connections with native memory`() {
        assertEquals(2, BufferSizing.defaultConnectionCount((1.9 * gb).toLong(), nativeMemory = true, fallback = 2))
        assertEquals(2, BufferSizing.defaultConnectionCount(0L, nativeMemory = true, fallback = 2))
        assertEquals(4, BufferSizing.defaultConnectionCount((2.8 * gb).toLong(), nativeMemory = true, fallback = 2))
        assertEquals(4, BufferSizing.defaultConnectionCount((3.8 * gb).toLong(), nativeMemory = true, fallback = 2))
        assertEquals(2, BufferSizing.defaultConnectionCount((3.8 * gb).toLong(), nativeMemory = false, fallback = 2))
        assertTrue(BufferSizing.isSmallBox((2.2 * gb).toLong()))
        assertFalse(BufferSizing.isSmallBox((2.4 * gb).toLong()))
    }

    private data class Row(val boxMb: Int, val availMb: Long, val thresholdMb: Long, val windowMb: Int, val heldMb: Int, val expectMb: Int)

    @Test
    fun `size follows the memory that is free`() {
        val rows = listOf(
            // Fire TV Stick 4K Max, other apps stopped: room for the whole 2 GB size.
            Row(150, 700, 130, 96, 0, 150),
            // Same box with apps in the background (MemAvailable 425 MB measured idle): the floor.
            Row(150, 425, 130, 96, 0, 50),
            // Somewhere between: what is spare.
            Row(150, 600, 130, 96, 0, 62),
            // Previous player still holds 80 MB that comes back.
            Row(150, 600, 130, 96, 80, 142),
            // AM9 Pro with plenty free: the 4 GB size.
            Row(1000, 2400, 220, 192, 0, 1000),
            // AM9 Pro with 1.4 GB free.
            Row(1000, 1400, 220, 192, 0, 676),
            // Custom buffers keep the chunks inside the budget, so no window outside it.
            Row(150, 520, 130, 0, 0, 78),
        )
        for (r in rows) {
            val sized = BufferSizing.sizedBudgetMb(r.boxMb, free(r.availMb, r.thresholdMb), r.windowMb, r.heldMb)
            assertEquals(r.toString(), r.expectMb, sized)
        }
    }

    @Test
    fun `low memory starts at the floor`() {
        assertEquals(50, BufferSizing.sizedBudgetMb(1000, free(2400, 220, low = true), 0, 0))
    }

    @Test
    fun `unknown free memory keeps the box size`() {
        assertEquals(150, BufferSizing.sizedBudgetMb(150, null, 96, 0))
    }

    @Test
    fun `the floor never raises a smaller size`() {
        assertEquals(40, BufferSizing.sizedBudgetMb(40, free(300, 130), 0, 0))
    }

    @Test
    fun `the back buffer always leaves 5 s ahead at 80 Mbit s`() {
        assertEquals(0, BufferSizing.backBufferMsForTarget(12_000, 50 * mb))
        assertEquals(1_200, BufferSizing.backBufferMsForTarget(12_000, 62 * mb))
        assertEquals(5_000, BufferSizing.backBufferMsForTarget(12_000, 150 * mb))
        assertEquals(12_000, BufferSizing.backBufferMsForTarget(12_000, 1000 * mb))
        assertEquals(1_500, BufferSizing.backBufferMsForTarget(1_500, 150 * mb))
        assertEquals(0, BufferSizing.backBufferMsForTarget(0, 1000 * mb))
        val bytesPerSecond = 10 * mb
        for (targetMb in 50L..2000L) {
            val backMs = BufferSizing.backBufferMsForTarget(12_000, targetMb * mb)
            val backBytes = backMs * bytesPerSecond / 1000
            val aheadBytes = BufferSizing.MIN_AHEAD_MS * bytesPerSecond / 1000
            assertTrue("target $targetMb MB", backBytes + aheadBytes <= targetMb * mb)
            assertTrue("target $targetMb MB", backBytes <= targetMb * mb / 3)
        }
    }
}
