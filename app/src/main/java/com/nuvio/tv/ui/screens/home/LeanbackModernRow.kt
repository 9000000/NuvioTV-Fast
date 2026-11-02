package com.nuvio.tv.ui.screens.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.recyclerview.widget.RecyclerView
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.BitmapImage
import coil3.Image
import coil3.asDrawable
import coil3.target.Target

private val dominantColorCache = LruCache<String, Int>(256)

/**
 * Trích xuất màu chủ đạo rực rỡ nhất từ Bitmap bằng Histogram 16 Hue Bins.
 * Đảm bảo màu diện tích lớn nhất (như nền vàng rực rỡ) sẽ luôn chiến thắng,
 * đồng thời giữ nguyên khả năng bắt các màu neon nổi bật trên nền đen.
 */
fun extractDominantColor(bitmap: Bitmap): Int {
    if (bitmap.isRecycled) return Color.WHITE
    return runCatching {
        val stepX = (bitmap.width / 14).coerceAtLeast(1)
    val stepY = (bitmap.height / 14).coerceAtLeast(1)
    val hsv = FloatArray(3)
    val numBins = 16
    val binScores = FloatArray(numBins)
    val binBestWeight = FloatArray(numBins)
    val binRepresentative = IntArray(numBins)

    for (x in 0 until bitmap.width step stepX) {
        for (y in 0 until bitmap.height step stepY) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 128) continue
            Color.colorToHSV(pixel, hsv)
            val sat = hsv[1]
            val brightness = hsv[2]
            // Loại bỏ màu đen kịt và màu xám/trắng không có sắc độ
            if (sat > 0.22f && brightness > 0.18f) {
                // Trọng số ưu tiên độ bão hòa màu và độ sáng
                val weight = sat * sat * brightness
                val bin = (((hsv[0] + 11.25f) % 360f) / 22.5f).toInt().coerceIn(0, numBins - 1)
                binScores[bin] += weight
                if (weight > binBestWeight[bin]) {
                    binBestWeight[bin] = weight
                    binRepresentative[bin] = pixel
                }
            }
        }
    }

    var bestBin = -1
    var maxBinScore = 0f
    for (i in 0 until numBins) {
        if (binScores[i] > maxBinScore) {
            maxBinScore = binScores[i]
            bestBin = i
        }
    }

    val dominantColor = if (bestBin >= 0 && binRepresentative[bestBin] != 0) {
        binRepresentative[bestBin]
    } else {
        Color.parseColor("#38BDF8") // Fallback: Electric Cyan
    }

    Color.colorToHSV(dominantColor, hsv)
    // Tăng độ bão hòa và đẩy độ sáng lên cực đại để ra chất đèn Neon phát quang rực rỡ
    hsv[1] = (hsv[1] * 1.20f).coerceIn(0.75f, 1.0f)
    hsv[2] = 1.0f
    Color.HSVToColor(hsv)
    }.getOrDefault(Color.parseColor("#38BDF8"))
}

/**
 * View vẽ hào quang ánh sáng đèn Neon chân thực (Gaussian Neon Bloom) tỏa ra phía ngoài poster.
 * - Sử dụng Skia BlurMaskFilter.Blur.OUTER trên Software Layer:
 *   + Ở TRONG GIỮ NGUYÊN HOÀN TOÀN: Blur.OUTER chỉ vẽ ra ngoài biên dạng, không vẽ bất kỳ pixel nào bên trong.
 *   + Kết hợp clipOutPath loại bỏ 100% diện tích bên trong poster, đảm bảo poster sắc nét hoàn hảo.
 *   + Tỏa ra ngoài với 3 tầng tán xạ Gaussian mịn màng (Core, Mid, Ambient Aura),
 *     tạo hiệu ứng phát sáng đèn neon liên tục, không có các vòng tròn đồng tâm giật cục.
 * - Đổi màu phát quang linh hoạt theo màu chủ đạo rực rỡ trích xuất từ poster.
 */
