package com.nuvio.tv.ui.screens.detail

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.nuvio.tv.domain.model.CardDepthSurface
import com.nuvio.tv.ui.components.LocalCardDepthStyle
import com.nuvio.tv.ui.components.nuvioCardDepth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
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
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.request.transformations
import com.nuvio.tv.R
import com.nuvio.tv.core.tracking.TrackingMediaReference
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.EpisodeOptionsOverlayStyle
import com.nuvio.tv.ui.components.FocusMarqueeText
import com.nuvio.tv.ui.components.ImdbRatingSourceLabel
import com.nuvio.tv.ui.components.LightweightCard
import com.nuvio.tv.ui.components.MonochromePosterPlaceholder
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import java.util.Locale
import com.nuvio.tv.ui.util.localizeEpisodeTitle
import com.nuvio.tv.ui.util.SmoothDownscaleTransformation
import com.nuvio.tv.ui.util.rememberLongPressKeyTracker
import com.nuvio.tv.ui.leanback.LeanbackNativeRow
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem

private const val EPISODE_CARD_CONTENT_TYPE = "episode_card"
private const val EPISODE_SCROLL_REPEAT_THROTTLE_MS = 80L
private const val EPISODE_RESTORE_FALLBACK_MS = 250L
private const val EPISODE_RESTORE_FOCUS_ATTEMPTS = 24
private const val EPISODE_OVERLAY_PREFETCH_DELAY_MS = 120L
private const val SEASON_POSTER_ASPECT = 2f / 3f
private val SEASON_TAB_POSTER_HEIGHT = 150.dp
private val SEASON_TAB_POSTER_CORNER_RADIUS = 6.dp

@Immutable
data class SeasonPosterTabsStyle(
    val posters: Map<Int, String?>
)

