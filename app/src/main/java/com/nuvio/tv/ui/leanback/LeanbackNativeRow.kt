package com.nuvio.tv.ui.leanback

import android.view.ViewGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.RecyclerView
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.container.InteropHorizontalGridView
import com.nuvio.tv.ui.leanback.holder.LeanbackBaseViewHolder
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.leanback.pool.LocalLeanbackRecycledViewPool

/**
 * Universal Composable Bridge for Android Leanback Native Horizontal Rows.
 * Replaces heavy Compose LazyRow with 60fps hardware-accelerated HorizontalGridView.
 */
@Composable
fun LeanbackNativeRow(
    rowKey: String,
    items: List<LeanbackCardItem>,
    cardWidth: Dp,
    cardHeight: Dp,
    cornerRadius: Dp,
    showLabels: Boolean = true,
    animationsEnabled: Boolean = true,
    startPadding: Dp = 48.dp,
    spacing: Dp = 12.dp,
    onItemFocused: (Int, LeanbackCardItem) -> Unit = { _, _ -> },
    onItemClick: (LeanbackCardItem) -> Unit,
    onItemLongClick: (LeanbackCardItem) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onNavigateDown: () -> Boolean = { false },
    onNavigateUp: () -> Boolean = { false },
    onNavigateLeftOnFirstItem: () -> Boolean = { false },
    onNavigateBack: () -> Boolean = { false },
    rowGrids: MutableMap<String, HorizontalGridView>? = null,
    isPendingFocus: Boolean = false,
    pendingFocusIndex: Int = 0,
    initialFocusPosition: Int = 0,
    pendingFocusNonce: Int = 0,
    entryFocusRequester: FocusRequester? = null,
    entryFocusNonce: Int = 0,
    entryFocusIndex: Int = 0,
    onClearPendingFocus: () -> Unit = {},
    recycledViewPool: RecyclerView.RecycledViewPool? = null,
    paddingValues: PaddingValues? = null,
    itemSpacing: Dp? = null,
    isFocusEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val effectiveStartPadding = paddingValues?.calculateLeftPadding(layoutDirection) ?: startPadding
    val effectiveSpacing = itemSpacing ?: spacing

    val cardWidthPx = with(density) { cardWidth.roundToPx() }
    val cardHeightPx = with(density) { cardHeight.roundToPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val startPaddingPx = with(density) { effectiveStartPadding.roundToPx() }
    val spacingPx = with(density) { effectiveSpacing.roundToPx() }
    val verticalPaddingDp = 34.dp
    val verticalPaddingPx = with(density) { verticalPaddingDp.roundToPx() }
    val labelHeightDp = if (showLabels) 44.dp else 0.dp
    val labelHeightPx = with(density) { labelHeightDp.roundToPx() }
    val totalRowHeightPx = cardHeightPx + labelHeightPx + (verticalPaddingPx * 2)
    val totalRowHeightDp = cardHeight + labelHeightDp + (verticalPaddingDp * 2)

    val effectivePool = recycledViewPool ?: LocalLeanbackRecycledViewPool.current

    val adapter = remember(rowKey) {
        LeanbackUniversalAdapter(
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
            onNavigateLeft = onNavigateLeftOnFirstItem,
            onNavigateBack = onNavigateBack
        )
    }

    DisposableEffect(rowKey) {
        onDispose {
            rowGrids?.remove(rowKey)
        }
    }

    fun executeRowFocus(grid: InteropHorizontalGridView, targetIdx: Int) {
        val action = Runnable {
            if (!grid.isAttachedToWindow) return@Runnable
            if (animationsEnabled) {
                grid.setSelectedPositionSmooth(targetIdx)
            } else {
                grid.setSelectedPosition(targetIdx)
            }
            grid.requestFocus()
            val vh = grid.findViewHolderForAdapterPosition(targetIdx)
            if (vh != null) {
                vh.itemView.requestFocus()
                (vh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
            } else {
                grid.post {
                    if (grid.isAttachedToWindow) {
                        val deferredVh = grid.findViewHolderForAdapterPosition(targetIdx)
                        deferredVh?.itemView?.requestFocus()
                        (deferredVh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                    }
                }
            }
        }
        if (grid.isAttachedToWindow) {
            grid.post(action)
        } else {
            grid.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: android.view.View) {
                    grid.removeOnAttachStateChangeListener(this)
                    grid.post(action)
                }
                override fun onViewDetachedFromWindow(v: android.view.View) {}
            })
        }
    }

    var localGridViewRef by remember(rowKey) { mutableStateOf<InteropHorizontalGridView?>(null) }
    var initialScrollApplied by remember(rowKey) { mutableStateOf(false) }

    LaunchedEffect(isPendingFocus, pendingFocusNonce, items.isNotEmpty()) {
        if (isPendingFocus && items.isNotEmpty()) {
            val safeIdx = pendingFocusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val gridView = localGridViewRef ?: (rowGrids?.get(rowKey) as? InteropHorizontalGridView)
            if (gridView != null && gridView.isAttachedToWindow) {
                executeRowFocus(gridView, safeIdx)
                onClearPendingFocus()
            }
        }
    }

    LaunchedEffect(initialFocusPosition, items.isNotEmpty()) {
        if (!initialScrollApplied && !isPendingFocus && items.isNotEmpty() && initialFocusPosition in items.indices) {
            val gridView = localGridViewRef ?: (rowGrids?.get(rowKey) as? InteropHorizontalGridView)
            if (gridView != null && gridView.isAttachedToWindow && !gridView.hasFocus()) {
                if (initialFocusPosition > 0) {
                    gridView.setSelectedPosition(initialFocusPosition)
                }
                initialScrollApplied = true
            }
        }
    }

    LaunchedEffect(entryFocusNonce, entryFocusIndex, rowKey) {
        if (entryFocusNonce <= 0) return@LaunchedEffect
        repeat(8) {
            withFrameNanos { }
            val gridView = localGridViewRef ?: (rowGrids?.get(rowKey) as? InteropHorizontalGridView)
            if (gridView != null && gridView.isAttachedToWindow && items.isNotEmpty()) {
                executeRowFocus(
                    grid = gridView,
                    targetIdx = entryFocusIndex.coerceIn(0, items.lastIndex)
                )
                return@LaunchedEffect
            }
        }
    }

    AndroidView(
        modifier = modifier
            .then(
                if (!isFocusEnabled) {
                    Modifier.focusProperties { canFocus = false }
                } else if (entryFocusRequester != null) {
                    Modifier.focusRequester(entryFocusRequester)
                } else {
                    Modifier
                }
            )
            .height(totalRowHeightDp)
            .fillMaxWidth()
            .graphicsLayer { clip = false },
        factory = { context ->
            InteropHorizontalGridView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    totalRowHeightPx
                )
                setRowHeight(totalRowHeightPx)
                setNumRows(1)
                windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
                windowAlignmentOffset = startPaddingPx
                windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
                itemAlignmentOffset = 0
                itemAlignmentOffsetPercent = 0f
                setItemSpacing(spacingPx)
                clipToPadding = false
                clipChildren = false
                setPadding(startPaddingPx, verticalPaddingPx, startPaddingPx, verticalPaddingPx)
                this.adapter = adapter
                this.onNavigateDown = onNavigateDown
                this.onNavigateUp = onNavigateUp
                this.onNavigateBack = onNavigateBack
                this.onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem
                this.animationsEnabled = animationsEnabled
                setHasFixedSize(true)
                setItemViewCacheSize(8)
                effectivePool?.let { setRecycledViewPool(it) }
                if (!isFocusEnabled) {
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    isFocusable = false
                    isFocusableInTouchMode = false
                } else {
                    descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
                localGridViewRef = this
                rowGrids?.put(rowKey, this)
                addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: android.view.View) {
                        adapter.flushPendingUpdates()
                    }
                    override fun onViewDetachedFromWindow(v: android.view.View) {}
                })
                if (isPendingFocus && items.isNotEmpty()) {
                    val targetIdx = pendingFocusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                    executeRowFocus(this, targetIdx)
                    onClearPendingFocus()
                } else if (initialFocusPosition in items.indices && initialFocusPosition > 0) {
                    setSelectedPosition(initialFocusPosition)
                    initialScrollApplied = true
                }
            }
        },
        update = { gridView ->
            localGridViewRef = gridView
            if (!isFocusEnabled) {
                gridView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                gridView.isFocusable = false
                gridView.isFocusableInTouchMode = false
            } else {
                gridView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                gridView.isFocusable = true
                gridView.isFocusableInTouchMode = true
            }
            val lp = gridView.layoutParams
            if (lp != null && lp.height != totalRowHeightPx) {
                lp.height = totalRowHeightPx
                gridView.layoutParams = lp
            }
            gridView.setRowHeight(totalRowHeightPx)
            gridView.windowAlignmentOffset = startPaddingPx
            gridView.setItemSpacing(spacingPx)
            if (gridView.paddingLeft != startPaddingPx ||
                gridView.paddingTop != verticalPaddingPx ||
                gridView.paddingRight != startPaddingPx ||
                gridView.paddingBottom != verticalPaddingPx
            ) {
                gridView.setPadding(startPaddingPx, verticalPaddingPx, startPaddingPx, verticalPaddingPx)
            }
            gridView.onNavigateDown = onNavigateDown
            gridView.onNavigateUp = onNavigateUp
            gridView.onNavigateBack = onNavigateBack
            gridView.onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem
            gridView.animationsEnabled = animationsEnabled
            gridView.setAnimateChildLayout(animationsEnabled)
            adapter.animationsEnabled = { animationsEnabled }
            adapter.onItemFocused = onItemFocused
            adapter.onItemClick = onItemClick
            adapter.onItemLongClick = onItemLongClick
            adapter.onLoadMore = onLoadMore
            adapter.onNavigateDown = onNavigateDown
            adapter.onNavigateUp = onNavigateUp
            adapter.onNavigateLeft = onNavigateLeftOnFirstItem
            adapter.onNavigateBack = onNavigateBack
            if (gridView.adapter !== adapter) {
                gridView.adapter = adapter
            }
            effectivePool?.let {
                if (gridView.recycledViewPool !== it) {
                    gridView.setRecycledViewPool(it)
                }
            }
            adapter.updateConfig(cardWidthPx, cardHeightPx, cornerRadiusPx, showLabels)
            rowGrids?.put(rowKey, gridView)
            adapter.submitItems(items)
        }
    )
}

