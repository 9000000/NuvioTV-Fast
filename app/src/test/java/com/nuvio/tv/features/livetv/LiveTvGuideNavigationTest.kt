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

    @Test
    fun `default guide grid uses 3 columns and navigates boundaries accurately`() {
        assertEquals(3, LIVE_TV_GUIDE_GRID_COLUMNS)
        // 8 items in 3 columns -> row0: 0..2, row1: 3..5, row2: 6..7
        assertNull(moveGridUp(index = 2))
        assertEquals(2, moveGridUp(index = 5))
        assertEquals(5, moveGridDown(index = 2, totalCount = 8))
        // Index 5 (row 1 col 2) clamps to last item 7 on the incomplete row 2.
        assertEquals(7, moveGridDown(index = 5, totalCount = 8))
        assertEquals(GridMoveResult.Boundary, moveGridLeft(index = 3))
        assertEquals(GridMoveResult.Boundary, moveGridRight(index = 2, totalCount = 8))
        assertEquals(GridMoveResult.MoveTo(7), moveGridRight(index = 6, totalCount = 8))
    }

    @Test
    fun `computeGridScrollAction scrolls back up when row enters top padding or top row scrolled`() {
        // Returning to row 0 while scrolled down slightly must snap all the way back to index 0.
        assertEquals(
            GridScrollAction.ScrollToIndex(0),
            computeGridScrollAction(
                targetIndex = 1,
                totalItemsCount = 12,
                cols = 3,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 24,
                visibleItems = listOf(GridVisibleItemGeometry(index = 1, topPx = -24, heightPx = 120)),
                viewportStartOffset = -36,
                viewportEndOffset = 500,
                afterContentPadding = 36,
                verticalSpacingPx = 14
            )
        )

        // Row clipped inside beforeContentPadding (-20 >= -36) must still scroll upward to topPx = 0.
        assertEquals(
            GridScrollAction.ScrollByOffset(-20f),
            computeGridScrollAction(
                targetIndex = 3,
                totalItemsCount = 18,
                cols = 3,
                firstVisibleItemIndex = 3,
                firstVisibleItemScrollOffset = 20,
                visibleItems = listOf(GridVisibleItemGeometry(index = 3, topPx = -20, heightPx = 120)),
                viewportStartOffset = -36,
                viewportEndOffset = 500,
                afterContentPadding = 36,
                verticalSpacingPx = 14
            )
        )

        // Off-screen row above viewport must scroll up to that row start.
        assertEquals(
            GridScrollAction.ScrollToIndex(6),
            computeGridScrollAction(
                targetIndex = 7,
                totalItemsCount = 30,
                cols = 3,
                firstVisibleItemIndex = 12,
                firstVisibleItemScrollOffset = 0,
                visibleItems = listOf(GridVisibleItemGeometry(index = 12, topPx = 0, heightPx = 120)),
                viewportStartOffset = -36,
                viewportEndOffset = 500,
                afterContentPadding = 36,
                verticalSpacingPx = 14
            )
        )

        // Item protruding past safe bottom (500 - 36 = 464) scrolls downward just enough to clear bottom padding.
        assertEquals(
            GridScrollAction.ScrollByOffset(36f),
            computeGridScrollAction(
                targetIndex = 9,
                totalItemsCount = 30,
                cols = 3,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                visibleItems = listOf(
                    GridVisibleItemGeometry(index = 0, topPx = 0, heightPx = 120),
                    GridVisibleItemGeometry(index = 9, topPx = 380, heightPx = 120)
                ),
                viewportStartOffset = -36,
                viewportEndOffset = 500,
                afterContentPadding = 36,
                verticalSpacingPx = 14
            )
        )

        // Completely visible item inside [0, 464] needs no scroll.
        assertEquals(
            GridScrollAction.None,
            computeGridScrollAction(
                targetIndex = 6,
                totalItemsCount = 30,
                cols = 3,
                firstVisibleItemIndex = 3,
                firstVisibleItemScrollOffset = 0,
                visibleItems = listOf(GridVisibleItemGeometry(index = 6, topPx = 134, heightPx = 120)),
                viewportStartOffset = -36,
                viewportEndOffset = 500,
                afterContentPadding = 36,
                verticalSpacingPx = 14
            )
        )
    }

    @Test
    fun `favorites and recent always include channels from all playlists even when switching playlist`() {
        val chP1Fav = channel("ch_p1_fav", playlistId = "p1", group = "News", name = "P1 Fav")
        val chP1Recent = channel("ch_p1_rec", playlistId = "p1", group = "Sports", name = "P1 Recent")
        val chP2Sport = channel("ch_p2_sport", playlistId = "p2", group = "Sports", name = "P2 Sport")
        val allChannels = listOf(chP1Fav, chP1Recent, chP2Sport)

        // User switches playlist filter to p2 only.
        val scopedToP2 = scopeChannelsByPlaylist(allChannels, "p2")
        val favoriteIds = setOf("ch_p1_fav")
        val recentIds = listOf("ch_p1_rec", "ch_p2_sport")

        val groups = buildLiveTvChannelGroups(
            scopedChannels = scopedToP2,
            favoriteIds = favoriteIds,
            recentIds = recentIds,
            favoritesLabel = "Favorites",
            recentLabel = "Recent",
            allLabel = "All",
            allChannels = allChannels
        )

        // Favorites and Recent counts reflect saved channels across all playlists, while ALL/custom stay scoped to p2.
        assertEquals(1, groups.first { it.id == ChannelGroup.FAVORITES }.channelCount)
        assertEquals(2, groups.first { it.id == ChannelGroup.RECENT }.channelCount)
        assertEquals(1, groups.first { it.id == ChannelGroup.ALL }.channelCount)
        assertEquals(1, groups.first { it.id == "custom:Sports" }.channelCount)

        val favChannels = filterChannelsForGroup(
            scopedChannels = scopedToP2,
            groupId = ChannelGroup.FAVORITES,
            favoriteIds = favoriteIds,
            recentIds = recentIds,
            allChannels = allChannels
        )
        assertEquals(listOf("ch_p1_fav"), favChannels.map { it.id })

        val recentChannels = filterChannelsForGroup(
            scopedChannels = scopedToP2,
            groupId = ChannelGroup.RECENT,
            favoriteIds = favoriteIds,
            recentIds = recentIds,
            allChannels = allChannels
        )
        assertEquals(listOf("ch_p1_rec", "ch_p2_sport"), recentChannels.map { it.id })

        val p2AllChannels = filterChannelsForGroup(
            scopedChannels = scopedToP2,
            groupId = ChannelGroup.ALL,
            favoriteIds = favoriteIds,
            recentIds = recentIds,
            allChannels = allChannels
        )
        assertEquals(listOf("ch_p2_sport"), p2AllChannels.map { it.id })
    }
}
