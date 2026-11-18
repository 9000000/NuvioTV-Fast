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
 * Handles D-pad rapid key acceleration, RTL directional mapping, boundary edge transitions,
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

    internal var configuredRowHeightPx: Int = Int.MIN_VALUE
    internal var configuredSpacingPx: Int = Int.MIN_VALUE
    internal var configuredStartPaddingPx: Int = Int.MIN_VALUE
    internal var configuredVerticalPaddingPx: Int = Int.MIN_VALUE
    internal var configuredAnimationsEnabled: Boolean? = null
    internal var configuredFocusEnabled: Boolean? = null

    var canTakeFocus: (() -> Boolean)? = null

    private var isNavigatingToSidebar: Boolean = false

    fun mayTakeFocus(): Boolean = hasFocus() || canTakeFocus?.invoke() != false

    var isHoldingHorizontalKey: Boolean = false
        private set

    val hasPendingHorizontalNavigation: Boolean
        get() = isHoldingHorizontalKey ||
            pendingTargetPosition != RecyclerView.NO_POSITION ||
            scrollState != RecyclerView.SCROLL_STATE_IDLE

    private var arrivalAt0Time = 0L
    private var arrivedAt0FromEnd = false
    private var hasReleasedKeyAfterArrivingAt0 = true
    private var lastHorizontalKeyTime = 0L
    private var lastVerticalKeyTime = 0L
    private var pendingTargetPosition = RecyclerView.NO_POSITION
    var restoredPosition: Int = 0

    fun prepareForRowChange() {
        isHoldingHorizontalKey = false
        pendingTargetPosition = RecyclerView.NO_POSITION
    }

    private val universalAdapter: LeanbackUniversalAdapter?
        get() = adapter as? LeanbackUniversalAdapter

    init {
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        itemAnimator = null
        layoutManager?.isItemPrefetchEnabled = true
        setHasFixedSize(true)
        setItemViewCacheSize(10)

        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    isHoldingHorizontalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
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
                val isPendingTarget = (position == pendingTargetPosition) || (pendingTargetPosition != RecyclerView.NO_POSITION)
                // Keep pendingTargetPosition intact until the target ViewHolder is actually laid out
                if (position == pendingTargetPosition && child != null) {
                    pendingTargetPosition = RecyclerView.NO_POSITION
                }
                if (position == 0) {
                    arrivalAt0Time = SystemClock.uptimeMillis()
                } else {
                    arrivedAt0FromEnd = true
                    hasReleasedKeyAfterArrivingAt0 = false
                }

                restoredPosition = position

                val hasGridFocus = (hasFocus() || isFocused || (parent as? View)?.hasFocus() == true ||
                    isHoldingHorizontalKey || isPendingTarget) && mayTakeFocus()
                if (child != null && hasGridFocus) {
                    if (!child.itemView.hasFocus()) {
                        child.itemView.requestFocus()
                    }
                    (child as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                    universalAdapter?.onFocusedPositionChanged(context, position)
                } else if (hasGridFocus) {
                    universalAdapter?.onFocusedPositionChanged(context, position)
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

    fun ensureTargetChildFocused(targetPos: Int, direction: Int, retryCount: Int = 0) {
        if (!isAttachedToWindow || !mayTakeFocus()) return
        // Abort stale retry if the user already moved to a newer horizontal target
        val activeTarget = if (pendingTargetPosition != RecyclerView.NO_POSITION) {
            pendingTargetPosition
        } else {
            selectedPosition
        }
        if (activeTarget != RecyclerView.NO_POSITION && activeTarget != targetPos) return

        val vh = findViewHolderForAdapterPosition(targetPos)
        if (vh != null) {
            if (!vh.itemView.hasFocus()) {
                vh.itemView.requestFocus(direction)
            }
            (vh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
        } else if (retryCount < 10) {
            post {
                if (isAttachedToWindow && mayTakeFocus()) {
                    ensureTargetChildFocused(targetPos, direction, retryCount + 1)
                }
            }
        }
    }

    private val isBusyLayout: Boolean
        get() = isComputingLayout || isInLayout || !isAttachedToWindow

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        isNavigatingToSidebar = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        if (!mayTakeFocus()) return false
        if (isBusyLayout) {
            post { if (mayTakeFocus()) requestFocus(direction, previouslyFocusedRect) }
            return false
        }
        val targetPos = if (pendingTargetPosition != RecyclerView.NO_POSITION) {
            pendingTargetPosition
        } else if (restoredPosition > 0) {
            restoredPosition
        } else {
            selectedPosition.coerceAtLeast(0)
        }
        val vh = findViewHolderForAdapterPosition(targetPos)
        if (vh != null) {
            val focused = vh.itemView.requestFocus(direction)
            if (focused) {
                (vh as? LeanbackBaseViewHolder)?.updateFocusVisual(true)
                return true
            }
        }
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        isFocusable = true
        isFocusableInTouchMode = true
        val result = super.requestFocus(direction, previouslyFocusedRect)
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        setSelectedPosition(targetPos)
        ensureTargetChildFocused(targetPos, direction)
        post {
            if (isAttachedToWindow && mayTakeFocus()) {
                ensureTargetChildFocused(targetPos, direction)
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

    override fun clearChildFocus(child: View?) {
        val deliberateRowChange = isNavigatingToSidebar || canTakeFocus?.invoke() == false
        val shouldProtectFocus = isAttachedToWindow &&
            !deliberateRowChange &&
            (hasPendingHorizontalNavigation || universalAdapter?.isSubmittingItems == true)

        if (shouldProtectFocus) {
            val targetPos = if (pendingTargetPosition != RecyclerView.NO_POSITION) {
                pendingTargetPosition
            } else {
                selectedPosition.coerceAtLeast(0)
            }
            val targetVh = findViewHolderForAdapterPosition(targetPos)
            if (targetVh != null && targetVh.itemView !== child) {
                targetVh.itemView.requestFocus()
                return
            }
            if (!hasFocus() || findFocus() === child) {
                isFocusable = true
                isFocusableInTouchMode = true
                super.requestFocus()
            }
            return
        }
        super.clearChildFocus(child)
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (isBusyLayout) {
            return focused ?: findFocus() ?: this
        }
        val total = universalAdapter?.itemCount ?: 0
        val currentPos = selectedPosition
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val towardStartDir = if (isRtl) View.FOCUS_RIGHT else View.FOCUS_LEFT
        val towardEndDir = if (isRtl) View.FOCUS_LEFT else View.FOCUS_RIGHT

        if (direction == towardStartDir && currentPos <= 0) {
            return focused ?: findFocus() ?: this
        }
        if (direction == towardEndDir && total > 0 && currentPos >= total - 1) {
            return focused ?: findFocus() ?: this
        }
        val result = super.focusSearch(focused, direction)
        if (result == null || result === this || !isChildOfThis(result)) {
            return focused?.takeIf { isChildOfThis(it) }
                ?: findFocus()?.takeIf { isChildOfThis(it) }
                ?: findViewHolderForAdapterPosition(currentPos)?.itemView
                ?: this
        }
        return result
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        try {
            super.onLayout(changed, l, t, r, b)
        } catch (e: Exception) {
            android.util.Log.e("InteropHorizontalGridView", "Layout error in row", e)
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
            pendingTargetPosition = RecyclerView.NO_POSITION
        } else {
            if (selectedPosition <= 0) {
                hasReleasedKeyAfterArrivingAt0 = true
                arrivedAt0FromEnd = false
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
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val towardStartKey = if (isRtl) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        val towardEndKey = if (isRtl) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val towardStartFocusDir = if (isRtl) View.FOCUS_RIGHT else View.FOCUS_LEFT
        val towardEndFocusDir = if (isRtl) View.FOCUS_LEFT else View.FOCUS_RIGHT

        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    isHoldingHorizontalKey = false
                    if (isNavigatingToSidebar) {
                        isNavigatingToSidebar = false
                        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                        return true
                    }
                    if (!mayTakeFocus()) {
                        return true
                    }
                    val total = universalAdapter?.itemCount ?: 0
                    val currentSelected = selectedPosition.coerceIn(0, (total - 1).coerceAtLeast(0))
                    val target = if (pendingTargetPosition != RecyclerView.NO_POSITION) {
                        pendingTargetPosition.coerceIn(0, (total - 1).coerceAtLeast(0))
                    } else {
                        currentSelected
                    }
                    pendingTargetPosition = RecyclerView.NO_POSITION

                    if (event.keyCode == towardStartKey && target <= 0) {
                        hasReleasedKeyAfterArrivingAt0 = true
                        arrivedAt0FromEnd = false
                    }

                    // Never call stopScroll() — let Leanback settle smoothly onto the keyline
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
                        ensureTargetChildFocused(
                            target,
                            if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) View.FOCUS_LEFT else View.FOCUS_RIGHT
                        )
                    }
                    return true
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = SystemClock.uptimeMillis()
            val focusedChild = findFocus()
            val holder = if (focusedChild != null) findContainingViewHolder(focusedChild) else null
            val resolvedHolderPos = holder?.bindingAdapterPosition?.takeIf { it != RecyclerView.NO_POSITION }
            val resolvedSelectedPos = selectedPosition.takeIf { it != RecyclerView.NO_POSITION }
            val actualPos = when {
                pendingTargetPosition != RecyclerView.NO_POSITION -> pendingTargetPosition
                scrollState == RecyclerView.SCROLL_STATE_IDLE -> resolvedHolderPos ?: resolvedSelectedPos ?: 0
                else -> resolvedSelectedPos ?: resolvedHolderPos ?: 0
            }
            val total = universalAdapter?.itemCount ?: 0

            when (event.keyCode) {
                towardStartKey -> {
                    if (actualPos <= 0 || total <= 0) {
                        pendingTargetPosition = 0
                        if (selectedPosition != 0) {
                            if (animationsEnabled) {
                                setSelectedPositionSmooth(0)
                            } else {
                                setSelectedPosition(0)
                            }
                        }
                        val arrivedRecently = arrivedAt0FromEnd && (now - arrivalAt0Time < 300L)
                        if (!arrivedRecently && event.repeatCount == 0 && hasReleasedKeyAfterArrivingAt0) {
                            if (onNavigateLeftOnFirstItem?.invoke() == true) {
                                isNavigatingToSidebar = true
                                isHoldingHorizontalKey = false
                                pendingTargetPosition = RecyclerView.NO_POSITION
                                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                clearFocus()
                                return true
                            }
                        }
                        return true
                    }

                    if (event.repeatCount > 0) {
                        isHoldingHorizontalKey = true
                        if (now - lastHorizontalKeyTime < 55L) {
                            return true
                        }
                    }
                    lastHorizontalKeyTime = now

                    val target = (actualPos - 1).coerceAtLeast(0)
                    pendingTargetPosition = target
                    val nextVh = findViewHolderForAdapterPosition(target)
                    if (!animationsEnabled) {
                        setSelectedPosition(target)
                        if (nextVh != null) {
                            nextVh.itemView.requestFocus(towardStartFocusDir)
                        } else {
                            ensureTargetChildFocused(target, towardStartFocusDir)
                        }
                        return true
                    }
                    setSelectedPositionSmooth(target)
                    if (nextVh != null) {
                        nextVh.itemView.requestFocus(towardStartFocusDir)
                    } else {
                        ensureTargetChildFocused(target, towardStartFocusDir)
                    }
                    return true
                }
                towardEndKey -> {
                    if (total <= 0 || actualPos >= total - 1) {
                        val lastIdx = (total - 1).coerceAtLeast(0)
                        pendingTargetPosition = lastIdx
                        if (selectedPosition != lastIdx && total > 0) {
                            if (animationsEnabled) {
                                setSelectedPositionSmooth(lastIdx)
                            } else {
                                setSelectedPosition(lastIdx)
                            }
                        }
                        return true
                    }

                    if (event.repeatCount > 0) {
                        isHoldingHorizontalKey = true
                        if (now - lastHorizontalKeyTime < 55L) {
                            return true
                        }
                    }
                    lastHorizontalKeyTime = now

                    val target = (actualPos + 1).coerceAtMost((total - 1).coerceAtLeast(0))
                    pendingTargetPosition = target
                    val nextVh = findViewHolderForAdapterPosition(target)
                    if (!animationsEnabled) {
                        setSelectedPosition(target)
                        if (nextVh != null) {
                            nextVh.itemView.requestFocus(towardEndFocusDir)
                        } else {
                            ensureTargetChildFocused(target, towardEndFocusDir)
                        }
                        return true
                    }
                    setSelectedPositionSmooth(target)
                    if (nextVh != null) {
                        nextVh.itemView.requestFocus(towardEndFocusDir)
                    } else {
                        ensureTargetChildFocused(target, towardEndFocusDir)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    isHoldingHorizontalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
                    if (event.repeatCount > 0 && now - lastVerticalKeyTime < 115L) {
                        return true
                    }
                    lastVerticalKeyTime = now
                    if (onNavigateDown?.invoke() == true) return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    isHoldingHorizontalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
                    if (event.repeatCount > 0 && now - lastVerticalKeyTime < 115L) {
                        return true
                    }
                    lastVerticalKeyTime = now
                    if (onNavigateUp?.invoke() == true) return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    isHoldingHorizontalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
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
