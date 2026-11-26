package com.nuvio.tv.ui.screens.home

import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.nuvio.tv.ui.leanback.core.LeanbackArtworkLoader
import com.nuvio.tv.ui.leanback.core.LeanbackCardExpandSpec
import com.nuvio.tv.ui.leanback.core.rememberLeanbackExpandedTrailerHost
import com.nuvio.tv.ui.leanback.core.rememberLeanbackThemeStyle
import com.nuvio.tv.ui.leanback.pool.LeanbackPoolManager

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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
import com.nuvio.tv.core.image.HomeImageLoadingProfile
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

private const val VERTICAL_HOLD_START_VELOCITY_DP_PER_SEC = 520f
private const val VERTICAL_HOLD_MAX_VELOCITY_DP_PER_SEC = 960f
private const val VERTICAL_HOLD_RAMP_SEC = 0.32f
private const val VERTICAL_HOLD_IDLE_TIMEOUT_MS = 420L
private const val VERTICAL_HOLD_GRACE_PERIOD_MS = 200L
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

internal fun findNearestNonEmptyRowIndex(
    rows: List<HeroCarouselRow>,
    preferredIdx: Int,
    visibleItems: List<LazyListItemInfo> = emptyList()
): Int {
    if (rows.isEmpty()) return -1
    val clamped = preferredIdx.coerceIn(0, rows.lastIndex)
    var bestVisibleIdx = -1
    var bestVisibleDist = Int.MAX_VALUE
    for (i in visibleItems.indices) {
        val idx = visibleItems[i].index
        if (idx in rows.indices && rows[idx].items.list.isNotEmpty()) {
            val dist = kotlin.math.abs(idx - clamped)
            if (dist < bestVisibleDist || (dist == bestVisibleDist && idx >= clamped)) {
                bestVisibleDist = dist
                bestVisibleIdx = idx
            }
        }
    }
    if (bestVisibleIdx >= 0) return bestVisibleIdx
    for (offset in 0..rows.size) {
        val fwd = clamped + offset
        if (fwd in rows.indices && rows[fwd].items.list.isNotEmpty()) return fwd
        if (offset > 0) {
            val bwd = clamped - offset
            if (bwd in rows.indices && rows[bwd].items.list.isNotEmpty()) return bwd
        }
    }
    return -1
}

private const val PREFETCH_DEDUP_MAX_SIZE = 768
private const val PREFETCH_DEDUP_TRIM_BATCH = 192

private data class VerticalPrefetchSnapshot(
    val lastVisibleRowIndex: Int,
    val signature: String,
    val isScrolling: Boolean
)

internal fun computeLazyCatalogPrefetchWindow(
    firstVisible: Int,
    lastVisible: Int,
    aheadCount: Int,
    totalCount: Int
): IntRange {
    if (lastVisible < 0 || totalCount <= 0) return IntRange.EMPTY
    val start = firstVisible.coerceAtLeast(0)
    val end = (lastVisible + aheadCount.coerceAtLeast(0)).coerceAtMost(totalCount - 1)
    return if (start <= end) start..end else IntRange.EMPTY
}

internal fun computeVerticalImagePrefetchWindow(
    lastVisibleRowIndex: Int,
    aheadRows: Int,
    totalRows: Int
): IntRange {
    if (lastVisibleRowIndex < 0 || aheadRows <= 0 || totalRows <= 0) return IntRange.EMPTY
    val start = (lastVisibleRowIndex + 1).coerceAtLeast(0)
    val end = (lastVisibleRowIndex + aheadRows).coerceAtMost(totalRows - 1)
    return if (start <= end) start..end else IntRange.EMPTY
}

internal fun computeRowCardPrefetchIndices(
    focusedIndex: Int,
    itemCount: Int,
    prefetchCount: Int
): IntRange {
    if (itemCount <= 0 || prefetchCount <= 0) return IntRange.EMPTY
    val count = prefetchCount.coerceAtMost(itemCount)
    val clampedFocused = focusedIndex.coerceIn(0, itemCount - 1)
    val maxStart = (itemCount - count).coerceAtLeast(0)
    val start = clampedFocused.coerceAtMost(maxStart)
    val end = (start + count - 1).coerceAtMost(itemCount - 1)
    return start..end
}

internal inline fun encodeVerticalPrefetchWindowSignature(
    anchorRowIndex: Int,
    window: IntRange,
    crossinline rowKey: (Int) -> String,
    crossinline isLoading: (Int) -> Boolean,
    crossinline firstRealUrl: (Int) -> String?,
    crossinline itemCount: (Int) -> Int,
    crossinline focusedIndex: (String) -> Int
): String {
    if (window.isEmpty()) return "last=$anchorRowIndex|empty"
    return buildString {
        append("last=").append(anchorRowIndex)
        for (idx in window) {
            val key = rowKey(idx)
            val realUrl = firstRealUrl(idx)?.takeIf { it.isNotBlank() && !it.isPlaceholder() } ?: "placeholder"
            append('|')
            append(key).append(':')
            append(isLoading(idx)).append(':')
            append(realUrl).append(':')
            append(itemCount(idx)).append(':')
            append(focusedIndex(key))
        }
    }
}

internal fun buildVerticalImagePrefetchSignature(
    lastVisibleRowIndex: Int,
    rows: List<HeroCarouselRow>,
    aheadRows: Int,
    focusedIndexProvider: (String) -> Int
): String {
    val window = computeVerticalImagePrefetchWindow(
        lastVisibleRowIndex = lastVisibleRowIndex,
        aheadRows = aheadRows,
        totalRows = rows.size
    )
    return encodeVerticalPrefetchWindowSignature(
        anchorRowIndex = lastVisibleRowIndex,
        window = window,
        rowKey = { rows[it].key },
        isLoading = { rows[it].isLoading },
        firstRealUrl = { idx ->
            rows[idx].items.list.firstNotNullOfOrNull { item ->
                (item.imageUrl ?: item.heroPreview.poster ?: item.heroPreview.backdrop)
                    ?.takeIf { !it.isBlank() && !it.isPlaceholder() }
            }
        },
        itemCount = { rows[it].items.list.size },
        focusedIndex = focusedIndexProvider
    )
}

