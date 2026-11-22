package com.nuvio.tv.ui.screens.home

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil3.BitmapImage
import coil3.Image
import coil3.SingletonImageLoader
import coil3.request.Disposable
import com.nuvio.tv.core.image.HomeImageLoadingProfile
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.isPlaceholder
import com.nuvio.tv.ui.leanback.adapter.LeanbackUpdatePlan
import com.nuvio.tv.ui.leanback.adapter.computeLeanbackUpdatePlan
import com.nuvio.tv.ui.leanback.core.HorizontalPrefetchProfileCache
import com.nuvio.tv.ui.leanback.core.LeanbackArtworkLoader
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import com.nuvio.tv.ui.leanback.core.LeanbackThemeStyle
import com.nuvio.tv.ui.leanback.core.PillProgressBarDrawable
import com.nuvio.tv.ui.leanback.holder.FocusIsolatedRootLayout
import com.nuvio.tv.ui.leanback.pool.LeanbackPoolManager

class LeanbackCardViewHolder(
    val root: LinearLayout,
    val cardContainer: FrameLayout,
    val posterImage: ImageView,
    val titleText: TextView?,
    val progressBar: ProgressBar?,
    var cornerRadiusPx: Float,
    var adapter: LeanbackRowAdapter,
    val watchedBadgeView: TextView? = null,
    val emojiCoverView: TextView? = null,
    val fallbackTitleText: TextView? = null,
    private val progressBarHeightPx: Float = 7f
) : RecyclerView.ViewHolder(root) {

    internal var lastProgressAccentColor: Int = 0

    private val placeholderBackgroundDrawable = GradientDrawable().apply {
        setColor(adapter.themeStyle.placeholderColor)
        cornerRadius = cornerRadiusPx
    }

    private fun ensurePlaceholderBackground() {
        placeholderBackgroundDrawable.setColor(adapter.themeStyle.placeholderColor)
        placeholderBackgroundDrawable.cornerRadius = cornerRadiusPx
        if (cardContainer.background !== placeholderBackgroundDrawable) {
            cardContainer.background = placeholderBackgroundDrawable
        }
    }

    fun updateCornerRadius(newRadiusPx: Float) {
        val radiusChanged = cornerRadiusPx != newRadiusPx
        if (!radiusChanged) return
        cornerRadiusPx = newRadiusPx

        placeholderBackgroundDrawable.cornerRadius = newRadiusPx
        (cardContainer.background as? GradientDrawable)?.cornerRadius = newRadiusPx
        posterImage.invalidateOutline()

        val horizMargin = (newRadiusPx * 0.75f).toInt().coerceAtLeast(6)
        (progressBar?.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.marginStart != horizMargin || lp.marginEnd != horizMargin) {
                lp.marginStart = horizMargin
                lp.marginEnd = horizMargin
                progressBar.layoutParams = lp
            }
        }

        if (root.hasFocus()) {
            updateFocusVisual(true)
        }
    }

    private var currentItem: ModernCarouselItem? = null
    var currentDominantColor: Int = Color.WHITE
    internal var imageDisposable: Disposable? = null
    private var boundImageUrl: String? = null
    private var boundFallbackUrl: String? = null
    private var boundWidthPx: Int = 0
    private var boundHeightPx: Int = 0
    private var boundBlurred: Boolean = false
    private var lastLoadedColorKey: String? = null

    init {
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        posterImage.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (cornerRadiusPx > 0f) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                } else {
                    outline.setRect(0, 0, view.width, view.height)
                }
            }
        }
        posterImage.clipToOutline = true

        root.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            val gridView = adapter.attachedRecyclerView as? ModernInteropHorizontalGridView
            if (hasFocus && gridView?.mayTakeFocus() == false) {
                // Reject stray focus on an inactive row
                gridView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                root.clearFocus()
                updateFocusVisual(false)
                return@OnFocusChangeListener
            }
            updateFocusVisual(hasFocus)
            if (hasFocus) {
                val pos = bindingAdapterPosition
                val item = currentItem
                if (pos != RecyclerView.NO_POSITION && item != null) {
                    adapter.onItemFocused(pos, item)
                }
            }
        }

        root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                event.repeatCount == 0 &&
                keyCode == KeyEvent.KEYCODE_MENU
            ) {
                currentItem?.let { adapter.onItemLongClick(it) }
                return@setOnKeyListener true
            }
            false
        }

        root.setOnClickListener {
            currentItem?.let { adapter.onItemClick(it) }
        }

        root.setOnLongClickListener {
            currentItem?.let { adapter.onItemLongClick(it) }
            true
        }
    }

    fun updateFocusVisual(hasFocus: Boolean) {
        root.animate().cancel()
        if (root.scaleX != 1.0f) root.scaleX = 1.0f
        if (root.scaleY != 1.0f) root.scaleY = 1.0f

        val item = currentItem
        if (hasFocus && item != null) {
            val loadableUrl = (item.imageUrl ?: item.heroPreview.poster ?: item.heroPreview.backdrop)
                ?.takeIf { it.isNotBlank() && !it.trim().isPlaceholder() }
            val colorCacheKey = loadableUrl ?: item.key
            val cachedColor = LeanbackColorEngine.getCachedColor(colorCacheKey)
            if (cachedColor != null) {
                currentDominantColor = cachedColor
                lastLoadedColorKey = colorCacheKey
            } else if (loadableUrl != null && lastLoadedColorKey != colorCacheKey) {
                val bmp = (posterImage.drawable as? BitmapDrawable)?.bitmap
                if (bmp != null && !bmp.isRecycled) {
                    lastLoadedColorKey = colorCacheKey
                    val boundKey = item.key
                    LeanbackColorEngine.extractDominantColorAsync(colorCacheKey, bmp) { extracted ->
                        if (currentItem?.key == boundKey) {
                            val changed = currentDominantColor != extracted
                            currentDominantColor = extracted
                            if (root.hasFocus() && changed) {
                                updateFocusVisual(true)
                            }
                        }
                    }
                }
            }
        }

        titleText?.let { tv ->
            if (tv.isSelected != hasFocus) {
                tv.isSelected = hasFocus
            }
        }

        val targetTranslationZ = if (hasFocus) 12f else 0f
        if (root.translationZ != targetTranslationZ) root.translationZ = targetTranslationZ
        if (cardContainer.elevation != 0f) cardContainer.elevation = 0f
        if (cardContainer.translationZ != 0f) cardContainer.translationZ = 0f
        if (cardContainer.foreground != null) cardContainer.foreground = null

        val gridView = adapter.attachedRecyclerView as? ModernInteropHorizontalGridView
        if (hasFocus) {
            gridView?.notifyCardFocused(cardContainer, currentDominantColor)
        }
    }

    private fun resolveCardColorFromImage(
        result: Image,
        boundKey: String,
        boundColorKey: String,
        notifyIfCachedAndFocused: Boolean
    ) {
        lastLoadedColorKey = boundColorKey
        val existingColor = LeanbackColorEngine.getCachedColor(boundColorKey)
        if (existingColor != null) {
            currentDominantColor = existingColor
            if (notifyIfCachedAndFocused && root.hasFocus()) {
                updateFocusVisual(true)
            }
            return
        }
        val bmp = (result as? BitmapImage)?.bitmap
        if (bmp != null && !bmp.isRecycled) {
            LeanbackColorEngine.extractDominantColorAsync(boundColorKey, bmp) { extracted ->
                if (currentItem?.key == boundKey) {
                    currentDominantColor = extracted
                    if (root.hasFocus()) {
                        updateFocusVisual(true)
                    }
                }
            }
        } else if (notifyIfCachedAndFocused && root.hasFocus()) {
            updateFocusVisual(true)
        }
    }

    fun bind(
        item: ModernCarouselItem,
        cardWidthPx: Int,
        cardHeightPx: Int,
        cornerRadiusPx: Float,
        showLabels: Boolean
    ) {
        val rawCandidateUrl = item.imageUrl ?: item.heroPreview.poster ?: item.heroPreview.backdrop
        val hasLoadableImage = !rawCandidateUrl.isNullOrBlank() && !rawCandidateUrl.trim().isPlaceholder()
        val imageUrl = if (hasLoadableImage) rawCandidateUrl else null
        val fallbackUrl = item.metaPreview?.rawPosterUrl?.takeIf { !it.isBlank() && !it.trim().isPlaceholder() }
        val cwPayload = item.payload as? ModernPayload.ContinueWatching
        val shouldBlur = adapter.blurUnwatchedEpisodes && cwPayload?.item is ContinueWatchingItem.NextUp
        val colorCacheKey = imageUrl ?: item.key
        val wPx = cardWidthPx.coerceAtLeast(1)
        val hPx = cardHeightPx.coerceAtLeast(1)
        val sameImageRequest = hasLoadableImage &&
            currentItem?.key == item.key &&
            boundImageUrl == imageUrl &&
            boundFallbackUrl == fallbackUrl &&
            boundWidthPx == wPx &&
            boundHeightPx == hPx &&
            boundBlurred == shouldBlur &&
            (posterImage.drawable != null || imageDisposable?.isDisposed == false)

        currentItem = item
        updateCornerRadius(cornerRadiusPx)

        if (!sameImageRequest) {
            lastLoadedColorKey = null
            posterImage.setImageDrawable(null)
            ensurePlaceholderBackground()
        }

        val cachedColor = LeanbackColorEngine.getCachedColor(colorCacheKey)
        currentDominantColor = cachedColor ?: LeanbackColorEngine.defaultFallbackColor

        // Set dimensions
        val cardLp = cardContainer.layoutParams
        if (cardLp.width != cardWidthPx || cardLp.height != cardHeightPx) {
            cardLp.width = cardWidthPx
            cardLp.height = cardHeightPx
            cardContainer.layoutParams = cardLp

            val rootLp = root.layoutParams
            if (rootLp != null && rootLp.width != cardWidthPx) {
                rootLp.width = cardWidthPx
                root.layoutParams = rootLp
            }
        }

        // Title, typography & accessibility description
        val style = adapter.themeStyle
        style.typeface?.let { tf ->
            if (titleText != null && titleText.typeface !== tf) titleText.typeface = tf
            if (fallbackTitleText != null && fallbackTitleText.typeface !== tf) fallbackTitleText.typeface = tf
        }
        fallbackTitleText?.setTextColor(style.titleColor)
        if (showLabels && titleText != null && item.title.isNotBlank()) {
            titleText.visibility = View.VISIBLE
            titleText.setTextColor(style.titleColor)
            titleText.text = item.title
        } else {
            titleText?.visibility = View.GONE
        }

        // Watched badge (hidden for placeholder items)
        val isPlaceholderItem = item.imageUrl?.trim()?.isPlaceholder() == true ||
            (item.payload as? ModernPayload.Catalog)?.itemId?.startsWith("__placeholder_") == true
        val isWatched = !isPlaceholderItem &&
            item.metaPreview?.let { adapter.isCatalogItemWatched(it) } == true
        applyWatchedBadgeState(isWatched, item)

        // Collection folder emoji cover when no artwork image is available
        val folderEmoji = (item.payload as? ModernPayload.CollectionFolder)
            ?.coverEmoji
            ?.takeIf { imageUrl == null && it.isNotBlank() }
        if (emojiCoverView != null) {
            if (folderEmoji != null) {
                emojiCoverView.text = folderEmoji
                emojiCoverView.visibility = View.VISIBLE
            } else {
                emojiCoverView.visibility = View.GONE
            }
        }

        val shouldShowFallbackTitle = !isPlaceholderItem &&
            folderEmoji == null &&
            !showLabels &&
            item.title.isNotBlank()

        // Progress bar for Continue Watching
        if (progressBar != null && lastProgressAccentColor != style.accentColor) {
            progressBar.progressDrawable = PillProgressBarDrawable.create(
                heightPx = progressBarHeightPx,
                progressColor = style.accentColor
            )
            lastProgressAccentColor = style.accentColor
        }
        val progressItem = cwPayload?.item as? ContinueWatchingItem.InProgress
        if (progressItem != null && progressBar != null) {
            progressBar.visibility = View.VISIBLE
            val pct = progressItem.progress.progressPercent?.toInt()
                ?: if (progressItem.progress.duration > 0) {
                    ((progressItem.progress.position.toFloat() / progressItem.progress.duration) * 100).toInt()
                } else 0
            progressBar.progress = pct.coerceIn(0, 100)
        } else {
            progressBar?.visibility = View.GONE
        }

        // Image loading via unified LeanbackArtworkLoader (matches CustomPoster FALLBACK_URL_KEY fast-path)
        if (imageUrl != null) {
            if (!sameImageRequest) {
                fallbackTitleText?.visibility = View.GONE
                val boundKey = item.key
                val boundColorKey = colorCacheKey
                boundImageUrl = imageUrl
                boundFallbackUrl = fallbackUrl
                boundWidthPx = wPx
                boundHeightPx = hPx
                boundBlurred = shouldBlur
                imageDisposable?.dispose()
                imageDisposable = LeanbackArtworkLoader.loadIntoImageView(
                    imageView = posterImage,
                    imageUrl = imageUrl,
                    fallbackUrl = fallbackUrl,
                    widthPx = wPx,
                    heightPx = hPx,
                    blurred = shouldBlur,
                    isStillBound = { currentItem?.key == boundKey },
                    onCacheHit = { result ->
                        cardContainer.background = null
                        fallbackTitleText?.visibility = View.GONE
                        if (root.hasFocus() || bindingAdapterPosition == 0) {
                            resolveCardColorFromImage(
                                result = result,
                                boundKey = boundKey,
                                boundColorKey = boundColorKey,
                                notifyIfCachedAndFocused = false
                            )
                        }
                    },
                    onAsyncSuccess = { result ->
                        cardContainer.background = null
                        fallbackTitleText?.visibility = View.GONE
                        if (root.hasFocus() || bindingAdapterPosition == 0) {
                            resolveCardColorFromImage(
                                result = result,
                                boundKey = boundKey,
                                boundColorKey = boundColorKey,
                                notifyIfCachedAndFocused = true
                            )
                        }
                    },
                    onAsyncError = {
                        boundImageUrl = null
                        ensurePlaceholderBackground()
                        if (shouldShowFallbackTitle && fallbackTitleText != null) {
                            fallbackTitleText.text = item.title
                            fallbackTitleText.visibility = View.VISIBLE
                        }
                    }
                )
            }
        } else {
            imageDisposable?.dispose()
            imageDisposable = null
            boundImageUrl = null
            boundFallbackUrl = null
            boundWidthPx = 0
            boundHeightPx = 0
            boundBlurred = false
            lastLoadedColorKey = null
            posterImage.setImageDrawable(null)
            ensurePlaceholderBackground()
            if (shouldShowFallbackTitle && fallbackTitleText != null) {
                fallbackTitleText.text = item.title
                fallbackTitleText.visibility = View.VISIBLE
            } else {
                fallbackTitleText?.visibility = View.GONE
            }
        }

        // Sync focus visual to current focus state when bound or recycled
        val gridView = adapter.attachedRecyclerView as? ModernInteropHorizontalGridView
        val mayTakeFocus = gridView?.mayTakeFocus() != false
        val isRowFocused = mayTakeFocus && gridView?.hasFocus() == true
        val targetPos = gridView?.selectedPosition?.coerceAtLeast(0) ?: 0
        val isCurrentSelectedPos = isRowFocused && bindingAdapterPosition != RecyclerView.NO_POSITION && bindingAdapterPosition == targetPos

        if (mayTakeFocus && (root.hasFocus() || isCurrentSelectedPos)) {
            if (!root.hasFocus()) {
                root.requestFocus()
            }
            updateFocusVisual(true)
        } else {
            if (!mayTakeFocus && root.hasFocus()) {
                root.clearFocus()
            }
            updateFocusVisual(false)
        }
    }

    internal fun applyWatchedBadgeState(isWatched: Boolean, item: ModernCarouselItem? = currentItem) {
        if (watchedBadgeView != null) {
            watchedBadgeView.visibility = if (isWatched) View.VISIBLE else View.GONE
            if (isWatched) {
                (watchedBadgeView.background as? GradientDrawable)?.setColor(adapter.themeStyle.accentColor)
            }
        }
        if (item != null) {
            root.contentDescription = buildString {
                append(item.title)
                if (!item.subtitle.isNullOrBlank()) {
                    if (isNotEmpty()) append(", ")
                    append(item.subtitle)
                }
                if (isWatched) {
                    if (isNotEmpty()) append(", ")
                    append("Watched")
                }
            }.ifBlank { null }
        }
    }

    fun unbind() {
        root.animate().cancel()
        root.scaleX = 1.0f
        root.scaleY = 1.0f
        root.contentDescription = null
        titleText?.isSelected = false
        watchedBadgeView?.visibility = View.GONE
        emojiCoverView?.visibility = View.GONE
        fallbackTitleText?.visibility = View.GONE
        progressBar?.visibility = View.GONE
        imageDisposable?.dispose()
        imageDisposable = null
        currentItem = null
        boundImageUrl = null
        boundFallbackUrl = null
        boundWidthPx = 0
        boundHeightPx = 0
        boundBlurred = false
        lastLoadedColorKey = null
        posterImage.setImageDrawable(null)
        cardContainer.foreground = null
        ensurePlaceholderBackground()
    }
}

