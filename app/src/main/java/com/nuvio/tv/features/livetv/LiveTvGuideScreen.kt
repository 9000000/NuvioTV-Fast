package com.nuvio.tv.features.livetv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class GuideFocusArea { DRAWER, CHANNELS, TIMELINE }

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

    // Stop playback immediately when leaving screen or app goes to background
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

    when {
        ui.isLoading && ui.channels.isEmpty() -> {
            LiveTvLoadingState()
        }
        !ui.hasPlaylist -> {
            LiveTvEmptyState(onNavigateToSettings)
        }
        else -> {
            LiveTvGuideContent(
                viewModel = viewModel,
                ui = ui,
                settings = settings,
                playback = playback,
                programs = programs,
                user = user,
                now = now,
                onChannelSelected = { channel ->
                    viewModel.stopPlayback()
                    onChannelSelected(channel)
                },
                onOpenSettings = onNavigateToSettings
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveTvGuideContent(
    viewModel: LiveTvViewModel,
    ui: LiveTvUiState,
    settings: LiveTvSettings,
    playback: LivePlaybackState,
    programs: Map<String, List<EpgProgram>>,
    user: LiveUserState,
    now: Long,
    onChannelSelected: (LiveTvChannel) -> Unit,
    onOpenSettings: () -> Unit
) {
    val rawChannels = ui.channels
    val groups = remember(rawChannels, user.favorites, user.recent) {
        val list = mutableListOf<ChannelGroup>()
        
        // 1. Kênh yêu thích
        val favCount = rawChannels.count { it.id in user.favorites }
        list.add(ChannelGroup(ChannelGroup.FAVORITES, "Yêu thích ⭐", channelCount = favCount))

        // 2. Xem gần đây
        val recentCount = rawChannels.count { it.id in user.recent }
        list.add(ChannelGroup(ChannelGroup.RECENT, "Xem gần đây", channelCount = recentCount))

        // 3. Các nhóm danh mục theo đài / thể loại
        val groupCounts = rawChannels.groupBy { it.group?.trim().orEmpty() }
        groupCounts.forEach { (grpName, chList) ->
            if (grpName.isNotBlank()) {
                list.add(ChannelGroup("custom:$grpName", grpName, channelCount = chList.size))
            }
        }

        // 4. Tất cả các kênh (ở cuối cùng)
        list.add(ChannelGroup(ChannelGroup.ALL, "Tất cả các kênh", channelCount = rawChannels.size))

        list
    }

    var selectedGroupId by remember { mutableStateOf(ChannelGroup.FAVORITES) }

    val channels = remember(rawChannels, selectedGroupId, user.favorites, user.recent) {
        when {
            selectedGroupId == ChannelGroup.FAVORITES -> rawChannels.filter { it.id in user.favorites }
            selectedGroupId == ChannelGroup.RECENT -> {
                val recentMap = user.recent.withIndex().associate { it.value to it.index }
                rawChannels.filter { it.id in recentMap.keys }.sortedBy { recentMap[it.id] ?: 999 }
            }
            selectedGroupId.startsWith("custom:") -> {
                val gName = selectedGroupId.removePrefix("custom:")
                rawChannels.filter { it.group?.trim().equals(gName, ignoreCase = true) }
            }
            else -> rawChannels
        }
    }

    var focusArea by remember { mutableStateOf(GuideFocusArea.DRAWER) }
    var channelIndex by remember { mutableIntStateOf(0) }
    var drawerIndex by remember { mutableIntStateOf(0) }
    var timelineProgramIndex by remember { mutableIntStateOf(0) }

    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var showOsd by remember { mutableStateOf(true) }
    var showQuickChannels by remember { mutableStateOf(false) }
    val fullscreenFocusRequester = remember { FocusRequester() }
    val quickChannelListState = rememberLazyListState()

    val channelListState = rememberLazyListState()
    val drawerListState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    val coroutineScope = rememberCoroutineScope()
    var okPressJob by remember { mutableStateOf<Job?>(null) }
    var isLongPressHandled by remember { mutableStateOf(false) }
    var favoriteToastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(favoriteToastMessage) {
        if (favoriteToastMessage != null) {
            delay(2500)
            favoriteToastMessage = null
        }
    }

    LaunchedEffect(isFullscreen) {
        delay(60)
        runCatching {
            if (isFullscreen) {
                fullscreenFocusRequester.requestFocus()
            } else {
                focusRequester.requestFocus()
            }
        }
    }

    // Auto-hide OSD in fullscreen after 5 seconds
    LaunchedEffect(showOsd, channelIndex, isFullscreen) {
        if (isFullscreen && showOsd && !showQuickChannels) {
            delay(5000)
            showOsd = false
        }
    }

    // Scroll quick channels list when opened
    LaunchedEffect(showQuickChannels, channelIndex) {
        if (showQuickChannels && channels.isNotEmpty()) {
            quickChannelListState.scrollToItem((channelIndex - 2).coerceAtLeast(0))
        }
    }

    // Auto-tune first channel into Preview if enabled and nothing is currently tuned (only when not in Drawer)
    LaunchedEffect(channels, settings.showPreview, focusArea) {
        if (settings.showPreview && focusArea != GuideFocusArea.DRAWER && playback.channelKey == null && channels.isNotEmpty()) {
            val lastId = user.lastChannelKey
            val initial = channels.firstOrNull { it.id == lastId } ?: channels.first()
            viewModel.play(initial, selectedGroupId)
        }
    }

    // Auto-tune into Preview when navigating channels with debounce (450ms) to prevent lag (only when not in Drawer)
    LaunchedEffect(channelIndex, selectedGroupId, settings.showPreview, focusArea) {
        if (settings.showPreview && focusArea != GuideFocusArea.DRAWER && channels.isNotEmpty()) {
            delay(450)
            val ch = channels.getOrNull(channelIndex)
            if (ch != null && playback.channelKey != ch.key) {
                viewModel.play(ch, selectedGroupId)
            }
        }
    }

    // Keep channel list scrolled smoothly near active index without cut-offs at top/bottom
    LaunchedEffect(channelIndex) {
        if (channels.isNotEmpty()) {
            if (channelIndex == 0) {
                channelListState.animateScrollToItem(0, scrollOffset = 0)
            } else if (channelIndex == channels.lastIndex) {
                channelListState.animateScrollToItem(channels.lastIndex, scrollOffset = 0)
            } else {
                val layoutInfo = channelListState.layoutInfo
                val visible = layoutInfo.visibleItemsInfo
                if (visible.isNotEmpty()) {
                    val targetVisible = visible.firstOrNull { it.index == channelIndex }
                    if (targetVisible == null) {
                        channelListState.animateScrollToItem((channelIndex - 2).coerceAtLeast(0))
                    } else {
                        val viewportTop = layoutInfo.viewportStartOffset
                        val viewportBottom = layoutInfo.viewportEndOffset
                        val itemTop = targetVisible.offset
                        val itemBottom = itemTop + targetVisible.size

                        if (itemTop < viewportTop + 8) {
                            channelListState.animateScrollToItem((channelIndex - 1).coerceAtLeast(0))
                        } else if (itemBottom > viewportBottom - 48) {
                            channelListState.animateScrollToItem((channelIndex - (visible.size - 2)).coerceAtLeast(0))
                        }
                    }
                }
            }
        }
    }

    // Keep category drawer scrolled near active index when navigating UP/DOWN
    LaunchedEffect(drawerIndex) {
        if (groups.isNotEmpty()) {
            if (drawerIndex == 0) {
                drawerListState.animateScrollToItem(0, scrollOffset = 0)
            } else if (drawerIndex == groups.lastIndex) {
                drawerListState.animateScrollToItem(groups.lastIndex, scrollOffset = 0)
            } else {
                drawerListState.animateScrollToItem((drawerIndex - 2).coerceAtLeast(0))
            }
        }
    }

    // Timeline 2-hour window aligned to half-hour boundary with horizontal scrolling support
    val halfHourMs = 30 * 60 * 1000L
    var windowOffsetMs by remember { mutableLongStateOf(0L) }
    val baseStartWindow = (now / halfHourMs) * halfHourMs - halfHourMs
    val startWindow = baseStartWindow + windowOffsetMs
    val windowDurationMs = 2 * 60 * 60 * 1000L
    val endWindow = startWindow + windowDurationMs

    val focusedChannel = channels.getOrNull(channelIndex)
    val focusedChannelPrograms = focusedChannel?.let { programs[it.key] }.orEmpty()
    val maxFutureMs = now + 24 * 3600 * 1000L
    val focusedVisiblePrograms = remember(focusedChannel?.key, focusedChannelPrograms, startWindow, endWindow, now) {
        val inWindow = focusedChannelPrograms.filter {
            it.stopMs > startWindow && it.startMs < endWindow && it.startMs <= maxFutureMs
        }
        val pastInWindow = inWindow.filter { it.stopMs <= now }
        if (pastInWindow.size > 2) {
            val allowedPast = pastInWindow.takeLast(2).toSet()
            inWindow.filter { it.stopMs > now || it in allowedPast }
        } else {
            inWindow
        }
    }
    val focusedProgram = when (focusArea) {
        GuideFocusArea.TIMELINE -> focusedVisiblePrograms.getOrNull(timelineProgramIndex) ?: focusedVisiblePrograms.firstOrNull { now in it.startMs until it.stopMs }
        else -> focusedChannelPrograms.firstOrNull { now in it.startMs until it.stopMs } ?: focusedChannelPrograms.firstOrNull()
    }

    // Row height from settings (Default STANDARD = 56dp)
    val rowHeightDp = when (settings.effectiveGuideSize) {
        "COMPACT" -> 48.dp
        "LARGE" -> 64.dp
        "XLARGE" -> 74.dp
        else -> 56.dp
    }

    val selectGroupAndFocusChannels: (Int) -> Unit = { targetIndex ->
        val group = groups.getOrNull(targetIndex)
        val targetGroupId = group?.id ?: ChannelGroup.FAVORITES
        drawerIndex = targetIndex
        selectedGroupId = targetGroupId
        focusArea = GuideFocusArea.CHANNELS
        channelIndex = 0

        val targetChannels = when {
            targetGroupId == ChannelGroup.FAVORITES -> rawChannels.filter { it.id in user.favorites }
            targetGroupId == ChannelGroup.RECENT -> {
                val recentMap = user.recent.withIndex().associate { it.value to it.index }
                rawChannels.filter { it.id in recentMap.keys }.sortedBy { recentMap[it.id] ?: 999 }
            }
            targetGroupId.startsWith("custom:") -> {
                val gName = targetGroupId.removePrefix("custom:")
                rawChannels.filter { it.group?.trim().equals(gName, ignoreCase = true) }
            }
            else -> rawChannels
        }
        val firstCh = targetChannels.firstOrNull()
        if (firstCh != null && settings.showPreview && playback.channelKey != firstCh.key) {
            viewModel.play(firstCh, targetGroupId)
        }
    }

    fun onOkPressed() {
        when (focusArea) {
            GuideFocusArea.DRAWER -> {
                selectGroupAndFocusChannels(drawerIndex)
            }
            GuideFocusArea.CHANNELS -> {
                val ch = focusedChannel ?: return
                if (playback.channelKey == ch.key) {
                    // Already playing in preview -> Seamlessly expand to Fullscreen!
                    isFullscreen = true
                    showOsd = true
                } else {
                    // Tune into Preview
                    viewModel.play(ch, selectedGroupId)
                }
            }
            GuideFocusArea.TIMELINE -> {
                val ch = focusedChannel ?: return
                val p = focusedProgram ?: return
                when {
                    now in p.startMs until p.stopMs -> {
                        if (playback.channelKey != ch.key) {
                            viewModel.play(ch, selectedGroupId)
                        }
                        isFullscreen = true
                        showOsd = true
                    }
                    p.stopMs <= now && ch.catchup != null && CatchupUrlBuilder.isAvailable(ch.catchup, p.startMs, now) -> {
                        val catchup = ch.catchup
                        val catchupUrl = CatchupUrlBuilder.build(
                            liveUrl = ch.url,
                            catchup = catchup,
                            startMs = p.startMs,
                            stopMs = p.stopMs,
                            nowMs = now
                        )
                        if (catchupUrl != null) {
                            viewModel.play(ch.copy(streamUrl = catchupUrl), selectedGroupId)
                            isFullscreen = true
                            showOsd = true
                        } else {
                            viewModel.play(ch, selectedGroupId)
                            isFullscreen = true
                            showOsd = true
                        }
                    }
                    else -> {
                        if (playback.channelKey != ch.key) {
                            viewModel.play(ch, selectedGroupId)
                        }
                        isFullscreen = true
                        showOsd = true
                    }
                }
            }
        }
    }

    if (isFullscreen) {
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
                                showQuickChannels = false
                                true
                            } else {
                                isFullscreen = false
                                showOsd = false
                                true
                            }
                        }
                        e.key == Key.DirectionUp -> {
                            if (showQuickChannels) {
                                if (channelIndex > 0) {
                                    channelIndex--
                                    channels.getOrNull(channelIndex)?.let { viewModel.play(it, selectedGroupId) }
                                }
                            } else {
                                if (channels.isNotEmpty()) {
                                    channelIndex = if (channelIndex > 0) channelIndex - 1 else channels.lastIndex
                                    channels.getOrNull(channelIndex)?.let { viewModel.play(it, selectedGroupId) }
                                    showOsd = true
                                }
                            }
                            true
                        }
                        e.key == Key.DirectionDown -> {
                            if (showQuickChannels) {
                                if (channelIndex < channels.lastIndex) {
                                    channelIndex++
                                    channels.getOrNull(channelIndex)?.let { viewModel.play(it, selectedGroupId) }
                                }
                            } else {
                                if (channels.isNotEmpty()) {
                                    channelIndex = if (channelIndex < channels.lastIndex) channelIndex + 1 else 0
                                    channels.getOrNull(channelIndex)?.let { viewModel.play(it, selectedGroupId) }
                                    showOsd = true
                                }
                            }
                            true
                        }
                        e.key == Key.DirectionLeft -> {
                            if (!showQuickChannels) {
                                showQuickChannels = true
                                showOsd = false
                            }
                            true
                        }
                        e.key == Key.DirectionRight -> {
                            if (showQuickChannels) {
                                showQuickChannels = false
                            } else {
                                showOsd = !showOsd
                            }
                            true
                        }
                        isOk -> {
                            if (showQuickChannels) {
                                showQuickChannels = false
                            } else {
                                showOsd = !showOsd
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
                    modifier = Modifier.size(48.dp).align(Alignment.Center)
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
                    if (focusedChannel != null) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                LivePosterImage(
                                    url = focusedChannel.logo,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentDescription = focusedChannel.name
                                )
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        LiveText(
                                            text = "${if (focusedChannel.number > 0) "${focusedChannel.number} · " else ""}${focusedChannel.name}",
                                            size = 18.sp,
                                            weight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        if (focusedProgram != null && now in focusedProgram.startMs until focusedProgram.stopMs) {
                                            LiveTag("TRỰC TIẾP")
                                        }
                                        if (focusedChannel.catchup != null) {
                                            LiveTag("CATCH-UP")
                                        }
                                    }

                                    Spacer(Modifier.height(4.dp))

                                    LiveText(
                                        text = focusedProgram?.title ?: "Chương trình trực tiếp",
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
                                        text = formatRange(focusedProgram.startMs, focusedProgram.stopMs),
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
                            text = "DANH SÁCH KÊNH",
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
                                val curProg = programs[ch.key]?.firstOrNull { now in it.startMs until it.stopMs }

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
                                        .clickable {
                                            channelIndex = idx
                                            viewModel.play(ch, selectedGroupId)
                                            showQuickChannels = false
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
                                            Icons.Default.PlayArrow,
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
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101014))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { e ->
                val isOk = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter

                if (isOk) {
                    if (e.type == KeyEventType.KeyDown) {
                        if (e.nativeKeyEvent.repeatCount == 0) {
                            isLongPressHandled = false
                            okPressJob?.cancel()
                            okPressJob = coroutineScope.launch {
                                delay(600)
                                if (focusArea == GuideFocusArea.CHANNELS) {
                                    val ch = focusedChannel
                                    if (ch != null) {
                                        isLongPressHandled = true
                                        val isFav = ch.id in user.favorites
                                        viewModel.toggleFavorite(ch)
                                        favoriteToastMessage = if (!isFav) "Đã thêm ${ch.name} vào Yêu thích ⭐" else "Đã xóa ${ch.name} khỏi Yêu thích"
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
                        onOkPressed()
                        return@onPreviewKeyEvent true
                    }
                }

                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                when (focusArea) {
                    GuideFocusArea.DRAWER -> when {
                        e.key == Key.DirectionUp -> {
                            drawerIndex = (drawerIndex - 1).coerceAtLeast(0)
                            selectedGroupId = groups.getOrNull(drawerIndex)?.id ?: ChannelGroup.FAVORITES
                            channelIndex = 0
                            true
                        }
                        e.key == Key.DirectionDown -> {
                            drawerIndex = (drawerIndex + 1).coerceAtMost(groups.lastIndex)
                            selectedGroupId = groups.getOrNull(drawerIndex)?.id ?: ChannelGroup.FAVORITES
                            channelIndex = 0
                            true
                        }
                        e.key == Key.DirectionRight -> {
                            selectGroupAndFocusChannels(drawerIndex)
                            true
                        }
                        e.key == Key.Back -> {
                            false
                        }
                        else -> false
                    }
                    GuideFocusArea.CHANNELS -> when {
                        e.key == Key.DirectionUp -> {
                            if (channelIndex > 0) {
                                channelIndex--
                            }
                            true
                        }
                        e.key == Key.DirectionDown -> {
                            if (channelIndex < channels.lastIndex) {
                                channelIndex++
                            }
                            true
                        }
                        e.key == Key.DirectionLeft -> {
                            focusArea = GuideFocusArea.DRAWER
                            drawerIndex = groups.indexOfFirst { it.id == selectedGroupId }.coerceAtLeast(0)
                            true
                        }
                        e.key == Key.Back -> {
                            focusArea = GuideFocusArea.DRAWER
                            drawerIndex = groups.indexOfFirst { it.id == selectedGroupId }.coerceAtLeast(0)
                            true
                        }
                        e.key == Key.DirectionRight -> {
                            if (focusedVisiblePrograms.isNotEmpty()) {
                                focusArea = GuideFocusArea.TIMELINE
                                windowOffsetMs = 0L
                                val liveIdx = focusedVisiblePrograms.indexOfFirst { now in it.startMs until it.stopMs }
                                timelineProgramIndex = if (liveIdx >= 0) liveIdx else 0
                            }
                            true
                        }
                        else -> false
                    }
                    GuideFocusArea.TIMELINE -> when {
                        e.key == Key.DirectionLeft -> {
                            if (timelineProgramIndex > 0) {
                                timelineProgramIndex--
                            } else {
                                if (windowOffsetMs > 0L) {
                                    windowOffsetMs = (windowOffsetMs - halfHourMs).coerceAtLeast(0L)
                                    timelineProgramIndex = 0
                                } else {
                                    // Đã ở đầu dải (tối đa 2 chương trình đã phát) -> Chuyển ngay focus về cột kênh!
                                    focusArea = GuideFocusArea.CHANNELS
                                }
                            }
                            true
                        }
                        e.key == Key.DirectionRight -> {
                            if (timelineProgramIndex < focusedVisiblePrograms.lastIndex) {
                                timelineProgramIndex++
                            } else {
                                val maxOffsetMs = 24 * 3600 * 1000L
                                val canScrollRight = (windowOffsetMs + halfHourMs <= maxOffsetMs) &&
                                        focusedChannelPrograms.any { it.stopMs > endWindow && it.startMs <= maxFutureMs }
                                if (canScrollRight) {
                                    windowOffsetMs += halfHourMs
                                    val newVis = focusedChannelPrograms.filter {
                                        it.stopMs > (startWindow + halfHourMs) && it.startMs < (endWindow + halfHourMs) && it.startMs <= maxFutureMs
                                    }
                                    timelineProgramIndex = (newVis.lastIndex).coerceAtLeast(0)
                                }
                            }
                            true
                        }
                        e.key == Key.DirectionUp -> {
                            if (channelIndex > 0) {
                                val curProg = focusedVisiblePrograms.getOrNull(timelineProgramIndex)
                                val targetTime = curProg?.let { (it.startMs + it.stopMs) / 2 } ?: (startWindow + halfHourMs)
                                channelIndex--
                                val newCh = channels.getOrNull(channelIndex)
                                val newProgs = newCh?.let { programs[it.key] }.orEmpty().filter {
                                    it.stopMs > startWindow && it.startMs < endWindow
                                }
                                val matchIdx = newProgs.indexOfFirst { targetTime in it.startMs until it.stopMs }
                                timelineProgramIndex = if (matchIdx >= 0) matchIdx else 0
                            }
                            true
                        }
                        e.key == Key.DirectionDown -> {
                            if (channelIndex < channels.lastIndex) {
                                val curProg = focusedVisiblePrograms.getOrNull(timelineProgramIndex)
                                val targetTime = curProg?.let { (it.startMs + it.stopMs) / 2 } ?: (startWindow + halfHourMs)
                                channelIndex++
                                val newCh = channels.getOrNull(channelIndex)
                                val newProgs = newCh?.let { programs[it.key] }.orEmpty().filter {
                                    it.stopMs > startWindow && it.startMs < endWindow
                                }
                                val matchIdx = newProgs.indexOfFirst { targetTime in it.startMs until it.stopMs }
                                timelineProgramIndex = if (matchIdx >= 0) matchIdx else 0
                            }
                            true
                        }
                        e.key == Key.Back -> {
                            windowOffsetMs = 0L
                            focusArea = GuideFocusArea.CHANNELS
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Header with Program Details & Live Preview Player
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Left: Channel info, Program Title, Progress & Description
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (focusedChannel != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LivePosterImage(
                                url = focusedChannel.logo,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentDescription = focusedChannel.name
                            )
                            LiveText(
                                text = "${if (focusedChannel.number > 0) "${focusedChannel.number} · " else ""}${focusedChannel.name}",
                                size = 15.sp,
                                weight = FontWeight.SemiBold,
                                color = Color.LightGray
                            )
                            if (focusedProgram != null && now in focusedProgram.startMs until focusedProgram.stopMs) {
                                LiveTag("TRỰC TIẾP")
                            }
                            if (focusedChannel.catchup != null) {
                                LiveTag("CATCH-UP")
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        LiveText(
                            text = focusedProgram?.title ?: "Chương trình truyền hình",
                            size = 24.sp,
                            weight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )

                        Spacer(Modifier.height(4.dp))

                        if (focusedProgram != null) {
                            val timeStr = formatRange(focusedProgram.startMs, focusedProgram.stopMs)
                            LiveText(
                                text = timeStr,
                                size = 13.sp,
                                color = Color.Gray
                            )
                            val prog = focusedProgram.progress(now)
                            if (now in focusedProgram.startMs until focusedProgram.stopMs) {
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .width(260.dp)
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
                            Spacer(Modifier.height(6.dp))
                            ScrollingDescription(
                                text = focusedProgram.description ?: "Không có mô tả chi tiết cho chương trình này.",
                                color = Color.LightGray,
                                size = 13.sp,
                                maxLines = 2
                            )
                        } else {
                            LiveText(
                                text = "Không có thông tin lịch phát sóng EPG cho kênh này.",
                                size = 13.sp,
                                color = Color.Gray
                            )
                        }
                    } else {
                        LiveText(
                            text = "Chọn một kênh để bắt đầu xem",
                            size = 18.sp,
                            color = Color.Gray
                        )
                    }
                }

                // Right: Live Preview Player Window
                if (settings.showPreview) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                isFullscreen = true
                                showOsd = true
                            }
                    ) {
                        LivePlayerSurface(
                            player = viewModel.playback.player,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (playback.channelKey == null) {
                            LiveText(
                                text = "Bấm OK trên kênh để phát",
                                size = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else if (playback.isBuffering) {
                            CircularProgressIndicator(
                                color = NuvioTheme.colors.Primary,
                                modifier = Modifier.size(32.dp).align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            // ── Time Ruler Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(Color(0xFF16161B))
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Group & Channel column header
                Box(modifier = Modifier.width(280.dp)) {
                    val groupTitle = groups.firstOrNull { it.id == selectedGroupId }?.name ?: "Tất cả các kênh"
                    LiveText(
                        text = groupTitle.uppercase(),
                        size = 13.sp,
                        weight = FontWeight.Bold,
                        color = NuvioTheme.colors.Primary
                    )
                }

                // 4 Time intervals (30 minutes each across 2 hours)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val dayFormat = SimpleDateFormat("EEE, dd/MM", Locale.getDefault())
                    val isDifferentDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(startWindow)) !=
                            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date(now))

                    for (i in 0 until 4) {
                        val slotMs = startWindow + i * halfHourMs
                        val text = if (i == 0 && isDifferentDay) {
                            "${dayFormat.format(Date(slotMs))} · ${timeFormat.format(Date(slotMs))}"
                        } else {
                            timeFormat.format(Date(slotMs))
                        }
                        LiveText(
                            text = text,
                            size = 12.sp,
                            weight = FontWeight.SemiBold,
                            color = if (isDifferentDay && i == 0) NuvioTheme.colors.Primary else Color.Gray
                        )
                    }
                }
            }

            // ── Main Content: Left Category Drawer + Channels & EPG Timeline
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Slide-out Categories Drawer
                if (focusArea == GuideFocusArea.DRAWER) {
                    Column(
                        modifier = Modifier
                            .width(240.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF141418))
                            .padding(12.dp)
                    ) {
                        LiveText(
                            text = "DANH MỤC",
                            size = 13.sp,
                            weight = FontWeight.Bold,
                            color = Color.LightGray,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        LazyColumn(
                            state = drawerListState,
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(groups) { idx, grp ->
                                val isFocused = idx == drawerIndex
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isFocused) NuvioTheme.colors.Primary else Color.Transparent)
                                        .clickable {
                                            selectGroupAndFocusChannels(idx)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LiveText(
                                            text = grp.name,
                                            size = 14.sp,
                                            weight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isFocused) NuvioTheme.colors.OnPrimary else Color.White
                                        )
                                        LiveText(
                                            text = "${grp.channelCount}",
                                            size = 12.sp,
                                            color = if (isFocused) NuvioTheme.colors.OnPrimary.copy(alpha = 0.8f) else Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Vertical Channel List + Horizontal EPG Grid
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val totalWidth = maxWidth
                    val channelColWidth = 280.dp
                    val gridWidth = (totalWidth - channelColWidth).coerceAtLeast(100.dp)

                    if (channels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Icon(
                                    when (selectedGroupId) {
                                        ChannelGroup.FAVORITES -> Icons.Default.Star
                                        ChannelGroup.RECENT -> Icons.Default.History
                                        else -> Icons.Default.PlayArrow
                                    },
                                    contentDescription = null,
                                    tint = if (selectedGroupId == ChannelGroup.FAVORITES) Color(0xFFFFD700) else Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                LiveText(
                                    text = when (selectedGroupId) {
                                        ChannelGroup.FAVORITES -> "Chưa có kênh yêu thích nào"
                                        ChannelGroup.RECENT -> "Chưa có kênh xem gần đây"
                                        else -> "Không có kênh nào trong danh mục này"
                                    },
                                    size = 18.sp,
                                    weight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (selectedGroupId == ChannelGroup.FAVORITES) {
                                    LiveText(
                                        text = "Nhấn giữ phím OK trên kênh bất kỳ để thêm vào mục Yêu thích ⭐",
                                        size = 14.sp,
                                        color = Color.Gray
                                    )
                                } else if (selectedGroupId == ChannelGroup.RECENT) {
                                    LiveText(
                                        text = "Các kênh bạn theo dõi sẽ tự động lưu lại ở đây để xem lại nhanh chóng",
                                        size = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = channelListState,
                            contentPadding = PaddingValues(top = 6.dp, bottom = 56.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                        itemsIndexed(channels, key = { _, ch -> ch.key }) { index, ch ->
                            val isChannelFocused = index == channelIndex && focusArea != GuideFocusArea.DRAWER
                            val isPlaying = ch.key == playback.channelKey

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeightDp)
                                    .background(if (index % 2 == 0) Color(0xFF121216) else Color(0xFF15151B)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Channel Item Block
                                Box(
                                    modifier = Modifier
                                        .width(channelColWidth)
                                        .fillMaxHeight()
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isChannelFocused && focusArea == GuideFocusArea.CHANNELS -> NuvioTheme.colors.FocusBackground
                                                isPlaying -> Color(0xFF262630)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(
                                            width = if (isPlaying) 1.5.dp else if (isChannelFocused && focusArea == GuideFocusArea.CHANNELS) 1.dp else 0.dp,
                                            color = if (isPlaying) LIVE_RED else if (isChannelFocused && focusArea == GuideFocusArea.CHANNELS) NuvioTheme.colors.Primary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                channelIndex = index
                                                focusArea = GuideFocusArea.CHANNELS
                                                onOkPressed()
                                            },
                                            onLongClick = {
                                                channelIndex = index
                                                focusArea = GuideFocusArea.CHANNELS
                                                val isFav = ch.id in user.favorites
                                                viewModel.toggleFavorite(ch)
                                                favoriteToastMessage = if (!isFav) "Đã thêm ${ch.name} vào Yêu thích ⭐" else "Đã xóa ${ch.name} khỏi Yêu thích"
                                            }
                                        )
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isPlaying) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = LIVE_RED,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        } else {
                                            LiveText(
                                                text = "${if (ch.number > 0) ch.number else (index + 1)}",
                                                size = 12.sp,
                                                color = Color.Gray,
                                                modifier = Modifier.width(28.dp)
                                            )
                                        }

                                        LivePosterImage(
                                            url = ch.logo,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            contentDescription = ch.name
                                        )
                                        Spacer(Modifier.width(10.dp))

                                        LiveText(
                                            text = ch.name,
                                            size = 14.sp,
                                            weight = FontWeight.SemiBold,
                                            color = if (isChannelFocused) Color.White else Color(0xFFE2E2E2),
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (ch.catchup != null) {
                                            Icon(
                                                Icons.Default.History,
                                                contentDescription = "Catchup",
                                                tint = Color.Gray,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        if (ch.id in user.favorites) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = "Favorite",
                                                tint = Color(0xFFFFD700),
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }

                                // Horizontal Timeline EPG Strip (Canvas drawing 60fps)
                                val chPrograms = remember(ch.key, programs[ch.key], startWindow, endWindow, now) {
                                    val allP = programs[ch.key].orEmpty()
                                    val inWindow = allP.filter { it.stopMs > startWindow && it.startMs < endWindow && it.startMs <= maxFutureMs }
                                    val pastInWindow = inWindow.filter { it.stopMs <= now }
                                    if (pastInWindow.size > 2) {
                                        val allowedPast = pastInWindow.takeLast(2).toSet()
                                        inWindow.filter { it.stopMs > now || it in allowedPast }
                                    } else {
                                        inWindow
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .width(gridWidth)
                                        .fillMaxHeight()
                                        .padding(vertical = 4.dp)
                                ) {
                                    if (chPrograms.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 4.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF1E1E24).copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            LiveText(
                                                text = "Không có thông tin lịch chiếu",
                                                size = 13.sp,
                                                color = Color.DarkGray,
                                                modifier = Modifier.padding(start = 12.dp)
                                            )
                                        }
                                    } else {
                                        // Build strip items for Canvas drawing
                                        val stripItems = chPrograms.mapIndexed { pIdx, p ->
                                            val pStart = p.startMs.coerceAtLeast(startWindow)
                                            val pStop = p.stopMs.coerceAtMost(endWindow)
                                            val fracStart = (pStart - startWindow).toFloat() / windowDurationMs
                                            val fracDuration = (pStop - pStart).toFloat() / windowDurationMs
                                            val itemX = gridWidth * fracStart
                                            val itemWidth = (gridWidth * fracDuration).coerceAtLeast(10.dp)

                                            val isTimelineFocused = isChannelFocused &&
                                                    focusArea == GuideFocusArea.TIMELINE &&
                                                    pIdx == timelineProgramIndex

                                            val isLive = now in p.startMs until p.stopMs

                                            StripItem(
                                                x = itemX,
                                                width = itemWidth,
                                                title = p.title,
                                                subtitle = formatRange(p.startMs, p.stopMs),
                                                fill = when {
                                                    isTimelineFocused -> NuvioTheme.colors.FocusBackground
                                                    isLive -> Color(0xFF2B2B36)
                                                    else -> Color(0xFF1C1C22)
                                                },
                                                edge = when {
                                                    isTimelineFocused -> NuvioTheme.colors.Primary
                                                    isLive -> LIVE_RED
                                                    else -> Color(0xFF2C2C35)
                                                },
                                                text = if (isTimelineFocused || isLive) Color.White else Color.LightGray,
                                                progress = if (isLive) p.progress(now) else null,
                                                liveTag = isLive,
                                                catchup = ch.catchup != null
                                            )
                                        }

                                        GuideRowStrip(
                                            items = stripItems,
                                            titleSize = 13.sp,
                                            smallSize = 11.sp
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

        // Favorite Toast Notification Popup
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
                        Icons.Default.Star,
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
}

private fun formatRange(startMs: Long, stopMs: Long): String {
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return "${timeFmt.format(Date(startMs))} - ${timeFmt.format(Date(stopMs))}"
}