class PlayStoreGlowView(context: Context) : View(context) {
    var glowColor: Int = Color.WHITE
        set(value) {
            field = value
            updatePaints()
            invalidate()
        }
    var cornerRadiusPx: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var glowSpreadPx: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                updateFilters()
                invalidate()
            }
        }
    var glowMarginPx: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val clipPath = Path()

    // 3 bo loc Gaussian blur cho hieu ung anh sang Neon thuc te (chuan v1.2.50):
    // Core: tia sang neon cuong do cao ngay sat vien
    // Mid: than sang quang hoc
    // Aura: quang sang tan xa khi quyen mem mai ra xa
    private var coreFilter: BlurMaskFilter? = null
    private var midFilter: BlurMaskFilter? = null
    private var auraFilter: BlurMaskFilter? = null

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        updatePaints()
    }

    private fun updateFilters() {
        if (glowSpreadPx <= 0f) {
            coreFilter = null
            midFilter = null
            auraFilter = null
            corePaint.maskFilter = null
            midPaint.maskFilter = null
            auraPaint.maskFilter = null
            return
        }
        val rCore = (glowSpreadPx * 0.22f).coerceAtLeast(1f)
        val rMid = (glowSpreadPx * 0.50f).coerceAtLeast(1f)
        val rAura = (glowSpreadPx * 0.85f).coerceAtLeast(1f)
        coreFilter = BlurMaskFilter(rCore, BlurMaskFilter.Blur.NORMAL)
        midFilter = BlurMaskFilter(rMid, BlurMaskFilter.Blur.NORMAL)
        auraFilter = BlurMaskFilter(rAura, BlurMaskFilter.Blur.NORMAL)
        corePaint.maskFilter = coreFilter
        midPaint.maskFilter = midFilter
        auraPaint.maskFilter = auraFilter
    }

    private fun updatePaints() {
        corePaint.color = ColorUtils.setAlphaComponent(glowColor, 255)
        midPaint.color = ColorUtils.setAlphaComponent(glowColor, 200)
        auraPaint.color = ColorUtils.setAlphaComponent(glowColor, 110)
    }

    override fun onDraw(canvas: Canvas) {
        if (glowColor == 0 || glowSpreadPx <= 0f) return
        val margin = if (glowMarginPx > 0f) glowMarginPx else glowSpreadPx
        val cardLeft = margin
        val cardTop = margin
        val cardRight = width - margin
        val cardBottom = height - margin

        clipPath.reset()
        if (cornerRadiusPx > 0f) {
            clipPath.addRoundRect(
                cardLeft,
                cardTop,
                cardRight,
                cardBottom,
                cornerRadiusPx,
                cornerRadiusPx,
                Path.Direction.CW
            )
        } else {
            clipPath.addRect(
                cardLeft,
                cardTop,
                cardRight,
                cardBottom,
                Path.Direction.CW
            )
        }

        if (auraPaint.maskFilter != null) {
            canvas.drawPath(clipPath, auraPaint)
        }
        if (midPaint.maskFilter != null) {
            canvas.drawPath(clipPath, midPaint)
        }
        if (corePaint.maskFilter != null) {
            canvas.drawPath(clipPath, corePaint)
        }
    }
}

class InteropHorizontalGridView(context: Context) : HorizontalGridView(context) {
    var onNavigateDown: (() -> Boolean)? = null
    var onNavigateUp: (() -> Boolean)? = null
    var onNavigateBack: (() -> Boolean)? = null
    var onNavigateLeftOnFirstItem: (() -> Boolean)? = null
    var animationsEnabled: Boolean = true

    private var isHoldingHorizontalKey = false
    private var isAtItem0 = false
    private var item0SettledTime = 0L
    private var hasReleasedLeftKeySinceAt0 = true

    private val rowAdapter: LeanbackRowAdapter?
        get() = adapter as? LeanbackRowAdapter

