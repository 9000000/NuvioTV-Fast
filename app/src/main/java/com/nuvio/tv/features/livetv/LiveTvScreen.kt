package com.nuvio.tv.features.livetv

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val GRID_COLUMNS = 5

private enum class FilterType {
    ALL, FAVORITES, RECENT, GROUP, PLAYLIST
}

// ─── Entry point ─────────────────────────────────────────────────────────────

@Composable
fun LiveTvScreen(
    onChannelSelected: (LiveTvChannel) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    LaunchedEffect(Unit) { LiveTvRepository.ensureLoaded() }
    val state by LiveTvRepository.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = NuvioTheme.colors.Primary)
            }
            !state.hasPlaylist -> LiveTvEmptyState(onNavigateToSettings)
            state.errorMessage != null && state.channels.isEmpty() -> LiveTvErrorState(
                message = state.errorMessage!!,
                onRetry = { LiveTvRepository.refresh() },
                onNavigateToSettings = onNavigateToSettings
            )
            else -> LiveTvContent(
                state = state,
                onChannelSelected = { channel ->
                    LiveTvRepository.recordLastWatched(channel)
                    onChannelSelected(channel)
                },
                onToggleFavorite = { LiveTvRepository.toggleFavoriteChannel(it) },
                onRefresh = { LiveTvRepository.refresh() },
                onNavigateToSettings = onNavigateToSettings
            )
        }
    }
}

// ─── Empty / Error ────────────────────────────────────────────────────────────

@Composable
private fun LiveTvEmptyState(onNavigateToSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Card(
            onClick = onNavigateToSettings,
            modifier = Modifier.width(480.dp).padding(24.dp),
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
            scale = CardDefaults.scale(focusedScale = 1.0f)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.LiveTv, null, Modifier.size(64.dp), tint = NuvioTheme.colors.Primary)
                Text(stringResource(R.string.livetv_empty_title), style = MaterialTheme.typography.headlineSmall,
                    color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.livetv_empty_desc), style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(NuvioTheme.colors.Primary)
                    .padding(horizontal = 24.dp, vertical = 10.dp)) {
                    Text(stringResource(R.string.livetv_configure_now), color = NuvioTheme.colors.OnPrimary,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun LiveTvErrorState(message: String, onRetry: () -> Unit, onNavigateToSettings: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(24.dp)) {
            Text("⚠️ $message", color = NuvioTheme.colors.Error,
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(onClick = onRetry,
                    colors = CardDefaults.colors(containerColor = NuvioTheme.colors.Primary,
                        focusedContainerColor = NuvioTheme.colors.FocusBackground),
                    shape = CardDefaults.shape(RoundedCornerShape(8.dp))) {
                    Text("Thử lại (Retry)", Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = NuvioTheme.colors.OnPrimary, fontWeight = FontWeight.Bold)
                }
                Card(onClick = onNavigateToSettings,
                    colors = CardDefaults.colors(containerColor = NuvioTheme.colors.BackgroundElevated,
                        focusedContainerColor = NuvioTheme.colors.FocusBackground),
                    shape = CardDefaults.shape(RoundedCornerShape(8.dp))) {
                    Text(stringResource(R.string.settings_livetv_title),
                        Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = NuvioTheme.colors.TextPrimary)
                }
            }
        }
    }
}

