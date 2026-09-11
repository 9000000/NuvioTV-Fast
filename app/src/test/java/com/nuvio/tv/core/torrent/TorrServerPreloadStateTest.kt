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
}