    init {
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        itemAnimator = null
        layoutManager?.isItemPrefetchEnabled = false

        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    rowAdapter?.onScrollSettled()
                }
            }
        })

        addOnChildViewHolderSelectedListener(object : OnChildViewHolderSelectedListener() {
            override fun onChildViewHolderSelected(
                parent: RecyclerView,
                child: RecyclerView.ViewHolder?,
                position: Int,
                subposition: Int
            ) {
                if (position == 0) {
                    isAtItem0 = true
                    item0SettledTime = SystemClock.uptimeMillis()
                } else {
                    isAtItem0 = false
                    hasReleasedLeftKeySinceAt0 = false
                }

                if (position >= 0 && child != null) {
                    val item = rowAdapter?.getItem(position)
                    if (item != null) {
                        rowAdapter?.onItemFocused?.invoke(position, item)
                    }
                }

                val totalCount = rowAdapter?.itemCount ?: 0
                if (position >= totalCount - 5 && totalCount > 0) {
                    post {
                        rowAdapter?.onLoadMore?.invoke()
                    }
                }
            }
        })
    }

    // Always isolate the root view to 'this'.
    // Android View.java: rootViewRequestFocus() calls getRootView().requestFocus().
    // If getRootView() returns DecorView, it requests focus from the OS window down into AndroidComposeView,
    // which triggers performMeasureAndLayout() and crashes Compose when called during draw/layout passes.
    // By returning 'this', any focus requests from view removals stay strictly local inside this HorizontalGridView.
    override fun getRootView(): View = this

    private val isBusyLayout: Boolean
        get() = isComputingLayout || isInLayout || !isAttachedToWindow

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        if (isBusyLayout) {
            post { requestFocus(direction, previouslyFocusedRect) }
            return false
        }
        return super.requestFocus(direction, previouslyFocusedRect)
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        if (isBusyLayout) {
            post { super.requestChildFocus(child, focused) }
            return
        }
        super.requestChildFocus(child, focused)
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (isBusyLayout) {
            return null
        }
        return super.focusSearch(focused, direction)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        try {
            super.onLayout(changed, l, t, r, b)
        } catch (e: Exception) {
            // Intercept Compose focus recursion or RecyclerView internal layout race conditions safely
        }
    }

    override fun removeViewAt(index: Int) {
        try {
            super.removeViewAt(index)
        } catch (e: Exception) {
        }
    }

    override fun removeViews(start: Int, count: Int) {
        try {
            super.removeViews(start, count)
        } catch (e: Exception) {
        }
    }

    override fun removeView(view: View?) {
        try {
            super.removeView(view)
        } catch (e: Exception) {
        }
    }

    override fun removeViewInLayout(view: View?) {
        try {
            super.removeViewInLayout(view)
        } catch (e: Exception) {
        }
    }

    override fun requestChildRectangleOnScreen(child: View, rect: Rect, immediate: Boolean): Boolean {
        if (!animationsEnabled) {
            return super.requestChildRectangleOnScreen(child, rect, true)
        }
        return super.requestChildRectangleOnScreen(child, rect, immediate)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (isHoldingHorizontalKey) {
                        stopScroll()
                        isHoldingHorizontalKey = false
                    }
                    if (selectedPosition <= 0) {
                        hasReleasedLeftKeySinceAt0 = true
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isHoldingHorizontalKey) {
                        stopScroll()
                        isHoldingHorizontalKey = false
                    }
                    return true
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = SystemClock.uptimeMillis()
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    val currentPos = selectedPosition
                    if (currentPos <= 0) {
                        if (hasReleasedLeftKeySinceAt0 && (now - item0SettledTime >= 350L) && event.repeatCount == 0) {
                            if (onNavigateLeftOnFirstItem?.invoke() == true) {
                                return true
                            }
                        }
                        stopScroll()
                        return true
                    }

                    hasReleasedLeftKeySinceAt0 = false
                    if (event.repeatCount > 0) {
                        isHoldingHorizontalKey = true
                    }

                    if (!animationsEnabled) {
                        val target = (currentPos - 1).coerceAtLeast(0)
                        stopScroll()
                        setSelectedPosition(target)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.repeatCount > 0) {
                        isHoldingHorizontalKey = true
                    }

                    if (!animationsEnabled) {
                        val total = rowAdapter?.itemCount ?: 0
                        val target = (selectedPosition + 1).coerceAtMost((total - 1).coerceAtLeast(0))
                        stopScroll()
                        setSelectedPosition(target)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (onNavigateDown?.invoke() == true) return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (onNavigateUp?.invoke() == true) return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (onNavigateBack?.invoke() == true) return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

class LeanbackCardViewHolder(
    val root: LinearLayout,
    val cardContainer: FrameLayout,
    val glowView: PlayStoreGlowView,
    val posterImage: ImageView,
    val titleText: TextView?,
    val progressBar: ProgressBar?,
    var cornerRadiusPx: Float,
    val borderWidthPx: Int,
    val glowMarginPx: Int,
    val adapter: LeanbackRowAdapter
) : RecyclerView.ViewHolder(root) {

    fun updateCornerRadius(newRadiusPx: Float) {
        if (cornerRadiusPx == newRadiusPx) return
        cornerRadiusPx = newRadiusPx

        (cardContainer.background as? GradientDrawable)?.cornerRadius = newRadiusPx

        posterImage.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (newRadiusPx > 0f) {
                    outline.setRoundRect(0, 0, view.width, view.height, newRadiusPx)
                } else {
                    outline.setRect(0, 0, view.width, view.height)
                }
            }
        }
        posterImage.invalidateOutline()

        glowView.cornerRadiusPx = newRadiusPx

        if (root.hasFocus()) {
            updateFocusVisual(true)
        }
    }

    private var currentItem: ModernCarouselItem? = null
    private val interpolator = DecelerateInterpolator(1.5f)
    var currentDominantColor: Int = Color.WHITE
    private var imageDisposable: coil3.request.Disposable? = null

    init {
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        root.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
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
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (adapter.onNavigateDown()) return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (adapter.onNavigateUp()) return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (adapter.onNavigateBack()) return@setOnKeyListener true
                    }
                }
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
        val anim = adapter.animationsEnabled()
        val targetScale = if (anim && hasFocus) 1.08f else 1.0f

        if (anim) {
            root.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(110)
                .setInterpolator(interpolator)
                .start()
        } else {
            root.animate().cancel()
            root.scaleX = targetScale
            root.scaleY = targetScale
        }

        cardContainer.elevation = 0f
        cardContainer.translationZ = 0f

        if (anim && hasFocus) {
            glowView.visibility = View.VISIBLE
            glowView.glowColor = currentDominantColor
            val tintedWhite = ColorUtils.blendARGB(currentDominantColor, Color.WHITE, 0.90f)
            cardContainer.foreground = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(borderWidthPx, tintedWhite)
                cornerRadius = cornerRadiusPx
            }
        } else if (hasFocus) {
            glowView.visibility = View.GONE
            cardContainer.foreground = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(borderWidthPx, Color.WHITE)
                cornerRadius = cornerRadiusPx
            }
        } else {
            glowView.visibility = View.GONE
            cardContainer.foreground = null
        }
    }

    fun bind(
        item: ModernCarouselItem,
        cardWidthPx: Int,
        cardHeightPx: Int,
        cornerRadiusPx: Float,
        showLabels: Boolean
    ) {
        if (currentItem?.key != item.key) {
            posterImage.setImageDrawable(null)
        }
        currentItem = item
        updateCornerRadius(cornerRadiusPx)
        val cachedColor = dominantColorCache.get(item.key)
        currentDominantColor = cachedColor ?: Color.parseColor("#38BDF8")

        // Set dimensions
        val cardLp = cardContainer.layoutParams
        if (cardLp.width != cardWidthPx || cardLp.height != cardHeightPx) {
            cardLp.width = cardWidthPx
            cardLp.height = cardHeightPx
            cardContainer.layoutParams = cardLp

            val glowLp = glowView.layoutParams as FrameLayout.LayoutParams
            glowLp.width = cardWidthPx + (glowMarginPx * 2)
            glowLp.height = cardHeightPx + (glowMarginPx * 2)
            glowView.layoutParams = glowLp
        }

        // Title
        if (showLabels && titleText != null) {
            titleText.visibility = View.VISIBLE
            titleText.text = item.title
        } else {
            titleText?.visibility = View.GONE
        }

        // Progress bar for Continue Watching
        val cwPayload = item.payload as? ModernPayload.ContinueWatching
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

        // Image loading via Coil với Dynamic Color extraction & Instant Memory Cache
        val imageUrl = item.imageUrl ?: item.heroPreview.poster ?: item.heroPreview.backdrop
        if (!imageUrl.isNullOrBlank()) {
            val ctx = posterImage.context
            val request = ImageRequest.Builder(ctx)
                .data(imageUrl)
                .size(cardWidthPx, cardHeightPx)
                .allowHardware(false)
                .crossfade(false)
                .target(
                    object : Target {
                        override fun onStart(placeholder: Image?) {
                            if (placeholder != null) {
                                posterImage.setImageDrawable(placeholder.asDrawable(ctx.resources))
                            }
                            // Retain previous image while loading to avoid flashing black
                        }
                        override fun onSuccess(result: Image) {
                            posterImage.setImageDrawable(result.asDrawable(ctx.resources))
                            val bmp = (result as? BitmapImage)?.bitmap
                            if (bmp != null && !bmp.isRecycled) {
                                val cached = dominantColorCache.get(item.key)
                                val extracted = cached ?: extractDominantColor(bmp).also {
                                    dominantColorCache.put(item.key, it)
                                }
                                currentDominantColor = extracted
                                if (root.hasFocus()) {
                                    updateFocusVisual(true)
                                }
                            }
                        }
                        override fun onError(error: Image?) {
                            if (error != null) {
                                posterImage.setImageDrawable(error.asDrawable(ctx.resources))
                            } else {
                                posterImage.setImageDrawable(null)
                            }
                        }
                    }
                )
                .build()
            imageDisposable?.dispose()
            imageDisposable = SingletonImageLoader.get(ctx).enqueue(request)
        } else {
            posterImage.setImageDrawable(null)
        }
    }

    fun unbind() {
        imageDisposable?.dispose()
        imageDisposable = null
        glowView.visibility = View.GONE
        cardContainer.foreground = null
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
    var onLoadMore: () -> Unit,
    var onNavigateDown: () -> Boolean,
    var onNavigateUp: () -> Boolean,
    var onNavigateLeftOnFirstItem: () -> Boolean,
    var onNavigateBack: () -> Boolean
) : RecyclerView.Adapter<LeanbackCardViewHolder>() {

    private var items: List<ModernCarouselItem> = emptyList()
    private var pendingItems: List<ModernCarouselItem>? = null

    fun getItem(position: Int): ModernCarouselItem? = items.getOrNull(position)

    fun updateConfig(
        newWidthPx: Int,
        newHeightPx: Int,
        newRadiusPx: Float,
        newShowLabels: Boolean
    ) {
        val changed = cardWidthPx != newWidthPx ||
            cardHeightPx != newHeightPx ||
            cornerRadiusPx != newRadiusPx ||
            showLabels != newShowLabels
        if (changed) {
            cardWidthPx = newWidthPx
            cardHeightPx = newHeightPx
            cornerRadiusPx = newRadiusPx
            showLabels = newShowLabels
            notifyItemRangeChanged(0, items.size)
        }
    }

    var attachedRecyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        attachedRecyclerView = null
    }

    fun submitItems(newItems: List<ModernCarouselItem>) {
        if (items === newItems) return
        if (items.size == newItems.size && items == newItems) return

        val rv = attachedRecyclerView
        if (rv != null && (rv.isComputingLayout || rv.isInLayout || rv.scrollState != RecyclerView.SCROLL_STATE_IDLE)) {
            pendingItems = newItems
            return
        }
        pendingItems = null
        applyItems(newItems)
    }

    fun onScrollSettled() {
        val pending = pendingItems ?: return
        pendingItems = null
        applyItems(pending)
    }

    private fun applyItems(newItems: List<ModernCarouselItem>) {
        val oldItems = items
        val oldSize = oldItems.size
        val newSize = newItems.size
        items = newItems

        val rv = attachedRecyclerView
        if (rv != null && rv.isComputingLayout) {
            rv.post {
                if (attachedRecyclerView === rv) {
                    applyItemsInternal(oldItems, oldSize, newSize, newItems)
                }
            }
        } else {
            applyItemsInternal(oldItems, oldSize, newSize, newItems)
        }
    }

    private fun applyItemsInternal(
        oldItems: List<ModernCarouselItem>,
        oldSize: Int,
        newSize: Int,
        newItems: List<ModernCarouselItem>
    ) {
        if (oldSize > 0 && newSize > oldSize && oldItems == newItems.take(oldSize)) {
            notifyItemRangeInserted(oldSize, newSize - oldSize)
        } else {
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeanbackCardViewHolder {
        val context = parent.context

        // Root container: getRootView() returns this so rootViewRequestFocus() never escapes to DecorView
        val root = object : LinearLayout(context) {
            override fun getRootView(): View = this
        }.apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = ViewGroup.LayoutParams(cardWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val glowSpreadDp = 18f
        val glowSpreadPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, glowSpreadDp, context.resources.displayMetrics)
        val glowMarginPx = (glowSpreadPx * 1.35f).toInt()

        // Card container (bo góc tròn, không để lộ góc nhọn xám)
        val cardContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(cardWidthPx, cardHeightPx)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = cornerRadiusPx
            }
            clipToOutline = false
            clipChildren = false
        }

        // Play Store Glow View (nằm sau posterImage, tỏa ánh sáng ra ngoài nền)
        val glowView = PlayStoreGlowView(context).apply {
            this.cornerRadiusPx = cornerRadiusPx
            this.glowSpreadPx = glowSpreadPx
            this.glowMarginPx = glowMarginPx.toFloat()
            layoutParams = FrameLayout.LayoutParams(
                cardWidthPx + (glowMarginPx * 2),
                cardHeightPx + (glowMarginPx * 2)
            ).apply {
                leftMargin = -glowMarginPx
                topMargin = -glowMarginPx
            }
            visibility = View.GONE
        }
        cardContainer.addView(glowView)

        // Poster image
        val posterImage = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                }
            }
            clipToOutline = true
        }
        cardContainer.addView(posterImage)

        // Progress bar for continue watching
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics).toInt()
            ).apply {
                gravity = Gravity.BOTTOM
            }
            max = 100
            progress = 0
            visibility = View.GONE
        }
        cardContainer.addView(progressBar)

        root.addView(cardContainer)

        // Title text
        val titleText: TextView? = if (showLabels) {
            TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6f, context.resources.displayMetrics).toInt()
                }
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }.also { root.addView(it) }
        } else null

        val borderWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2.0f, context.resources.displayMetrics).toInt().coerceAtLeast(2)

        return LeanbackCardViewHolder(
            root = root,
            cardContainer = cardContainer,
            glowView = glowView,
            posterImage = posterImage,
            titleText = titleText,
            progressBar = progressBar,
            cornerRadiusPx = cornerRadiusPx,
            borderWidthPx = borderWidthPx,
            glowMarginPx = glowMarginPx,
            adapter = this
        )
    }

    override fun onBindViewHolder(holder: LeanbackCardViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, cardWidthPx, cardHeightPx, cornerRadiusPx, showLabels)
    }

    override fun onViewRecycled(holder: LeanbackCardViewHolder) {
        holder.unbind()
    }
}

