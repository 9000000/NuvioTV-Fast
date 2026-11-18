package com.nuvio.tv.ui.leanback.core

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

/**
 * High-fidelity 3-tier Skia Gaussian Bloom Glow View for Android TV.
 * Renders atmospheric neon bloom around cards with round stroke caps,
 * synchronized with card corner radius or circular avatars.
 *
 * Optimization:
 * - Shares immutable [BlurMaskFilter] instances across all ViewHolders via a static cache.
 * - Allocates a `LAYER_TYPE_SOFTWARE` backing buffer ONLY while `visibility == VISIBLE`,
 *   releasing it (`LAYER_TYPE_NONE`) immediately on `GONE`/`INVISIBLE`/detach.
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
                syncSoftwareLayer()
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
                syncSoftwareLayer()
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
        val needsSoftwareLayer = isAttachedToWindow &&
            visibility == VISIBLE &&
            glowSpreadPx > 0f &&
            glowColor != 0
        val desiredLayer = if (needsSoftwareLayer) LAYER_TYPE_SOFTWARE else LAYER_TYPE_NONE
        if (layerType != desiredLayer) {
            setLayerType(desiredLayer, null)
        }
    }

    private fun updateFilters() {
        if (glowSpreadPx <= 0f) {
            corePaint.maskFilter = null
            midPaint.maskFilter = null
            auraPaint.maskFilter = null
            return
        }
        val filters = obtainBlurFilterSet(glowSpreadPx)
        corePaint.maskFilter = filters.core
        midPaint.maskFilter = filters.mid
        auraPaint.maskFilter = filters.aura

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
        if (cardRight <= cardLeft || cardBottom <= cardTop) return

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

    private data class BlurFilterSet(
        val core: BlurMaskFilter,
        val mid: BlurMaskFilter,
        val aura: BlurMaskFilter
    )

    companion object {
        private val filterCache = LruCache<Int, BlurFilterSet>(12)

        private fun obtainBlurFilterSet(spreadPx: Float): BlurFilterSet {
            val bucketKey = (spreadPx * 10f).roundToInt().coerceAtLeast(1)
            synchronized(filterCache) {
                filterCache.get(bucketKey)?.let { return it }
            }
            val rCore = (spreadPx * 0.15f).coerceAtLeast(1f)
            val rMid = (spreadPx * 0.35f).coerceAtLeast(1f)
            val rAura = (spreadPx * 0.65f).coerceAtLeast(1f)
            val created = BlurFilterSet(
                core = BlurMaskFilter(rCore, BlurMaskFilter.Blur.NORMAL),
                mid = BlurMaskFilter(rMid, BlurMaskFilter.Blur.NORMAL),
                aura = BlurMaskFilter(rAura, BlurMaskFilter.Blur.NORMAL)
            )
            synchronized(filterCache) {
                filterCache.put(bucketKey, created)
            }
            return created
        }
    }
}
