package com.nuvio.tv.features.livetv

internal const val LIVE_TV_GUIDE_GRID_COLUMNS = 4
private const val EPG_PAST_WINDOW_MS = 4 * 60 * 60 * 1000L
private const val EPG_FUTURE_WINDOW_MS = 24 * 60 * 60 * 1000L
private const val EPG_MAX_PAST_PROGRAMS = 4

/**
 * Distinct focusable regions inside the 3-column Live TV guide screen.
 */
internal enum class GuideFocusArea {
    CATEGORY,
    CHANNEL_GRID,
    PREVIEW_PLAYER,
    VERTICAL_EPG
}

/**
 * Selectable playlist filter item displayed in the Live TV channel list picker.
 * [id] == null represents all enabled channel lists combined.
 */
internal data class LiveTvPlaylistOption(
    val id: String?,
    val name: String,
    val channelCount: Int
)

internal sealed interface GridMoveResult {
    data class MoveTo(val index: Int) : GridMoveResult
    data object Boundary : GridMoveResult
}

internal fun buildLiveTvPlaylistOptions(
    ui: LiveTvUiState,
    allPlaylistsLabel: String
): List<LiveTvPlaylistOption> {
    val countsByPlaylist = ui.channels
        .mapNotNull { ch -> ch.playlistId?.let { id -> id to ch } }
        .groupBy({ it.first }, { it.second })

    val seenIds = LinkedHashSet<String>()
    val result = mutableListOf<LiveTvPlaylistOption>()

    // Always put "All channel lists" as the default first option.
    result.add(
        LiveTvPlaylistOption(
            id = null,
            name = allPlaylistsLabel,
            channelCount = ui.channels.size
        )
    )

    // Exclude explicitly disabled playlists so fallback grouping never resurfaces them.
    ui.playlists.filter { !it.isEnabled }.forEach { seenIds.add(it.id) }

    ui.playlists
        .filter { it.isEnabled && (countsByPlaylist[it.id]?.isNotEmpty() == true) }
        .sortedBy { it.name.lowercase() }
        .forEach { playlist ->
            seenIds.add(playlist.id)
            result.add(
                LiveTvPlaylistOption(
                    id = playlist.id,
                    name = playlist.name,
                    channelCount = countsByPlaylist[playlist.id]?.size ?: 0
                )
            )
        }

    val xtreamCount = countsByPlaylist[XTREAM_PLAYLIST_ID]?.size ?: 0
    if (ui.xtreamSettings.isConfigured && ui.xtreamSettings.isEnabled && xtreamCount > 0) {
        seenIds.add(XTREAM_PLAYLIST_ID)
        result.add(
            LiveTvPlaylistOption(
                id = XTREAM_PLAYLIST_ID,
                name = "Xtream",
                channelCount = xtreamCount
            )
        )
    }

    val stalkerCount = countsByPlaylist[STALKER_PLAYLIST_ID]?.size ?: 0
    if (ui.stalkerSettings.isConfigured && ui.stalkerSettings.isEnabled && stalkerCount > 0) {
        seenIds.add(STALKER_PLAYLIST_ID)
        result.add(
            LiveTvPlaylistOption(
                id = STALKER_PLAYLIST_ID,
                name = "Stalker Portal",
                channelCount = stalkerCount
            )
        )
    }

    // Fallback for any enabled channel playlistId not covered by explicit settings entries.
    countsByPlaylist.forEach { (playlistId, chList) ->
        if (playlistId !in seenIds && chList.isNotEmpty()) {
            val fallbackName = chList.firstNotNullOfOrNull {
                it.playlistName?.trim()?.takeIf(String::isNotBlank)
            } ?: playlistId
            result.add(
                LiveTvPlaylistOption(
                    id = playlistId,
                    name = fallbackName,
                    channelCount = chList.size
                )
            )
        }
    }

    return result
}

internal fun scopeChannelsByPlaylist(
    channels: List<LiveTvChannel>,
    selectedPlaylistId: String?
): List<LiveTvChannel> {
    if (selectedPlaylistId == null) return channels
    return channels.filter { it.playlistId == selectedPlaylistId }
}

internal fun buildLiveTvChannelGroups(
    scopedChannels: List<LiveTvChannel>,
    favoriteIds: Set<String>,
    recentIds: List<String>,
    favoritesLabel: String,
    recentLabel: String,
    allLabel: String
): List<ChannelGroup> {
    val list = mutableListOf<ChannelGroup>()

    val favCount = scopedChannels.count { it.id in favoriteIds }
    list.add(ChannelGroup(ChannelGroup.FAVORITES, favoritesLabel, channelCount = favCount))

    val recentSet = recentIds.toSet()
    val recentCount = scopedChannels.count { it.id in recentSet }
    list.add(ChannelGroup(ChannelGroup.RECENT, recentLabel, channelCount = recentCount))

    val groupCounts = scopedChannels.groupBy { it.group?.trim().orEmpty() }
    groupCounts.forEach { (grpName, chList) ->
        if (grpName.isNotBlank()) {
            list.add(ChannelGroup("custom:$grpName", grpName, channelCount = chList.size))
        }
    }

    list.add(ChannelGroup(ChannelGroup.ALL, allLabel, channelCount = scopedChannels.size))
    return list
}

