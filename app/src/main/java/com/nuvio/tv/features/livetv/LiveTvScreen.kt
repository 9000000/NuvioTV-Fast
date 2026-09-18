package com.nuvio.tv.features.livetv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

private enum class FilterType {
    ALL,
    FAVORITES,
    RECENT,
    GROUP
}

@Composable
fun LiveTvScreen(
    onChannelSelected: (LiveTvChannel) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        LiveTvRepository.ensureLoaded()
    }

    val state by LiveTvRepository.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
    ) {
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NuvioTheme.colors.Primary)
                }
            }

            !state.hasPlaylist -> {
                LiveTvEmptyState(onNavigateToSettings = onNavigateToSettings)
            }

            state.errorMessage != null && state.channels.isEmpty() -> {
                LiveTvErrorState(
                    message = state.errorMessage!!,
                    onRetry = { LiveTvRepository.refresh() },
                    onNavigateToSettings = onNavigateToSettings
                )
            }

            else -> {
                LiveTvContent(
                    state = state,
                    onChannelSelected = { channel ->
                        LiveTvRepository.recordLastWatched(channel)
                        onChannelSelected(channel)
                    },
                    onToggleFavorite = { channelId ->
                        LiveTvRepository.toggleFavoriteChannel(channelId)
                    },
                    onRefresh = { LiveTvRepository.refresh() },
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        }
    }
}

@Composable
private fun LiveTvEmptyState(
    onNavigateToSettings: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .width(480.dp)
                .padding(24.dp),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.FocusBackground
            ),
            border = CardDefaults.border(
                border = Border(
                    border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                    shape = RoundedCornerShape(16.dp)
                ),
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xs),
                    shape = RoundedCornerShape(16.dp)
                )
            ),
            shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
            scale = CardDefaults.scale(focusedScale = 1.04f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = NuvioTheme.colors.Primary
                )

                Text(
                    text = stringResource(R.string.livetv_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = NuvioTheme.colors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.livetv_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NuvioTheme.colors.Primary)
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.livetv_configure_now),
                        color = NuvioTheme.colors.OnPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTvErrorState(
    message: String,
    onRetry: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "⚠️ $message",
                color = NuvioTheme.colors.Error,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    onClick = onRetry,
                    colors = CardDefaults.colors(
                        containerColor = NuvioTheme.colors.Primary,
                        focusedContainerColor = NuvioTheme.colors.FocusBackground
                    ),
                    shape = CardDefaults.shape(RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "Thử lại (Retry)",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = NuvioTheme.colors.OnPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Card(
                    onClick = onNavigateToSettings,
                    colors = CardDefaults.colors(
                        containerColor = NuvioTheme.colors.BackgroundElevated,
                        focusedContainerColor = NuvioTheme.colors.FocusBackground
                    ),
                    shape = CardDefaults.shape(RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = stringResource(R.string.settings_livetv_title),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = NuvioTheme.colors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTvContent(
    state: LiveTvUiState,
    onChannelSelected: (LiveTvChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var activeFilter by remember { mutableStateOf(FilterType.ALL) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }

    val allGroups = remember(state.channels) {
        state.channels.mapNotNull { it.group?.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filteredChannels = remember(state.channels, activeFilter, selectedGroup, state.favoriteChannelIds, state.lastWatchedChannelId) {
        when (activeFilter) {
            FilterType.ALL -> state.channels
            FilterType.FAVORITES -> state.channels.filter { it.id in state.favoriteChannelIds }
            FilterType.RECENT -> state.channels.filter { it.id == state.lastWatchedChannelId }
            FilterType.GROUP -> state.channels.filter { it.group?.trim() == selectedGroup }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- HEADER BAR ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.nav_livetv),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = NuvioTheme.colors.TextPrimary
                )
                Text(
                    text = stringResource(R.string.livetv_channels_count, filteredChannels.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Refresh Button
                Card(
                    onClick = onRefresh,
                    colors = CardDefaults.colors(
                        containerColor = NuvioTheme.colors.BackgroundElevated,
                        focusedContainerColor = NuvioTheme.colors.FocusBackground
                    ),
                    shape = CardDefaults.shape(CircleShape),
                    scale = CardDefaults.scale(focusedScale = 1.1f)
                ) {
                    Box(modifier = Modifier.padding(10.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = NuvioTheme.colors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Settings Button
                Card(
                    onClick = onNavigateToSettings,
                    colors = CardDefaults.colors(
                        containerColor = NuvioTheme.colors.BackgroundElevated,
                        focusedContainerColor = NuvioTheme.colors.FocusBackground
                    ),
                    shape = CardDefaults.shape(CircleShape),
                    scale = CardDefaults.scale(focusedScale = 1.1f)
                ) {
                    Box(modifier = Modifier.padding(10.dp)) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = NuvioTheme.colors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // --- FILTER CHIPS ROW ---
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            // All Channels
            item {
                FilterChipItem(
                    label = stringResource(R.string.livetv_group_all),
                    isSelected = activeFilter == FilterType.ALL,
                    onClick = {
                        activeFilter = FilterType.ALL
                        selectedGroup = null
                    }
                )
            }

            // Favorites
            item {
                FilterChipItem(
                    label = stringResource(R.string.livetv_group_favorites),
                    isSelected = activeFilter == FilterType.FAVORITES,
                    onClick = {
                        activeFilter = FilterType.FAVORITES
                        selectedGroup = null
                    }
                )
            }

            // Recent
            if (state.lastWatchedChannelId != null) {
                item {
                    FilterChipItem(
                        label = stringResource(R.string.livetv_group_recent),
                        isSelected = activeFilter == FilterType.RECENT,
                        onClick = {
                            activeFilter = FilterType.RECENT
                            selectedGroup = null
                        }
                    )
                }
            }

            // Dynamic Groups from Playlist
            items(allGroups) { group ->
                FilterChipItem(
                    label = group,
                    isSelected = activeFilter == FilterType.GROUP && selectedGroup == group,
                    onClick = {
                        activeFilter = FilterType.GROUP
                        selectedGroup = group
                    }
                )
            }
        }

        // --- CHANNELS GRID ---
        if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Không có kênh nào trong mục này",
                    color = NuvioTheme.colors.TextSecondary,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredChannels, key = { it.id }) { channel ->
                    val isFav = channel.id in state.favoriteChannelIds
                    TvChannelCard(
                        channel = channel,
                        isFavorite = isFav,
                        onClick = { onChannelSelected(channel) },
                        onToggleFavorite = { onToggleFavorite(channel.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = if (isSelected) NuvioTheme.colors.Primary else NuvioTheme.colors.BackgroundElevated,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f)
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
            Text(
                text = label,
                color = if (isSelected) NuvioTheme.colors.OnPrimary else NuvioTheme.colors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun TvChannelCard(
    channel: LiveTvChannel,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.25f),
        colors = CardDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundElevated,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                shape = RoundedCornerShape(12.dp)
            ),
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xs),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Channel Logo or Placeholder
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .padding(bottom = 28.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = NuvioTheme.colors.TextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Favorite star badge
            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Channel name bar at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
