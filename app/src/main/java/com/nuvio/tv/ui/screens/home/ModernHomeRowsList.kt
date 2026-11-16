package com.nuvio.tv.ui.screens.home

import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.nuvio.tv.ui.leanback.pool.LocalLeanbackRecycledViewPool

import com.nuvio.tv.domain.model.catalogRowLegacyKey
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.LocalRequestUpdateBannerFocus

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import com.nuvio.tv.domain.model.ContinueWatchingCardStyle
import com.nuvio.tv.LocalSidebarExpanded
import com.nuvio.tv.domain.model.FocusedPosterTrailerPlaybackTarget
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.isPlaceholder
import com.nuvio.tv.ui.util.StableList
import com.nuvio.tv.ui.util.StableMap
import com.nuvio.tv.ui.util.StableRef
import com.nuvio.tv.ui.util.recompositionHighlighter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

private const val VERTICAL_HOLD_START_VELOCITY_DP_PER_SEC = 520f
private const val VERTICAL_HOLD_MAX_VELOCITY_DP_PER_SEC = 960f
private const val VERTICAL_HOLD_RAMP_SEC = 0.32f
private const val VERTICAL_HOLD_IDLE_TIMEOUT_MS = 420L
private const val VERTICAL_STEP_DURATION_MS = 160
private const val VERTICAL_SETTLE_DURATION_MS = 135

private class VerticalRingOverlayHost(context: android.content.Context) : FrameLayout(context) {
    val ringView = ModernRowFloatingFocusRingView(context).apply {
        elevation = 30f
        isFocusable = false
        isFocusableInTouchMode = false
    }

    var onVerticalRepeat: ((Int) -> Boolean)? = null
    var onVerticalRelease: (() -> Unit)? = null
    var onVerticalSingleStep: ((Int) -> Boolean)? = null
    var onEscapeKey: ((AndroidKeyEvent) -> Boolean)? = null
    var isGlideHolding: () -> Boolean = { false }

    init {
        clipChildren = false
        clipToPadding = false
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        val margin = (ringView.glowMarginPx * 2).toInt()
        addView(ringView, LayoutParams(margin.coerceAtLeast(1), margin.coerceAtLeast(1)))
    }

    fun preMeasureCardSize(cardWidthPx: Int, cardHeightPx: Int) {
        if (ringView.visibility == View.VISIBLE) return
        val margin = (ringView.glowMarginPx * 2).toInt()
        val targetW = cardWidthPx + margin
        val targetH = cardHeightPx + margin
        val lp = ringView.layoutParams
        if (lp != null && (lp.width != targetW || lp.height != targetH)) {
            lp.width = targetW
            lp.height = targetH
            ringView.layoutParams = lp
        }
    }

    override fun getRootView(): View = this

    fun activateGlideFocus() {
        isFocusable = true
        isFocusableInTouchMode = true
        if (!hasFocus()) {
            requestFocus()
        }
    }

    fun deactivateGlideFocusAndHideRing() {
        ringView.hideRing(true)
        isFocusable = false
        isFocusableInTouchMode = false
        if (hasFocus()) {
            clearFocus()
        }
    }

