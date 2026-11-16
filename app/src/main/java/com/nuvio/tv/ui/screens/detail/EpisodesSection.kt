package com.nuvio.tv.ui.screens.detail

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.EpisodeOptionsOverlayStyle
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import java.util.Locale
import com.nuvio.tv.ui.util.localizeEpisodeTitle
import com.nuvio.tv.ui.util.rememberLongPressKeyTracker
import com.nuvio.tv.ui.leanback.LeanbackNativeRow
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem

private const val EPISODE_CARD_CONTENT_TYPE = "episode_card"
private const val EPISODE_SCROLL_REPEAT_THROTTLE_MS = 80L
private const val EPISODE_RESTORE_FALLBACK_MS = 250L
private const val EPISODE_RESTORE_FOCUS_ATTEMPTS = 24
private const val EPISODE_OVERLAY_PREFETCH_DELAY_MS = 120L
private const val SEASON_EDGE_PIN_COUNT = 4

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun SeasonTabs(
    seasons: List<Int>,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
    onSeasonLongPress: (Int) -> Unit = {},
    seasonFocusRequesters: MutableMap<Int, FocusRequester> = remember { mutableMapOf() },
    selectedTabFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    onNavigateDown: (() -> Boolean)? = null,
    isFocusEnabled: Boolean = true
) {
    // Move season 0 (specials) to the end
    val sortedSeasons = remember(seasons) {
        val regularSeasons = seasons.filter { it > 0 }.sorted()
        val specials = seasons.filter { it == 0 }
        regularSeasons + specials
    }

    val tabShape = remember { RoundedCornerShape(20.dp) }
    val tabBorder = CardDefaults.border(
        focusedBorder = Border(
            border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
            shape = RoundedCornerShape(20.dp)
        )
    )
    val tabScale = CardDefaults.scale(focusedScale = 1.0f)
    val typography = MaterialTheme.typography
    val tabTextStyle = remember(typography) { typography.titleMedium }
    val textSecondary = NuvioTheme.extendedColors.textSecondary
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val initialSeasonIndex = remember(sortedSeasons, selectedSeason) {
        (sortedSeasons.indexOf(selectedSeason) - (SEASON_EDGE_PIN_COUNT - 1)).coerceAtLeast(0)
    }
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialSeasonIndex)

    var suppressFocusSwitch by remember { mutableStateOf(false) }
    var lastAppliedSeason by remember { mutableStateOf(selectedSeason) }
    var lastFocusedSeason by remember { mutableStateOf(selectedSeason) }
    // Clear suppress whenever selectedSeason actually settles (composition runs
    // with the new value). This guarantees reset even if the scroll coroutine is cancelled.
    if (lastAppliedSeason != selectedSeason) {
        lastAppliedSeason = selectedSeason
        suppressFocusSwitch = false
        lastFocusedSeason = selectedSeason
    }

    var pendingSeason by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingSeason) {
        val target = pendingSeason ?: return@LaunchedEffect
        delay(150)
        if (pendingSeason != target) return@LaunchedEffect
        onSeasonSelected(target)
        if (pendingSeason == target) pendingSeason = null
    }

    LaunchedEffect(sortedSeasons, selectedSeason) {
        val selectedIndex = sortedSeasons.indexOf(selectedSeason)
        if (selectedIndex < 0) return@LaunchedEffect
        val lastIndex = sortedSeasons.lastIndex
        val pinToStart = selectedIndex < SEASON_EDGE_PIN_COUNT
        val pinToEnd = selectedIndex > lastIndex - SEASON_EDGE_PIN_COUNT
        var attempts = 0
        while (attempts < 2) {
            attempts++
            val info = snapshotFlow { lazyListState.layoutInfo }
                .first { it.visibleItemsInfo.isNotEmpty() }
            val visible = info.visibleItemsInfo
            val contentEnd = info.viewportEndOffset - info.afterContentPadding
            if (pinToStart) {
                if (lazyListState.firstVisibleItemIndex != 0 || lazyListState.firstVisibleItemScrollOffset != 0) {
                    suppressFocusSwitch = true
                    lazyListState.scrollToItem(0)
                    suppressFocusSwitch = false
                }
                return@LaunchedEffect
            }
            if (pinToEnd) {
                val lastItem = visible.firstOrNull { it.index == lastIndex }
                if (lastItem == null) {
                    suppressFocusSwitch = true
                    lazyListState.scrollToItem(
                        lastIndex,
                        scrollOffset = -(contentEnd - visible.last().size).coerceAtLeast(0)
                    )
                    suppressFocusSwitch = false
                    snapshotFlow { lazyListState.layoutInfo }
                        .first { it.visibleItemsInfo.any { it.index == lastIndex } }
                    continue
                }
                if (lastItem.offset + lastItem.size > contentEnd) {
                    suppressFocusSwitch = true
                    lazyListState.scrollToItem(
                        lastIndex,
                        scrollOffset = -(contentEnd - lastItem.size)
                    )
                    suppressFocusSwitch = false
                }
                return@LaunchedEffect
            }
            val item = visible.firstOrNull { it.index == selectedIndex }
            if (item == null) {
                suppressFocusSwitch = true
                if (selectedIndex > visible.last().index) {
                    lazyListState.scrollToItem(
                        selectedIndex,
                        scrollOffset = -(contentEnd - visible.last().size).coerceAtLeast(0)
                    )
                } else {
                    lazyListState.scrollToItem(selectedIndex)
                }
                suppressFocusSwitch = false
                snapshotFlow { lazyListState.layoutInfo }
                    .first { it.visibleItemsInfo.any { it.index == selectedIndex } }
                continue
            }
            when {
                item.offset < 0 -> {
                    suppressFocusSwitch = true
                    lazyListState.scrollToItem(selectedIndex)
                    suppressFocusSwitch = false
                }
                item.offset + item.size > contentEnd -> {
                    suppressFocusSwitch = true
                    lazyListState.scrollToItem(
                        selectedIndex,
                        scrollOffset = -(contentEnd - item.size)
                    )
                    suppressFocusSwitch = false
                }
            }
            break
        }
    }

    val restorerRequester = remember(lastFocusedSeason, selectedSeason) {
        val target = if (sortedSeasons.contains(lastFocusedSeason)) lastFocusedSeason else selectedSeason
        seasonFocusRequesters.getOrPut(target) { FocusRequester() }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selectedTabFocusRequester != null) Modifier.focusRequester(selectedTabFocusRequester) else Modifier)
            .focusRestorer {
                restorerRequester
            }
            .focusGroup(),
        state = lazyListState,
        contentPadding = PaddingValues(horizontal = NuvioTheme.spacing.xxxl, vertical = NuvioTheme.spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        items(sortedSeasons, key = { it }) { season ->
            val isSelected = season == selectedSeason
            val isRightEdge = if (isRtl) season == sortedSeasons.first() else season == sortedSeasons.last()
            val isLeftEdge = if (isRtl) season == sortedSeasons.last() else season == sortedSeasons.first()
            var isFocused by remember { mutableStateOf(false) }
            var longPressTriggered by remember { mutableStateOf(false) }
            val longPressKeyTracker = rememberLongPressKeyTracker()
            val seasonFocusRequester = remember(season) {
                seasonFocusRequesters.getOrPut(season) { FocusRequester() }
            }

            Card(
                onClick = {
                    if (longPressTriggered) {
                        longPressTriggered = false
                    } else {
                        onSeasonSelected(season)
                    }
                },
                modifier = Modifier
                    .focusRequester(seasonFocusRequester)
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        if (native.action == AndroidKeyEvent.ACTION_DOWN && native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN) {
                            if (pendingSeason != null) {
                                val s = pendingSeason!!
                                pendingSeason = null
                                onSeasonSelected(s)
                            }
                            if (onNavigateDown != null && onNavigateDown()) {
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }
                    .focusProperties {
                        canFocus = isFocusEnabled
                        if (isRightEdge) right = FocusRequester.Cancel
                        if (isLeftEdge) left = FocusRequester.Cancel
                        if (onNavigateDown == null && isSelected && downFocusRequester != null) {
                            down = downFocusRequester
                        }
                        if (upFocusRequester != null) {
                            up = upFocusRequester
                        }
                    }
                    .onFocusChanged {
                    val nowFocused = it.isFocused
                    isFocused = nowFocused
                    if (nowFocused) {
                        lastFocusedSeason = season
                        if (!isSelected && !suppressFocusSwitch) {
                            pendingSeason = season
                        }
                    }
                }
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        if (native.action == AndroidKeyEvent.ACTION_DOWN) {
                            if (native.keyCode == AndroidKeyEvent.KEYCODE_MENU) {
                                longPressTriggered = true
                                onSeasonLongPress(season)
                                return@onPreviewKeyEvent true
                            }
                        }
                        if (longPressKeyTracker.handle(native, ::isSelectKey) {
                                longPressTriggered = true
                                onSeasonLongPress(season)
                            }
                        ) {
                            if (native.action == AndroidKeyEvent.ACTION_UP) {
                                longPressTriggered = false
                            }
                            return@onPreviewKeyEvent true
                        }
                        if (native.action == AndroidKeyEvent.ACTION_UP &&
                            longPressTriggered &&
                            (isSelectKey(native.keyCode) || native.keyCode == AndroidKeyEvent.KEYCODE_MENU)
                        ) {
                            longPressTriggered = false
                            return@onPreviewKeyEvent true
                        }
                        false
                    }
                    .semantics {
                        role = Role.Tab
                        selected = isSelected
                    },
                shape = CardDefaults.shape(shape = tabShape),
                colors = CardDefaults.colors(
                    containerColor = if (isSelected) NuvioTheme.colors.SurfaceVariant else NuvioTheme.colors.BackgroundCard,
                    focusedContainerColor = NuvioTheme.colors.Secondary
                ),
                border = tabBorder,
                scale = tabScale
            ) {
                Text(
                    text = if (season == 0) stringResource(R.string.episodes_specials) else stringResource(R.string.episodes_season, season),
                    style = tabTextStyle,
                    color = when {
                        isFocused -> NuvioTheme.colors.OnSecondary
                        isSelected -> NuvioTheme.colors.TextPrimary
                        else -> textSecondary
                    },
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun EpisodesRow(
    episodes: List<Video>,
    episodeProgressMap: Map<Pair<Int, Int>, com.nuvio.tv.domain.model.WatchProgress> = emptyMap(),
    episodeRatings: Map<Pair<Int, Int>, Double> = emptyMap(),
    watchedEpisodes: Set<Pair<Int, Int>> = emptySet(),
    episodeWatchedPendingKeys: Set<String> = emptySet(),
    blurUnwatchedEpisodes: Boolean = false,
    episodeOptionsOverlayStyle: EpisodeOptionsOverlayStyle = EpisodeOptionsOverlayStyle.ARTWORK,
    posterCardCornerRadiusDp: Int = 12,
    onEpisodeClick: (Video) -> Unit,
    canPlayEpisode: (Video) -> Boolean = { true },
    onEpisodeManualPlayClick: (Video) -> Unit = onEpisodeClick,
    onEpisodeStartFromBeginningClick: (Video) -> Unit = onEpisodeClick,
    onToggleEpisodeWatched: (Video) -> Unit,
    showManualPlayOption: Boolean = false,
    onMarkSeasonWatched: (Int) -> Unit = {},
    onMarkSeasonUnwatched: (Int) -> Unit = {},
    isSeasonFullyWatched: Boolean = false,
    selectedSeason: Int = 1,
    onOpenEpisodeComments: (Video) -> Unit = {},
    showOpenEpisodeComments: Boolean = false,
    onMarkPreviousEpisodesWatched: (Video) -> Unit = {},
    upFocusRequester: FocusRequester,
    downFocusRequester: FocusRequester? = null,
    entryFocusRequester: FocusRequester? = null,
    entryFocusNonce: Int = 0,
    entryFocusIndex: Int = 0,
    onNavigateDown: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    episodeFocusRequesters: MutableMap<String, FocusRequester> = mutableMapOf(),
    restoreEpisodeId: String? = null,
    restoreFocusToken: Int = 0,
    onRestoreFocusHandled: () -> Unit = {},
    onEpisodeFocused: (episodeId: String) -> Unit = {},
    scrollToEpisodeId: String? = null,
    onScrollToEpisodeHandled: () -> Unit = {},
    anchorEpisodeId: String? = null,
    windowResetKey: String? = null,
    isFocusEnabled: Boolean = true
) {
    val dedupedEpisodes = remember(episodes) { episodes.distinctBy { it.id } }
    var optionsEpisode by remember { mutableStateOf<Video?>(null) }
    val cardMetrics = rememberEpisodeCardMetrics(posterCardCornerRadiusDp)

    LaunchedEffect(restoreFocusToken, restoreEpisodeId, dedupedEpisodes) {
        if (restoreFocusToken <= 0 || restoreEpisodeId.isNullOrBlank()) return@LaunchedEffect
        if (dedupedEpisodes.none { it.id == restoreEpisodeId }) {
            if (dedupedEpisodes.isEmpty()) return@LaunchedEffect
            delay(EPISODE_RESTORE_FALLBACK_MS)
            if (dedupedEpisodes.none { it.id == restoreEpisodeId }) {
                onRestoreFocusHandled()
            }
        }
    }

    val context = LocalContext.current
    val leanbackEpisodes = remember(
        dedupedEpisodes,
        episodeProgressMap,
        episodeRatings,
        watchedEpisodes,
        blurUnwatchedEpisodes,
        context
    ) {
        dedupedEpisodes.map { episode ->
            val seasonEp = episode.season?.let { s -> episode.episode?.let { e -> s to e } }
            val progress = seasonEp?.let { episodeProgressMap[it] }
            val imdbRating = seasonEp?.let { episodeRatings[it] }
            val isMarkedWatched = seasonEp?.let { watchedEpisodes.contains(it) } ?: false
            val progressPercent = progress?.progressPercent?.toInt()
                ?: if (progress != null && progress.duration > 0) ((progress.position.toFloat() / progress.duration) * 100).toInt() else null
            val seasonNumber = episode.season
            val episodeNumber = episode.episode

            LeanbackCardItem.Episode(
                key = episode.id,
                title = episode.title.localizeEpisodeTitle(context),
                imageUrl = episode.thumbnail,
                description = episode.overview?.trim()?.takeIf { it.isNotEmpty() },
                episodeNumber = episodeNumber,
                seasonNumber = seasonNumber,
                episodeLabel = when {
                    seasonNumber != null && episodeNumber != null ->
                        context.getString(R.string.season_episode_format, seasonNumber, episodeNumber)

                    episodeNumber != null ->
                        "${context.getString(R.string.episodes_episode)} $episodeNumber"

                    else -> null
                },
                runtimeMinutes = episode.runtime,
                progressPercent = progressPercent,
                isWatched = isMarkedWatched,
                releaseDate = episode.released?.let(::formatReleaseDate)?.takeIf { it.isNotBlank() },
                imdbRating = imdbRating?.let { String.format(Locale.US, "%.1f", it) },
                isAvailable = episode.available != false,
                blurThumbnail = blurUnwatchedEpisodes && !isMarkedWatched,
                payload = episode
            )
        }
    }

    val targetFocusEpisodeId = restoreEpisodeId ?: scrollToEpisodeId ?: anchorEpisodeId
    val initialIdx = remember(dedupedEpisodes, targetFocusEpisodeId) {
        if (!targetFocusEpisodeId.isNullOrBlank()) {
            dedupedEpisodes.indexOfFirst { it.id == targetFocusEpisodeId }.coerceAtLeast(0)
        } else 0
    }
    val shouldRequestEpisodeFocus = !restoreEpisodeId.isNullOrBlank() && restoreFocusToken > 0

    LaunchedEffect(scrollToEpisodeId) {
        if (!scrollToEpisodeId.isNullOrBlank()) {
            onScrollToEpisodeHandled()
        }
    }

    LeanbackNativeRow(
        rowKey = "episodes_season_${selectedSeason}_${windowResetKey ?: "default"}",
        items = leanbackEpisodes,
        cardWidth = cardMetrics.cardWidth,
        cardHeight = cardMetrics.cardHeight,
        cornerRadius = cardMetrics.cornerRadius,
        showLabels = false,
        animationsEnabled = NuvioTheme.animationsEnabled,
        startPadding = cardMetrics.rowHorizontalPadding,
        spacing = cardMetrics.itemSpacing,
        isFocusEnabled = isFocusEnabled,
        onItemFocused = { _, cardItem ->
            val ep = cardItem.payload as? Video ?: return@LeanbackNativeRow
            onEpisodeFocused(ep.id)
            if (ep.id == restoreEpisodeId && !restoreEpisodeId.isNullOrBlank()) {
                onRestoreFocusHandled()
            }
            if (ep.id == scrollToEpisodeId && !scrollToEpisodeId.isNullOrBlank()) {
                onScrollToEpisodeHandled()
            }
        },
        onItemClick = { cardItem ->
            val ep = cardItem.payload as? Video ?: return@LeanbackNativeRow
            onEpisodeClick(ep)
        },
        onItemLongClick = { cardItem ->
            val ep = cardItem.payload as? Video ?: return@LeanbackNativeRow
            optionsEpisode = ep
        },
        onNavigateUp = {
            if (onNavigateUp != null && onNavigateUp()) {
                true
            } else {
                runCatching { upFocusRequester.requestFocus() }
                true
            }
        },
        onNavigateDown = {
            if (onNavigateDown != null && onNavigateDown()) {
                true
            } else if (downFocusRequester != null) {
                downFocusRequester.requestFocus()
                true
            } else {
                false
            }
        },
        initialFocusPosition = initialIdx,
        isPendingFocus = shouldRequestEpisodeFocus,
        pendingFocusIndex = initialIdx,
        pendingFocusNonce = restoreFocusToken,
        onClearPendingFocus = {
            if (!restoreEpisodeId.isNullOrBlank()) {
                onRestoreFocusHandled()
            }
            if (!scrollToEpisodeId.isNullOrBlank()) {
                onScrollToEpisodeHandled()
            }
        },
        entryFocusRequester = entryFocusRequester,
        entryFocusNonce = entryFocusNonce,
        entryFocusIndex = entryFocusIndex
    )

    optionsEpisode?.let { selectedEpisode ->
        val selectedWatched = selectedEpisode.season?.let { season ->
            selectedEpisode.episode?.let { episode ->
                episodeProgressMap[season to episode]?.isCompleted() == true
                    || watchedEpisodes.contains(season to episode)
            }
        } ?: false
        val isPending = episodeWatchedPendingKeys.contains(episodePendingKey(selectedEpisode))
        val firstEpisodeInSeason = dedupedEpisodes.minByOrNull { it.episode ?: Int.MAX_VALUE }
        val hasPreviousEpisodes = selectedEpisode.episode != null &&
            firstEpisodeInSeason?.episode != null &&
            selectedEpisode.episode > firstEpisodeInSeason.episode

        EpisodeOptionsOverlay(
            episode = selectedEpisode,
            imdbRating = selectedEpisode.season?.let { season ->
                selectedEpisode.episode?.let { episode -> episodeRatings[season to episode] }
            },
            isWatched = selectedWatched,
            blurUnwatchedEpisodes = blurUnwatchedEpisodes,
            style = episodeOptionsOverlayStyle,
            isPending = isPending,
            isSeasonFullyWatched = isSeasonFullyWatched,
            hasPreviousEpisodes = hasPreviousEpisodes,
            hasProgress = selectedEpisode.season?.let { season ->
                selectedEpisode.episode?.let { episode ->
                    episodeProgressMap[season to episode]?.let { progress ->
                        !progress.isCompleted() && progress.position > 0
                    }
                }
            } ?: false,
            onDismiss = { optionsEpisode = null },
            onNavigateEpisode = { step ->
                adjacentEpisode(dedupedEpisodes, selectedEpisode, step)?.let { optionsEpisode = it }
            },
            isPlayEnabled = canPlayEpisode(selectedEpisode),
            onPlay = {
                onEpisodeClick(selectedEpisode)
                optionsEpisode = null
            },
            onStartFromBeginning = {
                onEpisodeStartFromBeginningClick(selectedEpisode)
                optionsEpisode = null
            },
            onOpenEpisodeComments = {
                onOpenEpisodeComments(selectedEpisode)
                optionsEpisode = null
            },
            showOpenEpisodeComments = showOpenEpisodeComments,
            onPlayManually = {
                onEpisodeManualPlayClick(selectedEpisode)
                optionsEpisode = null
            },
            showPlayManually = showManualPlayOption && canPlayEpisode(selectedEpisode),
            onToggleWatched = {
                onToggleEpisodeWatched(selectedEpisode)
                optionsEpisode = null
            },
            onMarkSeasonWatched = {
                onMarkSeasonWatched(selectedSeason)
                optionsEpisode = null
            },
            onMarkSeasonUnwatched = {
                onMarkSeasonUnwatched(selectedSeason)
                optionsEpisode = null
            },
            onMarkPreviousEpisodesWatched = {
                onMarkPreviousEpisodesWatched(selectedEpisode)
                optionsEpisode = null
            }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonOptionsDialog(
    season: Int,
    isFullyWatched: Boolean,
    hasPreviousSeasons: Boolean = false,
    onDismiss: () -> Unit,
    onMarkSeasonWatched: () -> Unit,
    onMarkSeasonUnwatched: () -> Unit,
    onMarkPreviousSeasonsWatched: () -> Unit = {}
) {
    val primaryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        primaryFocusRequester.requestFocus()
    }

    NuvioDialog(
        onDismiss = onDismiss,
        title = if (season == 0) stringResource(R.string.episodes_specials) else stringResource(R.string.episodes_season, season),
        subtitle = stringResource(R.string.episodes_season_actions)
    ) {
        Button(
            onClick = if (isFullyWatched) onMarkSeasonUnwatched else onMarkSeasonWatched,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(primaryFocusRequester),
            colors = ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                contentColor = NuvioTheme.colors.TextPrimary
            )
        ) {
            Text(if (isFullyWatched) stringResource(R.string.episodes_mark_season_unwatched) else stringResource(R.string.episodes_mark_season_watched))
        }

        if (hasPreviousSeasons && season > 0) {
            Button(
                onClick = onMarkPreviousSeasonsWatched,
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.episodes_mark_previous_seasons_watched))
            }
        }
    }
}

private data class EpisodeCardMetrics(
    val rowHorizontalPadding: Dp,
    val rowVerticalPadding: Dp,
    val itemSpacing: Dp,
    val cardWidth: Dp,
    val cardHeight: Dp,
    val cornerRadius: Dp,
    val contentPadding: Dp,
    val contentBottomPadding: Dp,
    val episodeBadgeHorizontalPadding: Dp,
    val episodeBadgeVerticalPadding: Dp,
    val episodeBadgeCornerRadius: Dp,
    val episodeBadgeLetterSpacing: androidx.compose.ui.unit.TextUnit,
    val titleLineHeight: androidx.compose.ui.unit.TextUnit,
    val descriptionLineHeight: androidx.compose.ui.unit.TextUnit,
    val descriptionMaxLines: Int,
    val metadataIconSize: Dp,
    val imdbLogoWidth: Dp,
    val imdbLogoHeight: Dp,
    val progressBarHeight: Dp,
    val statusBadgeSize: Dp,
    val statusIconSize: Dp,
    val statusBadgeInset: Dp
)

@Composable
private fun rememberEpisodeCardMetrics(posterCardCornerRadiusDp: Int = 12): EpisodeCardMetrics {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val userCornerRadius = posterCardCornerRadiusDp.dp
    return remember(screenWidthDp, userCornerRadius) {
        when {
            screenWidthDp >= 1300 -> EpisodeCardMetrics(
                rowHorizontalPadding = NuvioTheme.spacing.huge,
                rowVerticalPadding = 18.dp,
                itemSpacing = 20.dp,
                cardWidth = 400.dp,
                cardHeight = 263.dp,
                cornerRadius = userCornerRadius,
                contentPadding = 20.dp,
                contentBottomPadding = NuvioTheme.spacing.xl,
                episodeBadgeHorizontalPadding = 10.dp,
                episodeBadgeVerticalPadding = 5.dp,
                episodeBadgeCornerRadius = NuvioTheme.spacing.sm,
                episodeBadgeLetterSpacing = 1.0.sp,
                titleLineHeight = 28.sp,
                descriptionLineHeight = 22.sp,
                descriptionMaxLines = 4,
                metadataIconSize = NuvioTheme.spacing.lg,
                imdbLogoWidth = 28.dp,
                imdbLogoHeight = 14.dp,
                progressBarHeight = NuvioTheme.spacing.xs,
                statusBadgeSize = NuvioTheme.spacing.xxl,
                statusIconSize = 20.dp,
                statusBadgeInset = NuvioTheme.spacing.lg
            )

            screenWidthDp >= 1000 -> EpisodeCardMetrics(
                rowHorizontalPadding = 52.dp,
                rowVerticalPadding = 18.dp,
                itemSpacing = 18.dp,
                cardWidth = 360.dp,
                cardHeight = 235.dp,
                cornerRadius = userCornerRadius,
                contentPadding = 18.dp,
                contentBottomPadding = 22.dp,
                episodeBadgeHorizontalPadding = 9.dp,
                episodeBadgeVerticalPadding = NuvioTheme.spacing.xs,
                episodeBadgeCornerRadius = 7.dp,
                episodeBadgeLetterSpacing = 0.9.sp,
                titleLineHeight = 25.sp,
                descriptionLineHeight = 20.sp,
                descriptionMaxLines = 4,
                metadataIconSize = 15.dp,
                imdbLogoWidth = 26.dp,
                imdbLogoHeight = 13.dp,
                progressBarHeight = NuvioTheme.spacing.xs,
                statusBadgeSize = 28.dp,
                statusIconSize = 18.dp,
                statusBadgeInset = 14.dp
            )

            screenWidthDp >= 760 -> EpisodeCardMetrics(
                rowHorizontalPadding = NuvioTheme.spacing.xxxl,
                rowVerticalPadding = NuvioTheme.spacing.lg,
                itemSpacing = NuvioTheme.spacing.lg,
                cardWidth = 320.dp,
                cardHeight = 207.dp,
                cornerRadius = userCornerRadius,
                contentPadding = NuvioTheme.spacing.lg,
                contentBottomPadding = 20.dp,
                episodeBadgeHorizontalPadding = NuvioTheme.spacing.sm,
                episodeBadgeVerticalPadding = NuvioTheme.spacing.xs,
                episodeBadgeCornerRadius = 6.dp,
                episodeBadgeLetterSpacing = 0.9.sp,
                titleLineHeight = 22.sp,
                descriptionLineHeight = 18.sp,
                descriptionMaxLines = 3,
                metadataIconSize = 14.dp,
                imdbLogoWidth = NuvioTheme.spacing.xl,
                imdbLogoHeight = NuvioTheme.spacing.md,
                progressBarHeight = NuvioTheme.spacing.xs,
                statusBadgeSize = NuvioTheme.spacing.xl,
                statusIconSize = NuvioTheme.spacing.lg,
                statusBadgeInset = NuvioTheme.spacing.md
            )

            else -> EpisodeCardMetrics(
                rowHorizontalPadding = NuvioTheme.spacing.xxl,
                rowVerticalPadding = 14.dp,
                itemSpacing = 14.dp,
                cardWidth = 280.dp,
                cardHeight = 179.dp,
                cornerRadius = userCornerRadius,
                contentPadding = 14.dp,
                contentBottomPadding = NuvioTheme.spacing.lg,
                episodeBadgeHorizontalPadding = 7.dp,
                episodeBadgeVerticalPadding = 3.dp,
                episodeBadgeCornerRadius = 5.dp,
                episodeBadgeLetterSpacing = 0.8.sp,
                titleLineHeight = 19.sp,
                descriptionLineHeight = 16.sp,
                descriptionMaxLines = 3,
                metadataIconSize = 13.dp,
                imdbLogoWidth = 22.dp,
                imdbLogoHeight = 11.dp,
                progressBarHeight = NuvioTheme.spacing.xs,
                statusBadgeSize = 22.dp,
                statusIconSize = 14.dp,
                statusBadgeInset = 10.dp
            )
        }
    }
}

private fun isSelectKey(keyCode: Int): Boolean {
    return keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
}

private fun episodePendingKey(video: Video): String {
    return "${video.id}:${video.season ?: -1}:${video.episode ?: -1}"
}
