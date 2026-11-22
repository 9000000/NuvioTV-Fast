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
import com.nuvio.tv.ui.leanback.holder.LeanbackBaseViewHolder

/**
 * Android Leanback VerticalGridView customized for 2D TV Grids in Jetpack Compose.
 * Powers Catalog See All, Folder Tabbed Grid, and Search Discover at native 60fps
 * with RTL column awareness and 2D row-ahead artwork prefetching.
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

    internal var configuredNumColumns: Int = Int.MIN_VALUE
    internal var configuredTopPaddingPx: Int = Int.MIN_VALUE
    internal var configuredHorizontalSpacingPx: Int = Int.MIN_VALUE
    internal var configuredVerticalSpacingPx: Int = Int.MIN_VALUE
    internal var configuredAnimationsEnabled: Boolean? = null

    var isHoldingVerticalKey: Boolean = false
        private set

    private var pendingTargetPosition: Int = RecyclerView.NO_POSITION
    private var lastKeyNavigationTime: Long = 0L
    private var isNavigatingOutOfGrid: Boolean = false

    private val universalAdapter: LeanbackUniversalAdapter?
        get() = adapter as? LeanbackUniversalAdapter

    init {
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        itemAnimator = null
        layoutManager?.isItemPrefetchEnabled = true
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
                val hasGridFocus = pendingTargetPosition == position || hasFocus()
                if (child != null && hasGridFocus) {
                    if (!child.itemView.hasFocus()) {
                        child.itemView.requestFocus()
                    }
                    (child as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                    if (pendingTargetPosition == position) {
                        pendingTargetPosition = RecyclerView.NO_POSITION
                    }
                    universalAdapter?.onGridPositionFocused(context, position, numColumnsCount)
                } else if (hasGridFocus) {
                    universalAdapter?.onGridPositionFocused(context, position, numColumnsCount)
                }

                val totalCount = universalAdapter?.itemCount ?: 0
                if (position >= totalCount - (numColumnsCount * 2) && totalCount > 0) {
                    post {
                        universalAdapter?.requestLoadMore()
                    }
                }
            }
        })
    }

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

    private val isBusyLayout: Boolean
        get() = isComputingLayout || isInLayout || !isAttachedToWindow

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        if (clearFocusDepth > 0) return false
        if (isBusyLayout) {
            post {
                if (isAttachedToWindow && !isNavigatingOutOfGrid) {
                    requestFocus(direction, previouslyFocusedRect)
                }
            }
            return false
        }
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
            post {
                if (isAttachedToWindow && child != null && isChildOfThis(child)) {
                    super.requestChildFocus(child, focused)
                }
            }
            return
        }
        super.requestChildFocus(child, focused)
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (isBusyLayout) {
            return focused ?: findFocus() ?: this
        }
        val currentFocused = focused ?: findFocus()
        val holder = if (currentFocused != null) findContainingViewHolder(currentFocused) else null
        val actualPos = holder?.bindingAdapterPosition?.takeIf { it != RecyclerView.NO_POSITION }
            ?: selectedPosition
        val cols = numColumnsCount.coerceAtLeast(1)
        val isFirstRow = actualPos in 0 until cols
        val totalItems = universalAdapter?.itemCount ?: 0
        val isLastRow = isVerticalGridLastRow(actualPos, totalItems, cols)

        val result = super.focusSearch(focused, direction)

        when (direction) {
            View.FOCUS_LEFT, View.FOCUS_RIGHT -> {
                // Focus inside grid must NEVER escape to outside views via focusSearch
                if (result == null || !isChildOfThis(result)) {
                    return focused ?: findFocus()
                }
            }
            View.FOCUS_UP -> {
                if (!isFirstRow) {
                    val targetPos = actualPos - cols
                    if (targetPos >= 0) {
                        val targetVh = findViewHolderForAdapterPosition(targetPos)
                        if (targetVh != null) {
                            return targetVh.itemView
                        }
                    }
                    if (result == null || !isChildOfThis(result)) {
                        return focused ?: findFocus()
                    }
                }
            }
            View.FOCUS_DOWN -> {
                if (!isLastRow) {
                    val targetPos = (actualPos + cols).coerceAtMost(totalItems - 1)
                    if (targetPos < totalItems) {
                        val targetVh = findViewHolderForAdapterPosition(targetPos)
                        if (targetVh != null) {
                            return targetVh.itemView
                        }
                    }
                    if (result == null || !isChildOfThis(result)) {
                        return focused ?: findFocus()
                    }
                }
            }
        }
        if (result === this) {
            return focused ?: findFocus()
        }
        return result
    }

    private fun moveToPosition(targetPos: Int) {
        isNavigatingOutOfGrid = false
        pendingTargetPosition = targetPos
        if (animationsEnabled && !isHoldingVerticalKey) {
            setSelectedPositionSmooth(targetPos)
        } else {
            setSelectedPosition(targetPos)
        }
        val vh = findViewHolderForAdapterPosition(targetPos)
        if (vh != null) {
            vh.itemView.requestFocus()
            (vh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
            pendingTargetPosition = RecyclerView.NO_POSITION
        } else {
            post {
                if (isAttachedToWindow && pendingTargetPosition == targetPos) {
                    val deferredVh = findViewHolderForAdapterPosition(targetPos)
                    if (deferredVh != null) {
                        deferredVh.itemView.requestFocus()
                        (deferredVh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                        pendingTargetPosition = RecyclerView.NO_POSITION
                    }
                }
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        try {
            super.onLayout(changed, l, t, r, b)
        } catch (e: Exception) {
            android.util.Log.e("InteropVerticalGridView", "Layout error in grid", e)
        }
    }

    override fun removeViewAt(index: Int) {
        try { super.removeViewAt(index) } catch (_: Exception) {}
    }

    override fun removeViews(start: Int, count: Int) {
        try { super.removeViews(start, count) } catch (_: Exception) {}
    }

    override fun removeView(view: View) {
        try { super.removeView(view) } catch (_: Exception) {}
    }

    override fun removeViewInLayout(view: View?) {
        try { super.removeViewInLayout(view) } catch (_: Exception) {}
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) {
            isHoldingVerticalKey = false
        } else {
            isNavigatingOutOfGrid = false
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
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val towardStartColKey = if (isRtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        val towardEndColKey = if (isRtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT

        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!hasFocus() || isNavigatingOutOfGrid) {
                        isHoldingVerticalKey = false
                        pendingTargetPosition = RecyclerView.NO_POSITION
                        isNavigatingOutOfGrid = false
                        return false
                    }
                    isHoldingVerticalKey = false
                    val total = universalAdapter?.itemCount ?: 0
                    val currentSelected = selectedPosition.coerceIn(0, (total - 1).coerceAtLeast(0))
                    val target = if (pendingTargetPosition != RecyclerView.NO_POSITION) {
                        pendingTargetPosition.coerceIn(0, (total - 1).coerceAtLeast(0))
                    } else {
                        currentSelected
                    }
                    pendingTargetPosition = RecyclerView.NO_POSITION

                    if (animationsEnabled) {
                        setSelectedPositionSmooth(target)
                    } else {
                        setSelectedPosition(target)
                    }

                    val vh = findViewHolderForAdapterPosition(target)
                    if (vh != null) {
                        if (!vh.itemView.hasFocus()) {
                            vh.itemView.requestFocus()
                        }
                        (vh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                    } else {
                        post {
                            if (isAttachedToWindow && hasFocus() && !isNavigatingOutOfGrid) {
                                val deferredVh = findViewHolderForAdapterPosition(target)
                                deferredVh?.itemView?.requestFocus()
                                (deferredVh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                            }
                        }
                    }
                    return true
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = SystemClock.uptimeMillis()
            val currentFocused = findFocus()
            val holder = if (currentFocused != null) findContainingViewHolder(currentFocused) else null
            val actualPos = if (pendingTargetPosition != RecyclerView.NO_POSITION) {
                pendingTargetPosition
            } else {
                holder?.bindingAdapterPosition?.takeIf { it != RecyclerView.NO_POSITION }
                    ?: selectedPosition
            }
            val cols = numColumnsCount.coerceAtLeast(1)
            val currentColumn = if (actualPos >= 0) actualPos % cols else 0
            val isFirstRow = actualPos in 0 until cols
            val totalItems = universalAdapter?.itemCount ?: 0
            val isLastRow = isVerticalGridLastRow(actualPos, totalItems, cols)

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (isFirstRow) {
                        return handleBoundaryNavigation(event.repeatCount, onNavigateUpOnFirstRow)
                    }
                    if (event.repeatCount > 0) {
                        isHoldingVerticalKey = true
                        if (now - lastKeyNavigationTime < 85L) return true
                    }
                    lastKeyNavigationTime = now
                    val targetPos = actualPos - cols
                    if (targetPos >= 0) {
                        moveToPosition(targetPos)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (isLastRow) {
                        return handleBoundaryNavigation(event.repeatCount, onNavigateDownOnLastRow)
                    }
                    if (event.repeatCount > 0) {
                        isHoldingVerticalKey = true
                        if (now - lastKeyNavigationTime < 85L) return true
                    }
                    lastKeyNavigationTime = now
                    val targetPos = (actualPos + cols).coerceAtMost(totalItems - 1)
                    if (targetPos < totalItems) {
                        moveToPosition(targetPos)
                    }
                    return true
                }
                towardStartColKey -> {
                    if (currentColumn == 0) {
                        return handleBoundaryNavigation(event.repeatCount, onNavigateLeftOnFirstColumn)
                    } else {
                        if (event.repeatCount > 0 && now - lastKeyNavigationTime < 60L) {
                            return true
                        }
                        lastKeyNavigationTime = now
                        val targetPos = actualPos - 1
                        if (targetPos >= 0) {
                            moveToPosition(targetPos)
                        }
                        return true
                    }
                }
                towardEndColKey -> {
                    if (currentColumn == cols - 1 || actualPos == totalItems - 1) {
                        return handleBoundaryNavigation(event.repeatCount, onNavigateRightOnLastColumn)
                    } else {
                        if (event.repeatCount > 0 && now - lastKeyNavigationTime < 60L) {
                            return true
                        }
                        lastKeyNavigationTime = now
                        val targetPos = actualPos + 1
                        if (targetPos < totalItems) {
                            moveToPosition(targetPos)
                        }
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

    private fun handleBoundaryNavigation(repeatCount: Int, callback: (() -> Boolean)?): Boolean {
        if (repeatCount > 0) return true
        if (callback == null) return false
        if (callback.invoke()) {
            isNavigatingOutOfGrid = true
        }
        return true
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

internal fun isVerticalGridLastRow(actualPos: Int, totalItems: Int, cols: Int): Boolean {
    val safeCols = cols.coerceAtLeast(1)
    return totalItems > 0 && actualPos >= 0 && (actualPos / safeCols) >= (totalItems - 1) / safeCols
}
