@file:OptIn(ExperimentalFoundationApi::class, kotlinx.coroutines.FlowPreview::class)

package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.recyclerview.widget.RecyclerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.isPlaceholder
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.StableRef
import com.nuvio.tv.ui.util.contentTextDirection

internal val LocalVerticalRowsScrolling = compositionLocalOf<State<Boolean>> { mutableStateOf(false) }

/**
 * True while the user is actively "fast-scrolling" — i.e. holding DPAD_LEFT/RIGHT or
 * DPAD_UP/DOWN and the LazyColumn-level key handler has taken over to drag the list
 * programmatically instead of letting [androidx.compose.ui.focus.FocusManager.moveFocus]
 * pull focus card-by-card. Cards use this to suppress their focus chrome (border / glow /
 * GIF) during the drag; the chrome snaps back onto whichever card focus lands on when
 * the user releases the key. Defaults to `false`, so any card used outside a modern
 * home row keeps its normal focus visuals.
 */
internal val LocalFastScrollActive = compositionLocalOf<State<Boolean>> { mutableStateOf(false) }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ModernRowSection(
    row: HeroCarouselRow,
    isActiveRow: () -> Boolean,
    rowFocusRequester: FocusRequester,
    rowTitleBottom: Dp,
    focusedItemByRow: StableRef<MutableMap<String, Int>>,
    loadMoreRequestedTotals: StableRef<MutableMap<String, Int>>,
    onRowItemFocused: (String, Int, Boolean) -> Unit,
    useLandscapePosters: Boolean,
    showLabels: Boolean,
    posterCardCornerRadius: Dp,
    portraitCatalogCardWidth: Dp,
    portraitCatalogCardHeight: Dp,
    landscapeCatalogCardWidth: Dp,
    landscapeCatalogCardHeight: Dp,
    continueWatchingCardWidth: Dp,
    continueWatchingCardHeight: Dp,
    continueWatchingCornerRadius: Dp,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onContinueWatchingOptions: (ContinueWatchingItem) -> Unit,
    onCatalogItemLongPress: (MetaPreview, String) -> Unit,
    onItemFocus: (MetaPreview) -> Unit,
    onCatalogSelectionFocused: (FocusedCatalogSelection) -> Unit,
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToFolderDetail: (String, String) -> Unit,
    onLoadMoreCatalog: (String, String, String) -> Unit,
    onNavigateDown: () -> Boolean = { false },
    onNavigateUp: () -> Boolean = { false },
    onNavigateLeftOnFirstItem: () -> Boolean = { false },
    onNavigateBack: () -> Boolean = { false },
    onVerticalHoldScroll: ((Int) -> Boolean)? = null,
    onVerticalHoldRelease: (() -> Unit)? = null,
    isVerticalGlideActive: (() -> Boolean)? = null,
    onHorizontalInterrupt: (() -> Unit)? = null,
    rowGrids: MutableMap<String, androidx.leanback.widget.HorizontalGridView>? = null,
    sharedRecycledViewPool: RecyclerView.RecycledViewPool? = null,
    isPendingFocus: Boolean = false,
    pendingFocusIndex: Int = 0,
    pendingFocusNonce: Int = 0,
    onClearPendingFocus: () -> Unit = {},
    isCatalogItemWatched: (MetaPreview) -> Boolean = { false },
    blurUnwatchedEpisodes: Boolean = false
) {
    @Suppress("NAME_SHADOWING") val focusedItemByRow = focusedItemByRow.value
    @Suppress("NAME_SHADOWING") val loadMoreRequestedTotals = loadMoreRequestedTotals.value
    val rowKey = row.key
    val animationsEnabled = NuvioTheme.animationsEnabled
    val isContinueWatchingRow = rowKey == MODERN_CONTINUE_WATCHING_ROW_KEY || rowKey == MODERN_UPCOMING_ROW_KEY

    // Blocks vertical focus exit during placeholder->data transition.
    val blockingFocusExit = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.then(
            if (blockingFocusExit.value) {
                Modifier.focusProperties {
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                }
            } else Modifier
        )
    ) {
        val titleMediumStyle = MaterialTheme.typography.titleMedium
        val rowTitleStyle = remember(titleMediumStyle) {
            titleMediumStyle.copy(fontWeight = FontWeight.SemiBold)
        }
        val rowTitle = row.title
        val textColor = NuvioTheme.colors.TextPrimary
        val textModifier = remember(rowTitleBottom) {
            Modifier.padding(start = 52.dp, bottom = rowTitleBottom)
        }
        Text(
            text = rowTitle,
            style = rowTitleStyle.copy(textDirection = rowTitle.contentTextDirection()),
            color = textColor,
            modifier = textModifier
        )

        // When placeholder items are replaced by real data and this row
        // is the active row, re-request focus on the first real item.
        val firstItemImageUrl = row.items.list.firstOrNull()?.imageUrl
        val wasPlaceholderRef = remember { mutableStateOf(row.isLoading && firstItemImageUrl.isPlaceholder()) }
        val needsFocusRestore = remember { mutableStateOf(false) }

        LaunchedEffect(row.isLoading, firstItemImageUrl, isActiveRow) {
            val wasPlaceholder = wasPlaceholderRef.value
            val isNowReal = !row.isLoading || !firstItemImageUrl.isPlaceholder()
            if (wasPlaceholder && isNowReal && isActiveRow()) {
                needsFocusRestore.value = true
                blockingFocusExit.value = true
            }
            wasPlaceholderRef.value = row.isLoading && firstItemImageUrl.isPlaceholder()
        }

        // Restore focus after placeholder->data transition.
        LaunchedEffect(needsFocusRestore.value, rowKey) {
            if (!needsFocusRestore.value) return@LaunchedEffect
            needsFocusRestore.value = false
            blockingFocusExit.value = false
            val grid = rowGrids?.get(rowKey) as? ModernInteropHorizontalGridView
            if (grid != null && grid.isAttachedToWindow && row.items.list.isNotEmpty()) {
                val targetIdx = (focusedItemByRow[rowKey] ?: 0).coerceIn(0, row.items.list.lastIndex)
                grid.post {
                    // Only re-anchor focus while this row still owns it; the user may have
                    // moved to another row while the data was being applied.
                    if (grid.isAttachedToWindow && isActiveRow()) {
                        grid.prepareForIncomingFocus()
                        grid.setSelectedPosition(targetIdx)
                        if (!grid.hasFocus()) {
                            grid.requestFocus()
                        }
                        grid.ensureChildFocusAndRing(targetPos = targetIdx, animated = false)
                    }
                }
            }
        }

        val currentRowState = rememberUpdatedState(row)
        val latestOnRowItemFocused = rememberUpdatedState(onRowItemFocused)
        val latestIsActiveRow = rememberUpdatedState(isActiveRow)
        val latestOnItemFocus = rememberUpdatedState(onItemFocus)
        val latestOnCatalogSelectionFocused = rememberUpdatedState(onCatalogSelectionFocused)
        val latestOnNavigateToDetail = rememberUpdatedState(onNavigateToDetail)
        val latestOnNavigateToFolderDetail = rememberUpdatedState(onNavigateToFolderDetail)
        val latestOnContinueWatchingClick = rememberUpdatedState(onContinueWatchingClick)
        val latestOnCatalogItemLongPress = rememberUpdatedState(onCatalogItemLongPress)
        val latestOnContinueWatchingOptions = rememberUpdatedState(onContinueWatchingOptions)
        val latestOnLoadMoreCatalog = rememberUpdatedState(onLoadMoreCatalog)

        val stableOnItemFocused = remember(rowKey, isContinueWatchingRow) {
            { index: Int, item: ModernCarouselItem ->
                latestOnRowItemFocused.value(rowKey, index, isContinueWatchingRow)
                // A stray focus event from a non-target row was rejected: don't update hero/selection.
                if (latestIsActiveRow.value()) {
                    item.metaPreview?.let { latestOnItemFocus.value(it) }
                    when (val p = item.payload) {
                        is ModernPayload.Catalog -> {
                            if (!p.itemId.startsWith("__placeholder_")) {
                                latestOnCatalogSelectionFocused.value(FocusedCatalogSelection(p.focusKey, p))
                            }
                        }
                        is ModernPayload.CollectionFolder -> {
                            latestOnCatalogSelectionFocused.value(FocusedCatalogSelection(p.focusKey, p))
                        }
                        is ModernPayload.ContinueWatching -> Unit
                    }
                }
            }
        }

        val stableOnItemClick = remember(rowKey) {
            { item: ModernCarouselItem ->
                when (val p = item.payload) {
                    is ModernPayload.Catalog -> {
                        if (!p.itemId.startsWith("__placeholder_")) {
                            latestOnNavigateToDetail.value(p.itemId, p.itemType, p.addonBaseUrl)
                        }
                    }
                    is ModernPayload.CollectionFolder -> {
                        latestOnNavigateToFolderDetail.value(p.collectionId, p.folderId)
                    }
                    is ModernPayload.ContinueWatching -> {
                        latestOnContinueWatchingClick.value(p.item)
                    }
                }
            }
        }

        val stableOnItemLongClick = remember(rowKey) {
            { item: ModernCarouselItem ->
                when (val p = item.payload) {
                    is ModernPayload.Catalog -> {
                        item.metaPreview?.let { latestOnCatalogItemLongPress.value(it, p.addonBaseUrl) }
                        Unit
                    }
                    is ModernPayload.ContinueWatching -> {
                        latestOnContinueWatchingOptions.value(p.item)
                    }
                    else -> Unit
                }
            }
        }

        val stableOnLoadMore = remember(rowKey) {
            {
                val rowState = currentRowState.value
                val catalogId = rowState.catalogId
                val addonId = rowState.addonId
                val apiType = rowState.apiType
                if (rowState.supportsSkip &&
                    rowState.hasMore &&
                    !rowState.isLoading &&
                    !catalogId.isNullOrBlank() &&
                    !addonId.isNullOrBlank() &&
                    !apiType.isNullOrBlank()
                ) {
                    val lastRequestedTotal = loadMoreRequestedTotals[rowKey]
                    val total = rowState.items.list.size
                    if (lastRequestedTotal != total) {
                        loadMoreRequestedTotals[rowKey] = total
                        latestOnLoadMoreCatalog.value(catalogId, addonId, apiType)
                    }
                }
            }
        }

        val rowStartPadding = 52.dp
        val effectiveCardWidth = when {
            isContinueWatchingRow -> continueWatchingCardWidth
            useLandscapePosters -> landscapeCatalogCardWidth
            else -> portraitCatalogCardWidth
        }
        val effectiveCardHeight = when {
            isContinueWatchingRow -> continueWatchingCardHeight
            useLandscapePosters -> landscapeCatalogCardHeight
            else -> portraitCatalogCardHeight
        }
        val effectiveCornerRadius = if (isContinueWatchingRow) continueWatchingCornerRadius else posterCardCornerRadius

        LeanbackModernRow(
            rowKey = rowKey,
            recycledViewPool = sharedRecycledViewPool,
            items = row.items.list,
            cardWidth = effectiveCardWidth,
            cardHeight = effectiveCardHeight,
            cornerRadius = effectiveCornerRadius,
            showLabels = showLabels,
            animationsEnabled = animationsEnabled,
            startPadding = rowStartPadding,
            spacing = NuvioTheme.spacing.md,
            onItemFocused = stableOnItemFocused,
            onItemClick = stableOnItemClick,
            onItemLongClick = stableOnItemLongClick,
            onLoadMore = stableOnLoadMore,
            onNavigateDown = onNavigateDown,
            onNavigateUp = onNavigateUp,
            onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem,
            onNavigateBack = onNavigateBack,
            onVerticalHoldScroll = onVerticalHoldScroll,
            onVerticalHoldRelease = onVerticalHoldRelease,
            isVerticalGlideActive = isVerticalGlideActive,
            onHorizontalInterrupt = onHorizontalInterrupt,
            rowGrids = rowGrids,
            isPendingFocus = isPendingFocus,
            pendingFocusIndex = pendingFocusIndex,
            initialFocusPosition = focusedItemByRow[rowKey] ?: 0,
            pendingFocusNonce = pendingFocusNonce,
            onClearPendingFocus = onClearPendingFocus,
            canTakeFocus = isActiveRow,
            isCatalogItemWatched = isCatalogItemWatched,
            blurUnwatchedEpisodes = blurUnwatchedEpisodes,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(rowFocusRequester)
        )
    }
}
