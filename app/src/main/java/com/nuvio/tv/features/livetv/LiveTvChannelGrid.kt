package com.nuvio.tv.features.livetv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp

internal const val LIVE_TV_GRID_COLUMNS = 5

/**
 * Grid component for displaying Live TV channels.
 * Uses LazyVerticalGrid with stable channel keys and D-pad edge handling.
 */
@Composable
internal fun LiveTvChannelGrid(
    channels: List<LiveTvChannel>,
    favoriteChannelIds: Set<String>,
    launchingChannelId: String?,
    gridState: LazyGridState,
    channelFocusRequesters: MutableMap<String, FocusRequester>,
    onChannelClick: (LiveTvChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRequestNavigateToCategory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalRows = remember(channels) {
        if (channels.isEmpty()) 0
        else (channels.size + LIVE_TV_GRID_COLUMNS - 1) / LIVE_TV_GRID_COLUMNS
    }
    val lastRowStartIndex = remember(totalRows) {
        if (totalRows == 0) 0 else (totalRows - 1) * LIVE_TV_GRID_COLUMNS
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(LIVE_TV_GRID_COLUMNS),
        state = gridState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(
            items = channels,
            key = { _, channel -> channel.id }
        ) { itemIndex, channel ->
            val isFav = channel.id in favoriteChannelIds
            val requester = remember(channel.id) {
                channelFocusRequesters.getOrPut(channel.id) { FocusRequester() }
            }

            TvChannelCard(
                channel = channel,
                isFavorite = isFav,
                isLaunching = (launchingChannelId == channel.id),
                focusRequester = requester,
                isFirstRow = itemIndex < LIVE_TV_GRID_COLUMNS,
                isLastRow = itemIndex >= lastRowStartIndex,
                onClick = { onChannelClick(channel) },
                onToggleFavorite = { onToggleFavorite(channel.id) },
                onRequestNavigateToCategory = onRequestNavigateToCategory
            )
        }
    }
}
