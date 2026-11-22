package com.nuvio.tv.ui.screens.home

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.recyclerview.widget.RecyclerView

/**
 * Specialized [HorizontalGridView] for Modern Home rows with anchored floating focus ring,
 * 60fps vertical glide handoff, RTL directional awareness, and root-view focus isolation.
 */
class ModernInteropHorizontalGridView(context: Context) : HorizontalGridView(context) {
    var onNavigateDown: (() -> Boolean)? = null
    var onNavigateUp: (() -> Boolean)? = null
    var onNavigateBack: (() -> Boolean)? = null
    var onNavigateLeftOnFirstItem: (() -> Boolean)? = null
    var onVerticalHoldScroll: ((Int) -> Boolean)? = null
    var onVerticalHoldRelease: (() -> Unit)? = null
    var isVerticalGlideActive: (() -> Boolean)? = null
    var onHorizontalInterrupt: (() -> Unit)? = null
    var animationsEnabled: Boolean = true

    var floatingRingView: ModernRowFloatingFocusRingView? = null
    internal var configuredRowHeightPx: Int = Int.MIN_VALUE
    internal var configuredSpacingPx: Int = Int.MIN_VALUE
    internal var configuredStartPaddingPx: Int = Int.MIN_VALUE
    internal var configuredVerticalPaddingPx: Int = Int.MIN_VALUE
    internal var configuredAnimationsEnabled: Boolean? = null

    private var hadRowFocus: Boolean = false

    private val isOverlayRingActive: Boolean
        get() = isVerticalGlideActive?.invoke() == true

    private val globalFocusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        if (isOverlayRingActive) {
            if (floatingRingView?.visibility == VISIBLE) {
                floatingRingView?.hideRing(true)
            }
            hadRowFocus = hasFocus()
            return@OnGlobalFocusChangeListener
        }
        val currentlyHasFocus = hasFocus()
        if (!hadRowFocus && !currentlyHasFocus) {
            return@OnGlobalFocusChangeListener
        }
        val hasRowFocus = (currentlyHasFocus || (newFocus != null && isDescendant(newFocus))) && mayTakeFocus()
        hadRowFocus = hasRowFocus
        if (!hasRowFocus) {
            isHoldingHorizontalKey = false
            isHoldingVerticalKey = false
            pendingTargetPosition = RecyclerView.NO_POSITION
            floatingRingView?.hideRing(false)
        } else {
            val focusedChild = newFocus ?: findFocus()
            if (focusedChild != null && isDescendant(focusedChild)) {
                val holder = findContainingViewHolder(focusedChild) as? LeanbackCardViewHolder
                if (holder != null) {
                    if (floatingRingView?.currentTargetView !== holder.cardContainer) {
                        holder.updateFocusVisual(true)
                        floatingRingView?.moveToTarget(holder.cardContainer, holder.currentDominantColor, false)
                    }
                } else {
                    ensureChildFocusAndRing(animated = false)
                }
            } else if (currentlyHasFocus) {
                ensureChildFocusAndRing(animated = false)
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        hadRowFocus = hasFocus()
        viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hadRowFocus = false
        isHoldingVerticalKey = false
        viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusListener)
        floatingRingView?.hideRing(true)
    }

    private var clearFocusDepth: Int = 0

    override fun clearFocus() {
        clearFocusDepth++
        try {
            super.clearFocus()
            if (!hasFocus()) {
                floatingRingView?.hideRing(true)
            }
        } finally {
            clearFocusDepth--
        }
    }

    private var pendingIncomingFocus: Boolean = false

    var canTakeFocus: (() -> Boolean)? = null

    private var isNavigatingToSidebar: Boolean = false
    @Volatile
    var isDetaching: Boolean = false

