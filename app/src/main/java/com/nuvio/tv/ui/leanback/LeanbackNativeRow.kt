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
import androidx.compose.ui.Modifier
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
    onClearPendingFocus: () -> Unit = {},
    recycledViewPool: RecyclerView.RecycledViewPool? = null,
    paddingValues: PaddingValues? = null,
    itemSpacing: Dp? = null,
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
    val labelHeightPx = if (showLabels) with(density) { 26.dp.roundToPx() } else 0
    val totalRowHeightPx = cardHeightPx + labelHeightPx + (verticalPaddingPx * 2)

    val labelHeightDp = if (showLabels) 26.dp else 0.dp
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
        grid.post {
            if (!grid.isAttachedToWindow) return@post
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
    }

    var localGridViewRef by remember(rowKey) { mutableStateOf<InteropHorizontalGridView?>(null) }

    LaunchedEffect(isPendingFocus, pendingFocusNonce) {
        val targetIdx = if (isPendingFocus) pendingFocusIndex else initialFocusPosition
        if (isPendingFocus && items.isNotEmpty()) {
            val safeIdx = targetIdx.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val gridView = localGridViewRef ?: (rowGrids?.get(rowKey) as? InteropHorizontalGridView)
            if (gridView != null && gridView.isAttachedToWindow) {
                executeRowFocus(gridView, safeIdx)
                onClearPendingFocus()
            }
        }
    }

    AndroidView(
        modifier = modifier
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
                localGridViewRef = this
                rowGrids?.put(rowKey, this)
                if (isPendingFocus && items.isNotEmpty()) {
                    val targetIdx = pendingFocusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                    executeRowFocus(this, targetIdx)
                    onClearPendingFocus()
                } else if (initialFocusPosition in items.indices) {
                    executeRowFocus(this, initialFocusPosition)
                }
            }
        },
        update = { gridView ->
            localGridViewRef = gridView
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
            if (isPendingFocus && items.isNotEmpty()) {
                val targetIdx = pendingFocusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                executeRowFocus(gridView, targetIdx)
                onClearPendingFocus()
            }
        }
    )
}