// ─── Main content ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveTvContent(
    state: LiveTvUiState,
    onChannelSelected: (LiveTvChannel) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // Dùng rememberSaveable để nhớ filter đã chọn giữa các lần navigation
    var activeFilter by rememberSaveable { mutableStateOf(FilterType.ALL) }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var isInitialEntry by rememberSaveable { mutableStateOf(true) }
    var showPlaylistDropdown by remember { mutableStateOf(false) }

    val allGroups = remember(state.channels) {
        state.channels.mapNotNull { it.group?.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    // Tất cả playlist có kênh (bao gồm M3U, Xtream và Stalker Portal)
    val allPlaylists = remember(state.playlists, state.channels, state.xtreamSettings, state.stalkerSettings) {
        val idsWithChannels = state.channels.mapNotNull { it.playlistId }.toSet()
        val standardPlaylists = state.playlists.filter { it.isEnabled && it.id in idsWithChannels }
            .sortedBy { it.name }

        buildList {
            addAll(standardPlaylists)
            if (state.xtreamSettings.isConfigured && state.xtreamSettings.isEnabled && XTREAM_PLAYLIST_ID in idsWithChannels) {
                add(
                    LiveTvPlaylist(
                        id = XTREAM_PLAYLIST_ID,
                        name = "Xtream",
                        type = LiveTvPlaylistType.Url,
                        source = state.xtreamSettings.serverUrl,
                        isEnabled = true
                    )
                )
            }
            if (state.stalkerSettings.isConfigured && state.stalkerSettings.isEnabled && STALKER_PLAYLIST_ID in idsWithChannels) {
                add(
                    LiveTvPlaylist(
                        id = STALKER_PLAYLIST_ID,
                        name = "Stalker Portal",
                        type = LiveTvPlaylistType.Url,
                        source = state.stalkerSettings.portalUrl,
                        isEnabled = true
                    )
                )
            }
        }
    }

    val selectedPlaylistName = remember(selectedPlaylistId, allPlaylists) {
        allPlaylists.firstOrNull { it.id == selectedPlaylistId }?.name
    }

    val filteredChannels = remember(
        state.channels, activeFilter, selectedGroup, selectedPlaylistId,
        state.favoriteChannelIds, state.recentChannelIds
    ) {
        when (activeFilter) {
            FilterType.ALL -> state.channels
            FilterType.FAVORITES -> state.channels.filter { it.id in state.favoriteChannelIds }
            FilterType.RECENT -> {
                val channelMap = state.channels.associateBy { it.id }
                state.recentChannelIds.mapNotNull { channelMap[it] }
            }
            FilterType.GROUP -> state.channels.filter { it.group?.trim() == selectedGroup }
            FilterType.PLAYLIST -> state.channels.filter { it.playlistId == selectedPlaylistId }
        }
    }

    val totalRows = remember(filteredChannels) {
        if (filteredChannels.isEmpty()) 0
        else (filteredChannels.size + GRID_COLUMNS - 1) / GRID_COLUMNS
    }
    val lastRowStartIndex = remember(totalRows) {
        if (totalRows == 0) 0 else (totalRows - 1) * GRID_COLUMNS
    }

    val gridState = rememberLazyGridState()
    val filterChipsListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val channelFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var restoredChannelId by rememberSaveable { mutableStateOf<String?>(null) }

    // Focus requesters cho filter chips
    val allChipFocusRequester = remember { FocusRequester() }
    val favoritesChipFocusRequester = remember { FocusRequester() }
    val recentChipFocusRequester = remember { FocusRequester() }
    val groupChipFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val playlistDropdownButtonFocusRequester = remember { FocusRequester() }

    // Tính số chip tĩnh trước GROUP chips: Playlist (luôn có), Favorites, Recent (nếu có)
    val staticChipCount = 1 + 1 + (if (state.recentChannelIds.isNotEmpty()) 1 else 0)

    LaunchedEffect(filteredChannels.map { it.id }) {
        channelFocusRequesters.keys.retainAll(filteredChannels.map { it.id }.toSet())
    }

    // Restore focus khi quay lại từ player
    LaunchedEffect(state.lastWatchedChannelId, filteredChannels, isInitialEntry) {
        val targetId = state.lastWatchedChannelId
        if (!isInitialEntry && targetId != null && targetId != restoredChannelId) {
            val idx = filteredChannels.indexOfFirst { it.id == targetId }
            if (idx >= 0) {
                gridState.scrollToItem(idx)
                delay(120)
                runCatching { channelFocusRequesters[targetId]?.requestFocus() }
                restoredChannelId = targetId
            }
        }
    }

    // Focus ban đầu - ưu tiên Recent nếu có, không thì All Channels
    LaunchedEffect(isInitialEntry, state.recentChannelIds) {
        if (isInitialEntry) {
            delay(150)
            if (state.recentChannelIds.isNotEmpty()) {
                // Có lịch sử xem → hiển thị Recent
                activeFilter = FilterType.RECENT
                runCatching { recentChipFocusRequester.requestFocus() }
            } else {
                // Chưa có lịch sử → hiển thị All Channels
                activeFilter = FilterType.ALL
                runCatching { playlistDropdownButtonFocusRequester.requestFocus() }
            }
            isInitialEntry = false
        }
    }

    // Helper navigate về chip hiện tại với scroll an toàn, retry và fallback
    suspend fun navigateToActiveChip() {
        showPlaylistDropdown = false
        val hasRecent = state.recentChannelIds.isNotEmpty()

        val targetIndex = when (activeFilter) {
            FilterType.PLAYLIST, FilterType.ALL -> 0
            FilterType.FAVORITES -> 1
            FilterType.RECENT -> if (hasRecent) 2 else 1
            FilterType.GROUP -> {
                val groupIdx = selectedGroup?.let { allGroups.indexOf(it) } ?: -1
                if (groupIdx >= 0) {
                    (if (hasRecent) 3 else 2) + groupIdx
                } else {
                    0
                }
            }
        }

        val getTargetRequester: () -> FocusRequester = {
            when (activeFilter) {
                FilterType.PLAYLIST, FilterType.ALL -> playlistDropdownButtonFocusRequester
                FilterType.FAVORITES -> favoritesChipFocusRequester
                FilterType.RECENT -> if (hasRecent) recentChipFocusRequester else favoritesChipFocusRequester
                FilterType.GROUP -> {
                    selectedGroup?.let { groupChipFocusRequesters.getOrPut(it) { FocusRequester() } }
                        ?: playlistDropdownButtonFocusRequester
                }
            }
        }

        // 1. Kiểm tra nếu chip đã visible trong viewport của LazyRow, nếu chưa thì cuộn đến
        val isVisible = filterChipsListState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        if (!isVisible) {
            runCatching {
                filterChipsListState.scrollToItem(targetIndex)
            }
        }

        // 2. Thử request focus (với retry để đảm bảo Composable modifier node đã attached sau khi scroll)
        var focused = false
        for (attempt in 0..4) {
            val req = getTargetRequester()
            focused = runCatching {
                req.requestFocus()
                true
            }.getOrDefault(false)

            if (focused) break
            delay(30)
        }

        // 3. Fallback an toàn: nếu vẫn chưa focus được (do chip lỗi hoặc unattached), cuộn về đầu và focus nút playlist dropdown
        if (!focused) {
            runCatching {
                filterChipsListState.scrollToItem(0)
            }
            delay(40)
            runCatching {
                playlistDropdownButtonFocusRequester.requestFocus()
            }
        }
    }

    // Bọc trong Box để có thể hiển thị dropdown overlay lên trên
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(stringResource(R.string.nav_livetv),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = NuvioTheme.colors.TextPrimary)
                    Text(stringResource(R.string.livetv_channels_count, filteredChannels.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = NuvioTheme.colors.TextSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    // Hint badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .background(NuvioTheme.colors.BackgroundElevated)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = Color(0xFFFFD700))
                        Text("Giữ [OK] / [Menu]: Yêu thích",
                            color = NuvioTheme.colors.TextSecondary, fontSize = 12.sp)
                    }
                    // Refresh
                    Card(
                        onClick = onRefresh,
                        colors = CardDefaults.colors(containerColor = NuvioTheme.colors.BackgroundElevated,
                            focusedContainerColor = NuvioTheme.colors.FocusBackground),
                        shape = CardDefaults.shape(CircleShape),
                        scale = CardDefaults.scale(focusedScale = 1.0f),
                        modifier = Modifier.onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN) {
                                coroutineScope.launch { navigateToActiveChip() }; true
                            } else false
                        }
                    ) { Box(Modifier.padding(10.dp)) {
                        Icon(Icons.Default.Refresh, "Refresh", Modifier.size(20.dp), tint = NuvioTheme.colors.TextPrimary)
                    } }
                    // Settings
                    Card(
                        onClick = onNavigateToSettings,
                        colors = CardDefaults.colors(containerColor = NuvioTheme.colors.BackgroundElevated,
                            focusedContainerColor = NuvioTheme.colors.FocusBackground),
                        shape = CardDefaults.shape(CircleShape),
                        scale = CardDefaults.scale(focusedScale = 1.0f),
                        modifier = Modifier.onKeyEvent { keyEvent ->
                            if (keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN) {
                                coroutineScope.launch { navigateToActiveChip() }; true
                            } else false
                        }
                    ) { Box(Modifier.padding(10.dp)) {
                        Icon(Icons.Default.Settings, "Settings", Modifier.size(20.dp), tint = NuvioTheme.colors.TextPrimary)
                    } }
                }
            }

            // ── Filter Chips + Playlist Dropdown Button ──
            LazyRow(
                state = filterChipsListState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                userScrollEnabled = true
            ) {
                // Playlist Dropdown Button — luôn hiển thị ở đầu tiên
                item(key = "playlist_dropdown") {
                    PlaylistDropdownButton(
                        selectedName = if (activeFilter == FilterType.PLAYLIST)
                            selectedPlaylistName
                        else if (activeFilter == FilterType.ALL)
                            stringResource(R.string.livetv_group_all)
                        else null,
                        isActive = activeFilter == FilterType.PLAYLIST || activeFilter == FilterType.ALL,
                        isOpen = showPlaylistDropdown,
                        focusRequester = playlistDropdownButtonFocusRequester,
                        onClick = {
                            showPlaylistDropdown = !showPlaylistDropdown
                            isInitialEntry = false
                        }
                    )
                }
                // FAVORITES
                item(key = "favorites") {
                    FilterChipItem(
                        label = stringResource(R.string.livetv_group_favorites),
                        isSelected = activeFilter == FilterType.FAVORITES,
                        onClick = {
                            activeFilter = FilterType.FAVORITES
                            selectedGroup = null; selectedPlaylistId = null
                            showPlaylistDropdown = false; isInitialEntry = false
                        },
                        focusRequester = favoritesChipFocusRequester
                    )
                }
                // RECENT
                if (state.recentChannelIds.isNotEmpty()) {
                    item(key = "recent") {
                        FilterChipItem(
                            label = stringResource(R.string.livetv_group_recent),
                            isSelected = activeFilter == FilterType.RECENT,
                            onClick = {
                                activeFilter = FilterType.RECENT
                                selectedGroup = null; selectedPlaylistId = null
                                showPlaylistDropdown = false; isInitialEntry = false
                            },
                            focusRequester = recentChipFocusRequester
                        )
                    }
                }
                // GROUP chips
                items(allGroups, key = { "group_$it" }) { group ->
                    val req = remember(group) { groupChipFocusRequesters.getOrPut(group) { FocusRequester() } }
                    FilterChipItem(
                        label = group,
                        isSelected = activeFilter == FilterType.GROUP && selectedGroup == group,
                        onClick = {
                            activeFilter = FilterType.GROUP
                            selectedGroup = group; selectedPlaylistId = null
                            showPlaylistDropdown = false; isInitialEntry = false
                        },
                        focusRequester = req
                    )
                }
            }

            // ── Channels Grid ──
            if (filteredChannels.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    Text("Không có kênh nào trong mục này",
                        color = NuvioTheme.colors.TextSecondary, fontSize = 15.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(GRID_COLUMNS),
                    state = gridState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(items = filteredChannels, key = { _, channel -> channel.id }) { itemIndex, channel ->
                        val isFav = channel.id in state.favoriteChannelIds
                        val requester = remember(channel.id) {
                            channelFocusRequesters.getOrPut(channel.id) { FocusRequester() }
                        }
                        TvChannelCard(
                            channel = channel,
                            isFavorite = isFav,
                            focusRequester = requester,
                            isFirstRow = itemIndex < GRID_COLUMNS,
                            isLastRow = itemIndex >= lastRowStartIndex,
                            onClick = {
                                showPlaylistDropdown = false
                                restoredChannelId = null
                                onChannelSelected(channel)
                            },
                            onToggleFavorite = { onToggleFavorite(channel.id) },
                            onRequestNavigateToCategory = {
                                coroutineScope.launch {
                                    runCatching { gridState.animateScrollToItem(0) }
                                    navigateToActiveChip()
                                }
                            }
                        )
                    }
                }
            }
        }

        // ── Playlist Dropdown Panel (overlay) ──
        AnimatedVisibility(
            visible = showPlaylistDropdown,
            enter = fadeIn() + slideInVertically { -it / 3 },
            exit = fadeOut() + slideOutVertically { -it / 3 },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 110.dp, end = 24.dp)
                .zIndex(10f)
        ) {
            PlaylistDropdownPanel(
                playlists = allPlaylists,
                selectedPlaylistId = selectedPlaylistId,
                isAllChannelsSelected = activeFilter == FilterType.ALL,
                onAllChannelsSelected = {
                    activeFilter = FilterType.ALL
                    selectedPlaylistId = null
                    selectedGroup = null
                    showPlaylistDropdown = false
                    isInitialEntry = false
                },
                onPlaylistSelected = { playlist ->
                    activeFilter = FilterType.PLAYLIST
                    selectedPlaylistId = playlist.id
                    selectedGroup = null
                    showPlaylistDropdown = false
                    isInitialEntry = false
                },
                onDismiss = {
                    showPlaylistDropdown = false
                    coroutineScope.launch {
                        delay(80)
                        playlistDropdownButtonFocusRequester.requestFocus()
                    }
                }
            )
        }
    }
}

