package com.nuvio.tv.ui.screens.home

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.RecyclerView
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.leanback.core.LeanbackExpandedTrailerHost
import com.nuvio.tv.ui.leanback.core.rememberLeanbackThemeStyle
import com.nuvio.tv.ui.util.StableMap
import kotlinx.coroutines.awaitCancellation

@Composable
fun LeanbackModernRow(
    rowKey: String,
    items: List<ModernCarouselItem>,
    cardWidth: Dp,
    cardHeight: Dp,
    cornerRadius: Dp,
    showLabels: Boolean,
    animationsEnabled: Boolean,
    startPadding: Dp,
    spacing: Dp,
    onItemFocused: (Int, ModernCarouselItem) -> Unit,
    onItemClick: (ModernCarouselItem) -> Unit,
    onItemLongClick: (ModernCarouselItem) -> Unit,
    onLoadMore: () -> Unit,
    onNavigateDown: () -> Boolean = { false },
    onNavigateUp: () -> Boolean = { false },
    onNavigateLeftOnFirstItem: () -> Boolean = { false },
    onNavigateBack: () -> Boolean = { false },
    onVerticalHoldScroll: ((Int) -> Boolean)? = null,
    onVerticalHoldRelease: (() -> Unit)? = null,
    isVerticalGlideActive: (() -> Boolean)? = null,
    onHorizontalInterrupt: (() -> Unit)? = null,
    rowGrids: MutableMap<String, HorizontalGridView>? = null,
    isPendingFocus: Boolean = false,
    pendingFocusIndex: Int = 0,
    initialFocusPosition: Int = 0,
    pendingFocusNonce: Int = 0,
    onClearPendingFocus: () -> Unit = {},
    canTakeFocus: (() -> Boolean)? = null,
    recycledViewPool: RecyclerView.RecycledViewPool? = null,
    isCatalogItemWatched: (MetaPreview) -> Boolean = { false },
    blurUnwatchedEpisodes: Boolean = false,
    backdropExpandEnabled: Boolean = false,
    useLandscapePosters: Boolean = false,
    alwaysShowLandscapeClearlogo: Boolean = false,
    playTrailerInExpandedCard: Boolean = false,
    trailerMuted: Boolean = true,
    expandedCatalogFocusKeyState: State<String?>? = null,
    expandedTrailerPreviewUrl: () -> String? = { null },
    expandedTrailerPreviewAudioUrl: () -> String? = { null },
    expandedTrailerHost: LeanbackExpandedTrailerHost? = null,
    enrichedPreviews: State<StableMap<String, MetaPreview>>? = null,
    onBackdropInteraction: (() -> Unit)? = null,
    onExpandedTrailerEnded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val themeStyle = rememberLeanbackThemeStyle(borderWidthDp = 2.0f)
    val cardWidthPx = with(density) { cardWidth.roundToPx() }
    val cardHeightPx = with(density) { cardHeight.roundToPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val startPaddingPx = with(density) { startPadding.roundToPx() }
    val spacingPx = with(density) { spacing.roundToPx() }
    val verticalPaddingDp = MODERN_ROW_CARD_VERTICAL_PADDING
    val verticalPaddingPx = with(density) { verticalPaddingDp.roundToPx() }
    val labelHeightDp = if (showLabels) (26f * density.fontScale.coerceIn(1f, 1.4f)).dp else 0.dp
    val labelHeightPx = if (showLabels) with(density) { labelHeightDp.roundToPx() } else 0
    val totalRowHeightPx = cardHeightPx + labelHeightPx + (verticalPaddingPx * 2)
    val totalRowHeightDp = cardHeight + labelHeightDp + (verticalPaddingDp * 2)

    val adapter = remember(rowKey) {
        LeanbackRowAdapter(
            cardWidthPx = cardWidthPx,
            cardHeightPx = cardHeightPx,
            cornerRadiusPx = cornerRadiusPx,
            showLabels = showLabels,
            animationsEnabled = { animationsEnabled },
            onItemFocused = onItemFocused,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onLoadMore = onLoadMore,
            themeStyle = themeStyle,
            isCatalogItemWatched = isCatalogItemWatched,
            blurUnwatchedEpisodes = blurUnwatchedEpisodes
        )
    }

    DisposableEffect(rowKey) {
        onDispose {
            adapter.collapseExpandedCardImmediate()
            rowGrids?.remove(rowKey)
        }
    }

    val latestExpandedTrailerPreviewUrl by rememberUpdatedState(expandedTrailerPreviewUrl)
    val latestExpandedTrailerPreviewAudioUrl by rememberUpdatedState(expandedTrailerPreviewAudioUrl)
    val latestEnrichedPreviews by rememberUpdatedState(enrichedPreviews)

    // Observe focus-driven expansion state via snapshotFlow so changing the expanded card
    // never recomposes ModernRowSection or sibling rows.
    LaunchedEffect(
        rowKey,
        adapter,
        backdropExpandEnabled,
        useLandscapePosters,
        alwaysShowLandscapeClearlogo,
        playTrailerInExpandedCard,
        trailerMuted,
        expandedTrailerHost,
        expandedCatalogFocusKeyState
    ) {
        adapter.backdropExpandEnabled = backdropExpandEnabled
        adapter.useLandscapePosters = useLandscapePosters
        adapter.alwaysShowLandscapeClearlogo = alwaysShowLandscapeClearlogo
        adapter.playTrailerInExpandedCard = playTrailerInExpandedCard
        adapter.trailerMuted = trailerMuted
        adapter.expandedTrailerHost = if (playTrailerInExpandedCard) expandedTrailerHost else null
        adapter.enrichedPreviewsProvider = { latestEnrichedPreviews?.value?.map ?: emptyMap() }
        adapter.onExpandedTrailerEnded = onExpandedTrailerEnded

        if (!backdropExpandEnabled || expandedCatalogFocusKeyState == null) {
            adapter.collapseExpandedCardImmediate()
            return@LaunchedEffect
        }

        snapshotFlow {
            val focusKey = expandedCatalogFocusKeyState.value
            val trailerUrl = if (focusKey != null && playTrailerInExpandedCard) {
                latestExpandedTrailerPreviewUrl()
            } else {
                null
            }
            val trailerAudioUrl = if (focusKey != null && playTrailerInExpandedCard) {
                latestExpandedTrailerPreviewAudioUrl()
            } else {
                null
            }
            val enrichedSnapshot = if (focusKey != null) latestEnrichedPreviews?.value?.map else null
            Triple(focusKey, trailerUrl to trailerAudioUrl, enrichedSnapshot)
        }.collect { (focusKey, trailerPair, _) ->
            adapter.updateExpandedCardState(
                expandedFocusKey = focusKey,
                trailerUrl = trailerPair.first,
                trailerAudioUrl = trailerPair.second
            )
        }
    }

    fun executeRowFocus(grid: ModernInteropHorizontalGridView, targetIdx: Int) {
        if (canTakeFocus?.invoke() != false) {
            grid.prepareForIncomingFocus()
        }
        grid.post {
            if (!grid.isAttachedToWindow) return@post
            if (canTakeFocus?.invoke() != false) {
                grid.prepareForIncomingFocus()
            }
            if (!grid.mayTakeFocus()) return@post
            grid.setSelectedPosition(targetIdx)
            grid.requestFocus()
            grid.ensureChildFocusAndRing(targetPos = targetIdx, animated = false)
        }
    }

    var localGridViewRef by remember(rowKey) { mutableStateOf<ModernInteropHorizontalGridView?>(null) }
    var initialScrollApplied by remember(rowKey) { mutableStateOf(false) }
    var lastAppliedInitialPosition by remember(rowKey) { mutableIntStateOf(-1) }

    LaunchedEffect(isPendingFocus, pendingFocusNonce, items.isNotEmpty(), localGridViewRef) {
        if (isPendingFocus && items.isNotEmpty()) {
            val targetIdx = pendingFocusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val gridView = localGridViewRef ?: (rowGrids?.get(rowKey) as? ModernInteropHorizontalGridView)
            if (gridView != null) {
                if (gridView.isAttachedToWindow) {
                    executeRowFocus(gridView, targetIdx)
                    onClearPendingFocus()
                } else {
                    val attachListener = object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            v.removeOnAttachStateChangeListener(this)
                            executeRowFocus(gridView, targetIdx)
                            onClearPendingFocus()
                        }
                        override fun onViewDetachedFromWindow(v: View) {}
                    }
                    gridView.addOnAttachStateChangeListener(attachListener)
                    try {
                        awaitCancellation()
                    } finally {
                        gridView.removeOnAttachStateChangeListener(attachListener)
                    }
                }
            }
        }
    }

    LaunchedEffect(initialFocusPosition, items.isNotEmpty(), localGridViewRef) {
        if (!initialScrollApplied && !isPendingFocus && items.isNotEmpty() && initialFocusPosition in items.indices) {
            val gridView = localGridViewRef ?: (rowGrids?.get(rowKey) as? ModernInteropHorizontalGridView) ?: return@LaunchedEffect
            fun applyInitialPosition() {
                if (!gridView.hasFocus() && initialFocusPosition in items.indices) {
                    if (initialFocusPosition > 0) {
                        gridView.setSelectedPosition(initialFocusPosition)
                    }
                    lastAppliedInitialPosition = initialFocusPosition
                    initialScrollApplied = true
                }
            }
            if (gridView.isAttachedToWindow) {
                applyInitialPosition()
            } else {
                val attachListener = object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        v.removeOnAttachStateChangeListener(this)
                        applyInitialPosition()
                    }
                    override fun onViewDetachedFromWindow(v: View) {}
                }
                gridView.addOnAttachStateChangeListener(attachListener)
                try {
                    awaitCancellation()
                } finally {
                    gridView.removeOnAttachStateChangeListener(attachListener)
                }
            }
        }
    }

    // Read isCatalogItemWatched during composition so Compose registers a snapshot observer
    // on HomeScreen's latestMovieWatchedStatus without changing the stable lambda identity.
    var watchedMaskHash = 1
    for (i in items.indices) {
        val meta = items[i].metaPreview
        val isWatched = meta != null && isCatalogItemWatched(meta)
        watchedMaskHash = 31 * watchedMaskHash + if (isWatched) 1231 else 1237
    }

    AndroidView(
        modifier = modifier
            .height(totalRowHeightDp)
            .fillMaxWidth()
            .graphicsLayer { clip = false },
        factory = { context ->
            val container = ModernRowContainerLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    totalRowHeightPx
                )
                clipChildren = false
                clipToPadding = false
            }
            val gridView = ModernInteropHorizontalGridView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    totalRowHeightPx
                )
                setRowHeight(totalRowHeightPx)
                configuredRowHeightPx = totalRowHeightPx
                setNumRows(1)
                focusScrollStrategy = BaseGridView.FOCUS_SCROLL_ALIGNED
                windowAlignment = BaseGridView.WINDOW_ALIGN_NO_EDGE
                windowAlignmentOffset = startPaddingPx
                windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
                itemAlignmentOffset = 0
                itemAlignmentOffsetPercent = 0f
                setItemSpacing(spacingPx)
                configuredSpacingPx = spacingPx
                clipToPadding = false
                clipChildren = false
                setPaddingRelative(startPaddingPx, verticalPaddingPx, startPaddingPx, verticalPaddingPx)
                configuredStartPaddingPx = startPaddingPx
                configuredVerticalPaddingPx = verticalPaddingPx
                adapter.submitItems(items)
                this.adapter = adapter
                this.onNavigateDown = onNavigateDown
                this.onNavigateUp = onNavigateUp
                this.onNavigateBack = onNavigateBack
                this.onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem
                this.onVerticalHoldScroll = onVerticalHoldScroll
                this.onVerticalHoldRelease = onVerticalHoldRelease
                this.isVerticalGlideActive = isVerticalGlideActive
                this.onHorizontalInterrupt = onHorizontalInterrupt
                this.onBackdropInteraction = onBackdropInteraction
                this.canTakeFocus = canTakeFocus
                this.animationsEnabled = animationsEnabled
                setAnimateChildLayout(animationsEnabled && !backdropExpandEnabled)
                configuredAnimationsEnabled = animationsEnabled
                configuredBackdropExpandEnabled = backdropExpandEnabled
                setHasFixedSize(!backdropExpandEnabled)
                setItemViewCacheSize(8)
                recycledViewPool?.let { setRecycledViewPool(it) }
                localGridViewRef = this
                rowGrids?.put(rowKey, this)
            }
            val focusRing = ModernRowFloatingFocusRingView(context).apply {
                this.cornerRadiusPx = cornerRadiusPx
                this.isGlowEnabled = animationsEnabled
                this.themeStyle = themeStyle
                this.elevation = 30f
                val totalGlowMargin = (this.glowMarginPx * 2).toInt()
                this.layoutParams = FrameLayout.LayoutParams(
                    cardWidthPx + totalGlowMargin,
                    cardHeightPx + totalGlowMargin
                )
            }
            gridView.floatingRingView = focusRing
            container.gridView = gridView
            container.floatingRingView = focusRing
            container.addView(gridView)
            container.addView(focusRing)

            gridView.restoredPosition = initialFocusPosition
            if (!isPendingFocus && initialFocusPosition in items.indices) {
                if (initialFocusPosition > 0) {
                    gridView.setSelectedPosition(initialFocusPosition)
                }
                lastAppliedInitialPosition = initialFocusPosition
                initialScrollApplied = true
            }
            container
        },
        update = { container ->
            container.isDetaching = false
            val gridView = container.gridView
            gridView.isDetaching = false
            val focusRing = container.floatingRingView
            if (gridView.configuredRowHeightPx != totalRowHeightPx) {
                val lp = container.layoutParams
                if (lp != null && lp.height != totalRowHeightPx) {
                    lp.height = totalRowHeightPx
                    container.layoutParams = lp
                }
                val gridLp = gridView.layoutParams
                if (gridLp != null && gridLp.height != totalRowHeightPx) {
                    gridLp.height = totalRowHeightPx
                    gridView.layoutParams = gridLp
                }
                gridView.setRowHeight(totalRowHeightPx)
                gridView.configuredRowHeightPx = totalRowHeightPx
            }
            if (focusRing.cornerRadiusPx != cornerRadiusPx) {
                focusRing.cornerRadiusPx = cornerRadiusPx
            }
            if (focusRing.isGlowEnabled != animationsEnabled) {
                focusRing.isGlowEnabled = animationsEnabled
            }
            if (focusRing.themeStyle != themeStyle) {
                focusRing.themeStyle = themeStyle
            }
            if (gridView.configuredStartPaddingPx != startPaddingPx ||
                gridView.configuredVerticalPaddingPx != verticalPaddingPx
            ) {
                gridView.windowAlignmentOffset = startPaddingPx
                gridView.setPaddingRelative(startPaddingPx, verticalPaddingPx, startPaddingPx, verticalPaddingPx)
                gridView.configuredStartPaddingPx = startPaddingPx
                gridView.configuredVerticalPaddingPx = verticalPaddingPx
            }
            if (gridView.configuredSpacingPx != spacingPx) {
                gridView.setItemSpacing(spacingPx)
                gridView.configuredSpacingPx = spacingPx
            }
            localGridViewRef = gridView
            gridView.onNavigateDown = onNavigateDown
            gridView.onNavigateUp = onNavigateUp
            gridView.onNavigateBack = onNavigateBack
            gridView.onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem
            gridView.onVerticalHoldScroll = onVerticalHoldScroll
            gridView.onVerticalHoldRelease = onVerticalHoldRelease
            gridView.isVerticalGlideActive = isVerticalGlideActive
            gridView.onHorizontalInterrupt = onHorizontalInterrupt
            gridView.onBackdropInteraction = onBackdropInteraction
            gridView.canTakeFocus = canTakeFocus
            if (gridView.configuredAnimationsEnabled != animationsEnabled ||
                gridView.configuredBackdropExpandEnabled != backdropExpandEnabled
            ) {
                gridView.animationsEnabled = animationsEnabled
                gridView.setAnimateChildLayout(animationsEnabled && !backdropExpandEnabled)
                gridView.setHasFixedSize(!backdropExpandEnabled)
                gridView.configuredAnimationsEnabled = animationsEnabled
                gridView.configuredBackdropExpandEnabled = backdropExpandEnabled
            }
            adapter.animationsEnabled = { animationsEnabled }
            adapter.onItemFocused = onItemFocused
            adapter.onItemClick = onItemClick
            adapter.onItemLongClick = onItemLongClick
            adapter.onLoadMore = onLoadMore
            adapter.isCatalogItemWatched = isCatalogItemWatched
            adapter.lastWatchedMaskHash = watchedMaskHash
            if (gridView.adapter !== adapter) {
                gridView.adapter = adapter
            }
            recycledViewPool?.let {
                if (gridView.recycledViewPool !== it) {
                    gridView.setRecycledViewPool(it)
                }
            }
            adapter.updateConfig(
                newWidthPx = cardWidthPx,
                newHeightPx = cardHeightPx,
                newRadiusPx = cornerRadiusPx,
                newShowLabels = showLabels,
                newThemeStyle = themeStyle,
                newBlurUnwatchedEpisodes = blurUnwatchedEpisodes
            )
            rowGrids?.put(rowKey, gridView)
            adapter.submitItems(items)
            if (initialFocusPosition in items.indices) {
                gridView.restoredPosition = initialFocusPosition
                if (initialFocusPosition != lastAppliedInitialPosition &&
                    !gridView.hasFocus() &&
                    initialFocusPosition in 0 until adapter.itemCount
                ) {
                    gridView.setSelectedPosition(initialFocusPosition)
                    lastAppliedInitialPosition = initialFocusPosition
                    initialScrollApplied = true
                }
            }
        },
        onRelease = { container ->
            adapter.collapseExpandedCardImmediate()
            val gridView = container.gridView
            if (localGridViewRef === gridView) {
                localGridViewRef = null
            }
            gridView.floatingRingView?.hideRing(true)
            if (rowGrids?.get(rowKey) === gridView) {
                rowGrids.remove(rowKey)
            }
            // Detach adapter on row disposal so all cached ViewHolders return to the shared pool
            gridView.adapter = null
        }
    )
}
