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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.leanback.LeanbackNativeRow
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun MoreLikeThisSection(
    items: List<MetaPreview>,
    listState: LazyListState = rememberLazyListState(),
    sourceLabel: String? = null,
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
            .then(
                if (blockDefaultRestore) {
                    Modifier.focusProperties { canFocus = false }
                } else {
                    Modifier
                }
            )
            .padding(top = NuvioTheme.spacing.sm, bottom = NuvioTheme.spacing.sm)
    ) {
        LeanbackNativeRow(
            rowKey = "more_like_this_${windowResetKey ?: "default"}",
            items = leanbackItems,
            cardWidth = 260.dp,
            cardHeight = 146.dp,
            cornerRadius = posterCardCornerRadius,
            showLabels = true,
            animationsEnabled = NuvioTheme.animationsEnabled,
            startPadding = NuvioTheme.spacing.xxxl,
            spacing = NuvioTheme.spacing.md,
            isFocusEnabled = !blockDefaultRestore,
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

        sourceLabel
            ?.takeIf { it.isNotBlank() }
            ?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.End)
                        .padding(end = 10.dp, top = NuvioTheme.spacing.xxs, bottom = NuvioTheme.spacing.xxs)
                )
            }
    }
}
