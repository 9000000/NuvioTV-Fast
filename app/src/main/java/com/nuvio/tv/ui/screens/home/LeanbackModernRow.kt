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
 *   + Kết hợp clipOutPath loại bỏ 100% diện tích bên trong poster, đảm bảo poster sắc nét hoàn hảo
 *     và CPU không phải tính toán Gaussian Blur vô ích dưới lớp poster.
 *   + Tỏa ra ngoài với 3 tầng tán xạ Gaussian mịn màng (Core, Mid, Ambient Aura),
 *     tạo hiệu ứng phát sáng đèn neon liên tục, không có các vòng tròn đồng tâm giật cục.
 * - Đổi màu phát quang linh hoạt theo màu chủ đạo rực rỡ trích xuất từ poster.
 */
class PlayStoreGlowView(context: Context) : View(context) {
    var glowColor: Int = Color.WHITE
        set(value) {
            if (field != value) {
                field = value
                updatePaints()
                invalidate()
            }
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

    private val cardPath = Path()

    // 3 bộ lọc Gaussian blur cho hiệu ứng ánh sáng Neon thực tế (chuẩn Play Store TV & Cyberpunk):
    // 1. Aura: Quầng sáng tán xạ khí quyển mềm mại tỏa rộng ra nền đen (FILL, Blur.NORMAL)
    // 2. Mid: Đai quang học dày dặn ôm trọn 4 cạnh và bù năng lượng 4 góc bo (STROKE, Blur.NORMAL)
    // 3. Core: Sợi dây tim neon phát quang cường độ cao ôm sát mép poster (STROKE, Blur.NORMAL)
    private var coreFilter: BlurMaskFilter? = null
    private var midFilter: BlurMaskFilter? = null
    private var auraFilter: BlurMaskFilter? = null

    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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
        val rCore = (glowSpreadPx * 0.15f).coerceAtLeast(1f)
        val rMid = (glowSpreadPx * 0.35f).coerceAtLeast(1f)
        val rAura = (glowSpreadPx * 0.65f).coerceAtLeast(1f)
        coreFilter = BlurMaskFilter(rCore, BlurMaskFilter.Blur.NORMAL)
        midFilter = BlurMaskFilter(rMid, BlurMaskFilter.Blur.NORMAL)
        auraFilter = BlurMaskFilter(rAura, BlurMaskFilter.Blur.NORMAL)
        corePaint.maskFilter = coreFilter
        midPaint.maskFilter = midFilter
        auraPaint.maskFilter = auraFilter

        auraPaint.strokeWidth = (glowSpreadPx * 0.75f).coerceAtLeast(4f)
        midPaint.strokeWidth = (glowSpreadPx * 0.40f).coerceAtLeast(2f)
        corePaint.strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 3.5f, resources.displayMetrics
        ).coerceAtLeast(2f)
    }

    private fun updatePaints() {
        if (glowColor == 0) return
        val coreColor = ColorUtils.blendARGB(glowColor, Color.WHITE, 0.15f)
        corePaint.color = ColorUtils.setAlphaComponent(coreColor, 255)
        midPaint.color = ColorUtils.setAlphaComponent(glowColor, 185)
        auraPaint.color = ColorUtils.setAlphaComponent(glowColor, 85)
    }

    override fun onDraw(canvas: Canvas) {
        if (glowColor == 0 || glowSpreadPx <= 0f) return
        val margin = if (glowMarginPx > 0f) glowMarginPx else glowSpreadPx
        val cardLeft = margin
        val cardTop = margin
        val cardRight = width - margin
        val cardBottom = height - margin

        cardPath.reset()
        if (cornerRadiusPx > 0f) {
            cardPath.addRoundRect(
                cardLeft,
                cardTop,
                cardRight,
                cardBottom,
                cornerRadiusPx,
                cornerRadiusPx,
                Path.Direction.CW
            )
        } else {
            cardPath.addRect(
                cardLeft,
                cardTop,
                cardRight,
                cardBottom,
                Path.Direction.CW
            )
        }

        // Vẽ từ tầng tán xạ xa (Aura) đến tầng vầng sáng quang học (Mid) rồi sợi tim Neon rực rỡ (Core).
        // KHÔNG dùng clipOutPath: để ánh sáng neon bắt nguồn liên tục từ dưới lưng tấm poster tỏa ra ngoài,
        // ôm trọn vẹn 100% cả 4 góc bo cong lẫn 4 cạnh, triệt tiêu hoàn toàn các khoảng đen hở góc.
        if (auraPaint.maskFilter != null) {
            canvas.drawPath(cardPath, auraPaint)
        }
        if (midPaint.maskFilter != null) {
            canvas.drawPath(cardPath, midPaint)
        }
        if (corePaint.maskFilter != null) {
            canvas.drawPath(cardPath, corePaint)
        }
    }
}

class InteropHorizontalGridView(context: Context) : HorizontalGridView(context) {
    var onNavigateDown: (() -> Boolean)? = null
    var onNavigateUp: (() -> Boolean)? = null
    var onNavigateBack: (() -> Boolean)? = null
    var onNavigateLeftOnFirstItem: (() -> Boolean)? = null
    var animationsEnabled: Boolean = true