internal fun MutableSet<String>.tryMarkPrefetchedBounded(
    key: String,
    maxSize: Int = PREFETCH_DEDUP_MAX_SIZE,
    trimBatch: Int = PREFETCH_DEDUP_TRIM_BATCH
): Boolean {
    if (contains(key)) return false
    if (size >= maxSize) {
        val iter = iterator()
        repeat(trimBatch) {
            if (iter.hasNext()) {
                iter.next()
                iter.remove()
            }
        }
    }
    add(key)
    return true
}

/**
 * Leading+trailing throttle that never starves during continuous D-pad scrolling and
 * always flushes the latest value once emissions settle.
 */
internal suspend fun <T> Flow<T>.collectThrottledLeadingTrailing(
    leadingIntervalMs: Long,
    trailingDelayMs: Long,
    nowMs: () -> Long = { android.os.SystemClock.uptimeMillis() },
    shouldBypassThrottle: (T) -> Boolean = { false },
    action: suspend (T) -> Unit
) {
    coroutineScope {
        var lastTriggerMs = Long.MIN_VALUE
        var trailingJob: Job? = null
        var pendingValue: T? = null
        var hasPending = false

        try {
            collect { value ->
                val currentMs = nowMs()
                val elapsed = if (lastTriggerMs == Long.MIN_VALUE) leadingIntervalMs else currentMs - lastTriggerMs
                if (shouldBypassThrottle(value) || elapsed >= leadingIntervalMs) {
                    trailingJob?.cancel()
                    trailingJob = null
                    pendingValue = null
                    hasPending = false
                    lastTriggerMs = currentMs
                    action(value)
                } else {
                    pendingValue = value
                    hasPending = true
                    if (trailingJob?.isActive != true) {
                        trailingJob = launch {
                            delay(trailingDelayMs)
                            if (hasPending) {
                                val latest = pendingValue
                                pendingValue = null
                                hasPending = false
                                lastTriggerMs = nowMs()
                                @Suppress("UNCHECKED_CAST")
                                action(latest as T)
                            }
                        }
                    }
                }
            }
        } finally {
            trailingJob?.cancel()
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalTvMaterial3Api::class,
    ExperimentalComposeUiApi::class
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
    singleRowMode: Boolean = false,
    catalogBottomPadding: Dp,
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
    val themeStyle = rememberLeanbackThemeStyle(borderWidthDp = 2.0f)
    val effectiveCardExpandEnabled = effectiveExpandEnabled &&
        !LeanbackCardExpandSpec.shouldSuppressModernCardExpand(
            effectiveAutoplayEnabled = effectiveAutoplayEnabled,
            trailerPlaybackTarget = trailerPlaybackTarget
        )
    val playTrailerInExpandedCard = effectiveAutoplayEnabled &&
        trailerPlaybackTarget == FocusedPosterTrailerPlaybackTarget.EXPANDED_CARD
    // Only instantiate the shared trailer host when inline expanded-card trailer autoplay is enabled.
    val expandedTrailerHost = if (playTrailerInExpandedCard) rememberLeanbackExpandedTrailerHost() else null
    val latestOnExpandedCatalogFocusKeyChange = rememberUpdatedState(onExpandedCatalogFocusKeyChange)
    val stableOnExpandedTrailerEnded = remember {
        { latestOnExpandedCatalogFocusKeyChange.value(null) }
    }
    val isSidebarExpanded = LocalSidebarExpanded.current
    val isSidebarExpandedState = rememberUpdatedState(isSidebarExpanded)
    val focusedItemByRowMap = focusedItemByRow.value
    val loadMoreRequestedTotalsMap = loadMoreRequestedTotals.value

    val latestOnActiveRowKeyChange = rememberUpdatedState(onActiveRowKeyChange)
    val latestOnActiveItemIndexChange = rememberUpdatedState(onActiveItemIndexChange)

    val rowFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val attachedRowKeys = remember { mutableSetOf<String>() }
    val rowGrids = remember { mutableMapOf<String, androidx.leanback.widget.HorizontalGridView>() }
    // Isolate Modern Home's LeanbackCardViewHolder pool from LeanbackUniversalAdapter's shared pool
    // so ViewHolders of incompatible types never cross-pollinate on navigation transitions.
    val sharedRecycledViewPool = remember {
        LeanbackPoolManager.createModernHomePool()
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
    val isVerticalGlideRef = remember { AtomicBoolean(false) }
    val verticalHoldJobRef = remember { AtomicReference<Job?>(null) }
    val verticalHoldTimeoutRef = remember { AtomicReference<Job?>(null) }
    val verticalHoldDirectionRef = remember { AtomicInteger(0) }
    val lastVerticalSingleStepAtMsRef = remember { AtomicLong(0L) }
    val lastVerticalSingleStepDirRef = remember { AtomicInteger(0) }
    val overlayGenerationRef = remember { AtomicLong(0L) }
    val verticalOverlayHostRef = remember { AtomicReference<VerticalRingOverlayHost?>(null) }
    val latestOnFastScrollingChanged = rememberUpdatedState(onFastScrollingChanged)
    val isVerticalGlideActiveLambda = remember { { isVerticalGlideRef.get() } }
    val stableClearPendingFocus = remember {
        {
            localPendingFocusRowKey = null
            if (!isVerticalGlideRef.get() && verticalHoldDirectionRef.get() == 0) {
                verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            overlayGenerationRef.incrementAndGet()
            verticalHoldDirectionRef.set(0)
            verticalHoldJobRef.getAndSet(null)?.cancel()
            verticalHoldTimeoutRef.getAndSet(null)?.cancel()
            scrollJob?.cancel()
            isVerticalGlideRef.set(false)
            LeanbackArtworkLoader.isHoldScrolling = false
            latestOnFastScrollingChanged.value(false)
            verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
        }
    }

    LaunchedEffect(pendingRowFocusNonce.value) {
        val targetKey = pendingRowFocusKey.value ?: return@LaunchedEffect
        val targetIdx = pendingRowFocusIndex.value ?: 0
        if (isVerticalGlideRef.get() || verticalHoldDirectionRef.get() != 0) {
            onPendingRowFocusCleared()
            return@LaunchedEffect
        }
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
                    (targetGrid as? ModernInteropHorizontalGridView)?.prepareForIncomingFocus()
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
                    val modernGrid = targetGrid as? ModernInteropHorizontalGridView
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
                (grid as? ModernInteropHorizontalGridView)?.floatingRingView?.hideRing(false)
            }
        }
    }

    val density = LocalDensity.current
    val context = LocalContext.current
    val loadingProfile = remember(context) { HomeImageLoadingProfile.forContext(context) }
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
    fun isContinueWatchingRowKey(key: String?): Boolean =
        key == MODERN_CONTINUE_WATCHING_ROW_KEY || key == MODERN_UPCOMING_ROW_KEY

    fun rowCardDimensionsPx(rowKey: String?): Triple<Int, Int, Float> =
        if (isContinueWatchingRowKey(rowKey)) {
            Triple(cwCardWidthPx, cwCardHeightPx, cwCornerRadiusPx)
        } else {
            Triple(catalogCardWidthPx, catalogCardHeightPx, posterCornerRadiusPx)
        }

    val titleLineHeight = MaterialTheme.typography.titleMedium.lineHeight
    val defaultCardTopInRowPx = with(density) {
        val titleHeightDp = runCatching { titleLineHeight.toDp() }.getOrDefault(24.dp)
        (titleHeightDp + MODERN_ROW_TITLE_BOTTOM_SPACING + MODERN_ROW_CARD_VERTICAL_PADDING).toPx()
    }
    val cachedCardTopInRowRef = remember(defaultCardTopInRowPx) {
        AtomicReference(defaultCardTopInRowPx)
    }
    val verticalPrefetchImageLoader = context.imageLoader
    val latestCarouselRowsForImagePrefetch = rememberUpdatedState(carouselRows)
    val prefetchedVerticalItems = remember { LinkedHashSet<String>(PREFETCH_DEDUP_MAX_SIZE) }

    LaunchedEffect(
        verticalPrefetchImageLoader,
        verticalRowListState,
        loadingProfile,
        cwCardWidthPx,
        cwCardHeightPx,
        catalogCardWidthPx,
        catalogCardHeightPx
    ) {
        snapshotFlow {
            val lastVisibleRowIndex = verticalRowListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val rows = latestCarouselRowsForImagePrefetch.value.list
            val signature = buildVerticalImagePrefetchSignature(
                lastVisibleRowIndex = lastVisibleRowIndex,
                rows = rows,
                aheadRows = loadingProfile.verticalPrefetchRows,
                focusedIndexProvider = { key -> focusedItemByRowMap[key] ?: 0 }
            )
            VerticalPrefetchSnapshot(
                lastVisibleRowIndex = lastVisibleRowIndex,
                signature = signature,
                isScrolling = verticalRowListState.isScrollInProgress
            )
        }
            .distinctUntilChanged { old, new -> old.signature == new.signature }
            .filter { it.lastVisibleRowIndex >= 0 }
            .collectThrottledLeadingTrailing(
                leadingIntervalMs = 60L,
                trailingDelayMs = 60L,
                shouldBypassThrottle = { !it.isScrolling }
            ) { snapshot ->
                if (isVerticalGlideRef.get() || LeanbackArtworkLoader.isHoldScrolling) return@collectThrottledLeadingTrailing
                val rows = latestCarouselRowsForImagePrefetch.value.list
                val window = computeVerticalImagePrefetchWindow(
                    lastVisibleRowIndex = snapshot.lastVisibleRowIndex,
                    aheadRows = loadingProfile.verticalPrefetchRows,
                    totalRows = rows.size
                )
                for (rowIdx in window) {
                    val row = rows[rowIdx]
                    val items = row.items.list
                    if (items.isEmpty()) continue
                    val firstUrl = items.firstOrNull()?.let {
                        it.imageUrl ?: it.heroPreview.poster ?: it.heroPreview.backdrop
                    }
                    if (firstUrl.isPlaceholder()) continue
                    val (wPx, hPx, _) = rowCardDimensionsPx(row.key)
                    val cardIndices = computeRowCardPrefetchIndices(
                        focusedIndex = focusedItemByRowMap[row.key] ?: 0,
                        itemCount = items.size,
                        prefetchCount = loadingProfile.verticalPrefetchItems
                    )
                    for (itemIdx in cardIndices) {
                        val item = items[itemIdx]
                        val url = (item.imageUrl ?: item.heroPreview.poster ?: item.heroPreview.backdrop)?.trim()
                        if (url.isNullOrEmpty() || url.isPlaceholder()) continue
                        val cwPayload = item.payload as? ModernPayload.ContinueWatching
                        val shouldBlur = blurUnwatchedEpisodes && cwPayload?.item is ContinueWatchingItem.NextUp
                        val dedupKey = if (shouldBlur) "${url}|${wPx}x${hPx}|blur" else "${url}|${wPx}x${hPx}"
                        if (!prefetchedVerticalItems.tryMarkPrefetchedBounded(dedupKey)) continue
                        LeanbackArtworkLoader.prefetch(
                            context = context,
                            imageLoader = verticalPrefetchImageLoader,
                            imageUrl = url,
                            fallbackUrl = item.metaPreview?.rawPosterUrl,
                            widthPx = wPx,
                            heightPx = hPx,
                            blurred = shouldBlur,
                            isCollectionCover = item.payload is ModernPayload.CollectionFolder
                        )
                    }
                }
            }
    }

    val latestOnRequestLazyCatalogLoad = rememberUpdatedState(onRequestLazyCatalogLoad)
    val latestCarouselRowsForLazy = rememberUpdatedState(carouselRows)
    fun requestLazyInWindow(firstVisible: Int, lastVisible: Int) {
        val rows = latestCarouselRowsForLazy.value.list
        val window = computeLazyCatalogPrefetchWindow(
            firstVisible = firstVisible,
            lastVisible = lastVisible,
            aheadCount = loadingProfile.lazyPrefetchAheadModern,
            totalCount = rows.size
        )
        for (idx in window) {
            val row = rows[idx]
            if (row.isLoading && row.items.list.firstOrNull()?.imageUrl.isPlaceholder()) {
                val addonId = row.addonId ?: continue
                val apiType = row.apiType ?: continue
                val catalogId = row.catalogId ?: continue
                latestOnRequestLazyCatalogLoad.value(
                    catalogRowLegacyKey(addonId, apiType, catalogId)
                )
            }
        }
    }
    fun requestLazyForRowIndexWindow(rowIndex: Int) {
        requestLazyInWindow(firstVisible = rowIndex, lastVisible = rowIndex)
    }
    val openSidebar = com.nuvio.tv.LocalOpenSidebar.current
    val requestUpdateBannerFocus = LocalRequestUpdateBannerFocus.current
    val latestRequestUpdateBannerFocus = rememberUpdatedState(requestUpdateBannerFocus)
    // The row that currently owns (or is about to own) focus. Falls back to the nearest
    // surviving non-empty row if the tracked row was removed or became empty.
    val effectiveActiveRowKey: () -> String? = remember {
        {
            val rows = latestCarouselRowsForLazy.value.list
            fun validNonEmpty(key: String?): String? =
                key?.takeIf { candidate ->
                    rows.any { it.key == candidate && it.items.list.isNotEmpty() }
                }

            validNonEmpty(navTargetRowKey.get())
                ?: validNonEmpty(activeRowKey.value)
                ?: validNonEmpty(currentRowKeyTracker.get())
                ?: findNearestNonEmptyRowIndex(
                    rows = rows,
                    preferredIdx = targetRowIndexTracker,
                    visibleItems = verticalRowListState.layoutInfo.visibleItemsInfo
                ).takeIf { it >= 0 }?.let { rows[it].key }
        }
    }

    // Synchronously evacuate View focus from any AndroidView row about to be removed or
    // emptied BEFORE CompositionImpl.applyChanges() runs Operation$RemoveNode / removeAndroidView().
    // Otherwise ViewGroup.removeViewInternal() sees hasFocus() == true and triggers
    // rootViewRequestFocus() -> AndroidComposeView.requestFocus() -> subcompose() mid-applyChanges(),
    // throwing ComposeRuntimeError ("pending composition has not been applied").
    remember(carouselRows) {
        val rows = carouselRows.list
        val validNonEmptyKeys = HashSet<String>(rows.size).apply {
            rows.forEach { if (it.items.list.isNotEmpty()) add(it.key) }
        }
        val focusedRemovedEntries = rowGrids.entries.filter { (key, grid) ->
            key !in validNonEmptyKeys && grid.isAttachedToWindow && grid.hasFocus()
        }
        if (focusedRemovedEntries.isNotEmpty()) {
            focusedRemovedEntries.forEach { (_, grid) ->
                (grid as? ModernInteropHorizontalGridView)?.prepareForDetachment()
                (grid.parent as? ModernRowContainerLayout)?.isDetaching = true
            }
            val isGlideActive = isVerticalGlideRef.get() || verticalHoldDirectionRef.get() != 0
            var focusTransferred = false
            if (!isSidebarExpandedState.value) {
                if (!isGlideActive) {
                    val nearestIdx = findNearestNonEmptyRowIndex(
                        rows = rows,
                        preferredIdx = targetRowIndexTracker,
                        visibleItems = verticalRowListState.layoutInfo.visibleItemsInfo
                    )
                    val fallbackRow = if (nearestIdx >= 0) rows[nearestIdx] else null
                    if (fallbackRow != null) {
                        targetRowIndexTracker = nearestIdx
                        navTargetRowKey.set(fallbackRow.key)
                        currentRowKeyTracker.set(fallbackRow.key)
                        val safeIdx = (focusedItemByRowMap[fallbackRow.key] ?: 0)
                            .coerceIn(0, (fallbackRow.items.list.size - 1).coerceAtLeast(0))
                        val fallbackGrid = rowGrids[fallbackRow.key] as? ModernInteropHorizontalGridView
                        if (fallbackGrid != null && fallbackGrid.isAttachedToWindow) {
                            fallbackGrid.prepareForIncomingFocus()
                            fallbackGrid.setSelectedPosition(safeIdx)
                            focusTransferred = fallbackGrid.requestFocus()
                            if (focusTransferred) {
                                fallbackGrid.ensureChildFocusAndRing(targetPos = safeIdx, animated = false)
                            }
                        }
                    }
                }
                if (!focusTransferred) {
                    val overlayHost = verticalOverlayHostRef.get()
                    if (overlayHost != null && overlayHost.isAttachedToWindow) {
                        overlayHost.activateGlideFocus()
                        focusTransferred = overlayHost.hasFocus()
                    }
                }
            }
            focusedRemovedEntries.forEach { (_, grid) ->
                if (grid.hasFocus()) {
                    grid.clearFocus()
                }
            }
        }
        true
    }

    LaunchedEffect(carouselRows) {
        val rows = carouselRows.list
        val validKeys = HashSet<String>(rows.size).apply {
            rows.forEach { add(it.key) }
        }
        val validNonEmptyKeys = HashSet<String>(rows.size).apply {
            rows.forEach { if (it.items.list.isNotEmpty()) add(it.key) }
        }
        rowFocusRequesters.keys.retainAll(validKeys)
        attachedRowKeys.retainAll(validKeys)

        val currentNav = navTargetRowKey.get()
        val currentTracker = currentRowKeyTracker.get()
        val currentActive = activeRowKey.value
        val navLost = currentNav != null && currentNav !in validNonEmptyKeys
        val trackerLost = currentTracker != null && currentTracker !in validNonEmptyKeys
        val activeLost = currentActive != null && currentActive !in validNonEmptyKeys
        val pendingLost = localPendingFocusRowKey != null && localPendingFocusRowKey !in validNonEmptyKeys

        if (navLost || trackerLost || activeLost || pendingLost) {
            val nearestIdx = findNearestNonEmptyRowIndex(
                rows = rows,
                preferredIdx = targetRowIndexTracker,
                visibleItems = verticalRowListState.layoutInfo.visibleItemsInfo
            )
            val fallbackRow = if (nearestIdx >= 0) rows[nearestIdx] else null
            if (fallbackRow != null) {
                targetRowIndexTracker = nearestIdx
                navTargetRowKey.set(fallbackRow.key)
                currentRowKeyTracker.set(fallbackRow.key)
                latestOnActiveRowKeyChange.value(fallbackRow.key)
                val safeIdx = (focusedItemByRowMap[fallbackRow.key] ?: 0)
                    .coerceIn(0, (fallbackRow.items.list.size - 1).coerceAtLeast(0))
                latestOnActiveItemIndexChange.value(safeIdx)
                if (pendingLost) {
                    localPendingFocusRowKey = fallbackRow.key
                    localPendingFocusIndex = safeIdx
                    localPendingFocusNonce++
                }
                if (!isSidebarExpandedState.value &&
                    !isVerticalGlideRef.get() &&
                    verticalHoldDirectionRef.get() == 0
                ) {
                    val fallbackGrid = rowGrids[fallbackRow.key] as? ModernInteropHorizontalGridView
                    if (fallbackGrid != null && fallbackGrid.isAttachedToWindow) {
                        fallbackGrid.prepareForIncomingFocus()
                        fallbackGrid.setSelectedPosition(safeIdx)
                        val focusTaken = fallbackGrid.requestFocus()
                        fallbackGrid.ensureChildFocusAndRing(targetPos = safeIdx, animated = false)
                        if (focusTaken) {
                            if (verticalOverlayHostRef.get()?.hasFocus() == true) {
                                verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
                            }
                        } else if (!pendingLost) {
                            localPendingFocusRowKey = fallbackRow.key
                            localPendingFocusIndex = safeIdx
                            localPendingFocusNonce++
                        }
                    } else if (!pendingLost) {
                        localPendingFocusRowKey = fallbackRow.key
                        localPendingFocusIndex = safeIdx
                        localPendingFocusNonce++
                    }
                }
            } else {
                navTargetRowKey.set(null)
                currentRowKeyTracker.set(null)
                if (pendingLost) localPendingFocusRowKey = null
            }
        }
        val isGlideActive = isVerticalGlideRef.get() || verticalHoldDirectionRef.get() != 0
        if (!isSidebarExpandedState.value &&
            !isGlideActive &&
            verticalOverlayHostRef.get()?.hasFocus() == true
        ) {
            val targetKey = effectiveActiveRowKey()
            val targetRow = targetKey?.let { key -> rows.firstOrNull { it.key == key } }
            val targetGrid = targetKey?.let { rowGrids[it] as? ModernInteropHorizontalGridView }
            if (targetRow != null && targetGrid != null && targetGrid.isAttachedToWindow) {
                val safeIdx = (focusedItemByRowMap[targetRow.key] ?: 0)
                    .coerceIn(0, (targetRow.items.list.size - 1).coerceAtLeast(0))
                targetGrid.prepareForIncomingFocus()
                targetGrid.setSelectedPosition(safeIdx)
                if (targetGrid.requestFocus()) {
                    targetGrid.ensureChildFocusAndRing(targetPos = safeIdx, animated = false)
                    verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
                }
            }
        }
    }

    fun resolveAnchoredLeftPx(cardWidthPx: Int): Float {
        val host = verticalOverlayHostRef.get()
        return if (layoutDirection == LayoutDirection.Rtl && host != null && host.width > 0) {
            (host.width - rowStartPaddingPx - cardWidthPx).coerceAtLeast(0f)
        } else {
            rowStartPaddingPx
        }
    }

    fun triggerLandingRowImageRefresh(grid: androidx.leanback.widget.HorizontalGridView?) {
        if (grid == null) return
        grid.post {
            if (!grid.isAttachedToWindow) return@post
            val adapter = grid.adapter as? LeanbackRowAdapter ?: return@post
            adapter.onScrollSettled()
            adapter.prefetchFromVisibleRange(grid.context)
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i) ?: continue
                val vh = grid.getChildViewHolder(child) as? LeanbackCardViewHolder ?: continue
                if (vh.posterImage.drawable == null) {
                    val pos = grid.getChildAdapterPosition(child)
                    if (pos != RecyclerView.NO_POSITION && pos in 0 until adapter.itemCount) {
                        adapter.onBindViewHolder(vh, pos)
                    }
                }
            }
        }
    }

    fun refreshAllVisibleRowImages(excludeGrid: androidx.leanback.widget.HorizontalGridView? = null) {
        rowGrids.values.forEach { grid ->
            if (grid !== excludeGrid && grid.isAttachedToWindow) {
                triggerLandingRowImageRefresh(grid)
            }
        }
    }

    fun handoffOverlayToGrid(
        rowKey: String,
        targetCardIndex: Int,
        expectedGen: Long,
        retryCount: Int = 0
    ) {
        if (overlayGenerationRef.get() != expectedGen) {
            if (isVerticalGlideRef.get() && scrollJob?.isActive != true && verticalHoldJobRef.get()?.isActive != true) {
                isVerticalGlideRef.set(false)
                verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
            }
            return
        }
        val rows = latestCarouselRowsForLazy.value.list
        val resolvedRow = rows.firstOrNull { it.key == rowKey && it.items.list.isNotEmpty() }
            ?: findNearestNonEmptyRowIndex(
                rows = rows,
                preferredIdx = targetRowIndexTracker,
                visibleItems = verticalRowListState.layoutInfo.visibleItemsInfo
            ).takeIf { it >= 0 }?.let { idx ->
                targetRowIndexTracker = idx
                val fallback = rows[idx]
                navTargetRowKey.set(fallback.key)
                currentRowKeyTracker.set(fallback.key)
                latestOnActiveRowKeyChange.value(fallback.key)
                fallback
            }
        if (resolvedRow == null) {
            isVerticalGlideRef.set(false)
            verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
            return
        }
        val resolvedKey = resolvedRow.key
        val resolvedCardIdx = if (resolvedKey == rowKey) {
            targetCardIndex.coerceIn(0, (resolvedRow.items.list.size - 1).coerceAtLeast(0))
        } else {
            (focusedItemByRowMap[resolvedKey] ?: 0).coerceIn(0, (resolvedRow.items.list.size - 1).coerceAtLeast(0))
        }
        val grid = rowGrids[resolvedKey] as? ModernInteropHorizontalGridView
        if (grid != null && grid.isAttachedToWindow) {
            grid.prepareForIncomingFocus()
            grid.setSelectedPosition(resolvedCardIdx)
            val vh = grid.findViewHolderForAdapterPosition(resolvedCardIdx) as? LeanbackCardViewHolder
            val ring = grid.floatingRingView
            if (!grid.isComputingLayout && !grid.isInLayout &&
                vh != null && ring != null && vh.cardContainer.width > 0 && vh.cardContainer.height > 0
            ) {
                val wasGlide = isVerticalGlideRef.get()
                isVerticalGlideRef.set(false)
                if (!grid.hasFocus()) {
                    grid.requestFocus()
                }
                grid.ensureChildFocusAndRing(targetPos = resolvedCardIdx, animated = false)
                val ringMargin = (ring.glowMarginPx * 2).toInt()
                if (grid.hasFocus() && ring.visibility == View.VISIBLE && ring.width > ringMargin && ring.height > ringMargin) {
                    verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
                    triggerLandingRowImageRefresh(grid)
                    return
                }
                if (wasGlide) isVerticalGlideRef.set(true)
            }
            if (retryCount < 8) {
                grid.postOnAnimation {
                    handoffOverlayToGrid(resolvedKey, resolvedCardIdx, expectedGen, retryCount + 1)
                }
                return
            }
            isVerticalGlideRef.set(false)
            if (!grid.hasFocus()) grid.requestFocus()
            grid.ensureChildFocusAndRing(targetPos = resolvedCardIdx, animated = false)
            verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
            triggerLandingRowImageRefresh(grid)
        } else {
            val overlayHost = verticalOverlayHostRef.get()
            if (retryCount < 8 && overlayHost != null && overlayHost.isAttachedToWindow) {
                overlayHost.postOnAnimation {
                    handoffOverlayToGrid(resolvedKey, resolvedCardIdx, expectedGen, retryCount + 1)
                }
                return
            }
            isVerticalGlideRef.set(false)
            localPendingFocusRowKey = resolvedKey
            localPendingFocusIndex = resolvedCardIdx
            localPendingFocusNonce++
            overlayHost?.deactivateGlideFocusAndHideRing()
        }
    }

    fun finishOverlayRingToCurrentGrid() {
        val overlayHost = verticalOverlayHostRef.get()
        val overlayActive = isVerticalGlideRef.get() ||
            scrollJob?.isActive == true ||
            overlayHost?.ringView?.visibility == View.VISIBLE ||
            overlayHost?.hasFocus() == true
        val wasHoldScrolling = LeanbackArtworkLoader.isHoldScrolling
        if (wasHoldScrolling) {
            LeanbackArtworkLoader.isHoldScrolling = false
        }
        if (!overlayActive) {
            if (wasHoldScrolling) {
                refreshAllVisibleRowImages()
            }
            return
        }

        overlayGenerationRef.incrementAndGet()
        verticalHoldDirectionRef.set(0)
        verticalHoldJobRef.getAndSet(null)?.cancel()
        verticalHoldTimeoutRef.getAndSet(null)?.cancel()
        scrollJob?.cancel()
        isVerticalGlideRef.set(false)
        LeanbackArtworkLoader.isHoldScrolling = false
        latestOnFastScrollingChanged.value(false)
        overlayHost?.deactivateGlideFocusAndHideRing()
        refreshAllVisibleRowImages()
        // Hand focus back cleanly without snapping selectedPosition backward over a pending horizontal move
        val key = effectiveActiveRowKey()
        val grid = key?.let { rowGrids[it] as? ModernInteropHorizontalGridView }
        if (grid != null && grid.isAttachedToWindow) {
            grid.prepareForIncomingFocus()
            if (!grid.hasFocus()) {
                grid.requestFocus()
            }
        }
    }

    fun measureFocusedCardInOverlay(rowKey: String?): FloatArray? {
        val host = verticalOverlayHostRef.get() ?: return null
        if (!host.isAttachedToWindow) return null
        val grid = rowKey?.let { rowGrids[it] as? ModernInteropHorizontalGridView }
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
        LeanbackArtworkLoader.isHoldScrolling = false

        val rows = latestCarouselRowsForLazy.value.list
        if (rows.isEmpty()) {
            overlayGenerationRef.incrementAndGet()
            isVerticalGlideRef.set(false)
            verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
            refreshAllVisibleRowImages()
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
        val rawLandingIdx = when {
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
        val landingIdx = when {
            dir > 0 -> maxOf(rawLandingIdx, targetRowIndexTracker).coerceIn(0, lastNonEmptyIdx)
            dir < 0 -> minOf(rawLandingIdx, targetRowIndexTracker).coerceIn(0, lastNonEmptyIdx)
            else -> rawLandingIdx.coerceIn(0, lastNonEmptyIdx)
        }
        val landingRow = rows.getOrNull(landingIdx) ?: run {
            overlayGenerationRef.incrementAndGet()
            isVerticalGlideRef.set(false)
            verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
            refreshAllVisibleRowImages()
            return
        }

        val prevKey = navTargetRowKey.get() ?: currentRowKeyTracker.get() ?: activeRowKey.value
        if (prevKey != null && prevKey != landingRow.key) {
            (rowGrids[prevKey] as? ModernInteropHorizontalGridView)?.let { prevGrid ->
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
        triggerLandingRowImageRefresh(landingGrid)
        refreshAllVisibleRowImages(excludeGrid = landingGrid)
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
            // Protect the initial single-step scrollJob from early repeat pulses (repeatCount == 1)
            // emitted by IR (NEC protocol) or Bluetooth remotes during a normal single press.
            val nowMs = android.os.SystemClock.uptimeMillis()
            val elapsedSinceSingleStep = nowMs - lastVerticalSingleStepAtMsRef.get()
            if (lastVerticalSingleStepDirRef.get() == sign &&
                elapsedSinceSingleStep in 0 until VERTICAL_HOLD_GRACE_PERIOD_MS
            ) {
                return true
            }
            scrollJob?.cancel()
            verticalHoldJobRef.getAndSet(null)?.cancel()
            val glideGen = overlayGenerationRef.incrementAndGet()
            verticalHoldDirectionRef.set(sign)
            isVerticalGlideRef.set(true)
            LeanbackArtworkLoader.isHoldScrolling = true
            latestOnFastScrollingChanged.value(true)

            val host = verticalOverlayHostRef.get()
            val existingRing = host?.ringView
            val visibleRing = existingRing?.takeIf {
                it.visibility == View.VISIBLE && it.width > 0 && it.height > 0
            }
            val currentKey = navTargetRowKey.get() ?: currentRowKeyTracker.get() ?: activeRowKey.value
            val currentGrid = currentKey?.let { rowGrids[it] as? ModernInteropHorizontalGridView }
            val currentRowWasExpanded = (currentGrid?.adapter as? LeanbackRowAdapter)?.let {
                it.hasExpandedCard || it.hasAnimatingWidthCard
            } == true
            if (currentRowWasExpanded) {
                currentGrid.prepareForRowChange()
            }
            val (defaultW, defaultH, defaultR) = rowCardDimensionsPx(currentKey)
            val anchorY = cachedCardTopInRowRef.get()
            val measuredRect = if (visibleRing != null || currentRowWasExpanded) null else measureFocusedCardInOverlay(currentKey)
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
                visibleRing != null -> (visibleRing.drawnCardWidthPx.takeIf { it > 0 }?.toFloat()
                    ?: (visibleRing.width - (visibleRing.glowMarginPx * 2f))).coerceAtLeast(1f)
                measuredRect != null -> measuredRect[2]
                else -> defaultW.toFloat()
            }
            val startH = when {
                visibleRing != null -> (visibleRing.drawnCardHeightPx.takeIf { it > 0 }?.toFloat()
                    ?: (visibleRing.height - (visibleRing.glowMarginPx * 2f))).coerceAtLeast(1f)
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
                (rowGrids[currentKey] as? ModernInteropHorizontalGridView)?.floatingRingView?.hideRing(true)
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
                                    requestLazyForRowIndexWindow(passingInfo.index)
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
        LeanbackArtworkLoader.isHoldScrolling = false
        val rows = latestCarouselRowsForLazy.value.list
        if (rows.isEmpty()) return false
        val currentKey = effectiveActiveRowKey()
        val currentIdx = currentKey?.let { key -> rows.indexOfFirst { it.key == key } }
            ?.takeIf { it >= 0 }
            ?: targetRowIndexTracker.coerceIn(0, rows.lastIndex)

        var candidateIdx = currentIdx + sign
        while (candidateIdx in rows.indices && rows[candidateIdx].items.list.isEmpty()) {
            val emptyRow = rows[candidateIdx]
            val addonId = emptyRow.addonId
            val apiType = emptyRow.apiType
            val catalogId = emptyRow.catalogId
            if (addonId != null && apiType != null && catalogId != null) {
                latestOnRequestLazyCatalogLoad.value(
                    catalogRowLegacyKey(addonId, apiType, catalogId)
                )
            }
            candidateIdx += sign
        }

        if (sign < 0 && candidateIdx < 0) {
            return latestRequestUpdateBannerFocus.value?.invoke() == true
        }
        if (candidateIdx !in rows.indices) {
            return false
        }
        val targetIdx = candidateIdx
        val targetRow = rows[targetIdx]
        requestLazyForRowIndexWindow(targetIdx)

        lastVerticalSingleStepAtMsRef.set(android.os.SystemClock.uptimeMillis())
        lastVerticalSingleStepDirRef.set(sign)

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
            overlayHost.activateGlideFocus()
            if (prevKey != null) {
                (rowGrids[prevKey] as? ModernInteropHorizontalGridView)?.let { prevGrid ->
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
                (rowGrids[prevKey] as? ModernInteropHorizontalGridView)?.let { prevGrid ->
                    prevGrid.prepareForRowChange()
                    prevGrid.floatingRingView?.hideRing(true)
                }
            }
            overlayHost?.deactivateGlideFocusAndHideRing()
        }

        val movedToTargetGrid = if (targetGrid != null && targetGrid.isAttachedToWindow) {
            (targetGrid as? ModernInteropHorizontalGridView)?.prepareForIncomingFocus()
            targetGrid.setSelectedPosition(targetCardIndex)
            val granted = targetGrid.requestFocus()
            if (!canUseFixedStepRing) {
                val modernGrid = targetGrid as? ModernInteropHorizontalGridView
                modernGrid?.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
                targetGrid.post {
                    modernGrid?.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
                }
            }
            if (!granted) {
                localPendingFocusRowKey = targetRow.key
                localPendingFocusIndex = targetCardIndex
                localPendingFocusNonce++
            }
            granted
        } else {
            localPendingFocusRowKey = targetRow.key
            localPendingFocusIndex = targetCardIndex
            localPendingFocusNonce++
            false
        }
        if (!canUseFixedStepRing && !movedToTargetGrid) {
            // Move focus off prevGrid before LazyColumn scrolls it off-screen and recycles its
            // AndroidView via Operation$RemoveNode.
            if (overlayHost != null && overlayHost.isAttachedToWindow) {
                overlayHost.activateGlideFocus()
            } else if (prevKey != null) {
                (rowGrids[prevKey] as? ModernInteropHorizontalGridView)?.let { prevGrid ->
                    if (prevGrid.hasFocus()) {
                        prevGrid.prepareForDetachment()
                        (prevGrid.parent as? ModernRowContainerLayout)?.isDetaching = true
                        prevGrid.clearFocus()
                    }
                }
            }
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
                if (overlayGenerationRef.get() == stepGen &&
                    (canUseFixedStepRing || verticalOverlayHostRef.get()?.hasFocus() == true)
                ) {
                    handoffOverlayToGrid(targetRow.key, targetCardIndex, stepGen)
                }
            }
        }
        return true
    }
    LaunchedEffect(verticalRowListState, loadingProfile) {
        snapshotFlow {
            val info = verticalRowListState.layoutInfo
            val firstVisible = info.visibleItemsInfo.firstOrNull()?.index ?: -1
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            Triple(firstVisible, lastVisible, latestCarouselRowsForLazy.value.list.size)
        }
            .distinctUntilChanged()
            .filter { it.second >= 0 }
            .collectThrottledLeadingTrailing(
                leadingIntervalMs = 80L,
                trailingDelayMs = 100L
            ) { (firstVisible, lastVisible, _) ->
                requestLazyInWindow(firstVisible, lastVisible)
            }
    }

    val focusRestorerRequester = remember {
        {
            // Only return a FocusRequester currently attached to an active row composition;
            // returning an unattached/offscreen requester logs FocusRelatedWarning and fails to focus.
            val activeKey = effectiveActiveRowKey()
            val attachedRequester = activeKey
                ?.takeIf { it in attachedRowKeys }
                ?.let { rowFocusRequesters[it] }
                ?: verticalRowListState.layoutInfo.visibleItemsInfo
                    .firstNotNullOfOrNull { info ->
                        val key = info.key as? String
                        if (key != null && key in attachedRowKeys) rowFocusRequesters[key] else null
                    }
            attachedRequester ?: FocusRequester.Default
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
                    (grid as? ModernInteropHorizontalGridView)?.prepareForIncomingFocus()
                    grid.setSelectedPosition(savedIndex)
                    grid.requestFocus()
                    (grid as? ModernInteropHorizontalGridView)?.ensureChildFocusAndRing(targetPos = savedIndex, animated = false)
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
                            effectiveCardExpandEnabled &&
                            expandedCatalogFocusKey.value != null &&
                            currentRowKey == firstRowKey
                        ) return@onPreviewKeyEvent true
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.DirectionDown &&
                            effectiveCardExpandEnabled &&
                            expandedCatalogFocusKey.value != null &&
                            currentRowKey == lastRowKey
                        ) return@onPreviewKeyEvent true
                        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                            when (event.key) {
                                Key.DirectionDown -> {
                                    if (verticalHoldDirectionRef.get() != 0) {
                                        endVerticalHoldScroll()
                                    }
                                    return@onPreviewKeyEvent stepVerticalRow(1)
                                }
                                Key.DirectionUp -> {
                                    if (verticalHoldDirectionRef.get() != 0) {
                                        endVerticalHoldScroll()
                                    }
                                    return@onPreviewKeyEvent stepVerticalRow(-1)
                                }
                            }
                        }
                        val blockKey = if (layoutDirection == LayoutDirection.Rtl)
                            Key.DirectionRight else Key.DirectionLeft
                        if (blockLeftOnFirstExpandedItem &&
                            event.type == KeyEventType.KeyDown &&
                            event.key == blockKey &&
                            effectiveCardExpandEnabled &&
                            expandedCatalogFocusKey.value != null &&
                            activeItemIndex.value == 0
                        ) return@onPreviewKeyEvent true
                        false
                    }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.DirectionDown -> {
                                    return@onKeyEvent stepVerticalRow(1)
                                }
                                Key.DirectionUp -> {
                                    return@onKeyEvent stepVerticalRow(-1)
                                }
                            }
                        }
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
                DisposableEffect(row.key) {
                    attachedRowKeys.add(row.key)
                    onDispose {
                        attachedRowKeys.remove(row.key)
                    }
                }
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
                                    val currentGrid = rowGrids[currentActiveRowKey] as? ModernInteropHorizontalGridView
                                    currentGrid?.prepareForRowChange()
                                    currentGrid?.floatingRingView?.hideRing(false)
                                    val firstGrid = rowGrids[firstRow.key]
                                    if (firstGrid == null && currentGrid != null && currentGrid.hasFocus()) {
                                        val overlayHost = verticalOverlayHostRef.get()
                                        if (overlayHost != null && overlayHost.isAttachedToWindow) {
                                            overlayHost.activateGlideFocus()
                                        } else {
                                            currentGrid.prepareForDetachment()
                                            (currentGrid.parent as? ModernRowContainerLayout)?.isDetaching = true
                                            currentGrid.clearFocus()
                                        }
                                    }
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
                                    if (firstGrid != null) {
                                        firstGrid.post {
                                            if (firstGrid.isAttachedToWindow &&
                                                navTargetRowKey.get() == firstRow.key
                                            ) {
                                                val modernGrid = firstGrid as? ModernInteropHorizontalGridView
                                                modernGrid?.prepareForIncomingFocus()
                                                if (animationsEnabled) {
                                                    firstGrid.setSelectedPositionSmooth(targetCardIndex)
                                                } else {
                                                    firstGrid.setSelectedPosition(targetCardIndex)
                                                }
                                                firstGrid.requestFocus()
                                                modernGrid?.ensureChildFocusAndRing(targetPos = targetCardIndex, animated = false)
                                                if (!isVerticalGlideRef.get()) {
                                                    verticalOverlayHostRef.get()?.deactivateGlideFocusAndHideRing()
                                                }
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
                            key?.takeIf { candidate ->
                                rows.any { it.key == candidate && it.items.list.isNotEmpty() }
                            }
                        val intendedKey = knownKey(navTargetRowKey.get())
                            ?: knownKey(currentRowKeyTracker.get())
                            ?: knownKey(activeRowKey.value)
                        val now = System.currentTimeMillis()

                        if (intendedKey != null && intendedKey != rowKey) {
                            val activeGrid = rowGrids[intendedKey]
                            if (activeGrid != null && activeGrid.isAttachedToWindow) {
                                val modernActiveGrid = activeGrid as? ModernInteropHorizontalGridView
                                modernActiveGrid?.prepareForIncomingFocus()
                                activeGrid.requestFocus()
                                modernActiveGrid?.ensureChildFocusAndRing(animated = false)
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
                    rowTitleBottom = MODERN_ROW_TITLE_BOTTOM_SPACING,
                    minRowHeight = if (singleRowMode) rowsViewportHeight else Dp.Unspecified,
                    focusedItemByRow = focusedItemByRow,
                    loadMoreRequestedTotals = loadMoreRequestedTotals,
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
                    onClearPendingFocus = stableClearPendingFocus,
                    isCatalogItemWatched = isCatalogItemWatched,
                    blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                    backdropExpandEnabled = effectiveCardExpandEnabled,
                    alwaysShowLandscapeClearlogo = alwaysShowLandscapeClearlogo,
                    playTrailerInExpandedCard = playTrailerInExpandedCard,
                    focusedPosterBackdropTrailerMuted = focusedPosterBackdropTrailerMuted,
                    expandedCatalogFocusKeyState = expandedCatalogFocusKey,
                    expandedTrailerPreviewUrl = expandedTrailerPreviewUrl,
                    expandedTrailerPreviewAudioUrl = expandedTrailerPreviewAudioUrl,
                    expandedTrailerHost = expandedTrailerHost,
                    enrichedPreviews = enrichedPreviews,
                    onBackdropInteraction = onBackdropInteraction,
                    onExpandedTrailerEnded = stableOnExpandedTrailerEnded
                )
            }
        }
            AndroidView(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        clip = false
                    },
                factory = { ctx ->
                    VerticalRingOverlayHost(ctx).also { host ->
                        host.preMeasureCardSize(catalogCardWidthPx, catalogCardHeightPx)
                        host.ringView.isGlowEnabled = animationsEnabled
                        host.ringView.themeStyle = themeStyle
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
                    host.ringView.isGlowEnabled = animationsEnabled
                    host.ringView.themeStyle = themeStyle
                    host.preMeasureCardSize(catalogCardWidthPx, catalogCardHeightPx)
                }
            )
        }
    }
}
