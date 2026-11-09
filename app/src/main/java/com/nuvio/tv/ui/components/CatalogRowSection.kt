package com.nuvio.tv.ui.components

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection
import com.nuvio.tv.R
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.leanback.LeanbackNativeRow
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.stableKey
import com.nuvio.tv.domain.model.stableItemKeys
import com.nuvio.tv.domain.model.CardDepthSurface
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.stableItemKey
import com.nuvio.tv.domain.model.stableItemKeys
import com.nuvio.tv.domain.model.PLACEHOLDER_IMAGE_URL
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.util.formatAddonTypeLabel
import com.nuvio.tv.ui.util.localizedContentType
import com.nuvio.tv.ui.util.contentTextDirection
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun CatalogRowSection(
    catalogRow: CatalogRow,
    onItemClick: (String, String, String) -> Unit,
    onSeeAll: () -> Unit = {},
    showSeeAll: Boolean = catalogRow.hasMore || catalogRow.items.size >= 15,
    seeAllLabel: String? = null,
    posterCardStyle: PosterCardStyle = PosterCardDefaults.Style,
    showPosterLabels: Boolean = true,
    showImdbRatings: Boolean = true,
    showAddonName: Boolean = true,
    showCatalogTypeSuffix: Boolean = true,
    focusedPosterBackdropExpandEnabled: Boolean = false,
    focusedPosterBackdropExpandDelaySeconds: Int = 3,
    focusedPosterBackdropTrailerEnabled: Boolean = false,
    focusedPosterBackdropTrailerMuted: Boolean = true,
    trailerPreviewUrls: Map<String, String> = emptyMap(),
    trailerPreviewAudioUrls: Map<String, String> = emptyMap(),
    onRequestTrailerPreview: (MetaPreview) -> Unit = {},
    onItemFocus: (MetaPreview) -> Unit = {},
    isItemWatched: (MetaPreview) -> Boolean = { false },
    onItemLongPress: (MetaPreview, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    enableRowFocusRestorer: Boolean = true,
    initialScrollIndex: Int = 0,
    /** Used only for initial focus restore (e.g. returning from detail screen). */
    focusedItemIndex: Int = -1,
    /** Persisted focus index from parent — used only by focusRestorer to
     *  survive LazyColumn recycling.  Does NOT trigger a focus request. */
    restorerFocusedIndex: Int = -1,
    /** Clears the remembered focus index when this changes, including when the new content has
     *  the same items. */
    focusResetToken: String? = null,
    onItemFocused: (itemIndex: Int) -> Unit = {},
    rowFocusRequester: FocusRequester? = null,
    /** FocusRequester that will be attached to the first-or-last-focused card.
     *  Wide elements above (CW, collections) can point their D-pad down here. */
    entryFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onNavigateLeftOnFirstItem: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    allowOpenSidebar: Boolean = false,
    listState: LazyListState = rememberLazyListState(initialFirstVisibleItemIndex = initialScrollIndex)
) {
    val catalogRowKey = remember(catalogRow) { catalogRow.stableKey() }
    val rowItemIdentities = remember(catalogRow.items) { catalogRow.stableItemKeys() }

    // Item keys carry item identity, which the placeholder the ring sits on loses when real data
    // arrives: its key changes and Compose tears the focused node down. Lend that one card a
    // positional key for as long as the ring can be on it, so the node is reused instead.
    //
    // Armed from the item id, not a loading flag: a lazily loaded catalog composes its row
    // before loading starts and would never see the flag go up.
    val firstCardKey = remember(catalogRowKey) { catalogRowKey + "__first" }
    val firstIsPlaceholder = catalogRow.items.firstOrNull()?.id?.startsWith("__placeholder_") == true
    val pinFirstCard = remember(catalogRowKey) { mutableStateOf(firstIsPlaceholder) }
    val pinSpent = remember(catalogRowKey) { mutableStateOf(false) }
    // Lent to the card, not the slot: slot 0 would hand the key to whatever lands there.
    val pinnedItemKey = remember(catalogRowKey) { mutableStateOf<String?>(null) }
    if (firstIsPlaceholder && !pinSpent.value) pinFirstCard.value = true
    if (pinFirstCard.value && !firstIsPlaceholder && pinnedItemKey.value == null) {
        pinnedItemKey.value = rowItemIdentities.firstOrNull()
    }

    fun rowItemFocusKey(index: Int, item: MetaPreview): String {
        val identity = rowItemIdentities.getOrElse(index) { catalogRow.stableItemKey(item) }
        if (!pinFirstCard.value) return identity
        val pinned = pinnedItemKey.value
        return when {
            pinned != null -> if (identity == pinned) firstCardKey else identity
            index == 0 -> firstCardKey
            else -> identity
        }
    }

    val seeAllCardShape = RoundedCornerShape(posterCardStyle.cornerRadius)
    val internalRowFocusRequester = remember { FocusRequester() }
    val resolvedRowFocusRequester = rowFocusRequester ?: internalRowFocusRequester
    val itemFocusRequestersByKey = remember { mutableMapOf<String, FocusRequester>() }
    var lastRequestedFocusItemKey by remember { mutableStateOf<String?>(null) }
    val lastFocusedItemIndex = remember { mutableIntStateOf(-1) }
    // Item keys as they were when lastFocusedItemIndex was recorded, so the index can be
    // relocated when the row changes instead of pointing at whatever took that slot.
    val previousRowItemKeys = remember { mutableStateOf<List<String>>(emptyList()) }
    // Update during composition so focusRestorer sees the reset immediately.
    val lastFocusResetToken = remember { mutableStateOf(focusResetToken) }
    if (lastFocusResetToken.value != focusResetToken) {
        lastFocusResetToken.value = focusResetToken
        lastFocusedItemIndex.intValue = -1
    }
    // Runs during composition, not in an effect: focusRestorer below is driven by the user and
    // can fire before an effect would have relocated the index, which would restore focus onto
    // whatever took that slot.
    if (previousRowItemKeys.value !== rowItemIdentities) {
        val storedIndex = lastFocusedItemIndex.intValue
        val previousKeys = previousRowItemKeys.value
        if (storedIndex >= 0 && previousKeys.isNotEmpty()) {
            val wanted = previousKeys.getOrNull(storedIndex)
            val relocated = wanted?.let { rowItemIdentities.indexOf(it) } ?: -1
            if (relocated != storedIndex) lastFocusedItemIndex.intValue = relocated
        }
        previousRowItemKeys.value = rowItemIdentities
    }

    val blockingFocusExit = remember { mutableStateOf(false) }
    val rowHasFocusRef = remember { mutableStateOf(false) }
    val firstItemId = catalogRow.items.firstOrNull()?.id
    val wasPlaceholderRef = remember { mutableStateOf(firstItemId?.startsWith("__placeholder_") == true) }
    val isNowReal = firstItemId?.startsWith("__placeholder_") != true

    if (wasPlaceholderRef.value && isNowReal && rowHasFocusRef.value) {
        blockingFocusExit.value = true
    }
    wasPlaceholderRef.value = firstItemId?.startsWith("__placeholder_") == true

    // Released once the ring has left the row: swapping the node earlier is visible for nothing.
    if (pinFirstCard.value && !firstIsPlaceholder && !rowHasFocusRef.value) {
        pinFirstCard.value = false
        pinnedItemKey.value = null
        pinSpent.value = true
    }

    LaunchedEffect(blockingFocusExit.value) {
        if (!blockingFocusExit.value) return@LaunchedEffect
        val targetKey = rowItemFocusKey(0, catalogRow.items.firstOrNull() ?: run {
            blockingFocusExit.value = false
            return@LaunchedEffect
        })
        repeat(15) {
            val req = itemFocusRequestersByKey[targetKey]
            if (req != null) {
                val ok = runCatching { req.requestFocus(); true }.getOrDefault(false)
                if (ok) { blockingFocusExit.value = false; return@LaunchedEffect }
            }
            withFrameNanos { }
        }
        blockingFocusExit.value = false
    }

    val latestOnItemClick by rememberUpdatedState(onItemClick)
    val latestOnSeeAll by rememberUpdatedState(onSeeAll)
    val latestOnItemFocus by rememberUpdatedState(onItemFocus)
    val latestIsItemWatched by rememberUpdatedState(isItemWatched)
    val latestOnItemLongPress by rememberUpdatedState(onItemLongPress)
    val latestOnItemFocused by rememberUpdatedState(onItemFocused)
    val latestOnRequestTrailerPreview by rememberUpdatedState(onRequestTrailerPreview)

    LaunchedEffect(catalogRow.items) {
        val validKeys = catalogRow.items.mapIndexedTo(mutableSetOf()) { index, item ->
            rowItemFocusKey(index, item)
        }
        itemFocusRequestersByKey.keys.retainAll(validKeys)
        if (lastRequestedFocusItemKey !in validKeys) {
            lastRequestedFocusItemKey = null
        }
    }

    LaunchedEffect(focusedItemIndex) {
        if (focusedItemIndex >= 0 && catalogRow.items.isNotEmpty()) {
            runCatching { (entryFocusRequester ?: resolvedRowFocusRequester).requestFocus() }
        }
    }

    val directionalFocusModifier = if (upFocusRequester != null) {
        Modifier.focusProperties { up = upFocusRequester }
    } else {
        Modifier
    }

    val catalogContext = LocalContext.current
    val typeLabel = remember(catalogRow.rawType, catalogRow.apiType, catalogContext) {
        val raw = catalogRow.rawType.takeIf { it.isNotBlank() } ?: catalogRow.apiType
        localizedContentType(catalogContext, raw)
    }
    val catalogTitle = remember(catalogRow.catalogName, typeLabel, showCatalogTypeSuffix) {
        val formattedName = catalogRow.catalogName.replaceFirstChar { it.uppercase() }
        if (formattedName.isBlank()) ""
        else if (showCatalogTypeSuffix && typeLabel.isNotEmpty()) "$formattedName - $typeLabel" else formattedName
    }

    Column(modifier = modifier.fillMaxWidth().then(
        if (blockingFocusExit.value) {
            Modifier.focusProperties {
                up = FocusRequester.Cancel
                down = FocusRequester.Cancel
            }
        } else Modifier
    )) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = NuvioTheme.spacing.xxxl, end = NuvioTheme.spacing.xxxl, bottom = NuvioTheme.spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)) {
                Text(
                    text = catalogTitle.ifBlank { " " },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        textDirection = catalogTitle.contentTextDirection()
                    ),
                    color = if (catalogTitle.isBlank()) Color.Transparent else NuvioTheme.colors.TextPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Clip
                )
                if (showAddonName) {
                    val addonText = if (catalogTitle.isBlank()) " " else stringResource(R.string.catalog_from_addon, catalogRow.addonName)
                    Text(
                        text = addonText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            textDirection = addonText.contentTextDirection()
                        ),
                        color = if (catalogTitle.isBlank()) Color.Transparent else NuvioTheme.colors.TextTertiary
                    )
                }
            }
        }

        val density = LocalDensity.current
        val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
        val layoutDirection = LocalLayoutDirection.current
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val horizontalBringIntoViewSpec = remember(density, defaultBringIntoViewSpec, isRtl) {
            val startPx = with(density) { NuvioTheme.spacing.xxxl.roundToPx() }
            @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
            object : BringIntoViewSpec {
                override val scrollAnimationSpec: AnimationSpec<Float> =
                    defaultBringIntoViewSpec.scrollAnimationSpec
                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                    val childSize = kotlin.math.abs(size)
                    if (isRtl) {
                        val childSmallerThanParent = childSize <= containerSize
                        val initialTarget = containerSize - startPx.toFloat()
                        val targetForTrailingEdge =
                            if (childSmallerThanParent && initialTarget < childSize) {
                                childSize
                            } else {
                                initialTarget
                            }
                        return (offset + size) - targetForTrailingEdge
                    } else {
                        val target = startPx.toFloat()
                        val space = containerSize - target
                        val leading = if (childSize <= containerSize && space < childSize) containerSize - childSize else target
                        return offset - leading
                    }
                }
            }
        }

        val usesPlaceholderShimmer = catalogRow.isLoading &&
            catalogRow.items.firstOrNull()?.poster == PLACEHOLDER_IMAGE_URL
        val placeholderShimmerOffsetState = if (usesPlaceholderShimmer) {
            rememberPlaceholderShimmerOffsetState(label = "classicPlaceholderShimmer")
        } else {
            null
        }

        val isLandscape = LocalLandscapePosterMode.current || catalogRow.items.firstOrNull()?.posterShape == PosterShape.LANDSCAPE
        val strSeeAll = seeAllLabel ?: stringResource(R.string.action_see_all)
        val leanbackItems = remember(catalogRow.items, showSeeAll, isLandscape, strSeeAll) {
            val list = catalogRow.items.mapIndexed { index, item ->
                LeanbackCardItem.Poster(
                    key = rowItemFocusKey(index, item),
                    title = item.name,
                    imageUrl = if (isLandscape) (item.landscapePoster ?: item.backdropUrl ?: item.poster) else (item.poster ?: item.backdropUrl),
                    isLandscape = isLandscape,
                    isWatched = latestIsItemWatched(item),
                    payload = item
                )
            }.toMutableList()

            if (showSeeAll) {
                list.add(
                    LeanbackCardItem.Poster(
                        key = "${catalogRow.type}_${catalogRow.catalogId}_see_all",
                        title = strSeeAll,
                        imageUrl = null,
                        isLandscape = isLandscape,
                        badgeText = "ALL",
                        payload = "SEE_ALL"
                    )
                )
            }
            list
        }

        val targetFocusIndex = if (focusedItemIndex >= 0) {
            focusedItemIndex
        } else if (restorerFocusedIndex in catalogRow.items.indices) {
            restorerFocusedIndex
        } else 0

        val openSidebar = com.nuvio.tv.LocalOpenSidebar.current

        LeanbackNativeRow(
            modifier = Modifier
                .then(if (entryFocusRequester != null) Modifier.focusRequester(entryFocusRequester) else Modifier)
                .focusRequester(resolvedRowFocusRequester),
            rowKey = "catalog_row_${catalogRow.stableKey()}",
            items = leanbackItems,
            cardWidth = if (isLandscape) 260.dp else posterCardStyle.width,
            cardHeight = if (isLandscape) 146.dp else posterCardStyle.height,
            cornerRadius = posterCardStyle.cornerRadius,
            showLabels = showPosterLabels,
            animationsEnabled = NuvioTheme.animationsEnabled,
            startPadding = NuvioTheme.spacing.xxxl,
            spacing = NuvioTheme.spacing.lg,
            onItemFocused = { index, cardItem ->
                if (cardItem.payload == "SEE_ALL") {
                    lastFocusedItemIndex.intValue = index
                    latestOnItemFocused(index)
                    return@LeanbackNativeRow
                }
                val item = cardItem.payload as? MetaPreview ?: return@LeanbackNativeRow
                latestOnItemFocus(item)
                lastFocusedItemIndex.intValue = index
                latestOnItemFocused(index)
            },
            onItemClick = { cardItem ->
                if (cardItem.payload == "SEE_ALL") {
                    onSeeAll()
                    return@LeanbackNativeRow
                }
                val item = cardItem.payload as? MetaPreview ?: return@LeanbackNativeRow
                if (!item.id.startsWith("__placeholder_")) {
                    latestOnItemClick(item.id, item.apiType, catalogRow.addonBaseUrl)
                }
            },
            onItemLongClick = { cardItem ->
                if (cardItem.payload == "SEE_ALL") return@LeanbackNativeRow
                val item = cardItem.payload as? MetaPreview ?: return@LeanbackNativeRow
                if (!item.id.startsWith("__placeholder_")) {
                    latestOnItemLongPress(item, catalogRow.addonBaseUrl)
                }
            },
            onNavigateDown = onNavigateDown ?: { false },
            onNavigateUp = {
                if (onNavigateUp != null) {
                    onNavigateUp()
                } else {
                    upFocusRequester?.requestFocus()
                    upFocusRequester != null
                }
            },
            onNavigateLeftOnFirstItem = {
                if (onNavigateLeftOnFirstItem != null) {
                    onNavigateLeftOnFirstItem()
                } else if (allowOpenSidebar && openSidebar != null) {
                    openSidebar()
                    true
                } else {
                    false
                }
            },
            isPendingFocus = focusedItemIndex >= 0,
            pendingFocusIndex = targetFocusIndex
        )
    }
}
