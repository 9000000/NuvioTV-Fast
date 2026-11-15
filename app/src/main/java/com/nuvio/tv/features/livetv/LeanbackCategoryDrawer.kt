package com.nuvio.tv.features.livetv

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.nuvio.tv.ui.leanback.container.InteropVerticalGridView
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Android Leanback Native Category Drawer for Live TV Guide.
 * 60fps locked hardware-accelerated drawer navigation with instant focus response.
 */
@Composable
internal fun LeanbackCategoryDrawer(
    groups: List<ChannelGroup>,
    drawerIndex: Int,
    selectedGroupId: String,
    onCategoryFocused: (Int, ChannelGroup) -> Unit,
    onSelectGroup: (Int) -> Unit,
    onNavigateRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColorArgb = NuvioTheme.colors.Primary.toArgb()
    val latestOnCategoryFocused by rememberUpdatedState(onCategoryFocused)
    val latestOnSelectGroup by rememberUpdatedState(onSelectGroup)
    val latestOnNavigateRight by rememberUpdatedState(onNavigateRight)

    var gridRef by remember { mutableStateOf<InteropVerticalGridView?>(null) }

    val adapter = remember {
        LeanbackCategoryAdapter(
            primaryColorArgb = primaryColorArgb,
            onFocused = { idx, group -> latestOnCategoryFocused(idx, group) },
            onSelected = { idx -> latestOnSelectGroup(idx) },
            onNavigateRight = { latestOnNavigateRight() }
        )
    }

    LaunchedEffect(primaryColorArgb) {
        adapter.primaryColorArgb = primaryColorArgb
    }

    LaunchedEffect(groups) {
        adapter.submitGroups(groups, drawerIndex, selectedGroupId)
    }

    LaunchedEffect(drawerIndex, selectedGroupId) {
        adapter.updateSelection(drawerIndex, selectedGroupId)
        gridRef?.let { grid ->
            if (grid.isAttachedToWindow && drawerIndex in groups.indices) {
                if (grid.selectedPosition != drawerIndex) {
                    grid.setSelectedPositionSmooth(drawerIndex)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .width(240.dp)
            .fillMaxHeight()
            .background(Color(0xFF141418))
            .padding(top = 12.dp, bottom = 12.dp, start = 10.dp, end = 10.dp)
    ) {
        LiveText(
            text = "DANH MỤC",
            size = 13.sp,
            weight = FontWeight.Bold,
            color = Color.LightGray,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                InteropVerticalGridView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.numColumnsCount = 1
                    setNumColumns(1)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setVerticalSpacing((4f * context.resources.displayMetrics.density).toInt())
                    clipToPadding = false
                    clipChildren = false
                    setPadding(0, (6f * context.resources.displayMetrics.density).toInt(), 0, (16f * context.resources.displayMetrics.density).toInt())
                    setHasFixedSize(true)
                    setItemViewCacheSize(10)
                    this.adapter = adapter
                    gridRef = this

                    if (drawerIndex in groups.indices) {
                        post {
                            if (isAttachedToWindow) {
                                setSelectedPosition(drawerIndex)
                            }
                        }
                    }
                }
            },
            update = { grid ->
                gridRef = grid
                grid.onNavigateRightOnLastColumn = {
                    latestOnNavigateRight()
                    true
                }
            }
        )
    }
}

/**
 * Native RecyclerView Adapter for Category Drawer items.
 */
private class LeanbackCategoryAdapter(
    var primaryColorArgb: Int,
    val onFocused: (Int, ChannelGroup) -> Unit,
    val onSelected: (Int) -> Unit,
    val onNavigateRight: () -> Unit
) : RecyclerView.Adapter<LeanbackCategoryViewHolder>() {

    private var items: List<ChannelGroup> = emptyList()
    var currentFocusedIndex: Int = 0
    var currentSelectedGroupId: String = ""

    fun submitGroups(newItems: List<ChannelGroup>, focusedIdx: Int, selectedId: String) {
        val oldItems = items
        items = newItems
        currentFocusedIndex = focusedIdx
        currentSelectedGroupId = selectedId

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldItems.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition].id == newItems[newItemPosition].id
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean =
                oldItems[oldItemPosition] == newItems[newItemPosition]
        })
        diff.dispatchUpdatesTo(this)
    }

    fun updateSelection(focusedIdx: Int, selectedId: String) {
        val prevFocused = currentFocusedIndex
        val prevSelected = currentSelectedGroupId
        currentFocusedIndex = focusedIdx
        currentSelectedGroupId = selectedId

        if (prevFocused != focusedIdx) {
            notifyItemChanged(prevFocused)
            notifyItemChanged(focusedIdx)
        }
        if (prevSelected != selectedId) {
            val oldIdx = items.indexOfFirst { it.id == prevSelected }
            val newIdx = items.indexOfFirst { it.id == selectedId }
            if (oldIdx >= 0) notifyItemChanged(oldIdx)
            if (newIdx >= 0) notifyItemChanged(newIdx)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeanbackCategoryViewHolder {
        return LeanbackCategoryViewHolder.create(parent, primaryColorArgb, onFocused, onSelected, onNavigateRight)
    }

    override fun onBindViewHolder(holder: LeanbackCategoryViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, position, position == currentFocusedIndex, item.id == currentSelectedGroupId, primaryColorArgb)
    }
}

