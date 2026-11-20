package com.nuvio.tv.ui.leanback.holder

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import coil3.BitmapImage
import coil3.Image
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.container.InteropHorizontalGridView
import com.nuvio.tv.ui.leanback.container.InteropVerticalGridView
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import com.nuvio.tv.ui.leanback.core.PlayStoreGlowView
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem

/**
 * Root LinearLayout that isolates `getRootView()` to prevent Android's `rootViewRequestFocus()`
 * from escaping up to `DecorView` / `AndroidComposeView` during RecyclerView detach or recycling.
 */
class FocusIsolatedRootLayout(context: Context) : LinearLayout(context) {
    private var clearFocusDepth: Int = 0

    override fun getRootView(): View = this

    override fun clearFocus() {
        clearFocusDepth++
        try {
            super.clearFocus()
        } finally {
            clearFocusDepth--
        }
    }

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        // Prevent View.clearFocusInternal() -> rootViewRequestFocus() from refocusing this card
        // while clearing focus, which avoids infinite clearFocus/requestFocus recursion.
        if (clearFocusDepth > 0) return false
        return super.requestFocus(direction, previouslyFocusedRect)
    }
}

internal data class LeanbackCardChromeMetrics(
    val glowSpreadPx: Float,
    val glowMarginPx: Int,
    val borderWidthPx: Int
)

internal fun computeLeanbackCardChromeMetrics(context: Context): LeanbackCardChromeMetrics {
    val metrics = context.resources.displayMetrics
    val glowSpreadPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 18f, metrics)
    val glowMarginPx = (glowSpreadPx * 1.35f).toInt()
    val borderWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2.0f, metrics)
        .toInt()
        .coerceAtLeast(2)
    return LeanbackCardChromeMetrics(
        glowSpreadPx = glowSpreadPx,
        glowMarginPx = glowMarginPx,
        borderWidthPx = borderWidthPx
    )
}

/**
 * Base ViewHolder for all Leanback Native Cards.
 * Implements standard 60fps focus visuals, deferred glow, zero GPU overdraw,
 * placeholder restoration, and key event delegation.
 */
