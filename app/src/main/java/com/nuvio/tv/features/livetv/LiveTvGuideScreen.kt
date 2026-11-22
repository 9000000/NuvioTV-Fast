package com.nuvio.tv.features.livetv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.LocalOpenSidebar
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class GridScrollRequest(
    val channelIndex: Int,
    val groupId: String,
    val playlistId: String?,
    val focusArea: GuideFocusArea,
    val channelCount: Int
)

private fun resolveGridScrollAction(
    gridState: LazyGridState,
    layoutInfo: LazyGridLayoutInfo,
    targetIndex: Int,
    verticalSpacingPx: Int
): GridScrollAction = computeGridScrollAction(
    targetIndex = targetIndex,
    totalItemsCount = layoutInfo.totalItemsCount,
    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
    visibleItems = layoutInfo.visibleItemsInfo.map {
        GridVisibleItemGeometry(it.index, it.offset.y, it.size.height)
    },
    viewportStartOffset = layoutInfo.viewportStartOffset,
    viewportEndOffset = layoutInfo.viewportEndOffset,
    afterContentPadding = layoutInfo.afterContentPadding,
    verticalSpacingPx = verticalSpacingPx
)

@Composable
fun LiveTvGuideScreen(
    onChannelSelected: (LiveTvChannel) -> Unit,
    onNavigateToSettings: () -> Unit = {},
    viewModel: LiveTvViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playback by viewModel.playbackState.collectAsStateWithLifecycle()
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    val user by viewModel.userState.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()

    // Stop playback immediately when leaving the screen or backgrounding the app.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        viewModel.playback.attach()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.stopPlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPlayback()
            viewModel.playback.detach()
        }
    }

    val view = LocalView.current
    DisposableEffect(view, playback.channelKey) {
        view.keepScreenOn = playback.channelKey != null
        onDispose { view.keepScreenOn = false }
    }

    when {
        ui.isLoading && ui.channels.isEmpty() -> LiveTvLoadingState()
        !ui.hasPlaylist -> LiveTvEmptyState(onNavigateToSettings)
        else -> {
            LiveTvGuideContent(
                viewModel = viewModel,
                ui = ui,
                settings = settings,
                playback = playback,
                programs = programs,
                user = user,
                now = now
            )
        }
    }
}

