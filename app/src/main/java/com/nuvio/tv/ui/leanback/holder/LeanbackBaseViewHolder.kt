package com.nuvio.tv.ui.leanback.holder

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.container.InteropHorizontalGridView
import com.nuvio.tv.ui.leanback.container.InteropVerticalGridView
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import com.nuvio.tv.ui.leanback.core.PlayStoreGlowView
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem

/**
 * Base ViewHolder for all Leanback Native Cards.
 * Implements standard 60fps focus animations, deferred glow, zero GPU overdraw,
 * and key event delegation.
 */
abstract class LeanbackBaseViewHolder(
    val root: LinearLayout,
    val cardContainer: FrameLayout,
    val glowView: PlayStoreGlowView,
    var cornerRadiusPx: Float,
    val borderWidthPx: Int,
    val glowMarginPx: Int,
    val adapter: LeanbackUniversalAdapter
) : RecyclerView.ViewHolder(root) {

    protected val interpolator = DecelerateInterpolator(1.5f)
    var currentDominantColor: Int = LeanbackColorEngine.defaultFallbackColor
    protected var currentItem: LeanbackCardItem? = null
    open val isCircle: Boolean get() = false

    // Reuse a single focus border drawable to eliminate per-frame allocations
    val focusBorderDrawable = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
        cornerRadius = cornerRadiusPx
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

        (cardContainer.background as? GradientDrawable)?.cornerRadius = newRadiusPx
        focusBorderDrawable.cornerRadius = newRadiusPx
        glowView.cornerRadiusPx = newRadiusPx

        if (root.hasFocus()) {
            updateFocusVisual(true)
        }
    }

    open fun updateFocusVisual(hasFocus: Boolean) {
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

        // Lift focused card so glow and 1.08f scaled borders are never drawn over by adjacent views
        root.translationZ = if (hasFocus) 12f else 0f
        cardContainer.elevation = 0f
        cardContainer.translationZ = 0f

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
        glowView.visibility = View.GONE
        cardContainer.foreground = null
        if (cardContainer.background == null) {
            cardContainer.background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = cornerRadiusPx
            }
        }
    }
}