    fun prepareForIncomingFocus() {
        isDetaching = false
        isNavigatingToSidebar = false
        pendingIncomingFocus = true
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    fun mayTakeFocus(): Boolean = !isDetaching && (pendingIncomingFocus || canTakeFocus?.invoke() != false)

    val hasPendingHorizontalNavigation: Boolean
        get() = isHoldingHorizontalKey ||
            pendingTargetPosition != RecyclerView.NO_POSITION ||
            scrollState != RecyclerView.SCROLL_STATE_IDLE

    val isSubmittingItems: Boolean
        get() = rowAdapter?.isSubmittingItems == true

    override fun clearChildFocus(child: View?) {
        val deliberateRowChange = isDetaching || isNavigatingToSidebar || canTakeFocus?.invoke() == false
        val shouldProtectFocus = isAttachedToWindow &&
            !deliberateRowChange &&
            (hasPendingHorizontalNavigation || isSubmittingItems)

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

    var isHoldingHorizontalKey: Boolean = false
        private set
    private var isHoldingVerticalKey: Boolean = false

    private var arrivalAt0Time = 0L
    private var arrivedAt0FromEnd = false
    private var hasReleasedKeyAfterArrivingAt0 = true
    private var lastHorizontalRepeatDispatchMs = 0L
    private var lastVerticalRepeatDispatchMs = 0L
    private var pendingTargetPosition = RecyclerView.NO_POSITION
    var restoredPosition: Int = 0

    private val rowAdapter: LeanbackRowAdapter?
        get() = adapter as? LeanbackRowAdapter

    fun notifyCardFocused(cardContainer: View, dominantColor: Int) {
        if (isOverlayRingActive || !mayTakeFocus()) {
            floatingRingView?.hideRing(true)
            return
        }
        floatingRingView?.moveToTarget(cardContainer, dominantColor, animationsEnabled)
    }

    fun prepareForRowChange() {
        isHoldingHorizontalKey = false
        isHoldingVerticalKey = false
        pendingIncomingFocus = false
        pendingTargetPosition = RecyclerView.NO_POSITION
        if (canTakeFocus?.invoke() == false) {
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }

    fun prepareForDetachment() {
        isDetaching = true
        prepareForRowChange()
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    fun ensureChildFocusAndRing(targetPos: Int = -1, animated: Boolean = false, retryCount: Int = 0) {
        if (!isAttachedToWindow || !mayTakeFocus()) return
        if (isOverlayRingActive) {
            floatingRingView?.hideRing(true)
            return
        }
        val pos = if (targetPos >= 0) targetPos else {
            if (pendingTargetPosition != RecyclerView.NO_POSITION) pendingTargetPosition
            else selectedPosition.coerceAtLeast(0)
        }
        val vh = findViewHolderForAdapterPosition(pos) as? LeanbackCardViewHolder
        if (vh != null) {
            if (!vh.itemView.hasFocus()) {
                vh.itemView.requestFocus()
            }
            vh.updateFocusVisual(true)
            floatingRingView?.moveToTarget(vh.cardContainer, vh.currentDominantColor, animated)
        } else if (retryCount < 8) {
            post {
                if (isAttachedToWindow && mayTakeFocus() && !isOverlayRingActive) {
                    ensureChildFocusAndRing(targetPos, animated, retryCount + 1)
                }
            }
        }
    }

    fun ensureTargetChildFocused(targetPos: Int, direction: Int, retryCount: Int = 0) {
        if (!isAttachedToWindow || !mayTakeFocus()) return
        if (isOverlayRingActive) {
            floatingRingView?.hideRing(true)
            return
        }
        val activeTarget = pendingTargetPosition
        if (activeTarget != RecyclerView.NO_POSITION && activeTarget != targetPos) return

        val vh = findViewHolderForAdapterPosition(targetPos) as? LeanbackCardViewHolder
        if (vh != null) {
            if (!vh.itemView.hasFocus()) {
                vh.itemView.requestFocus(direction)
            }
            vh.updateFocusVisual(true)
            floatingRingView?.moveToTarget(vh.cardContainer, vh.currentDominantColor, animationsEnabled)
        } else if (retryCount < 10) {
            post {
                if (isAttachedToWindow && mayTakeFocus() && !isOverlayRingActive) {
                    ensureTargetChildFocused(targetPos, direction, retryCount + 1)
                }
            }
        }
    }

    init {
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        itemAnimator = null
        layoutManager?.isItemPrefetchEnabled = true
        setHasFixedSize(true)

        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    isHoldingHorizontalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
                    post {
                        if (!isComputingLayout) {
                            ensureChildFocusAndRing(animated = false)
                            rowAdapter?.onScrollSettled()
                            floatingRingView?.onScrollSettled()
                        }
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                floatingRingView?.onParentScrolled(dx)
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
                    (child as? LeanbackCardViewHolder)?.let { vh ->
                        vh.updateFocusVisual(true)
                        if (!isOverlayRingActive) {
                            floatingRingView?.moveToTarget(vh.cardContainer, vh.currentDominantColor, animationsEnabled)
                        } else {
                            floatingRingView?.hideRing(true)
                        }
                    }
                    rowAdapter?.onFocusedPositionChanged(context, position)
                } else if (hasGridFocus) {
                    ensureChildFocusAndRing(targetPos = position, animated = animationsEnabled)
                    rowAdapter?.onFocusedPositionChanged(context, position)
                }

                val totalCount = rowAdapter?.itemCount ?: 0
                val loadMoreThreshold = maxOf(8, totalCount - 12)
                if (position >= loadMoreThreshold && totalCount > 0) {
                    post {
                        rowAdapter?.onLoadMore?.invoke()
                    }
                }
            }
        })
    }

    override fun getRootView(): View = this

    private val isBusyLayout: Boolean
        get() = isComputingLayout || isInLayout || !isAttachedToWindow

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        if (clearFocusDepth > 0) return false
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
        val vh = findViewHolderForAdapterPosition(targetPos) as? LeanbackCardViewHolder
        if (vh != null) {
            val granted = vh.itemView.requestFocus(direction)
            if (granted) {
                vh.updateFocusVisual(true)
                if (!isOverlayRingActive) {
                    floatingRingView?.moveToTarget(vh.cardContainer, vh.currentDominantColor, false)
                } else {
                    floatingRingView?.hideRing(true)
                }
                return true
            }
        }

        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        isFocusable = true
        isFocusableInTouchMode = true
        val granted = super.requestFocus(direction, previouslyFocusedRect)
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        setSelectedPosition(targetPos)
        ensureChildFocusAndRing(targetPos = targetPos, animated = false)
        post {
            if (isAttachedToWindow && mayTakeFocus()) {
                ensureChildFocusAndRing(targetPos = targetPos, animated = false)
            }
        }
        return granted
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        if (!mayTakeFocus()) {
            floatingRingView?.hideRing(true)
            return
        }
        if (isBusyLayout) {
            post { if (mayTakeFocus()) super.requestChildFocus(child, focused) }
            return
        }
        super.requestChildFocus(child, focused)
        val target = focused ?: child
        if (target != null && isDescendant(target)) {
            val holder = findContainingViewHolder(target) as? LeanbackCardViewHolder
            if (holder != null) {
                holder.updateFocusVisual(true)
                if (!isOverlayRingActive) {
                    floatingRingView?.moveToTarget(holder.cardContainer, holder.currentDominantColor, false)
                } else {
                    floatingRingView?.hideRing(true)
                }
            } else {
                ensureChildFocusAndRing(animated = false)
            }
        }
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (isBusyLayout) {
            return focused ?: findFocus() ?: this
        }
        val total = rowAdapter?.itemCount ?: 0
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
        if (result == null || result === this || !isDescendant(result)) {
            return focused?.takeIf { isDescendant(it) }
                ?: findFocus()?.takeIf { isDescendant(it) }
                ?: findViewHolderForAdapterPosition(currentPos)?.itemView
                ?: this
        }
        return result
    }