@Composable
private fun LiveTvGuideContent(
    viewModel: LiveTvViewModel,
    ui: LiveTvUiState,
    settings: LiveTvSettings,
    playback: LivePlaybackState,
    programs: Map<String, List<EpgProgram>>,
    user: LiveUserState,
    now: Long
) {
    val context = LocalContext.current
    val openSidebar = LocalOpenSidebar.current
    val rawChannels = ui.channels
    val favoriteSet = remember(ui.favoriteChannelIds, user.favorites) {
        if (ui.favoriteChannelIds.isNotEmpty()) ui.favoriteChannelIds else user.favorites.toSet()
    }
    val recentIds = remember(ui.recentChannelIds, user.recent) {
        if (ui.recentChannelIds.isNotEmpty()) ui.recentChannelIds else user.recent
    }

    val allPlaylistsLabel = stringResource(R.string.livetv_playlist_all)
    val favLabel = stringResource(R.string.livetv_group_favorites)
    val recentLabel = stringResource(R.string.livetv_group_recent)
    val allChannelsLabel = stringResource(R.string.livetv_group_all)

    val playlistOptions = remember(ui, allPlaylistsLabel) {
        buildLiveTvPlaylistOptions(ui, allPlaylistsLabel)
    }

    // Restore the last selected playlist filter; default category on entry follows FAVORITES -> RECENT -> ALL.
    var selectedPlaylistId by rememberSaveable { mutableStateOf(LiveTvStorage.loadGuidePlaylistId()) }
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }

    // Only clear a persisted playlist selection once channels are loaded and it truly no longer exists.
    LaunchedEffect(rawChannels.isNotEmpty(), ui.isLoading, playlistOptions, selectedPlaylistId) {
        if (rawChannels.isNotEmpty() && !ui.isLoading &&
            selectedPlaylistId != null &&
            playlistOptions.none { it.id == selectedPlaylistId }
        ) {
            selectedPlaylistId = null
            LiveTvStorage.saveGuidePlaylistId(null)
        }
    }

    val scopedChannels = remember(rawChannels, selectedPlaylistId) {
        scopeChannelsByPlaylist(rawChannels, selectedPlaylistId)
    }

    val groups = remember(scopedChannels, rawChannels, favoriteSet, recentIds, favLabel, recentLabel, allChannelsLabel) {
        buildLiveTvChannelGroups(
            scopedChannels = scopedChannels,
            favoriteIds = favoriteSet,
            recentIds = recentIds,
            favoritesLabel = favLabel,
            recentLabel = recentLabel,
            allLabel = allChannelsLabel,
            allChannels = rawChannels
        )
    }

    val effectiveGroupId = remember(groups, selectedGroupId, favoriteSet, recentIds, rawChannels) {
        if (selectedGroupId != null) {
            resolveValidGroupId(groups, selectedGroupId)
        } else {
            resolveDefaultGuideGroupSelection(
                groups = groups,
                allChannels = rawChannels,
                favoriteIds = favoriteSet,
                recentIds = recentIds
            ).first
        }
    }

    val channels = remember(scopedChannels, rawChannels, effectiveGroupId, favoriteSet, recentIds) {
        filterChannelsForGroup(
            scopedChannels = scopedChannels,
            groupId = effectiveGroupId,
            favoriteIds = favoriteSet,
            recentIds = recentIds,
            allChannels = rawChannels
        )
    }

    fun findTargetChannelIndex(targetChannels: List<LiveTvChannel>): Int {
        if (targetChannels.isEmpty()) return 0
        val lastWatchedId = playback.channelKey ?: ui.lastWatchedChannelId ?: user.lastChannelKey
        if (lastWatchedId != null) {
            val idx = targetChannels.indexOfFirst { it.id == lastWatchedId || it.key == lastWatchedId }
            if (idx >= 0) return idx
        }
        return 0
    }

    var focusArea by remember { mutableStateOf(GuideFocusArea.CATEGORY) }
    // drawerIndex 0 is the "Channel List" picker; indices >= 1 map to groups[drawerIndex - 1].
    var drawerIndex by remember {
        mutableIntStateOf((groups.indexOfFirst { it.id == effectiveGroupId }.coerceAtLeast(0)) + 1)
    }
    var hasSyncedInitialGroupDrawer by rememberSaveable { mutableStateOf(false) }
    var channelIndex by remember(rawChannels) {
        mutableIntStateOf(findTargetChannelIndex(channels))
    }
    var epgIndex by remember { mutableIntStateOf(0) }

    var showPlaylistPicker by remember { mutableStateOf(false) }
    var playlistPickerFocusIndex by remember { mutableIntStateOf(0) }

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var showOsd by remember { mutableStateOf(true) }
    var showQuickChannels by remember { mutableStateOf(false) }

    LaunchedEffect(rawChannels.isNotEmpty(), groups, favoriteSet, recentIds) {
        if (rawChannels.isNotEmpty() && (!hasSyncedInitialGroupDrawer || selectedGroupId == null)) {
            val (defaultGroupId, defaultDrawerIdx) = resolveDefaultGuideGroupSelection(
                groups = groups,
                allChannels = rawChannels,
                favoriteIds = favoriteSet,
                recentIds = recentIds
            )
            selectedGroupId = defaultGroupId
            LiveTvStorage.saveGuideGroupId(defaultGroupId)
            drawerIndex = defaultDrawerIdx
            focusArea = GuideFocusArea.CATEGORY
            val targetChannels = filterChannelsForGroup(
                scopedChannels = scopedChannels,
                groupId = defaultGroupId,
                favoriteIds = favoriteSet,
                recentIds = recentIds,
                allChannels = rawChannels
            )
            channelIndex = findTargetChannelIndex(targetChannels)
            hasSyncedInitialGroupDrawer = true
        }
    }

    val resetTrigger by LiveTvRepository.navigationResetEvent.collectAsStateWithLifecycle()
    var lastHandledResetTrigger by rememberSaveable { mutableLongStateOf(0L) }

    LaunchedEffect(resetTrigger, rawChannels.isNotEmpty(), groups, favoriteSet, recentIds) {
        if (resetTrigger != 0L && resetTrigger != lastHandledResetTrigger) {
            if (rawChannels.isNotEmpty()) {
                lastHandledResetTrigger = resetTrigger
                val (defaultGroupId, defaultDrawerIdx) = resolveDefaultGuideGroupSelection(
                    groups = groups,
                    allChannels = rawChannels,
                    favoriteIds = favoriteSet,
                    recentIds = recentIds
                )
                selectedGroupId = defaultGroupId
                LiveTvStorage.saveGuideGroupId(defaultGroupId)
                drawerIndex = defaultDrawerIdx
                focusArea = GuideFocusArea.CATEGORY
                showPlaylistPicker = false
                showQuickChannels = false
                isFullscreen = false
                val targetChannels = filterChannelsForGroup(
                    scopedChannels = scopedChannels,
                    groupId = defaultGroupId,
                    favoriteIds = favoriteSet,
                    recentIds = recentIds,
                    allChannels = rawChannels
                )
                channelIndex = findTargetChannelIndex(targetChannels)
                hasSyncedInitialGroupDrawer = true
            } else {
                selectedGroupId = null
                hasSyncedInitialGroupDrawer = false
            }
        }
    }

    val guideFocusRequester = remember { FocusRequester() }
    val fullscreenFocusRequester = remember { FocusRequester() }
    val drawerListState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val epgListState = rememberLazyListState()
    val quickChannelListState = rememberLazyListState()
    val playlistPickerListState = rememberLazyListState()

    val coroutineScope = rememberCoroutineScope()
    var okPressJob by remember { mutableStateOf<Job?>(null) }
    var isLongPressHandled by remember { mutableStateOf(false) }
    var ignoreNextOkKeyUp by remember { mutableStateOf(false) }
    var favoriteToastMessage by remember { mutableStateOf<String?>(null) }
    var lastGridDpadTime by remember { mutableLongStateOf(0L) }
    var isRapidGridDpad by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val verticalSpacingPx = remember(density) { with(density) { LIVE_TV_GUIDE_GRID_SPACING.roundToPx() } }
    val latestChannels by rememberUpdatedState(channels)

    fun markGridDpadPress() {
        val nowMs = System.currentTimeMillis()
        isRapidGridDpad = (nowMs - lastGridDpadTime) < 180L
        lastGridDpadTime = nowMs
    }

    val gridFocusedChannel = channels.getOrNull(channelIndex)
    val playingChannel = remember(rawChannels, playback.channelKey) {
        rawChannels.firstOrNull { it.key == playback.channelKey }
    }
    val activeChannel = gridFocusedChannel ?: playingChannel

    val minuteBucket = now / 60_000L
    val activeChannelPrograms = remember(activeChannel?.key, programs[activeChannel?.key], minuteBucket) {
        val ch = activeChannel ?: return@remember emptyList()
        val raw = programs[ch.key].takeIf { !it.isNullOrEmpty() } ?: SyntheticEpgGenerator.generate(ch, now)
        filterVerticalEpgPrograms(raw, now)
    }

    LaunchedEffect(favoriteToastMessage) {
        if (favoriteToastMessage != null) {
            delay(2_500L)
            favoriteToastMessage = null
        }
    }

    LaunchedEffect(isFullscreen) {
        delay(60L)
        runCatching {
            if (isFullscreen) fullscreenFocusRequester.requestFocus()
            else guideFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(showOsd, channelIndex, isFullscreen) {
        if (isFullscreen && showOsd && !showQuickChannels) {
            delay(5_000L)
            showOsd = false
        }
    }

    LaunchedEffect(showQuickChannels, channelIndex) {
        if (showQuickChannels && channels.isNotEmpty()) {
            quickChannelListState.scrollToItem((channelIndex - 2).coerceAtLeast(0))
        }
    }

    // Keep Category list scrolled comfortably around focused group.
    LaunchedEffect(drawerIndex, groups.size) {
        if (drawerIndex <= 1 || groups.isEmpty()) {
            drawerListState.scrollToItem(0)
        } else {
            val groupIdx = (drawerIndex - 1).coerceIn(0, groups.lastIndex)
            drawerListState.scrollToItem((groupIdx - 2).coerceAtLeast(0))
        }
    }

    LaunchedEffect(showPlaylistPicker, playlistPickerFocusIndex) {
        if (showPlaylistPicker && playlistOptions.isNotEmpty()) {
            playlistPickerListState.scrollToItem(playlistPickerFocusIndex.coerceIn(0, playlistOptions.lastIndex))
        }
    }

    // Keep focused grid tile completely visible (upward & downward) without breaking fast D-pad hold.
    LaunchedEffect(gridState, isFullscreen) {
        if (isFullscreen) return@LaunchedEffect
        var previousContext: Pair<String, String?>? = null

        snapshotFlow {
            GridScrollRequest(
                channelIndex = channelIndex,
                groupId = effectiveGroupId,
                playlistId = selectedPlaylistId,
                focusArea = focusArea,
                channelCount = latestChannels.size
            )
        }.collectLatest { req ->
            if (req.channelCount <= 0) return@collectLatest
            val safeIdx = req.channelIndex.coerceIn(0, req.channelCount - 1)
            if (safeIdx != channelIndex) channelIndex = safeIdx

            val contextKey = req.groupId to req.playlistId
            val isContextReset = previousContext != contextKey

            val readyLayout = gridState.layoutInfo.takeIf {
                it.totalItemsCount == req.channelCount && it.viewportSize.height > 0
            } ?: snapshotFlow { gridState.layoutInfo }.first {
                it.totalItemsCount == req.channelCount && it.viewportSize.height > 0
            }

            val action = resolveGridScrollAction(gridState, readyLayout, safeIdx, verticalSpacingPx)

            val snapImmediately = isContextReset || isRapidGridDpad
            when (action) {
                GridScrollAction.None -> Unit
                is GridScrollAction.ScrollToIndex -> {
                    if (snapImmediately) {
                        gridState.scrollToItem(action.index)
                        withFrameNanos { }
                    } else {
                        gridState.animateScrollToItem(action.index)
                    }
                    val followUp = resolveGridScrollAction(
                        gridState,
                        gridState.layoutInfo,
                        safeIdx,
                        verticalSpacingPx
                    )
                    if (followUp is GridScrollAction.ScrollByOffset) {
                        gridState.scrollBy(followUp.deltaPx)
                    }
                }
                is GridScrollAction.ScrollByOffset -> {
                    if (snapImmediately) {
                        gridState.scrollBy(action.deltaPx)
                    } else {
                        gridState.animateScrollBy(action.deltaPx, tween(durationMillis = 120))
                    }
                }
            }
            previousContext = contextKey
        }
    }

    // Reset EPG list scroll to the live program whenever the active channel changes.
    LaunchedEffect(activeChannel?.key, focusArea) {
        if (focusArea != GuideFocusArea.VERTICAL_EPG && activeChannelPrograms.isNotEmpty()) {
            val defaultIdx = findDefaultEpgIndex(activeChannelPrograms, now)
            epgIndex = defaultIdx
            epgListState.scrollToItem((defaultIdx - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(epgIndex, focusArea) {
        if (focusArea == GuideFocusArea.VERTICAL_EPG && activeChannelPrograms.isNotEmpty()) {
            val safeEpg = epgIndex.coerceIn(0, activeChannelPrograms.lastIndex)
            epgListState.animateScrollToItem((safeEpg - 2).coerceAtLeast(0))
        }
    }

    // Pre-resolve focused channel in background for instant playback on OK.
    LaunchedEffect(channelIndex, channels, focusArea, showQuickChannels) {
        if ((focusArea == GuideFocusArea.CHANNEL_GRID || showQuickChannels) && channels.isNotEmpty()) {
            val ch = channels.getOrNull(channelIndex) ?: return@LaunchedEffect
            delay(220L)
            withContext(Dispatchers.IO) {
                runCatching { LiveTvRepository.prepareForPlayback(ch) }
            }
        }
    }

    fun syncDrawerToSelectedGroup() {
        val groupPos = groups.indexOfFirst { it.id == effectiveGroupId }
        drawerIndex = if (groupPos >= 0) groupPos + 1 else 1
    }

    fun selectGroup(groupIdx: Int, moveFocusToGrid: Boolean) {
        val grp = groups.getOrNull(groupIdx) ?: return
        selectedGroupId = grp.id
        LiveTvStorage.saveGuideGroupId(grp.id)
        drawerIndex = groupIdx + 1
        val nextChannels = filterChannelsForGroup(
            scopedChannels = scopedChannels,
            groupId = grp.id,
            favoriteIds = favoriteSet,
            recentIds = recentIds,
            allChannels = rawChannels
        )
        channelIndex = findTargetChannelIndex(nextChannels)
        if (moveFocusToGrid) {
            focusArea = GuideFocusArea.CHANNEL_GRID
        }
    }

    fun applyPlaylistOption(option: LiveTvPlaylistOption) {
        selectedPlaylistId = option.id
        LiveTvStorage.saveGuidePlaylistId(option.id)
        val nextScoped = scopeChannelsByPlaylist(rawChannels, option.id)
        val nextGroups = buildLiveTvChannelGroups(
            scopedChannels = nextScoped,
            favoriteIds = favoriteSet,
            recentIds = recentIds,
            favoritesLabel = favLabel,
            recentLabel = recentLabel,
            allLabel = allChannelsLabel,
            allChannels = rawChannels
        )
        val nextGroupId = resolveValidGroupId(nextGroups, selectedGroupId)
        selectedGroupId = nextGroupId
        LiveTvStorage.saveGuideGroupId(nextGroupId)
        val nextChannels = filterChannelsForGroup(
            scopedChannels = nextScoped,
            groupId = nextGroupId,
            favoriteIds = favoriteSet,
            recentIds = recentIds,
            allChannels = rawChannels
        )
        channelIndex = findTargetChannelIndex(nextChannels)
        drawerIndex = 0
        showPlaylistPicker = false
    }

    fun openPlaylistPicker() {
        val currentIdx = playlistOptions.indexOfFirst { it.id == selectedPlaylistId }.coerceAtLeast(0)
        playlistPickerFocusIndex = currentIdx
        showPlaylistPicker = true
    }

    fun togglePlaylistPicker() {
        if (showPlaylistPicker) {
            showPlaylistPicker = false
        } else {
            openPlaylistPicker()
        }
    }

    fun toggleChannelFavorite(ch: LiveTvChannel) {
        val wasFav = ch.id in favoriteSet
        viewModel.toggleFavorite(ch)
        favoriteToastMessage = context.getString(
            if (wasFav) R.string.livetv_favorite_removed else R.string.livetv_favorite_added,
            ch.name
        )
    }

    fun persistCurrentSelection(
        playlistId: String? = selectedPlaylistId,
        groupId: String = effectiveGroupId
    ) {
        LiveTvStorage.saveGuidePlaylistId(playlistId)
        LiveTvStorage.saveGuideGroupId(groupId)
    }

    fun playProgramOrCatchup(ch: LiveTvChannel, prog: EpgProgram) {
        persistCurrentSelection()
        val catchupInfo = ch.catchup
        val isPastCatchup = prog.stopMs <= now &&
            catchupInfo != null &&
            CatchupUrlBuilder.isAvailable(catchupInfo, prog.startMs, now)
        if (isPastCatchup) {
            val catchupUrl = CatchupUrlBuilder.build(
                liveUrl = ch.url,
                catchup = catchupInfo,
                startMs = prog.startMs,
                stopMs = prog.stopMs,
                nowMs = now
            )
            if (catchupUrl != null) {
                viewModel.play(ch.copy(streamUrl = catchupUrl), effectiveGroupId)
            } else {
                viewModel.play(ch, effectiveGroupId)
            }
        } else if (playback.channelKey != ch.key) {
            viewModel.play(ch, effectiveGroupId)
        }
        isFullscreen = true
        showOsd = true
    }

    fun playChannelAction(ch: LiveTvChannel, forceFullscreen: Boolean = false) {
        persistCurrentSelection()
        if (!settings.showPreview || forceFullscreen || playback.channelKey == ch.key) {
            if (playback.channelKey != ch.key) {
                viewModel.play(ch, effectiveGroupId)
            }
            isFullscreen = true
            showOsd = true
        } else {
            viewModel.play(ch, effectiveGroupId)
        }
    }

    fun handleOkPressed() {
        when (focusArea) {
            GuideFocusArea.CATEGORY -> {
                if (drawerIndex == 0) {
                    openPlaylistPicker()
                } else {
                    // Keep focus on the category list; user presses RIGHT to enter the channel grid.
                    selectGroup(drawerIndex - 1, moveFocusToGrid = false)
                }
            }
            GuideFocusArea.CHANNEL_GRID -> {
                val ch = gridFocusedChannel ?: return
                playChannelAction(ch)
            }
            GuideFocusArea.PREVIEW_PLAYER -> {
                val targetCh = playingChannel ?: activeChannel ?: return
                playChannelAction(targetCh, forceFullscreen = playback.channelKey != null)
            }
            GuideFocusArea.VERTICAL_EPG -> {
                val ch = activeChannel ?: return
                val prog = activeChannelPrograms.getOrNull(epgIndex)
                if (prog != null) {
                    playProgramOrCatchup(ch, prog)
                } else {
                    playChannelAction(ch, forceFullscreen = true)
                }
            }
        }
    }

    if (isFullscreen) {
        FullscreenPlayerOverlay(
            viewModel = viewModel,
            channels = channels,
            playingChannel = playingChannel ?: activeChannel,
            programs = programs,
            playback = playback,
            selectedGroupId = effectiveGroupId,
            now = now,
            channelIndex = channelIndex,
            showOsd = showOsd,
            showQuickChannels = showQuickChannels,
            fullscreenFocusRequester = fullscreenFocusRequester,
            quickChannelListState = quickChannelListState,
            onChannelIndexChanged = { channelIndex = it },
            onShowOsdChanged = { showOsd = it },
            onShowQuickChannelsChanged = { showQuickChannels = it },
            onExitFullscreen = {
                isFullscreen = false
                showOsd = false
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101014))
            .focusRequester(guideFocusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter

                if (showPlaylistPicker) {
                    if (isOk) {
                        if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) {
                            ignoreNextOkKeyUp = true
                            playlistOptions.getOrNull(playlistPickerFocusIndex)?.let(::applyPlaylistOption)
                        } else if (e.type == KeyEventType.KeyUp) {
                            ignoreNextOkKeyUp = false
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                    return@onPreviewKeyEvent when {
                        e.key == Key.DirectionUp -> {
                            if (playlistPickerFocusIndex > 0) {
                                playlistPickerFocusIndex--
                            } else {
                                showPlaylistPicker = false
                            }
                            true
                        }
                        e.key == Key.DirectionDown -> {
                            if (playlistPickerFocusIndex < playlistOptions.lastIndex) playlistPickerFocusIndex++
                            true
                        }
                        e.key == Key.Back || e.key == Key.DirectionLeft || e.key == Key.DirectionRight -> {
                            showPlaylistPicker = false
                            true
                        }
                        else -> true
                    }
                }

                if (isOk) {
                    if (ignoreNextOkKeyUp) {
                        if (e.type == KeyEventType.KeyUp) {
                            ignoreNextOkKeyUp = false
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (e.type == KeyEventType.KeyDown) {
                        if (e.nativeKeyEvent.repeatCount == 0) {
                            isLongPressHandled = false
                            okPressJob?.cancel()
                            okPressJob = coroutineScope.launch {
                                delay(600L)
                                if (focusArea == GuideFocusArea.CHANNEL_GRID) {
                                    val ch = gridFocusedChannel
                                    if (ch != null) {
                                        isLongPressHandled = true
                                        toggleChannelFavorite(ch)
                                    }
                                }
                            }
                        }
                        return@onPreviewKeyEvent true
                    } else if (e.type == KeyEventType.KeyUp) {
                        okPressJob?.cancel()
                        if (isLongPressHandled) {
                            isLongPressHandled = false
                            return@onPreviewKeyEvent true
                        }
                        handleOkPressed()
                        return@onPreviewKeyEvent true
                    }
                }

                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (focusArea) {
                    GuideFocusArea.CATEGORY -> when {
                        e.key == Key.DirectionUp -> {
                            if (drawerIndex > 0) {
                                drawerIndex--
                            }
                            true
                        }
                        e.key == Key.DirectionDown -> {
                            if (drawerIndex < groups.size) {
                                drawerIndex++
                            }
                            true
                        }
                        e.key == Key.DirectionRight -> {
                            if (channels.isNotEmpty()) {
                                isRapidGridDpad = false
                                focusArea = GuideFocusArea.CHANNEL_GRID
                            }
                            true
                        }
                        e.key == Key.DirectionLeft -> {
                            if (openSidebar != null) {
                                openSidebar()
                                true
                            } else false
                        }
                        else -> false
                    }

                    GuideFocusArea.CHANNEL_GRID -> when {
                        e.key == Key.DirectionUp -> {
                            markGridDpadPress()
                            moveGridUp(channelIndex)?.let { channelIndex = it }
                            true
                        }
                        e.key == Key.DirectionDown -> {
                            markGridDpadPress()
                            moveGridDown(channelIndex, channels.size)?.let { channelIndex = it }
                            true
                        }
                        e.key == Key.DirectionLeft -> {
                            markGridDpadPress()
                            when (val res = moveGridLeft(channelIndex)) {
                                is GridMoveResult.MoveTo -> channelIndex = res.index
                                GridMoveResult.Boundary -> {
                                    syncDrawerToSelectedGroup()
                                    focusArea = GuideFocusArea.CATEGORY
                                }
                            }
                            true
                        }
                        e.key == Key.DirectionRight -> {
                            markGridDpadPress()
                            when (val res = moveGridRight(channelIndex, channels.size)) {
                                is GridMoveResult.MoveTo -> channelIndex = res.index
                                GridMoveResult.Boundary -> {
                                    val isTopRow = channelIndex < LIVE_TV_GUIDE_GRID_COLUMNS
                                    if (isTopRow && settings.showPreview) {
                                        focusArea = GuideFocusArea.PREVIEW_PLAYER
                                    } else if (activeChannelPrograms.isNotEmpty()) {
                                        focusArea = GuideFocusArea.VERTICAL_EPG
                                    } else if (settings.showPreview) {
                                        focusArea = GuideFocusArea.PREVIEW_PLAYER
                                    }
                                }
                            }
                            true
                        }
                        e.key == Key.Menu || e.key == Key.Bookmark -> {
                            gridFocusedChannel?.let(::toggleChannelFavorite)
                            true
                        }
                        e.key == Key.Back -> {
                            syncDrawerToSelectedGroup()
                            focusArea = GuideFocusArea.CATEGORY
                            true
                        }
                        else -> false
                    }

                    GuideFocusArea.PREVIEW_PLAYER -> when {
                        e.key == Key.DirectionDown -> {
                            if (activeChannelPrograms.isNotEmpty()) {
                                focusArea = GuideFocusArea.VERTICAL_EPG
                            }
                            true
                        }
                        e.key == Key.DirectionLeft || e.key == Key.Back -> {
                            if (channels.isNotEmpty()) {
                                focusArea = GuideFocusArea.CHANNEL_GRID
                            } else {
                                syncDrawerToSelectedGroup()
                                focusArea = GuideFocusArea.CATEGORY
                            }
                            true
                        }
                        else -> true
                    }

                    GuideFocusArea.VERTICAL_EPG -> when {
                        e.key == Key.DirectionUp -> {
                            if (epgIndex > 0) {
                                epgIndex--
                            } else if (settings.showPreview) {
                                focusArea = GuideFocusArea.PREVIEW_PLAYER
                            }
                            true
                        }
                        e.key == Key.DirectionDown -> {
                            if (epgIndex < activeChannelPrograms.lastIndex) {
                                epgIndex++
                            }
                            true
                        }
                        e.key == Key.DirectionLeft || e.key == Key.Back -> {
                            if (channels.isNotEmpty()) {
                                focusArea = GuideFocusArea.CHANNEL_GRID
                            } else {
                                syncDrawerToSelectedGroup()
                                focusArea = GuideFocusArea.CATEGORY
                            }
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left fixed column (Yellow frame): Channel List dropdown + Categories.
            LiveTvLeftPanel(
                playlistOptions = playlistOptions,
                selectedPlaylistId = selectedPlaylistId,
                isPlaylistExpanded = showPlaylistPicker,
                playlistPickerFocusIndex = playlistPickerFocusIndex,
                playlistPickerListState = playlistPickerListState,
                groups = groups,
                selectedGroupId = effectiveGroupId,
                drawerIndex = drawerIndex,
                isCategoryFocused = focusArea == GuideFocusArea.CATEGORY && !showPlaylistPicker,
                drawerListState = drawerListState,
                onTogglePlaylistPicker = {
                    focusArea = GuideFocusArea.CATEGORY
                    drawerIndex = 0
                    togglePlaylistPicker()
                },
                onSelectPlaylistOption = ::applyPlaylistOption,
                onSelectGroup = { idx ->
                    showPlaylistPicker = false
                    selectGroup(idx, moveFocusToGrid = false)
                }
            )

            VerticalDivider(color = Color(0x1EFFFFFF), thickness = 1.dp)

            // Middle column (Blue frame + Pink channel tiles): full-height 3-column channel grid.
            val currentGroupName = groups.firstOrNull { it.id == effectiveGroupId }?.name ?: allChannelsLabel
            val currentPlaylistLabel = selectedPlaylistId?.let { id ->
                playlistOptions.firstOrNull { it.id == id }?.name
            }
            LiveTvChannelGridPanel(
                channels = channels,
                groupTitle = currentGroupName,
                playlistLabel = currentPlaylistLabel,
                selectedGroupId = effectiveGroupId,
                favoriteIds = favoriteSet,
                playingChannelKey = playback.channelKey,
                focusedChannelIndex = channelIndex,
                isGridFocused = focusArea == GuideFocusArea.CHANNEL_GRID && !showPlaylistPicker,
                isRapidNavigation = isRapidGridDpad,
                showChannelNames = settings.showChannelNames,
                twoLineChannelNames = settings.twoLineChannelNames,
                showChannelNumbers = settings.showChannelNumbers,
                gridState = gridState,
                onChannelClick = { idx, ch ->
                    channelIndex = idx
                    focusArea = GuideFocusArea.CHANNEL_GRID
                    playChannelAction(ch)
                },
                onChannelLongClick = { idx, ch ->
                    channelIndex = idx
                    focusArea = GuideFocusArea.CHANNEL_GRID
                    toggleChannelFavorite(ch)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            VerticalDivider(color = Color(0x1EFFFFFF), thickness = 1.dp)

            // Right column: 16:9 Live Preview (Purple frame) + Vertical EPG schedule (Orange frame).
            LiveTvRightPanel(
                showPreview = settings.showPreview,
                playback = playback,
                player = viewModel.playback.player,
                isPreviewFocused = focusArea == GuideFocusArea.PREVIEW_PLAYER && !showPlaylistPicker,
                activeChannel = activeChannel,
                channelPrograms = activeChannelPrograms,
                now = now,
                epgIndex = epgIndex,
                isVerticalEpgFocused = focusArea == GuideFocusArea.VERTICAL_EPG && !showPlaylistPicker,
                epgListState = epgListState,
                onPreviewClick = {
                    focusArea = GuideFocusArea.PREVIEW_PLAYER
                    val targetCh = playingChannel ?: activeChannel ?: return@LiveTvRightPanel
                    playChannelAction(targetCh, forceFullscreen = playback.channelKey != null)
                },
                onProgramClick = { idx, prog ->
                    epgIndex = idx
                    focusArea = GuideFocusArea.VERTICAL_EPG
                    val ch = activeChannel ?: return@LiveTvRightPanel
                    playProgramOrCatchup(ch, prog)
                }
            )
        }

        AnimatedVisibility(
            visible = favoriteToastMessage != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xF0181822))
                    .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(18.dp)
                    )
                    LiveText(
                        text = favoriteToastMessage.orEmpty(),
                        size = 14.sp,
                        weight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenPlayerOverlay(
    viewModel: LiveTvViewModel,
    channels: List<LiveTvChannel>,
    playingChannel: LiveTvChannel?,
    programs: Map<String, List<EpgProgram>>,
    playback: LivePlaybackState,
    selectedGroupId: String,
    now: Long,
    channelIndex: Int,
    showOsd: Boolean,
    showQuickChannels: Boolean,
    fullscreenFocusRequester: FocusRequester,
    quickChannelListState: androidx.compose.foundation.lazy.LazyListState,
    onChannelIndexChanged: (Int) -> Unit,
    onShowOsdChanged: (Boolean) -> Unit,
    onShowQuickChannelsChanged: (Boolean) -> Unit,
    onExitFullscreen: () -> Unit
) {
    val activePrograms = playingChannel?.let { ch ->
        programs[ch.key].takeIf { !it.isNullOrEmpty() } ?: SyntheticEpgGenerator.generate(ch, now)
    }.orEmpty()
    val focusedProgram = activePrograms.firstOrNull { it.isLive(now) } ?: activePrograms.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(fullscreenFocusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                when {
                    e.key == Key.Back -> {
                        if (showQuickChannels) {
                            onShowQuickChannelsChanged(false)
                            val playingIdx = channels.indexOfFirst { it.key == playback.channelKey }
                            if (playingIdx >= 0) onChannelIndexChanged(playingIdx)
                        } else {
                            onExitFullscreen()
                        }
                        true
                    }
                    e.key == Key.DirectionUp -> {
                        if (showQuickChannels) {
                            if (channelIndex > 0) onChannelIndexChanged(channelIndex - 1)
                        } else if (channels.isNotEmpty()) {
                            val next = if (channelIndex > 0) channelIndex - 1 else channels.lastIndex
                            onChannelIndexChanged(next)
                            channels.getOrNull(next)?.let { viewModel.play(it, selectedGroupId) }
                            onShowOsdChanged(true)
                        }
                        true
                    }
                    e.key == Key.DirectionDown -> {
                        if (showQuickChannels) {
                            if (channelIndex < channels.lastIndex) onChannelIndexChanged(channelIndex + 1)
                        } else if (channels.isNotEmpty()) {
                            val next = if (channelIndex < channels.lastIndex) channelIndex + 1 else 0
                            onChannelIndexChanged(next)
                            channels.getOrNull(next)?.let { viewModel.play(it, selectedGroupId) }
                            onShowOsdChanged(true)
                        }
                        true
                    }
                    e.key == Key.DirectionLeft -> {
                        if (!showQuickChannels) {
                            val playingIdx = channels.indexOfFirst { it.key == playback.channelKey }
                            if (playingIdx >= 0) onChannelIndexChanged(playingIdx)
                            onShowQuickChannelsChanged(true)
                            onShowOsdChanged(false)
                        }
                        true
                    }
                    e.key == Key.DirectionRight -> {
                        if (showQuickChannels) {
                            onShowQuickChannelsChanged(false)
                            val playingIdx = channels.indexOfFirst { it.key == playback.channelKey }
                            if (playingIdx >= 0) onChannelIndexChanged(playingIdx)
                        } else {
                            onShowOsdChanged(!showOsd)
                        }
                        true
                    }
                    isOk -> {
                        if (showQuickChannels) {
                            channels.getOrNull(channelIndex)?.let { ch ->
                                if (ch.key != playback.channelKey) {
                                    viewModel.play(ch, selectedGroupId)
                                }
                            }
                            onShowQuickChannelsChanged(false)
                        } else {
                            onShowOsdChanged(!showOsd)
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        LivePlayerSurface(
            player = viewModel.playback.player,
            modifier = Modifier.fillMaxSize()
        )

        if (playback.isBuffering) {
            CircularProgressIndicator(
                color = NuvioTheme.colors.Primary,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = showOsd,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .padding(horizontal = 48.dp, vertical = 28.dp)
            ) {
                if (playingChannel != null) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LivePosterImage(
                                url = playingChannel.logo,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentDescription = playingChannel.name
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LiveText(
                                        text = "${if (playingChannel.number > 0) "${playingChannel.number} · " else ""}${playingChannel.name}",
                                        size = 18.sp,
                                        weight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (focusedProgram != null && focusedProgram.isLive(now)) {
                                        LiveTag(stringResource(R.string.livetv_live_badge))
                                    }
                                    if (playingChannel.catchup != null) {
                                        LiveTag(
                                            text = stringResource(R.string.livetv_catchup_badge),
                                            background = NuvioTheme.colors.Primary
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                LiveText(
                                    text = focusedProgram?.title ?: stringResource(R.string.livetv_default_program_title),
                                    size = 20.sp,
                                    weight = FontWeight.SemiBold,
                                    color = NuvioTheme.colors.Primary
                                )
                            }
                        }

                        if (focusedProgram != null) {
                            Spacer(Modifier.height(10.dp))
                            val prog = focusedProgram.progress(now)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                LiveText(
                                    text = formatTimeRange(focusedProgram.startMs, focusedProgram.stopMs),
                                    size = 13.sp,
                                    color = Color.LightGray
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.DarkGray)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(prog)
                                            .height(4.dp)
                                            .background(NuvioTheme.colors.Primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showQuickChannels,
            enter = fadeIn() + slideInHorizontally { -it },
            exit = fadeOut() + slideOutHorizontally { -it },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .background(Color(0xF0101014))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LiveText(
                        text = stringResource(R.string.livetv_quick_channels_title),
                        size = 14.sp,
                        weight = FontWeight.Bold,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyColumn(
                        state = quickChannelListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(channels) { idx, ch ->
                            val isCurrent = idx == channelIndex
                            val isPlaying = ch.key == playback.channelKey
                            val curProg = programs[ch.key]?.firstOrNull { it.isLive(now) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isCurrent -> NuvioTheme.colors.FocusBackground
                                            isPlaying -> Color(0xFF262630)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        width = if (isCurrent) 1.dp else 0.dp,
                                        color = if (isCurrent) NuvioTheme.colors.Primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .focusProperties { canFocus = false }
                                    .clickable {
                                        onChannelIndexChanged(idx)
                                        viewModel.play(ch, selectedGroupId)
                                        onShowQuickChannelsChanged(false)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LivePosterImage(
                                    url = ch.logo,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentDescription = ch.name
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    LiveText(
                                        text = ch.name,
                                        size = 14.sp,
                                        weight = if (isCurrent || isPlaying) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) Color.White else Color.LightGray,
                                        maxLines = 1
                                    )
                                    if (curProg != null) {
                                        LiveText(
                                            text = curProg.title,
                                            size = 11.sp,
                                            color = Color.Gray,
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (isPlaying) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = LIVE_RED,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