    var isHoldingHorizontalKey: Boolean = false
        private set

    private var arrivalAt0Time = 0L
    private var arrivedAt0FromRight = false
    private var hasReleasedKeyAfterArrivingAt0 = true

    private val rowAdapter: LeanbackRowAdapter?
        get() = adapter as? LeanbackRowAdapter

    init {
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        itemAnimator = null
        layoutManager?.isItemPrefetchEnabled = false
        setHasFixedSize(true)
        setItemViewCacheSize(8)

        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    isHoldingHorizontalKey = false
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
                    arrivalAt0Time = SystemClock.uptimeMillis()
                } else {
                    arrivedAt0FromRight = true
                    hasReleasedKeyAfterArrivingAt0 = false
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

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) {
            isHoldingHorizontalKey = false
        } else if (selectedPosition <= 0) {
            hasReleasedKeyAfterArrivingAt0 = true
            arrivedAt0FromRight = false
        }
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
                        hasReleasedKeyAfterArrivingAt0 = true
                        arrivedAt0FromRight = false
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
                        val arrivedRecently = arrivedAt0FromRight && (now - arrivalAt0Time < 300L)
                        if (!arrivedRecently && event.repeatCount == 0) {
                            hasReleasedKeyAfterArrivingAt0 = true
                            arrivedAt0FromRight = false
                            if (onNavigateLeftOnFirstItem?.invoke() == true) {
                                return true
                            }
                        }
                        stopScroll()
                        return true
                    }

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
                    isHoldingHorizontalKey = false
                    if (onNavigateDown?.invoke() == true) return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    isHoldingHorizontalKey = false
                    if (onNavigateUp?.invoke() == true) return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    isHoldingHorizontalKey = false
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

    // Tái sử dụng 1 đối tượng GradientDrawable duy nhất cho viền Focus để triệt tiêu cấp phát bộ nhớ khi cuộn
    private val focusBorderDrawable = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = cornerRadiusPx
    }

    // Trì hoãn nhẹ việc bật Software Layer GlowView khi người dùng đang nhấn giữ phím cuộn liên tục
    private val deferredGlowRunnable = Runnable {
        if (root.hasFocus() && adapter.animationsEnabled()) {
            glowView.glowColor = currentDominantColor
            glowView.visibility = View.VISIBLE
        }
    }

    fun updateCornerRadius(newRadiusPx: Float) {
        val radiusChanged = cornerRadiusPx != newRadiusPx || glowView.cornerRadiusPx != newRadiusPx
        if (!radiusChanged) return
        cornerRadiusPx = newRadiusPx

        (cardContainer.background as? GradientDrawable)?.cornerRadius = newRadiusPx
        focusBorderDrawable.cornerRadius = newRadiusPx
        posterImage.invalidateOutline()
        glowView.cornerRadiusPx = newRadiusPx

        val horizMargin = (newRadiusPx * 0.75f).toInt().coerceAtLeast(6)
        (progressBar?.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.leftMargin != horizMargin || lp.rightMargin != horizMargin) {
                lp.leftMargin = horizMargin
                lp.rightMargin = horizMargin
                progressBar.layoutParams = lp
            }
        }

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
        root.removeCallbacks(deferredGlowRunnable)
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
            val tintedWhite = ColorUtils.blendARGB(currentDominantColor, Color.WHITE, 0.90f)
            focusBorderDrawable.setStroke(borderWidthPx, tintedWhite)
            focusBorderDrawable.cornerRadius = cornerRadiusPx
            cardContainer.foreground = focusBorderDrawable

            val isFastScrolling = (adapter.attachedRecyclerView as? InteropHorizontalGridView)?.isHoldingHorizontalKey == true
            if (isFastScrolling) {
                glowView.visibility = View.GONE
                root.postDelayed(deferredGlowRunnable, 55L)
            } else {
                glowView.glowColor = currentDominantColor
                glowView.visibility = View.VISIBLE
            }
        } else if (hasFocus) {
            glowView.visibility = View.GONE
            focusBorderDrawable.setStroke(borderWidthPx, Color.WHITE)
            focusBorderDrawable.cornerRadius = cornerRadiusPx
            cardContainer.foreground = focusBorderDrawable
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

            val rootLp = root.layoutParams
            if (rootLp != null && rootLp.width != cardWidthPx) {
                rootLp.width = cardWidthPx
                root.layoutParams = rootLp
            }
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

        // Image loading via Coil với Dynamic Color extraction & Hardware Bitmap khi đã có màu trong cache
        val imageUrl = item.imageUrl ?: item.heroPreview.poster ?: item.heroPreview.backdrop
        if (!imageUrl.isNullOrBlank()) {
            val ctx = posterImage.context
            val hasCachedColor = cachedColor != null
            val request = ImageRequest.Builder(ctx)
                .data(imageUrl)
                .size(cardWidthPx, cardHeightPx)
                .allowHardware(hasCachedColor)
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
                            // Gợi ý 2: Triệt tiêu 100% GPU Overdraw khi ảnh đã nạp xong kín thẻ
                            cardContainer.background = null
                            val existingColor = dominantColorCache.get(item.key)
                            if (existingColor != null) {
                                currentDominantColor = existingColor
                            } else {
                                val bmp = (result as? BitmapImage)?.bitmap
                                if (bmp != null && !bmp.isRecycled && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || bmp.config != Bitmap.Config.HARDWARE)) {
                                    val extracted = extractDominantColor(bmp).also {
                                        dominantColorCache.put(item.key, it)
                                    }
                                    currentDominantColor = extracted
                                }
                            }
                            if (root.hasFocus()) {
                                updateFocusVisual(true)
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

        // Always sync focus visual to current focus state when bound or recycled
        updateFocusVisual(root.hasFocus())
    }

    fun unbind() {
        root.removeCallbacks(deferredGlowRunnable)
        imageDisposable?.dispose()
        imageDisposable = null
        glowView.visibility = View.GONE
        cardContainer.foreground = null
        // Phục hồi lại background placeholder khi view được recycle
        if (cardContainer.background == null) {
            cardContainer.background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = cornerRadiusPx
            }
        }
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
            this.cornerRadiusPx = this@LeanbackRowAdapter.cornerRadiusPx
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

        // Progress bar for continue watching (Gợi ý 1: Bo tròn 2 đầu dạng viên thuốc Pill-Shape, không bao giờ lòi góc nhọn)
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            val h = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3.5f, context.resources.displayMetrics).toInt().coerceAtLeast(2)
            val horizMargin = (cornerRadiusPx * 0.75f).toInt().coerceAtLeast(6)
            val bMargin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics).toInt()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                h
            ).apply {
                gravity = Gravity.BOTTOM
                leftMargin = horizMargin
                rightMargin = horizMargin
                bottomMargin = bMargin
            }
            max = 100
            progress = 0
            
            // Thiết lập Drawable bo tròn viên thuốc cho cả track và progress
            val trackPill = GradientDrawable().apply {
                setColor(Color.parseColor("#4D000000")) // Đen mờ 30%
                cornerRadius = h / 2f
            }
            val progPill = GradientDrawable().apply {
                setColor(Color.parseColor("#38BDF8")) // Electric Cyan
                cornerRadius = h / 2f
            }
            val clipProg = android.graphics.drawable.ClipDrawable(progPill, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
            progressDrawable = LayerDrawable(arrayOf(trackPill, clipProg)).apply {
                setId(0, android.R.id.background)
                setId(1, android.R.id.progress)
            }
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
    recycledViewPool: RecyclerView.RecycledViewPool? = null,
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

    fun executeRowFocus(grid: InteropHorizontalGridView, targetIdx: Int) {
        grid.post {
            if (!grid.isAttachedToWindow) return@post
            if (animationsEnabled) {
                grid.setSelectedPositionSmooth(targetIdx)
            } else {
                grid.setSelectedPosition(targetIdx)
            }
            grid.requestFocus()
            val vh = grid.findViewHolderForAdapterPosition(targetIdx)
            if (vh != null) {
                vh.itemView.requestFocus()
                (vh as? LeanbackCardViewHolder)?.updateFocusVisual(true)
            } else {
                grid.post {
                    if (grid.isAttachedToWindow) {
                        val deferredVh = grid.findViewHolderForAdapterPosition(targetIdx)
                        deferredVh?.itemView?.requestFocus()
                        (deferredVh as? LeanbackCardViewHolder)?.updateFocusVisual(true)
                    }
                }
            }
        }
    }

    LaunchedEffect(isPendingFocus, pendingFocusNonce, items.size) {
        if (isPendingFocus && items.isNotEmpty()) {
            val targetIdx = pendingFocusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val gridView = rowGrids?.get(rowKey) as? InteropHorizontalGridView
            if (gridView != null && gridView.isAttachedToWindow) {
                executeRowFocus(gridView, targetIdx)
                onClearPendingFocus()
            }
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
                setHasFixedSize(true)
                setItemViewCacheSize(8)
                recycledViewPool?.let { setRecycledViewPool(it) }
                rowGrids?.put(rowKey, this)
                if (isPendingFocus && items.isNotEmpty()) {
                    val targetIdx = pendingFocusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                    executeRowFocus(this, targetIdx)
                    onClearPendingFocus()
                }
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
            recycledViewPool?.let {
                if (gridView.recycledViewPool !== it) {
                    gridView.setRecycledViewPool(it)
                }
            }
            adapter.updateConfig(cardWidthPx, cardHeightPx, cornerRadiusPx, showLabels)
            rowGrids?.put(rowKey, gridView)
            adapter.submitItems(items)
            if (isPendingFocus && items.isNotEmpty() && gridView is InteropHorizontalGridView) {
                val targetIdx = pendingFocusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                executeRowFocus(gridView, targetIdx)
                onClearPendingFocus()
            }
        }
    )
}
