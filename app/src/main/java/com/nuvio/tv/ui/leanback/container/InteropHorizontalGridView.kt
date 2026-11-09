package com.nuvio.tv.ui.leanback.container

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.recyclerview.widget.RecyclerView
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.holder.LeanbackBaseViewHolder

/**
 * Android Leanback HorizontalGridView customized for Jetpack Compose interop.
 * Handles D-pad rapid key acceleration, boundary edge transitions,
 * detached view sanitization, and layout race prevention.
 */
class InteropHorizontalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalGridView(context, attrs, defStyleAttr) {

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
    private var lastEdgeLeftPressTime = 0L

    private val universalAdapter: LeanbackUniversalAdapter?
        get() = adapter as? LeanbackUniversalAdapter

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
                    universalAdapter?.onScrollSettled()
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

                if (child != null && hasFocus()) {
                    child.itemView.requestFocus()
                    (child as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                }

                val totalCount = universalAdapter?.itemCount ?: 0
                if (position >= totalCount - 5 && totalCount > 0) {
                    post {
                        universalAdapter?.onLoadMore?.invoke()
                    }
                }
            }
        })
    }

    override fun getRootView(): View = this

    private val isBusyLayout: Boolean
        get() = isComputingLayout || isInLayout || !isAttachedToWindow

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        if (isBusyLayout) {
            post { requestFocus(direction, previouslyFocusedRect) }
            return false
        }
        // If coming from another view with geometry rect (e.g. D-pad vertical movement),
        // let super find the card with closest geometric alignment (same column).
        if (previouslyFocusedRect != null) {
            val result = super.requestFocus(direction, previouslyFocusedRect)
            if (result) {
                val focusedChild = findFocus()
                if (focusedChild !== this) {
                    val holder = if (focusedChild != null) findContainingViewHolder(focusedChild) else null
                    (holder as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                    return true
                }
            }
        }
        // Programmatic focus (e.g. from search submit / enter): target the current selectedPosition
        val targetPos = selectedPosition.coerceAtLeast(0)
        val vh = findViewHolderForAdapterPosition(targetPos)
        if (vh != null) {
            val focused = vh.itemView.requestFocus(direction, previouslyFocusedRect)
            if (focused) {
                (vh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                return true
            }
        }
        val result = super.requestFocus(direction, previouslyFocusedRect)
        if (result) {
            val focusedChild = findFocus()
            if (focusedChild !== this) {
                val holder = if (focusedChild != null) findContainingViewHolder(focusedChild) else null
                (holder as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
            } else {
                val deferredVh = findViewHolderForAdapterPosition(targetPos)
                if (deferredVh != null) {
                    deferredVh.itemView.requestFocus()
                    (deferredVh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                }
            }
        }
        return result
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
        val result = super.focusSearch(focused, direction)
        if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
            if (result != null && !isChildOfThis(result)) {
                return focused
            }
        }
        if (result === this) {
            return focused
        }
        return result
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        try {
            super.onLayout(changed, l, t, r, b)
        } catch (_: Exception) {
            // Guard against internal layout pass race conditions
        }
    }

    override fun removeViewAt(index: Int) {
        try { super.removeViewAt(index) } catch (_: Exception) {}
    }

    override fun removeViews(start: Int, count: Int) {
        try { super.removeViews(start, count) } catch (_: Exception) {}
    }

    override fun removeView(view: View?) {
        try { super.removeView(view) } catch (_: Exception) {}
    }

    override fun removeViewInLayout(view: View?) {
        try { super.removeViewInLayout(view) } catch (_: Exception) {}
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
        } else {
            if (selectedPosition <= 0) {
                hasReleasedKeyAfterArrivingAt0 = true
                arrivedAt0FromRight = false
            }
            if (findFocus() === this) {
                val targetPos = selectedPosition.coerceAtLeast(0)
                val vh = findViewHolderForAdapterPosition(targetPos)
                if (vh != null) {
                    vh.itemView.requestFocus()
                    (vh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                } else {
                    post {
                        if (isAttachedToWindow && findFocus() === this) {
                            val deferredVh = findViewHolderForAdapterPosition(targetPos)
                            deferredVh?.itemView?.requestFocus()
                            (deferredVh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                        }
                    }
                }
            }
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
                    val focusedChild = findFocus()
                    val holder = if (focusedChild != null) findContainingViewHolder(focusedChild) else null
                    val actualPos = holder?.bindingAdapterPosition ?: selectedPosition

                    if (actualPos == 0) {
                        val arrivedRecently = arrivedAt0FromRight && (now - arrivalAt0Time < 350L)
                        if (!arrivedRecently && event.repeatCount == 0 && onNavigateLeftOnFirstItem != null) {
                            val isIntentionalEdgeTrigger = (now - lastEdgeLeftPressTime) < 700L
                            if (isIntentionalEdgeTrigger) {
                                lastEdgeLeftPressTime = 0L
                                hasReleasedKeyAfterArrivingAt0 = true
                                arrivedAt0FromRight = false
                                if (onNavigateLeftOnFirstItem?.invoke() == true) {
                                    return true
                                }
                            } else {
                                lastEdgeLeftPressTime = now
                            }
                        }
                        stopScroll()
                        return true
                    }

                    if (event.repeatCount > 0) {
                        isHoldingHorizontalKey = true
                    }

                    if (!animationsEnabled) {
                        val target = (actualPos - 1).coerceAtLeast(0)
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
                        val total = universalAdapter?.itemCount ?: 0
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

    private fun isChildOfThis(target: View): Boolean {
        var p = target.parent
        while (p != null) {
            if (p === this) return true
            p = p.parent
        }
        return false
    }
}