abstract class LeanbackBaseViewHolder(
    val root: LinearLayout,
    val cardContainer: FrameLayout,
    val glowView: PlayStoreGlowView,
    var cornerRadiusPx: Float,
    val borderWidthPx: Int,
    val glowMarginPx: Int,
    var adapter: LeanbackUniversalAdapter
) : RecyclerView.ViewHolder(root) {

    var currentDominantColor: Int = LeanbackColorEngine.defaultFallbackColor
    protected var currentItem: LeanbackCardItem? = null
    open val isCircle: Boolean get() = false

    // Reuse a single focus border drawable to eliminate per-frame allocations
    val focusBorderDrawable = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = cornerRadiusPx
    }

    // Reuse a single placeholder background drawable to eliminate GC churn on recycle
    protected val placeholderBackgroundDrawable = GradientDrawable().apply {
        setColor(Color.parseColor("#1A1A1A"))
        cornerRadius = cornerRadiusPx
    }

    protected fun applyPlaceholderBackgroundShape() {
        if (isCircle) {
            placeholderBackgroundDrawable.shape = GradientDrawable.OVAL
            placeholderBackgroundDrawable.setColor(Color.parseColor("#222226"))
        } else {
            placeholderBackgroundDrawable.shape = GradientDrawable.RECTANGLE
            placeholderBackgroundDrawable.setColor(Color.parseColor("#1A1A1A"))
            placeholderBackgroundDrawable.cornerRadius = cornerRadiusPx
        }
    }

    protected fun ensurePlaceholderBackground() {
        applyPlaceholderBackgroundShape()
        if (cardContainer.background !== placeholderBackgroundDrawable) {
            cardContainer.background = placeholderBackgroundDrawable
        }
    }

    protected fun resolveDominantColorFromImage(
        result: Image,
        boundKey: String,
        colorCacheKey: String,
        notifyIfCachedAndFocused: Boolean
    ) {
        val existingColor = LeanbackColorEngine.getCachedColor(colorCacheKey)
        if (existingColor != null) {
            currentDominantColor = existingColor
            if (notifyIfCachedAndFocused && root.hasFocus()) {
                updateFocusVisual(true)
            }
            return
        }
        val bmp = (result as? BitmapImage)?.bitmap
        if (bmp != null && !bmp.isRecycled) {
            LeanbackColorEngine.extractDominantColorAsync(colorCacheKey, bmp) { extracted ->
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

    // Defer software layer glow by 55ms during rapid navigation
    val deferredGlowRunnable = Runnable {
        if (root.hasFocus() && adapter.animationsEnabled()) {
            glowView.glowColor = currentDominantColor
            glowView.visibility = View.VISIBLE
        }
    }

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
                    KeyEvent.KEYCODE_BACK -> if (adapter.onNavigateBack()) return@setOnKeyListener true
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

    open fun updateCornerRadius(newRadiusPx: Float) {
        val radiusChanged = cornerRadiusPx != newRadiusPx || glowView.cornerRadiusPx != newRadiusPx
        if (!radiusChanged) return
        cornerRadiusPx = newRadiusPx

        applyPlaceholderBackgroundShape()
        if (!isCircle) {
            (cardContainer.background as? GradientDrawable)?.cornerRadius = newRadiusPx
        }
        focusBorderDrawable.cornerRadius = newRadiusPx
        glowView.cornerRadiusPx = newRadiusPx

        if (root.hasFocus()) {
            updateFocusVisual(true)
        }
    }

    open fun updateFocusVisual(hasFocus: Boolean) {
        root.removeCallbacks(deferredGlowRunnable)
        val anim = adapter.animationsEnabled()
        root.animate().cancel()
        if (root.scaleX != 1.0f) root.scaleX = 1.0f
        if (root.scaleY != 1.0f) root.scaleY = 1.0f

        // Lift focused card so glow and borders are never drawn over by adjacent views
        val targetZ = if (hasFocus) 12f else 0f
        if (root.translationZ != targetZ) root.translationZ = targetZ
        if (cardContainer.elevation != 0f) cardContainer.elevation = 0f
        if (cardContainer.translationZ != 0f) cardContainer.translationZ = 0f

        if (isCircle) {
            focusBorderDrawable.shape = GradientDrawable.OVAL
        } else {
            focusBorderDrawable.shape = GradientDrawable.RECTANGLE
            focusBorderDrawable.cornerRadius = cornerRadiusPx
        }

        if (anim && hasFocus) {
            val tintedWhite = ColorUtils.blendARGB(currentDominantColor, Color.WHITE, 0.90f)
            focusBorderDrawable.setStroke(borderWidthPx, tintedWhite)
            cardContainer.foreground = focusBorderDrawable

            val isFastScrollingHorizontal = (adapter.attachedRecyclerView as? InteropHorizontalGridView)?.isHoldingHorizontalKey == true
            val isFastScrollingVertical = (adapter.attachedRecyclerView as? InteropVerticalGridView)?.isHoldingVerticalKey == true
            if (isFastScrollingHorizontal || isFastScrollingVertical) {
                glowView.visibility = View.GONE
                root.postDelayed(deferredGlowRunnable, 55L)
            } else {
                glowView.glowColor = currentDominantColor
                glowView.visibility = View.VISIBLE
            }
        } else if (hasFocus) {
            glowView.visibility = View.GONE
            focusBorderDrawable.setStroke(borderWidthPx, Color.WHITE)
            cardContainer.foreground = focusBorderDrawable
        } else {
            glowView.visibility = View.GONE
            cardContainer.foreground = null
        }
    }

    abstract fun bind(
        item: LeanbackCardItem,
        cardWidthPx: Int,
        cardHeightPx: Int,
        cornerRadiusPx: Float,
        showLabels: Boolean
    )

    open fun unbind() {
        root.removeCallbacks(deferredGlowRunnable)
        currentItem = null
        glowView.visibility = View.GONE
        cardContainer.foreground = null
        ensurePlaceholderBackground()
    }
}
