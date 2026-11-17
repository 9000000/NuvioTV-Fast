package com.nuvio.tv.features.livetv

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import com.nuvio.tv.R
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import com.nuvio.tv.ui.leanback.core.PlayStoreGlowView
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PanelBgColor = Color(0xFF14141A)
private val GridBgColor = Color(0xFF101014)
private val CardBgColor = Color(0xFF1A1A22)
private val DividerColor = Color(0x22FFFFFF)
private val DEFAULT_TILE_GLOW_COLOR: Int = AndroidColor.parseColor("#F59E0B")
internal val LIVE_TV_GUIDE_GRID_SPACING = 14.dp

internal fun formatTimeRange(startMs: Long, stopMs: Long): String {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return "${fmt.format(Date(startMs))} - ${fmt.format(Date(stopMs))}"
}

/**
 * Fixed left column (Yellow zone): Channel List picker at the very top,
 * followed by the pinned Categories list filling the column height.
 */
@Composable
internal fun LiveTvLeftPanel(
    playlistOptions: List<LiveTvPlaylistOption>,
    selectedPlaylistId: String?,
    isPlaylistExpanded: Boolean,
    playlistPickerFocusIndex: Int,
    playlistPickerListState: LazyListState,
    groups: List<ChannelGroup>,
    selectedGroupId: String,
    drawerIndex: Int,
    isCategoryFocused: Boolean,
    drawerListState: LazyListState,
    onTogglePlaylistPicker: () -> Unit,
    onSelectPlaylistOption: (LiveTvPlaylistOption) -> Unit,
    onSelectGroup: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedPlaylist = remember(playlistOptions, selectedPlaylistId) {
        playlistOptions.firstOrNull { it.id == selectedPlaylistId } ?: playlistOptions.firstOrNull()
    }

    Column(
        modifier = modifier
            .width(176.dp)
            .fillMaxHeight()
            .background(PanelBgColor)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        // Topmost item in the category column: Channel List selector with inline dropdown.
        val isPlaylistRowFocused = isCategoryFocused && drawerIndex == 0
        ChannelListFilterButton(
            label = stringResource(R.string.livetv_playlist_filter_label),
            selectedName = selectedPlaylist?.name ?: stringResource(R.string.livetv_playlist_all),
            count = selectedPlaylist?.channelCount ?: 0,
            isFocused = isPlaylistRowFocused,
            isExpanded = isPlaylistExpanded,
            onClick = onTogglePlaylistPicker
        )

        AnimatedVisibility(
            visible = isPlaylistExpanded,
            enter = expandVertically(tween(150)) + fadeIn(tween(120)),
            exit = shrinkVertically(tween(130)) + fadeOut(tween(100))
        ) {
            PlaylistDropdownMenu(
                options = playlistOptions,
                selectedPlaylistId = selectedPlaylistId,
                focusedIndex = playlistPickerFocusIndex,
                listState = playlistPickerListState,
                onSelect = onSelectPlaylistOption,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        LiveText(
            text = stringResource(R.string.livetv_categories_title),
            size = 11.sp,
            weight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )

        LazyColumn(
            state = drawerListState,
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(groups, key = { _, grp -> grp.id }) { idx, grp ->
                val isFocused = isCategoryFocused && drawerIndex == (idx + 1)
                val isSelected = grp.id == selectedGroupId
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isFocused -> NuvioTheme.colors.Primary
                                isSelected -> Color.White.copy(alpha = 0.14f)
                                else -> Color.Transparent
                            }
                        )
                        .border(
                            width = if (isSelected && !isFocused) 1.dp else 0.dp,
                            color = if (isSelected && !isFocused) NuvioTheme.colors.Primary.copy(alpha = 0.65f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .focusProperties { canFocus = false }
                        .clickable { onSelectGroup(idx) }
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiveText(
                            text = grp.name,
                            size = 12.sp,
                            weight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isFocused || isSelected) Color.White else Color.LightGray,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        LiveText(
                            text = "${grp.channelCount}",
                            size = 11.sp,
                            color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelListFilterButton(
    label: String,
    selectedName: String,
    count: Int,
    isFocused: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isFocused) NuvioTheme.colors.Primary else Color(0xFF1F1F29)
            )
            .border(
                width = 1.dp,
                color = if (isFocused || isExpanded) Color.White.copy(alpha = 0.8f) else Color(0x30FFFFFF),
                shape = RoundedCornerShape(10.dp)
            )
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = label,
            tint = if (isFocused) Color.White else NuvioTheme.colors.Primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            LiveText(
                text = label.uppercase(),
                size = 10.sp,
                weight = FontWeight.Bold,
                color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Gray
            )
            LiveText(
                text = "$selectedName ($count)",
                size = 12.sp,
                weight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(18.dp)
                .rotate(if (isExpanded) 180f else 0f)
        )
    }
}

@Composable
private fun PlaylistDropdownMenu(
    options: List<LiveTvPlaylistOption>,
    selectedPlaylistId: String?,
    focusedIndex: Int,
    listState: LazyListState,
    onSelect: (LiveTvPlaylistOption) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF1B1B26))
            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(10.dp))
            .padding(6.dp)
    ) {
        itemsIndexed(options) { idx, option ->
            val isFocused = idx == focusedIndex
            val isSelected = option.id == selectedPlaylistId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isFocused -> NuvioTheme.colors.Primary
                            isSelected -> Color.White.copy(alpha = 0.14f)
                            else -> Color.Transparent
                        }
                    )
                    .border(
                        width = if (isFocused) 1.dp else 0.dp,
                        color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .focusProperties { canFocus = false }
                    .clickable { onSelect(option) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveText(
                    text = option.name,
                    size = 12.sp,
                    weight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                LiveText(
                    text = "${option.channelCount}",
                    size = 11.sp,
                    color = if (isFocused) Color.White.copy(alpha = 0.85f) else Color.Gray
                )
                if (isSelected) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Full-height middle column (Blue zone with Pink channel tiles): displays a 3-column grid of
 * channels matching the active playlist filter and category.
 */
@Composable
internal fun LiveTvChannelGridPanel(
    channels: List<LiveTvChannel>,
    groupTitle: String,
    playlistLabel: String?,
    selectedGroupId: String,
    favoriteIds: Set<String>,
    playingChannelKey: String?,
    focusedChannelIndex: Int,
    isGridFocused: Boolean,
    isRapidNavigation: Boolean,
    showChannelNames: Boolean,
    twoLineChannelNames: Boolean,
    showChannelNumbers: Boolean,
    gridState: LazyGridState,
    onChannelClick: (Int, LiveTvChannel) -> Unit,
    onChannelLongClick: (Int, LiveTvChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(GridBgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(PanelBgColor)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val headerPrefix = buildString {
                if (!playlistLabel.isNullOrBlank()) {
                    append(playlistLabel)
                    append(" · ")
                }
                append(groupTitle)
                append(" (")
                append(channels.size)
                append(")")
            }
            LiveText(
                text = headerPrefix.uppercase(),
                size = 12.sp,
                weight = FontWeight.Bold,
                color = NuvioTheme.colors.Primary,
                maxLines = 1
            )
        }

        if (channels.isEmpty()) {
            ChannelGridEmptyPlaceholder(
                selectedGroupId = selectedGroupId,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(LIVE_TV_GUIDE_GRID_COLUMNS),
                state = gridState,
                // Padding wide enough to contain Home's 18dp PlayStoreGlowView bloom on edge tiles.
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(LIVE_TV_GUIDE_GRID_SPACING),
                verticalArrangement = Arrangement.spacedBy(LIVE_TV_GUIDE_GRID_SPACING),
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            ) {
                itemsIndexed(channels, key = { _, ch -> ch.key }) { idx, channel ->
                    GuideGridChannelTile(
                        channel = channel,
                        index = idx,
                        isFocused = isGridFocused && idx == focusedChannelIndex,
                        deferGlow = isRapidNavigation,
                        isPlaying = channel.key == playingChannelKey,
                        isFavorite = channel.id in favoriteIds,
                        showChannelNames = showChannelNames,
                        twoLineChannelNames = twoLineChannelNames,
                        showChannelNumbers = showChannelNumbers,
                        onClick = { onChannelClick(idx, channel) },
                        onLongClick = { onChannelLongClick(idx, channel) }
                    )
                }
            }
        }
    }
}

private fun resolveChannelGlowColor(dominantColor: Int): Int {
    return if (dominantColor != 0 && dominantColor != AndroidColor.WHITE && dominantColor != AndroidColor.BLACK) {
        dominantColor
    } else {
        DEFAULT_TILE_GLOW_COLOR
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideGridChannelTile(
    channel: LiveTvChannel,
    index: Int,
    isFocused: Boolean,
    deferGlow: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    showChannelNames: Boolean,
    twoLineChannelNames: Boolean,
    showChannelNumbers: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var dominantColorInt by remember(channel.logoUrl) {
        mutableIntStateOf(
            channel.logoUrl?.let { LeanbackColorEngine.getCachedColor(it) }
                ?: DEFAULT_TILE_GLOW_COLOR
        )
    }
    val onDominantExtracted = remember(channel.logoUrl) {
        { extracted: Int -> dominantColorInt = extracted }
    }

    val resolvedGlowInt = resolveChannelGlowColor(dominantColorInt)
    // Focus border follows the channel's dominant logo color (same tint as the glow core).
    val animatedFocusBorderColor by animateColorAsState(
        targetValue = Color(ColorUtils.blendARGB(resolvedGlowInt, AndroidColor.WHITE, 0.15f)),
        animationSpec = tween(durationMillis = 120),
        label = "channelTileFocusBorderColor"
    )

    val borderColor = when {
        isFocused -> animatedFocusBorderColor
        isPlaying -> LIVE_RED
        else -> Color(0x28FFFFFF)
    }
    val borderWidth = when {
        isFocused -> 2.dp
        isPlaying -> 1.5.dp
        else -> 0.5.dp
    }
    val bgColor = when {
        isFocused -> Color(ColorUtils.blendARGB(AndroidColor.rgb(26, 26, 36), resolvedGlowInt, 0.14f))
        isPlaying -> Color(0xFF221C24)
        else -> CardBgColor
    }

    // Mirror Home's PlayStoreGlowView behavior: one persistent glow view per tile toggled by visibility
    // (never swapped with a Compose glow), deferred 55ms only while the D-pad is held for fast scrolling.
    var deferredGlowReady by remember { mutableStateOf(false) }
    LaunchedEffect(isFocused, deferGlow) {
        deferredGlowReady = false
        if (isFocused && deferGlow) {
            delay(55L)
            deferredGlowReady = true
        }
    }
    val showGlow = isFocused && (!deferGlow || deferredGlowReady)

    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 10.dp.toPx() }
    val glowSpreadPx = with(density) { 18.dp.toPx() }
    val glowMarginPx = (glowSpreadPx * 1.35f).toInt()

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .zIndex(if (isFocused) 1f else 0f)
    ) {
        AndroidView(
            modifier = Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val glowW = constraints.maxWidth + glowMarginPx * 2
                    val glowH = constraints.maxHeight + glowMarginPx * 2
                    val placeable = measurable.measure(Constraints.fixed(glowW, glowH))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(-glowMarginPx, -glowMarginPx)
                    }
                },
            factory = { ctx ->
                PlayStoreGlowView(ctx).apply {
                    this.cornerRadiusPx = cornerRadiusPx
                    this.glowSpreadPx = glowSpreadPx
                    this.glowMarginPx = glowMarginPx.toFloat()
                    this.glowColor = resolvedGlowInt
                    visibility = View.GONE
                }
            },
            update = { view ->
                view.cornerRadiusPx = cornerRadiusPx
                view.glowSpreadPx = glowSpreadPx
                view.glowMarginPx = glowMarginPx.toFloat()
                view.glowColor = resolvedGlowInt
                view.visibility = if (showGlow) View.VISIBLE else View.GONE
            }
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
                .focusProperties { canFocus = false }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            LivePosterImage(
                url = channel.logoUrl,
                contentDescription = channel.name,
                forceFit = true,
                onDominantColorExtracted = onDominantExtracted,
                modifier = Modifier.fillMaxSize(),
                fallback = {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = Color(0xFF565666),
                        modifier = Modifier.size(28.dp)
                    )
                }
            )

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(LIVE_RED)
                )
            }

            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            if (showChannelNames) {
                val numberPrefix = if (showChannelNumbers) {
                    val num = if (channel.number > 0) channel.number else (index + 1)
                    "$num. "
                } else ""
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.34f to Color.Black.copy(alpha = 0.50f),
                                0.65f to Color.Black.copy(alpha = 0.76f),
                                1.0f to Color.Black.copy(alpha = 0.90f)
                            )
                        )
                        .padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LiveText(
                        text = "$numberPrefix${channel.name}",
                        size = 11.sp,
                        weight = if (isFocused || isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = if (twoLineChannelNames) 2 else 1,
                        align = TextAlign.Center,
                        shadow = true,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelGridEmptyPlaceholder(
    selectedGroupId: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(28.dp)
        ) {
            Icon(
                imageVector = when (selectedGroupId) {
                    ChannelGroup.FAVORITES -> Icons.Default.Star
                    ChannelGroup.RECENT -> Icons.Default.History
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = null,
                tint = if (selectedGroupId == ChannelGroup.FAVORITES) Color(0xFFFFD700) else Color.Gray,
                modifier = Modifier.size(44.dp)
            )
            LiveText(
                text = when (selectedGroupId) {
                    ChannelGroup.FAVORITES -> stringResource(R.string.livetv_empty_favorites_title)
                    ChannelGroup.RECENT -> stringResource(R.string.livetv_empty_recent_title)
                    else -> stringResource(R.string.livetv_empty_group_title)
                },
                size = 16.sp,
                weight = FontWeight.Bold,
                color = Color.White,
                align = TextAlign.Center,
                maxLines = 2
            )
            if (selectedGroupId == ChannelGroup.FAVORITES || selectedGroupId == ChannelGroup.RECENT) {
                LiveText(
                    text = when (selectedGroupId) {
                        ChannelGroup.FAVORITES -> stringResource(R.string.livetv_empty_favorites_desc)
                        else -> stringResource(R.string.livetv_empty_recent_desc)
                    },
                    size = 12.sp,
                    color = Color.Gray,
                    align = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * Right sidebar column: 16:9 Live Preview Player on top (Purple zone) and single-channel
 * vertical EPG schedule below (Orange zone).
 */
@Composable
internal fun LiveTvRightPanel(
    showPreview: Boolean,
    playback: LivePlaybackState,
    player: Player?,
    isPreviewFocused: Boolean,
    activeChannel: LiveTvChannel?,
    channelPrograms: List<EpgProgram>,
    now: Long,
    epgIndex: Int,
    isVerticalEpgFocused: Boolean,
    epgListState: LazyListState,
    onPreviewClick: () -> Unit,
    onProgramClick: (Int, EpgProgram) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(326.dp)
            .fillMaxHeight()
            .background(PanelBgColor)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        if (showPreview) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black)
                    .border(
                        width = if (isPreviewFocused) 2.dp else 1.dp,
                        color = if (isPreviewFocused) Color.White else Color(0x28FFFFFF),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .focusProperties { canFocus = false }
                    .clickable(onClick = onPreviewClick)
            ) {
                LivePlayerSurface(
                    player = player,
                    modifier = Modifier.fillMaxSize()
                )
                if (playback.channelKey == null) {
                    LiveText(
                        text = stringResource(R.string.livetv_preview_press_ok),
                        size = 12.sp,
                        color = Color.Gray,
                        align = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 12.dp)
                    )
                } else {
                    if (playback.isBuffering) {
                        CircularProgressIndicator(
                            color = NuvioTheme.colors.Primary,
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.Center)
                        )
                    }
                    if (isPreviewFocused) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.78f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            LiveText(
                                text = stringResource(R.string.livetv_preview_press_ok_fullscreen),
                                size = 11.sp,
                                weight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1D1D26))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                LiveText(
                    text = stringResource(R.string.livetv_preview_disabled_hint),
                    size = 11.sp,
                    color = Color.Gray,
                    maxLines = 2
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = DividerColor, thickness = 1.dp)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LiveText(
                text = stringResource(R.string.livetv_guide_schedule_title),
                size = 11.sp,
                weight = FontWeight.Bold,
                color = Color.Gray
            )
            if (activeChannel != null) {
                LiveText(
                    text = activeChannel.name,
                    size = 12.sp,
                    weight = FontWeight.SemiBold,
                    color = NuvioTheme.colors.Primary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        if (channelPrograms.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LiveText(
                    text = stringResource(R.string.livetv_no_epg_for_channel),
                    size = 12.sp,
                    color = Color.Gray,
                    align = TextAlign.Center,
                    maxLines = 3
                )
            }
        } else {
            LazyColumn(
                state = epgListState,
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                itemsIndexed(
                    items = channelPrograms,
                    key = { _, prog -> "${prog.startMs}_${prog.stopMs}_${prog.title}" }
                ) { idx, prog ->
                    val isLive = prog.isLive(now)
                    val isPast = prog.stopMs <= now
                    val canCatchup = isPast &&
                        activeChannel?.catchup != null &&
                        CatchupUrlBuilder.isAvailable(activeChannel.catchup, prog.startMs, now)
                    val isFocused = isVerticalEpgFocused && idx == epgIndex

                    VerticalEpgProgramRow(
                        program = prog,
                        now = now,
                        isLive = isLive,
                        isPast = isPast,
                        canCatchup = canCatchup,
                        isFocused = isFocused,
                        onClick = { onProgramClick(idx, prog) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VerticalEpgProgramRow(
    program: EpgProgram,
    now: Long,
    isLive: Boolean,
    isPast: Boolean,
    canCatchup: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val containerBg = when {
        isFocused -> Color(0xFF282836)
        isLive -> Color(0xFF231B20)
        else -> Color(0xFF1A1A23)
    }
    val edgeColor = when {
        isFocused -> Color.White
        isLive -> LIVE_RED.copy(alpha = 0.75f)
        else -> Color(0x1EFFFFFF)
    }
    val rowAlpha = if (isPast && !canCatchup && !isFocused) 0.58f else 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha)
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(if (isFocused) 1.5.dp else 1.dp, edgeColor, RoundedCornerShape(8.dp))
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LiveText(
                text = formatTimeRange(program.startMs, program.stopMs),
                size = 11.sp,
                weight = if (isLive || isFocused) FontWeight.Bold else FontWeight.Medium,
                color = if (isLive) Color.White else Color(0xFFA0A0B0)
            )
            when {
                isLive -> LiveTag(stringResource(R.string.livetv_live_badge))
                canCatchup -> LiveTag(
                    text = stringResource(R.string.livetv_catchup_badge),
                    background = NuvioTheme.colors.Primary.copy(alpha = 0.85f)
                )
                isPast -> LiveText(
                    text = stringResource(R.string.livetv_past_badge),
                    size = 10.sp,
                    color = Color.Gray
                )
                else -> LiveText(
                    text = stringResource(R.string.livetv_upcoming_badge),
                    size = 10.sp,
                    color = Color(0xFF8E90A0)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        LiveText(
            text = program.title,
            size = 13.sp,
            weight = if (isLive || isFocused) FontWeight.SemiBold else FontWeight.Normal,
            color = Color.White,
            maxLines = 2,
            lineHeight = 16.sp
        )

        if (isLive) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color(0xFF33262B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(program.progress(now))
                        .height(3.dp)
                        .background(LIVE_RED)
                )
            }
        }
    }
}
