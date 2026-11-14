package com.nuvio.tv.ui.leanback.core

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * High-fidelity 3-tier Skia Gaussian Bloom Glow View for Android TV.
 * Renders atmospheric neon bloom around cards with round stroke caps,
 * synchronized perfectly with card corner radius or circular avatars.
 */
class PlayStoreGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

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

    var isCircle: Boolean = false
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

    // 3 Skia Gaussian Blur filters for realistic TV Neon Glow:
    // 1. Aura: Soft atmospheric dispersion spreading into dark background
    // 2. Mid: Optical saturated beam hugging card edges and corners
    // 3. Core: High-intensity white-blended neon core filament
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
            if (layerType != LAYER_TYPE_NONE) {
                setLayerType(LAYER_TYPE_NONE, null)
            }
            return
        }
        if (visibility == VISIBLE && layerType != LAYER_TYPE_SOFTWARE) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
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

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility != VISIBLE) {
            if (layerType != LAYER_TYPE_NONE) {
                setLayerType(LAYER_TYPE_NONE, null)
            }
        } else {
            if (glowSpreadPx > 0f && layerType != LAYER_TYPE_SOFTWARE) {
                setLayerType(LAYER_TYPE_SOFTWARE, null)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (layerType != LAYER_TYPE_NONE) {
            setLayerType(LAYER_TYPE_NONE, null)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (glowColor == 0 || glowSpreadPx <= 0f) return
        val margin = if (glowMarginPx > 0f) glowMarginPx else glowSpreadPx
        val cardLeft = margin
        val cardTop = margin
        val cardRight = width - margin
        val cardBottom = height - margin

        cardPath.reset()
        if (isCircle) {
            cardPath.addOval(cardLeft, cardTop, cardRight, cardBottom, Path.Direction.CW)
        } else if (cornerRadiusPx > 0f) {
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
            cardPath.addRect(cardLeft, cardTop, cardRight, cardBottom, Path.Direction.CW)
        }

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

