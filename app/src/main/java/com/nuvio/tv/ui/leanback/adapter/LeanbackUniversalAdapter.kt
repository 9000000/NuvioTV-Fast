package com.nuvio.tv.ui.leanback.adapter

import android.content.Context
import android.os.SystemClock
import android.view.ViewGroup
import androidx.leanback.widget.BaseGridView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil3.SingletonImageLoader
import com.nuvio.tv.core.image.HomeImageLoadingProfile
import com.nuvio.tv.domain.model.isPlaceholder
import com.nuvio.tv.ui.leanback.container.InteropHorizontalGridView
import com.nuvio.tv.ui.leanback.core.HorizontalPrefetchProfileCache
import com.nuvio.tv.ui.leanback.core.LeanbackArtworkLoader
import com.nuvio.tv.ui.leanback.core.LeanbackExpandedTrailerHost
import com.nuvio.tv.ui.leanback.core.LeanbackThemeStyle
import com.nuvio.tv.ui.leanback.holder.LeanbackAvatarViewHolder
import com.nuvio.tv.ui.leanback.holder.LeanbackBaseViewHolder
import com.nuvio.tv.ui.leanback.holder.LeanbackEpisodeViewHolder
import com.nuvio.tv.ui.leanback.holder.LeanbackPosterViewHolder
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.leanback.pool.LeanbackPoolManager

/**
 * Universal polymorphic RecyclerView Adapter for all Android Leanback Cards.
 * Supports Poster, Episode, and Avatar items with O(N) in-place diffing for large grids,
 * dynamic dimension updating, unified MemoryCache prefetching, and focus lifecycle guards.
 */