@Composable
fun LeanbackModernRow(
    rowKey: String,
    items: List<ModernCarouselItem>,
    cardWidth: Dp,
    cardHeight: Dp,
    cornerRadius: Dp,
    showLabels: Boolean,
    animationsEnabled: Boolean,
    startPadding: Dp,
    spacing: Dp,
    onItemFocused: (Int, ModernCarouselItem) -> Unit,
    onItemClick: (ModernCarouselItem) -> Unit,
    onItemLongClick: (ModernCarouselItem) -> Unit,
    onLoadMore: () -> Unit,
    onNavigateDown: () -> Boolean = { false },
    onNavigateUp: () -> Boolean = { false },
    onNavigateLeftOnFirstItem: () -> Boolean = { false },
    onNavigateBack: () -> Boolean = { false },
    rowGrids: MutableMap<String, HorizontalGridView>? = null,
    isPendingFocus: Boolean = false,
    pendingFocusIndex: Int = 0,
    pendingFocusNonce: Int = 0,
    onClearPendingFocus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cardWidthPx = with(density) { cardWidth.roundToPx() }
    val cardHeightPx = with(density) { cardHeight.roundToPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val startPaddingPx = with(density) { startPadding.roundToPx() }
    val spacingPx = with(density) { spacing.roundToPx() }
    val verticalPaddingDp = 34.dp
    val verticalPaddingPx = with(density) { verticalPaddingDp.roundToPx() }
    val labelHeightPx = if (showLabels) with(density) { 26.dp.roundToPx() } else 0
    val totalRowHeightPx = cardHeightPx + labelHeightPx + (verticalPaddingPx * 2)

    val labelHeightDp = if (showLabels) 26.dp else 0.dp
    val totalRowHeightDp = cardHeight + labelHeightDp + (verticalPaddingDp * 2)

    val adapter = remember(rowKey) {
        LeanbackRowAdapter(
            cardWidthPx = cardWidthPx,
            cardHeightPx = cardHeightPx,
            cornerRadiusPx = cornerRadiusPx,
            showLabels = showLabels,
            animationsEnabled = { animationsEnabled },
            onItemFocused = onItemFocused,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onLoadMore = onLoadMore,
            onNavigateDown = onNavigateDown,
            onNavigateUp = onNavigateUp,
            onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem,
            onNavigateBack = onNavigateBack
        )
    }

    DisposableEffect(rowKey) {
        onDispose {
            rowGrids?.remove(rowKey)
        }
    }

    LaunchedEffect(isPendingFocus, pendingFocusNonce) {
        if (isPendingFocus && items.isNotEmpty()) {
            val targetIdx = pendingFocusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val gridView = rowGrids?.get(rowKey)
            if (gridView != null) {
                gridView.post {
                    if (animationsEnabled) {
                        gridView.setSelectedPositionSmooth(targetIdx)
                    } else {
                        gridView.setSelectedPosition(targetIdx)
                    }
                    gridView.requestFocus()
                }
            }
            onClearPendingFocus()
        }
    }

    AndroidView(
        modifier = modifier
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
                setNumRows(1)
                windowAlignment = BaseGridView.WINDOW_ALIGN_LOW_EDGE
                windowAlignmentOffset = startPaddingPx
                windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
                itemAlignmentOffset = 0
                itemAlignmentOffsetPercent = 0f
                setItemSpacing(spacingPx)
                clipToPadding = false
                clipChildren = false
                setPadding(startPaddingPx, verticalPaddingPx, startPaddingPx, verticalPaddingPx)
                this.adapter = adapter
                this.onNavigateDown = onNavigateDown
                this.onNavigateUp = onNavigateUp
                this.onNavigateBack = onNavigateBack
                this.onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem
                this.animationsEnabled = animationsEnabled
                rowGrids?.put(rowKey, this)
            }
        },
        update = { gridView ->
            if (gridView is InteropHorizontalGridView) {
                gridView.onNavigateDown = onNavigateDown
                gridView.onNavigateUp = onNavigateUp
                gridView.onNavigateBack = onNavigateBack
                gridView.onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem
                gridView.animationsEnabled = animationsEnabled
            }
            gridView.setAnimateChildLayout(animationsEnabled)
            adapter.animationsEnabled = { animationsEnabled }
            adapter.onItemFocused = onItemFocused
            adapter.onItemClick = onItemClick
            adapter.onItemLongClick = onItemLongClick
            adapter.onLoadMore = onLoadMore
            adapter.onNavigateDown = onNavigateDown
            adapter.onNavigateUp = onNavigateUp
            adapter.onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem
            adapter.onNavigateBack = onNavigateBack
            if (gridView.adapter !== adapter) {
                gridView.adapter = adapter
            }
            adapter.updateConfig(cardWidthPx, cardHeightPx, cornerRadiusPx, showLabels)
            rowGrids?.put(rowKey, gridView)
            adapter.submitItems(items)


        }
    )
}
