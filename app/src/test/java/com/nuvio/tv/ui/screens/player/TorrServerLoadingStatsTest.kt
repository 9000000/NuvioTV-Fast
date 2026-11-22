package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrServerLoadingStatsTest {

    @Test
    fun `cleanTorrServerStatString extracts percentage and removes seed peer info`() {
        assertEquals("45%", cleanTorrServerStatString("Preload: 45% (peers: 12, seeds: 5)"))
        assertEquals("90%", cleanTorrServerStatString("Torrent preloading: 90%"))
        assertEquals("100%", cleanTorrServerStatString("100%"))
        assertEquals("0%", cleanTorrServerStatString("0%"))
    }

    @Test
    fun `cleanTorrServerStatString strips seed and peer tokens when no percentage present`() {
        val result = cleanTorrServerStatString("Connecting to peers: 12 seeds, 34 peers")
        assertEquals("Connecting to", result)

        val result2 = cleanTorrServerStatString("peers: 5, seeds: 2")
        assertNull(result2)
    }

    @Test
    fun `cleanTorrServerStatString handles null and blank strings`() {
        assertNull(cleanTorrServerStatString(null))
        assertNull(cleanTorrServerStatString(""))
        assertNull(cleanTorrServerStatString("   "))
    }
}
