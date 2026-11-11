package com.nuvio.tv.ui.screens.home

import android.animation.ValueAnimator
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
import android.view.ViewTreeObserver
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.BitmapImage
import coil3.Image
import coil3.asDrawable
import coil3.target.Target
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import kotlinx.coroutines.awaitCancellation

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

/**
 * Khung Focus Ring nổi độc lập trên cùng của toàn bộ hàng poster (One focus ring per row).
 * - Luôn nổi ở trên cùng (elevation cao, nằm ngoài các card con).
 * - Khi chuyển focus giữa các card: Khung KHÔNG bị biến mất rồi hiện lại, mà thực hiện hoạt họa
 *   lướt trôi (glide animation 180ms) từ tọa độ card cũ sang card mới.
 * - Hiển thị viền vàng hổ phách (#F59E0B) cho poster portrait (như Ảnh 1).
 * - Hiển thị viền trắng (#FFFFFF) + quầng sáng phát quang Neon Glow (#FFB300) cho thẻ landscape (như Ảnh 2).
 */
class ModernRowFloatingFocusRingView(context: Context) : View(context) {
    var cornerRadiusPx: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    val borderWidthPx: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 2.5f, context.resources.displayMetrics
    )

    var strokeColor: Int = Color.parseColor("#F59E0B")
        set(value) {
            if (field != value) {
                field = value
                borderPaint.color = value
                invalidate()
            }
        }

    var glowColor: Int = Color.parseColor("#FFB300")
        set(value) {
            if (field != value) {
                field = value
                updateGlowPaints()
                invalidate()
            }
        }

    var isGlowEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = strokeColor
        strokeWidth = borderWidthPx
    }

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

    private val ringPath = Path()
    val glowSpreadPx: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 16f, context.resources.displayMetrics
    )
    val glowMarginPx: Float = glowSpreadPx * 1.35f

    var currentTargetView: View? = null
        private set
    private var glideAnimator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        updateGlowPaints()
        visibility = GONE
        isFocusable = false
        isFocusableInTouchMode = false
    }

    private fun updateGlowPaints() {
        val rMid = (glowSpreadPx * 0.35f).coerceAtLeast(1f)
        val rAura = (glowSpreadPx * 0.65f).coerceAtLeast(1f)
        midPaint.maskFilter = BlurMaskFilter(rMid, BlurMaskFilter.Blur.NORMAL)
        auraPaint.maskFilter = BlurMaskFilter(rAura, BlurMaskFilter.Blur.NORMAL)
        midPaint.strokeWidth = (glowSpreadPx * 0.40f).coerceAtLeast(2f)
        auraPaint.strokeWidth = (glowSpreadPx * 0.75f).coerceAtLeast(4f)
        midPaint.color = ColorUtils.setAlphaComponent(glowColor, 185)
        auraPaint.color = ColorUtils.setAlphaComponent(glowColor, 85)
    }

    override fun onDraw(canvas: Canvas) {
        val pad = glowMarginPx
        val w = width - (pad * 2)
        val h = height - (pad * 2)
        if (w <= 0 || h <= 0) return

        ringPath.reset()
        if (cornerRadiusPx > 0f) {
            ringPath.addRoundRect(pad, pad, pad + w, pad + h, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
        } else {
            ringPath.addRect(pad, pad, pad + w, pad + h, Path.Direction.CW)
        }

        if (isGlowEnabled) {
            canvas.drawPath(ringPath, auraPaint)
            canvas.drawPath(ringPath, midPaint)
        }

        borderPaint.strokeWidth = borderWidthPx
        canvas.drawPath(ringPath, borderPaint)
    }

    fun moveToTarget(target: View, isLandscape: Boolean, dominantColor: Int, animated: Boolean) {
        currentTargetView = target
        val parent = parent as? ViewGroup ?: return

        if (!target.isAttachedToWindow || !parent.isAttachedToWindow || target.width <= 0 || target.height <= 0 || parent.width <= 0) {
            target.post {
                if (currentTargetView === target && isAttachedToWindow) {
                    moveToTarget(target, isLandscape, dominantColor, animated)
                }
            }
            return
        }

        // Cancel previous animations to prevent old hideRing withEndAction from overriding visibility
        animate().cancel()
        glideAnimator?.cancel()

        val rect = Rect(0, 0, target.width, target.height)
        val success = try {
            parent.offsetDescendantRectToMyCoords(target, rect)
            true
        } catch (_: Exception) {
            false
        }

        val grid = (parent as? ModernRowContainerLayout)?.gridView
        val minX = (grid?.paddingLeft ?: 0).toFloat() - glowMarginPx
        val maxX = (parent.width - (grid?.paddingRight ?: 0) - target.width).toFloat() - glowMarginPx

        val rawTargetX = if (success) {
            rect.left.toFloat() - glowMarginPx
        } else {
            val parentLoc = IntArray(2)
            parent.getLocationInWindow(parentLoc)
            val targetLoc = IntArray(2)
            target.getLocationInWindow(targetLoc)
            (targetLoc[0] - parentLoc[0]).toFloat() - glowMarginPx
        }

        val targetX = if (maxX > minX) rawTargetX.coerceIn(minX, maxX) else rawTargetX

        val rawTargetY = if (success) {
            rect.top.toFloat() - glowMarginPx
        } else {
            val parentLoc = IntArray(2)
            parent.getLocationInWindow(parentLoc)
            val targetLoc = IntArray(2)
            target.getLocationInWindow(targetLoc)
            (targetLoc[1] - parentLoc[1]).toFloat() - glowMarginPx
        }
        val targetY = rawTargetY
        val targetWidth = target.width + (glowMarginPx * 2).toInt()
        val targetHeight = target.height + (glowMarginPx * 2).toInt()

        val resolvedGlowColor = if (dominantColor != 0 && dominantColor != Color.WHITE && dominantColor != Color.BLACK) {
            dominantColor
        } else {
            Color.parseColor("#F59E0B")
        }

        strokeColor = resolvedGlowColor
        glowColor = resolvedGlowColor
        isGlowEnabled = true

        val lp = layoutParams
        if (lp != null && (lp.width != targetWidth || lp.height != targetHeight)) {
            lp.width = targetWidth
            lp.height = targetHeight
            layoutParams = lp
        }

        // If ring is not fully visible or animated is false, snap directly to coordinates and fade in
        if (visibility != VISIBLE || alpha < 0.8f || !animated) {
            translationX = targetX
            translationY = targetY
            if (visibility != VISIBLE || alpha <= 0f) {
                alpha = 0f
                visibility = VISIBLE
                animate().alpha(1f).setDuration(120).start()
            } else {
                alpha = 1f
                visibility = VISIBLE
            }
            return
        }

        // Lướt trôi mượt mà từ vị trí hiện tại sang vị trí mới (180ms - chuẩn NuvioTV-Fork ROW_GLIDE_MS)
        val startX = translationX
        val startY = translationY
        glideAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                val frac = anim.animatedFraction
                translationX = startX + (targetX - startX) * frac
                translationY = startY + (targetY - startY) * frac
            }
            start()
        }
    }

    fun onParentScrolled(dx: Int) {
        val target = currentTargetView ?: return
        if (visibility != VISIBLE) return
        val parent = parent as? ViewGroup ?: return
        val grid = (parent as? ModernRowContainerLayout)?.gridView
        val minX = (grid?.paddingLeft ?: 0).toFloat() - glowMarginPx
        val maxX = (parent.width - (grid?.paddingRight ?: 0) - target.width).toFloat() - glowMarginPx

        if (maxX <= minX) return

        val rect = Rect(0, 0, target.width, target.height)
        val success = try {
            parent.offsetDescendantRectToMyCoords(target, rect)
            true
        } catch (_: Exception) {
            false
        }

        val currentCardX = if (success) {
            rect.left.toFloat() - glowMarginPx
        } else {
            val parentLoc = IntArray(2)
            parent.getLocationInWindow(parentLoc)
            val targetLoc = IntArray(2)
            target.getLocationInWindow(targetLoc)
            (targetLoc[0] - parentLoc[0]).toFloat() - glowMarginPx
        }

        // Khi đi đến đầu hàng (<= minX + 2px) hoặc cuối hàng (>= maxX - 2px):
        // Cố định viền tuyệt đối tại biên (minX hoặc maxX) để poster tự cuộn tới khớp vào mà KHÔNG bị giật!
        if (currentCardX <= minX + 2f) {
            glideAnimator?.cancel()
            translationX = minX
            return
        }
        if (currentCardX >= maxX - 2f) {
            glideAnimator?.cancel()
            translationX = maxX
            return
        }

        // Khi ở giữa màn hình và không có animation: neo theo thẻ
        if (glideAnimator?.isRunning != true) {
            translationX = currentCardX.coerceIn(minX, maxX)
        }
    }

    fun onScrollSettled() {
        val target = currentTargetView ?: return
        if (visibility != VISIBLE) return
        val parent = parent as? ViewGroup ?: return
        val grid = (parent as? ModernRowContainerLayout)?.gridView
        val minX = (grid?.paddingLeft ?: 0).toFloat() - glowMarginPx
        val maxX = (parent.width - (grid?.paddingRight ?: 0) - target.width).toFloat() - glowMarginPx

        if (maxX <= minX) return

        val rect = Rect(0, 0, target.width, target.height)
        val success = try {
            parent.offsetDescendantRectToMyCoords(target, rect)
            true
        } catch (_: Exception) {
            false
        }

        val currentCardX = if (success) {
            rect.left.toFloat() - glowMarginPx
        } else {
            val parentLoc = IntArray(2)
            parent.getLocationInWindow(parentLoc)
            val targetLoc = IntArray(2)
            target.getLocationInWindow(targetLoc)
            (targetLoc[0] - parentLoc[0]).toFloat() - glowMarginPx
        }

        val settledX = currentCardX.coerceIn(minX, maxX)
        if (Math.abs(translationX - settledX) > 1.5f) {
            animate().translationX(settledX).setDuration(80).start()
        }
    }

    fun hideRing(immediate: Boolean = false) {
        animate().cancel()
        glideAnimator?.cancel()
        currentTargetView = null
        if (immediate) {
            visibility = GONE
            alpha = 0f
        } else if (visibility == VISIBLE) {
            animate().alpha(0f).setDuration(100).withEndAction {
                visibility = GONE
            }.start()
        }
    }
}