// ─── Playlist Dropdown Button ──────────────────────────────────────────────────

@Composable
private fun PlaylistDropdownButton(
    selectedName: String?,
    isActive: Boolean,
    isOpen: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    // Mặc định hiển thị "All Channels" nếu chưa chọn gì
    val displayName = selectedName ?: stringResource(R.string.livetv_group_all)
    
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = if (isActive) NuvioTheme.colors.Primary else NuvioTheme.colors.BackgroundElevated,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
        scale = CardDefaults.scale(focusedScale = 1.0f),
        modifier = Modifier.focusRequester(focusRequester)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlaylistPlay,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = if (isActive) NuvioTheme.colors.OnPrimary else NuvioTheme.colors.TextSecondary
            )
            Text(
                text = displayName,
                color = if (isActive) NuvioTheme.colors.OnPrimary else NuvioTheme.colors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isActive) NuvioTheme.colors.OnPrimary else NuvioTheme.colors.TextSecondary
            )
        }
    }
}

// ─── Playlist Dropdown Panel ───────────────────────────────────────────────────

@Composable
private fun PlaylistDropdownPanel(
    playlists: List<LiveTvPlaylist>,
    selectedPlaylistId: String?,
    isAllChannelsSelected: Boolean,
    onAllChannelsSelected: () -> Unit,
    onPlaylistSelected: (LiveTvPlaylist) -> Unit,
    onDismiss: () -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    val playlistFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    
    LaunchedEffect(Unit) {
        delay(80)
        firstItemFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(NuvioTheme.colors.BackgroundElevated)
            .widthIn(min = 220.dp, max = 320.dp)
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NuvioTheme.colors.Background.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PlaylistPlay, null, Modifier.size(16.dp),
                    tint = NuvioTheme.colors.Primary)
                Text("Chọn danh sách phát",
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.TextSecondary,
                    fontWeight = FontWeight.Medium)
            }

            // Divider
            Box(Modifier.fillMaxWidth().height(1.dp).background(NuvioTheme.colors.Border))

            // Playlist list
            LazyColumn(
                modifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                // ALL CHANNELS item - luôn ở đầu
                item(key = "all_channels") {
                    PlaylistDropdownAllChannelsItem(
                        isSelected = isAllChannelsSelected,
                        focusRequester = firstItemFocusRequester,
                        onClick = onAllChannelsSelected,
                        onKeyUp = onDismiss,
                        isFirstItem = true,
                        isLastItem = playlists.isEmpty()
                    )
                }
                
                // Playlist items
                itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    val isSelected = playlist.id == selectedPlaylistId
                    val itemFocusRequester = remember(playlist.id) { 
                        playlistFocusRequesters.getOrPut(playlist.id) { FocusRequester() }
                    }
                    val isLastItem = index == playlists.lastIndex

                    PlaylistDropdownItem(
                        playlist = playlist,
                        isSelected = isSelected,
                        focusRequester = itemFocusRequester,
                        onClick = { onPlaylistSelected(playlist) },
                        onKeyUp = onDismiss,
                        isFirstItem = false,
                        isLastItem = isLastItem
                    )
                }
            }
        }
    }
}

