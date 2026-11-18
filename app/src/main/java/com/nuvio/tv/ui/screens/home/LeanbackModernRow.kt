package com.nuvio.tv.ui.screens.home

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.RecyclerView
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
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cardWidthPx = with(density) { cardWidth.roundToPx() }
    val cardHeightPx = with(density) { cardHeight.roundToPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val startPaddingPx = with(density) { startPadding.roundToPx() }
    val spacingPx = with(density) { spacing.roundToPx() }
    val verticalPaddingDp = 6.dp
    val verticalPaddingPx = with(density) { verticalPaddingDp.roundToPx() }
    val labelHeightPx = if (showLabels) with(density) { 26.dp.roundToPx() } else 0
    val totalRowHeightPx = cardHeightPx + labelHeightPx + (verticalPaddingPx * 2)

    val labelHeightDp = if (showLabels) 26.dp else 0.dp
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
            onNavigateDown = onNavigateDown,
            onNavigateUp = onNavigateUp,
            onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem,
            onNavigateBack = onNavigateBack
        )
    }

    DisposableEffect(rowKey) {
        onDispose {
            rowGrids?.remove(rowKey)
        }
    }

    fun executeRowFocus(grid: ModernInteropHorizontalGridView, targetIdx: Int) {
        grid.post {
            if (!grid.isAttachedToWindow || !grid.mayTakeFocus()) return@post
            grid.setSelectedPosition(targetIdx)
            grid.requestFocus()
            grid.ensureChildFocusAndRing(targetPos = targetIdx, animated = false)
        }
    }

    var localGridViewRef by remember(rowKey) { mutableStateOf<ModernInteropHorizontalGridView?>(null) }
    var initialScrollApplied by remember(rowKey) { mutableStateOf(false) }

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
            val gridView = localGridViewRef ?: (rowGrids?.get(rowKey) as? ModernInteropHorizontalGridView)
            if (gridView != null && gridView.isAttachedToWindow && !gridView.hasFocus()) {
                if (initialFocusPosition > 0) {
                    gridView.setSelectedPosition(initialFocusPosition)
                }
                initialScrollApplied = true
            }
        }
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
                this.canTakeFocus = canTakeFocus
                this.animationsEnabled = animationsEnabled
                setAnimateChildLayout(animationsEnabled)
                configuredAnimationsEnabled = animationsEnabled
                setHasFixedSize(true)
                setItemViewCacheSize(8)
                recycledViewPool?.let { setRecycledViewPool(it) }
                localGridViewRef = this
                rowGrids?.put(rowKey, this)
            }
            val focusRing = ModernRowFloatingFocusRingView(context).apply {
                this.cornerRadiusPx = cornerRadiusPx
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
            if (!isPendingFocus && initialFocusPosition in items.indices && initialFocusPosition > 0) {
                gridView.setSelectedPosition(initialFocusPosition)
                initialScrollApplied = true
            }
            container
        },
        update = { container ->
            val gridView = container.gridView
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
            if (initialFocusPosition in items.indices) {
                gridView.restoredPosition = initialFocusPosition
                if (initialFocusPosition > 0 && !gridView.hasFocus() && gridView.selectedPosition != initialFocusPosition) {
                    gridView.setSelectedPosition(initialFocusPosition)
                }
            }
            gridView.onNavigateDown = onNavigateDown
            gridView.onNavigateUp = onNavigateUp
            gridView.onNavigateBack = onNavigateBack
            gridView.onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem
            gridView.onVerticalHoldScroll = onVerticalHoldScroll
            gridView.onVerticalHoldRelease = onVerticalHoldRelease
            gridView.isVerticalGlideActive = isVerticalGlideActive
            gridView.onHorizontalInterrupt = onHorizontalInterrupt
            gridView.canTakeFocus = canTakeFocus
            if (gridView.configuredAnimationsEnabled != animationsEnabled) {
                gridView.animationsEnabled = animationsEnabled
                gridView.setAnimateChildLayout(animationsEnabled)
                gridView.configuredAnimationsEnabled = animationsEnabled
            }
            adapter.animationsEnabled = { animationsEnabled }
            adapter.onItemFocused = onItemFocused
            adapter.onItemClick = onItemClick
            adapter.onItemLongClick = onItemLongClick
            adapter.onLoadMore = onLoadMore
            adapter.onNavigateDown = onNavigateDown
            adapter.onNavigateUp = onNavigateUp
            adapter.onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem
            adapter.onNavigateBack = onNavigateBack
            if (gridView.adapter !== adapter) {
                gridView.adapter = adapter
            }
            recycledViewPool?.let {
                if (gridView.recycledViewPool !== it) {
                    gridView.setRecycledViewPool(it)
                }
            }
            adapter.updateConfig(cardWidthPx, cardHeightPx, cornerRadiusPx, showLabels)
            rowGrids?.put(rowKey, gridView)
            adapter.submitItems(items)
        },
        onRelease = { container ->
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