/**
 * Container bao bọc HorizontalGridView và ModernRowFloatingFocusRingView trên cùng.
 */
class ModernRowContainerLayout(context: Context) : FrameLayout(context) {
    lateinit var gridView: InteropHorizontalGridView
    lateinit var floatingRingView: ModernRowFloatingFocusRingView

    override fun clearFocus() {
        super.clearFocus()
        if (::floatingRingView.isInitialized && !hasFocus()) {
            floatingRingView.hideRing(true)
        }
    }

    override fun clearChildFocus(child: View?) {
        if (::gridView.isInitialized &&
            gridView.canTakeFocus?.invoke() != false &&
            (gridView.hasPendingHorizontalNavigation || gridView.isSubmittingItems)
        ) {
            // Prevent clearing focus up to ViewRootImpl / LazyColumn during horizontal navigation or dataset updates.
            return
        }
        super.clearChildFocus(child)
    }

    override fun requestChildRectangleOnScreen(child: View, rect: Rect, immediate: Boolean): Boolean {
        // Prevent child rectangle requests from bubbling up to AndroidComposeView / LazyColumn,
        // which cancels the smooth vertical animateScrollToItem animation when a row card receives focus.
        return true
    }
}

class InteropHorizontalGridView(context: Context) : HorizontalGridView(context) {
    var onNavigateDown: (() -> Boolean)? = null
    var onNavigateUp: (() -> Boolean)? = null
    var onNavigateBack: (() -> Boolean)? = null
    var onNavigateLeftOnFirstItem: (() -> Boolean)? = null
    var animationsEnabled: Boolean = true