internal fun filterChannelsForGroup(
    scopedChannels: List<LiveTvChannel>,
    groupId: String,
    favoriteIds: Set<String>,
    recentIds: List<String>
): List<LiveTvChannel> {
    return when {
        groupId == ChannelGroup.FAVORITES -> scopedChannels.filter { it.id in favoriteIds }
        groupId == ChannelGroup.RECENT -> {
            val recentOrder = recentIds.withIndex().associate { it.value to it.index }
            scopedChannels
                .filter { it.id in recentOrder }
                .sortedBy { recentOrder[it.id] ?: Int.MAX_VALUE }
        }
        groupId.startsWith("custom:") -> {
            val targetGroup = groupId.removePrefix("custom:")
            scopedChannels.filter { it.group?.trim().equals(targetGroup, ignoreCase = true) }
        }
        else -> scopedChannels
    }
}

internal fun resolveInitialGroupId(
    groups: List<ChannelGroup>,
    favoriteCount: Int,
    recentCount: Int
): String {
    return when {
        favoriteCount > 0 && groups.any { it.id == ChannelGroup.FAVORITES } -> ChannelGroup.FAVORITES
        recentCount > 0 && groups.any { it.id == ChannelGroup.RECENT } -> ChannelGroup.RECENT
        else -> ChannelGroup.ALL
    }
}

internal fun resolveValidGroupId(
    groups: List<ChannelGroup>,
    currentGroupId: String?
): String {
    if (currentGroupId != null && groups.any { it.id == currentGroupId }) {
        return currentGroupId
    }
    return ChannelGroup.ALL
}

internal fun filterVerticalEpgPrograms(
    programs: List<EpgProgram>,
    nowMs: Long
): List<EpgProgram> {
    if (programs.isEmpty()) return emptyList()
    val minPastStartMs = nowMs - EPG_PAST_WINDOW_MS
    val maxFutureStartMs = nowMs + EPG_FUTURE_WINDOW_MS
    val sorted = programs
        .filter { it.stopMs > minPastStartMs && it.startMs <= maxFutureStartMs }
        .sortedBy { it.startMs }
    if (sorted.isEmpty()) return emptyList()

    val past = sorted.filter { it.stopMs <= nowMs }.takeLast(EPG_MAX_PAST_PROGRAMS)
    val liveAndFuture = sorted.filter { it.stopMs > nowMs }
    return past + liveAndFuture
}

internal fun findDefaultEpgIndex(programs: List<EpgProgram>, nowMs: Long): Int {
    if (programs.isEmpty()) return 0
    val liveIdx = programs.indexOfFirst { nowMs in it.startMs until it.stopMs }
    if (liveIdx >= 0) return liveIdx
    val upcomingIdx = programs.indexOfFirst { it.startMs >= nowMs }
    return if (upcomingIdx >= 0) upcomingIdx else programs.lastIndex
}

internal fun moveGridUp(index: Int, cols: Int = LIVE_TV_GUIDE_GRID_COLUMNS): Int? {
    if (index < cols) return null
    return index - cols
}

internal fun moveGridDown(
    index: Int,
    totalCount: Int,
    cols: Int = LIVE_TV_GUIDE_GRID_COLUMNS
): Int? {
    if (totalCount <= 0) return null
    val safeIndex = index.coerceIn(0, totalCount - 1)
    val next = safeIndex + cols
    if (next < totalCount) return next

    val currentRow = safeIndex / cols
    val lastRow = (totalCount - 1) / cols
    return if (currentRow < lastRow) totalCount - 1 else null
}

internal fun moveGridLeft(
    index: Int,
    cols: Int = LIVE_TV_GUIDE_GRID_COLUMNS
): GridMoveResult {
    if (index <= 0 || index % cols == 0) return GridMoveResult.Boundary
    return GridMoveResult.MoveTo(index - 1)
}

internal fun moveGridRight(
    index: Int,
    totalCount: Int,
    cols: Int = LIVE_TV_GUIDE_GRID_COLUMNS
): GridMoveResult {
    if (totalCount <= 0) return GridMoveResult.Boundary
    val safeIndex = index.coerceIn(0, totalCount - 1)
    val isLastInRow = (safeIndex % cols == cols - 1) || (safeIndex == totalCount - 1)
    return if (isLastInRow) GridMoveResult.Boundary else GridMoveResult.MoveTo(safeIndex + 1)
}
