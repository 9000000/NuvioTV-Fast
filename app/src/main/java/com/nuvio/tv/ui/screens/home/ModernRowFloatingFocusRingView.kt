package com.nuvio.tv.ui.screens.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.nuvio.tv.ui.leanback.core.LeanbackBlurFilterCache
import com.nuvio.tv.ui.leanback.core.LeanbackCardExpandSpec
import com.nuvio.tv.ui.leanback.core.LeanbackThemeStyle

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

    var borderWidthPx: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 2.5f, context.resources.displayMetrics
    )
        private set

    var themeStyle: LeanbackThemeStyle = LeanbackThemeStyle.Default
        set(value) {
            if (field != value) {
                field = value
                borderWidthPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    value.borderWidthDp,
                    context.resources.displayMetrics
                )
                borderPaint.strokeWidth = borderWidthPx
                applyThemeColors(lastDominantColor)
                syncSoftwareLayer()
                invalidate()
            }
        }

    private var lastDominantColor: Int = DEFAULT_MODERN_FOCUS_COLOR

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
                applyThemeColors(lastDominantColor)
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
    private var cachedRainbowShader: LinearGradient? = null
    private var cachedShaderWidth: Int = -1
    private var cachedShaderHeight: Int = -1
    private var cachedShaderColors: List<Int> = emptyList()

    val glowSpreadPx: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 16f, context.resources.displayMetrics
    )
    val glowMarginPx: Float = glowSpreadPx * 1.35f

    var currentTargetView: View? = null
        private set
    var drawnCardWidthPx: Int = 0
        private set
    var drawnCardHeightPx: Int = 0
        private set
    private var observedTargetView: View? = null
    private var desiredViewWidthPx: Int = 0
    private var desiredViewHeightPx: Int = 0
    private var pendingBufferLayoutPosted: Boolean = false
    private var isAnimatingTargetSize: Boolean = false
    private var isTrackingExpandedTarget: Boolean = false
    private var ringSizeAnimator: ValueAnimator? = null
    private var morphTargetWidthPx: Int = -1
    private var morphTargetHeightPx: Int = -1

    val isMorphingSize: Boolean
        get() = ringSizeAnimator?.isRunning == true

    private val targetLayoutChangeListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        if (view !== currentTargetView || !isAttachedToWindow || visibility != VISIBLE) return@OnLayoutChangeListener
        if (isMorphingSize) return@OnLayoutChangeListener
        val parentView = parent as? ViewGroup ?: return@OnLayoutChangeListener
        if (!view.isAttachedToWindow || view.width <= 0 || view.height <= 0) return@OnLayoutChangeListener
        val grid = (parentView as? ModernRowContainerLayout)?.gridView
        val isRowAnimatingWidth = (grid?.adapter as? LeanbackRowAdapter)?.hasAnimatingWidthCard == true
        val isGridIdle = grid == null ||
            (!grid.isHoldingHorizontalKey && grid.scrollState == RecyclerView.SCROLL_STATE_IDLE)
        if (!isAnimatingTargetSize) {
            val resolvedW = LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
                explicitPx = null,
                layoutParamPx = view.layoutParams?.width,
                measuredPx = view.width,
                isLayoutRequested = view.isLayoutRequested,
                fallbackPx = drawnCardWidthPx
            )
            val resolvedH = LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
                explicitPx = null,
                layoutParamPx = view.layoutParams?.height,
                measuredPx = view.height,
                isLayoutRequested = view.isLayoutRequested,
                fallbackPx = drawnCardHeightPx
            )
            if (resolvedW > 0 && resolvedH > 0 && (drawnCardWidthPx != resolvedW || drawnCardHeightPx != resolvedH)) {
                updateDrawnDimensions(
                    cardWidthPx = resolvedW,
                    cardHeightPx = resolvedH,
                    bufferCardWidthPx = resolvedW,
                    allowShrink = true
                )
            }
        }
        val activeCardW = drawnCardWidthPx.takeIf { it > 0 } ?: view.width
        val useLiveX = isTrackingExpandedTarget || isAnimatingTargetSize || isRowAnimatingWidth || isGridIdle
        val (targetX, targetY) = resolveTargetPositionInParent(
            parent = parentView,
            target = view,
            cardWidthPx = activeCardW,
            useLiveTargetX = useLiveX
        )
        if (kotlin.math.abs(translationX - targetX) >= 0.5f) {
            translationX = targetX
        }
        if (kotlin.math.abs(translationY - targetY) >= 0.5f) {
            translationY = targetY
        }
    }

    init {
        midPaint.maskFilter = LeanbackBlurFilterCache.get((glowSpreadPx * 0.35f).coerceAtLeast(1f))
        auraPaint.maskFilter = LeanbackBlurFilterCache.get((glowSpreadPx * 0.65f).coerceAtLeast(1f))
        midPaint.strokeWidth = (glowSpreadPx * 0.40f).coerceAtLeast(2f)
        auraPaint.strokeWidth = (glowSpreadPx * 0.75f).coerceAtLeast(4f)
        updateGlowPaints()
        visibility = INVISIBLE
        isFocusable = false
        isFocusableInTouchMode = false
    }

    private fun attachTargetObserver(target: View?) {
        if (observedTargetView === target) return
        observedTargetView?.removeOnLayoutChangeListener(targetLayoutChangeListener)
        observedTargetView = target
        target?.addOnLayoutChangeListener(targetLayoutChangeListener)
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
        cancelRingSizeMorph()
        attachTargetObserver(null)
        pendingBufferLayoutPosted = false
        if (layerType != LAYER_TYPE_NONE) {
            setLayerType(LAYER_TYPE_NONE, null)
        }
    }

    private fun cancelRingSizeMorph() {
        ringSizeAnimator?.cancel()
        ringSizeAnimator = null
        morphTargetWidthPx = -1
        morphTargetHeightPx = -1
    }

    /**
     * Smoothly morphs the floating focus ring's drawn width back to [targetCardWidthPx] at the
     * anchored keyline while the row scrolls to the next poster. Zero `requestLayout()` calls
     * occur during the tween, and the backing buffer shrinks cleanly once the morph completes.
     */
    fun animateMorphToCollapsedCardSize(
        targetCardWidthPx: Int,
        targetCardHeightPx: Int,
        dominantColor: Int? = null,
        durationMs: Long = LeanbackCardExpandSpec.RING_COLLAPSE_MORPH_DURATION_MS
    ) {
        val safeTargetW = targetCardWidthPx.coerceAtLeast(1)
        val safeTargetH = targetCardHeightPx.coerceAtLeast(1)
        if (dominantColor != null) {
            applyThemeColors(dominantColor)
        }
        val parentView = parent as? ViewGroup ?: return
        val startW = drawnCardWidthPx.takeIf { it > 0 }
            ?: (width - (glowMarginPx * 2).toInt()).coerceAtLeast(safeTargetW)

        isTrackingExpandedTarget = false
        if (visibility != VISIBLE || !isAttachedToWindow || startW == safeTargetW || durationMs <= 0L) {
            cancelRingSizeMorph()
            if (isAnimatingTargetSize) {
                isAnimatingTargetSize = false
                syncSoftwareLayer()
            }
            updateDrawnDimensions(
                cardWidthPx = safeTargetW,
                cardHeightPx = safeTargetH,
                bufferCardWidthPx = safeTargetW,
                allowShrink = true
            )
            return
        }

        if (isMorphingSize && morphTargetWidthPx == safeTargetW && morphTargetHeightPx == safeTargetH) {
            return
        }

        cancelRingSizeMorph()
        isAnimatingTargetSize = true
        syncSoftwareLayer()

        val bufferW = maxOf(startW, safeTargetW)
        val animator = ValueAnimator.ofInt(startW, safeTargetW).apply {
            duration = durationMs
            interpolator = LeanbackCardExpandSpec.createExpandInterpolator()
            addUpdateListener { anim ->
                val animatedW = anim.animatedValue as Int
                updateDrawnDimensions(
                    cardWidthPx = animatedW,
                    cardHeightPx = safeTargetH,
                    bufferCardWidthPx = bufferW,
                    allowShrink = false
                )
                val grid = (parentView as? ModernRowContainerLayout)?.gridView
                val isIdleAtEdge = grid != null &&
                    !grid.isHoldingHorizontalKey &&
                    grid.scrollState == RecyclerView.SCROLL_STATE_IDLE
                val target = currentTargetView
                val anchoredX = if (isIdleAtEdge && target != null && target.isAttachedToWindow && target.width > 0) {
                    resolveTargetPositionInParent(
                        parent = parentView,
                        target = target,
                        cardWidthPx = animatedW,
                        useLiveTargetX = true
                    ).first
                } else {
                    computeAnchoredRingX(parentView, animatedW)
                }
                if (kotlin.math.abs(translationX - anchoredX) >= 0.5f) {
                    translationX = anchoredX
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var isCancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    isCancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (ringSizeAnimator === animation) {
                        ringSizeAnimator = null
                        morphTargetWidthPx = -1
                        morphTargetHeightPx = -1
                    }
                    if (isCancelled) return
                    isAnimatingTargetSize = false
                    syncSoftwareLayer()
                    updateDrawnDimensions(
                        cardWidthPx = safeTargetW,
                        cardHeightPx = safeTargetH,
                        bufferCardWidthPx = safeTargetW,
                        allowShrink = true
                    )
                    val grid = (parentView as? ModernRowContainerLayout)?.gridView
                    if (grid == null || grid.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                        onScrollSettled()
                    }
                }
            })
        }
        ringSizeAnimator = animator
        morphTargetWidthPx = safeTargetW
        morphTargetHeightPx = safeTargetH
        animator.start()
    }

    private fun syncSoftwareLayer() {
        val needsSoftware = isAttachedToWindow &&
            visibility == VISIBLE &&
            isGlowEnabled &&
            !isAnimatingTargetSize &&
            !themeStyle.hideFocusBorder
        val desiredLayer = if (needsSoftware) LAYER_TYPE_SOFTWARE else LAYER_TYPE_NONE
        if (layerType != desiredLayer) {
            setLayerType(desiredLayer, null)
        }
    }

    private fun applyThemeColors(dominantColor: Int) {
        lastDominantColor = dominantColor
        val resolvedGlowColor = themeStyle.resolveGlowColor(
            dominantColor = dominantColor,
            fallbackColor = DEFAULT_MODERN_FOCUS_COLOR
        )
        strokeColor = themeStyle.resolveFocusedBorderColor(
            dominantColor = resolvedGlowColor,
            blendWithDominant = isGlowEnabled
        )
        glowColor = resolvedGlowColor
        syncRainbowShader()
    }

    private fun syncRainbowShader(drawWidthPx: Float = drawnCardWidthPx.toFloat(), drawHeightPx: Float = drawnCardHeightPx.toFloat()) {
        if (!themeStyle.isRainbowBorder) {
            borderPaint.shader = null
            cachedRainbowShader = null
            return
        }
        val colors = themeStyle.focusGradientColors
        val effectiveW = if (drawWidthPx > 0f) drawWidthPx else (width - glowMarginPx * 2f)
        val effectiveH = if (drawHeightPx > 0f) drawHeightPx else (height - glowMarginPx * 2f)
        val roundedW = effectiveW.toInt()
        val roundedH = effectiveH.toInt()
        if (colors.size <= 1 || roundedW <= 0 || roundedH <= 0) {
            borderPaint.shader = null
            return
        }
        if (cachedRainbowShader != null &&
            cachedShaderWidth == roundedW &&
            cachedShaderHeight == roundedH &&
            cachedShaderColors == colors
        ) {
            borderPaint.shader = cachedRainbowShader
            return
        }
        val pad = glowMarginPx
        val shader = LinearGradient(
            pad,
            pad,
            (pad + effectiveW).coerceAtLeast(pad + 1f),
            (pad + effectiveH).coerceAtLeast(pad + 1f),
            colors.toIntArray(),
            null,
            Shader.TileMode.CLAMP
        )
        cachedRainbowShader = shader
        cachedShaderWidth = roundedW
        cachedShaderHeight = roundedH
        cachedShaderColors = colors
        borderPaint.shader = shader
    }

    private fun updateGlowPaints() {
        midPaint.color = ColorUtils.setAlphaComponent(glowColor, 185)
        auraPaint.color = ColorUtils.setAlphaComponent(glowColor, 85)
    }

    private fun applyBufferLayoutParamsIfNeeded() {
        val lp = layoutParams ?: return
        val targetW = desiredViewWidthPx
        val targetH = desiredViewHeightPx
        if (targetW <= 0 || targetH <= 0) return
        val needsUpdate = lp.width != targetW ||
            lp.height != targetH ||
            width != targetW ||
            height != targetH ||
            measuredWidth != targetW ||
            measuredHeight != targetH
        if (!needsUpdate) return

        val parentInLayout = (parent as? View)?.isInLayout == true
        if (isInLayout || parentInLayout) {
            if (!pendingBufferLayoutPosted) {
                pendingBufferLayoutPosted = true
                post {
                    pendingBufferLayoutPosted = false
                    if (isAttachedToWindow) {
                        applyBufferLayoutParamsIfNeeded()
                    }
                }
            }
            return
        }

        lp.width = targetW
        lp.height = targetH
        layoutParams = lp
        requestLayout()
    }

    /**
     * Updates the exact drawn card dimensions immediately via [invalidate], decoupling ring visuals
     * from delayed/dropped Compose AndroidView layout passes while keeping the backing view buffer
     * sized cleanly for software glow rendering.
     */
    private fun updateDrawnDimensions(
        cardWidthPx: Int,
        cardHeightPx: Int,
        bufferCardWidthPx: Int = cardWidthPx,
        allowShrink: Boolean
    ) {
        val safeW = cardWidthPx.coerceAtLeast(1)
        val safeH = cardHeightPx.coerceAtLeast(1)
        val dimsChanged = drawnCardWidthPx != safeW || drawnCardHeightPx != safeH
        drawnCardWidthPx = safeW
        drawnCardHeightPx = safeH

        val margin = (glowMarginPx * 2).toInt()
        val minViewW = maxOf(safeW, bufferCardWidthPx.coerceAtLeast(safeW)) + margin
        val minViewH = safeH + margin
        if (allowShrink) {
            desiredViewWidthPx = minViewW
            desiredViewHeightPx = minViewH
        } else {
            desiredViewWidthPx = maxOf(desiredViewWidthPx, width, minViewW)
            desiredViewHeightPx = maxOf(desiredViewHeightPx, height, minViewH)
        }
        applyBufferLayoutParamsIfNeeded()
        if (dimsChanged) {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (themeStyle.hideFocusBorder) return
        val pad = glowMarginPx
        val availW = (width - (pad * 2)).coerceAtLeast(0f)
        val availH = (height - (pad * 2)).coerceAtLeast(0f)
        val w = if (drawnCardWidthPx > 0) minOf(drawnCardWidthPx.toFloat(), availW) else availW
        val h = if (drawnCardHeightPx > 0) minOf(drawnCardHeightPx.toFloat(), availH) else availH
        if (w <= 0f || h <= 0f) return

        ringPath.reset()
        if (cornerRadiusPx > 0f) {
            ringPath.addRoundRect(pad, pad, pad + w, pad + h, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW)
        } else {
            ringPath.addRect(pad, pad, pad + w, pad + h, Path.Direction.CW)
        }

        if (isGlowEnabled && !isAnimatingTargetSize) {
            canvas.drawPath(ringPath, auraPaint)
            canvas.drawPath(ringPath, midPaint)
        }

        if (strokeColor != Color.TRANSPARENT || themeStyle.isRainbowBorder) {
            syncRainbowShader(w, h)
            borderPaint.strokeWidth = borderWidthPx
            canvas.drawPath(ringPath, borderPaint)
        }
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

    private fun resolveTargetPositionInParent(
        parent: ViewGroup,
        target: View,
        cardWidthPx: Int,
        useLiveTargetX: Boolean
    ): Pair<Float, Float> {
        tempRect.set(0, 0, target.width, target.height)
        val success = try {
            parent.offsetDescendantRectToMyCoords(target, tempRect)
            true
        } catch (_: Exception) {
            false
        }
        val targetX = if (useLiveTargetX && success) {
            tempRect.left.toFloat() - glowMarginPx
        } else {
            computeAnchoredRingX(parent, cardWidthPx)
        }
        val targetY = if (success) {
            tempRect.top.toFloat() - glowMarginPx
        } else {
            parent.getLocationInWindow(tempParentLoc)
            target.getLocationInWindow(tempTargetLoc)
            (tempTargetLoc[1] - tempParentLoc[1]).toFloat() - glowMarginPx
        }
        return targetX to targetY
    }

    fun moveToTarget(
        target: View,
        dominantColor: Int,
        animated: Boolean,
        keepColorIfVisible: Boolean = false,
        isExpandedCard: Boolean = false,
        explicitCardWidthPx: Int? = null,
        explicitCardHeightPx: Int? = null,
        useLivePosition: Boolean = false
    ) {
        val targetChanged = currentTargetView !== target
        currentTargetView = target
        attachTargetObserver(target)
        isTrackingExpandedTarget = isExpandedCard
        val parent = parent as? ViewGroup ?: return

        val resolvedCardW = LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
            explicitPx = explicitCardWidthPx,
            layoutParamPx = target.layoutParams?.width,
            measuredPx = target.width,
            isLayoutRequested = target.isLayoutRequested
        )
        val resolvedCardH = LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
            explicitPx = explicitCardHeightPx,
            layoutParamPx = target.layoutParams?.height,
            measuredPx = target.height,
            isLayoutRequested = target.isLayoutRequested
        )

        val keepRunningMorph = isMorphingSize &&
            animated &&
            !keepColorIfVisible &&
            !isExpandedCard &&
            morphTargetWidthPx == resolvedCardW &&
            morphTargetHeightPx == resolvedCardH
        if (!keepRunningMorph) {
            cancelRingSizeMorph()
            if (isAnimatingTargetSize) {
                isAnimatingTargetSize = false
                syncSoftwareLayer()
                invalidate()
            }
        } else {
            applyThemeColors(dominantColor)
            if (visibility != VISIBLE || alpha < 1f) {
                alpha = 1f
                visibility = VISIBLE
            }
            return
        }

        if (!target.isAttachedToWindow || !parent.isAttachedToWindow || resolvedCardW <= 0 || resolvedCardH <= 0 || parent.width <= 0) {
            target.post {
                if (currentTargetView === target && isAttachedToWindow) {
                    moveToTarget(
                        target = target,
                        dominantColor = dominantColor,
                        animated = animated,
                        keepColorIfVisible = keepColorIfVisible,
                        isExpandedCard = isExpandedCard,
                        explicitCardWidthPx = explicitCardWidthPx,
                        explicitCardHeightPx = explicitCardHeightPx,
                        useLivePosition = useLivePosition
                    )
                }
            }
            return
        }

        val margin = (glowMarginPx * 2).toInt()
        val expectedViewW = resolvedCardW + margin
        val expectedViewH = resolvedCardH + margin

        // While holding horizontal D-pad on uniform-width cards, keep the anchored ring steady
        // without re-rasterizing LAYER_TYPE_SOFTWARE blur filters on every passing card.
        if (keepColorIfVisible &&
            !isExpandedCard &&
            !useLivePosition &&
            visibility == VISIBLE &&
            alpha >= 1f &&
            drawnCardWidthPx == resolvedCardW &&
            drawnCardHeightPx == resolvedCardH &&
            width == expectedViewW &&
            height == expectedViewH
        ) {
            return
        }

        // Cancel previous animations to prevent old hideRing withEndAction from overriding visibility
        animate().cancel()

        val grid = (parent as? ModernRowContainerLayout)?.gridView
        val isRowAnimatingWidth = (grid?.adapter as? LeanbackRowAdapter)?.hasAnimatingWidthCard == true
        val isGridIdle = grid == null ||
            (!grid.isHoldingHorizontalKey && grid.scrollState == RecyclerView.SCROLL_STATE_IDLE)
        val (resolvedX, targetY) = resolveTargetPositionInParent(
            parent = parent,
            target = target,
            cardWidthPx = resolvedCardW,
            useLiveTargetX = isExpandedCard || useLivePosition || isRowAnimatingWidth || isGridIdle
        )

        applyThemeColors(dominantColor)
        updateDrawnDimensions(
            cardWidthPx = resolvedCardW,
            cardHeightPx = resolvedCardH,
            bufferCardWidthPx = resolvedCardW,
            allowShrink = true
        )

        translationX = resolvedX
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

        if (targetChanged && target.isLayoutRequested) {
            target.post {
                if (currentTargetView === target && isAttachedToWindow && visibility == VISIBLE) {
                    onScrollSettled()
                }
            }
        }
    }

    /**
     * Synchronizes ring size and position while a focused poster expands or collapses in place.
     * Allocates the expanded view buffer once per tween and updates [drawnCardWidthPx] per frame
     * with zero per-frame `requestLayout()` pressure on Compose AndroidView.
     */
    fun syncToTargetBounds(
        target: View,
        isAnimatingWidth: Boolean,
        isExpandedCard: Boolean,
        dominantColor: Int? = null,
        explicitCardWidthPx: Int? = null,
        explicitCardHeightPx: Int? = null,
        bufferCardWidthPx: Int? = null
    ) {
        val parent = parent as? ViewGroup ?: return
        if (!target.isAttachedToWindow || !parent.isAttachedToWindow) {
            return
        }
        if (currentTargetView != null && currentTargetView !== target) {
            return
        }
        val resolvedCardW = LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
            explicitPx = explicitCardWidthPx,
            layoutParamPx = target.layoutParams?.width,
            measuredPx = target.width,
            isLayoutRequested = target.isLayoutRequested,
            fallbackPx = drawnCardWidthPx
        )
        val resolvedCardH = LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
            explicitPx = explicitCardHeightPx,
            layoutParamPx = target.layoutParams?.height,
            measuredPx = target.height,
            isLayoutRequested = target.isLayoutRequested,
            fallbackPx = drawnCardHeightPx
        )
        if (resolvedCardW <= 0 || resolvedCardH <= 0) return

        cancelRingSizeMorph()
        currentTargetView = target
        attachTargetObserver(target)
        isTrackingExpandedTarget = isExpandedCard

        if (isAnimatingTargetSize != isAnimatingWidth) {
            isAnimatingTargetSize = isAnimatingWidth
            syncSoftwareLayer()
            invalidate()
        }
        if (dominantColor != null && !isAnimatingWidth) {
            applyThemeColors(dominantColor)
        }

        updateDrawnDimensions(
            cardWidthPx = resolvedCardW,
            cardHeightPx = resolvedCardH,
            bufferCardWidthPx = bufferCardWidthPx ?: resolvedCardW,
            allowShrink = !isAnimatingWidth
        )

        val (targetX, targetY) = resolveTargetPositionInParent(
            parent = parent,
            target = target,
            cardWidthPx = resolvedCardW,
            useLiveTargetX = true
        )
        translationX = targetX
        translationY = targetY
        if (visibility != VISIBLE) {
            alpha = 1f
            visibility = VISIBLE
        }
    }

    fun onParentScrolled(dx: Int) {
        if (visibility != VISIBLE) return
        val parent = parent as? ViewGroup ?: return
        val target = currentTargetView
        val grid = (parent as? ModernRowContainerLayout)?.gridView
        val isRowAnimatingWidth = (grid?.adapter as? LeanbackRowAdapter)?.hasAnimatingWidthCard == true
        val cardW = drawnCardWidthPx.takeIf { it > 0 } ?: (width - (glowMarginPx * 2).toInt()).coerceAtLeast(0)
        if (!isMorphingSize &&
            (isTrackingExpandedTarget || isAnimatingTargetSize || isRowAnimatingWidth) &&
            target != null &&
            target.isAttachedToWindow &&
            target.width > 0
        ) {
            val (targetX, targetY) = resolveTargetPositionInParent(
                parent = parent,
                target = target,
                cardWidthPx = cardW,
                useLiveTargetX = true
            )
            if (kotlin.math.abs(translationX - targetX) >= 0.5f) {
                translationX = targetX
            }
            if (kotlin.math.abs(translationY - targetY) >= 0.5f) {
                translationY = targetY
            }
            return
        }
        val targetX = computeAnchoredRingX(parent, cardW)
        if (kotlin.math.abs(translationX - targetX) >= 0.5f) {
            translationX = targetX
        }
    }

    fun onScrollSettled() {
        if (visibility != VISIBLE) return
        val parent = parent as? ViewGroup ?: return
        val target = currentTargetView
        if (target != null && target.isAttachedToWindow && target.width > 0 && target.height > 0) {
            if (!isAnimatingTargetSize && !isMorphingSize) {
                val resolvedW = LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
                    explicitPx = null,
                    layoutParamPx = target.layoutParams?.width,
                    measuredPx = target.width,
                    isLayoutRequested = target.isLayoutRequested,
                    fallbackPx = drawnCardWidthPx
                )
                val resolvedH = LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
                    explicitPx = null,
                    layoutParamPx = target.layoutParams?.height,
                    measuredPx = target.height,
                    isLayoutRequested = target.isLayoutRequested,
                    fallbackPx = drawnCardHeightPx
                )
                if (resolvedW > 0 && resolvedH > 0) {
                    updateDrawnDimensions(
                        cardWidthPx = resolvedW,
                        cardHeightPx = resolvedH,
                        bufferCardWidthPx = resolvedW,
                        allowShrink = true
                    )
                }
            }
            val cardW = drawnCardWidthPx.takeIf { it > 0 } ?: target.width
            val (targetX, targetY) = resolveTargetPositionInParent(
                parent = parent,
                target = target,
                cardWidthPx = cardW,
                useLiveTargetX = true
            )
            if (kotlin.math.abs(translationX - targetX) >= 0.5f) {
                translationX = targetX
            }
            if (kotlin.math.abs(translationY - targetY) >= 0.5f) {
                translationY = targetY
            }
            return
        }
        val cardW = drawnCardWidthPx.takeIf { it > 0 } ?: (width - (glowMarginPx * 2).toInt()).coerceAtLeast(0)
        val targetX = computeAnchoredRingX(parent, cardW)
        if (kotlin.math.abs(translationX - targetX) >= 0.5f) {
            translationX = targetX
        }
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
        cancelRingSizeMorph()
        currentTargetView = null
        attachTargetObserver(null)
        isTrackingExpandedTarget = false
        if (isAnimatingTargetSize) {
            isAnimatingTargetSize = false
            syncSoftwareLayer()
        }
        updateDrawnDimensions(
            cardWidthPx = cardWidthPx,
            cardHeightPx = cardHeightPx,
            bufferCardWidthPx = cardWidthPx,
            allowShrink = true
        )
        this.cornerRadiusPx = cornerRadiusPx
        applyThemeColors(dominantColor)
        translationX = xPx - glowMarginPx
        translationY = yPx - glowMarginPx
        if (visibility != VISIBLE || alpha < 1f) {
            alpha = 1f
            visibility = VISIBLE
        }
    }

    fun hideRing(immediate: Boolean = false) {
        animate().cancel()
        cancelRingSizeMorph()
        currentTargetView = null
        attachTargetObserver(null)
        isTrackingExpandedTarget = false
        if (isAnimatingTargetSize) {
            isAnimatingTargetSize = false
            syncSoftwareLayer()
        }
        if (immediate) {
            visibility = INVISIBLE
            alpha = 0f
        } else if (visibility == VISIBLE) {
            animate().alpha(0f).setDuration(100).withEndAction {
                visibility = INVISIBLE
            }.start()
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
