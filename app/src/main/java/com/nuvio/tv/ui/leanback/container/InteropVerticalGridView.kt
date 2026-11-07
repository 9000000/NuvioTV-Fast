package com.nuvio.tv.ui.leanback.container

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter

/**
 * Android Leanback VerticalGridView customized for 2D TV Grids in Jetpack Compose.
 * Powers Catalog See All, Library, Search Discover, and Home Grid at native 60fps.
 */
class InteropVerticalGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VerticalGridView(context, attrs, defStyleAttr) {

    var onNavigateUpOnFirstRow: (() -> Boolean)? = null
    var onNavigateDownOnLastRow: (() -> Boolean)? = null
    var onNavigateLeftOnFirstColumn: (() -> Boolean)? = null
    var onNavigateRightOnLastColumn: (() -> Boolean)? = null
    var onNavigateBack: (() -> Boolean)? = null
    var animationsEnabled: Boolean = true
    var numColumnsCount: Int = 5

    var isHoldingVerticalKey: Boolean = false
        private set

    private val universalAdapter: LeanbackUniversalAdapter?
        get() = adapter as? LeanbackUniversalAdapter

    init {
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        itemAnimator = null
        layoutManager?.isItemPrefetchEnabled = false
        setHasFixedSize(true)
        setItemViewCacheSize(12)

        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    isHoldingVerticalKey = false
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
                val totalCount = universalAdapter?.itemCount ?: 0
                if (position >= totalCount - (numColumnsCount * 2) && totalCount > 0) {
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

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) {
            isHoldingVerticalKey = false
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (isHoldingVerticalKey) {
                        stopScroll()
                        isHoldingVerticalKey = false
                    }
                    return true
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val focusedChild = findFocus()
            val holder = if (focusedChild != null) findContainingViewHolder(focusedChild) else null
            val actualPos = holder?.bindingAdapterPosition ?: selectedPosition
            val cols = numColumnsCount.coerceAtLeast(1)
            val currentColumn = if (actualPos >= 0) actualPos % cols else 0
            val isFirstRow = actualPos in 0 until cols
            val totalItems = universalAdapter?.itemCount ?: 0
            val isLastRow = actualPos >= 0 && actualPos >= (totalItems - cols)

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (isFirstRow) {
                        if (onNavigateUpOnFirstRow?.invoke() == true) {
                            return true
                        }
                    }
                    if (event.repeatCount > 0) {
                        isHoldingVerticalKey = true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (isLastRow) {
                        if (onNavigateDownOnLastRow?.invoke() == true) {
                            return true
                        }
                    }
                    if (event.repeatCount > 0) {
                        isHoldingVerticalKey = true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (currentColumn == 0) {
                        if (onNavigateLeftOnFirstColumn?.invoke() == true) {
                            return true
                        }
                        stopScroll()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (currentColumn == cols - 1) {
                        if (onNavigateRightOnLastColumn?.invoke() == true) {
                            return true
                        }
                        stopScroll()
                        return true
                    }
                }
                KeyEvent.KEYCODE_BACK -> {
                    isHoldingVerticalKey = false
                    if (onNavigateBack?.invoke() == true) {
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

