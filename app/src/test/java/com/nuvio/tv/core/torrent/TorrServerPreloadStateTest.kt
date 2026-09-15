package com.nuvio.tv.core.torrent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrServerPreloadStateTest {
    @Test
    fun `active status releases playback before preload size is reported`() {
        val state = TorrentState.Streaming(
            localUrl = "http://127.0.0.1:8090/stream",
            downloadSpeed = 0L,
            uploadSpeed = 0L,
            peers = 0,
            seeds = 0,
            bufferProgress = 1f,
            totalProgress = 1f,
            stat = 2,
            statString = "active"
        )

        assertTrue(state.isPreloadReady)
    }

    @Test
    fun `unrelated status does not release playback`() {
        val state = TorrentState.Streaming(
            localUrl = "http://127.0.0.1:8090/stream",
            downloadSpeed = 0L,
            uploadSpeed = 0L,
            peers = 0,
            seeds = 0,
            bufferProgress = 0.99f,
            totalProgress = 0.99f,
            stat = 2,
            statString = "preload"
        )

        assertFalse(state.isPreloadReady)
    }

    @Test
    fun `torrent working status releases playback`() {
        val state = TorrentState.Streaming(
            localUrl = "http://127.0.0.1:8090/stream",
            downloadSpeed = 0L,
            uploadSpeed = 0L,
            peers = 0,
            seeds = 0,
            bufferProgress = 0f,
            totalProgress = 0f,
            stat = 3,
            statString = "Torrent working"
        )

        assertTrue(state.isPreloadReady)
    }

    @Test
    fun `preload 95 percent threshold releases playback`() {
        val state = TorrentState.Streaming(
            localUrl = "http://127.0.0.1:8090/stream",
            downloadSpeed = 1024L,
            uploadSpeed = 0L,
            peers = 5,
            seeds = 5,
            bufferProgress = 0.96f,
            totalProgress = 0.96f,
            preloadedBytes = 96_000_000L,
            preloadSize = 100_000_000L,
            stat = 2,
            statString = "Torrent preload"
        )

        assertTrue(state.isPreloadReady)
    }

    @Test
    fun `preload below threshold does not release playback`() {
        val state = TorrentState.Streaming(
            localUrl = "http://127.0.0.1:8090/stream",
            downloadSpeed = 1024L,
            uploadSpeed = 0L,
            peers = 5,
            seeds = 5,
            bufferProgress = 0.5f,
            totalProgress = 0.5f,
            preloadedBytes = 50_000_000L,
            preloadSize = 100_000_000L,
            stat = 2,
            statString = "Torrent preload"
        )

        assertFalse(state.isPreloadReady)
    }
}