    var floatingRingView: ModernRowFloatingFocusRingView? = null

    private val globalFocusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        val hasRowFocus = hasFocus() || (newFocus != null && isDescendant(newFocus))
        if (!hasRowFocus) {
            isHoldingHorizontalKey = false
            pendingTargetPosition = RecyclerView.NO_POSITION
            floatingRingView?.hideRing(false)
        } else {
            val focusedChild = newFocus ?: findFocus()
            if (focusedChild != null && isDescendant(focusedChild)) {
                val holder = findContainingViewHolder(focusedChild) as? LeanbackCardViewHolder
                if (holder != null) {
                    holder.updateFocusVisual(true)
                    val isLandscape = rowAdapter?.let { it.cardWidthPx > it.cardHeightPx } ?: false
                    floatingRingView?.moveToTarget(holder.cardContainer, isLandscape, holder.currentDominantColor, false)
                } else {
                    ensureChildFocusAndRing(animated = false)
                }
            } else if (hasFocus()) {
                ensureChildFocusAndRing(animated = false)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusListener)
        floatingRingView?.hideRing(true)
    }

    override fun clearFocus() {
        super.clearFocus()
        if (!hasFocus()) {
            floatingRingView?.hideRing(true)
        }
    }

    /**
     * Returns true when this row is the current navigation target. Deferred focus work
     * (posts, retries, scroll-idle callbacks) must only grab focus when this row already
     * owns focus or is the target, otherwise it steals focus back from the row the user moved to.
     */
    var canTakeFocus: (() -> Boolean)? = null

    fun mayTakeFocus(): Boolean = hasFocus() || canTakeFocus?.invoke() != false

    val hasPendingHorizontalNavigation: Boolean
        get() = isHoldingHorizontalKey ||
            pendingTargetPosition != RecyclerView.NO_POSITION ||
            scrollState != RecyclerView.SCROLL_STATE_IDLE

    val isSubmittingItems: Boolean
        get() = rowAdapter?.isSubmittingItems == true

    override fun clearChildFocus(child: View?) {
        // Focus leaves this row either because the focused card was removed mid-scroll
        // (protect it) or because a deliberate row change already pointed focus at another
        // row (let it go). canTakeFocus is updated synchronously by the key handlers, while
        // hasFocus()/scroll state are still stale during the handoff.
        val deliberateRowChange = canTakeFocus?.invoke() == false
        val shouldProtectFocus = isAttachedToWindow &&
            !deliberateRowChange &&
            (hasPendingHorizontalNavigation || isSubmittingItems)

        if (shouldProtectFocus) {
            val targetPos = if (pendingTargetPosition != RecyclerView.NO_POSITION) {
                pendingTargetPosition
            } else {
                selectedPosition.coerceAtLeast(0)
            }
            val targetVh = findViewHolderForAdapterPosition(targetPos)
            if (targetVh != null && targetVh.itemView !== child) {
                targetVh.itemView.requestFocus()
                return
            }
            if (!hasFocus() || findFocus() === child) {
                isFocusable = true
                isFocusableInTouchMode = true
                super.requestFocus()
            }
            return
        }
        super.clearChildFocus(child)
    }

    var isHoldingHorizontalKey: Boolean = false
        private set

    private var arrivalAt0Time = 0L
    private var arrivedAt0FromRight = false
    private var hasReleasedKeyAfterArrivingAt0 = true
    private var lastHorizontalRepeatDispatchMs = 0L
    private var lastVerticalRepeatDispatchMs = 0L
    private var pendingTargetPosition = RecyclerView.NO_POSITION

    private val rowAdapter: LeanbackRowAdapter?
        get() = adapter as? LeanbackRowAdapter

    fun notifyCardFocused(cardContainer: View, isLandscape: Boolean, dominantColor: Int) {
        floatingRingView?.moveToTarget(cardContainer, isLandscape, dominantColor, animationsEnabled)
    }

