package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.ui.leanback.LeanbackNativeGrid
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.screens.home.HeroBackdropState

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.nuvio.tv.ui.util.contentTextDirection
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.GridContentCard
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.components.LocalCardDepthStyle
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.components.nuvioCardDepth
import com.nuvio.tv.domain.model.CardDepthSurface
import com.nuvio.tv.ui.util.dpadVerticalFastScroll
import com.nuvio.tv.ui.util.formatAddonTypeLabel
import com.nuvio.tv.ui.util.localizedContentType
import com.nuvio.tv.ui.util.localizedGenreLabel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun DiscoverSection(
    uiState: SearchUiState,
    posterCardStyle: PosterCardStyle,
    watchedMovieIds: Set<String> = emptySet(),
    watchedSeriesIds: Set<String> = emptySet(),
    focusResults: Boolean,
    showBuiltInHeader: Boolean = true,
    firstItemFocusRequester: FocusRequester,
    focusedItemIndex: Int,
    shouldRestoreFocusedItem: Boolean,
    blockFilterFocus: Boolean = false,
    onRestoreFocusedItemHandled: () -> Unit,
    onNavigateToDetail: (String, String, String) -> Unit,
    onDiscoverItemFocused: (Int) -> Unit,
    onSelectType: (String) -> Unit,
    onSelectCatalog: (String) -> Unit,
    onSelectGenre: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onItemLongPress: (MetaPreview, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val selectedCatalog = uiState.discoverCatalogs.firstOrNull { it.key == uiState.selectedDiscoverCatalogKey }
    val filteredCatalogs = uiState.discoverCatalogs.filter { it.type == uiState.selectedDiscoverType }
    val genres = selectedCatalog?.genres.orEmpty()
    var expandedPicker by remember { mutableStateOf<String?>(null) }
    val filterFocusRequester = remember { FocusRequester() }
    val openSidebar = com.nuvio.tv.LocalOpenSidebar.current
    var gridHasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!focusResults && !shouldRestoreFocusedItem) {
            repeat(3) { withFrameNanos { } }
            runCatching { filterFocusRequester.requestFocus() }
        }
    }

    androidx.activity.compose.BackHandler(enabled = gridHasFocus) {
        try { filterFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    val localContext = LocalContext.current
    fun localizedTypeLabel(type: String): String = localizedContentType(localContext, type)

    val availableTypes = remember(uiState.discoverCatalogs) {
        uiState.discoverCatalogs.map { it.type }.distinct()
    }
    val selectedTypeLabel = localizedTypeLabel(uiState.selectedDiscoverType)
    val selectedCatalogLabel = selectedCatalog?.catalogName ?: stringResource(R.string.discover_select_catalog)
    val selectedGenreLabel = uiState.selectedDiscoverGenre?.let { localizedGenreLabel(it) } ?: stringResource(R.string.discover_genre_default)

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NuvioTheme.colors.Background,
                            NuvioTheme.colors.Background,
                            NuvioTheme.colors.Background.copy(alpha = 0.95f),
                            Color.Transparent
                        )
                    )
                )
                .zIndex(10f)
                .padding(
                    start = NuvioTheme.spacing.xxxl,
                    end = NuvioTheme.spacing.xxxl,
                    bottom = NuvioTheme.spacing.md
                ),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
        ) {
            Text(
                text = stringResource(R.string.discover_title),
                style = MaterialTheme.typography.headlineMedium,
                color = if (showBuiltInHeader) NuvioTheme.colors.TextPrimary else Color.Transparent
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
            ) {
                DiscoverDropdownPicker(
                    modifier = Modifier.weight(1f),
                    cardModifier = Modifier
                        .focusRequester(filterFocusRequester)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.key == Key.DirectionLeft && keyEvent.type == KeyEventType.KeyDown && expandedPicker == null) {
                                if (openSidebar != null) {
                                    openSidebar()
                                    return@onPreviewKeyEvent true
                                }
                            }
                            false
                        },
                    title = stringResource(R.string.discover_filter_type),
                    value = selectedTypeLabel,
                    selectedValue = uiState.selectedDiscoverType,
                    expanded = expandedPicker == "type",
                    options = availableTypes.map { type ->
                        val label = localizedTypeLabel(type)
                        DiscoverOption(label, type)
                    },
                    onExpandedChange = { shouldExpand ->
                        expandedPicker = if (shouldExpand) "type" else null
                    },
                    onSelect = { option ->
                        onSelectType(option.value)
                        expandedPicker = null
                    },
                    blockFocus = blockFilterFocus
                )

                DiscoverDropdownPicker(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.discover_filter_catalog),
                    value = selectedCatalogLabel,
                    selectedValue = uiState.selectedDiscoverCatalogKey,
                    expanded = expandedPicker == "catalog",
                    options = filteredCatalogs.map { DiscoverOption(it.catalogName, it.key) },
                    onExpandedChange = { shouldExpand ->
                        expandedPicker = if (shouldExpand) "catalog" else null
                    },
                    onSelect = { option ->
                        onSelectCatalog(option.value)
                        expandedPicker = null
                    },
                    blockFocus = blockFilterFocus
                )

                DiscoverDropdownPicker(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.discover_filter_genre),
                    value = selectedGenreLabel,
                    selectedValue = uiState.selectedDiscoverGenre ?: "__default__",
                    expanded = expandedPicker == "genre",
                    options = buildList {
                        add(DiscoverOption(stringResource(R.string.discover_genre_default), "__default__"))
                        addAll(genres.map { DiscoverOption(localizedGenreLabel(it), it) })
                    },
                    onExpandedChange = { shouldExpand ->
                        expandedPicker = if (shouldExpand) "genre" else null
                    },
                    onSelect = { option ->
                        onSelectGenre(option.value.takeUnless { it == "__default__" })
                        expandedPicker = null
                    },
                    blockFocus = blockFilterFocus
                )
            }

            selectedCatalog?.let { catalog ->
                val metadataSegments = buildList {
                    add(catalog.addonName)
                    if (uiState.catalogTypeSuffixEnabled) {
                        localizedTypeLabel(catalog.type)
                            .takeIf { it.isNotEmpty() }
                            ?.let(::add)
                    }
                    uiState.selectedDiscoverGenre?.let { add(localizedGenreLabel(it)) }
                }
                Text(
                    text = metadataSegments.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                uiState.discoverLoading && uiState.discoverResults.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 28.dp, bottom = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }

                uiState.discoverResults.isNotEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onFocusChanged { gridHasFocus = it.hasFocus }
                    ) {
                        DiscoverGrid(
                            items = uiState.discoverResults,
                            posterCardStyle = posterCardStyle,
                            watchedMovieIds = watchedMovieIds,
                            watchedSeriesIds = watchedSeriesIds,
                            focusResults = focusResults,
                            firstItemFocusRequester = firstItemFocusRequester,
                            focusedItemIndex = focusedItemIndex,
                            shouldRestoreFocusedItem = shouldRestoreFocusedItem,
                            filterFocusRequester = filterFocusRequester,
                            onRestoreFocusedItemHandled = onRestoreFocusedItemHandled,
                            onItemFocused = onDiscoverItemFocused,
                            pendingCount = uiState.pendingDiscoverResults.size,
                            canLoadMore = uiState.discoverHasMore,
                            isLoadingMore = uiState.discoverLoadingMore,
                            onLoadMore = onLoadMore,
                            onItemClick = { _, item ->
                                HeroBackdropState.update(item.backdropUrl)
                                onNavigateToDetail(
                                    item.id,
                                    item.apiType,
                                    selectedCatalog?.addonBaseUrl ?: ""
                                )
                            },
                            onItemLongPress = { item ->
                                onItemLongPress(item, selectedCatalog?.addonBaseUrl ?: "")
                            },
                            filterKey = "${uiState.selectedDiscoverType}|${uiState.selectedDiscoverCatalogKey}|${uiState.selectedDiscoverGenre}"
                        )
                    }
                }

                uiState.discoverInitialized && selectedCatalog == null -> {
                    EmptyScreenState(
                        title = stringResource(R.string.discover_empty_no_catalog_title),
                        subtitle = stringResource(R.string.discover_empty_no_catalog_subtitle),
                        icon = Icons.Default.Search
                    )
                }

                uiState.discoverInitialized && !uiState.discoverLoading && selectedCatalog != null -> {
                    EmptyScreenState(
                        title = stringResource(R.string.discover_empty_no_content_title),
                        subtitle = stringResource(R.string.discover_empty_no_content_subtitle),
                        icon = Icons.Default.Search
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DiscoverDropdownPicker(
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    title: String,
    value: String,
    selectedValue: String?,
    expanded: Boolean,
    options: List<DiscoverOption>,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (DiscoverOption) -> Unit,
    blockFocus: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    // Seed focused option with the current selection so reopen highlights the right row
    // even before focus lands (fixes #2848 / incomplete #2507 race on TV).
    var focusedOptionValue by remember(expanded) {
        mutableStateOf(if (expanded) selectedValue else null)
    }
    val selectedItemFocusRequester = remember { FocusRequester() }
    val selectedBringIntoViewRequester = remember { BringIntoViewRequester() }

    // Popup content attaches focus targets a few frames after expand. A fixed 50ms
    // delay was flaky on TV and left focus on the first item for non-top selections.
    LaunchedEffect(expanded, selectedValue) {
        if (!expanded || selectedValue == null) return@LaunchedEffect
        var focused = selectedItemFocusRequester.requestFocusAfterFrames(frames = 3)
        var attempt = 0
        while (!focused && attempt < 6) {
            delay(32)
            focused = runCatching { selectedItemFocusRequester.requestFocus() }.getOrDefault(false)
            attempt++
        }
        if (!focused) return@LaunchedEffect
        runCatching { selectedBringIntoViewRequester.bringIntoView() }
        // Material DropdownMenu may still move initial focus to the first item after
        // the popup settles; re-assert once so long lists keep the real selection.
        delay(48)
        if (runCatching { selectedItemFocusRequester.requestFocus() }.getOrDefault(false)) {
            runCatching { selectedBringIntoViewRequester.bringIntoView() }
        }
    }

    Box(modifier = modifier) {
        Card(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .then(cardModifier)
                .onSizeChanged { anchorSize = it }
                .onFocusChanged { state ->
                    isFocused = state.isFocused
                }
                .then(
                    if (blockFocus) Modifier.focusProperties { canFocus = false }
                    else Modifier
                ),
            shape = CardDefaults.shape(shape = RoundedCornerShape(14.dp)),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                focusedContainerColor = NuvioTheme.colors.FocusBackground
            ),
            border = CardDefaults.border(
                border = Border(
                    border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                    shape = RoundedCornerShape(14.dp)
                ),
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                    shape = RoundedCornerShape(14.dp)
                )
            ),
            scale = CardDefaults.scale(
                focusedScale = 1.0f,
                pressedScale = 1.0f
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xxs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = NuvioTheme.colors.TextTertiary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDirection = value.contentTextDirection()
                        ),
                        color = NuvioTheme.colors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) stringResource(R.string.cd_collapse, title) else stringResource(R.string.cd_expand, title),
                        modifier = Modifier.size(20.dp),
                        tint = if (isFocused) NuvioTheme.colors.FocusRing else NuvioTheme.colors.TextSecondary
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                focusedOptionValue = null
                onExpandedChange(false)
            },
            modifier = Modifier
                .width(with(LocalDensity.current) { anchorSize.width.toDp() })
                .heightIn(max = 320.dp),
            shape = RoundedCornerShape(14.dp),
            containerColor = NuvioTheme.colors.BackgroundCard,
            tonalElevation = NuvioTheme.spacing.none,
            shadowElevation = NuvioTheme.spacing.sm,
            border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border)
        ) {
            options.forEach { option ->
                val isSelected = option.value == selectedValue
                val isOptionFocused = option.value == focusedOptionValue
                val itemTextColor = when {
                    isOptionFocused -> NuvioTheme.colors.OnSecondary
                    isSelected -> NuvioTheme.colors.TextPrimary
                    else -> NuvioTheme.colors.TextPrimary
                }
                val itemBackgroundColor = when {
                    isOptionFocused -> NuvioTheme.colors.Secondary
                    isSelected -> NuvioTheme.colors.FocusBackground
                    else -> Color.Transparent
                }

                DropdownMenuItem(
                    modifier = Modifier
                        .then(
                            if (isSelected) {
                                Modifier
                                    .focusRequester(selectedItemFocusRequester)
                                    .bringIntoViewRequester(selectedBringIntoViewRequester)
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = NuvioTheme.spacing.xxs)
                        .background(
                            color = itemBackgroundColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .onFocusChanged { state ->
                            val hasFocus = state.isFocused || state.hasFocus
                            focusedOptionValue = when {
                                hasFocus -> option.value
                                focusedOptionValue == option.value -> null
                                else -> focusedOptionValue
                            }
                        },
                    text = {
                        Text(
                            text = option.label,
                            color = itemTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                textDirection = option.label.contentTextDirection()
                            )
                        )
                    },
                    onClick = { onSelect(option) },
                    colors = MenuDefaults.itemColors(
                        textColor = itemTextColor,
                        disabledTextColor = NuvioTheme.colors.TextDisabled
                    )
                )
            }
        }
    }
}