class LeanbackUniversalAdapter(
    var cardWidthPx: Int,
    var cardHeightPx: Int,
    var cornerRadiusPx: Float,
    var showLabels: Boolean,
    var animationsEnabled: () -> Boolean,
    var onItemFocused: (Int, LeanbackCardItem) -> Unit,
    var onItemClick: (LeanbackCardItem) -> Unit,
    var onItemLongClick: (LeanbackCardItem) -> Unit,
    var onLoadMore: () -> Unit,
    var themeStyle: LeanbackThemeStyle = LeanbackThemeStyle.Default
) : RecyclerView.Adapter<LeanbackBaseViewHolder>() {

    private var items: List<LeanbackCardItem> = emptyList()
    private var pendingItems: List<LeanbackCardItem>? = null
    private var pendingNotifyOnAttach: Boolean = false
    private var lastLoadMoreTimestampMs: Long = 0L
    private var lastLoadMoreItemCount: Int = -1
    private var pendingFocusedPosition: Int = RecyclerView.NO_POSITION
    private var pendingFocusedItem: LeanbackCardItem? = null
    private var lastDispatchedFocusedPosition: Int = RecyclerView.NO_POSITION
    private var lastDispatchedFocusedKey: String? = null
    private var lastHorizontalPrefetchPosition: Int = RecyclerView.NO_POSITION
    var attachedRecyclerView: RecyclerView? = null
    var isSubmittingItems: Boolean = false
        private set

    var backdropExpandEnabled: Boolean = false
    var alwaysShowLandscapeClearlogo: Boolean = false
    var playTrailerInExpandedCard: Boolean = false
    var trailerMuted: Boolean = true
    var expandedTrailerHost: LeanbackExpandedTrailerHost? = null
    var onExpandedTrailerEnded: () -> Unit = {}

    var expandedItemKey: String? = null
        private set
    var expandedTrailerUrl: String? = null
        private set
    var expandedTrailerAudioUrl: String? = null
        private set

    val hasExpandedCard: Boolean
        get() {
            val rv = attachedRecyclerView ?: return false
            for (i in 0 until rv.childCount) {
                val vh = rv.getChildViewHolder(rv.getChildAt(i)) as? LeanbackPosterViewHolder ?: continue
                if (vh.isBackdropExpanded) return true
            }
            return false
        }

    val hasAnimatingWidthCard: Boolean
        get() {
            val rv = attachedRecyclerView ?: return false
            for (i in 0 until rv.childCount) {
                val vh = rv.getChildViewHolder(rv.getChildAt(i)) as? LeanbackPosterViewHolder ?: continue
                if (vh.isAnimatingWidth) return true
            }
            return false
        }

    fun findExpandedOrAnimatingHolder(): LeanbackPosterViewHolder? {
        val rv = attachedRecyclerView ?: return null
        for (i in 0 until rv.childCount) {
            val vh = rv.getChildViewHolder(rv.getChildAt(i)) as? LeanbackPosterViewHolder ?: continue
            if (vh.isBackdropExpanded || vh.isAnimatingWidth) return vh
        }
        return null
    }

    fun updateExpandedCardState(
        expandedKey: String?,
        trailerUrl: String?,
        trailerAudioUrl: String?
    ) {
        expandedItemKey = if (backdropExpandEnabled) expandedKey else null
        expandedTrailerUrl = if (playTrailerInExpandedCard) trailerUrl else null
        expandedTrailerAudioUrl = if (playTrailerInExpandedCard) trailerAudioUrl else null

        val rv = attachedRecyclerView ?: return
        val targetKey = expandedItemKey
        val grid = rv as? InteropHorizontalGridView
        val allowAnimatedCollapse = animationsEnabled() &&
            grid?.isHoldingHorizontalKey != true &&
            (grid == null || grid.scrollState == RecyclerView.SCROLL_STATE_IDLE)
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i) ?: continue
            val vh = rv.getChildViewHolder(child) as? LeanbackPosterViewHolder ?: continue
            val pos = vh.bindingAdapterPosition
            val item = if (pos in items.indices) items[pos] as? LeanbackCardItem.Poster else null
            val shouldExpand = targetKey != null &&
                backdropExpandEnabled &&
                item != null &&
                !item.isSeeAll &&
                item.key == targetKey &&
                vh.root.hasFocus()
            if (shouldExpand) {
                vh.applyExpandedState(
                    expanded = true,
                    animated = true,
                    trailerUrl = expandedTrailerUrl,
                    trailerAudioUrl = expandedTrailerAudioUrl
                )
            } else if (vh.isBackdropExpanded) {
                vh.applyExpandedState(expanded = false, animated = allowAnimatedCollapse)
            }
        }
    }

    fun collapseExpandedCard(animated: Boolean) {
        val prevKey = expandedItemKey
        expandedItemKey = null
        expandedTrailerUrl = null
        expandedTrailerAudioUrl = null
        if (prevKey != null) {
            expandedTrailerHost?.detach(prevKey)
        }
        val rv = attachedRecyclerView ?: return
        for (i in 0 until rv.childCount) {
            val child = rv.getChildAt(i) ?: continue
            val vh = rv.getChildViewHolder(child) as? LeanbackPosterViewHolder ?: continue
            if (vh.isBackdropExpanded || (!animated && vh.isAnimatingWidth)) {
                if (animated && animationsEnabled()) {
                    vh.collapseToBaseWidth(animated = true)
                } else {
                    vh.resetExpandedStateImmediate()
                    if (vh.root.hasFocus()) {
                        vh.updateFocusVisual(true)
                    }
                }
            }
        }
    }

    fun collapseExpandedCardImmediate() {
        collapseExpandedCard(animated = false)
    }

    fun dispatchItemFocused(position: Int, item: LeanbackCardItem) {
        if (position !in items.indices) return
        val gridView = attachedRecyclerView as? InteropHorizontalGridView
        if (gridView?.isHoldingHorizontalKey == true) {
            pendingFocusedPosition = position
            pendingFocusedItem = item
            return
        }
        pendingFocusedPosition = RecyclerView.NO_POSITION
        pendingFocusedItem = null
        lastDispatchedFocusedPosition = position
        lastDispatchedFocusedKey = item.key
        onItemFocused(position, item)
    }

    fun flushPendingFocusedItem() {
        val pos = pendingFocusedPosition
        val pending = pendingFocusedItem
        pendingFocusedPosition = RecyclerView.NO_POSITION
        pendingFocusedItem = null
        if (pos in items.indices) {
            val resolvedItem = items[pos]
            if (pos != lastDispatchedFocusedPosition || resolvedItem.key != lastDispatchedFocusedKey) {
                lastDispatchedFocusedPosition = pos
                lastDispatchedFocusedKey = resolvedItem.key
                onItemFocused(pos, resolvedItem)
            }
            return
        }
        if (pending != null) {
            val relocatedPos = items.indexOfFirst { it.key == pending.key }
            if (relocatedPos >= 0) {
                val resolvedItem = items[relocatedPos]
                if (relocatedPos != lastDispatchedFocusedPosition || resolvedItem.key != lastDispatchedFocusedKey) {
                    lastDispatchedFocusedPosition = relocatedPos
                    lastDispatchedFocusedKey = resolvedItem.key
                    onItemFocused(relocatedPos, resolvedItem)
                }
                return
            }
        }
        val gridView = attachedRecyclerView as? InteropHorizontalGridView
        val selectedPos = gridView?.selectedPosition ?: RecyclerView.NO_POSITION
        if (selectedPos in items.indices && gridView?.hasFocus() == true && gridView.mayTakeFocus()) {
            val selectedItem = items[selectedPos]
            if (selectedPos != lastDispatchedFocusedPosition || selectedItem.key != lastDispatchedFocusedKey) {
                lastDispatchedFocusedPosition = selectedPos
                lastDispatchedFocusedKey = selectedItem.key
                onItemFocused(selectedPos, selectedItem)
            }
        }
    }

    fun requestLoadMore() {
        val currentCount = items.size
        if (currentCount <= 0) return
        val now = SystemClock.uptimeMillis()
        if (currentCount == lastLoadMoreItemCount && now - lastLoadMoreTimestampMs < LOAD_MORE_DEBOUNCE_MS) {
            return
        }
        lastLoadMoreTimestampMs = now
        lastLoadMoreItemCount = currentCount
        onLoadMore()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
        val pending = pendingItems
        if (pending != null) {
            pendingItems = null
            applyItemsUpdate(pending)
        } else if (items.isNotEmpty() || pendingNotifyOnAttach) {
            pendingNotifyOnAttach = false
            safeNotifyDataSetChanged()
        }
        scheduleInitialEdgePrefetch(recyclerView, items)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        collapseExpandedCardImmediate()
        super.onDetachedFromRecyclerView(recyclerView)
        if (attachedRecyclerView === recyclerView) {
            attachedRecyclerView = null
        }
        pendingNotifyOnAttach = false
    }

    fun onHostAttached() {
        val pending = pendingItems
        if (pending != null) {
            flushPendingUpdates()
            return
        }
        val rv = attachedRecyclerView ?: return
        if ((pendingNotifyOnAttach || (items.isNotEmpty() && rv.childCount == 0)) && rv.isAttachedToWindow) {
            pendingNotifyOnAttach = false
            safeNotifyDataSetChanged()
        }
        scheduleInitialEdgePrefetch(rv, items)
    }

    fun flushPendingUpdates() {
        val pending = pendingItems ?: run {
            if (pendingNotifyOnAttach) {
                pendingNotifyOnAttach = false
                safeNotifyDataSetChanged()
            }
            return
        }
        val rv = attachedRecyclerView
        if (rv != null && (rv.isComputingLayout || rv.isInLayout)) {
            rv.post { flushPendingUpdates() }
            return
        }
        pendingItems = null
        pendingNotifyOnAttach = false
        applyItemsUpdate(pending)
    }

    fun getItem(position: Int): LeanbackCardItem? = items.getOrNull(position)

    fun updateConfig(
        newWidthPx: Int,
        newHeightPx: Int,
        newRadiusPx: Float,
        newShowLabels: Boolean,
        newThemeStyle: LeanbackThemeStyle = themeStyle
    ) {
        val changed = cardWidthPx != newWidthPx ||
            cardHeightPx != newHeightPx ||
            cornerRadiusPx != newRadiusPx ||
            showLabels != newShowLabels ||
            themeStyle != newThemeStyle
        if (changed) {
            cardWidthPx = newWidthPx
            cardHeightPx = newHeightPx
            cornerRadiusPx = newRadiusPx
            showLabels = newShowLabels
            themeStyle = newThemeStyle
            if (items.isNotEmpty()) {
                val rv = attachedRecyclerView
                if (rv != null && (rv.isComputingLayout || rv.isInLayout)) {
                    rv.post {
                        if (attachedRecyclerView === rv && items.isNotEmpty()) {
                            notifyItemRangeChanged(0, items.size)
                        }
                    }
                } else {
                    notifyItemRangeChanged(0, items.size)
                }
            }
        }
    }

    private fun hasSameCardVisuals(a: LeanbackCardItem, b: LeanbackCardItem): Boolean {
        if (a === b) return true
        if (a.key != b.key || a.title != b.title || a.imageUrl != b.imageUrl) return false
        return when {
            a is LeanbackCardItem.Poster && b is LeanbackCardItem.Poster ->
                a.fallbackImageUrl == b.fallbackImageUrl &&
                    a.subtitle == b.subtitle &&
                    a.isLandscape == b.isLandscape &&
                    a.progressPercent == b.progressPercent &&
                    a.badgeText == b.badgeText &&
                    a.isWatched == b.isWatched &&
                    a.isSeeAll == b.isSeeAll &&
                    a.backdropUrl == b.backdropUrl &&
                    a.landscapePosterUrl == b.landscapePosterUrl &&
                    a.logoUrl == b.logoUrl &&
                    a.expandedMetaTokens == b.expandedMetaTokens &&
                    a.ageRating == b.ageRating &&
                    a.description == b.description
            a is LeanbackCardItem.Episode && b is LeanbackCardItem.Episode ->
                a.description == b.description &&
                    a.episodeNumber == b.episodeNumber &&
                    a.seasonNumber == b.seasonNumber &&
                    a.episodeLabel == b.episodeLabel &&
                    a.runtimeMinutes == b.runtimeMinutes &&
                    a.progressPercent == b.progressPercent &&
                    a.isWatched == b.isWatched &&
                    a.releaseDate == b.releaseDate &&
                    a.imdbRating == b.imdbRating &&
                    a.isAvailable == b.isAvailable &&
                    a.blurThumbnail == b.blurThumbnail
            a is LeanbackCardItem.Avatar && b is LeanbackCardItem.Avatar ->
                a.subtitle == b.subtitle
            else -> false
        }
    }

    private fun itemsMatchVisuals(a: List<LeanbackCardItem>, b: List<LeanbackCardItem>, count: Int): Boolean {
        for (i in 0 until count) {
            if (!hasSameCardVisuals(a[i], b[i])) return false
        }
        return true
    }

    fun submitItems(newItems: List<LeanbackCardItem>) {
        if (items === newItems && pendingItems == null && !pendingNotifyOnAttach) return
        if (!pendingNotifyOnAttach && items.size == newItems.size && itemsMatchVisuals(items, newItems, items.size)) {
            items = newItems
            pendingItems = null
            return
        }
        pendingItems = newItems

        val rv = attachedRecyclerView
        if (rv == null || !rv.isAttachedToWindow) {
            applyItemsUpdate(newItems)
            return
        }

        if (rv.isComputingLayout || rv.isInLayout) {
            rv.post {
                val pending = pendingItems ?: return@post
                pendingItems = null
                applyItemsUpdate(pending)
            }
            return
        }

        if (rv.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
            // Defer update until scroll settles to prevent mid-scroll layout hitches
            return
        }

        applyItemsUpdate(newItems)
    }

    private fun applyItemsUpdate(newItems: List<LeanbackCardItem>) {
        val oldItems = items
        val oldSize = oldItems.size
        val newSize = newItems.size

        val plan = computeLeanbackUpdatePlan(
            oldItems = oldItems,
            newItems = newItems,
            keyOf = { it.key },
            visualsMatch = ::hasSameCardVisuals
        )

        items = newItems
        pendingItems = null

        val rv = attachedRecyclerView
        if (rv == null || !rv.isAttachedToWindow) {
            notifyDataSetChanged()
            return
        }

        if (plan is LeanbackUpdatePlan.NoOp) return

        val hadFocus = rv.hasFocus()
        val prevSelectedPos = when (rv) {
            is BaseGridView -> rv.selectedPosition.coerceAtLeast(0)
            else -> 0
        }

        try {
            isSubmittingItems = true
            when (plan) {
                is LeanbackUpdatePlan.NoOp -> Unit
                is LeanbackUpdatePlan.InPlaceChanges -> {
                    try {
                        val positions = plan.changedPositions
                        for (i in positions.indices) {
                            notifyItemChanged(positions[i])
                        }
                    } catch (_: Exception) {
                        safeNotifyDataSetChanged()
                    }
                }
                is LeanbackUpdatePlan.AppendAndChange -> {
                    try {
                        notifyItemRangeInserted(plan.insertStart, plan.insertCount)
                        val positions = plan.changedPrefixPositions
                        for (i in positions.indices) {
                            notifyItemChanged(positions[i])
                        }
                    } catch (_: Exception) {
                        safeNotifyDataSetChanged()
                    }
                }
                is LeanbackUpdatePlan.MyersDiff -> {
                    try {
                        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                            override fun getOldListSize(): Int = oldSize
                            override fun getNewListSize(): Int = newSize
                            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                                return oldItems[oldItemPosition].key == newItems[newItemPosition].key
                            }
                            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                                return hasSameCardVisuals(oldItems[oldItemPosition], newItems[newItemPosition])
                            }
                        }, false)
                        diffResult.dispatchUpdatesTo(this)
                    } catch (_: Exception) {
                        safeNotifyDataSetChanged()
                    }
                }
                is LeanbackUpdatePlan.FullReset -> {
                    safeNotifyDataSetChanged()
                }
            }
        } finally {
            isSubmittingItems = false
        }

        // Placeholder-to-Data Focus Guard:
        // Re-anchor focus safely when transitioning from placeholder items to real content
        if (hadFocus && newItems.isNotEmpty()) {
            val safePos = prevSelectedPos.coerceIn(0, newItems.lastIndex)
            rv.post {
                val focusedChild = rv.findFocus()
                val cardStillHasFocus = focusedChild != null && focusedChild !== rv
                val mayTake = when (rv) {
                    is InteropHorizontalGridView -> rv.canReclaimFocus()
                    else -> rv.hasFocus()
                }
                if (rv.isAttachedToWindow && !cardStillHasFocus && mayTake) {
                    if (rv is BaseGridView) {
                        rv.setSelectedPosition(safePos)
                    }
                    val vh = rv.findViewHolderForAdapterPosition(safePos)
                    if (vh != null) {
                        vh.itemView.requestFocus()
                        (vh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                    } else {
                        rv.post {
                            val deferredFocusedChild = rv.findFocus()
                            val deferredCardHasFocus = deferredFocusedChild != null && deferredFocusedChild !== rv
                            val deferredMayTake = when (rv) {
                                is InteropHorizontalGridView -> rv.canReclaimFocus()
                                else -> rv.hasFocus()
                            }
                            if (rv.isAttachedToWindow && !deferredCardHasFocus && deferredMayTake) {
                                val deferredVh = rv.findViewHolderForAdapterPosition(safePos)
                                deferredVh?.itemView?.requestFocus()
                                (deferredVh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                            }
                        }
                    }
                }
            }
        }

        if (newItems.size > 1 && hasRealItems(newItems) && rv.isAttachedToWindow &&
            (rv.hasFocus() || LeanbackArtworkLoader.isRowNearViewport(rv))
        ) {
            val (firstVisible, lastVisible) = LeanbackArtworkLoader.resolveVisibleBounds(rv)
            if (lastVisible < 0 || firstVisible > 0 || newItems.size > lastVisible + 1) {
                rv.post {
                    if (attachedRecyclerView === rv && rv.isAttachedToWindow && hasRealItems(items)) {
                        prefetchFromVisibleRange(rv.context)
                    }
                }
            }
        }
    }

    fun safeNotifyDataSetChanged() {
        val rv = attachedRecyclerView
        if (rv != null && (rv.isComputingLayout || rv.isInLayout || !rv.isAttachedToWindow)) {
            if (!rv.isAttachedToWindow) {
                pendingNotifyOnAttach = true
            }
            rv.post {
                if (attachedRecyclerView === rv && !rv.isComputingLayout && !rv.isInLayout && rv.isAttachedToWindow) {
                    pendingNotifyOnAttach = false
                    try {
                        notifyDataSetChanged()
                    } catch (_: IllegalStateException) {
                        rv.post { runCatching { notifyDataSetChanged() } }
                    }
                }
            }
            return
        }
        pendingNotifyOnAttach = false
        try {
            notifyDataSetChanged()
        } catch (_: IllegalStateException) {
            rv?.post { runCatching { notifyDataSetChanged() } }
        }
    }

    fun onScrollSettled() {
        flushPendingFocusedItem()
        lastHorizontalPrefetchPosition = RecyclerView.NO_POSITION
        val pending = pendingItems
        if (pending != null) {
            pendingItems = null
            applyItemsUpdate(pending)
        }
        val rv = attachedRecyclerView ?: return
        rebindMissingImages(rv)
        val count = rv.childCount
        for (i in 0 until count) {
            val child = rv.getChildAt(i)
            val vh = rv.getChildViewHolder(child) as? LeanbackBaseViewHolder
            if (vh != null && vh.itemView.hasFocus()) {
                vh.updateFocusVisual(true)
            }
        }
        if (rv.isAttachedToWindow && hasRealItems(items)) {
            prefetchFromVisibleRange(rv.context)
        }
    }

    private fun rebindMissingImages(rv: RecyclerView) {
        if (rv.isComputingLayout || rv.isInLayout) return
        val count = rv.childCount
        for (i in 0 until count) {
            val child = rv.getChildAt(i) ?: continue
            val vh = rv.getChildViewHolder(child) as? LeanbackBaseViewHolder ?: continue
            val pos = vh.bindingAdapterPosition
            if (pos in items.indices && vh.needsImageRebind()) {
                onBindViewHolder(vh, pos)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (val item = items[position]) {
            is LeanbackCardItem.Poster -> {
                if (item.isLandscape) LeanbackPoolManager.VIEW_TYPE_POSTER_LANDSCAPE
                else LeanbackPoolManager.VIEW_TYPE_POSTER
            }
            is LeanbackCardItem.Episode -> LeanbackPoolManager.VIEW_TYPE_EPISODE
            is LeanbackCardItem.Avatar -> LeanbackPoolManager.VIEW_TYPE_AVATAR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeanbackBaseViewHolder {
        val context = parent.context
        return when (viewType) {
            LeanbackPoolManager.VIEW_TYPE_EPISODE -> {
                LeanbackEpisodeViewHolder.create(
                    context = context,
                    cardWidthPx = cardWidthPx,
                    cardHeightPx = cardHeightPx,
                    cornerRadiusPx = cornerRadiusPx,
                    adapter = this
                )
            }
            LeanbackPoolManager.VIEW_TYPE_AVATAR -> {
                LeanbackAvatarViewHolder.create(
                    context = context,
                    cardWidthPx = cardWidthPx,
                    cardHeightPx = cardHeightPx,
                    cornerRadiusPx = cornerRadiusPx,
                    adapter = this
                )
            }
            else -> {
                LeanbackPosterViewHolder.create(
                    context = context,
                    cardWidthPx = cardWidthPx,
                    cardHeightPx = cardHeightPx,
                    cornerRadiusPx = cornerRadiusPx,
                    showLabels = showLabels,
                    adapter = this
                )
            }
        }
    }

    private val prefetchedKeys = LinkedHashSet<String>()
    private val loadingProfileCache = HorizontalPrefetchProfileCache()

    internal var loadingProfileProvider: ((Context) -> HomeImageLoadingProfile)?
        get() = loadingProfileCache.providerOverride
        set(value) {
            loadingProfileCache.providerOverride = value
        }

    internal fun resolveLoadingProfile(context: Context): HomeImageLoadingProfile =
        loadingProfileCache.get(context)

    override fun onBindViewHolder(holder: LeanbackBaseViewHolder, position: Int) {
        holder.adapter = this
        val item = items[position]
        holder.bind(item, cardWidthPx, cardHeightPx, cornerRadiusPx, showLabels)
    }

    private fun hasRealItems(list: List<LeanbackCardItem>): Boolean {
        for (i in list.indices) {
            val url = list[i].imageUrl
            if (!url.isNullOrBlank() && !url.trim().isPlaceholder()) {
                return true
            }
        }
        return false
    }

    private fun scheduleInitialEdgePrefetch(rv: RecyclerView, list: List<LeanbackCardItem>) {
        if (list.size <= 1 || !hasRealItems(list)) return
        rv.post {
            if (attachedRecyclerView === rv &&
                rv.isAttachedToWindow &&
                hasRealItems(items) &&
                (rv.hasFocus() || LeanbackArtworkLoader.isRowNearViewport(rv))
            ) {
                val (firstVisible, lastVisible) = LeanbackArtworkLoader.resolveVisibleBounds(rv)
                if (lastVisible < 0 || firstVisible > 0 || items.size > lastVisible + 1) {
                    prefetchFromVisibleRange(rv.context)
                }
            }
        }
    }

    private fun prefetchItemAt(context: Context, imageLoader: coil3.ImageLoader, index: Int) {
        val item = items.getOrNull(index) ?: return
        val wPx = cardWidthPx.coerceAtLeast(1)
        val hPx = cardHeightPx.coerceAtLeast(1)
        when (item) {
            is LeanbackCardItem.Poster -> {
                LeanbackArtworkLoader.prefetch(
                    context = context,
                    imageLoader = imageLoader,
                    imageUrl = item.imageUrl,
                    fallbackUrl = item.fallbackImageUrl,
                    widthPx = wPx,
                    heightPx = hPx,
                    prefetchedKeysTracker = prefetchedKeys
                )
            }
            is LeanbackCardItem.Episode -> {
                LeanbackArtworkLoader.prefetch(
                    context = context,
                    imageLoader = imageLoader,
                    imageUrl = item.imageUrl,
                    widthPx = wPx,
                    heightPx = hPx,
                    blurred = item.blurThumbnail,
                    prefetchedKeysTracker = prefetchedKeys
                )
            }
            is LeanbackCardItem.Avatar -> {
                val diameter = minOf(wPx, hPx)
                LeanbackArtworkLoader.prefetch(
                    context = context,
                    imageLoader = imageLoader,
                    imageUrl = item.imageUrl,
                    widthPx = diameter,
                    heightPx = diameter,
                    prefetchedKeysTracker = prefetchedKeys
                )
            }
        }
    }

    private fun prefetchRowIndices(
        context: Context,
        focusedPosition: Int,
        firstVisiblePosition: Int,
        lastVisiblePosition: Int,
        aheadCount: Int? = null,
        behindCount: Int? = null
    ) {
        val count = items.size
        if (count <= 1) return
        val resolvedAhead = aheadCount ?: resolveLoadingProfile(context).horizontalPrefetchAhead
        val resolvedBehind = behindCount ?: resolveLoadingProfile(context).horizontalPrefetchBehind
        val indices = LeanbackArtworkLoader.computeHorizontalPrefetchIndices(
            focusedPosition = focusedPosition,
            firstVisiblePosition = firstVisiblePosition,
            lastVisiblePosition = lastVisiblePosition,
            itemCount = count,
            aheadCount = resolvedAhead,
            behindCount = resolvedBehind
        )
        if (indices.isEmpty()) return
        val imageLoader = SingletonImageLoader.get(context)
        for (i in indices.indices) {
            prefetchItemAt(context, imageLoader, indices[i])
        }
    }

    @JvmOverloads
    fun prefetchFromVisibleRange(
        context: Context,
        aheadCount: Int? = null,
        behindCount: Int? = null
    ) {
        val rv = attachedRecyclerView ?: return
        val count = items.size
        if (count <= 1 || !hasRealItems(items)) return
        val (firstVisible, lastVisible) = LeanbackArtworkLoader.resolveVisibleBounds(rv)
        val focusedPosition = (rv as? BaseGridView)?.selectedPosition
            ?.takeIf { it in 0 until count }
            ?: if (firstVisible >= 0) firstVisible else 0
        prefetchRowIndices(
            context = context,
            focusedPosition = focusedPosition,
            firstVisiblePosition = firstVisible,
            lastVisiblePosition = lastVisible,
            aheadCount = aheadCount,
            behindCount = behindCount
        )
    }

    @JvmOverloads
    fun onFocusedPositionChanged(
        context: Context,
        position: Int,
        aheadCount: Int? = null,
        behindCount: Int? = null
    ) {
        val count = items.size
        if (count <= 1) return
        val isHoldingHorizontal = (attachedRecyclerView as? InteropHorizontalGridView)?.isHoldingHorizontalKey == true
        if (isHoldingHorizontal &&
            lastHorizontalPrefetchPosition != RecyclerView.NO_POSITION &&
            kotlin.math.abs(position - lastHorizontalPrefetchPosition) < HORIZONTAL_HOLD_PREFETCH_STRIDE
        ) {
            return
        }
        lastHorizontalPrefetchPosition = position
        val (firstVisible, lastVisible) = LeanbackArtworkLoader.resolveVisibleBounds(attachedRecyclerView)
        prefetchRowIndices(
            context = context,
            focusedPosition = position,
            firstVisiblePosition = firstVisible,
            lastVisiblePosition = lastVisible,
            aheadCount = aheadCount,
            behindCount = behindCount
        )
    }

    fun onGridPositionFocused(context: Context, position: Int, numColumns: Int) {
        val count = items.size
        if (count <= 1) return
        val cols = numColumns.coerceAtLeast(1)
        val imageLoader = SingletonImageLoader.get(context)
        // Prefetch immediate neighbor and the 3 items below in the next row
        if (position + 1 < count) {
            prefetchItemAt(context, imageLoader, position + 1)
        }
        val nextRowCenter = position + cols
        for (candidate in (nextRowCenter - 1)..(nextRowCenter + 1)) {
            if (candidate in 0 until count) {
                prefetchItemAt(context, imageLoader, candidate)
            }
        }
    }

    override fun onViewRecycled(holder: LeanbackBaseViewHolder) {
        holder.unbind()
    }

    companion object {
        private const val LOAD_MORE_DEBOUNCE_MS = 250L
        internal const val HORIZONTAL_HOLD_PREFETCH_STRIDE = 3
    }
}
