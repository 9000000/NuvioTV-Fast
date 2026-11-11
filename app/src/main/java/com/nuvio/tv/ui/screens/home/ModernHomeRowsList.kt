package com.nuvio.tv.ui.screens.home

import androidx.recyclerview.widget.RecyclerView

import com.nuvio.tv.domain.model.catalogRowLegacyKey
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.LocalRequestUpdateBannerFocus

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
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
import com.nuvio.tv.ui.components.rememberPlaceholderShimmerOffsetState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

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
    rowListStates: StableRef<MutableMap<String, LazyListState>>,
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
    val rowListStatesMap = rowListStates.value
    val loadMoreRequestedTotalsMap = loadMoreRequestedTotals.value

    val latestOnActiveRowKeyChange = rememberUpdatedState(onActiveRowKeyChange)
    val latestOnActiveItemIndexChange = rememberUpdatedState(onActiveItemIndexChange)

    val rowFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val stableItemFocusRequestersByRow = remember { mutableMapOf<String, StableRef<MutableMap<Int, FocusRequester>>>() }
    val rowGrids = remember { mutableMapOf<String, androidx.leanback.widget.HorizontalGridView>() }
    val sharedRecycledViewPool = remember {
        RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(0, 30)
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var localPendingFocusRowKey by remember { mutableStateOf<String?>(null) }
    var localPendingFocusIndex by remember { mutableIntStateOf(0) }
    var localPendingFocusNonce by remember { mutableIntStateOf(0) }
    var targetRowIndexTracker by remember { mutableIntStateOf(0) }
    // Single source of truth for "which row should own focus", updated synchronously on every
    // deliberate row change (activeRowKey is Compose state and lags one frame behind).
    val navTargetRowKey = remember { java.util.concurrent.atomic.AtomicReference<String?>(null) }
    // Last row that actually reported focus. Kept next to navTargetRowKey so the focus
    // callbacks never have to wait a frame for activeRowKey (Compose state) to catch up.
    val currentRowKeyTracker = remember { java.util.concurrent.atomic.AtomicReference<String?>(null) }
    var lastNavTimeMs by remember { mutableLongStateOf(0L) }

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
        val prefetchItemsPerRow = 1
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
                            val url = item.imageUrl ?: continue
                            val metrics = item.catalogCardRequestMetrics(
                                useLandscapePosters = useLandscapePosters,
                                portraitCardWidth = portraitCatalogCardWidth,
                                portraitCardHeight = portraitCatalogCardHeight,
                                landscapeCardWidth = landscapeCatalogCardWidth,
                                landscapeCardHeight = landscapeCatalogCardHeight,
                                expandEnabled = effectiveExpandEnabled
                            )
                            val wPx = with(density) { metrics.width.roundToPx() }
                            val hPx = with(density) { metrics.height.roundToPx() }
                            if (wPx <= 0 || hPx <= 0) continue
                            val cacheKey = "${url}_${wPx}x${hPx}_v0"
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
    // The row that currently owns (or is about to own) focus. Falls back to activeRowKey when the
    // nav target was removed from the list.
    val effectiveActiveRowKey: () -> String? = remember {
        {
            navTargetRowKey.get()
                ?.takeIf { key -> latestCarouselRowsForLazy.value.list.any { it.key == key } }
                ?: activeRowKey.value
        }
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

    val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current

    // Only run the shared shimmer while a row on screen actually draws it: rememberInfiniteTransition
    // keeps waking the Compose frame clock on every frame for as long as it is composed, even when
    // nothing reads its value. Rows below the fold stay placeholders until they are scrolled to, so
    // the check has to be on the visible rows, not on the whole list. Keyed on the list state for
    // the same reason isVerticalRowsScrollingState is in ModernHomeContent: rememberLazyListState
    // is saveable-backed and can hand back a new instance, and a derived state still holding the
    // old one would read a layout that has stopped updating.
    val needsPlaceholderShimmer by remember(verticalRowListState) {
        derivedStateOf {
            val rows = latestCarouselRowsForLazy.value.list
            verticalRowListState.layoutInfo.visibleItemsInfo.any { visibleRow ->
                rows.getOrNull(visibleRow.index)?.showsPlaceholderShimmer() == true
            }
        }
    }
    val sharedPlaceholderShimmerOffsetState = if (needsPlaceholderShimmer) {
        rememberPlaceholderShimmerOffsetState(label = "sharedRowShimmer")
    } else {
        null
    }

    CompositionLocalProvider(
        LocalBringIntoViewSpec provides verticalRowBringIntoViewSpec,
        LocalFastScrollActive provides isFastScrolling,
        LocalVerticalRowsScrolling provides isVerticalRowsScrollingState
    ) {
        val openSidebar = com.nuvio.tv.LocalOpenSidebar.current
        val requestUpdateBannerFocus = LocalRequestUpdateBannerFocus.current

        LazyColumn(
            state = verticalRowListState,
            modifier = modifier
                .fillMaxWidth()
                .recompositionHighlighter()
                .height(rowsViewportHeight)
                .padding(bottom = catalogBottomPadding)
                .clipToBounds()
                .graphicsLayer { alpha = trailerContentAlpha() }
                .focusRequester(contentFocusRequester)
                .focusRestorer { focusRestorerRequester() }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight, Key.Back -> {
                                onUserNavigated()
                            }
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
                contentType = { _, row -> row.apiType ?: "modern_home_row" }
            ) { rowIndex, row ->
                val onNavigateLeftOnFirstItem = remember(openSidebar) {
                    {
                        onUserNavigated()
                        if (openSidebar != null) {
                            openSidebar()
                            true
                        } else {
                            false
                        }
                    }
                }

                val onNavigateBack = remember(carouselRows.list, animationsEnabled) {
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
                                val firstRow = carouselRows.list.firstOrNull()
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

                val onNavigateDown = remember(rowIndex, carouselRows.list, animationsEnabled) {
                    fun navigateDown(): Boolean {
                        onUserNavigated()
                        val now = System.currentTimeMillis()
                        // Giới hạn tần suất lặp phím khi bấm giữ im (tối thiểu 85ms giữa các hàng)
                        if (now - lastNavTimeMs < 85L) return true
                        val isRapid = (now - lastNavTimeMs) < 180L
                        lastNavTimeMs = now
                        val shouldAnimate = animationsEnabled && !isRapid

                        val nextIdx = rowIndex + 1
                        if (nextIdx >= carouselRows.list.size) return false
                        val nextRow = carouselRows.list[nextIdx]
                        if (nextRow.items.list.isEmpty()) {
                            // Catalog đang rỗng/tải: chặn focus rơi tự do xuống dưới và
                            // kích hoạt tải ngay bằng đúng legacy key mà ViewModel chờ.
                            val addonId = nextRow.addonId
                            val apiType = nextRow.apiType
                            val catalogId = nextRow.catalogId
                            if (addonId != null && apiType != null && catalogId != null) {
                                latestOnRequestLazyCatalogLoad.value(
                                    catalogRowLegacyKey(addonId, apiType, catalogId)
                                )
                            }
                            return true
                        }

                        val currentGrid = rowGrids[row.key] as? InteropHorizontalGridView
                        currentGrid?.prepareForRowChange()
                        currentGrid?.floatingRingView?.hideRing(false)
                        targetRowIndexTracker = nextIdx
                        navTargetRowKey.set(nextRow.key)
                        currentRowKeyTracker.set(nextRow.key)
                        val targetCardIndex = (focusedItemByRowMap[nextRow.key] ?: 0)
                            .coerceIn(0, (nextRow.items.list.size - 1).coerceAtLeast(0))

                        latestOnActiveRowKeyChange.value(nextRow.key)
                        latestOnActiveItemIndexChange.value(targetCardIndex)

                        val nextGrid = rowGrids[nextRow.key]
                        if (nextGrid != null && nextGrid.isAttachedToWindow) {
                            nextGrid.setSelectedPosition(targetCardIndex)
                            nextGrid.requestFocus()
                            val modernGrid = nextGrid as? InteropHorizontalGridView
                            modernGrid?.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
                            nextGrid.post {
                                modernGrid?.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
                            }
                        } else {
                            localPendingFocusRowKey = nextRow.key
                            localPendingFocusIndex = targetCardIndex
                            localPendingFocusNonce++
                        }

                        scrollJob?.cancel()
                        scrollJob = coroutineScope.launch {
                            if (!shouldAnimate) {
                                verticalRowListState.scrollToItem(nextIdx)
                            } else {
                                verticalRowListState.animateScrollToItem(nextIdx)
                            }
                        }
                        return true
                    }
                    ::navigateDown
                }

                val onNavigateUp = remember(rowIndex, carouselRows.list, animationsEnabled, requestUpdateBannerFocus) {
                    fun navigateUp(): Boolean {
                        onUserNavigated()
                        val now = System.currentTimeMillis()
                        // Giới hạn tần suất lặp phím khi bấm giữ im (tối thiểu 85ms giữa các hàng)
                        if (now - lastNavTimeMs < 85L) return true
                        val isRapid = (now - lastNavTimeMs) < 180L
                        lastNavTimeMs = now
                        val shouldAnimate = animationsEnabled && !isRapid

                        val prevIdx = rowIndex - 1
                        if (prevIdx < 0) {
                            return requestUpdateBannerFocus?.invoke() == true
                        }
                        val prevRow = carouselRows.list[prevIdx]
                        if (prevRow.items.list.isEmpty()) {
                            // Catalog đang rỗng/tải: chặn focus rơi tự do lên trên và
                            // kích hoạt tải ngay bằng đúng legacy key mà ViewModel chờ.
                            val addonId = prevRow.addonId
                            val apiType = prevRow.apiType
                            val catalogId = prevRow.catalogId
                            if (addonId != null && apiType != null && catalogId != null) {
                                latestOnRequestLazyCatalogLoad.value(
                                    catalogRowLegacyKey(addonId, apiType, catalogId)
                                )
                            }
                            return true
                        }

                        val currentGrid = rowGrids[row.key] as? InteropHorizontalGridView
                        currentGrid?.prepareForRowChange()
                        currentGrid?.floatingRingView?.hideRing(false)
                        targetRowIndexTracker = prevIdx
                        navTargetRowKey.set(prevRow.key)
                        currentRowKeyTracker.set(prevRow.key)
                        val targetCardIndex = (focusedItemByRowMap[prevRow.key] ?: 0)
                            .coerceIn(0, (prevRow.items.list.size - 1).coerceAtLeast(0))

                        latestOnActiveRowKeyChange.value(prevRow.key)
                        latestOnActiveItemIndexChange.value(targetCardIndex)

                        val prevGrid = rowGrids[prevRow.key]
                        if (prevGrid != null && prevGrid.isAttachedToWindow) {
                            prevGrid.setSelectedPosition(targetCardIndex)
                            prevGrid.requestFocus()
                            val modernGrid = prevGrid as? InteropHorizontalGridView
                            modernGrid?.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
                            prevGrid.post {
                                modernGrid?.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
                            }
                        } else {
                            localPendingFocusRowKey = prevRow.key
                            localPendingFocusIndex = targetCardIndex
                            localPendingFocusNonce++
                        }

                        scrollJob?.cancel()
                        scrollJob = coroutineScope.launch {
                            if (!shouldAnimate) {
                                verticalRowListState.scrollToItem(prevIdx)
                            } else {
                                verticalRowListState.animateScrollToItem(prevIdx)
                            }
                        }
                        return true
                    }
                    ::navigateUp
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
                    // to move. Otherwise deferred focus work in the old row can win the race.
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
                    defaultBringIntoViewSpec = defaultBringIntoViewSpec,
                    focusStateCatalogRowScrollIndex = focusState.catalogRowScrollStates[row.key] ?: 0,
                    focusStateCatalogRowScrollAnchor = focusState.catalogRowScrollAnchors[row.key],
                    focusedItemByRow = focusedItemByRow,
                    rowListStates = rowListStates,
                    loadMoreRequestedTotals = loadMoreRequestedTotals,
                    pendingRowFocusKey = pendingRowFocusKey,
                    pendingRowFocusIndex = pendingRowFocusIndex,
                    pendingRowFocusNonce = pendingRowFocusNonce,
                    onPendingRowFocusCleared = onPendingRowFocusCleared,
                    onRowItemFocused = stableOnRowItemFocused,
                    onNavigateDown = onNavigateDown,
                    onNavigateUp = onNavigateUp,
                    onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem,
                    onNavigateBack = onNavigateBack,
                    rowGrids = rowGrids,
                    sharedRecycledViewPool = sharedRecycledViewPool,
                    isPendingFocus = localPendingFocusRowKey == row.key,
                    pendingFocusIndex = localPendingFocusIndex,
                    pendingFocusNonce = localPendingFocusNonce,
                    onClearPendingFocus = { localPendingFocusRowKey = null },
                    useLandscapePosters = useLandscapePosters,
                    alwaysShowLandscapeClearlogo = alwaysShowLandscapeClearlogo,
                    showLabels = showLabels,
                    posterCardCornerRadius = posterCardCornerRadius,
                    focusedPosterBackdropTrailerMuted = focusedPosterBackdropTrailerMuted,
                    effectiveExpandEnabled = effectiveExpandEnabled,
                    effectiveAutoplayEnabled = effectiveAutoplayEnabled,
                    trailerPlaybackTarget = trailerPlaybackTarget,
                    expandedCatalogFocusKey = expandedCatalogFocusKey,
                    expandedTrailerPreviewUrl = expandedTrailerPreviewUrl,
                    expandedTrailerPreviewAudioUrl = expandedTrailerPreviewAudioUrl,
                    portraitCatalogCardWidth = portraitCatalogCardWidth,
                    portraitCatalogCardHeight = portraitCatalogCardHeight,
                    landscapeCatalogCardWidth = landscapeCatalogCardWidth,
                    landscapeCatalogCardHeight = landscapeCatalogCardHeight,
                    continueWatchingCardWidth = continueWatchingCardWidth,
                    continueWatchingCardHeight = continueWatchingCardHeight,
                    blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                    useEpisodeThumbnails = useEpisodeThumbnails,
                    continueWatchingCardStyle = continueWatchingCardStyle,
                    continueWatchingCornerRadius = continueWatchingCornerRadius,
                    onContinueWatchingClick = onContinueWatchingClick,
                    onContinueWatchingOptions = stableOnContinueWatchingOptions,
                    isCatalogItemWatched = isCatalogItemWatched,
                    onCatalogItemLongPress = onCatalogItemLongPress,
                    onItemFocus = onItemFocus,
                    onPreloadAdjacentItem = onPreloadAdjacentItem,
                    enrichedPreviews = enrichedPreviews,
                    onCatalogSelectionFocused = stableOnCatalogSelectionFocused,
                    onNavigateToDetail = onNavigateToDetail,
                    onNavigateToFolderDetail = onNavigateToFolderDetail,
                    onLoadMoreCatalog = onLoadMoreCatalog,
                    onBackdropInteraction = onBackdropInteraction,
                    onExpandedCatalogFocusKeyChange = onExpandedCatalogFocusKeyChange,
                    sharedPlaceholderShimmerOffsetState = sharedPlaceholderShimmerOffsetState,
                    isVerticalRowsScrollingState = isVerticalRowsScrollingState,
                    itemFocusRequesters = stableItemFocusRequestersByRow.getOrPut(row.key) {
                        StableRef(mutableMapOf())
                    }
                )
            }
        }
    }
}