private data class DiscoverOption(
    val label: String,
    val value: String
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun DiscoverGrid(
    items: List<MetaPreview>,
    posterCardStyle: PosterCardStyle,
    watchedMovieIds: Set<String> = emptySet(),
    watchedSeriesIds: Set<String> = emptySet(),
    focusResults: Boolean,
    firstItemFocusRequester: FocusRequester,
    focusedItemIndex: Int,
    shouldRestoreFocusedItem: Boolean,
    onRestoreFocusedItemHandled: () -> Unit,
    onItemFocused: (Int) -> Unit,
    pendingCount: Int,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onItemClick: (Int, MetaPreview) -> Unit,
    onItemLongPress: (MetaPreview) -> Unit = {},
    filterKey: String = "",
    filterFocusRequester: FocusRequester? = null
) {
    val openSidebar = com.nuvio.tv.LocalOpenSidebar.current
    val globalLandscape = com.nuvio.tv.ui.components.LocalLandscapePosterMode.current
    val numCols = if (globalLandscape) 4 else 6
    val effectiveWidth = if (globalLandscape) 260.dp else posterCardStyle.width
    val effectiveHeight = if (globalLandscape) 146.dp else (posterCardStyle.width * 1.5f)

    val leanbackItems = remember(items, globalLandscape, watchedMovieIds, watchedSeriesIds) {
        items.mapIndexed { index, item ->
            val isSeries = item.apiType.equals("series", ignoreCase = true) || item.apiType.equals("tv", ignoreCase = true)
            val isWatched = if (isSeries) item.id in watchedSeriesIds else item.id in watchedMovieIds
            LeanbackCardItem.Poster(
                key = item.id.ifEmpty { "discover_${filterKey}_$index" },
                title = item.name,
                imageUrl = if (globalLandscape) (item.landscapePoster ?: item.backdropUrl ?: item.poster) else (item.poster ?: item.backdropUrl),
                isLandscape = globalLandscape,
                isWatched = isWatched,
                payload = Pair(index, item)
            )
        }
    }

    val initialPos = when {
        shouldRestoreFocusedItem && focusedItemIndex in items.indices -> focusedItemIndex
        focusResults && items.isNotEmpty() -> 0
        else -> focusedItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(shouldRestoreFocusedItem) {
        if (shouldRestoreFocusedItem) {
            onRestoreFocusedItemHandled()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LeanbackNativeGrid(
            gridKey = "discover_grid_$filterKey",
            items = leanbackItems,
            numColumns = numCols,
            cardWidth = effectiveWidth,
            cardHeight = effectiveHeight,
            cornerRadius = posterCardStyle.cornerRadius,
            showLabels = true,
            animationsEnabled = NuvioTheme.animationsEnabled,
            paddingValues = PaddingValues(
                start = NuvioTheme.spacing.xxxl,
                end = NuvioTheme.spacing.xxxl,
                top = NuvioTheme.spacing.md,
                bottom = NuvioTheme.spacing.xxl
            ),
            horizontalSpacing = 10.dp,
            verticalSpacing = NuvioTheme.spacing.lg,
            initialFocusPosition = initialPos,
            onItemFocused = { _, cardItem ->
                val pair = cardItem.payload as? Pair<*, *> ?: return@LeanbackNativeGrid
                val index = pair.first as? Int ?: return@LeanbackNativeGrid
                onItemFocused(index)
            },
            onItemClick = { cardItem ->
                val pair = cardItem.payload as? Pair<*, *> ?: return@LeanbackNativeGrid
                val index = pair.first as? Int ?: return@LeanbackNativeGrid
                val item = pair.second as? MetaPreview ?: return@LeanbackNativeGrid
                onItemClick(index, item)
            },
            onItemLongClick = { cardItem ->
                val pair = cardItem.payload as? Pair<*, *> ?: return@LeanbackNativeGrid
                val item = pair.second as? MetaPreview ?: return@LeanbackNativeGrid
                onItemLongPress(item)
            },
            onLoadMore = {
                if (canLoadMore && !isLoadingMore) {
                    onLoadMore()
                }
            },
            onNavigateUpOnFirstRow = {
                filterFocusRequester?.let {
                    runCatching { it.requestFocus() }.isSuccess
                } ?: false
            },
            onNavigateLeftOnFirstColumn = {
                if (openSidebar != null) {
                    openSidebar()
                    true
                } else {
                    false
                }
            }
        )
    }
}

private sealed class DiscoverGridAction {
    object None : DiscoverGridAction()
    object ShowMore : DiscoverGridAction()
    object LoadMore : DiscoverGridAction()
    object Loading : DiscoverGridAction()
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DiscoverActionCard(
    actionType: DiscoverGridAction,
    posterCardStyle: PosterCardStyle,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(posterCardStyle.cornerRadius)
    val title = when (actionType) {
        DiscoverGridAction.ShowMore -> stringResource(R.string.discover_load_more)
        DiscoverGridAction.LoadMore -> stringResource(R.string.discover_load_more)
        DiscoverGridAction.Loading -> stringResource(R.string.discover_loading)
        DiscoverGridAction.None -> ""
    }
    val cardDepthStyle = LocalCardDepthStyle.current
    val globalLandscape = com.nuvio.tv.ui.components.LocalLandscapePosterMode.current

    Column(
        modifier = modifier.then(if (globalLandscape) Modifier.fillMaxWidth() else Modifier.width(posterCardStyle.width))
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .then(
                    if (globalLandscape) Modifier.fillMaxWidth().aspectRatio(com.nuvio.tv.domain.model.PosterShape.LANDSCAPE.aspectRatio())
                    else Modifier.width(posterCardStyle.width).height(posterCardStyle.height)
                )
                .focusProperties { canFocus = actionType != DiscoverGridAction.Loading }
                .onPreviewKeyEvent { event ->
                    actionType != DiscoverGridAction.None &&
                        event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                        event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT
                }
                .onFocusChanged { state -> if (state.isFocused) onFocused() }
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                ),
            shape = CardDefaults.shape(shape = cardShape),
            colors = CardDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundCard,
                focusedContainerColor = NuvioTheme.colors.FocusBackground
            ),
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = NuvioTheme.posterFocusRing.border(posterCardStyle.focusedBorderWidth),
                    shape = cardShape
                )
            ),
            scale = CardDefaults.scale(focusedScale = posterCardStyle.focusedScale)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
                    .nuvioCardDepth(
                        shape = cardShape,
                        surface = CardDepthSurface.POSTERS,
                        style = cardDepthStyle
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (actionType == DiscoverGridAction.Loading) {
                    LoadingIndicator()
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioTheme.colors.TextPrimary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        // Reserve space for label to match GridContentCard height
        Spacer(
            modifier = Modifier
                .then(if (globalLandscape) Modifier.fillMaxWidth() else Modifier.width(posterCardStyle.width))
                .padding(top = NuvioTheme.spacing.sm)
                .height(MaterialTheme.typography.titleMedium.lineHeight.value.dp)
        )
    }
}