    override fun dispatchKeyEvent(event: AndroidKeyEvent?): Boolean {
        if (event == null) return false
        if (event.action == AndroidKeyEvent.ACTION_UP) {
            when (event.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    if (isGlideHolding()) {
                        onVerticalRelease?.invoke()
                        return true
                    }
                }
            }
        }
        if (event.action == AndroidKeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (event.repeatCount > 0) {
                        return onVerticalRepeat?.invoke(1) == true
                    }
                    if (isGlideHolding()) {
                        onVerticalRelease?.invoke()
                    }
                    return onVerticalSingleStep?.invoke(1) == true
                }
                AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                    if (event.repeatCount > 0) {
                        return onVerticalRepeat?.invoke(-1) == true
                    }
                    if (isGlideHolding()) {
                        onVerticalRelease?.invoke()
                    }
                    return onVerticalSingleStep?.invoke(-1) == true
                }
                AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_BACK -> {
                    if (onEscapeKey?.invoke(event) == true) return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

private fun findAnchoredRowInfo(
    visibleItems: List<LazyListItemInfo>,
    probeY: Float,
    maxRowIndex: Int
): LazyListItemInfo? {
    if (visibleItems.isEmpty()) return null
    var bestFallback: LazyListItemInfo? = null
    for (i in visibleItems.indices) {
        val item = visibleItems[i]
        if (item.index > maxRowIndex) continue
        bestFallback = item
        if (item.offset + item.size > probeY) {
            return item
        }
    }
    return bestFallback ?: visibleItems.firstOrNull()
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class,
    kotlinx.coroutines.FlowPreview::class
)
@Composable
internal fun ModernHomeRowsList(
    isVerticalRowsScrollingState: State<Boolean>,
    carouselRows: StableList<HeroCarouselRow>,
    verticalRowListState: LazyListState,
    focusedItemByRow: StableRef<MutableMap<String, Int>>,
    loadMoreRequestedTotals: StableRef<MutableMap<String, Int>>,
    focusState: HomeScreenFocusState,
    activeRowKey: State<String?>,
    activeItemIndex: State<Int>,
    isFastScrolling: State<Boolean>,
    onFastScrollingChanged: (Boolean) -> Unit,
    contentFocusRequester: FocusRequester,
    rowsViewportHeight: Dp,
    catalogBottomPadding: Dp,
    trailerContentAlpha: () -> Float,
    verticalRowBringIntoViewSpec: BringIntoViewSpec,
    onRowItemFocusedInternal: (String, Int, Boolean) -> Unit,
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToFolderDetail: (String, String) -> Unit,
    onLoadMoreCatalog: (String, String, String) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onContinueWatchingOptions: (ContinueWatchingItem) -> Unit,
    onRequestLazyCatalogLoad: (String) -> Unit,
    onBackdropInteraction: () -> Unit,
    onExpandedCatalogFocusKeyChange: (String?) -> Unit,
    isCatalogItemWatched: (MetaPreview) -> Boolean,
    onCatalogItemLongPress: (MetaPreview, String) -> Unit,
    onItemFocus: (MetaPreview) -> Unit,
    onPreloadAdjacentItem: (MetaPreview) -> Unit,
    enrichedPreviews: State<StableMap<String, MetaPreview>>,
    trailerPreviewUrls: StableMap<String, String>,
    trailerPreviewAudioUrls: StableMap<String, String>,
    useLandscapePosters: Boolean,
    alwaysShowLandscapeClearlogo: Boolean = false,
    showLabels: Boolean,
    posterCardCornerRadius: Dp,
    focusedPosterBackdropTrailerMuted: Boolean,
    effectiveExpandEnabled: Boolean,
    effectiveAutoplayEnabled: Boolean,
    trailerPlaybackTarget: FocusedPosterTrailerPlaybackTarget,
    expandedCatalogFocusKey: State<String?>,
    expandedTrailerPreviewUrl: () -> String?,
    expandedTrailerPreviewAudioUrl: () -> String?,
    portraitCatalogCardWidth: Dp,
    portraitCatalogCardHeight: Dp,
    landscapeCatalogCardWidth: Dp,
    landscapeCatalogCardHeight: Dp,
    continueWatchingCardWidth: Dp,
    continueWatchingCardHeight: Dp,
    blurUnwatchedEpisodes: Boolean,
    useEpisodeThumbnails: Boolean,
    continueWatchingCardStyle: ContinueWatchingCardStyle,
    continueWatchingCornerRadius: Dp,
    pendingRowFocusKey: State<String?>,
    pendingRowFocusIndex: State<Int?>,
    pendingRowFocusNonce: State<Int>,
    onPendingRowFocusCleared: () -> Unit,
    onActiveRowKeyChange: (String?) -> Unit,
    onActiveItemIndexChange: (Int) -> Unit,
    lastHeroNavigationAtMs: State<Long>,
    onLastHeroNavigationAtMsChange: (Long) -> Unit,
    onHeroFocusSettleDelayChange: (Long) -> Unit,
    lastFocusedContinueWatchingIndex: State<Int>,
    onLastFocusedContinueWatchingIndexChange: (Int) -> Unit,
    focusedCatalogSelection: State<FocusedCatalogSelection?>,
    onFocusedCatalogSelectionChange: (FocusedCatalogSelection?) -> Unit,
    focusedHeroMediaNonce: State<Int>,
    onFocusedHeroMediaNonceChange: (Int) -> Unit,
    onExpansionInteractionNonceChange: (Int) -> Unit,
    blockLeftOnFirstExpandedItem: Boolean = false,
    onUserNavigated: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Unwrap StableRef wrappers for internal use (not passed to child composables)
    val animationsEnabled = NuvioTheme.animationsEnabled
    val isSidebarExpanded = LocalSidebarExpanded.current
    val isSidebarExpandedState = rememberUpdatedState(isSidebarExpanded)
    val focusedItemByRowMap = focusedItemByRow.value
    val loadMoreRequestedTotalsMap = loadMoreRequestedTotals.value

    val latestOnActiveRowKeyChange = rememberUpdatedState(onActiveRowKeyChange)
    val latestOnActiveItemIndexChange = rememberUpdatedState(onActiveItemIndexChange)

    val rowFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val rowGrids = remember { mutableMapOf<String, androidx.leanback.widget.HorizontalGridView>() }
    val externalPool = LocalLeanbackRecycledViewPool.current
    val sharedRecycledViewPool = remember(externalPool) {
        externalPool ?: RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(0, 56)
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var localPendingFocusRowKey by remember { mutableStateOf<String?>(null) }
    val stableClearPendingFocus = remember { { localPendingFocusRowKey = null } }
    var localPendingFocusIndex by remember { mutableIntStateOf(0) }
    var localPendingFocusNonce by remember { mutableIntStateOf(0) }
    var targetRowIndexTracker by remember { mutableIntStateOf(0) }
    // Single source of truth for "which row should own focus", updated synchronously on every
    // deliberate row change (activeRowKey is Compose state and lags one frame behind).
    val navTargetRowKey = remember { java.util.concurrent.atomic.AtomicReference<String?>(null) }
    // Last row that actually reported focus. Kept next to navTargetRowKey so the focus
    // callbacks never have to wait a frame for activeRowKey (Compose state) to catch up.
    val currentRowKeyTracker = remember { java.util.concurrent.atomic.AtomicReference<String?>(null) }
    val isVerticalGlideRef = remember { AtomicBoolean(false) }
    val verticalHoldJobRef = remember { AtomicReference<Job?>(null) }
    val verticalHoldTimeoutRef = remember { AtomicReference<Job?>(null) }
    val verticalHoldDirectionRef = remember { AtomicInteger(0) }
    val overlayGenerationRef = remember { AtomicLong(0L) }
    val verticalOverlayHostRef = remember { AtomicReference<VerticalRingOverlayHost?>(null) }
    val latestOnFastScrollingChanged = rememberUpdatedState(onFastScrollingChanged)
    val isVerticalGlideActiveLambda = remember { { isVerticalGlideRef.get() } }

    DisposableEffect(Unit) {
        onDispose {
            overlayGenerationRef.incrementAndGet()
            verticalHoldDirectionRef.set(0)
            verticalHoldJobRef.getAndSet(null)?.cancel()
            verticalHoldTimeoutRef.getAndSet(null)?.cancel()
            scrollJob?.cancel()
            isVerticalGlideRef.set(false)
            latestOnFastScrollingChanged.value(false)
            verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
        }
    }

    LaunchedEffect(pendingRowFocusNonce.value) {
        val targetKey = pendingRowFocusKey.value ?: return@LaunchedEffect
        val targetIdx = pendingRowFocusIndex.value ?: 0
        navTargetRowKey.set(targetKey)
        currentRowKeyTracker.set(targetKey)
        // Publish the target immediately: the target row may not be composed yet, and without
        // this the Compose-side active row would keep pointing at the row we just left.
        latestOnActiveRowKeyChange.value(targetKey)
        latestOnActiveItemIndexChange.value(targetIdx)
        localPendingFocusRowKey = targetKey
        localPendingFocusIndex = targetIdx
        localPendingFocusNonce++

        val rowIdx = carouselRows.list.indexOfFirst { it.key == targetKey }
        if (rowIdx >= 0) {
            targetRowIndexTracker = rowIdx
            if (verticalRowListState.firstVisibleItemIndex != rowIdx) {
                scrollJob?.cancel()
                scrollJob = coroutineScope.launch {
                    if (animationsEnabled) {
                        verticalRowListState.animateScrollToItem(rowIdx)
                    } else {
                        verticalRowListState.scrollToItem(rowIdx)
                    }
                }
            }
        }
        val targetGrid = rowGrids[targetKey]
        if (targetGrid != null && targetGrid.isAttachedToWindow) {
            targetGrid.post {
                // The user may have moved on before this post ran; never pull focus back.
                if (targetGrid.isAttachedToWindow && navTargetRowKey.get() == targetKey) {
                    targetGrid.setSelectedPosition(targetIdx)
                    targetGrid.requestFocus()
                    val vh = targetGrid.findViewHolderForAdapterPosition(targetIdx)
                    if (vh != null) {
                        vh.itemView.requestFocus()
                        (vh as? LeanbackCardViewHolder)?.updateFocusVisual(true)
                    } else {
                        targetGrid.post {
                            if (targetGrid.isAttachedToWindow && navTargetRowKey.get() == targetKey) {
                                val deferredVh = targetGrid.findViewHolderForAdapterPosition(targetIdx)
                                deferredVh?.itemView?.requestFocus()
                                (deferredVh as? LeanbackCardViewHolder)?.updateFocusVisual(true)
                            }
                        }
                    }
                    val modernGrid = targetGrid as? InteropHorizontalGridView
                    modernGrid?.ensureChildFocusAndRing(targetPos = targetIdx, animated = false)
                    targetGrid.post {
                        modernGrid?.ensureChildFocusAndRing(targetPos = targetIdx, animated = false)
                    }
                }
            }
            localPendingFocusRowKey = null
        }
        // Always release the pending request, even when the target grid is not attached yet.
        // The row-level pending focus (localPendingFocusRowKey) hands it off once the row
        // composes; leaving the request armed made a later recomposition steal focus back
        // to a row the user had already left.
        onPendingRowFocusCleared()
    }

    LaunchedEffect(activeRowKey.value) {
        val currentKey = activeRowKey.value ?: return@LaunchedEffect
        if (navTargetRowKey.get() == null) {
            navTargetRowKey.set(currentKey)
            currentRowKeyTracker.set(currentKey)
        }
        // The intended row can already be one step ahead of activeRowKey when a row change
        // was requested from a key callback; hide the ring based on that, not the lagging value.
        val ringOwnerKey = navTargetRowKey.get()
            ?.takeIf { key -> carouselRows.list.any { it.key == key } }
            ?: currentKey
        val idx = carouselRows.list.indexOfFirst { it.key == currentKey }
        if (idx >= 0) {
            if (targetRowIndexTracker != idx && verticalRowListState.firstVisibleItemIndex != idx) {
                targetRowIndexTracker = idx
                scrollJob?.cancel()
                scrollJob = coroutineScope.launch {
                    if (animationsEnabled) {
                        verticalRowListState.animateScrollToItem(idx)
                    } else {
                        verticalRowListState.scrollToItem(idx)
                    }
                }
            }
        }
        rowGrids.forEach { (key, grid) ->
            if (key != ringOwnerKey) {
                (grid as? InteropHorizontalGridView)?.floatingRingView?.hideRing(false)
            }
        }
    }

    val density = LocalDensity.current
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val rowStartPaddingPx = with(density) { 52.dp.toPx() }
    val portraitCardWidthPx = with(density) { portraitCatalogCardWidth.roundToPx() }.coerceAtLeast(1)
    val portraitCardHeightPx = with(density) { portraitCatalogCardHeight.roundToPx() }.coerceAtLeast(1)
    val landscapeCardWidthPx = with(density) { landscapeCatalogCardWidth.roundToPx() }.coerceAtLeast(1)
    val landscapeCardHeightPx = with(density) { landscapeCatalogCardHeight.roundToPx() }.coerceAtLeast(1)
    val cwCardWidthPx = with(density) { continueWatchingCardWidth.roundToPx() }.coerceAtLeast(1)
    val cwCardHeightPx = with(density) { continueWatchingCardHeight.roundToPx() }.coerceAtLeast(1)
    val catalogCardWidthPx = if (useLandscapePosters) landscapeCardWidthPx else portraitCardWidthPx
    val catalogCardHeightPx = if (useLandscapePosters) landscapeCardHeightPx else portraitCardHeightPx
    val posterCornerRadiusPx = with(density) { posterCardCornerRadius.toPx() }
    val cwCornerRadiusPx = with(density) { continueWatchingCornerRadius.toPx() }
    val titleLineHeight = MaterialTheme.typography.titleMedium.lineHeight
    val defaultCardTopInRowPx = with(density) {
        val titleHeightDp = runCatching { titleLineHeight.toDp() }.getOrDefault(24.dp)
        (titleHeightDp + 4.dp + 6.dp).toPx()
    }
    val cachedCardTopInRowRef = remember(defaultCardTopInRowPx) {
        AtomicReference(defaultCardTopInRowPx)
    }
    val verticalPrefetchImageLoader = context.imageLoader
    val latestCarouselRowsForImagePrefetch = rememberUpdatedState(carouselRows)

    LaunchedEffect(
        verticalPrefetchImageLoader,
        verticalRowListState,
        density,
        useLandscapePosters,
        effectiveExpandEnabled,
        portraitCatalogCardWidth,
        portraitCatalogCardHeight,
        landscapeCatalogCardWidth,
        landscapeCatalogCardHeight
    ) {
        val prefetchAheadRows = 1
        val prefetchItemsPerRow = 4
        snapshotFlow {
            verticalRowListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }
            .distinctUntilChanged()
            .debounce(240L) // VERTICAL_PREFETCH_DEBOUNCE_MS
            .collect { lastVisibleRowIndex ->
                withContext(Dispatchers.IO) {
                    for (rowOffset in 1..prefetchAheadRows) {
                        val row = latestCarouselRowsForImagePrefetch.value.list
                            .getOrNull(lastVisibleRowIndex + rowOffset) ?: continue
                        for (i in 0 until minOf(prefetchItemsPerRow, row.items.list.size)) {
                            val item = row.items.list[i]
                            val url = item.imageUrl ?: item.heroPreview.poster ?: item.heroPreview.backdrop ?: continue
                            val metrics = item.catalogCardMetrics(
                                useLandscapePosters = useLandscapePosters,
                                portraitCardWidth = portraitCatalogCardWidth,
                                portraitCardHeight = portraitCatalogCardHeight,
                                landscapeCardWidth = landscapeCatalogCardWidth,
                                landscapeCardHeight = landscapeCatalogCardHeight
                            )
                            val wPx = with(density) { metrics.width.roundToPx() }.coerceAtLeast(1)
                            val hPx = with(density) { metrics.height.roundToPx() }.coerceAtLeast(1)
                            val mappedUrl = com.nuvio.tv.core.image.sizedHomeArtworkUrl(url, wPx, hPx)
                            val cacheKey = "${mappedUrl}_${wPx}x${hPx}"
                            if (verticalPrefetchImageLoader.memoryCache?.get(MemoryCache.Key(cacheKey)) != null) continue
                            verticalPrefetchImageLoader.enqueue(
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .memoryCacheKey(cacheKey)
                                    .size(width = wPx, height = hPx)
                                    .build()
                            )
                        }
                    }
                }
            }
    }

    val latestOnRequestLazyCatalogLoad = rememberUpdatedState(onRequestLazyCatalogLoad)
    val latestCarouselRowsForLazy = rememberUpdatedState(carouselRows)
    val openSidebar = com.nuvio.tv.LocalOpenSidebar.current
    val requestUpdateBannerFocus = LocalRequestUpdateBannerFocus.current
    val latestRequestUpdateBannerFocus = rememberUpdatedState(requestUpdateBannerFocus)
    // The row that currently owns (or is about to own) focus. Falls back to activeRowKey when the
    // nav target was removed from the list.
    val effectiveActiveRowKey: () -> String? = remember {
        {
            navTargetRowKey.get()
                ?.takeIf { key -> latestCarouselRowsForLazy.value.list.any { it.key == key } }
                ?: activeRowKey.value
        }
    }

    fun isContinueWatchingRowKey(key: String?): Boolean =
        key == MODERN_CONTINUE_WATCHING_ROW_KEY || key == MODERN_UPCOMING_ROW_KEY

    fun rowCardDimensionsPx(rowKey: String?): Triple<Int, Int, Float> =
        if (isContinueWatchingRowKey(rowKey)) {
            Triple(cwCardWidthPx, cwCardHeightPx, cwCornerRadiusPx)
        } else {
            Triple(catalogCardWidthPx, catalogCardHeightPx, posterCornerRadiusPx)
        }

    fun resolveAnchoredLeftPx(cardWidthPx: Int): Float {
        val host = verticalOverlayHostRef.get()
        return if (layoutDirection == LayoutDirection.Rtl && host != null && host.width > 0) {
            (host.width - rowStartPaddingPx - cardWidthPx).coerceAtLeast(0f)
        } else {
            rowStartPaddingPx
        }
    }

    fun handoffOverlayToGrid(
        rowKey: String,
        targetCardIndex: Int,
        expectedGen: Long,
        retryCount: Int = 0
    ) {
        if (overlayGenerationRef.get() != expectedGen) return
        val grid = rowGrids[rowKey] as? InteropHorizontalGridView
        if (grid != null && grid.isAttachedToWindow) {
            grid.setSelectedPosition(targetCardIndex)
            grid.requestFocus()
            val vh = grid.findViewHolderForAdapterPosition(targetCardIndex) as? LeanbackCardViewHolder
            val ring = grid.floatingRingView
            if (vh != null && ring != null && vh.cardContainer.width > 0 && vh.cardContainer.height > 0) {
                isVerticalGlideRef.set(false)
                grid.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
                if (ring.visibility == View.VISIBLE && ring.width > 0 && ring.height > 0) {
                    verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
                    return
                }
            }
            if (retryCount < 8) {
                grid.post {
                    handoffOverlayToGrid(rowKey, targetCardIndex, expectedGen, retryCount + 1)
                }
                return
            }
            isVerticalGlideRef.set(false)
            grid.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
            verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
        } else {
            isVerticalGlideRef.set(false)
            localPendingFocusRowKey = rowKey
            localPendingFocusIndex = targetCardIndex
            localPendingFocusNonce++
            verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
        }
    }

    fun finishOverlayRingToCurrentGrid() {
        val overlayHost = verticalOverlayHostRef.get()
        val overlayActive = isVerticalGlideRef.get() ||
            scrollJob?.isActive == true ||
            overlayHost?.ringView?.visibility == View.VISIBLE ||
            overlayHost?.hasFocus() == true
        if (!overlayActive) return

        overlayGenerationRef.incrementAndGet()
        verticalHoldDirectionRef.set(0)
        verticalHoldJobRef.getAndSet(null)?.cancel()
        verticalHoldTimeoutRef.getAndSet(null)?.cancel()
        scrollJob?.cancel()
        isVerticalGlideRef.set(false)
        latestOnFastScrollingChanged.value(false)
        overlayHost?.deactivateGlideFocusAndHideRing()
        // Hand focus back cleanly without snapping selectedPosition backward over a pending horizontal move
        val key = effectiveActiveRowKey()
        val grid = key?.let { rowGrids[it] as? InteropHorizontalGridView }
        if (grid != null && grid.isAttachedToWindow && !grid.hasFocus()) {
            grid.requestFocus()
        }
    }

    fun measureFocusedCardInOverlay(rowKey: String?): FloatArray? {
        val host = verticalOverlayHostRef.get() ?: return null
        if (!host.isAttachedToWindow) return null
        val grid = rowKey?.let { rowGrids[it] as? InteropHorizontalGridView }
        val cardView = grid?.floatingRingView?.currentTargetView ?: grid?.findFocus() ?: return null
        if (!cardView.isAttachedToWindow || cardView.width <= 0 || cardView.height <= 0) return null
        val hostLoc = IntArray(2)
        val cardLoc = IntArray(2)
        host.getLocationInWindow(hostLoc)
        cardView.getLocationInWindow(cardLoc)
        val curX = (cardLoc[0] - hostLoc[0]).toFloat()
        val curY = (cardLoc[1] - hostLoc[1]).toFloat()
        val rowInfo = verticalRowListState.layoutInfo.visibleItemsInfo.firstOrNull { (it.key as? String) == rowKey }
        if (rowInfo != null) {
            val measuredTop = (curY - rowInfo.offset).coerceAtLeast(0f)
            if (measuredTop > 0f) {
                cachedCardTopInRowRef.set(measuredTop)
            }
        }
        return floatArrayOf(curX, curY, cardView.width.toFloat(), cardView.height.toFloat())
    }

    fun findLastNonEmptyRowIndex(rows: List<HeroCarouselRow>): Int =
        rows.indexOfLast { it.items.list.isNotEmpty() }

    fun shouldHaltDownwardGlide(rows: List<HeroCarouselRow>, lastNonEmptyIdx: Int): Boolean {
        if (lastNonEmptyIdx < 0) return true
        val visible = verticalRowListState.layoutInfo.visibleItemsInfo
        val lastNonEmptyInfo = visible.firstOrNull { it.index == lastNonEmptyIdx }
        return lastNonEmptyInfo != null && lastNonEmptyInfo.offset <= 0
    }

    fun endVerticalHoldScroll(immediate: Boolean = false) {
        val dir = verticalHoldDirectionRef.getAndSet(0)
        val activeJob = verticalHoldJobRef.getAndSet(null)
        verticalHoldTimeoutRef.getAndSet(null)?.cancel()
        if (dir == 0 && activeJob == null) return
        activeJob?.cancel()
        latestOnFastScrollingChanged.value(false)

        val rows = latestCarouselRowsForLazy.value.list
        if (rows.isEmpty()) {
            overlayGenerationRef.incrementAndGet()
            isVerticalGlideRef.set(false)
            verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
            return
        }
        val lastNonEmptyIdx = findLastNonEmptyRowIndex(rows).coerceAtLeast(0)
        val visible = verticalRowListState.layoutInfo.visibleItemsInfo
        val anchorY = cachedCardTopInRowRef.get()
        val currentRingHeight = verticalOverlayHostRef.get()?.ringView?.let { rv ->
            (rv.height - (rv.glowMarginPx * 2f)).takeIf { it > 0f }
        } ?: catalogCardHeightPx.toFloat()
        val probeY = anchorY + currentRingHeight * 0.45f
        val anchoredInfo = findAnchoredRowInfo(visible, probeY, lastNonEmptyIdx)
        val baseIdx = (anchoredInfo?.index ?: targetRowIndexTracker).coerceIn(0, lastNonEmptyIdx)
        // Bias landing row in the user's glide direction so releasing never snaps backward
        val landingIdx = when {
            dir > 0 && anchoredInfo != null &&
                anchoredInfo.offset < -(anchoredInfo.size * 0.25f) &&
                baseIdx + 1 <= lastNonEmptyIdx &&
                rows.getOrNull(baseIdx + 1)?.items?.list?.isNotEmpty() == true -> baseIdx + 1
            dir < 0 && anchoredInfo != null &&
                anchoredInfo.offset > (anchoredInfo.size * 0.35f) &&
                baseIdx > 0 &&
                rows.getOrNull(baseIdx - 1)?.items?.list?.isNotEmpty() == true -> baseIdx - 1
            else -> baseIdx
        }
        val landingRow = rows.getOrNull(landingIdx) ?: run {
            overlayGenerationRef.incrementAndGet()
            isVerticalGlideRef.set(false)
            verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
            return
        }

        val prevKey = navTargetRowKey.get() ?: currentRowKeyTracker.get() ?: activeRowKey.value
        if (prevKey != null && prevKey != landingRow.key) {
            (rowGrids[prevKey] as? InteropHorizontalGridView)?.let { prevGrid ->
                prevGrid.prepareForRowChange()
                prevGrid.floatingRingView?.hideRing(true)
            }
        }

        targetRowIndexTracker = landingIdx
        navTargetRowKey.set(landingRow.key)
        currentRowKeyTracker.set(landingRow.key)
        val targetCardIndex = (focusedItemByRowMap[landingRow.key] ?: 0)
            .coerceIn(0, (landingRow.items.list.size - 1).coerceAtLeast(0))
        latestOnActiveRowKeyChange.value(landingRow.key)
        latestOnActiveItemIndexChange.value(targetCardIndex)

        val (landingW, landingH, landingR) = rowCardDimensionsPx(landingRow.key)
        val landingAnchorX = resolveAnchoredLeftPx(landingW)
        val landingGrid = rowGrids[landingRow.key]
        val landingColor = resolveRowTargetGlowColor(
            grid = landingGrid,
            cardIndex = targetCardIndex,
            fallbackItem = landingRow.items.list.getOrNull(targetCardIndex)
        )
        val settleGen = overlayGenerationRef.incrementAndGet()

        val liveInfo = visible.firstOrNull { it.index == landingIdx }
        scrollJob?.cancel()
        if (immediate || !animationsEnabled) {
            if (liveInfo == null || liveInfo.offset != 0) {
                scrollJob = coroutineScope.launch {
                    verticalRowListState.scrollToItem(landingIdx, 0)
                    handoffOverlayToGrid(landingRow.key, targetCardIndex, settleGen)
                }
            } else {
                handoffOverlayToGrid(landingRow.key, targetCardIndex, settleGen)
            }
        } else if (liveInfo != null && liveInfo.offset != 0) {
            verticalOverlayHostRef.get()?.ringView?.showFixedOverlayAt(
                xPx = landingAnchorX,
                yPx = anchorY,
                cardWidthPx = landingW,
                cardHeightPx = landingH,
                cornerRadiusPx = landingR,
                dominantColor = landingColor
            )
            scrollJob = coroutineScope.launch {
                try {
                    verticalRowListState.animateScrollBy(
                        value = liveInfo.offset.toFloat(),
                        animationSpec = tween(
                            durationMillis = VERTICAL_SETTLE_DURATION_MS,
                            easing = FastOutSlowInEasing
                        )
                    )
                } finally {
                    handoffOverlayToGrid(landingRow.key, targetCardIndex, settleGen)
                }
            }
        } else if (liveInfo == null) {
            scrollJob = coroutineScope.launch {
                verticalRowListState.scrollToItem(landingIdx, 0)
                handoffOverlayToGrid(landingRow.key, targetCardIndex, settleGen)
            }
        } else {
            handoffOverlayToGrid(landingRow.key, targetCardIndex, settleGen)
        }
    }

    fun startOrExtendVerticalHoldScroll(sign: Int): Boolean {
        onUserNavigated()
        val rows = latestCarouselRowsForLazy.value.list
        if (rows.isEmpty()) return false
        val lastNonEmptyIdx = findLastNonEmptyRowIndex(rows)
        if (sign > 0 && shouldHaltDownwardGlide(rows, lastNonEmptyIdx)) {
            if (verticalHoldDirectionRef.get() != 0 || verticalHoldJobRef.get()?.isActive == true) {
                endVerticalHoldScroll()
            }
            return true
        }
        if (sign < 0 && !verticalRowListState.canScrollBackward) {
            if (verticalHoldDirectionRef.get() != 0 || verticalHoldJobRef.get()?.isActive == true) {
                endVerticalHoldScroll()
            }
            return true
        }

        val needsStart = verticalHoldDirectionRef.get() != sign ||
            verticalHoldJobRef.get()?.isActive != true

        if (needsStart) {
            scrollJob?.cancel()
            verticalHoldJobRef.getAndSet(null)?.cancel()
            val glideGen = overlayGenerationRef.incrementAndGet()
            verticalHoldDirectionRef.set(sign)
            isVerticalGlideRef.set(true)
            latestOnFastScrollingChanged.value(true)

            val host = verticalOverlayHostRef.get()
            val existingRing = host?.ringView
            val visibleRing = existingRing?.takeIf {
                it.visibility == View.VISIBLE && it.width > 0 && it.height > 0
            }
            val currentKey = navTargetRowKey.get() ?: currentRowKeyTracker.get() ?: activeRowKey.value
            val (defaultW, defaultH, defaultR) = rowCardDimensionsPx(currentKey)
            val anchorY = cachedCardTopInRowRef.get()
            val measuredRect = if (visibleRing != null) null else measureFocusedCardInOverlay(currentKey)
            val startX = when {
                visibleRing != null -> visibleRing.translationX + visibleRing.glowMarginPx
                measuredRect != null -> measuredRect[0]
                else -> resolveAnchoredLeftPx(defaultW)
            }
            val startY = when {
                visibleRing != null -> visibleRing.translationY + visibleRing.glowMarginPx
                measuredRect != null -> measuredRect[1]
                else -> anchorY
            }
            val startW = when {
                visibleRing != null -> (visibleRing.width - (visibleRing.glowMarginPx * 2f)).coerceAtLeast(1f)
                measuredRect != null -> measuredRect[2]
                else -> defaultW.toFloat()
            }
            val startH = when {
                visibleRing != null -> (visibleRing.height - (visibleRing.glowMarginPx * 2f)).coerceAtLeast(1f)
                measuredRect != null -> measuredRect[3]
                else -> defaultH.toFloat()
            }
            val currentCardIdx = currentKey?.let { focusedItemByRowMap[it] } ?: 0
            val startColor = resolveRowTargetGlowColor(
                grid = currentKey?.let { rowGrids[it] },
                cardIndex = currentCardIdx,
                fallbackItem = rows.firstOrNull { it.key == currentKey }?.items?.list?.getOrNull(currentCardIdx)
            )

            existingRing?.showFixedOverlayAt(
                xPx = startX,
                yPx = startY,
                cardWidthPx = startW.roundToInt(),
                cardHeightPx = startH.roundToInt(),
                cornerRadiusPx = defaultR,
                dominantColor = startColor
            )
            if (currentKey != null) {
                (rowGrids[currentKey] as? InteropHorizontalGridView)?.floatingRingView?.hideRing(true)
            }
            host?.activateGlideFocus()

            val startVelPx = with(density) { VERTICAL_HOLD_START_VELOCITY_DP_PER_SEC.dp.toPx() }
            val maxVelPx = with(density) { VERTICAL_HOLD_MAX_VELOCITY_DP_PER_SEC.dp.toPx() }

            verticalHoldJobRef.set(
                coroutineScope.launch {
                    try {
                        var elapsedSec = 0f
                        var smoothedDt = 1f / 60f
                        var displayX = startX
                        var displayY = startY
                        var targetX = startX
                        var targetW = startW.roundToInt()
                        var targetH = startH.roundToInt()
                        var targetR = defaultR
                        var targetColor = startColor
                        var lastPassingRowKey: String? = currentKey
                        var appliedX = startX
                        var appliedY = startY
                        var appliedW = targetW
                        var appliedH = targetH
                        var appliedR = targetR
                        var appliedColor = targetColor

                        verticalRowListState.scroll {
                            var lastFrameNs = withFrameNanos { it }
                            while (true) {
                                val nowNs = withFrameNanos { it }
                                val rawDt = ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.004f, 0.032f)
                                lastFrameNs = nowNs
                                // Smooth frame delta to avoid micro-stutter when a new row enters composition
                                smoothedDt = smoothedDt * 0.7f + rawDt * 0.3f
                                elapsedSec += smoothedDt

                                val liveRows = latestCarouselRowsForLazy.value.list
                                val liveLastNonEmpty = findLastNonEmptyRowIndex(liveRows)
                                if (sign > 0 && shouldHaltDownwardGlide(liveRows, liveLastNonEmpty)) break
                                if (sign < 0 && !verticalRowListState.canScrollBackward) break

                                val t = (elapsedSec / VERTICAL_HOLD_RAMP_SEC).coerceIn(0f, 1f)
                                val smoothT = t * t * (3f - 2f * t)
                                val velPx = startVelPx + (maxVelPx - startVelPx) * smoothT
                                val delta = sign * velPx * smoothedDt
                                val consumed = scrollBy(delta)
                                if (consumed == 0f && delta != 0f) break

                                val visible = verticalRowListState.layoutInfo.visibleItemsInfo
                                val probeY = anchorY + targetH * 0.45f
                                val passingInfo = findAnchoredRowInfo(visible, probeY, liveLastNonEmpty.coerceAtLeast(0))
                                val passingRow = passingInfo?.let { liveRows.getOrNull(it.index) }
                                if (passingRow != null && passingRow.key != lastPassingRowKey) {
                                    lastPassingRowKey = passingRow.key
                                    val (nextW, nextH, nextR) = rowCardDimensionsPx(passingRow.key)
                                    targetW = nextW
                                    targetH = nextH
                                    targetR = nextR
                                    targetX = resolveAnchoredLeftPx(nextW)
                                    val cardIdx = (focusedItemByRowMap[passingRow.key] ?: 0)
                                        .coerceIn(0, (passingRow.items.list.size - 1).coerceAtLeast(0))
                                    targetColor = resolveRowTargetGlowColor(
                                        grid = rowGrids[passingRow.key],
                                        cardIndex = cardIdx,
                                        fallbackItem = passingRow.items.list.getOrNull(cardIdx)
                                    )
                                }

                                val settleFactor = (smoothedDt * 18f).coerceIn(0.25f, 1f)
                                displayX = if (kotlin.math.abs(targetX - displayX) < 0.5f) {
                                    targetX
                                } else {
                                    displayX + (targetX - displayX) * settleFactor
                                }
                                displayY = if (kotlin.math.abs(anchorY - displayY) < 0.5f) {
                                    anchorY
                                } else {
                                    displayY + (anchorY - displayY) * settleFactor
                                }

                                // Only call showFixedOverlayAt when bounds, position, or row color actually changed
                                // so same-size catalog rows avoid 60fps BlurMaskFilter redraws and requestLayout().
                                val styleOrBoundsChanged = appliedW != targetW ||
                                    appliedH != targetH ||
                                    appliedR != targetR ||
                                    appliedColor != targetColor
                                val posChanged = kotlin.math.abs(appliedX - displayX) >= 0.5f ||
                                    kotlin.math.abs(appliedY - displayY) >= 0.5f
                                if (styleOrBoundsChanged || posChanged) {
                                    verticalOverlayHostRef.get()?.ringView?.showFixedOverlayAt(
                                        xPx = displayX,
                                        yPx = displayY,
                                        cardWidthPx = targetW,
                                        cardHeightPx = targetH,
                                        cornerRadiusPx = targetR,
                                        dominantColor = targetColor
                                    )
                                    appliedX = displayX
                                    appliedY = displayY
                                    appliedW = targetW
                                    appliedH = targetH
                                    appliedR = targetR
                                    appliedColor = targetColor
                                }
                            }
                        }
                        if (overlayGenerationRef.get() == glideGen) {
                            endVerticalHoldScroll()
                        }
                    } catch (_: CancellationException) {
                    }
                }
            )
        }

        verticalHoldTimeoutRef.getAndSet(null)?.cancel()
        verticalHoldTimeoutRef.set(
            coroutineScope.launch {
                delay(VERTICAL_HOLD_IDLE_TIMEOUT_MS)
                endVerticalHoldScroll()
            }
        )
        return true
    }

    fun stepVerticalRow(sign: Int): Boolean {
        onUserNavigated()
        val rows = latestCarouselRowsForLazy.value.list
        if (rows.isEmpty()) return false
        val currentKey = effectiveActiveRowKey()
        val currentIdx = currentKey?.let { key -> rows.indexOfFirst { it.key == key } }
            ?.takeIf { it >= 0 }
            ?: targetRowIndexTracker.coerceIn(0, rows.lastIndex)
        val targetIdx = currentIdx + sign

        if (sign < 0 && targetIdx < 0) {
            return latestRequestUpdateBannerFocus.value?.invoke() == true
        }
        if (targetIdx !in rows.indices) {
            return false
        }

        val targetRow = rows[targetIdx]
        if (targetRow.items.list.isEmpty()) {
            val addonId = targetRow.addonId
            val apiType = targetRow.apiType
            val catalogId = targetRow.catalogId
            if (addonId != null && apiType != null && catalogId != null) {
                latestOnRequestLazyCatalogLoad.value(
                    catalogRowLegacyKey(addonId, apiType, catalogId)
                )
            }
            return true
        }

        val prevKey = currentKey ?: currentRowKeyTracker.get() ?: activeRowKey.value
        val measuredBefore = measureFocusedCardInOverlay(prevKey)
        val overlayHost = verticalOverlayHostRef.get()

        targetRowIndexTracker = targetIdx
        navTargetRowKey.set(targetRow.key)
        currentRowKeyTracker.set(targetRow.key)
        val targetCardIndex = (focusedItemByRowMap[targetRow.key] ?: 0)
            .coerceIn(0, (targetRow.items.list.size - 1).coerceAtLeast(0))
        latestOnActiveRowKeyChange.value(targetRow.key)
        latestOnActiveItemIndexChange.value(targetCardIndex)

        val targetGrid = rowGrids[targetRow.key]
        val (targetW, targetH, targetR) = rowCardDimensionsPx(targetRow.key)
        val anchorX = resolveAnchoredLeftPx(targetW)
        val anchorY = cachedCardTopInRowRef.get()
        val targetColor = resolveRowTargetGlowColor(
            grid = targetGrid,
            cardIndex = targetCardIndex,
            fallbackItem = targetRow.items.list.getOrNull(targetCardIndex)
        )

        val canUseFixedStepRing = animationsEnabled &&
            overlayHost != null &&
            (measuredBefore != null || overlayHost.ringView.visibility == View.VISIBLE)

        val stepGen = overlayGenerationRef.incrementAndGet()
        if (canUseFixedStepRing) {
            val ringView = checkNotNull(overlayHost).ringView
            val prevMargin = (ringView.glowMarginPx * 2).toInt()
            val sizeChanged = ringView.width <= 0 ||
                ringView.height <= 0 ||
                ringView.width != targetW + prevMargin ||
                ringView.height != targetH + prevMargin
            isVerticalGlideRef.set(true)
            ringView.showFixedOverlayAt(
                xPx = anchorX,
                yPx = anchorY,
                cardWidthPx = targetW,
                cardHeightPx = targetH,
                cornerRadiusPx = targetR,
                dominantColor = targetColor
            )
            if (prevKey != null) {
                (rowGrids[prevKey] as? InteropHorizontalGridView)?.let { prevGrid ->
                    prevGrid.prepareForRowChange()
                    if (sizeChanged && prevGrid.floatingRingView?.visibility == View.VISIBLE) {
                        prevGrid.post { prevGrid.floatingRingView?.hideRing(true) }
                    } else {
                        prevGrid.floatingRingView?.hideRing(true)
                    }
                }
            }
        } else {
            isVerticalGlideRef.set(false)
            if (prevKey != null) {
                (rowGrids[prevKey] as? InteropHorizontalGridView)?.let { prevGrid ->
                    prevGrid.prepareForRowChange()
                    prevGrid.floatingRingView?.hideRing(true)
                }
            }
            overlayHost?.deactivateGlideFocusAndHideRing()
        }

        if (targetGrid != null && targetGrid.isAttachedToWindow) {
            targetGrid.setSelectedPosition(targetCardIndex)
            targetGrid.requestFocus()
            if (!canUseFixedStepRing) {
                val modernGrid = targetGrid as? InteropHorizontalGridView
                modernGrid?.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
                targetGrid.post {
                    modernGrid?.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
                }
            }
        } else {
            localPendingFocusRowKey = targetRow.key
            localPendingFocusIndex = targetCardIndex
            localPendingFocusNonce++
        }

        scrollJob?.cancel()
        scrollJob = coroutineScope.launch {
            try {
                if (!animationsEnabled) {
                    verticalRowListState.scrollToItem(targetIdx, 0)
                } else {
                    var resolvedInfo = verticalRowListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIdx }
                    if (resolvedInfo == null && sign < 0 && verticalRowListState.canScrollBackward) {
                        verticalRowListState.scrollBy(-1f)
                        resolvedInfo = verticalRowListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIdx }
                    }
                    if (resolvedInfo != null) {
                        verticalRowListState.animateScrollBy(
                            value = resolvedInfo.offset.toFloat(),
                            animationSpec = tween(
                                durationMillis = VERTICAL_STEP_DURATION_MS,
                                easing = FastOutSlowInEasing
                            )
                        )
                    } else {
                        verticalRowListState.animateScrollToItem(targetIdx, 0)
                    }
                }
            } finally {
                if (canUseFixedStepRing && overlayGenerationRef.get() == stepGen) {
                    handoffOverlayToGrid(targetRow.key, targetCardIndex, stepGen)
                }
            }
        }
        return true
    }
    LaunchedEffect(verticalRowListState) {
        val prefetchAheadForLazy = 2
        snapshotFlow {
            val info = verticalRowListState.layoutInfo
            val firstVisible = info.visibleItemsInfo.firstOrNull()?.index ?: -1
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            firstVisible to lastVisible
        }.collectLatest { (firstVisible, lastVisible) ->
            if (lastVisible < 0) return@collectLatest
            // Debounce: restarts on every new emission during rapid scroll.
            // Only fires when visible indices stabilize for 240ms.
            delay(240)
            val rows = latestCarouselRowsForLazy.value
            for (idx in firstVisible.coerceAtLeast(0)..(lastVisible + prefetchAheadForLazy)) {
                val row = rows.list.getOrNull(idx) ?: continue
                if (row.isLoading && row.items.list.firstOrNull()?.imageUrl.isPlaceholder()) {
                    val legacyKey = catalogRowLegacyKey(
                        row.addonId ?: continue,
                        row.apiType ?: continue,
                        row.catalogId ?: continue
                    )
                    latestOnRequestLazyCatalogLoad.value(legacyKey)
                }
            }
        }
    }

    val focusRestorerRequester = remember {
        {
            // Prefer the navigation target: activeRowKey state can still point at the row the
            // user just left while focus is already moving to the next one.
            effectiveActiveRowKey()?.let { rowFocusRequesters[it] } ?: FocusRequester.Default
        }
    }

    var wasSidebarExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(isSidebarExpanded) {
        if (wasSidebarExpanded && !isSidebarExpanded) {
            val targetKey = effectiveActiveRowKey()
            if (targetKey != null) {
                val grid = rowGrids[targetKey]
                val savedIndex = (focusedItemByRowMap[targetKey] ?: 0)
                if (grid != null && grid.isAttachedToWindow) {
                    grid.setSelectedPosition(savedIndex)
                    grid.requestFocus()
                    (grid as? InteropHorizontalGridView)?.ensureChildFocusAndRing(targetPos = savedIndex, animated = false)
                } else {
                    localPendingFocusRowKey = targetKey
                    localPendingFocusIndex = savedIndex
                    localPendingFocusNonce++
                }
            }
        }
        wasSidebarExpanded = isSidebarExpanded
    }

    CompositionLocalProvider(
        LocalBringIntoViewSpec provides verticalRowBringIntoViewSpec,
        LocalFastScrollActive provides isFastScrolling,
        LocalVerticalRowsScrolling provides isVerticalRowsScrollingState
    ) {
        val latestOnVerticalHoldScroll = rememberUpdatedState(::startOrExtendVerticalHoldScroll)
        val latestOnVerticalHoldRelease = rememberUpdatedState { endVerticalHoldScroll(immediate = false) }
        val latestOnVerticalSingleStep = rememberUpdatedState(::stepVerticalRow)
        val latestOnHorizontalInterrupt = rememberUpdatedState(::finishOverlayRingToCurrentGrid)
        val latestOnEscapeKey = rememberUpdatedState { keyEvent: AndroidKeyEvent ->
            val activeKey = effectiveActiveRowKey()
            val hadHold = verticalHoldDirectionRef.get() != 0
            if (hadHold) {
                endVerticalHoldScroll(immediate = true)
            } else {
                scrollJob?.cancel()
                latestOnHorizontalInterrupt.value()
            }
            val landedKey = effectiveActiveRowKey() ?: activeKey
            val landedGrid = landedKey?.let { rowGrids[it] }
            if (landedGrid != null && landedGrid.isAttachedToWindow) {
                val cloneEvent = AndroidKeyEvent(keyEvent)
                landedGrid.post {
                    if (landedGrid.isAttachedToWindow) {
                        landedGrid.dispatchKeyEvent(cloneEvent)
                    }
                }
            }
            true
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(rowsViewportHeight)
                .padding(bottom = catalogBottomPadding)
        ) {
            LazyColumn(
                state = verticalRowListState,
                modifier = Modifier
                    .fillMaxSize()
                    .recompositionHighlighter()
                    .clipToBounds()
                    .graphicsLayer { alpha = trailerContentAlpha() }
                    .focusRequester(contentFocusRequester)
                    .focusRestorer { focusRestorerRequester() }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp) {
                            if ((event.key == Key.DirectionUp || event.key == Key.DirectionDown) &&
                                verticalHoldDirectionRef.get() != 0
                            ) {
                                endVerticalHoldScroll()
                                return@onPreviewKeyEvent true
                            }
                            return@onPreviewKeyEvent false
                        }
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Back -> {
                                    onUserNavigated()
                                }
                            }
                            if ((event.key == Key.DirectionDown || event.key == Key.DirectionUp) &&
                                event.nativeKeyEvent.repeatCount > 0
                            ) {
                                val sign = if (event.key == Key.DirectionDown) 1 else -1
                                return@onPreviewKeyEvent startOrExtendVerticalHoldScroll(sign)
                            }
                        }
                        val firstRowKey = carouselRows.list.firstOrNull()?.key
                        val lastRowKey = carouselRows.list.lastOrNull()?.key
                        val currentRowKey = effectiveActiveRowKey()
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionUp &&
                            currentRowKey == firstRowKey
                        ) {
                            if (requestUpdateBannerFocus?.invoke() == true) {
                                return@onPreviewKeyEvent true
                            }
                        }
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionUp &&
                            effectiveExpandEnabled &&
                            expandedCatalogFocusKey.value != null &&
                            currentRowKey == firstRowKey
                        ) return@onPreviewKeyEvent true
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionDown &&
                            effectiveExpandEnabled &&
                            expandedCatalogFocusKey.value != null &&
                            currentRowKey == lastRowKey
                        ) return@onPreviewKeyEvent true
                        val blockKey = if (layoutDirection == LayoutDirection.Rtl)
                            Key.DirectionRight else Key.DirectionLeft
                        if (blockLeftOnFirstExpandedItem &&
                            event.type == KeyEventType.KeyDown &&
                            event.key == blockKey &&
                            effectiveExpandEnabled &&
                            expandedCatalogFocusKey.value != null &&
                            activeItemIndex.value == 0
                        ) return@onPreviewKeyEvent true
                        false
                    },
                contentPadding = PaddingValues(bottom = rowsViewportHeight),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            itemsIndexed(
                items = carouselRows.list,
                key = { _, row -> row.key },
                contentType = { _, row ->
                    if (isContinueWatchingRowKey(row.key)) "modern_cw_row" else "modern_catalog_row"
                }
            ) { _, row ->
                val onNavigateLeftOnFirstItem = remember(openSidebar, row.key) {
                    {
                        onUserNavigated()
                        navTargetRowKey.set(row.key)
                        currentRowKeyTracker.set(row.key)
                        if (openSidebar != null) {
                            openSidebar()
                            true
                        } else {
                            false
                        }
                    }
                }

                val onNavigateBack = remember(animationsEnabled) {
                    {
                        onUserNavigated()
                        if (isSidebarExpandedState.value) {
                            false
                        } else {
                            val currentActiveRowKey = effectiveActiveRowKey()
                            val currentRowFocusedItem = currentActiveRowKey?.let { focusedItemByRowMap[it] } ?: 0
                            if (currentRowFocusedItem > 0 && currentActiveRowKey != null) {
                                focusedItemByRowMap[currentActiveRowKey] = 0
                                latestOnActiveItemIndexChange.value(0)
                                val currentGrid = rowGrids[currentActiveRowKey]
                                if (currentGrid != null) {
                                    currentGrid.post {
                                        // Bail out if the user already moved to another row.
                                        if (currentGrid.isAttachedToWindow &&
                                            navTargetRowKey.get() == currentActiveRowKey
                                        ) {
                                            if (animationsEnabled) {
                                                currentGrid.setSelectedPositionSmooth(0)
                                            } else {
                                                currentGrid.setSelectedPosition(0)
                                            }
                                            currentGrid.requestFocus()
                                        }
                                    }
                                }
                                true
                            } else if (targetRowIndexTracker > 0) {
                                targetRowIndexTracker = 0
                                val firstRow = latestCarouselRowsForLazy.value.list.firstOrNull()
                                if (firstRow != null) {
                                    val targetCardIndex = (focusedItemByRowMap[firstRow.key] ?: 0)
                                        .coerceIn(0, (firstRow.items.list.size - 1).coerceAtLeast(0))
                                    val currentGrid = rowGrids[currentActiveRowKey] as? InteropHorizontalGridView
                                    currentGrid?.prepareForRowChange()
                                    currentGrid?.floatingRingView?.hideRing(false)
                                    navTargetRowKey.set(firstRow.key)
                                    currentRowKeyTracker.set(firstRow.key)
                                    latestOnActiveRowKeyChange.value(firstRow.key)
                                    latestOnActiveItemIndexChange.value(targetCardIndex)
                                    scrollJob?.cancel()
                                    scrollJob = coroutineScope.launch {
                                        if (animationsEnabled) {
                                            verticalRowListState.animateScrollToItem(0)
                                        } else {
                                            verticalRowListState.scrollToItem(0)
                                        }
                                    }
                                    val firstGrid = rowGrids[firstRow.key]
                                    if (firstGrid != null) {
                                        firstGrid.post {
                                            if (firstGrid.isAttachedToWindow &&
                                                navTargetRowKey.get() == firstRow.key
                                            ) {
                                                if (animationsEnabled) {
                                                    firstGrid.setSelectedPositionSmooth(targetCardIndex)
                                                } else {
                                                    firstGrid.setSelectedPosition(targetCardIndex)
                                                }
                                                firstGrid.requestFocus()
                                                val modernGrid = firstGrid as? InteropHorizontalGridView
                                                modernGrid?.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
                                                firstGrid.post {
                                                    modernGrid?.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
                                                }
                                            }
                                        }
                                    } else {
                                        localPendingFocusRowKey = firstRow.key
                                        localPendingFocusIndex = targetCardIndex
                                        localPendingFocusNonce++
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                    }
                }

                val onNavigateDown = remember {
                    { latestOnVerticalSingleStep.value(1) }
                }

                val onNavigateUp = remember {
                    { latestOnVerticalSingleStep.value(-1) }
                }
                val stableOnContinueWatchingOptions = remember(onContinueWatchingOptions) {
                    { item: ContinueWatchingItem -> onContinueWatchingOptions(item) }
                }
                val stableOnRowItemFocused = remember {
                    { rowKey: String, index: Int, isContinueWatchingRow: Boolean ->
                        // Only the row we deliberately handed focus to may claim it. A focus
                        // event from any other row is stray (deferred ring/focus work from a
                        // row the user already left), so restore the intended row instead of
                        // following it. navTargetRowKey is written synchronously from the key
                        // handlers, so this no longer depends on a timing window.
                        val rows = latestCarouselRowsForLazy.value.list
                        fun knownKey(key: String?): String? =
                            key?.takeIf { candidate -> rows.any { it.key == candidate } }
                        val intendedKey = knownKey(navTargetRowKey.get())
                            ?: knownKey(currentRowKeyTracker.get())
                            ?: knownKey(activeRowKey.value)
                        val now = System.currentTimeMillis()

                        if (intendedKey != null && intendedKey != rowKey) {
                            val activeGrid = rowGrids[intendedKey]
                            if (activeGrid != null && activeGrid.isAttachedToWindow) {
                                activeGrid.requestFocus()
                                (activeGrid as? InteropHorizontalGridView)?.ensureChildFocusAndRing(animated = false)
                            }
                        } else {
                            navTargetRowKey.set(rowKey)
                            currentRowKeyTracker.set(rowKey)
                            val itemChanged = activeItemIndex.value != index
                            // activeRowKey normally already points here because the key handler
                            // published it synchronously; this fallback keeps hero/focus state
                            // correct for focus that arrived without going through a nav handler.
                            val rowStateChanged = activeRowKey.value != rowKey

                            if (rowStateChanged || itemChanged) {
                                val timeSinceLastHeroNav = now - lastHeroNavigationAtMs.value
                                onHeroFocusSettleDelayChange(
                                    if (lastHeroNavigationAtMs.value != 0L &&
                                        timeSinceLastHeroNav in 1 until 130L // MODERN_HERO_RAPID_NAV_THRESHOLD_MS
                                    ) 400L // MODERN_HERO_RAPID_NAV_SETTLE_MS
                                    else 450L // MODERN_HERO_FOCUS_DEBOUNCE_MS
                                )
                                onLastHeroNavigationAtMsChange(now)
                                onActiveRowKeyChange(rowKey)
                                onActiveItemIndexChange(index)
                            }

                            // Always keep the focusedItemByRow map in sync for ALL rows
                            if (focusedItemByRowMap[rowKey] != index) {
                                focusedItemByRowMap[rowKey] = index
                            }

                            if (isContinueWatchingRow) {
                                if (lastFocusedContinueWatchingIndex.value != index) {
                                    onLastFocusedContinueWatchingIndexChange(index)
                                }
                                if (focusedCatalogSelection.value != null) {
                                    onFocusedCatalogSelectionChange(null)
                                }
                            }
                            onRowItemFocusedInternal(rowKey, index, isContinueWatchingRow)
                        }
                    }
                }
                val isActiveRowLambda = remember(row.key) {
                    // Uses the same source of truth as the focus handoff, so the row we just
                    // left stops being treated as active the moment the key handler decides
                    // to move.
                    { row.key == effectiveActiveRowKey() }
                }
                val stableOnCatalogSelectionFocused = remember {
                    { selection: FocusedCatalogSelection ->
                        val isCollectionFolder = selection.payload is ModernPayload.CollectionFolder
                        if (focusedCatalogSelection.value != selection || isCollectionFolder) {
                            onFocusedCatalogSelectionChange(selection)
                            if (isCollectionFolder) {
                                onFocusedHeroMediaNonceChange(focusedHeroMediaNonce.value + 1)
                            }
                        }
                    }
                }
                ModernRowSection(
                    row = row,
                    isActiveRow = isActiveRowLambda,
                    rowFocusRequester = rowFocusRequesters.getOrPut(row.key) { FocusRequester() },
                    rowTitleBottom = 4.dp, // rowTitleBottom sát poster
                    focusedItemByRow = focusedItemByRow,
                    loadMoreRequestedTotals = loadMoreRequestedTotals,
                    pendingRowFocusKey = pendingRowFocusKey,
                    pendingRowFocusIndex = pendingRowFocusIndex,
                    pendingRowFocusNonce = pendingRowFocusNonce,
                    onRowItemFocused = stableOnRowItemFocused,
                    useLandscapePosters = useLandscapePosters,
                    showLabels = showLabels,
                    posterCardCornerRadius = posterCardCornerRadius,
                    portraitCatalogCardWidth = portraitCatalogCardWidth,
                    portraitCatalogCardHeight = portraitCatalogCardHeight,
                    landscapeCatalogCardWidth = landscapeCatalogCardWidth,
                    landscapeCatalogCardHeight = landscapeCatalogCardHeight,
                    continueWatchingCardWidth = continueWatchingCardWidth,
                    continueWatchingCardHeight = continueWatchingCardHeight,
                    continueWatchingCornerRadius = continueWatchingCornerRadius,
                    onContinueWatchingClick = onContinueWatchingClick,
                    onContinueWatchingOptions = stableOnContinueWatchingOptions,
                    onCatalogItemLongPress = onCatalogItemLongPress,
                    onItemFocus = onItemFocus,
                    onCatalogSelectionFocused = stableOnCatalogSelectionFocused,
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToFolderDetail = onNavigateToFolderDetail,
                    onLoadMoreCatalog = onLoadMoreCatalog,
                    onNavigateDown = onNavigateDown,
                    onNavigateUp = onNavigateUp,
                    onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem,
                    onNavigateBack = onNavigateBack,
                    onVerticalHoldScroll = latestOnVerticalHoldScroll.value,
                    onVerticalHoldRelease = latestOnVerticalHoldRelease.value,
                    isVerticalGlideActive = isVerticalGlideActiveLambda,
                    onHorizontalInterrupt = latestOnHorizontalInterrupt.value,
                    rowGrids = rowGrids,
                    sharedRecycledViewPool = sharedRecycledViewPool,
                    isPendingFocus = localPendingFocusRowKey == row.key,
                    pendingFocusIndex = localPendingFocusIndex,
                    pendingFocusNonce = localPendingFocusNonce,
                    onClearPendingFocus = stableClearPendingFocus
                )
            }
        }
            AndroidView(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = false
                        alpha = trailerContentAlpha()
                    },
                factory = { ctx ->
                    VerticalRingOverlayHost(ctx).also { host ->
                        host.preMeasureCardSize(catalogCardWidthPx, catalogCardHeightPx)
                        host.isGlideHolding = { verticalHoldDirectionRef.get() != 0 }
                        host.onVerticalRepeat = { sign -> latestOnVerticalHoldScroll.value(sign) }
                        host.onVerticalRelease = { latestOnVerticalHoldRelease.value() }
                        host.onVerticalSingleStep = { sign -> latestOnVerticalSingleStep.value(sign) }
                        host.onEscapeKey = { keyEvent -> latestOnEscapeKey.value(keyEvent) }
                        verticalOverlayHostRef.set(host)
                    }
                },
                update = { host ->
                    verticalOverlayHostRef.set(host)
                    host.preMeasureCardSize(catalogCardWidthPx, catalogCardHeightPx)
                }
            )
        }
    }
}
