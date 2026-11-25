package com.nuvio.tv.ui.screens.home

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.OnChildViewHolderSelectedListener
import androidx.recyclerview.widget.RecyclerView
import com.nuvio.tv.ui.leanback.core.LeanbackCardExpandSpec

internal const val MAX_HORIZONTAL_LOOKAHEAD = 2
private const val HORIZONTAL_REPEAT_INTERVAL_MS = 85L
private const val HORIZONTAL_HOLD_SCROLL_DURATION_MS = 105
private val HORIZONTAL_HOLD_LINEAR_INTERPOLATOR = LinearInterpolator()

internal fun computeNextHorizontalTargetPosition(
    anchorPos: Int,
    pendingTargetPos: Int,
    step: Int,
    totalCount: Int,
    isScrolling: Boolean,
    maxLookahead: Int = MAX_HORIZONTAL_LOOKAHEAD
): Int {
    if (totalCount <= 0) return 0
    val lastIdx = totalCount - 1
    val safeAnchor = anchorPos.coerceIn(0, lastIdx)
    val basePos = if (pendingTargetPos != RecyclerView.NO_POSITION) {
        pendingTargetPos.coerceIn(0, lastIdx)
    } else {
        safeAnchor
    }
    val rawTarget = (basePos + step).coerceIn(0, lastIdx)
    if (!isScrolling || maxLookahead <= 0) return rawTarget
    return if (step > 0) {
        val maxAllowed = (safeAnchor + maxLookahead).coerceAtMost(lastIdx)
        rawTarget.coerceAtMost(maxAllowed)
    } else {
        val minAllowed = (safeAnchor - maxLookahead).coerceAtLeast(0)
        rawTarget.coerceAtLeast(minAllowed)
    }
}

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
    var onBackdropInteraction: (() -> Unit)? = null
    var animationsEnabled: Boolean = true

    private val expandBoundsTempRect = Rect()

    var floatingRingView: ModernRowFloatingFocusRingView? = null
    internal var configuredRowHeightPx: Int = Int.MIN_VALUE
    internal var configuredSpacingPx: Int = Int.MIN_VALUE
    internal var configuredStartPaddingPx: Int = Int.MIN_VALUE
    internal var configuredVerticalPaddingPx: Int = Int.MIN_VALUE
    internal var configuredAnimationsEnabled: Boolean? = null
    internal var configuredBackdropExpandEnabled: Boolean? = null

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
            val wasHolding = isHoldingHorizontalKey
            isHoldingHorizontalKey = false
            isHoldingVerticalKey = false
            pendingTargetPosition = RecyclerView.NO_POSITION
            if (wasHolding) {
                rowAdapter?.flushPendingFocusedItem()
            }
            floatingRingView?.hideRing(false)
        } else {
            val focusedChild = newFocus ?: findFocus()
            if (focusedChild != null && isDescendant(focusedChild)) {
                val holder = findContainingViewHolder(focusedChild) as? LeanbackCardViewHolder
                if (holder != null) {
                    if (floatingRingView?.currentTargetView !== holder.cardContainer) {
                        holder.updateFocusVisual(true)
                        floatingRingView?.moveToTarget(
                            target = holder.cardContainer,
                            dominantColor = holder.currentDominantColor,
                            animated = false,
                            keepColorIfVisible = isHoldingHorizontalKey,
                            isExpandedCard = holder.isBackdropExpanded,
                            explicitCardWidthPx = holder.currentEffectiveCardWidthPx(),
                            explicitCardHeightPx = holder.currentEffectiveCardHeightPx(),
                            useLivePosition = rowAdapter?.hasAnimatingWidthCard == true
                        )
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

    fun notifyCardFocused(
        cardContainer: View,
        dominantColor: Int,
        isExpandedCard: Boolean = false,
        explicitCardWidthPx: Int? = null,
        explicitCardHeightPx: Int? = null
    ) {
        if (isOverlayRingActive || !mayTakeFocus()) {
            floatingRingView?.hideRing(true)
            return
        }
        floatingRingView?.moveToTarget(
            target = cardContainer,
            dominantColor = dominantColor,
            animated = animationsEnabled,
            keepColorIfVisible = isHoldingHorizontalKey,
            isExpandedCard = isExpandedCard,
            explicitCardWidthPx = explicitCardWidthPx,
            explicitCardHeightPx = explicitCardHeightPx,
            useLivePosition = rowAdapter?.hasAnimatingWidthCard == true
        )
    }

    fun notifyFocusedCardBoundsChanged(
        cardContainer: View,
        isAnimatingWidth: Boolean,
        isExpandedCard: Boolean,
        dominantColor: Int,
        explicitCardWidthPx: Int? = null,
        explicitCardHeightPx: Int? = null,
        bufferCardWidthPx: Int? = null
    ) {
        if (isOverlayRingActive || !mayTakeFocus()) {
            floatingRingView?.hideRing(true)
            return
        }
        floatingRingView?.syncToTargetBounds(
            target = cardContainer,
            isAnimatingWidth = isAnimatingWidth,
            isExpandedCard = isExpandedCard,
            dominantColor = dominantColor,
            explicitCardWidthPx = explicitCardWidthPx,
            explicitCardHeightPx = explicitCardHeightPx,
            bufferCardWidthPx = bufferCardWidthPx
        )
        if (!isAnimatingWidth && isExpandedCard && isAttachedToWindow && cardContainer.isAttachedToWindow && width > 0) {
            val resolvedW = explicitCardWidthPx ?: cardContainer.width
            val resolvedH = explicitCardHeightPx ?: cardContainer.height
            expandBoundsTempRect.set(0, 0, resolvedW, resolvedH)
            val ok = try {
                offsetDescendantRectToMyCoords(cardContainer, expandBoundsTempRect)
                true
            } catch (_: Exception) {
                false
            }
            if (ok) {
                val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
                if (!isRtl) {
                    val maxRight = width - paddingRight
                    val overflow = expandBoundsTempRect.right - maxRight
                    if (overflow > 0) {
                        smoothScrollBy(overflow, 0)
                    }
                } else {
                    val minLeft = paddingLeft
                    val overflow = expandBoundsTempRect.left - minLeft
                    if (overflow < 0) {
                        smoothScrollBy(overflow, 0)
                    }
                }
            }
        }
    }

    fun onSiblingCardWidthAnimationFinished() {
        if (!isAttachedToWindow || isOverlayRingActive || !mayTakeFocus()) return
        if (scrollState == RecyclerView.SCROLL_STATE_IDLE) {
            post {
                if (isAttachedToWindow && !isOverlayRingActive && !isComputingLayout) {
                    floatingRingView?.onScrollSettled()
                }
            }
        }
    }

    fun prepareForRowChange() {
        rowAdapter?.collapseExpandedCardImmediate()
        val wasHolding = isHoldingHorizontalKey
        isHoldingHorizontalKey = false
        isHoldingVerticalKey = false
        pendingIncomingFocus = false
        pendingTargetPosition = RecyclerView.NO_POSITION
        if (wasHolding) {
            rowAdapter?.flushPendingFocusedItem()
        }
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
            floatingRingView?.moveToTarget(
                target = vh.cardContainer,
                dominantColor = vh.currentDominantColor,
                animated = animated,
                keepColorIfVisible = isHoldingHorizontalKey,
                isExpandedCard = vh.isBackdropExpanded,
                explicitCardWidthPx = vh.currentEffectiveCardWidthPx(),
                explicitCardHeightPx = vh.currentEffectiveCardHeightPx(),
                useLivePosition = rowAdapter?.hasAnimatingWidthCard == true
            )
        } else if (retryCount < 8 && !isHoldingHorizontalKey) {
            post {
                if (isAttachedToWindow && mayTakeFocus() && !isOverlayRingActive && !isHoldingHorizontalKey) {
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
            floatingRingView?.moveToTarget(
                target = vh.cardContainer,
                dominantColor = vh.currentDominantColor,
                animated = animationsEnabled,
                keepColorIfVisible = isHoldingHorizontalKey,
                isExpandedCard = vh.isBackdropExpanded,
                explicitCardWidthPx = vh.currentEffectiveCardWidthPx(),
                explicitCardHeightPx = vh.currentEffectiveCardHeightPx(),
                useLivePosition = rowAdapter?.hasAnimatingWidthCard == true
            )
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
                    rowAdapter?.flushPendingFocusedItem()
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
                val hasPending = pendingTargetPosition != RecyclerView.NO_POSITION
                val isIntermediateWhilePending = hasPending && position != pendingTargetPosition
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
                    isHoldingHorizontalKey || hasPending) && mayTakeFocus()
                if (child != null && hasGridFocus) {
                    if (!isIntermediateWhilePending && !child.itemView.hasFocus()) {
                        child.itemView.requestFocus()
                    }
                    (child as? LeanbackCardViewHolder)?.let { vh ->
                        vh.updateFocusVisual(true)
                        if (!isOverlayRingActive) {
                            floatingRingView?.moveToTarget(
                                target = vh.cardContainer,
                                dominantColor = vh.currentDominantColor,
                                animated = animationsEnabled,
                                keepColorIfVisible = isHoldingHorizontalKey,
                                isExpandedCard = vh.isBackdropExpanded,
                                explicitCardWidthPx = vh.currentEffectiveCardWidthPx(),
                                explicitCardHeightPx = vh.currentEffectiveCardHeightPx(),
                                useLivePosition = rowAdapter?.hasAnimatingWidthCard == true
                            )
                        } else {
                            floatingRingView?.hideRing(true)
                        }
                    }
                    rowAdapter?.onFocusedPositionChanged(context, position)
                } else if (hasGridFocus) {
                    if (!isHoldingHorizontalKey && !isIntermediateWhilePending) {
                        ensureChildFocusAndRing(targetPos = position, animated = animationsEnabled)
                    }
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
                    floatingRingView?.moveToTarget(
                        target = vh.cardContainer,
                        dominantColor = vh.currentDominantColor,
                        animated = false,
                        keepColorIfVisible = isHoldingHorizontalKey,
                        isExpandedCard = vh.isBackdropExpanded,
                        explicitCardWidthPx = vh.currentEffectiveCardWidthPx(),
                        explicitCardHeightPx = vh.currentEffectiveCardHeightPx(),
                        useLivePosition = rowAdapter?.hasAnimatingWidthCard == true
                    )
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
            post {
                if (isAttachedToWindow && mayTakeFocus() && child != null && isDescendant(child)) {
                    super.requestChildFocus(child, focused)
                }
            }
            return
        }
        super.requestChildFocus(child, focused)
        val target = focused ?: child
        if (target != null && isDescendant(target)) {
            val holder = findContainingViewHolder(target) as? LeanbackCardViewHolder
            if (holder != null) {
                holder.updateFocusVisual(true)
                if (!isOverlayRingActive) {
                    floatingRingView?.moveToTarget(
                        target = holder.cardContainer,
                        dominantColor = holder.currentDominantColor,
                        animated = false,
                        keepColorIfVisible = isHoldingHorizontalKey,
                        isExpandedCard = holder.isBackdropExpanded,
                        explicitCardWidthPx = holder.currentEffectiveCardWidthPx(),
                        explicitCardHeightPx = holder.currentEffectiveCardHeightPx(),
                        useLivePosition = rowAdapter?.hasAnimatingWidthCard == true
                    )
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

    override fun smoothScrollBy(dx: Int, dy: Int) {
        if (isHoldingHorizontalKey && dx != 0 && dy == 0) {
            super.smoothScrollBy(dx, dy, HORIZONTAL_HOLD_LINEAR_INTERPOLATOR, HORIZONTAL_HOLD_SCROLL_DURATION_MS)
        } else {
            super.smoothScrollBy(dx, dy)
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus && !hasFocus()) {
            val wasHolding = isHoldingHorizontalKey
            isHoldingHorizontalKey = false
            isHoldingVerticalKey = false
            pendingTargetPosition = RecyclerView.NO_POSITION
            if (wasHolding) {
                rowAdapter?.flushPendingFocusedItem()
            }
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
                    pendingTargetPosition = RecyclerView.NO_POSITION

                    if (event.keyCode == towardStartKey && target <= 0) {
                        hasReleasedKeyAfterArrivingAt0 = true
                        arrivedAt0FromEnd = false
                    }

                    if (selectedPosition != target && total > 0) {
                        if (animationsEnabled) {
                            setSelectedPositionSmooth(target)
                        } else {
                            setSelectedPosition(target)
                        }
                    }

                    rowAdapter?.flushPendingFocusedItem()
                    if (scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                        ensureChildFocusAndRing(targetPos = target, animated = false)
                        rowAdapter?.onScrollSettled()
                        floatingRingView?.onScrollSettled()
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
            if (LeanbackCardExpandSpec.shouldResetExpandTimerOnKey(event.keyCode)) {
                val adapterRef = rowAdapter
                if (adapterRef?.hasExpandedCard == true || (event.repeatCount > 0 && adapterRef?.hasAnimatingWidthCard == true)) {
                    val shouldAnimateCollapse = LeanbackCardExpandSpec.shouldAnimateCollapseOnKey(
                        keyCode = event.keyCode,
                        repeatCount = event.repeatCount,
                        animationsEnabled = animationsEnabled
                    )
                    adapterRef.collapseExpandedCard(animated = shouldAnimateCollapse)
                }
                onBackdropInteraction?.invoke()
            }
            val now = SystemClock.uptimeMillis()
            val total = rowAdapter?.itemCount ?: 0
            val focusedChild = findFocus()
            val holder = if (focusedChild != null) findContainingViewHolder(focusedChild) else null
            val resolvedHolderPos = holder?.bindingAdapterPosition?.takeIf { it != RecyclerView.NO_POSITION }
            val resolvedSelectedPos = selectedPosition.takeIf { it != RecyclerView.NO_POSITION }
            val anchorPos = if (scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                resolvedHolderPos ?: resolvedSelectedPos ?: 0
            } else {
                resolvedSelectedPos ?: resolvedHolderPos ?: 0
            }
            val currentPos = if (pendingTargetPosition != RecyclerView.NO_POSITION) {
                pendingTargetPosition
            } else {
                anchorPos
            }
            when (event.keyCode) {
                towardStartKey -> {
                    isHoldingVerticalKey = false
                    if (isOverlayRingActive) {
                        onHorizontalInterrupt?.invoke()
                    }
                    if (currentPos <= 0 || total <= 0) {
                        pendingTargetPosition = 0
                        if (selectedPosition != 0 && total > 0) {
                            if (animationsEnabled) {
                                setSelectedPositionSmooth(0)
                            } else {
                                setSelectedPosition(0)
                            }
                        }
                        if (isHoldingHorizontalKey) {
                            rowAdapter?.flushPendingFocusedItem()
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
                        if (now - lastHorizontalRepeatDispatchMs < HORIZONTAL_REPEAT_INTERVAL_MS) {
                            return true
                        }
                        lastHorizontalRepeatDispatchMs = now
                    } else {
                        isHoldingHorizontalKey = false
                        lastHorizontalRepeatDispatchMs = now
                    }

                    val isScrolling = scrollState != RecyclerView.SCROLL_STATE_IDLE
                    val target = computeNextHorizontalTargetPosition(
                        anchorPos = anchorPos,
                        pendingTargetPos = pendingTargetPosition,
                        step = -1,
                        totalCount = total,
                        isScrolling = isScrolling
                    )
                    if (isScrolling && target == pendingTargetPosition) {
                        return true
                    }
                    pendingTargetPosition = target
                    if (!animationsEnabled) {
                        val nextVh = findViewHolderForAdapterPosition(target)
                        setSelectedPosition(target)
                        if (nextVh != null) {
                            nextVh.itemView.requestFocus(towardStartFocusDir)
                        } else {
                            ensureTargetChildFocused(target, towardStartFocusDir)
                        }
                        return true
                    }

                    setSelectedPositionSmooth(target)
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
                        if (selectedPosition != lastIdx && total > 0) {
                            if (animationsEnabled) {
                                setSelectedPositionSmooth(lastIdx)
                            } else {
                                setSelectedPosition(lastIdx)
                            }
                        }
                        if (isHoldingHorizontalKey) {
                            rowAdapter?.flushPendingFocusedItem()
                        }
                        return true
                    }

                    if (event.repeatCount > 0) {
                        isHoldingHorizontalKey = true
                        if (now - lastHorizontalRepeatDispatchMs < HORIZONTAL_REPEAT_INTERVAL_MS) {
                            return true
                        }
                        lastHorizontalRepeatDispatchMs = now
                    } else {
                        isHoldingHorizontalKey = false
                        lastHorizontalRepeatDispatchMs = now
                    }

                    val isScrolling = scrollState != RecyclerView.SCROLL_STATE_IDLE
                    val target = computeNextHorizontalTargetPosition(
                        anchorPos = anchorPos,
                        pendingTargetPos = pendingTargetPosition,
                        step = 1,
                        totalCount = total,
                        isScrolling = isScrolling
                    )
                    if (target >= (total - 12).coerceAtLeast(0) && total > 0) {
                        rowAdapter?.onLoadMore?.invoke()
                    }
                    if (isScrolling && target == pendingTargetPosition) {
                        return true
                    }
                    pendingTargetPosition = target
                    if (!animationsEnabled) {
                        val nextVh = findViewHolderForAdapterPosition(target)
                        setSelectedPosition(target)
                        if (nextVh != null) {
                            nextVh.itemView.requestFocus(towardEndFocusDir)
                        } else {
                            ensureTargetChildFocused(target, towardEndFocusDir)
                        }
                        return true
                    }

                    setSelectedPositionSmooth(target)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val wasHoldingHorizontal = isHoldingHorizontalKey
                    isHoldingHorizontalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
                    if (wasHoldingHorizontal) {
                        rowAdapter?.flushPendingFocusedItem()
                    }
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
                    val wasHoldingHorizontal = isHoldingHorizontalKey
                    isHoldingHorizontalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
                    if (wasHoldingHorizontal) {
                        rowAdapter?.flushPendingFocusedItem()
                    }
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
                    val wasHoldingHorizontal = isHoldingHorizontalKey
                    isHoldingHorizontalKey = false
                    isHoldingVerticalKey = false
                    pendingTargetPosition = RecyclerView.NO_POSITION
                    if (wasHoldingHorizontal) {
                        rowAdapter?.flushPendingFocusedItem()
                    }
                    if (onNavigateBack?.invoke() == true) return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_MENU -> {
                    if (isHoldingHorizontalKey) {
                        isHoldingHorizontalKey = false
                        pendingTargetPosition = RecyclerView.NO_POSITION
                        rowAdapter?.flushPendingFocusedItem()
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
