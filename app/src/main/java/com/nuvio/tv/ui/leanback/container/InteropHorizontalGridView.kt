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
}