internal fun resolveSeasonPosters(meta: Meta, seasons: List<Int>): Map<Int, String>? {
    val posters = seasons.mapNotNull { season ->
        val poster = meta.videos.firstNotNullOfOrNull { video ->
            video.seasonPoster?.takeIf { video.season == season && it.isNotBlank() }
        } ?: meta.seasonPosters[season]?.takeIf { it.isNotBlank() }
        poster?.let { season to it }
    }.toMap()
    return posters.takeIf { it.isNotEmpty() }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    isFocusEnabled: Boolean = true,
    posterStyle: SeasonPosterTabsStyle? = null
) {
    // Move season 0 (specials) to the end
    val sortedSeasons = remember(seasons) {
        val regularSeasons = seasons.filter { it > 0 }.sorted()
        val specials = seasons.filter { it == 0 }
        regularSeasons + specials
    }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val initialSeasonIndex = remember(sortedSeasons, selectedSeason) {
        sortedSeasons.indexOf(selectedSeason).coerceAtLeast(0)
    }
    val lazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialSeasonIndex)

    var lastAppliedSeason by remember { mutableStateOf(selectedSeason) }
    var lastFocusedSeason by remember { mutableStateOf(selectedSeason) }
    // Clear suppress whenever selectedSeason actually settles (composition runs
    // with the new value). This guarantees reset even if the scroll coroutine is cancelled.
    if (lastAppliedSeason != selectedSeason) {
        lastAppliedSeason = selectedSeason
        lastFocusedSeason = selectedSeason
    }

    var pendingSeason by remember { mutableStateOf<Int?>(null) }
    var seasonRowHasFocus by remember { mutableStateOf(false) }
    var lastHorizontalKeyRepeatTime by remember { mutableStateOf(0L) }
    val currentOnSeasonSelected by rememberUpdatedState(onSeasonSelected)
    val currentOnSeasonLongPress by rememberUpdatedState(onSeasonLongPress)
    val onTabClick: (Int) -> Unit = remember { { season -> currentOnSeasonSelected(season) } }
    val onTabLongPress: (Int) -> Unit = remember { { season -> currentOnSeasonLongPress(season) } }
    val onTabFocused: (Int, Boolean) -> Unit = remember {
        { season, isSelected ->
            lastFocusedSeason = season
            if (!isSelected) {
                pendingSeason = season
            }
        }
    }
    LaunchedEffect(pendingSeason) {
        val target = pendingSeason ?: return@LaunchedEffect
        delay(150)
        if (pendingSeason != target) return@LaunchedEffect
        onSeasonSelected(target)
        if (pendingSeason == target) pendingSeason = null
    }

    LaunchedEffect(sortedSeasons, selectedSeason) {
        val selectedIndex = sortedSeasons.indexOf(selectedSeason)
        if (seasonRowHasFocus || selectedIndex < 0) return@LaunchedEffect
        lazyListState.requestScrollToItem(selectedIndex)
    }

    val restorerRequester = remember(lastFocusedSeason, selectedSeason) {
        val target = if (sortedSeasons.contains(lastFocusedSeason)) lastFocusedSeason else selectedSeason
        seasonFocusRequesters.getOrPut(target) { FocusRequester() }
    }

    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
    val rowStartPaddingPx = with(LocalDensity.current) { NuvioTheme.spacing.xxxl.toPx() }
    val seasonBringIntoViewSpec = remember(defaultBringIntoViewSpec, rowStartPaddingPx, isRtl) {
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        object : BringIntoViewSpec {
            override val scrollAnimationSpec: AnimationSpec<Float> = defaultBringIntoViewSpec.scrollAnimationSpec

            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                if (isRtl) {
                    (offset + size) - (containerSize - rowStartPaddingPx)
                } else {
                    offset - rowStartPaddingPx
                }
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides seasonBringIntoViewSpec) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { seasonRowHasFocus = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val isHorizontalKey = native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
                        native.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                    if (
                        isHorizontalKey &&
                        native.action == AndroidKeyEvent.ACTION_DOWN &&
                        native.repeatCount > 0
                    ) {
                        val now = System.currentTimeMillis()
                        if (now - lastHorizontalKeyRepeatTime < EPISODE_SCROLL_REPEAT_THROTTLE_MS) {
                            return@onPreviewKeyEvent true
                        }
                        lastHorizontalKeyRepeatTime = now
                    }
                    false
                }
                .focusRestorer {
                    if (pendingSeason != null) {
                        FocusRequester.Cancel
                    } else {
                        restorerRequester
                    }
                }
                .focusGroup(),
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = NuvioTheme.spacing.xxxl, vertical = NuvioTheme.spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(if (posterStyle != null) NuvioTheme.spacing.lg else NuvioTheme.spacing.md)
        ) {
            items(sortedSeasons, key = { it }) { season ->
                val isSelected = season == selectedSeason
                val seasonFocusRequester = if (isSelected && selectedTabFocusRequester != null) {
                    selectedTabFocusRequester
                } else {
                    remember(season) { seasonFocusRequesters.getOrPut(season) { FocusRequester() } }
                }
                SeasonTab(
                    season = season,
                    isSelected = isSelected,
                    isLeftEdge = if (isRtl) season == sortedSeasons.last() else season == sortedSeasons.first(),
                    isRightEdge = if (isRtl) season == sortedSeasons.first() else season == sortedSeasons.last(),
                    isFocusEnabled = isFocusEnabled,
                    rowHasFocus = isSelected && seasonRowHasFocus,
                    isPosterMode = posterStyle != null,
                    posterUrl = posterStyle?.posters?.get(season),
                    focusRequester = seasonFocusRequester,
                    upFocusRequester = upFocusRequester,
                    downFocusRequester = downFocusRequester,
                    onNavigateDown = onNavigateDown,
                    onClick = onTabClick,
                    onLongPress = onTabLongPress,
                    onFocused = onTabFocused,
                    onDpadDownPending = {
                        if (pendingSeason != null) {
                            val s = pendingSeason
                            pendingSeason = null
                            if (s != null) onSeasonSelected(s)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeasonTab(
    season: Int,
    isSelected: Boolean,
    isLeftEdge: Boolean,
    isRightEdge: Boolean,
    isFocusEnabled: Boolean,
    rowHasFocus: Boolean,
    isPosterMode: Boolean,
    posterUrl: String?,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester?,
    onNavigateDown: (() -> Boolean)? = null,
    onClick: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    onFocused: (Int, Boolean) -> Unit,
    onDpadDownPending: () -> Unit = {}
) {
    val tabShape = remember(isPosterMode) {
        RoundedCornerShape(if (isPosterMode) SEASON_TAB_POSTER_CORNER_RADIUS else 20.dp)
    }
    val tabBorder = CardDefaults.border(
        focusedBorder = Border(
            border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
            shape = tabShape
        )
    )
    val selectedPosterTabBorder = CardDefaults.border(
        border = Border(
            border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.TextPrimary.copy(alpha = 0.25f)),
            shape = tabShape
        ),
        focusedBorder = Border(
            border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
            shape = tabShape
        )
    )
    val tabScale = CardDefaults.scale(focusedScale = 1.0f)
    val textSecondary = NuvioTheme.extendedColors.textSecondary
    var isFocused by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    val longPressKeyTracker = rememberLongPressKeyTracker()
    val label = if (season == 0) stringResource(R.string.episodes_specials) else stringResource(R.string.episodes_season, season)
    val isHighlighted = isFocused || (isSelected && !rowHasFocus)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = {
                if (longPressTriggered) {
                    longPressTriggered = false
                } else {
                    onClick(season)
                }
            },
            modifier = Modifier
                .focusRequester(focusRequester)
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
                        onFocused(season, isSelected)
                    }
                }
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (native.action == AndroidKeyEvent.ACTION_DOWN) {
                        if (native.keyCode == AndroidKeyEvent.KEYCODE_MENU) {
                            longPressTriggered = true
                            onLongPress(season)
                            return@onPreviewKeyEvent true
                        }
                    }
                    if (longPressKeyTracker.handle(native, ::isSelectKey) {
                            longPressTriggered = true
                            onLongPress(season)
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
            border = if (isPosterMode && isSelected && !rowHasFocus) selectedPosterTabBorder else tabBorder,
            scale = tabScale
        ) {
            if (isPosterMode) {
                SeasonPosterImage(posterUrl = posterUrl, shape = tabShape)
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        isFocused -> NuvioTheme.colors.OnSecondary
                        isSelected -> NuvioTheme.colors.TextPrimary
                        else -> textSecondary
                    },
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 20.dp)
                )
            }
        }
        if (isPosterMode) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isHighlighted) NuvioTheme.colors.TextPrimary else textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(SEASON_TAB_POSTER_HEIGHT * SEASON_POSTER_ASPECT)
                    .padding(top = NuvioTheme.spacing.sm)
            )
        }
    }
}

@Composable
private fun SeasonPosterImage(posterUrl: String?, shape: Shape) {
    val context = LocalContext.current
    val cardDepthStyle = LocalCardDepthStyle.current
    val density = LocalDensity.current
    val height = SEASON_TAB_POSTER_HEIGHT
    val width = height * SEASON_POSTER_ASPECT
    val imageRequest = remember(posterUrl, density) {
        posterUrl?.let { url ->
            val widthPx = with(density) { width.roundToPx() }.coerceAtLeast(1)
            val heightPx = with(density) { height.roundToPx() }.coerceAtLeast(1)
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .size(width = widthPx * 2, height = heightPx * 2)
                .allowRgb565(false)
                .transformations(SmoothDownscaleTransformation(widthPx, heightPx))
                .build()
        }
    }
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(NuvioTheme.colors.BackgroundCard)
            .nuvioCardDepth(
                shape = shape,
                surface = CardDepthSurface.POSTERS,
                style = cardDepthStyle
            )
    ) {
        if (imageRequest == null) {
            MonochromePosterPlaceholder(modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
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
    isFocusEnabled: Boolean = true,
    ratingMedia: (Video) -> TrackingMediaReference? = { null }
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
            },
            ratingMedia = ratingMedia(selectedEpisode)
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