    /**
     * Called just before the key handler hands focus to another row. Clears the transient
     * horizontal state so closeChildFocus()/scroll callbacks from this row cannot pull focus
     * back while the target row is taking it, which made vertical navigation bounce.
     */
    fun prepareForRowChange() {
        isHoldingHorizontalKey = false
        pendingTargetPosition = RecyclerView.NO_POSITION
    }

    fun ensureChildFocusAndRing(targetPos: Int = -1, animated: Boolean = false, retryCount: Int = 0) {
        if (!isAttachedToWindow || !mayTakeFocus()) return
        val pos = if (targetPos >= 0) targetPos else {
            if (pendingTargetPosition != RecyclerView.NO_POSITION) pendingTargetPosition
            else selectedPosition.coerceAtLeast(0)
        }
        val vh = findViewHolderForAdapterPosition(pos) as? LeanbackCardViewHolder
        if (vh != null) {
            if (!vh.itemView.hasFocus()) {
                vh.itemView.requestFocus()
            }
            vh.updateFocusVisual(true)
            val isLandscape = rowAdapter?.let { it.cardWidthPx > it.cardHeightPx } ?: false
            floatingRingView?.moveToTarget(vh.cardContainer, isLandscape, vh.currentDominantColor, animated)
        } else {
            if (retryCount < 8) {
                post {
                    if (isAttachedToWindow && mayTakeFocus()) {
                        ensureChildFocusAndRing(targetPos, animated, retryCount + 1)
                    }
                }
            }
        }
    }