class LeanbackRowAdapter(
    var cardWidthPx: Int,
    var cardHeightPx: Int,
    var cornerRadiusPx: Float,
    var showLabels: Boolean,
    var animationsEnabled: () -> Boolean,
    var onItemFocused: (Int, ModernCarouselItem) -> Unit,
    var onItemClick: (ModernCarouselItem) -> Unit,
    var onItemLongClick: (ModernCarouselItem) -> Unit,
    onLoadMore: () -> Unit,
    var themeStyle: LeanbackThemeStyle = LeanbackThemeStyle.Default,
    var isCatalogItemWatched: (MetaPreview) -> Boolean = { false },
    var blurUnwatchedEpisodes: Boolean = false
) : RecyclerView.Adapter<LeanbackCardViewHolder>() {

    private var lastLoadMoreTimestampMs = 0L
    private var rawOnLoadMore: () -> Unit = onLoadMore
    private val debouncedLoadMore: () -> Unit = {
        val now = SystemClock.uptimeMillis()
        if (now - lastLoadMoreTimestampMs >= 250L) {
            lastLoadMoreTimestampMs = now
            rawOnLoadMore()
        }
    }

    var onLoadMore: () -> Unit
        get() = debouncedLoadMore
        set(value) {
            rawOnLoadMore = value
        }

    private var items: List<ModernCarouselItem> = emptyList()
    private var watchedStates: BooleanArray = BooleanArray(0)
    internal var lastWatchedMaskHash: Int = 0
    private var pendingItems: List<ModernCarouselItem>? = null
    private var pendingApplyPosted: Boolean = false
    var isSubmittingItems: Boolean = false
        private set

    fun getItem(position: Int): ModernCarouselItem? = items.getOrNull(position)

    private fun resolveItemWatchedState(item: ModernCarouselItem): Boolean {
        val isPlaceholderItem = item.imageUrl?.trim()?.isPlaceholder() == true ||
            (item.payload as? ModernPayload.Catalog)?.itemId?.startsWith("__placeholder_") == true
        return !isPlaceholderItem && item.metaPreview?.let { isCatalogItemWatched(it) } == true
    }

    /**
     * Synchronizes watched badges in-place when [isCatalogItemWatched] changes for any item
     * even if the [ModernCarouselItem] list reference itself has not changed.
     * Returns the adapter positions whose watched state changed.
     */
    fun syncWatchedStateChanges(targetList: List<ModernCarouselItem> = items): List<Int> {
        val size = targetList.size
        if (size == 0) {
            if (watchedStates.isNotEmpty()) watchedStates = BooleanArray(0)
            return emptyList()
        }
        val oldStates = watchedStates
        if (oldStates.size != size) {
            watchedStates = BooleanArray(size) { resolveItemWatchedState(targetList[it]) }
            return emptyList()
        }
        var changedPositions: MutableList<Int>? = null
        val rv = attachedRecyclerView
        for (i in 0 until size) {
            val item = targetList[i]
            val nowWatched = resolveItemWatchedState(item)
            if (oldStates[i] != nowWatched) {
                oldStates[i] = nowWatched
                if (changedPositions == null) {
                    changedPositions = ArrayList(4)
                }
                changedPositions.add(i)
                val liveVh = rv?.findViewHolderForAdapterPosition(i) as? LeanbackCardViewHolder
                if (liveVh != null) {
                    liveVh.applyWatchedBadgeState(nowWatched, item)
                } else if (rv != null && (rv.isComputingLayout || rv.isInLayout)) {
                    val changedIndex = i
                    rv.post {
                        if (attachedRecyclerView === rv && changedIndex in items.indices) {
                            notifyItemChanged(changedIndex)
                        }
                    }
                } else {
                    notifyItemChanged(i)
                }
            }
        }
        return changedPositions ?: emptyList()
    }

    fun updateConfig(
        newWidthPx: Int,
        newHeightPx: Int,
        newRadiusPx: Float,
        newShowLabels: Boolean,
        newThemeStyle: LeanbackThemeStyle = themeStyle,
        newBlurUnwatchedEpisodes: Boolean = blurUnwatchedEpisodes
    ) {
        val changed = cardWidthPx != newWidthPx ||
            cardHeightPx != newHeightPx ||
            cornerRadiusPx != newRadiusPx ||
            showLabels != newShowLabels ||
            themeStyle != newThemeStyle ||
            blurUnwatchedEpisodes != newBlurUnwatchedEpisodes
        if (changed) {
            cardWidthPx = newWidthPx
            cardHeightPx = newHeightPx
            cornerRadiusPx = newRadiusPx
            showLabels = newShowLabels
            themeStyle = newThemeStyle
            blurUnwatchedEpisodes = newBlurUnwatchedEpisodes
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

    var attachedRecyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
        scheduleInitialEdgePrefetch(recyclerView, items)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        if (attachedRecyclerView === recyclerView) {
            attachedRecyclerView = null
            pendingApplyPosted = false
        }
    }

    private fun hasSameCardVisuals(a: ModernCarouselItem, b: ModernCarouselItem): Boolean {
        if (a === b) return true
        if (a.key != b.key || a.title != b.title || a.imageUrl != b.imageUrl) return false
        if (a.heroPreview.poster != b.heroPreview.poster || a.heroPreview.backdrop != b.heroPreview.backdrop) return false
        if (a.metaPreview?.rawPosterUrl != b.metaPreview?.rawPosterUrl) return false
        val cwA = (a.payload as? ModernPayload.ContinueWatching)?.item as? ContinueWatchingItem.InProgress
        val cwB = (b.payload as? ModernPayload.ContinueWatching)?.item as? ContinueWatchingItem.InProgress
        return cwA?.progress?.progressPercent == cwB?.progress?.progressPercent &&
            cwA?.progress?.position == cwB?.progress?.position &&
            cwA?.progress?.duration == cwB?.progress?.duration
    }

    private fun itemsMatchVisuals(a: List<ModernCarouselItem>, b: List<ModernCarouselItem>, count: Int): Boolean {
        for (i in 0 until count) {
            if (!hasSameCardVisuals(a[i], b[i])) return false
        }
        return true
    }

    fun submitItems(newItems: List<ModernCarouselItem>) {
        if (items === newItems) {
            syncWatchedStateChanges(items)
            return
        }
        if (items.size == newItems.size && itemsMatchVisuals(items, newItems, items.size)) {
            items = newItems
            pendingItems = null
            syncWatchedStateChanges(newItems)
            return
        }

        val rv = attachedRecyclerView
        if (rv != null && (rv.isComputingLayout || rv.isInLayout || rv.scrollState != RecyclerView.SCROLL_STATE_IDLE)) {
            pendingItems = newItems
            return
        }
        pendingItems = null
        applyItems(newItems)
    }

    fun onScrollSettled() {
        val rv = attachedRecyclerView
        if (rv != null) {
            val mayTakeFocus = (rv as? ModernInteropHorizontalGridView)?.mayTakeFocus() != false
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i)
                val vh = rv.getChildViewHolder(child) as? LeanbackCardViewHolder ?: continue
                if (vh.itemView.hasFocus()) {
                    if (mayTakeFocus) {
                        vh.updateFocusVisual(true)
                    } else {
                        vh.itemView.clearFocus()
                        vh.updateFocusVisual(false)
                    }
                }
                if (vh.posterImage.drawable == null && vh.imageDisposable == null) {
                    val pos = rv.getChildAdapterPosition(child)
                    if (pos != RecyclerView.NO_POSITION && pos in items.indices) {
                        val item = items[pos]
                        val rawUrl = item.imageUrl ?: item.heroPreview.poster ?: item.heroPreview.backdrop
                        if (!rawUrl.isNullOrBlank() && !rawUrl.trim().isPlaceholder()) {
                            onBindViewHolder(vh, pos)
                        }
                    }
                }
            }
        }
        val pending = pendingItems
        if (pending != null) {
            pendingItems = null
            applyItems(pending)
        }
        val settledRv = attachedRecyclerView
        if (settledRv != null && settledRv.isAttachedToWindow && hasRealItems(items)) {
            prefetchFromVisibleRange(settledRv.context)
        }
    }

    private fun scheduleFlushPendingItems(rv: RecyclerView) {
        if (pendingApplyPosted) return
        pendingApplyPosted = true
        rv.post {
            pendingApplyPosted = false
            if (attachedRecyclerView !== rv) return@post
            if (rv.isComputingLayout || rv.isInLayout) {
                scheduleFlushPendingItems(rv)
                return@post
            }
            if (rv.scrollState != RecyclerView.SCROLL_STATE_IDLE) return@post
            val pending = pendingItems ?: return@post
            pendingItems = null
            applyItems(pending)
        }
    }

    private fun applyItems(newItems: List<ModernCarouselItem>) {
        val rv = attachedRecyclerView
        if (rv != null && (rv.isComputingLayout || rv.isInLayout)) {
            // Defer updating `items` until RecyclerView finishes layout so getItemCount() stays consistent.
            pendingItems = newItems
            scheduleFlushPendingItems(rv)
            return
        }

        val oldItems = items
        val oldSize = oldItems.size
        val newSize = newItems.size
        items = newItems
        watchedStates = BooleanArray(newSize) { resolveItemWatchedState(newItems[it]) }
        applyItemsInternal(oldItems, oldSize, newSize, newItems)
    }

    private fun applyItemsInternal(
        oldItems: List<ModernCarouselItem>,
        oldSize: Int,
        newSize: Int,
        newItems: List<ModernCarouselItem>
    ) {
        val rv = attachedRecyclerView as? ModernInteropHorizontalGridView
        val hadFocus = rv?.hasFocus() == true
        val prevSelectedPos = rv?.selectedPosition?.coerceAtLeast(0) ?: 0

        val plan = computeLeanbackUpdatePlan(
            oldItems = oldItems,
            newItems = newItems,
            keyOf = { it.key },
            visualsMatch = ::hasSameCardVisuals
        )
        if (plan is LeanbackUpdatePlan.NoOp) return

        try {
            isSubmittingItems = true
            when (plan) {
                is LeanbackUpdatePlan.NoOp -> Unit
                is LeanbackUpdatePlan.InPlaceChanges -> {
                    val positions = plan.changedPositions
                    for (i in positions.indices) {
                        notifyItemChanged(positions[i])
                    }
                }
                is LeanbackUpdatePlan.AppendAndChange -> {
                    notifyItemRangeInserted(plan.insertStart, plan.insertCount)
                    val positions = plan.changedPrefixPositions
                    for (i in positions.indices) {
                        notifyItemChanged(positions[i])
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
                        notifyDataSetChanged()
                    }
                }
                is LeanbackUpdatePlan.FullReset -> {
                    notifyDataSetChanged()
                }
            }
        } finally {
            isSubmittingItems = false
        }

        // Placeholder-to-Data Focus Guard
        if (hadFocus && newItems.isNotEmpty()) {
            val safePos = prevSelectedPos.coerceIn(0, newItems.lastIndex)
            rv.post {
                val focusedChild = rv.findFocus()
                val cardStillHasFocus = focusedChild != null && focusedChild !== rv
                if (rv.isAttachedToWindow && !cardStillHasFocus && rv.mayTakeFocus()) {
                    rv.setSelectedPosition(safePos)
                    val vh = rv.findViewHolderForAdapterPosition(safePos)
                    if (vh != null) {
                        vh.itemView.requestFocus()
                        (vh as? LeanbackCardViewHolder)?.updateFocusVisual(true)
                    } else {
                        rv.post {
                            val deferredFocusedChild = rv.findFocus()
                            val deferredCardHasFocus = deferredFocusedChild != null && deferredFocusedChild !== rv
                            if (rv.isAttachedToWindow && !deferredCardHasFocus && rv.mayTakeFocus()) {
                                val deferredVh = rv.findViewHolderForAdapterPosition(safePos)
                                deferredVh?.itemView?.requestFocus()
                                (deferredVh as? LeanbackCardViewHolder)?.updateFocusVisual(true)
                                rv.ensureChildFocusAndRing(targetPos = safePos, animated = false)
                            }
                        }
                    }
                    rv.ensureChildFocusAndRing(targetPos = safePos, animated = false)
                }
            }
        }

        val hostRv = attachedRecyclerView
        if (newItems.size > 1 && hasRealItems(newItems) && hostRv != null && hostRv.isAttachedToWindow &&
            (hostRv.hasFocus() || LeanbackArtworkLoader.isRowNearViewport(hostRv))
        ) {
            val (firstVisible, lastVisible) = LeanbackArtworkLoader.resolveVisibleBounds(hostRv)
            if (lastVisible < 0 || firstVisible > 0 || newItems.size > lastVisible + 1) {
                hostRv.post {
                    if (attachedRecyclerView === hostRv && hostRv.isAttachedToWindow && hasRealItems(items)) {
                        prefetchFromVisibleRange(hostRv.context)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = LeanbackPoolManager.VIEW_TYPE_MODERN_CARD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeanbackCardViewHolder {
        val context = parent.context
        val metrics = context.resources.displayMetrics

        val root = FocusIsolatedRootLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = ViewGroup.LayoutParams(cardWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val cardContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(cardWidthPx, cardHeightPx)
            clipToOutline = false
            clipChildren = false
        }

        val posterImage = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
        }
        cardContainer.addView(posterImage)

        val emojiCoverView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        cardContainer.addView(emojiCoverView)

        val badgeSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22f, metrics).toInt()
        val badgeMarginPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, metrics).toInt()
        val watchedBadgeView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(badgeSizePx, badgeSizePx).apply {
                gravity = Gravity.TOP or Gravity.START
                marginStart = badgeMarginPx
                marginEnd = badgeMarginPx
                topMargin = badgeMarginPx
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(themeStyle.accentColor)
            }
            text = "\u2713"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        cardContainer.addView(watchedBadgeView)

        val pbHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3.5f, metrics)
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            val h = pbHeightPx.toInt().coerceAtLeast(2)
            val horizMargin = (cornerRadiusPx * 0.75f).toInt().coerceAtLeast(6)
            val bMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, metrics).toInt()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                h
            ).apply {
                gravity = Gravity.BOTTOM
                marginStart = horizMargin
                marginEnd = horizMargin
                bottomMargin = bMargin
            }
            max = 100
            progress = 0
            progressDrawable = PillProgressBarDrawable.create(
                heightPx = pbHeightPx,
                progressColor = themeStyle.accentColor
            )
            visibility = View.GONE
        }
        cardContainer.addView(progressBar)

        val fallbackPadPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, metrics).toInt()
        val fallbackTitleText = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(fallbackPadPx, fallbackPadPx, fallbackPadPx, fallbackPadPx)
            gravity = Gravity.CENTER
            setTextColor(themeStyle.titleColor)
            themeStyle.typeface?.let { typeface = it }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            visibility = View.GONE
        }
        cardContainer.addView(fallbackTitleText)

        root.addView(cardContainer)

        val titleText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, metrics).toInt()
            }
            setTextColor(themeStyle.titleColor)
            themeStyle.typeface?.let { typeface = it }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isSingleLine = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            visibility = if (showLabels) View.VISIBLE else View.GONE
        }.also { root.addView(it) }

        return LeanbackCardViewHolder(
            root = root,
            cardContainer = cardContainer,
            posterImage = posterImage,
            titleText = titleText,
            progressBar = progressBar,
            cornerRadiusPx = cornerRadiusPx,
            adapter = this,
            watchedBadgeView = watchedBadgeView,
            emojiCoverView = emojiCoverView,
            fallbackTitleText = fallbackTitleText,
            progressBarHeightPx = pbHeightPx
        ).also {
            it.lastProgressAccentColor = themeStyle.accentColor
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

    override fun onBindViewHolder(holder: LeanbackCardViewHolder, position: Int) {
        holder.adapter = this
        val item = items[position]
        holder.bind(item, cardWidthPx, cardHeightPx, cornerRadiusPx, showLabels)
    }

    private fun hasRealItems(list: List<ModernCarouselItem>): Boolean {
        for (i in list.indices) {
            val item = list[i]
            val url = item.imageUrl ?: item.heroPreview.poster ?: item.heroPreview.backdrop
            if (!url.isNullOrBlank() && !url.trim().isPlaceholder()) {
                return true
            }
        }
        return false
    }

    private fun scheduleInitialEdgePrefetch(rv: RecyclerView, list: List<ModernCarouselItem>) {
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
        val nextItem = items.getOrNull(index) ?: return
        val wPx = cardWidthPx.coerceAtLeast(1)
        val hPx = cardHeightPx.coerceAtLeast(1)
        val nextUrl = nextItem.imageUrl ?: nextItem.heroPreview.poster ?: nextItem.heroPreview.backdrop
        val cwPayload = nextItem.payload as? ModernPayload.ContinueWatching
        val shouldBlur = blurUnwatchedEpisodes && cwPayload?.item is ContinueWatchingItem.NextUp
        LeanbackArtworkLoader.prefetch(
            context = context,
            imageLoader = imageLoader,
            imageUrl = nextUrl,
            fallbackUrl = nextItem.metaPreview?.rawPosterUrl,
            widthPx = wPx,
            heightPx = hPx,
            blurred = shouldBlur,
            prefetchedKeysTracker = prefetchedKeys
        )
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

    override fun onViewRecycled(holder: LeanbackCardViewHolder) {
        holder.unbind()
    }
}

internal fun resolveRowTargetGlowColor(
    grid: HorizontalGridView?,
    cardIndex: Int,
    fallbackItem: ModernCarouselItem?
): Int {
    val vh = grid?.findViewHolderForAdapterPosition(cardIndex) as? LeanbackCardViewHolder
    if (vh != null && vh.currentDominantColor != 0 &&
        vh.currentDominantColor != Color.WHITE &&
        vh.currentDominantColor != Color.BLACK
    ) {
        return vh.currentDominantColor
    }
    if (fallbackItem != null) {
        val rawUrl = fallbackItem.imageUrl ?: fallbackItem.heroPreview.poster ?: fallbackItem.heroPreview.backdrop
        val colorCacheKey = rawUrl?.takeIf { !it.isBlank() && !it.trim().isPlaceholder() } ?: fallbackItem.key
        val cachedColor = LeanbackColorEngine.getCachedColor(colorCacheKey)
        if (cachedColor != null && cachedColor != 0 && cachedColor != Color.WHITE && cachedColor != Color.BLACK) {
            return cachedColor
        }
    }
    return DEFAULT_MODERN_FOCUS_COLOR
}
