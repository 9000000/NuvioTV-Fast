package com.nuvio.tv.ui.screens.home

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

internal val DEFAULT_MODERN_FOCUS_COLOR: Int = Color.parseColor("#F59E0B")

/**
 * Single floating focus ring per Modern Home row.
 * Stays anchored at the leading keyline (left in LTR, right in RTL) while poster cards scroll
 * underneath it. Allocates a `LAYER_TYPE_SOFTWARE` buffer ONLY while `visibility == VISIBLE`.
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

    var strokeColor: Int = DEFAULT_MODERN_FOCUS_COLOR
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
                syncSoftwareLayer()
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
    private val tempRect = Rect()
    private val tempParentLoc = IntArray(2)
    private val tempTargetLoc = IntArray(2)

    val glowSpreadPx: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 16f, context.resources.displayMetrics
    )
    val glowMarginPx: Float = glowSpreadPx * 1.35f

    var currentTargetView: View? = null
        private set

    init {
        val filters = obtainRingFilters(glowSpreadPx)
        midPaint.maskFilter = filters.first
        auraPaint.maskFilter = filters.second
        midPaint.strokeWidth = (glowSpreadPx * 0.40f).coerceAtLeast(2f)
        auraPaint.strokeWidth = (glowSpreadPx * 0.75f).coerceAtLeast(4f)
        updateGlowPaints()
        visibility = INVISIBLE
        isFocusable = false
        isFocusableInTouchMode = false
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        syncSoftwareLayer()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncSoftwareLayer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (layerType != LAYER_TYPE_NONE) {
            setLayerType(LAYER_TYPE_NONE, null)
        }
    }

    private fun syncSoftwareLayer() {
        val needsSoftware = isAttachedToWindow && visibility == VISIBLE && isGlowEnabled
        val desiredLayer = if (needsSoftware) LAYER_TYPE_SOFTWARE else LAYER_TYPE_NONE
        if (layerType != desiredLayer) {
            setLayerType(desiredLayer, null)
        }
    }

    private fun updateGlowPaints() {
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

    private fun computeAnchoredRingX(parent: ViewGroup, cardWidthPx: Int): Float {
        val grid = (parent as? ModernRowContainerLayout)?.gridView
        val isRtl = (grid?.layoutDirection ?: parent.layoutDirection) == LAYOUT_DIRECTION_RTL
        return if (isRtl && parent.width > 0) {
            val startInset = (grid?.paddingStart ?: 0).toFloat()
            (parent.width - startInset - cardWidthPx).coerceAtLeast(0f) - glowMarginPx
        } else {
            val startInset = (grid?.paddingStart ?: grid?.paddingLeft ?: 0).toFloat()
            startInset - glowMarginPx
        }
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

        tempRect.set(0, 0, target.width, target.height)
        val success = try {
            parent.offsetDescendantRectToMyCoords(target, tempRect)
            true
        } catch (_: Exception) {
            false
        }

        val anchoredX = computeAnchoredRingX(parent, target.width)
        val targetY = if (success) {
            tempRect.top.toFloat() - glowMarginPx
        } else {
            parent.getLocationInWindow(tempParentLoc)
            target.getLocationInWindow(tempTargetLoc)
            (tempTargetLoc[1] - tempParentLoc[1]).toFloat() - glowMarginPx
        }
        val targetWidth = target.width + (glowMarginPx * 2).toInt()
        val targetHeight = target.height + (glowMarginPx * 2).toInt()

        val resolvedGlowColor = resolveGlowColor(dominantColor)

        strokeColor = resolvedGlowColor
        glowColor = resolvedGlowColor
        isGlowEnabled = true

        val lp = layoutParams
        if (lp != null && (lp.width != targetWidth || lp.height != targetHeight)) {
            lp.width = targetWidth
            lp.height = targetHeight
            layoutParams = lp
        }

        translationX = anchoredX
        translationY = targetY

        if (!animated) {
            alpha = 1f
            visibility = VISIBLE
        } else if (visibility != VISIBLE || alpha <= 0f) {
            alpha = 0f
            visibility = VISIBLE
            animate().alpha(1f).setDuration(120).start()
        } else {
            alpha = 1f
            visibility = VISIBLE
        }
    }

    fun onParentScrolled(dx: Int) {
        if (visibility != VISIBLE) return
        val parent = parent as? ViewGroup ?: return
        val cardW = (width - (glowMarginPx * 2).toInt()).coerceAtLeast(0)
        translationX = computeAnchoredRingX(parent, cardW)
    }

    fun onScrollSettled() {
        if (visibility != VISIBLE) return
        val parent = parent as? ViewGroup ?: return
        val cardW = (width - (glowMarginPx * 2).toInt()).coerceAtLeast(0)
        translationX = computeAnchoredRingX(parent, cardW)
    }

    fun showFixedOverlayAt(
        xPx: Float,
        yPx: Float,
        cardWidthPx: Int,
        cardHeightPx: Int,
        cornerRadiusPx: Float,
        dominantColor: Int
    ) {
        animate().cancel()
        currentTargetView = null
        val targetWidth = cardWidthPx + (glowMarginPx * 2).toInt()
        val targetHeight = cardHeightPx + (glowMarginPx * 2).toInt()
        val lp = layoutParams
        if (lp != null && (lp.width != targetWidth || lp.height != targetHeight)) {
            lp.width = targetWidth
            lp.height = targetHeight
            layoutParams = lp
        }
        this.cornerRadiusPx = cornerRadiusPx
        val resolvedGlowColor = resolveGlowColor(dominantColor)
        strokeColor = resolvedGlowColor
        glowColor = resolvedGlowColor
        isGlowEnabled = true
        translationX = xPx - glowMarginPx
        translationY = yPx - glowMarginPx
        if (visibility != VISIBLE || alpha < 1f) {
            alpha = 1f
            visibility = VISIBLE
        }
    }

    fun hideRing(immediate: Boolean = false) {
        animate().cancel()
        currentTargetView = null
        if (immediate) {
            visibility = INVISIBLE
            alpha = 0f
        } else if (visibility == VISIBLE) {
            animate().alpha(0f).setDuration(100).withEndAction {
                visibility = INVISIBLE
            }.start()
        }
    }

    private fun resolveGlowColor(dominantColor: Int): Int {
        return if (dominantColor != 0 && dominantColor != Color.WHITE && dominantColor != Color.BLACK) {
            dominantColor
        } else {
            DEFAULT_MODERN_FOCUS_COLOR
        }
    }

    companion object {
        private val ringFilterCache = LruCache<Int, Pair<BlurMaskFilter, BlurMaskFilter>>(8)

        private fun obtainRingFilters(spreadPx: Float): Pair<BlurMaskFilter, BlurMaskFilter> {
            val key = (spreadPx * 10f).roundToInt().coerceAtLeast(1)
            synchronized(ringFilterCache) {
                ringFilterCache.get(key)?.let { return it }
            }
            val pair = Pair(
                BlurMaskFilter((spreadPx * 0.35f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL),
                BlurMaskFilter((spreadPx * 0.65f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            )
            synchronized(ringFilterCache) {
                ringFilterCache.put(key, pair)
            }
            return pair
        }
    }
}

/**
 * Host container wrapping [ModernInteropHorizontalGridView] and [ModernRowFloatingFocusRingView].
 */
class ModernRowContainerLayout(context: Context) : FrameLayout(context) {
    lateinit var gridView: ModernInteropHorizontalGridView
    lateinit var floatingRingView: ModernRowFloatingFocusRingView
    @Volatile
    var isDetaching: Boolean = false

    override fun clearFocus() {
        super.clearFocus()
        if (::floatingRingView.isInitialized && !hasFocus()) {
            floatingRingView.hideRing(true)
        }
    }

    override fun clearChildFocus(child: View?) {
        if (!isDetaching &&
            ::gridView.isInitialized &&
            gridView.isVerticalGlideActive?.invoke() != true &&
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
        // which cancels smooth vertical animateScrollToItem animations when a row card receives focus.
        return true
    }
}