// ─── Playlist Dropdown All Channels Item ───────────────────────────────────────

@Composable
private fun PlaylistDropdownAllChannelsItem(
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onKeyUp: () -> Unit,
    isFirstItem: Boolean,
    isLastItem: Boolean
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isSelected -> NuvioTheme.colors.Primary.copy(alpha = 0.18f)
        isFocused -> NuvioTheme.colors.FocusBackground
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                when {
                    // UP từ item đầu → đóng dropdown
                    isFirstItem &&
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        onKeyUp(); true
                    }
                    // DOWN từ item cuối → không làm gì (hoặc loop, tùy design)
                    isLastItem &&
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Không làm gì, giữ focus tại chỗ
                        true
                    }
                    // BACK → đóng dropdown
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK -> {
                        onKeyUp(); true
                    }
                    // OK / CENTER → chọn
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode in listOf(
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER
                    ) -> { onClick(); true }
                    else -> false
                }
            }
    ) {
        Card(
            onClick = onClick,
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale = CardDefaults.scale(focusedScale = 1.0f),
            border = CardDefaults.border(
                border = Border(BorderStroke(0.dp, Color.Transparent)),
                focusedBorder = Border(BorderStroke(0.dp, Color.Transparent))
            ),
            shape = CardDefaults.shape(RoundedCornerShape(0.dp)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LiveTv,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isSelected) NuvioTheme.colors.Primary
                           else if (isFocused) NuvioTheme.colors.TextPrimary
                           else NuvioTheme.colors.TextSecondary
                )
                Text(
                    text = "All Channels",
                    color = if (isSelected) NuvioTheme.colors.Primary
                            else if (isFocused) NuvioTheme.colors.TextPrimary
                            else NuvioTheme.colors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp),
                        tint = NuvioTheme.colors.Primary)
                }
            }
        }
    }
}

