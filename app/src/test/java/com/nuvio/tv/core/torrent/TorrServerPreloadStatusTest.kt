package com.nuvio.tv.core.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrServerPreloadStatusTest {

    @Test
    fun `isPreloadReady is false when server is active at start but has no buffered bytes`() {
        // When server is already in active / Torrent working state at startup,
        // but has not buffered any bytes yet, it must NOT be marked ready.
        val status = TorrServerRemoteStatus(
            hash = "dummy_hash",
            title = "Test Torrent",
            stat = 3,
            statString = "Torrent working",
            downloadSpeed = 0L,
            uploadSpeed = 0L,
            preloadedBytes = 0L,
            preloadSize = 33_554_432L, // 32MB preload configured
            loadedSize = 0L,
            torrentSize = 1_000_000_000L,
            activePeers = 5,
            connectedSeeders = 3,
            totalPeers = 20,
            files = emptyList()
        )

        assertFalse("Should not be ready when server is active but preloadedBytes is 0", status.isPreloadReady)
        assertEquals(0f, status.preloadProgress, 0.001f)
    }

    @Test
    fun `isPreloadReady is false when server is active with unknown preloadSize and zero buffer`() {
        val status = TorrServerRemoteStatus(
            hash = "dummy_hash",
            title = "Test Torrent",
            stat = 3,
            statString = "Torrent working",
            downloadSpeed = 0L,
            uploadSpeed = 0L,
            preloadedBytes = 0L,
            preloadSize = 0L,
            loadedSize = 0L,
            torrentSize = 1_000_000_000L,
            activePeers = 5,
            connectedSeeders = 3,
            totalPeers = 20,
            files = emptyList()
        )

        assertFalse("Should not be ready when buffer is zero even if active", status.isPreloadReady)
        assertEquals(0f, status.preloadProgress, 0.001f)
    }

    @Test
    fun `isPreloadReady is true when preloadedBytes reaches 95 percent of preloadSize`() {
        val targetSize = 33_554_432L
        val status = TorrServerRemoteStatus(
            hash = "dummy_hash",
            title = "Test Torrent",
            stat = 3,
            statString = "Torrent working",
            downloadSpeed = 5_000_000L,
            uploadSpeed = 0L,
            preloadedBytes = (targetSize * 96 / 100),
            preloadSize = targetSize,
            loadedSize = (targetSize * 96 / 100),
            torrentSize = 1_000_000_000L,
            activePeers = 10,
            connectedSeeders = 8,
            totalPeers = 30,
            files = emptyList()
        )

        assertTrue("Should be ready when 96% of preload buffer is reached", status.isPreloadReady)
        assertEquals(1f, status.preloadProgress, 0.001f)
    }

    @Test
    fun `isPreloadReady is false when preloadedBytes is below 95 percent of preloadSize`() {
        val targetSize = 33_554_432L
        val status = TorrServerRemoteStatus(
            hash = "dummy_hash",
            title = "Test Torrent",
            stat = 2,
            statString = "Torrent preloading",
            downloadSpeed = 5_000_000L,
            uploadSpeed = 0L,
            preloadedBytes = (targetSize * 92 / 100),
            preloadSize = targetSize,
            loadedSize = (targetSize * 92 / 100),
            torrentSize = 1_000_000_000L,
            activePeers = 10,
            connectedSeeders = 8,
            totalPeers = 30,
            files = emptyList()
        )

        assertFalse("Should not be ready when 92% of preload buffer is reached (threshold is 95%)", status.isPreloadReady)
    }

    @Test
    fun `isPreloadReady is true when torrent was already cached on server`() {
        val targetSize = 33_554_432L
        val status = TorrServerRemoteStatus(
            hash = "dummy_hash",
            title = "Test Torrent",
            stat = 3,
            statString = "Torrent working",
            downloadSpeed = 0L,
            uploadSpeed = 0L,
            preloadedBytes = 0L,
            preloadSize = targetSize,
            loadedSize = 50_000_000L, // already has 50MB in cache
            torrentSize = 1_000_000_000L,
            activePeers = 10,
            connectedSeeders = 8,
            totalPeers = 30,
            files = emptyList()
        )

        assertTrue("Should be ready when server already has sufficient cached loadedSize", status.isPreloadReady)
        assertEquals(1f, status.preloadProgress, 0.001f)
    }

    @Test
    fun `preloadProgress calculates intermediate progress correctly`() {
        val targetSize = 33_554_432L
        val status = TorrServerRemoteStatus(
            hash = "dummy_hash",
            title = "Test Torrent",
            stat = 2,
            statString = "Torrent preloading",
            downloadSpeed = 2_000_000L,
            uploadSpeed = 0L,
            preloadedBytes = targetSize / 2, // 50%
            preloadSize = targetSize,
            loadedSize = targetSize / 2,
            torrentSize = 1_000_000_000L,
            activePeers = 8,
            connectedSeeders = 5,
            totalPeers = 25,
            files = emptyList()
        )

        assertFalse(status.isPreloadReady)
        assertEquals(0.5f, status.preloadProgress, 0.01f)
    }
}