/**
 * Native ViewHolder for each Category Drawer item.
 */
private class LeanbackCategoryViewHolder(
    val root: LinearLayout,
    val nameText: TextView,
    val countText: TextView,
    val onFocused: (Int, ChannelGroup) -> Unit,
    val onSelected: (Int) -> Unit,
    val onNavigateRight: () -> Unit
) : RecyclerView.ViewHolder(root) {

    private var currentItem: ChannelGroup? = null
    private var currentPosition: Int = -1

    init {
        root.isFocusable = false
        root.isFocusableInTouchMode = false

        root.setOnClickListener {
            if (currentPosition >= 0) {
                onSelected(currentPosition)
            }
        }
    }

    fun bind(item: ChannelGroup, position: Int, isFocused: Boolean, isSelected: Boolean, primaryColorArgb: Int) {
        currentItem = item
        currentPosition = position
        nameText.text = item.name
        countText.text = "${item.channelCount}"
        updateVisual(isFocused, isSelected, primaryColorArgb)
    }

    private fun updateVisual(hasFocus: Boolean, isSelected: Boolean, primaryColorArgb: Int) {
        val dp = root.context.resources.displayMetrics.density
        val radius = 8f * dp

        if (hasFocus) {
            val bg = GradientDrawable().apply {
                setColor(if (primaryColorArgb != 0) primaryColorArgb else AndroidColor.parseColor("#E50914"))
                cornerRadius = radius
            }
            root.background = bg
            nameText.setTextColor(AndroidColor.WHITE)
            nameText.setTypeface(null, android.graphics.Typeface.BOLD)
            countText.setTextColor(AndroidColor.parseColor("#E6FFFFFF"))
        } else if (isSelected) {
            val bg = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#26FFFFFF"))
                cornerRadius = radius
            }
            root.background = bg
            nameText.setTextColor(AndroidColor.WHITE)
            nameText.setTypeface(null, android.graphics.Typeface.NORMAL)
            countText.setTextColor(AndroidColor.parseColor("#B0B0B8"))
        } else {
            root.background = null
            nameText.setTextColor(AndroidColor.parseColor("#D4D4D8"))
            nameText.setTypeface(null, android.graphics.Typeface.NORMAL)
            countText.setTextColor(AndroidColor.parseColor("#71717A"))
        }
    }

    companion object {
        fun create(
            parent: ViewGroup,
            primaryColorArgb: Int,
            onFocused: (Int, ChannelGroup) -> Unit,
            onSelected: (Int) -> Unit,
            onNavigateRight: () -> Unit
        ): LeanbackCategoryViewHolder {
            val context = parent.context
            val dp = context.resources.displayMetrics.density

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (42f * dp).toInt()
                ).apply {
                    val marginV = (2f * dp).toInt()
                    setMargins(0, marginV, 0, marginV)
                }
                val padH = (12f * dp).toInt()
                val padV = (8f * dp).toInt()
                setPadding(padH, padV, padH, padV)
            }

            val nameText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setTextColor(AndroidColor.parseColor("#D4D4D8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            root.addView(nameText)

            val countText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = (8f * dp).toInt()
                }
                setTextColor(AndroidColor.parseColor("#71717A"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            root.addView(countText)

            return LeanbackCategoryViewHolder(
                root = root,
                nameText = nameText,
                countText = countText,
                onFocused = onFocused,
                onSelected = onSelected,
                onNavigateRight = onNavigateRight
            )
        }
    }
}