// ─── Playlist Dropdown Item ────────────────────────────────────────────────────

@Composable
private fun PlaylistDropdownItem(
    playlist: LiveTvPlaylist,
    isSelected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onKeyUp: () -> Unit,
    isFirstItem: Boolean,
    isLastItem: Boolean
) {
    var isFocused by remember { mutableStateOf(false) }

    val bgColor = when {
        isSelected -> NuvioTheme.colors.Primary.copy(alpha = 0.18f)
        isFocused -> NuvioTheme.colors.FocusBackground
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                when {
                    // DOWN từ item cuối → không làm gì
                    isLastItem &&
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Giữ focus tại item cuối
                        true
                    }
                    // BACK → đóng dropdown
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK -> {
                        onKeyUp(); true
                    }
                    // OK / CENTER → chọn
                    keyEvent.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode in listOf(
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                        AndroidKeyEvent.KEYCODE_ENTER
                    ) -> { onClick(); true }
                    else -> false
                }
            }
    ) {
        Card(
            onClick = onClick,
            colors = CardDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent
            ),
            scale = CardDefaults.scale(focusedScale = 1.0f),
            border = CardDefaults.border(
                border = Border(BorderStroke(0.dp, Color.Transparent)),
                focusedBorder = Border(BorderStroke(0.dp, Color.Transparent))
            ),
            shape = CardDefaults.shape(RoundedCornerShape(0.dp)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isSelected) NuvioTheme.colors.Primary
                           else if (isFocused) NuvioTheme.colors.TextPrimary
                           else NuvioTheme.colors.TextSecondary
                )
                Text(
                    text = playlist.name,
                    color = if (isSelected) NuvioTheme.colors.Primary
                            else if (isFocused) NuvioTheme.colors.TextPrimary
                            else NuvioTheme.colors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(Icons.Default.Check, null, Modifier.size(16.dp),
                        tint = NuvioTheme.colors.Primary)
                }
            }
        }
    }
}

