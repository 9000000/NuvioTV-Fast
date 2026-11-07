package com.nuvio.tv.ui.leanback.adapter

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.nuvio.tv.ui.leanback.holder.LeanbackAvatarViewHolder
import com.nuvio.tv.ui.leanback.holder.LeanbackBaseViewHolder
import com.nuvio.tv.ui.leanback.holder.LeanbackEpisodeViewHolder
import com.nuvio.tv.ui.leanback.holder.LeanbackPosterViewHolder
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.leanback.pool.LeanbackPoolManager

/**
 * Universal polymorphic RecyclerView Adapter for all Android Leanback Cards.
 * Supports Poster, Episode, and Avatar items with safe notifyDataSetChanged execution,
 * dynamic dimension updating, and full focus lifecycle integration.
 */
class LeanbackUniversalAdapter(
    var cardWidthPx: Int,
    var cardHeightPx: Int,
    var cornerRadiusPx: Float,
    var showLabels: Boolean,
    var animationsEnabled: () -> Boolean,
    var onItemFocused: (Int, LeanbackCardItem) -> Unit,
    var onItemClick: (LeanbackCardItem) -> Unit,
    var onItemLongClick: (LeanbackCardItem) -> Unit,
    var onLoadMore: () -> Unit,
    var onNavigateDown: () -> Boolean = { false },
    var onNavigateUp: () -> Boolean = { false },
    var onNavigateLeft: () -> Boolean = { false },
    var onNavigateRight: () -> Boolean = { false },
    var onNavigateBack: () -> Boolean = { false }
) : RecyclerView.Adapter<LeanbackBaseViewHolder>() {

    private var items: List<LeanbackCardItem> = emptyList()
    private var pendingItems: List<LeanbackCardItem>? = null
    var attachedRecyclerView: RecyclerView? = null

    val borderWidthPx: Int = 2
    val glowMarginPx: Int = 16

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        attachedRecyclerView = null
    }

    fun getItem(position: Int): LeanbackCardItem? = items.getOrNull(position)

    fun updateConfig(
        newWidthPx: Int,
        newHeightPx: Int,
        newRadiusPx: Float,
        newShowLabels: Boolean
    ) {
        val changed = cardWidthPx != newWidthPx ||
            cardHeightPx != newHeightPx ||
            cornerRadiusPx != newRadiusPx ||
            showLabels != newShowLabels
        if (changed) {
            cardWidthPx = newWidthPx
            cardHeightPx = newHeightPx
            cornerRadiusPx = newRadiusPx
            showLabels = newShowLabels
            safeNotifyDataSetChanged()
        }
    }

    fun submitItems(newItems: List<LeanbackCardItem>) {
        if (items == newItems && pendingItems == null) return
        pendingItems = newItems

        val rv = attachedRecyclerView
        if (rv == null) {
            applyItemsUpdate(newItems)
            return
        }

        if (rv.isComputingLayout || !rv.isAttachedToWindow || rv.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
            rv.post {
                val pending = pendingItems ?: return@post
                if (rv.isAttachedToWindow && !rv.isComputingLayout && rv.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                    applyItemsUpdate(pending)
                } else {
                    rv.post {
                        val retryPending = pendingItems ?: return@post
                        applyItemsUpdate(retryPending)
                    }
                }
            }
            return
        }

        applyItemsUpdate(newItems)
    }

    private fun applyItemsUpdate(newItems: List<LeanbackCardItem>) {
        val oldItems = items
        val oldSize = oldItems.size
        val newSize = newItems.size
        val isAppendOnly = oldSize > 0 && newSize > oldSize && itemsMatchPrefix(oldItems, newItems, oldSize)

        items = newItems
        pendingItems = null

        val rv = attachedRecyclerView
        if (rv == null || !rv.isAttachedToWindow) {
            notifyDataSetChanged()
            return
        }

        if (isAppendOnly) {
            try {
                notifyItemRangeInserted(oldSize, newSize - oldSize)
            } catch (_: Exception) {
                safeNotifyDataSetChanged()
            }
        } else {
            safeNotifyDataSetChanged()
        }
    }

    private fun itemsMatchPrefix(oldItems: List<LeanbackCardItem>, newItems: List<LeanbackCardItem>, count: Int): Boolean {
        for (i in 0 until count) {
            if (oldItems[i].key != newItems[i].key) return false
        }
        return true
    }

    fun safeNotifyDataSetChanged() {
        val rv = attachedRecyclerView
        if (rv != null && (rv.isComputingLayout || !rv.isAttachedToWindow)) {
            rv.post {
                if (attachedRecyclerView != null && !rv.isComputingLayout && rv.isAttachedToWindow) {
                    try {
                        notifyDataSetChanged()
                    } catch (_: IllegalStateException) {
                        rv.post { runCatching { notifyDataSetChanged() } }
                    }
                }
            }
            return
        }
        try {
            notifyDataSetChanged()
        } catch (_: IllegalStateException) {
            rv?.post { runCatching { notifyDataSetChanged() } }
        }
    }

    fun onScrollSettled() {
        val rv = attachedRecyclerView ?: return
        val count = rv.childCount
        for (i in 0 until count) {
            val child = rv.getChildAt(i)
            val vh = rv.getChildViewHolder(child) as? LeanbackBaseViewHolder
            if (vh != null && vh.itemView.hasFocus()) {
                vh.updateFocusVisual(true)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is LeanbackCardItem.Poster -> LeanbackPoolManager.VIEW_TYPE_POSTER
            is LeanbackCardItem.Episode -> LeanbackPoolManager.VIEW_TYPE_EPISODE
            is LeanbackCardItem.Avatar -> LeanbackPoolManager.VIEW_TYPE_AVATAR
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeanbackBaseViewHolder {
        val parentLayout = (parent as? LinearLayout) ?: LinearLayout(parent.context)
        val dp = parent.context.resources.displayMetrics.density
        val glowSpreadDp = 18f
        val glowSpreadPx = glowSpreadDp * dp
        val dynamicGlowMarginPx = (glowSpreadPx * 1.35f).toInt()
        val dynamicBorderWidthPx = (2f * dp).toInt().coerceAtLeast(2)

        return when (viewType) {
            LeanbackPoolManager.VIEW_TYPE_EPISODE -> {
                LeanbackEpisodeViewHolder.create(
                    parent = parentLayout,
                    cardWidthPx = cardWidthPx,
                    cardHeightPx = cardHeightPx,
                    cornerRadiusPx = cornerRadiusPx,
                    borderWidthPx = dynamicBorderWidthPx,
                    glowMarginPx = dynamicGlowMarginPx,
                    showLabels = showLabels,
                    adapter = this
                )
            }
            LeanbackPoolManager.VIEW_TYPE_AVATAR -> {
                LeanbackAvatarViewHolder.create(
                    parent = parentLayout,
                    cardWidthPx = cardWidthPx,
                    cardHeightPx = cardHeightPx,
                    cornerRadiusPx = cornerRadiusPx,
                    borderWidthPx = dynamicBorderWidthPx,
                    glowMarginPx = dynamicGlowMarginPx,
                    showLabels = showLabels,
                    adapter = this
                )
            }
            else -> {
                LeanbackPosterViewHolder.create(
                    parent = parentLayout,
                    cardWidthPx = cardWidthPx,
                    cardHeightPx = cardHeightPx,
                    cornerRadiusPx = this@LeanbackUniversalAdapter.cornerRadiusPx,
                    borderWidthPx = dynamicBorderWidthPx,
                    glowMarginPx = dynamicGlowMarginPx,
                    showLabels = showLabels,
                    adapter = this
                )
            }
        }
    }

    override fun onBindViewHolder(holder: LeanbackBaseViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, cardWidthPx, cardHeightPx, cornerRadiusPx, showLabels)
    }

    override fun onViewRecycled(holder: LeanbackBaseViewHolder) {
        holder.unbind()
    }
}

