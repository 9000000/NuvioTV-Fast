package com.nuvio.tv.ui.leanback

import android.view.ViewGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.recyclerview.widget.RecyclerView
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.container.InteropVerticalGridView
import com.nuvio.tv.ui.leanback.holder.LeanbackBaseViewHolder
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.leanback.pool.LocalLeanbackRecycledViewPool

/**
 * Universal Composable Bridge for Android Leanback Native 2D Vertical Grids.
 * Replaces heavy Compose LazyVerticalGrid with 60fps hardware-accelerated VerticalGridView.
 */
@Composable
fun LeanbackNativeGrid(
    gridKey: String,
    items: List<LeanbackCardItem>,
    numColumns: Int = 5,
    cardWidth: Dp,
    cardHeight: Dp,
    cornerRadius: Dp,
    showLabels: Boolean = true,
    animationsEnabled: Boolean = true,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    horizontalSpacing: Dp = 16.dp,
    verticalSpacing: Dp = 20.dp,
    onItemFocused: (Int, LeanbackCardItem) -> Unit = { _, _ -> },
    onItemClick: (LeanbackCardItem) -> Unit,
    onItemLongClick: (LeanbackCardItem) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onNavigateUpOnFirstRow: () -> Boolean = { false },
    onNavigateDownOnLastRow: () -> Boolean = { false },
    onNavigateLeftOnFirstColumn: () -> Boolean = { false },
    onNavigateBack: () -> Boolean = { false },
    recycledViewPool: RecyclerView.RecycledViewPool? = null,
    initialFocusPosition: Int = 0,
    restoreFocusNonce: Int = 0,
    onGridCreated: ((InteropVerticalGridView) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val cardWidthPx = with(density) { cardWidth.roundToPx() }
    val cardHeightPx = with(density) { cardHeight.roundToPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val startPaddingPx = with(density) { paddingValues.calculateLeftPadding(layoutDirection).roundToPx() }
    val endPaddingPx = with(density) { paddingValues.calculateRightPadding(layoutDirection).roundToPx() }
    val topPaddingPx = with(density) { paddingValues.calculateTopPadding().roundToPx() }
    val bottomPaddingPx = with(density) { paddingValues.calculateBottomPadding().roundToPx() }
    val verticalSpacingPx = with(density) { verticalSpacing.roundToPx() }

    val effectivePool = recycledViewPool ?: LocalLeanbackRecycledViewPool.current

    val adapter = remember(gridKey) {
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
            onNavigateDown = onNavigateDownOnLastRow,
            onNavigateUp = onNavigateUpOnFirstRow,
            onNavigateLeft = onNavigateLeftOnFirstColumn,
            onNavigateBack = onNavigateBack
        )
    }

    var gridViewRef by remember(gridKey) { mutableStateOf<InteropVerticalGridView?>(null) }

    fun executeGridFocus(grid: InteropVerticalGridView, targetIdx: Int) {
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

    var initialFocusDone by remember(gridKey) { mutableStateOf(false) }

    LaunchedEffect(gridKey) {
        initialFocusDone = false
    }

    LaunchedEffect(restoreFocusNonce) {
        if (restoreFocusNonce > 0) {
            val grid = gridViewRef
            if (grid != null && grid.isAttachedToWindow && initialFocusPosition in items.indices) {
                executeGridFocus(grid, initialFocusPosition)
            }
        }
    }

    LaunchedEffect(items.isNotEmpty()) {
        if (!initialFocusDone && items.isNotEmpty()) {
            val grid = gridViewRef
            if (grid != null && grid.isAttachedToWindow && initialFocusPosition in items.indices) {
                executeGridFocus(grid, initialFocusPosition)
                initialFocusDone = true
            }
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { clip = false },
        factory = { context ->
            InteropVerticalGridView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                this.numColumnsCount = numColumns
                setNumColumns(numColumns)
                windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
                windowAlignmentOffset = topPaddingPx
                windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
                itemAlignmentOffset = 0
                itemAlignmentOffsetPercent = 0f
                setItemSpacing(verticalSpacingPx)
                clipToPadding = false
                clipChildren = false
                setPadding(startPaddingPx, topPaddingPx, endPaddingPx, bottomPaddingPx)
                this.adapter = adapter
                this.onNavigateUpOnFirstRow = onNavigateUpOnFirstRow
                this.onNavigateDownOnLastRow = onNavigateDownOnLastRow
                this.onNavigateLeftOnFirstColumn = onNavigateLeftOnFirstColumn
                this.onNavigateBack = onNavigateBack
                this.animationsEnabled = animationsEnabled
                setHasFixedSize(true)
                setItemViewCacheSize(12)
                effectivePool?.let { setRecycledViewPool(it) }
                gridViewRef = this
                onGridCreated?.invoke(this)

                if (!initialFocusDone && initialFocusPosition in items.indices) {
                    executeGridFocus(this, initialFocusPosition)
                    initialFocusDone = true
                }
            }
        },
        update = { gridView ->
            gridViewRef = gridView
            gridView.numColumnsCount = numColumns
            gridView.setNumColumns(numColumns)
            gridView.onNavigateUpOnFirstRow = onNavigateUpOnFirstRow
            gridView.onNavigateDownOnLastRow = onNavigateDownOnLastRow
            gridView.onNavigateLeftOnFirstColumn = onNavigateLeftOnFirstColumn
            gridView.onNavigateBack = onNavigateBack
            gridView.animationsEnabled = animationsEnabled
            gridView.setAnimateChildLayout(animationsEnabled)

            adapter.animationsEnabled = { animationsEnabled }
            adapter.onItemFocused = onItemFocused
            adapter.onItemClick = onItemClick
            adapter.onItemLongClick = onItemLongClick
            adapter.onLoadMore = onLoadMore
            adapter.onNavigateDown = onNavigateDownOnLastRow
            adapter.onNavigateUp = onNavigateUpOnFirstRow
            adapter.onNavigateLeft = onNavigateLeftOnFirstColumn
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
            adapter.submitItems(items)
        }
    )
}