// ─── Filter Chip ──────────────────────────────────────────────────────────────

@Composable
private fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.colors(
            containerColor = if (isSelected) NuvioTheme.colors.Primary
                            else NuvioTheme.colors.BackgroundElevated,
            focusedContainerColor = NuvioTheme.colors.FocusBackground
        ),
        shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
        scale = CardDefaults.scale(focusedScale = 1.0f),
        modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
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

// ─── Channel Card ─────────────────────────────────────────────────────────────

@Composable
private fun TvChannelCard(
    channel: LiveTvChannel,
    isFavorite: Boolean,
    focusRequester: FocusRequester? = null,
    isFirstRow: Boolean = false,
    isLastRow: Boolean = false,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRequestNavigateToCategory: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        onLongClick = onToggleFavorite,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.25f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                val code = keyEvent.nativeKeyEvent.keyCode
                val action = keyEvent.nativeKeyEvent.action
                when {
                    action == AndroidKeyEvent.ACTION_DOWN &&
                    code == AndroidKeyEvent.KEYCODE_DPAD_UP && isFirstRow -> {
                        onRequestNavigateToCategory(); true
                    }
                    action == AndroidKeyEvent.ACTION_DOWN &&
                    code == AndroidKeyEvent.KEYCODE_DPAD_DOWN && isLastRow -> {
                        true
                    }
                    action == AndroidKeyEvent.ACTION_UP && code in listOf(
                        AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_STAR,
                        AndroidKeyEvent.KEYCODE_BUTTON_Y, AndroidKeyEvent.KEYCODE_BOOKMARK,
                        AndroidKeyEvent.KEYCODE_PROG_YELLOW
                    ) -> { onToggleFavorite(); true }
                    else -> false
                }
            },
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
        scale = CardDefaults.scale(focusedScale = 1.0f)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = channel.name,
                    modifier = Modifier.fillMaxSize().padding(12.dp).padding(bottom = 28.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(Modifier.fillMaxSize().padding(bottom = 28.dp), Alignment.Center) {
                    Icon(Icons.Default.LiveTv, null, Modifier.size(36.dp),
                        tint = NuvioTheme.colors.TextMuted)
                }
            }
            // Favorite badge
            if (isFavorite) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)).padding(4.dp)) {
                    Icon(Icons.Default.Star, "Favorite", Modifier.size(16.dp), tint = Color(0xFFFFD700))
                }
            } else if (isFocused) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)).padding(4.dp)) {
                    Icon(Icons.Default.Star, "Add to favorite", Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.6f))
                }
            }
            // Channel name bar
            Box(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(channel.name, color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}
