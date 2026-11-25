package com.nuvio.tv.ui.leanback.core

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewTreeObserver
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Coordinates a zero-jank FLIP transition when navigating away from an expanded 16:9 poster
 * inside a Leanback [RecyclerView] / [androidx.leanback.widget.HorizontalGridView].
 *
 * Running a per-frame `requestLayout()` width tween concurrently with Leanback's
 * `setSelectedPositionSmooth()` causes `GridLayoutManager.onLayoutChildren()` to fight or cancel
 * the active `SmoothScroller` (snapping focus back on DPAD_LEFT and stuttering on DPAD_RIGHT).
 *
 * Instead, this helper:
 * 1. Captures the visual `left` of all visible sibling cards (First).
 * 2. Lets the caller collapse the expanded card's `LayoutParams` in a single layout pass (Last).
 * 3. In `OnPreDrawListener` (after layout, before the frame renders), compensates each shifted
 *    sibling with an exact GPU `translationX` offset so zero visual jump occurs (Invert), starts
 *    a pure-GPU 60fps `translationX -> 0f` tween (Play), and triggers the smooth scroll on a
 *    cleanly laid-out grid with zero competing `requestLayout()` calls.
 */
class LeanbackCollapseTransitionHelper(
    private val recyclerView: RecyclerView
) {
    private data class CapturedSibling(
        val view: View,
        val oldVisualLeftPx: Int
    )

    private data class ActiveSiblingOffset(
        val view: View,
        val startOffsetPx: Float
    )

    private var pendingPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private var pendingPreDrawHost: ViewTreeObserver? = null
    private var activeAnimator: ValueAnimator? = null
    private val activeOffsets = ArrayList<ActiveSiblingOffset>(12)

    val isAwaitingLayout: Boolean
        get() = pendingPreDrawListener != null

    val isActive: Boolean
        get() = pendingPreDrawListener != null || activeAnimator?.isRunning == true

    /**
     * Runs [collapseLayoutAction] to normalize the expanded card's width, then schedules
     * [onLayoutReady] right after the single collapse layout pass completes with GPU FLIP
     * compensation applied to shifted siblings.
     */
    fun runNavigationCollapseTransition(
        expandedItemView: View?,
        animated: Boolean,
        collapseLayoutAction: () -> Unit,
        onLayoutReady: () -> Unit
    ) {
        cancel(resetTranslations = true)

        if (!animated || !recyclerView.isAttachedToWindow) {
            collapseLayoutAction()
            onLayoutReady()
            return
        }

        val captured = ArrayList<CapturedSibling>(recyclerView.childCount)
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            if (child === expandedItemView) continue
            val visualLeft = child.left + child.translationX.roundToInt()
            captured.add(CapturedSibling(view = child, oldVisualLeftPx = visualLeft))
        }

        collapseLayoutAction()

        val vto = recyclerView.viewTreeObserver
        if (!vto.isAlive) {
            onLayoutReady()
            return
        }

        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                detachPendingPreDraw()
                if (!recyclerView.isAttachedToWindow) {
                    return true
                }

                activeOffsets.clear()
                for (i in 0 until captured.size) {
                    val item = captured[i]
                    val child = item.view
                    if (child.parent !== recyclerView) continue
                    val deltaPx = LeanbackCardExpandSpec.computeSiblingFlipDeltaPx(
                        oldLeftPx = item.oldVisualLeftPx,
                        oldTranslationXPx = 0f,
                        newLeftPx = child.left
                    )
                    if (abs(deltaPx) >= 1f) {
                        child.translationX = deltaPx
                        activeOffsets.add(ActiveSiblingOffset(view = child, startOffsetPx = deltaPx))
                    }
                }

                if (activeOffsets.isNotEmpty()) {
                    startGpuSlideBackAnimator()
                }

                onLayoutReady()
                return true
            }
        }

        pendingPreDrawListener = listener
        pendingPreDrawHost = vto
        vto.addOnPreDrawListener(listener)
        recyclerView.invalidate()
    }

    private fun startGpuSlideBackAnimator() {
        val animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = LeanbackCardExpandSpec.CARD_COLLAPSE_SLIDE_DURATION_MS
            interpolator = LeanbackCardExpandSpec.createExpandInterpolator()
            addUpdateListener { anim ->
                val remaining = anim.animatedValue as Float
                for (i in activeOffsets.indices) {
                    val entry = activeOffsets[i]
                    if (entry.view.parent === recyclerView) {
                        entry.view.translationX = entry.startOffsetPx * remaining
                    }
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var isCancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    isCancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (activeAnimator === animation) {
                        activeAnimator = null
                    }
                    if (!isCancelled) {
                        clearActiveTranslations()
                    }
                }
            })
        }
        activeAnimator = animator
        animator.start()
    }

    fun onChildDetached(child: View) {
        if (child.translationX != 0f) {
            child.translationX = 0f
        }
        for (i in activeOffsets.lastIndex downTo 0) {
            if (activeOffsets[i].view === child) {
                activeOffsets.removeAt(i)
            }
        }
    }

    fun cancel(resetTranslations: Boolean = true) {
        detachPendingPreDraw()
        activeAnimator?.cancel()
        activeAnimator = null
        if (resetTranslations) {
            clearActiveTranslations()
        } else {
            activeOffsets.clear()
        }
    }

    private fun detachPendingPreDraw() {
        val listener = pendingPreDrawListener
        if (listener != null) {
            val host = pendingPreDrawHost
            if (host != null && host.isAlive) {
                host.removeOnPreDrawListener(listener)
            } else if (recyclerView.viewTreeObserver.isAlive) {
                recyclerView.viewTreeObserver.removeOnPreDrawListener(listener)
            }
            pendingPreDrawListener = null
            pendingPreDrawHost = null
        }
    }

    private fun clearActiveTranslations() {
        for (i in activeOffsets.indices) {
            val view = activeOffsets[i].view
            if (view.translationX != 0f) {
                view.translationX = 0f
            }
        }
        activeOffsets.clear()
    }
}
