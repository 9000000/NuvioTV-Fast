package com.nuvio.tv.features.livetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader

class LiveTvOptimizationTests {

    @Test
    fun m3uStreamParser_readsFromBufferedReaderStream_successfully() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="vtv3" tvg-name="VTV3 HD" group-title="Entertainment", VTV3 HD
            https://stream.example.com/vtv3/playlist.m3u8
            #EXTINF:-1 tvg-id="vtv6" tvg-name="VTV6" group-title="Sports", VTV6
            https://stream.example.com/vtv6/playlist.m3u8
        """.trimIndent()

        val reader = BufferedReader(StringReader(m3uContent))
        val channels = M3uStreamParser.parse(reader)

        assertEquals(2, channels.size)
        assertEquals("VTV3 HD", channels[0].name)
        assertEquals("Entertainment", channels[0].group)
        assertEquals("VTV6", channels[1].name)
        assertEquals("Sports", channels[1].group)
    }

    @Test
    fun m3uStreamParser_filtersNoiseAndDividers() {
        val m3uContent = """
            #EXTM3U
            # ========================
            # --- VIETNAM CHANNELS ---
            # ========================
            #EXTINF:-1 tvg-name="VTV1", VTV1
            https://stream.example.com/vtv1.m3u8
            // Some noise line
            #EXTINF:-1 tvg-name="VTV2", VTV2
            https://stream.example.com/vtv2.m3u8
        """.trimIndent()

        val reader = BufferedReader(StringReader(m3uContent))
        val channels = M3uStreamParser.parse(reader)

        assertEquals(2, channels.size)
        assertEquals("VTV1", channels[0].name)
        assertEquals("VTV2", channels[1].name)
    }

    @Test
    fun searchFiltering_matchesNameAndGroup() {
        val channels = listOf(
            LiveTvChannel(id = "1", name = "HBO HD", streamUrl = "https://a.com", group = "Movies"),
            LiveTvChannel(id = "2", name = "Cinemax", streamUrl = "https://b.com", group = "Movies"),
            LiveTvChannel(id = "3", name = "Discovery", streamUrl = "https://c.com", group = "Documentary"),
            LiveTvChannel(id = "4", name = "Fox Sports", streamUrl = "https://d.com", group = "Sports")
        )

        // Search by channel name (case-insensitive)
        val nameQuery = "hbo"
        val nameMatches = channels.filter {
            it.name.contains(nameQuery, ignoreCase = true) || it.group?.contains(nameQuery, ignoreCase = true) == true
        }
        assertEquals(1, nameMatches.size)
        assertEquals("HBO HD", nameMatches[0].name)

        // Search by group name
        val groupQuery = "movies"
        val groupMatches = channels.filter {
            it.name.contains(groupQuery, ignoreCase = true) || it.group?.contains(groupQuery, ignoreCase = true) == true
        }
        assertEquals(2, groupMatches.size)
    }
}