    private fun isDescendant(view: View): Boolean {
        var p = view.parent
        while (p != null) {
            if (p === this) return true
            p = p.parent
        }
        return false
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        try {
            super.onLayout(changed, l, t, r, b)
        } catch (e: Exception) {
            android.util.Log.e("LeanbackModernRow", "Layout error in modern row", e)
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
        return true
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus && !hasFocus()) {
            isHoldingHorizontalKey = false
            isHoldingVerticalKey = false
            pendingTargetPosition = RecyclerView.NO_POSITION
            floatingRingView?.hideRing(isOverlayRingActive)
        } else if (gainFocus || hasFocus()) {
            if (selectedPosition <= 0) {
                hasReleasedKeyAfterArrivingAt0 = true
                arrivedAt0FromEnd = false
            }
            ensureChildFocusAndRing(animated = false)
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
                    val total = rowAdapter?.itemCount ?: 0
                    val currentSelected = selectedPosition.coerceIn(0, (total - 1).coerceAtLeast(0))
                    val target = if (pendingTargetPosition != RecyclerView.NO_POSITION) {
                        pendingTargetPosition.coerceIn(0, (total - 1).coerceAtLeast(0))
                    } else {
                        currentSelected
                    }

                    if (event.keyCode == towardStartKey && target <= 0) {
                        hasReleasedKeyAfterArrivingAt0 = true
                        arrivedAt0FromEnd = false
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP -> {
                    if (isHoldingVerticalKey || isOverlayRingActive) {
                        isHoldingVerticalKey = false
                        onVerticalHoldRelease?.invoke()
                        return true
                    }
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = SystemClock.uptimeMillis()
            val total = rowAdapter?.itemCount ?: 0
            val focusedChild = findFocus()
            val holder = if (focusedChild != null) findContainingViewHolder(focusedChild) else null
            val resolvedHolderPos = holder?.bindingAdapterPosition?.takeIf { it != RecyclerView.NO_POSITION }
            val resolvedSelectedPos = selectedPosition.takeIf { it != RecyclerView.NO_POSITION }
            val currentPos = when {
                pendingTargetPosition != RecyclerView.NO_POSITION -> pendingTargetPosition
                resolvedHolderPos != null -> resolvedHolderPos
                resolvedSelectedPos != null -> resolvedSelectedPos
                else -> 0
            }
            when (event.keyCode) {
                towardStartKey -> {
                    isHoldingVerticalKey = false
                    if (isOverlayRingActive) {
                        onHorizontalInterrupt?.invoke()
                    }
                    if (currentPos <= 0 || total <= 0) {
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
                                floatingRingView?.hideRing(false)
                                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                clearFocus()
                                return true
                            }
                        }
                        return true
                    }

                    if (event.repeatCount > 0) {
                        isHoldingHorizontalKey = true
                        if (now - lastHorizontalRepeatDispatchMs < 55L) {
                            return true
                        }
                        lastHorizontalRepeatDispatchMs = now
                    } else {
                        lastHorizontalRepeatDispatchMs = now
                    }

                    val target = (currentPos - 1).coerceAtLeast(0)
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
                    isHoldingVerticalKey = false
                    if (isOverlayRingActive) {
                        onHorizontalInterrupt?.invoke()
                    }
                    if (total <= 0 || currentPos >= total - 1) {
                        val lastIdx = (total - 1).coerceAtLeast(0)
                        pendingTargetPosition = lastIdx
                        if (animationsEnabled) {
                            setSelectedPositionSmooth(lastIdx)
                        } else {
                            setSelectedPosition(lastIdx)
                        }
                        return true
                    }

                    if (event.repeatCount > 0) {
                        isHoldingHorizontalKey = true
                        if (now - lastHorizontalRepeatDispatchMs < 55L) {
                            return true
                        }
                        lastHorizontalRepeatDispatchMs = now
                    } else {
                        lastHorizontalRepeatDispatchMs = now
                    }

                    val target = (currentPos + 1).coerceAtMost((total - 1).coerceAtLeast(0))
                    pendingTargetPosition = target
                    if (target >= (total - 12).coerceAtLeast(0) && total > 0) {
                        rowAdapter?.onLoadMore?.invoke()
                    }
                    rowAdapter?.onFocusedPositionChanged(context, target)
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
                    if (event.repeatCount > 0) {
                        isHoldingVerticalKey = true
                        if (onVerticalHoldScroll?.invoke(1) == true) return true
                        if (now - lastVerticalRepeatDispatchMs < 115L) {
                            return true
                        }
                        lastVerticalRepeatDispatchMs = now
                    } else {
                        isHoldingVerticalKey = false
                        onVerticalHoldRelease?.invoke()
                        lastVerticalRepeatDispatchMs = now
                    }
                    if (onNavigateDown?.invoke() == true) return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    isHoldingHorizontalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
                    if (event.repeatCount > 0) {
                        isHoldingVerticalKey = true
                        if (onVerticalHoldScroll?.invoke(-1) == true) return true
                        if (now - lastVerticalRepeatDispatchMs < 115L) {
                            return true
                        }
                        lastVerticalRepeatDispatchMs = now
                    } else {
                        isHoldingVerticalKey = false
                        onVerticalHoldRelease?.invoke()
                        lastVerticalRepeatDispatchMs = now
                    }
                    if (onNavigateUp?.invoke() == true) return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    isHoldingHorizontalKey = false
                    isHoldingVerticalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
                    if (onNavigateBack?.invoke() == true) return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
