package com.nuvio.tv.ui.leanback

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import com.nuvio.tv.ui.leanback.core.LeanbackCardExpandSpec
import com.nuvio.tv.ui.leanback.core.LeanbackExpandedTrailerHost
import com.nuvio.tv.ui.leanback.core.rememberLeanbackThemeStyle
import com.nuvio.tv.ui.leanback.holder.LeanbackBaseViewHolder
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.leanback.pool.LocalLeanbackRecycledViewPool
import kotlinx.coroutines.awaitCancellation

/**
 * Universal Composable Bridge for Android Leanback Native Horizontal Rows.
 * Replaces Compose LazyRow with 60fps hardware-accelerated HorizontalGridView,
 * lifecycle-safe attach listeners, and automatic ViewHolder pool release on disposal.
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
    verticalPadding: Dp = 24.dp,
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
    onClearEntryFocus: () -> Unit = {},
    onClearPendingFocus: () -> Unit = {},
    recycledViewPool: RecyclerView.RecycledViewPool? = null,
    paddingValues: PaddingValues? = null,
    itemSpacing: Dp? = null,
    isFocusEnabled: Boolean = true,
    focusedPosterBackdropExpandEnabled: Boolean = false,
    expandedItemKeyState: State<String?>? = null,
    alwaysShowLandscapeClearlogo: Boolean = false,
    playTrailerInExpandedCard: Boolean = false,
    trailerMuted: Boolean = true,
    expandedTrailerUrlProvider: () -> String? = { null },
    expandedTrailerAudioUrlProvider: () -> String? = { null },
    expandedTrailerHost: LeanbackExpandedTrailerHost? = null,
    onBackdropInteraction: (() -> Unit)? = null,
    onExpandedTrailerEnded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val effectiveStartPadding = paddingValues?.calculateStartPadding(layoutDirection) ?: startPadding
    val effectiveEndPadding = paddingValues?.calculateEndPadding(layoutDirection) ?: effectiveStartPadding
    val effectiveSpacing = itemSpacing ?: spacing

    val cardWidthPx = with(density) { cardWidth.roundToPx() }
    val cardHeightPx = with(density) { cardHeight.roundToPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val startPaddingPx = with(density) { effectiveStartPadding.roundToPx() }
    val endPaddingPx = with(density) { effectiveEndPadding.roundToPx() }
    val spacingPx = with(density) { effectiveSpacing.roundToPx() }
    val verticalPaddingPx = with(density) { verticalPadding.roundToPx() }
    val hasEpisodeItems = items.firstOrNull() is LeanbackCardItem.Episode
    val labelHeightDp = if (showLabels && !hasEpisodeItems) {
        val baseLabelHeightDp = if (focusedPosterBackdropExpandEnabled) {
            LeanbackCardExpandSpec.EXPANDED_LABEL_MIN_HEIGHT_DP.dp
        } else {
            44.dp
        }
        baseLabelHeightDp * density.fontScale.coerceIn(1f, 1.4f)
    } else {
        0.dp
    }
    val labelHeightPx = with(density) { labelHeightDp.roundToPx() }
    val totalRowHeightPx = cardHeightPx + labelHeightPx + (verticalPaddingPx * 2)
    val totalRowHeightDp = cardHeight + labelHeightDp + (verticalPadding * 2)

    val effectivePool = recycledViewPool ?: LocalLeanbackRecycledViewPool.current
    val themeStyle = rememberLeanbackThemeStyle()

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
            themeStyle = themeStyle
        )
    }

    DisposableEffect(rowKey) {
        onDispose {
            adapter.collapseExpandedCardImmediate()
            rowGrids?.remove(rowKey)
        }
    }

    val latestExpandedTrailerUrlProvider by rememberUpdatedState(expandedTrailerUrlProvider)
    val latestExpandedTrailerAudioUrlProvider by rememberUpdatedState(expandedTrailerAudioUrlProvider)

    LaunchedEffect(
        rowKey,
        adapter,
        focusedPosterBackdropExpandEnabled,
        alwaysShowLandscapeClearlogo,
        playTrailerInExpandedCard,
        trailerMuted,
        expandedTrailerHost,
        expandedItemKeyState
    ) {
        adapter.backdropExpandEnabled = focusedPosterBackdropExpandEnabled
        adapter.alwaysShowLandscapeClearlogo = alwaysShowLandscapeClearlogo
        adapter.playTrailerInExpandedCard = playTrailerInExpandedCard
        adapter.trailerMuted = trailerMuted
        adapter.expandedTrailerHost = if (playTrailerInExpandedCard) expandedTrailerHost else null
        adapter.onExpandedTrailerEnded = onExpandedTrailerEnded

        if (!focusedPosterBackdropExpandEnabled || expandedItemKeyState == null) {
            adapter.collapseExpandedCardImmediate()
            return@LaunchedEffect
        }

        snapshotFlow {
            val key = expandedItemKeyState.value
            val trailerUrl = if (key != null && playTrailerInExpandedCard) {
                latestExpandedTrailerUrlProvider()
            } else {
                null
            }
            val trailerAudioUrl = if (key != null && playTrailerInExpandedCard) {
                latestExpandedTrailerAudioUrlProvider()
            } else {
                null
            }
            Triple(key, trailerUrl, trailerAudioUrl)
        }.collect { (key, trailerUrl, trailerAudioUrl) ->
            adapter.updateExpandedCardState(
                expandedKey = key,
                trailerUrl = trailerUrl,
                trailerAudioUrl = trailerAudioUrl
            )
        }
    }

    fun executeAttachedRowFocus(grid: InteropHorizontalGridView, targetIdx: Int) {
        grid.restoredPosition = targetIdx
        grid.post {
            if (!grid.isAttachedToWindow || !grid.mayTakeFocus()) return@post
            if (animationsEnabled) {
                grid.setSelectedPositionSmooth(targetIdx)
            } else {
                grid.setSelectedPosition(targetIdx)
            }
            val composeEntered = if (entryFocusRequester != null) {
                runCatching {
                    entryFocusRequester.requestFocus()
                    true
                }.getOrDefault(false)
            } else {
                false
            }
            if (!composeEntered || !grid.hasFocus()) {
                grid.requestFocus()
            }
            val vh = grid.findViewHolderForAdapterPosition(targetIdx)
            if (vh != null) {
                if (!vh.itemView.hasFocus()) {
                    vh.itemView.requestFocus()
                }
                (vh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
            } else {
                grid.post {
                    if (grid.isAttachedToWindow && grid.mayTakeFocus()) {
                        val deferredVh = grid.findViewHolderForAdapterPosition(targetIdx)
                        deferredVh?.itemView?.requestFocus()
                        (deferredVh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                    }
                }
            }
        }
    }

    var localGridViewRef by remember(rowKey) { mutableStateOf<InteropHorizontalGridView?>(null) }
    var initialScrollApplied by remember(rowKey) { mutableStateOf(false) }
    var lastAppliedInitialPosition by remember(rowKey) { mutableIntStateOf(-1) }

    LaunchedEffect(isPendingFocus, pendingFocusNonce, items.isNotEmpty(), localGridViewRef) {
        if (!isPendingFocus || items.isEmpty()) return@LaunchedEffect
        val safeIdx = pendingFocusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val gridView = localGridViewRef ?: (rowGrids?.get(rowKey) as? InteropHorizontalGridView) ?: return@LaunchedEffect
        if (gridView.isAttachedToWindow) {
            executeAttachedRowFocus(gridView, safeIdx)
            onClearPendingFocus()
        } else {
            val attachListener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    v.removeOnAttachStateChangeListener(this)
                    executeAttachedRowFocus(gridView, safeIdx)
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

    LaunchedEffect(initialFocusPosition, items.isNotEmpty(), localGridViewRef) {
        if (!initialScrollApplied && !isPendingFocus && items.isNotEmpty() && initialFocusPosition in items.indices) {
            val gridView = localGridViewRef ?: (rowGrids?.get(rowKey) as? InteropHorizontalGridView) ?: return@LaunchedEffect
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

    LaunchedEffect(entryFocusNonce, rowKey) {
        if (entryFocusNonce <= 0) return@LaunchedEffect
        repeat(8) {
            withFrameNanos { }
            val gridView = localGridViewRef ?: (rowGrids?.get(rowKey) as? InteropHorizontalGridView)
            if (gridView != null && gridView.isAttachedToWindow && items.isNotEmpty()) {
                executeAttachedRowFocus(
                    grid = gridView,
                    targetIdx = entryFocusIndex.coerceIn(0, items.lastIndex)
                )
                onClearEntryFocus()
                return@LaunchedEffect
            }
        }
        onClearEntryFocus()
    }

    // Never attach focusProperties { canFocus = true } to AndroidView: Compose's internal
    // FocusGroupPropertiesNode intentionally sets canFocus = false on the outer FocusTargetNode
    // so focus enters the inner FocusTargetInteropElement (InteropHorizontalGridView). Overriding
    // canFocus = true breaks AndroidView focus interop and causes horizontal D-pad navigation to
    // lose focus back to the top Compose focusable row.
    AndroidView(
        modifier = modifier
            .then(
                if (entryFocusRequester != null) {
                    Modifier.focusRequester(entryFocusRequester)
                } else {
                    Modifier
                }
            )
            .then(
                if (!isFocusEnabled) {
                    Modifier.focusProperties { canFocus = false }
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
                setPaddingRelative(startPaddingPx, verticalPaddingPx, endPaddingPx, verticalPaddingPx)
                configuredStartPaddingPx = startPaddingPx
                configuredVerticalPaddingPx = verticalPaddingPx
                adapter.submitItems(items)
                this.adapter = adapter
                this.onNavigateDown = onNavigateDown
                this.onNavigateUp = onNavigateUp
                this.onNavigateBack = onNavigateBack
                this.onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem
                this.onBackdropInteraction = onBackdropInteraction
                this.animationsEnabled = animationsEnabled
                setAnimateChildLayout(animationsEnabled && !focusedPosterBackdropExpandEnabled)
                configuredAnimationsEnabled = animationsEnabled
                configuredBackdropExpandEnabled = focusedPosterBackdropExpandEnabled
                setHasFixedSize(!focusedPosterBackdropExpandEnabled)
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
                configuredFocusEnabled = isFocusEnabled
                localGridViewRef = this
                rowGrids?.put(rowKey, this)
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        adapter.flushPendingUpdates()
                    }
                    override fun onViewDetachedFromWindow(v: View) {}
                })
                restoredPosition = initialFocusPosition
                if (!isPendingFocus && initialFocusPosition in items.indices) {
                    if (initialFocusPosition > 0) {
                        setSelectedPosition(initialFocusPosition)
                    }
                    lastAppliedInitialPosition = initialFocusPosition
                    initialScrollApplied = true
                }
            }
        },
        update = { gridView ->
            localGridViewRef = gridView
            if (gridView.configuredFocusEnabled != isFocusEnabled) {
                if (!isFocusEnabled) {
                    gridView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    gridView.isFocusable = false
                    gridView.isFocusableInTouchMode = false
                } else {
                    gridView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                    gridView.isFocusable = true
                    gridView.isFocusableInTouchMode = true
                }
                gridView.configuredFocusEnabled = isFocusEnabled
            }
            if (gridView.configuredRowHeightPx != totalRowHeightPx) {
                val lp = gridView.layoutParams
                if (lp != null && lp.height != totalRowHeightPx) {
                    lp.height = totalRowHeightPx
                    gridView.layoutParams = lp
                }
                gridView.setRowHeight(totalRowHeightPx)
                gridView.configuredRowHeightPx = totalRowHeightPx
            }
            if (gridView.configuredStartPaddingPx != startPaddingPx ||
                gridView.configuredVerticalPaddingPx != verticalPaddingPx ||
                gridView.paddingEnd != endPaddingPx
            ) {
                gridView.windowAlignmentOffset = startPaddingPx
                gridView.setPaddingRelative(startPaddingPx, verticalPaddingPx, endPaddingPx, verticalPaddingPx)
                gridView.configuredStartPaddingPx = startPaddingPx
                gridView.configuredVerticalPaddingPx = verticalPaddingPx
            }
            if (gridView.configuredSpacingPx != spacingPx) {
                gridView.setItemSpacing(spacingPx)
                gridView.configuredSpacingPx = spacingPx
            }
            gridView.onNavigateDown = onNavigateDown
            gridView.onNavigateUp = onNavigateUp
            gridView.onNavigateBack = onNavigateBack
            gridView.onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem
            gridView.onBackdropInteraction = onBackdropInteraction
            if (gridView.configuredAnimationsEnabled != animationsEnabled ||
                gridView.configuredBackdropExpandEnabled != focusedPosterBackdropExpandEnabled
            ) {
                gridView.animationsEnabled = animationsEnabled
                gridView.setAnimateChildLayout(animationsEnabled && !focusedPosterBackdropExpandEnabled)
                gridView.setHasFixedSize(!focusedPosterBackdropExpandEnabled)
                gridView.configuredAnimationsEnabled = animationsEnabled
                gridView.configuredBackdropExpandEnabled = focusedPosterBackdropExpandEnabled
            }
            adapter.animationsEnabled = { animationsEnabled }
            adapter.onItemFocused = onItemFocused
            adapter.onItemClick = onItemClick
            adapter.onItemLongClick = onItemLongClick
            adapter.onLoadMore = onLoadMore
            if (gridView.adapter !== adapter) {
                gridView.adapter = adapter
            }
            effectivePool?.let {
                if (gridView.recycledViewPool !== it) {
                    gridView.setRecycledViewPool(it)
                }
            }
            adapter.updateConfig(cardWidthPx, cardHeightPx, cornerRadiusPx, showLabels, themeStyle)
            rowGrids?.put(rowKey, gridView)
            adapter.submitItems(items)
            if (initialFocusPosition in items.indices && !gridView.hasFocus()) {
                gridView.restoredPosition = initialFocusPosition
                if (initialFocusPosition != lastAppliedInitialPosition &&
                    initialFocusPosition in 0 until adapter.itemCount
                ) {
                    gridView.setSelectedPosition(initialFocusPosition)
                    lastAppliedInitialPosition = initialFocusPosition
                    initialScrollApplied = true
                }
            }
        },
        onRelease = { gridView ->
            adapter.collapseExpandedCardImmediate()
            if (localGridViewRef === gridView) {
                localGridViewRef = null
            }
            if (rowGrids?.get(rowKey) === gridView) {
                rowGrids.remove(rowKey)
            }
            // Return attached ViewHolders cleanly to the shared pool and unbind bitmaps
            gridView.adapter = null
        }
    )
}
