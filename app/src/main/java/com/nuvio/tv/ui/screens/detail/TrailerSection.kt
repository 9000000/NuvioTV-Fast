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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MetaTrailer
import com.nuvio.tv.ui.leanback.LeanbackNativeRow
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun TrailerSection(
    trailers: List<MetaTrailer>,
    listState: LazyListState = rememberLazyListState(),
    posterCardCornerRadius: Dp = NuvioTheme.spacing.md,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    sectionFocusRequester: FocusRequester? = null,
    restoreTrailerId: String? = null,
    restoreFocusToken: Int = 0,
    lastFocusedTrailerId: String? = null,
    onLastFocusedTrailerIdChange: (String) -> Unit = {},
    onRestoreFocusHandled: () -> Unit = {},
    onTrailerFocused: (MetaTrailer) -> Unit = {},
    onTrailerClick: (MetaTrailer) -> Unit,
    windowResetKey: String? = null
) {
    if (trailers.isEmpty()) return

    val trailerFallbackTitle = stringResource(R.string.detail_tab_trailer)
    val leanbackItems = remember(trailers, trailerFallbackTitle) {
        trailers.mapNotNull { trailer ->
            val ytId = trailer.ytId?.trim().orEmpty()
            if (ytId.isBlank()) return@mapNotNull null
            val title = trailer.name?.takeIf { it.isNotBlank() }
                ?: trailer.type?.takeIf { it.isNotBlank() }
                ?: trailerFallbackTitle
            val subtitle = buildList {
                trailer.type?.takeIf { it.isNotBlank() }?.let(::add)
                trailer.lang?.takeIf { it.isNotBlank() }?.uppercase()?.let(::add)
            }.joinToString(" • ")

            LeanbackCardItem.Poster(
                key = ytId,
                title = title,
                imageUrl = "https://img.youtube.com/vi/$ytId/hqdefault.jpg",
                subtitle = subtitle.ifBlank { null },
                isLandscape = true,
                payload = trailer
            )
        }
    }

    if (leanbackItems.isEmpty()) return

    val initialIndex = remember(leanbackItems, restoreTrailerId, lastFocusedTrailerId) {
        val targetId = restoreTrailerId ?: lastFocusedTrailerId
        if (!targetId.isNullOrBlank()) leanbackItems.indexOfFirst { it.key == targetId }.coerceAtLeast(0) else 0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NuvioTheme.spacing.sm, bottom = NuvioTheme.spacing.sm)
    ) {
        LeanbackNativeRow(
            rowKey = "trailers_${windowResetKey ?: "default"}",
            items = leanbackItems,
            cardWidth = 260.dp,
            cardHeight = 146.dp,
            cornerRadius = posterCardCornerRadius,
            showLabels = true,
            animationsEnabled = NuvioTheme.animationsEnabled,
            startPadding = NuvioTheme.spacing.xxxl,
            spacing = NuvioTheme.spacing.md,
            onItemFocused = { _, cardItem ->
                val trailer = cardItem.payload as? MetaTrailer ?: return@LeanbackNativeRow
                onLastFocusedTrailerIdChange(cardItem.key)
                onTrailerFocused(trailer)
                if (cardItem.key == restoreTrailerId && !restoreTrailerId.isNullOrBlank()) {
                    onRestoreFocusHandled()
                }
            },
            onItemClick = { cardItem ->
                val trailer = cardItem.payload as? MetaTrailer ?: return@LeanbackNativeRow
                onTrailerClick(trailer)
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
            isPendingFocus = !restoreTrailerId.isNullOrBlank() && restoreFocusToken > 0,
            pendingFocusIndex = initialIndex,
            pendingFocusNonce = restoreFocusToken,
            onClearPendingFocus = onRestoreFocusHandled,
            entryFocusRequester = sectionFocusRequester
        )
    }
}
