package com.nuvio.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.LocalOpenSidebar
import com.nuvio.tv.LocalSidebarExpanded
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.stableItemKey
import com.nuvio.tv.domain.model.stableItemKeys
import com.nuvio.tv.domain.model.stableKey
import com.nuvio.tv.ui.leanback.LeanbackNativeRow
import com.nuvio.tv.ui.leanback.core.LeanbackCardExpandSpec
import com.nuvio.tv.ui.leanback.core.rememberLeanbackExpandedTrailerHost
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.contentTextDirection
import com.nuvio.tv.ui.util.localizedContentType
import com.nuvio.tv.ui.util.localizedGenreLabel
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
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
    @Suppress("UNUSED_PARAMETER") enableRowFocusRestorer: Boolean = true,
    @Suppress("UNUSED_PARAMETER") initialScrollIndex: Int = 0,
    /** Used only for initial focus restore (e.g. returning from detail screen). */
    focusedItemIndex: Int = -1,
    /** Persisted focus index from parent — survives LazyColumn recycling without forcing focus. */
    restorerFocusedIndex: Int = -1,
    /** Clears the remembered focus index when this changes. */
    focusResetToken: String? = null,
    onItemFocused: (itemIndex: Int) -> Unit = {},
    rowFocusRequester: FocusRequester? = null,
    entryFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    onNavigateLeftOnFirstItem: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null,
    onNavigateUp: (() -> Boolean)? = null,
    allowOpenSidebar: Boolean = false,
    resetToStartNonce: Int = 0,
    @Suppress("UNUSED_PARAMETER") listState: LazyListState? = null
) {
    val catalogRowKey = remember(catalogRow) { catalogRow.stableKey() }
    val rowItemIdentities = remember(catalogRow.items) { catalogRow.stableItemKeys() }

    // Preserve positional identity on slot 0 while placeholder items transition to real data
    val firstCardKey = remember(catalogRowKey) { "${catalogRowKey}__first" }
    val firstIsPlaceholder = catalogRow.items.firstOrNull()?.id?.startsWith("__placeholder_") == true
    val pinFirstCard = remember(catalogRowKey) { mutableStateOf(firstIsPlaceholder) }
    val pinSpent = remember(catalogRowKey) { mutableStateOf(false) }
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

    val internalRowFocusRequester = remember { FocusRequester() }
    val resolvedRowFocusRequester = rowFocusRequester ?: internalRowFocusRequester
    val lastFocusedItemIndex = remember { mutableIntStateOf(-1) }
    val previousRowItemKeys = remember { mutableStateOf<List<String>>(emptyList()) }
    val lastFocusResetToken = remember { mutableStateOf(focusResetToken) }
    if (lastFocusResetToken.value != focusResetToken) {
        lastFocusResetToken.value = focusResetToken
        lastFocusedItemIndex.intValue = -1
    }
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

    val rowHasFocusRef = remember { mutableStateOf(false) }
    if (pinFirstCard.value && !firstIsPlaceholder && !rowHasFocusRef.value) {
        pinFirstCard.value = false
        pinnedItemKey.value = null
        pinSpent.value = true
    }

    val latestOnItemClick by rememberUpdatedState(onItemClick)
    val latestOnSeeAll by rememberUpdatedState(onSeeAll)
    val latestOnItemFocus by rememberUpdatedState(onItemFocus)
    val latestIsItemWatched by rememberUpdatedState(isItemWatched)
    val latestOnItemLongPress by rememberUpdatedState(onItemLongPress)
    val latestOnItemFocused by rememberUpdatedState(onItemFocused)
    val latestOnRequestTrailerPreview by rememberUpdatedState(onRequestTrailerPreview)

    val isSidebarExpanded = LocalSidebarExpanded.current
    val alwaysShowLandscapeClearlogo = LocalAlwaysBackdropWithLogo.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val playTrailerInExpandedCard = focusedPosterBackdropExpandEnabled && focusedPosterBackdropTrailerEnabled
    // Only instantiate the trailer host when expanded-card trailer autoplay is explicitly enabled.
    val expandedTrailerHost = if (playTrailerInExpandedCard) rememberLeanbackExpandedTrailerHost() else null
    val trailerPreviewUrlsState = remember { mutableStateOf(trailerPreviewUrls) }.apply { value = trailerPreviewUrls }
    val trailerPreviewAudioUrlsState = remember { mutableStateOf(trailerPreviewAudioUrls) }.apply { value = trailerPreviewAudioUrls }
    val focusedItemKeyForExpand = remember(catalogRowKey) { mutableStateOf<String?>(null) }
    val focusedItemIdForExpand = remember(catalogRowKey) { mutableStateOf<String?>(null) }
    val focusedMetaForExpand = remember(catalogRowKey) { mutableStateOf<MetaPreview?>(null) }
    val expandedItemKeyState = remember(catalogRowKey) { mutableStateOf<String?>(null) }
    val expandInteractionNonce = remember(catalogRowKey) { mutableIntStateOf(0) }

    LaunchedEffect(
        focusedPosterBackdropExpandEnabled,
        focusedPosterBackdropExpandDelaySeconds,
        focusedItemKeyForExpand.value,
        rowHasFocusRef.value,
        isSidebarExpanded,
        expandInteractionNonce.intValue
    ) {
        expandedItemKeyState.value = null
        if (!focusedPosterBackdropExpandEnabled || !rowHasFocusRef.value || isSidebarExpanded) {
            return@LaunchedEffect
        }
        val targetKey = focusedItemKeyForExpand.value ?: return@LaunchedEffect
        val targetId = focusedItemIdForExpand.value ?: return@LaunchedEffect
        if (targetId.startsWith("__placeholder_")) return@LaunchedEffect

        val delayMs = LeanbackCardExpandSpec.computeBackdropExpandDelayMs(
            delaySeconds = focusedPosterBackdropExpandDelaySeconds,
            minDelayMs = LeanbackCardExpandSpec.MIN_CLASSIC_EXPAND_DELAY_MS
        )
        delay(delayMs)
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        if (focusedPosterBackdropExpandEnabled &&
            rowHasFocusRef.value &&
            !isSidebarExpanded &&
            focusedItemKeyForExpand.value == targetKey
        ) {
            expandedItemKeyState.value = targetKey
        }
    }

    LaunchedEffect(
        playTrailerInExpandedCard,
        focusedMetaForExpand.value?.id,
        rowHasFocusRef.value,
        isSidebarExpanded
    ) {
        if (!playTrailerInExpandedCard || !rowHasFocusRef.value || isSidebarExpanded) return@LaunchedEffect
        val meta = focusedMetaForExpand.value ?: return@LaunchedEffect
        if (meta.id.startsWith("__placeholder_")) return@LaunchedEffect
        if (trailerPreviewUrlsState.value.containsKey(meta.id)) return@LaunchedEffect
        delay(LeanbackCardExpandSpec.TRAILER_REQUEST_FOCUS_DEBOUNCE_MS)
        if (playTrailerInExpandedCard &&
            rowHasFocusRef.value &&
            !isSidebarExpanded &&
            focusedMetaForExpand.value?.id == meta.id
        ) {
            latestOnRequestTrailerPreview(meta)
        }
    }

    LaunchedEffect(focusedItemIndex) {
        if (focusedItemIndex >= 0 && catalogRow.items.isNotEmpty()) {
            runCatching { (entryFocusRequester ?: resolvedRowFocusRequester).requestFocus() }
        }
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

    Column(modifier = modifier.fillMaxWidth()) {
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

        val isLandscape = LocalLandscapePosterMode.current || catalogRow.items.firstOrNull()?.posterShape == PosterShape.LANDSCAPE
        val strSeeAll = seeAllLabel ?: stringResource(R.string.action_see_all)
        var watchedMaskHash = 1
        for (i in catalogRow.items.indices) {
            watchedMaskHash = 31 * watchedMaskHash + if (latestIsItemWatched(catalogRow.items[i])) 1231 else 1237
        }
        val leanbackItems = remember(
            catalogRow.items,
            showSeeAll,
            isLandscape,
            strSeeAll,
            showImdbRatings,
            watchedMaskHash,
            focusedPosterBackdropExpandEnabled,
            catalogContext
        ) {
            val list = ArrayList<LeanbackCardItem>(catalogRow.items.size + if (showSeeAll) 1 else 0)
            catalogRow.items.forEachIndexed { index, item ->
                val isPlaceholder = item.id.startsWith("__placeholder_")
                val ratingBadge = if (showImdbRatings && !isPlaceholder) {
                    item.imdbRating?.let { rating ->
                        if (rating > 0f) String.format(java.util.Locale.US, "★ %.1f", rating) else null
                    }
                } else {
                    null
                }
                val metaTokens = if (focusedPosterBackdropExpandEnabled && !isPlaceholder) {
                    LeanbackCardExpandSpec.formatExpandedMetaTokens(
                        apiType = item.apiType,
                        contentType = item.type,
                        genres = item.genres,
                        seasonCount = item.seasonCount,
                        releaseInfo = item.releaseInfo,
                        imdbRating = item.imdbRating,
                        showImdbRatings = showImdbRatings,
                        localizeGenre = { genre -> localizedGenreLabel(catalogContext, genre) }
                    )
                } else {
                    emptyList()
                }
                list.add(
                    LeanbackCardItem.Poster(
                        key = rowItemFocusKey(index, item),
                        title = item.name,
                        imageUrl = if (isLandscape) (item.landscapePoster ?: item.backdropUrl ?: item.poster) else (item.poster ?: item.backdropUrl),
                        fallbackImageUrl = item.rawPosterUrl,
                        subtitle = item.releaseInfo?.takeIf { it.isNotBlank() && !isPlaceholder },
                        isLandscape = isLandscape,
                        badgeText = ratingBadge,
                        isWatched = latestIsItemWatched(item),
                        payload = item,
                        backdropUrl = if (focusedPosterBackdropExpandEnabled && !isPlaceholder) item.backdropUrl else null,
                        landscapePosterUrl = if (focusedPosterBackdropExpandEnabled && !isPlaceholder) item.landscapePoster else null,
                        logoUrl = if (focusedPosterBackdropExpandEnabled && !isPlaceholder) item.logo else null,
                        expandedMetaTokens = metaTokens,
                        ageRating = if (focusedPosterBackdropExpandEnabled && !isPlaceholder) item.ageRating else null,
                        description = if (focusedPosterBackdropExpandEnabled && !isPlaceholder) item.description else null
                    )
                )
            }

            if (showSeeAll) {
                list.add(
                    LeanbackCardItem.Poster(
                        key = "${catalogRow.type}_${catalogRow.catalogId}_see_all",
                        title = strSeeAll,
                        imageUrl = null,
                        isLandscape = isLandscape,
                        isSeeAll = true,
                        payload = "SEE_ALL"
                    )
                )
            }
            list
        }

        val targetFocusIndex = when {
            focusedItemIndex >= 0 -> focusedItemIndex
            restorerFocusedIndex in catalogRow.items.indices -> restorerFocusedIndex
            lastFocusedItemIndex.intValue in catalogRow.items.indices -> lastFocusedItemIndex.intValue
            else -> 0
        }

        var pendingFocusConsumed by remember(catalogRowKey, focusedItemIndex) { mutableStateOf(false) }
        var lastHandledResetNonce by remember(catalogRowKey) { mutableIntStateOf(0) }
        val isResetPending = resetToStartNonce > lastHandledResetNonce
        val openSidebar = LocalOpenSidebar.current

        LeanbackNativeRow(
            modifier = Modifier
                .onFocusChanged {
                    rowHasFocusRef.value = it.hasFocus
                    if (!it.hasFocus) {
                        expandedItemKeyState.value = null
                    }
                }
                .focusRequester(resolvedRowFocusRequester),
            entryFocusRequester = entryFocusRequester,
            rowKey = "catalog_row_${catalogRow.stableKey()}",
            items = leanbackItems,
            cardWidth = if (isLandscape) 260.dp else posterCardStyle.width,
            cardHeight = if (isLandscape) 146.dp else posterCardStyle.height,
            cornerRadius = posterCardStyle.cornerRadius,
            showLabels = showPosterLabels,
            animationsEnabled = NuvioTheme.animationsEnabled,
            startPadding = NuvioTheme.spacing.xxxl,
            spacing = NuvioTheme.spacing.lg,
            focusedPosterBackdropExpandEnabled = focusedPosterBackdropExpandEnabled,
            expandedItemKeyState = if (focusedPosterBackdropExpandEnabled) expandedItemKeyState else null,
            alwaysShowLandscapeClearlogo = alwaysShowLandscapeClearlogo,
            playTrailerInExpandedCard = playTrailerInExpandedCard,
            trailerMuted = focusedPosterBackdropTrailerMuted,
            expandedTrailerUrlProvider = {
                val itemId = focusedItemIdForExpand.value
                if (playTrailerInExpandedCard && itemId != null) trailerPreviewUrlsState.value[itemId] else null
            },
            expandedTrailerAudioUrlProvider = {
                val itemId = focusedItemIdForExpand.value
                if (playTrailerInExpandedCard && itemId != null) trailerPreviewAudioUrlsState.value[itemId] else null
            },
            expandedTrailerHost = expandedTrailerHost,
            onBackdropInteraction = if (focusedPosterBackdropExpandEnabled) {
                {
                    expandedItemKeyState.value = null
                    expandInteractionNonce.intValue++
                }
            } else null,
            onExpandedTrailerEnded = {
                expandedItemKeyState.value = null
            },
            onItemFocused = { index, cardItem ->
                if (cardItem.payload == "SEE_ALL") {
                    focusedItemKeyForExpand.value = null
                    focusedItemIdForExpand.value = null
                    focusedMetaForExpand.value = null
                    expandedItemKeyState.value = null
                    lastFocusedItemIndex.intValue = index
                    latestOnItemFocused(index)
                    return@LeanbackNativeRow
                }
                val item = cardItem.payload as? MetaPreview ?: return@LeanbackNativeRow
                focusedItemKeyForExpand.value = cardItem.key
                focusedItemIdForExpand.value = item.id
                focusedMetaForExpand.value = item
                latestOnItemFocus(item)
                lastFocusedItemIndex.intValue = index
                latestOnItemFocused(index)
            },
            onItemClick = { cardItem ->
                if (cardItem.payload == "SEE_ALL") {
                    latestOnSeeAll()
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
            initialFocusPosition = targetFocusIndex,
            isPendingFocus = (focusedItemIndex >= 0 && !pendingFocusConsumed) || isResetPending,
            pendingFocusIndex = if (isResetPending) 0 else targetFocusIndex,
            pendingFocusNonce = resetToStartNonce,
            onClearPendingFocus = {
                pendingFocusConsumed = true
                if (isResetPending) {
                    lastHandledResetNonce = resetToStartNonce
                }
            }
        )
    }
}
