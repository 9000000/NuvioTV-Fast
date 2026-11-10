package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.leanback.LeanbackNativeRow
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun CollectionSection(
    items: List<MetaPreview>,
    listState: LazyListState = rememberLazyListState(),
    title: String? = null,
    posterCardCornerRadius: Dp = NuvioTheme.spacing.md,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    sectionFocusRequester: FocusRequester? = null,
    restoreItemId: String? = null,
    restoreFocusToken: Int = 0,
    blockDefaultRestore: Boolean = false,
    lastFocusedItemId: String? = null,
    onLastFocusedItemIdChange: (String) -> Unit = {},
    onRestoreFocusHandled: () -> Unit = {},
    onItemFocused: (MetaPreview) -> Unit = {},
    onItemClick: (MetaPreview) -> Unit,
    onItemLongPress: (MetaPreview) -> Unit = {},
    isItemWatched: (MetaPreview) -> Boolean = { false },
    windowResetKey: String? = null
) {
    if (items.isEmpty()) return

    val leanbackItems = remember(items, isItemWatched) {
        items.map { item ->
            LeanbackCardItem.Poster(
                key = item.id,
                title = item.name,
                imageUrl = item.landscapePoster ?: item.backdropUrl ?: item.poster,
                subtitle = item.releaseInfo,
                isLandscape = true,
                isWatched = isItemWatched(item),
                payload = item
            )
        }
    }

    val initialIndex = remember(items, restoreItemId, lastFocusedItemId) {
        val targetId = restoreItemId ?: lastFocusedItemId
        if (!targetId.isNullOrBlank()) items.indexOfFirst { it.id == targetId }.coerceAtLeast(0) else 0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (title.isNullOrBlank()) NuvioTheme.spacing.sm else 20.dp, bottom = NuvioTheme.spacing.sm)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = NuvioTheme.colors.TextPrimary,
                modifier = Modifier
                    .padding(start = NuvioTheme.spacing.xxxl, end = NuvioTheme.spacing.xxxl, bottom = NuvioTheme.spacing.sm)
            )
        }

        LeanbackNativeRow(
            rowKey = "collection_${windowResetKey ?: "default"}",
            items = leanbackItems,
            cardWidth = 260.dp,
            cardHeight = 146.dp,
            cornerRadius = posterCardCornerRadius,
            showLabels = true,
            animationsEnabled = NuvioTheme.animationsEnabled,
            startPadding = NuvioTheme.spacing.xxxl,
            spacing = NuvioTheme.spacing.md,
            onItemFocused = { _, cardItem ->
                val item = cardItem.payload as? MetaPreview ?: return@LeanbackNativeRow
                onLastFocusedItemIdChange(item.id)
                onItemFocused(item)
                if (item.id == restoreItemId && !restoreItemId.isNullOrBlank()) {
                    onRestoreFocusHandled()
                }
            },
            onItemClick = { cardItem ->
                val item = cardItem.payload as? MetaPreview ?: return@LeanbackNativeRow
                onItemClick(item)
            },
            onItemLongClick = { cardItem ->
                val item = cardItem.payload as? MetaPreview ?: return@LeanbackNativeRow
                onItemLongPress(item)
            },
            onNavigateUp = {
                upFocusRequester?.requestFocus()
                upFocusRequester != null
            },
            onNavigateDown = {
                downFocusRequester?.requestFocus()
                downFocusRequester != null
            },
            initialFocusPosition = initialIndex,
            isPendingFocus = !restoreItemId.isNullOrBlank() && restoreFocusToken > 0,
            pendingFocusIndex = initialIndex,
            pendingFocusNonce = restoreFocusToken,
            onClearPendingFocus = onRestoreFocusHandled,
            entryFocusRequester = sectionFocusRequester
        )
    }
}
