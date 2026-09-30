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
import androidx.compose.foundation.layout.heightIn
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

internal enum class FilterType {
    ALL, FAVORITES, RECENT, GROUP, PLAYLIST
}

// ─── Entry point ─────────────────────────────────────────────────────────────

@Composable
fun LiveTvScreen(
    onChannelSelected: (LiveTvChannel) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    LaunchedEffect(Unit) { LiveTvRepository.onScreenEntered() }
    val state by LiveTvRepository.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        when {
            state.isLoading -> LiveTvLoadingState()
            !state.hasPlaylist -> LiveTvEmptyState(onNavigateToSettings)
            state.errorMessage != null && state.channels.isEmpty() -> LiveTvErrorState(
                message = state.errorMessage!!,
                onRetry = { LiveTvRepository.refresh(force = true, showLoadingIfHasChannels = true) },
                onNavigateToSettings = onNavigateToSettings
            )
            else -> LiveTvContent(
                state = state,
                onChannelSelected = { channel ->
                    LiveTvRepository.recordLastWatched(channel)
                    onChannelSelected(channel)
                },
                onToggleFavorite = { LiveTvRepository.toggleFavoriteChannel(it) },
                onRefresh = { LiveTvRepository.refresh(force = true, showLoadingIfHasChannels = true) },
                onNavigateToSettings = onNavigateToSettings
            )
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
    var activeFilter by rememberSaveable { mutableStateOf(FilterType.RECENT) }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var isInitialEntry by rememberSaveable { mutableStateOf(true) }
    var showPlaylistDropdown by remember { mutableStateOf(false) }
    var launchingChannelId by remember { mutableStateOf<String?>(null) }

    // Lắng nghe tín hiệu điều hướng từ Sidebar để luôn trở về giao diện mặc định
    val resetTrigger by LiveTvRepository.navigationResetEvent.collectAsState()
    var lastHandledResetTrigger by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger != 0L && resetTrigger != lastHandledResetTrigger) {
            lastHandledResetTrigger = resetTrigger
            selectedPlaylistId = null
            selectedGroup = null
            showPlaylistDropdown = false
            activeFilter = if (state.recentChannelIds.isNotEmpty()) FilterType.RECENT else FilterType.FAVORITES
            isInitialEntry = true
        }
    }

    // Kênh thuộc phạm vi playlist đã chọn (rỗng nếu chưa chọn playlist cụ thể)
    val playlistScopedChannels = remember(state.channels, selectedPlaylistId) {
        if (selectedPlaylistId == null) {
            emptyList()
        } else {
            state.channels.filter { it.playlistId == selectedPlaylistId }
        }
    }

    // Các mục (nhóm) CHỈ thuộc về danh sách đang chọn
    val allGroups = remember(playlistScopedChannels, selectedPlaylistId) {
        if (selectedPlaylistId == null) {
            emptyList()
        } else {
            playlistScopedChannels.mapNotNull { it.group?.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        }
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
        state.favoriteChannelIds, state.recentChannelIds, playlistScopedChannels
    ) {
        val channelMap = state.channels.associateBy { it.id }
        if (selectedPlaylistId == null) {
            // Khi chưa chọn danh sách phát: CHỈ hiển thị Gần đây hoặc Yêu thích
            when (activeFilter) {
                FilterType.FAVORITES -> state.channels.filter { it.id in state.favoriteChannelIds }
                FilterType.RECENT -> state.recentChannelIds.mapNotNull { channelMap[it] }
                else -> {
                    if (state.recentChannelIds.isNotEmpty()) {
                        state.recentChannelIds.mapNotNull { channelMap[it] }
                    } else {
                        state.channels.filter { it.id in state.favoriteChannelIds }
                    }
                }
            }
        } else {
            // Khi đã chọn danh sách phát cụ thể
            when (activeFilter) {
                FilterType.ALL, FilterType.PLAYLIST -> playlistScopedChannels
                FilterType.GROUP -> {
                    playlistScopedChannels.filter { it.group?.trim() == selectedGroup }
                }
                FilterType.FAVORITES -> state.channels.filter { it.id in state.favoriteChannelIds }
                FilterType.RECENT -> state.recentChannelIds.mapNotNull { channelMap[it] }
            }
        }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }

    val displayedChannels = remember(filteredChannels, searchQuery) {
        if (searchQuery.isBlank()) {
            filteredChannels
        } else {
            val query = searchQuery.trim()
            filteredChannels.filter { channel ->
                channel.name.contains(query, ignoreCase = true) ||
                channel.group?.contains(query, ignoreCase = true) == true
            }
        }
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

    LaunchedEffect(displayedChannels.map { it.id }) {
        channelFocusRequesters.keys.retainAll(displayedChannels.map { it.id }.toSet())
    }

    // Restore focus khi quay lại từ player
    LaunchedEffect(state.lastWatchedChannelId, displayedChannels, isInitialEntry) {
        val targetId = state.lastWatchedChannelId
        if (!isInitialEntry && targetId != null && targetId != restoredChannelId) {
            val idx = displayedChannels.indexOfFirst { it.id == targetId }
            if (idx >= 0) {
                gridState.scrollToItem(idx)
                delay(100)
                runCatching { channelFocusRequesters[targetId]?.requestFocus() }
                restoredChannelId = targetId
            }
        }
    }

    // Focus ban đầu khi vào màn hình: nếu chưa chọn danh sách phát thì ưu tiên Recent -> Favorites -> nút chọn Playlist
    LaunchedEffect(isInitialEntry, state.recentChannelIds, state.favoriteChannelIds, selectedPlaylistId) {
        if (isInitialEntry) {
            delay(120)
            if (selectedPlaylistId == null) {
                if (state.recentChannelIds.isNotEmpty()) {
                    activeFilter = FilterType.RECENT
                    runCatching { recentChipFocusRequester.requestFocus() }
                } else if (state.favoriteChannelIds.isNotEmpty()) {
                    activeFilter = FilterType.FAVORITES
                    runCatching { favoritesChipFocusRequester.requestFocus() }
                } else {
                    activeFilter = FilterType.RECENT
                    runCatching { playlistDropdownButtonFocusRequester.requestFocus() }
                }
            } else {
                activeFilter = FilterType.PLAYLIST
                runCatching { allChipFocusRequester.requestFocus() }
            }
            isInitialEntry = false
        }
    }

    // Helper navigate về chip hiện tại với scroll an toàn và fallback
    suspend fun navigateToActiveChip() {
        showPlaylistDropdown = false
        val hasRecent = state.recentChannelIds.isNotEmpty() || selectedPlaylistId == null

        val (targetIndex, getTargetRequester) = if (selectedPlaylistId == null) {
            when (activeFilter) {
                FilterType.RECENT -> (if (hasRecent) 1 else 0) to { recentChipFocusRequester }
                FilterType.FAVORITES -> (if (hasRecent) 2 else 1) to { favoritesChipFocusRequester }
                else -> 0 to { playlistDropdownButtonFocusRequester }
            }
        } else {
            val isAllSelected = (activeFilter == FilterType.ALL || activeFilter == FilterType.PLAYLIST) && selectedGroup == null
            when {
                isAllSelected -> 1 to { allChipFocusRequester }
                activeFilter == FilterType.RECENT && hasRecent -> 2 to { recentChipFocusRequester }
                activeFilter == FilterType.FAVORITES -> (if (hasRecent) 3 else 2) to { favoritesChipFocusRequester }
                activeFilter == FilterType.GROUP -> {
                    val groupIdx = selectedGroup?.let { allGroups.indexOf(it) } ?: -1
                    val baseIndex = 1 + (if (hasRecent) 1 else 0) + 1
                    val idx = if (groupIdx >= 0) baseIndex + groupIdx else 1
                    val req = { selectedGroup?.let { groupChipFocusRequesters.getOrPut(it) { FocusRequester() } } ?: allChipFocusRequester }
                    idx to req
                }
                else -> 0 to { playlistDropdownButtonFocusRequester }
            }
        }

        // Kiểm tra nếu chip đã visible trong viewport của LazyRow, nếu chưa thì cuộn đến
        val isVisible = filterChipsListState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
        if (!isVisible) {
            runCatching { filterChipsListState.scrollToItem(targetIndex) }
        }

        val req = getTargetRequester()
        val focused = runCatching {
            req.requestFocus()
            true
        }.getOrDefault(false)

        if (!focused) {
            delay(20)
            val retryOk = runCatching { req.requestFocus(); true }.getOrDefault(false)
            if (!retryOk) {
                runCatching { filterChipsListState.scrollToItem(0) }
                delay(30)
                runCatching { playlistDropdownButtonFocusRequester.requestFocus() }
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
            LiveTvHeader(
                channelCount = displayedChannels.size,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                isSearchOpen = isSearchOpen,
                onToggleSearch = {
                    isSearchOpen = !isSearchOpen
                    if (!isSearchOpen) searchQuery = ""
                },
                onRefresh = onRefresh,
                onNavigateToSettings = onNavigateToSettings,
                onRequestNavigateToFilters = {
                    coroutineScope.launch { navigateToActiveChip() }
                }
            )

            // ── Filter Chips + Playlist Dropdown Button ──
            LazyRow(
                state = filterChipsListState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                userScrollEnabled = true
            ) {
                // 1. Playlist Dropdown Button — nút chọn danh sách
                item(key = "playlist_dropdown") {
                    PlaylistDropdownButton(
                        selectedName = selectedPlaylistName,
                        isActive = showPlaylistDropdown,
                        isOpen = showPlaylistDropdown,
                        focusRequester = playlistDropdownButtonFocusRequester,
                        onClick = {
                            showPlaylistDropdown = !showPlaylistDropdown
                            isInitialEntry = false
                        }
                    )
                }

                // 2. Chip Tất cả kênh — CHỈ HIỆN KHI ĐÃ CHỌN PLAYLIST
                if (selectedPlaylistId != null) {
                    item(key = "all_channels_chip") {
                        val isAllSelected = (activeFilter == FilterType.ALL || activeFilter == FilterType.PLAYLIST) && selectedGroup == null
                        FilterChipItem(
                            label = stringResource(R.string.livetv_group_all),
                            isSelected = isAllSelected,
                            onClick = {
                                activeFilter = FilterType.PLAYLIST
                                selectedGroup = null
                                showPlaylistDropdown = false
                                isInitialEntry = false
                            },
                            focusRequester = allChipFocusRequester
                        )
                    }
                }

                // 3. RECENT - Xem gần đây
                if (state.recentChannelIds.isNotEmpty() || selectedPlaylistId == null) {
                    item(key = "recent") {
                        FilterChipItem(
                            label = stringResource(R.string.livetv_group_recent),
                            isSelected = activeFilter == FilterType.RECENT,
                            onClick = {
                                activeFilter = FilterType.RECENT
                                selectedGroup = null
                                showPlaylistDropdown = false
                                isInitialEntry = false
                            },
                            focusRequester = recentChipFocusRequester
                        )
                    }
                }

                // 4. FAVORITES - Kênh yêu thích
                item(key = "favorites") {
                    FilterChipItem(
                        label = stringResource(R.string.livetv_group_favorites),
                        isSelected = activeFilter == FilterType.FAVORITES,
                        onClick = {
                            activeFilter = FilterType.FAVORITES
                            selectedGroup = null
                            showPlaylistDropdown = false
                            isInitialEntry = false
                        },
                        focusRequester = favoritesChipFocusRequester
                    )
                }

                // 5. GROUP chips - CHỈ CÁC NHÓM CỦA DANH SÁCH ĐANG CHỌN (selectedPlaylistId != null)
                if (selectedPlaylistId != null) {
                    items(allGroups, key = { "group_$it" }) { group ->
                        val req = remember(group) { groupChipFocusRequesters.getOrPut(group) { FocusRequester() } }
                        FilterChipItem(
                            label = group,
                            isSelected = activeFilter == FilterType.GROUP && selectedGroup == group,
                            onClick = {
                                activeFilter = FilterType.GROUP
                                selectedGroup = group
                                showPlaylistDropdown = false
                                isInitialEntry = false
                            },
                            focusRequester = req
                        )
                    }
                }
            }

            // ── Channels Grid ──
            if (displayedChannels.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) {
                    LiveTvEmptyChannelState(
                        activeFilter = activeFilter,
                        hasPlaylistSelected = (selectedPlaylistId != null),
                        onSelectPlaylist = { showPlaylistDropdown = true }
                    )
                }
            } else {
                LiveTvChannelGrid(
                    channels = displayedChannels,
                    favoriteChannelIds = state.favoriteChannelIds,
                    launchingChannelId = launchingChannelId,
                    gridState = gridState,
                    channelFocusRequesters = channelFocusRequesters,
                    onChannelClick = { channel ->
                        showPlaylistDropdown = false
                        restoredChannelId = null
                        launchingChannelId = channel.id
                        onChannelSelected(channel)
                    },
                    onToggleFavorite = { onToggleFavorite(it) },
                    onRequestNavigateToCategory = {
                        coroutineScope.launch {
                            runCatching { gridState.animateScrollToItem(0) }
                            navigateToActiveChip()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Playlist Dropdown Panel (overlay) ──
        AnimatedVisibility(
            visible = showPlaylistDropdown,
            enter = fadeIn() + slideInVertically { -it / 3 },
            exit = fadeOut() + slideOutVertically { -it / 3 },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 124.dp, start = 28.dp)
                .zIndex(10f)
        ) {
            PlaylistDropdownPanel(
                playlists = allPlaylists,
                selectedPlaylistId = selectedPlaylistId,
                onDefaultViewSelected = {
                    selectedPlaylistId = null
                    selectedGroup = null
                    activeFilter = if (state.recentChannelIds.isNotEmpty()) FilterType.RECENT else FilterType.FAVORITES
                    showPlaylistDropdown = false
                    isInitialEntry = false
                    coroutineScope.launch {
                        delay(60)
                        playlistDropdownButtonFocusRequester.requestFocus()
                    }
                },
                onPlaylistSelected = { playlist ->
                    activeFilter = FilterType.PLAYLIST
                    selectedPlaylistId = playlist.id
                    selectedGroup = null
                    showPlaylistDropdown = false
                    isInitialEntry = false
                    coroutineScope.launch {
                        delay(60)
                        allChipFocusRequester.requestFocus()
                    }
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

