package com.nuvio.tv.features.livetv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.nuvio.tv.LocalOpenSidebar
import com.nuvio.tv.ui.leanback.LeanbackNativeGrid
import com.nuvio.tv.ui.leanback.container.InteropVerticalGridView
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.theme.NuvioTheme

internal const val LIVE_TV_GRID_COLUMNS = 5

/**
 * Grid component for displaying Live TV channels.
 * Powered by Android Leanback Native (InteropVerticalGridView) for 60fps locked navigation.
 */
@Composable
internal fun LiveTvChannelGrid(
    channels: List<LiveTvChannel>,
    favoriteChannelIds: Set<String>,
    launchingChannelId: String?,
    gridState: LazyGridState? = null,
    channelFocusRequesters: MutableMap<String, FocusRequester>? = null,
    firstChannelFocusRequester: FocusRequester? = null,
    onChannelClick: (LiveTvChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRequestNavigateToCategory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val openSidebar = LocalOpenSidebar.current
    val animationsEnabled = NuvioTheme.animationsEnabled
    var gridRef by remember { mutableStateOf<InteropVerticalGridView?>(null) }

    LaunchedEffect(channels, firstChannelFocusRequester) {
        if (channelFocusRequesters != null && firstChannelFocusRequester != null) {
            channels.forEach { ch ->
                channelFocusRequesters[ch.id] = firstChannelFocusRequester
            }
        }
    }

    val items = remember(channels, favoriteChannelIds, launchingChannelId) {
        channels.map { channel ->
            LeanbackCardItem.Channel(
                key = channel.id,
                title = channel.name,
                imageUrl = channel.logoUrl,
                isFavorite = channel.id in favoriteChannelIds,
                isLaunching = launchingChannelId == channel.id,
                payload = channel
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (firstChannelFocusRequester != null) {
                    Modifier
                        .focusRequester(firstChannelFocusRequester)
                        .focusable()
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                gridRef?.post {
                                    if (gridRef?.isAttachedToWindow == true) {
                                        gridRef?.requestFocus()
                                    }
                                }
                            }
                        }
                } else Modifier
            )
    ) {
        val totalWidth = maxWidth
        val horizontalSpacing = 14.dp
        val verticalSpacing = 14.dp
        val numCols = LIVE_TV_GRID_COLUMNS
        val availableWidth = (totalWidth - (horizontalSpacing * (numCols - 1))).coerceAtLeast(100.dp)
        val cardWidth = (availableWidth / numCols).coerceAtLeast(80.dp)
        val cardHeight = cardWidth / 1.25f

        LeanbackNativeGrid(
            gridKey = "livetv_channels_leanback_grid",
            items = items,
            numColumns = numCols,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            cornerRadius = 12.dp,
            showLabels = false,
            animationsEnabled = animationsEnabled,
            paddingValues = PaddingValues(bottom = 28.dp),
            horizontalSpacing = horizontalSpacing,
            verticalSpacing = verticalSpacing,
            onItemClick = { cardItem ->
                val channel = cardItem.payload as? LiveTvChannel ?: return@LeanbackNativeGrid
                onChannelClick(channel)
            },
            onItemLongClick = { cardItem ->
                val channel = cardItem.payload as? LiveTvChannel ?: return@LeanbackNativeGrid
                onToggleFavorite(channel.id)
            },
            onNavigateUpOnFirstRow = {
                onRequestNavigateToCategory()
                true
            },
            onNavigateLeftOnFirstColumn = {
                if (openSidebar != null) {
                    openSidebar()
                    true
                } else false
            },
            onGridCreated = { grid ->
                gridRef = grid
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
