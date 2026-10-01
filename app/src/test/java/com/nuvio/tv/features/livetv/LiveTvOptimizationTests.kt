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

    @Test
    fun chipIndexCalculation_resolvesCorrectPositions() {
        val allGroups = listOf("News", "Sports", "Movies")
        val hasRecent = true
        val selectedPlaylistId = "playlist_1"

        // Helper replicating getActiveChipIndex logic
        fun calculateIndex(filter: String, group: String?): Int {
            val isAll = (filter == "ALL" || filter == "PLAYLIST") && group == null
            return when {
                isAll -> 1
                filter == "RECENT" && hasRecent -> 2
                filter == "FAVORITES" -> if (hasRecent) 3 else 2
                filter == "GROUP" -> {
                    val groupIdx = group?.let { allGroups.indexOf(it) } ?: -1
                    val baseIndex = 2 + (if (hasRecent) 1 else 0) + 1
                    if (groupIdx >= 0) baseIndex + groupIdx else 1
                }
                else -> 0
            }
        }

        assertEquals(1, calculateIndex("PLAYLIST", null))
        assertEquals(2, calculateIndex("RECENT", null))
        assertEquals(3, calculateIndex("FAVORITES", null))
        // Group "News" should be index 4 (item 0: dropdown, 1: all, 2: recent, 3: fav, 4: News)
        assertEquals(4, calculateIndex("GROUP", "News"))
        // Group "Sports" should be index 5
        assertEquals(5, calculateIndex("GROUP", "Sports"))
        // Group "Movies" should be index 6
        assertEquals(6, calculateIndex("GROUP", "Movies"))
    }

    @Test
    fun liveTvUiState_storesLastWatchedContext() {
        val state = LiveTvUiState(
            lastWatchedChannelId = "ch_123",
            lastWatchedPlaylistId = "pl_456",
            lastWatchedGroup = "Sports",
            lastWatchedFilterType = "GROUP"
        )

        assertEquals("ch_123", state.lastWatchedChannelId)
        assertEquals("pl_456", state.lastWatchedPlaylistId)
        assertEquals("Sports", state.lastWatchedGroup)
        assertEquals("GROUP", state.lastWatchedFilterType)
    }
}