    fun ensureTargetChildFocused(targetPos: Int, direction: Int, retryCount: Int = 0) {
        if (!isAttachedToWindow || !mayTakeFocus()) return
        val vh = findViewHolderForAdapterPosition(targetPos) as? LeanbackCardViewHolder
        if (vh != null) {
            if (!vh.itemView.hasFocus()) {
                vh.itemView.requestFocus(direction)
            }
            vh.updateFocusVisual(true)
            val isLandscape = rowAdapter?.let { it.cardWidthPx > it.cardHeightPx } ?: false
            floatingRingView?.moveToTarget(vh.cardContainer, isLandscape, vh.currentDominantColor, animationsEnabled)
        } else {
            if (retryCount < 10) {
                post {
                    if (isAttachedToWindow && mayTakeFocus()) {
                        ensureTargetChildFocused(targetPos, direction, retryCount + 1)
                    }
                }
            }
        }
    }

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
                    pendingTargetPosition = RecyclerView.NO_POSITION
                    post {
                        if (!isComputingLayout) {
                            ensureChildFocusAndRing(animated = false)
                            rowAdapter?.onScrollSettled()
                            floatingRingView?.onScrollSettled()
                        }
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                floatingRingView?.onParentScrolled(dx)
            }
        })

        addOnChildViewHolderSelectedListener(object : OnChildViewHolderSelectedListener() {
            override fun onChildViewHolderSelected(
                parent: RecyclerView,
                child: RecyclerView.ViewHolder?,
                position: Int,
                subposition: Int
            ) {
                val isPendingTarget = (position == pendingTargetPosition) || (pendingTargetPosition != RecyclerView.NO_POSITION)
                if (position == pendingTargetPosition) {
                    pendingTargetPosition = RecyclerView.NO_POSITION
                }
                if (position == 0) {
                    arrivalAt0Time = SystemClock.uptimeMillis()
                } else {
                    arrivedAt0FromRight = true
                    hasReleasedKeyAfterArrivingAt0 = false
                }

                val hasGridFocus = (hasFocus() || isFocused || (parent as? View)?.hasFocus() == true ||
                    isHoldingHorizontalKey || isPendingTarget) && mayTakeFocus()
                if (child != null && hasGridFocus) {
                    if (!child.itemView.hasFocus()) {
                        child.itemView.requestFocus()
                    }
                    (child as? LeanbackCardViewHolder)?.let { vh ->
                        vh.updateFocusVisual(true)
                        val isLandscape = rowAdapter?.let { it.cardWidthPx > it.cardHeightPx } ?: false
                        floatingRingView?.moveToTarget(vh.cardContainer, isLandscape, vh.currentDominantColor, animationsEnabled)
                    }
                } else if (hasGridFocus) {
                    ensureChildFocusAndRing(targetPos = position, animated = animationsEnabled)
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
            post { if (mayTakeFocus()) requestFocus(direction, previouslyFocusedRect) }
            return false
        }
        val targetPos = if (pendingTargetPosition != RecyclerView.NO_POSITION) {
            pendingTargetPosition
        } else {
            selectedPosition.coerceAtLeast(0)
        }
        val vh = findViewHolderForAdapterPosition(targetPos) as? LeanbackCardViewHolder
        if (vh != null) {
            val granted = vh.itemView.requestFocus(direction, previouslyFocusedRect)
            if (granted) {
                vh.updateFocusVisual(true)
                val isLandscape = rowAdapter?.let { it.cardWidthPx > it.cardHeightPx } ?: false
                floatingRingView?.moveToTarget(vh.cardContainer, isLandscape, vh.currentDominantColor, false)
                return true
            }
        }
        val granted = super.requestFocus(direction, previouslyFocusedRect)
        if (granted) {
            ensureChildFocusAndRing(targetPos = targetPos, animated = false)
        }
        return granted
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        super.requestChildFocus(child, focused)
        val target = focused ?: child
        if (target != null && isDescendant(target)) {
            val holder = findContainingViewHolder(target) as? LeanbackCardViewHolder
            if (holder != null) {
                holder.updateFocusVisual(true)
                val isLandscape = rowAdapter?.let { it.cardWidthPx > it.cardHeightPx } ?: false
                floatingRingView?.moveToTarget(holder.cardContainer, isLandscape, holder.currentDominantColor, false)
            } else {
                ensureChildFocusAndRing(animated = false)
            }
        }
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (isBusyLayout) {
            return focused ?: findFocus() ?: this
        }
        val total = rowAdapter?.itemCount ?: 0
        val currentPos = selectedPosition
        if (direction == View.FOCUS_LEFT && currentPos <= 0) {
            return focused ?: findFocus() ?: this
        }
        if (direction == View.FOCUS_RIGHT && total > 0 && currentPos >= total - 1) {
            return focused ?: findFocus() ?: this
        }
        val result = super.focusSearch(focused, direction)
        // HorizontalGridView must NEVER let focus escape to another row via focusSearch!
        // Row-to-row navigation (UP/DOWN) is managed exclusively by onNavigateUp / onNavigateDown callbacks.
        if (result == null || result === this || !isDescendant(result)) {
            val fallback = focused?.takeIf { isDescendant(it) }
                ?: findFocus()?.takeIf { isDescendant(it) }
                ?: findViewHolderForAdapterPosition(currentPos)?.itemView
                ?: this
            return fallback
        }
        return result
    }

    private fun isDescendant(view: View): Boolean {
        var p = view.parent
        while (p != null) {
            if (p === this) return true
            p = p.parent
        }
        return false
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
        // HorizontalGridView handles its own internal horizontal scrolling via LayoutManager.
        // Returning true isolates vertical scroll management to the parent LazyColumn,
        // avoiding interrupting animateScrollToItem with conflicting bringIntoView requests.
        return true
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus && !hasFocus()) {
            isHoldingHorizontalKey = false
            pendingTargetPosition = RecyclerView.NO_POSITION
            floatingRingView?.hideRing(false)
        } else if (gainFocus || hasFocus()) {
            if (selectedPosition <= 0) {
                hasReleasedKeyAfterArrivingAt0 = true
                arrivedAt0FromRight = false
            }
            ensureChildFocusAndRing(animated = false)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    isHoldingHorizontalKey = false
                    val total = rowAdapter?.itemCount ?: 0
                    val currentSelected = selectedPosition.coerceIn(0, (total - 1).coerceAtLeast(0))
                    val target = if (pendingTargetPosition != RecyclerView.NO_POSITION) {
                        pendingTargetPosition.coerceIn(0, (total - 1).coerceAtLeast(0))
                    } else {
                        currentSelected
                    }
                    pendingTargetPosition = RecyclerView.NO_POSITION

                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && target <= 0) {
                        hasReleasedKeyAfterArrivingAt0 = true
                        arrivedAt0FromRight = false
                    }

                    // Tuyệt đối không gọi stopScroll() ngắt cụt gây lệch lửng poster!
                    // Luôn gọi setSelectedPositionSmooth để Leanback hoàn tất trôi và tự khớp chuẩn vào keyline
                    if (animationsEnabled) {
                        setSelectedPositionSmooth(target)
                    } else {
                        setSelectedPosition(target)
                    }

                    val vh = findViewHolderForAdapterPosition(target) as? LeanbackCardViewHolder
                    if (vh != null) {
                        if (!vh.itemView.hasFocus()) {
                            vh.itemView.requestFocus()
                        }
                    } else {
                        ensureChildFocusAndRing(animated = false)
                    }
                    return true
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = SystemClock.uptimeMillis()
            val total = rowAdapter?.itemCount ?: 0
            val focusedChild = findFocus()
            val holder = if (focusedChild != null) findContainingViewHolder(focusedChild) else null
            val resolvedHolderPos = holder?.bindingAdapterPosition?.takeIf { it != RecyclerView.NO_POSITION }
                ?: selectedPosition
            val currentPos = if (isHoldingHorizontalKey && pendingTargetPosition != RecyclerView.NO_POSITION) {
                pendingTargetPosition
            } else {
                resolvedHolderPos
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (currentPos <= 0 || total <= 0) {
                        pendingTargetPosition = 0
                        if (selectedPosition != 0) {
                            if (animationsEnabled) {
                                setSelectedPositionSmooth(0)
                            } else {
                                setSelectedPosition(0)
                            }
                        }
                        val arrivedRecently = arrivedAt0FromRight && (now - arrivalAt0Time < 300L)
                        if (!arrivedRecently && event.repeatCount == 0 && hasReleasedKeyAfterArrivingAt0) {
                            if (onNavigateLeftOnFirstItem?.invoke() == true) {
                                clearFocus()
                                return true
                            }
                        }
                        return true
                    }

                    if (event.repeatCount > 0) {
                        isHoldingHorizontalKey = true
                        if (now - lastHorizontalRepeatDispatchMs < 55L) {
                            return true
                        }
                        lastHorizontalRepeatDispatchMs = now
                    } else {
                        lastHorizontalRepeatDispatchMs = now
                    }

                    val target = (currentPos - 1).coerceAtLeast(0)
                    pendingTargetPosition = target
                    val nextVh = findViewHolderForAdapterPosition(target)
                    if (!animationsEnabled) {
                        setSelectedPosition(target)
                        if (nextVh != null) {
                            nextVh.itemView.requestFocus(View.FOCUS_LEFT)
                        } else {
                            ensureTargetChildFocused(target, View.FOCUS_LEFT)
                        }
                        return true
                    }

                    setSelectedPositionSmooth(target)
                    if (nextVh != null) {
                        nextVh.itemView.requestFocus(View.FOCUS_LEFT)
                    } else {
                        ensureTargetChildFocused(target, View.FOCUS_LEFT)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (total <= 0 || currentPos >= total - 1) {
                        val lastIdx = (total - 1).coerceAtLeast(0)
                        pendingTargetPosition = lastIdx
                        if (animationsEnabled) {
                            setSelectedPositionSmooth(lastIdx)
                        } else {
                            setSelectedPosition(lastIdx)
                        }
                        return true
                    }

                    if (event.repeatCount > 0) {
                        isHoldingHorizontalKey = true
                        if (now - lastHorizontalRepeatDispatchMs < 55L) {
                            return true
                        }
                        lastHorizontalRepeatDispatchMs = now
                    } else {
                        lastHorizontalRepeatDispatchMs = now
                    }

                    val target = (currentPos + 1).coerceAtMost((total - 1).coerceAtLeast(0))
                    pendingTargetPosition = target
                    val nextVh = findViewHolderForAdapterPosition(target)
                    if (!animationsEnabled) {
                        setSelectedPosition(target)
                        if (nextVh != null) {
                            nextVh.itemView.requestFocus(View.FOCUS_RIGHT)
                        } else {
                            ensureTargetChildFocused(target, View.FOCUS_RIGHT)
                        }
                        return true
                    }

                    setSelectedPositionSmooth(target)
                    if (nextVh != null) {
                        nextVh.itemView.requestFocus(View.FOCUS_RIGHT)
                    } else {
                        ensureTargetChildFocused(target, View.FOCUS_RIGHT)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    isHoldingHorizontalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
                    if (event.repeatCount > 0) {
                        if (now - lastVerticalRepeatDispatchMs < 115L) {
                            return true
                        }
                        lastVerticalRepeatDispatchMs = now
                    } else {
                        lastVerticalRepeatDispatchMs = now
                    }
                    if (onNavigateDown?.invoke() == true) return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    isHoldingHorizontalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
                    if (event.repeatCount > 0) {
                        if (now - lastVerticalRepeatDispatchMs < 115L) {
                            return true
                        }
                        lastVerticalRepeatDispatchMs = now
                    } else {
                        lastVerticalRepeatDispatchMs = now
                    }
                    if (onNavigateUp?.invoke() == true) return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    isHoldingHorizontalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
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

    private val placeholderBackgroundDrawable = GradientDrawable().apply {
        setColor(Color.parseColor("#1A1A1A"))
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

        placeholderBackgroundDrawable.cornerRadius = newRadiusPx
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
        root.animate().cancel()
        root.scaleX = 1.0f
        root.scaleY = 1.0f

        val isLandscape = adapter.cardWidthPx > adapter.cardHeightPx

        root.translationZ = if (hasFocus) 12f else 0f
        cardContainer.elevation = 0f
        cardContainer.translationZ = 0f
        cardContainer.foreground = null
        glowView.visibility = View.GONE

        val gridView = adapter.attachedRecyclerView as? InteropHorizontalGridView
        if (hasFocus) {
            gridView?.notifyCardFocused(cardContainer, isLandscape, currentDominantColor)
        }
    }

    fun bind(
        item: ModernCarouselItem,
        cardWidthPx: Int,
        cardHeightPx: Int,
        cornerRadiusPx: Float,
        showLabels: Boolean
    ) {
        val imageUrl = item.imageUrl ?: item.heroPreview.poster ?: item.heroPreview.backdrop
        val colorCacheKey = imageUrl ?: item.key
        val prevImageUrl = currentItem?.let { it.imageUrl ?: it.heroPreview.poster ?: it.heroPreview.backdrop }
        if (currentItem?.key != item.key || prevImageUrl != imageUrl) {
            posterImage.setImageDrawable(null)
        }
        currentItem = item
        updateCornerRadius(cornerRadiusPx)
        val cachedColor = LeanbackColorEngine.getCachedColor(colorCacheKey) ?: dominantColorCache.get(colorCacheKey)
        currentDominantColor = cachedColor ?: LeanbackColorEngine.defaultFallbackColor

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
        if (showLabels && titleText != null && item.title.isNotBlank()) {
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
        if (!imageUrl.isNullOrBlank()) {
            val ctx = posterImage.context
            val hasCachedColor = cachedColor != null
            val boundKey = item.key
            val boundColorKey = colorCacheKey
            val request = ImageRequest.Builder(ctx)
                .data(imageUrl)
                .size(cardWidthPx.coerceAtLeast(1), cardHeightPx.coerceAtLeast(1))
                .allowHardware(hasCachedColor)
                .crossfade(false)
                .target(
                    object : Target {
                        override fun onStart(placeholder: Image?) {
                            if (currentItem?.key != boundKey) return
                            if (placeholder != null) {
                                posterImage.setImageDrawable(placeholder.asDrawable(ctx.resources))
                            }
                        }
                        override fun onSuccess(result: Image) {
                            if (currentItem?.key != boundKey) return
                            posterImage.setImageDrawable(result.asDrawable(ctx.resources))
                            // Triệt tiêu 100% GPU Overdraw khi ảnh đã nạp xong kín thẻ
                            cardContainer.background = null
                            val existingColor = LeanbackColorEngine.getCachedColor(boundColorKey) ?: dominantColorCache.get(boundColorKey)
                            if (existingColor != null) {
                                currentDominantColor = existingColor
                                if (root.hasFocus()) {
                                    updateFocusVisual(true)
                                }
                            } else {
                                val bmp = (result as? BitmapImage)?.bitmap
                                if (bmp != null && !bmp.isRecycled && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || bmp.config != Bitmap.Config.HARDWARE)) {
                                    LeanbackColorEngine.extractDominantColorAsync(boundColorKey, bmp) { extracted ->
                                        dominantColorCache.put(boundColorKey, extracted)
                                        if (currentItem?.key == boundKey) {
                                            currentDominantColor = extracted
                                            if (root.hasFocus()) {
                                                updateFocusVisual(true)
                                            }
                                        }
                                    }
                                } else if (root.hasFocus()) {
                                    updateFocusVisual(true)
                                }
                            }
                        }
                        override fun onError(error: Image?) {
                            if (currentItem?.key != boundKey) return
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
        val gridView = adapter.attachedRecyclerView as? InteropHorizontalGridView
        val isRowFocused = gridView?.hasFocus() == true
        val targetPos = gridView?.selectedPosition?.coerceAtLeast(0) ?: 0
        val isCurrentSelectedPos = isRowFocused && (bindingAdapterPosition == targetPos || (bindingAdapterPosition == RecyclerView.NO_POSITION && targetPos == 0))

        if (root.hasFocus() || isCurrentSelectedPos) {
            if (!root.hasFocus()) {
                root.requestFocus()
            }
            updateFocusVisual(true)
        } else {
            updateFocusVisual(false)
        }
    }

    fun unbind() {
        root.removeCallbacks(deferredGlowRunnable)
        root.animate().cancel()
        root.scaleX = 1.0f
        root.scaleY = 1.0f
        imageDisposable?.dispose()
        imageDisposable = null
        glowView.visibility = View.GONE
        cardContainer.foreground = null
        // Phục hồi lại background placeholder tái sử dụng khi view được recycle
        placeholderBackgroundDrawable.cornerRadius = cornerRadiusPx
        if (cardContainer.background !== placeholderBackgroundDrawable) {
            cardContainer.background = placeholderBackgroundDrawable
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
    var isSubmittingItems: Boolean = false
        private set

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
        val rv = attachedRecyclerView
        if (rv != null) {
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i)
                val vh = rv.getChildViewHolder(child) as? LeanbackCardViewHolder
                if (vh != null && vh.itemView.hasFocus()) {
                    vh.updateFocusVisual(true)
                }
            }
        }
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
        val rv = attachedRecyclerView as? InteropHorizontalGridView
        val hadFocus = rv?.hasFocus() == true
        val prevSelectedPos = rv?.selectedPosition?.coerceAtLeast(0) ?: 0

        try {
            isSubmittingItems = true
            if (oldSize > 0 && newSize > oldSize && oldItems == newItems.take(oldSize)) {
                notifyItemRangeInserted(oldSize, newSize - oldSize)
            } else if (oldSize > 0 && newSize > 0 && oldSize <= 300 && newSize <= 300) {
                try {
                    val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                        override fun getOldListSize(): Int = oldSize
                        override fun getNewListSize(): Int = newSize
                        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                            return oldItems[oldItemPosition].key == newItems[newItemPosition].key
                        }
                        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                            return oldItems[oldItemPosition] == newItems[newItemPosition]
                        }
                    }, false)
                    diffResult.dispatchUpdatesTo(this)
                } catch (_: Exception) {
                    notifyDataSetChanged()
                }
            } else {
                notifyDataSetChanged()
            }
        } finally {
            isSubmittingItems = false
        }

        // Placeholder-to-Data Focus Guard:
        // Nếu hàng này đang nắm giữ tiêu điểm khi dữ liệu thay đổi (chuyển từ thẻ placeholder sang thẻ thật),
        // lập tức neo tiêu điểm và viền sáng vào thẻ mới tại vị trí tương ứng, ngăn chặn 100% việc rò rỉ focus!
        if (hadFocus && rv != null && newItems.isNotEmpty()) {
            val safePos = prevSelectedPos.coerceIn(0, newItems.lastIndex)
            rv.post {
                // If a card still owns focus, the update did not disturb it. Re-anchoring here
                // would undo a horizontal move the user made after the data was submitted.
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

        // Always create titleText so ViewHolders shared across pools between
        // showLabels=false and showLabels=true rows never lose their title view.
        val titleText = TextView(context).apply {
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
            visibility = if (showLabels) View.VISIBLE else View.GONE
        }.also { root.addView(it) }

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
    canTakeFocus: (() -> Boolean)? = null,
    recycledViewPool: RecyclerView.RecycledViewPool? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cardWidthPx = with(density) { cardWidth.roundToPx() }
    val cardHeightPx = with(density) { cardHeight.roundToPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val startPaddingPx = with(density) { startPadding.roundToPx() }
    val spacingPx = with(density) { spacing.roundToPx() }
    val verticalPaddingDp = 6.dp
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
            // Skip if the user already navigated to a different row in the meantime.
            if (!grid.isAttachedToWindow || !grid.mayTakeFocus()) return@post
            grid.setSelectedPosition(targetIdx)
            grid.requestFocus()
            grid.ensureChildFocusAndRing(targetPos = targetIdx, animated = false)
        }
    }

    var localGridViewRef by remember(rowKey) { mutableStateOf<InteropHorizontalGridView?>(null) }

    LaunchedEffect(isPendingFocus, pendingFocusNonce, items.isNotEmpty()) {
        if (isPendingFocus && items.isNotEmpty()) {
            val targetIdx = pendingFocusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
            val gridView = localGridViewRef ?: (rowGrids?.get(rowKey) as? InteropHorizontalGridView)
            if (gridView != null) {
                if (gridView.isAttachedToWindow) {
                    executeRowFocus(gridView, targetIdx)
                    onClearPendingFocus()
                } else {
                    val attachListener = object : View.OnAttachStateChangeListener {
                        override fun onViewAttachedToWindow(v: View) {
                            v.removeOnAttachStateChangeListener(this)
                            executeRowFocus(gridView, targetIdx)
                            onClearPendingFocus()
                        }
                        override fun onViewDetachedFromWindow(v: View) {}
                    }
                    gridView.addOnAttachStateChangeListener(attachListener)
                    // Remove the listener if this effect is cancelled (row recycled / pending cleared).
                    try {
                        awaitCancellation()
                    } finally {
                        gridView.removeOnAttachStateChangeListener(attachListener)
                    }
                }
            }
        }
    }

    AndroidView(
        modifier = modifier
            .height(totalRowHeightDp)
            .fillMaxWidth()
            .graphicsLayer { clip = false },
        factory = { context ->
            val container = ModernRowContainerLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    totalRowHeightPx
                )
                clipChildren = false
                clipToPadding = false
            }
            val gridView = InteropHorizontalGridView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    totalRowHeightPx
                )
                setRowHeight(totalRowHeightPx)
                setNumRows(1)
                focusScrollStrategy = BaseGridView.FOCUS_SCROLL_ITEM
                windowAlignment = BaseGridView.WINDOW_ALIGN_BOTH_EDGE
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
                this.canTakeFocus = canTakeFocus
                this.animationsEnabled = animationsEnabled
                setHasFixedSize(true)
                setItemViewCacheSize(8)
                recycledViewPool?.let { setRecycledViewPool(it) }
                localGridViewRef = this
                rowGrids?.put(rowKey, this)
            }
            val focusRing = ModernRowFloatingFocusRingView(context).apply {
                this.cornerRadiusPx = cornerRadiusPx
                this.elevation = 30f
                val spread = this.glowSpreadPx
                val margin = (spread * 1.35f).toInt()
                this.layoutParams = FrameLayout.LayoutParams(
                    cardWidthPx + (margin * 2),
                    cardHeightPx + (margin * 2)
                )
            }
            gridView.floatingRingView = focusRing
            container.gridView = gridView
            container.floatingRingView = focusRing
            container.addView(gridView)
            container.addView(focusRing)

            if (isPendingFocus && items.isNotEmpty()) {
                val targetIdx = pendingFocusIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
                executeRowFocus(gridView, targetIdx)
                onClearPendingFocus()
            }
            container
        },
        update = { container ->
            val gridView = container.gridView
            val focusRing = container.floatingRingView
            val lp = container.layoutParams
            if (lp != null && lp.height != totalRowHeightPx) {
                lp.height = totalRowHeightPx
                container.layoutParams = lp
            }
            val gridLp = gridView.layoutParams
            if (gridLp != null && gridLp.height != totalRowHeightPx) {
                gridLp.height = totalRowHeightPx
                gridView.layoutParams = gridLp
            }
            focusRing.cornerRadiusPx = cornerRadiusPx
            gridView.setRowHeight(totalRowHeightPx)
            gridView.focusScrollStrategy = BaseGridView.FOCUS_SCROLL_ITEM
            gridView.windowAlignment = BaseGridView.WINDOW_ALIGN_BOTH_EDGE
            gridView.windowAlignmentOffset = startPaddingPx
            gridView.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
            gridView.itemAlignmentOffset = 0
            gridView.itemAlignmentOffsetPercent = 0f
            gridView.setItemSpacing(spacingPx)
            if (gridView.paddingLeft != startPaddingPx ||
                gridView.paddingTop != verticalPaddingPx ||
                gridView.paddingRight != startPaddingPx ||
                gridView.paddingBottom != verticalPaddingPx
            ) {
                gridView.setPadding(startPaddingPx, verticalPaddingPx, startPaddingPx, verticalPaddingPx)
            }
            localGridViewRef = gridView
            gridView.onNavigateDown = onNavigateDown
            gridView.onNavigateUp = onNavigateUp
            gridView.onNavigateBack = onNavigateBack
            gridView.onNavigateLeftOnFirstItem = onNavigateLeftOnFirstItem
            gridView.canTakeFocus = canTakeFocus
            gridView.animationsEnabled = animationsEnabled
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
        }
    )
}
