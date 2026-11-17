package com.nuvio.tv.features.livetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTvGuideNavigationTest {

    private fun channel(
        id: String,
        playlistId: String? = "p1",
        group: String? = "Sports",
        name: String = "Channel $id"
    ) = LiveTvChannel(
        id = id,
        name = name,
        streamUrl = "https://example.com/$id.m3u8",
        playlistId = playlistId,
        group = group
    )

    @Test
    fun `buildLiveTvPlaylistOptions lists All first followed by enabled sources with channels`() {
        val ui = LiveTvUiState(
            playlists = listOf(
                LiveTvPlaylist(id = "p1", name = "VTVcab", type = LiveTvPlaylistType.Url, source = "https://a", isEnabled = true),
                LiveTvPlaylist(id = "p2", name = "Empty", type = LiveTvPlaylistType.Url, source = "https://b", isEnabled = true),
                LiveTvPlaylist(id = "p3", name = "Disabled", type = LiveTvPlaylistType.Url, source = "https://c", isEnabled = false)
            ),
            xtreamSettings = LiveTvXtreamSettings(serverUrl = "http://x", username = "u", password = "p", isEnabled = true),
            stalkerSettings = LiveTvStalkerSettings(portalUrl = "http://s", macAddress = "00:11", isEnabled = true),
            channels = listOf(
                channel("1", playlistId = "p1"),
                channel("2", playlistId = "p1"),
                channel("3", playlistId = "p3"),
                channel("4", playlistId = XTREAM_PLAYLIST_ID),
                channel("5", playlistId = STALKER_PLAYLIST_ID)
            )
        )

        val options = buildLiveTvPlaylistOptions(ui, allPlaylistsLabel = "Tất cả list kênh")

        assertEquals(listOf(null, "p1", XTREAM_PLAYLIST_ID, STALKER_PLAYLIST_ID), options.map { it.id })
        assertEquals(5, options.first().channelCount)
        assertEquals("Tất cả list kênh", options.first().name)
        assertEquals(2, options[1].channelCount)
    }

    @Test
    fun `scopeChannelsByPlaylist shows all when null and filters when selected`() {
        val channels = listOf(
            channel("1", playlistId = "p1"),
            channel("2", playlistId = "p2"),
            channel("3", playlistId = "p1")
        )

        assertEquals(3, scopeChannelsByPlaylist(channels, null).size)
        assertEquals(listOf("2"), scopeChannelsByPlaylist(channels, "p2").map { it.id })
    }

    @Test
    fun `resolveInitialGroupId falls back to recent or all when favorites empty`() {
        val groups = buildLiveTvChannelGroups(
            scopedChannels = listOf(channel("1"), channel("2")),
            favoriteIds = emptySet(),
            recentIds = listOf("2"),
            favoritesLabel = "Favorites",
            recentLabel = "Recent",
            allLabel = "All"
        )
        assertEquals(ChannelGroup.RECENT, resolveInitialGroupId(groups, favoriteCount = 0, recentCount = 1))
        assertEquals(ChannelGroup.ALL, resolveInitialGroupId(groups, favoriteCount = 0, recentCount = 0))
        assertEquals(ChannelGroup.FAVORITES, resolveInitialGroupId(groups, favoriteCount = 2, recentCount = 1))
    }

    @Test
    fun `resolveValidGroupId falls back to all when previous group absent after playlist change`() {
        val groups = buildLiveTvChannelGroups(
            scopedChannels = listOf(channel("1", group = "News")),
            favoriteIds = emptySet(),
            recentIds = emptyList(),
            favoritesLabel = "Favorites",
            recentLabel = "Recent",
            allLabel = "All"
        )
        assertEquals("custom:News", resolveValidGroupId(groups, "custom:News"))
        assertEquals(ChannelGroup.ALL, resolveValidGroupId(groups, "custom:Sports"))
    }

    @Test
    fun `grid navigation moves across rows and handles partial last row`() {
        // 10 items in 4 columns -> row0: 0..3, row1: 4..7, row2: 8..9
        assertNull(moveGridUp(index = 2, cols = 4))
        assertEquals(2, moveGridUp(index = 6, cols = 4))

        assertEquals(6, moveGridDown(index = 2, totalCount = 10, cols = 4))
        // Index 7 (row 1 col 3) has no item directly below at index 11, so it clamps to last item 9.
        assertEquals(9, moveGridDown(index = 7, totalCount = 10, cols = 4))
        assertNull(moveGridDown(index = 8, totalCount = 10, cols = 4))

        assertEquals(GridMoveResult.Boundary, moveGridLeft(index = 4, cols = 4))
        assertEquals(GridMoveResult.MoveTo(4), moveGridLeft(index = 5, cols = 4))

        assertEquals(GridMoveResult.Boundary, moveGridRight(index = 3, totalCount = 10, cols = 4))
        assertEquals(GridMoveResult.Boundary, moveGridRight(index = 9, totalCount = 10, cols = 4))
        assertEquals(GridMoveResult.MoveTo(9), moveGridRight(index = 8, totalCount = 10, cols = 4))
    }

    @Test
    fun `filterVerticalEpgPrograms retains recent catchup window and selects live index`() {
        val now = 1_000_000_000L
        val programs = listOf(
            EpgProgram(now - 6 * 3600_000L, now - 5 * 3600_000L, "Too Old"),
            EpgProgram(now - 2 * 3600_000L, now - 3600_000L, "Past Catchup"),
            EpgProgram(now - 30 * 60_000L, now + 30 * 60_000L, "Live Show"),
            EpgProgram(now + 30 * 60_000L, now + 90 * 60_000L, "Upcoming Show")
        )

        val filtered = filterVerticalEpgPrograms(programs, now)
        assertEquals(listOf("Past Catchup", "Live Show", "Upcoming Show"), filtered.map { it.title })
        assertEquals(1, findDefaultEpgIndex(filtered, now))
    }
}
