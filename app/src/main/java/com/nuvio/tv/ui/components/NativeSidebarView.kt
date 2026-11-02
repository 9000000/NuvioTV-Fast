package com.nuvio.tv.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.nuvio.tv.DrawerItem
import com.nuvio.tv.R

/**
 * High-performance Native Android View Sidebar for TV.
 * Replaces heavy Compose LazyColumn / Haze Blur panel with pure hardware-accelerated Views.
 * Ensures 60fps instant focus transitions and 100% focus synchronization with Leanback card rows.
 */
class NativeSidebarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var onDrawerItemClick: ((String) -> Unit)? = null
    var onNavigateRight: (() -> Unit)? = null
    var onNavigateBack: (() -> Boolean)? = null
    var onSwitchProfile: (() -> Unit)? = null

    private var currentItems: List<DrawerItem> = emptyList()
    private var currentSelectedRoute: String? = null
    private val itemViews = mutableMapOf<String, LinearLayout>()

    private val accentOrange = Color.parseColor("#E65100")
    private val selectedBgColor = Color.parseColor("#26E65100")
    private val textPrimary = Color.WHITE
    private val textMuted = Color.parseColor("#9E9E9E")

    private val dp = context.resources.displayMetrics.density
    private fun Int.toPx(): Int = (this * dp).toInt()
    private fun Float.toPx(): Float = this * dp

    private val headerContainer: FrameLayout
    private val itemsContainer: LinearLayout

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false

        // Background: Solid dark sleek surface (#161618) with 24dp right corner radius
        val bgDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#FA141416"))
            cornerRadii = floatArrayOf(
                0f, 0f,
                24f.toPx(), 24f.toPx(),
                24f.toPx(), 24f.toPx(),
                0f, 0f
            )
            setStroke(1.toPx(), Color.parseColor("#25FFFFFF"))
        }
        background = bgDrawable
        setPadding(12.toPx(), 24.toPx(), 12.toPx(), 24.toPx())

        // Header (Logo or Profile)
        headerContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 48.toPx()).apply {
                bottomMargin = 16.toPx()
            }
        }
        addView(headerContainer)

        // Navigation Items Container
        itemsContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(itemsContainer)
    }

    fun setHeader(profileName: String?, avatarColorHex: String?, showProfile: Boolean) {
        headerContainer.removeAllViews()
        if (showProfile && !profileName.isNullOrBlank()) {
            val profileLayout = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isFocusableInTouchMode = true
                setPadding(12.toPx(), 6.toPx(), 12.toPx(), 6.toPx())
                layoutParams = FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

                val avatarView = TextView(context).apply {
                    val avatarColor = runCatching { Color.parseColor(avatarColorHex) }.getOrDefault(accentOrange)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(avatarColor)
                    }
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    text = profileName.take(1).uppercase()
                    layoutParams = LayoutParams(32.toPx(), 32.toPx()).apply {
                        rightMargin = 12.toPx()
                    }
                }
                addView(avatarView)

                val nameView = TextView(context).apply {
                    setTextColor(textPrimary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    isSingleLine = true
                    text = profileName
                }
                addView(nameView)

                onFocusChangeListener = OnFocusChangeListener { v, hasFocus ->
                    val pillBg = GradientDrawable().apply {
                        cornerRadius = 24f.toPx()
                        if (hasFocus) {
                            setColor(Color.WHITE)
                            nameView.setTextColor(Color.BLACK)
                        } else {
                            setColor(Color.TRANSPARENT)
                            nameView.setTextColor(textPrimary)
                        }
                    }
                    v.background = pillBg
                }

                setOnClickListener { onSwitchProfile?.invoke() }
                setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                onNavigateRight?.invoke()
                                return@setOnKeyListener true
                            }
                            KeyEvent.KEYCODE_BACK -> {
                                if (onNavigateBack?.invoke() == true) return@setOnKeyListener true
                            }
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                                return@setOnKeyListener true
                            }
                        }
                    }
                    false
                }
            }
            headerContainer.addView(profileLayout)
        } else {
            // Nuvio Logo Header
            val logoImage = ImageView(context).apply {
                setImageResource(R.drawable.nuvio_text)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(120.toPx(), 32.toPx()).apply {
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    leftMargin = 12.toPx()
                }
            }
            headerContainer.addView(logoImage)
        }
    }

    fun setItems(items: List<DrawerItem>, selectedRoute: String?) {
        currentItems = items
        currentSelectedRoute = selectedRoute
        itemsContainer.removeAllViews()
        itemViews.clear()

        items.forEachIndexed { index, item ->
            val itemView = createNavigationItemView(item, index == 0, index == items.lastIndex)
            itemViews[item.route] = itemView
            itemsContainer.addView(itemView)
        }
    }

    fun setSelectedRoute(route: String?) {
        if (currentSelectedRoute == route) return
        currentSelectedRoute = route
        itemViews.forEach { (itemRoute, view) ->
            updateItemVisual(view, itemRoute == route, view.hasFocus())
        }
    }

    fun requestSidebarFocus(targetRoute: String? = null) {
        val target = targetRoute ?: currentSelectedRoute ?: currentItems.firstOrNull()?.route
        val targetView = target?.let { itemViews[it] } ?: itemViews.values.firstOrNull()
        targetView?.post {
            targetView.requestFocus()
        }
    }

    private fun createNavigationItemView(
        item: DrawerItem,
        isFirst: Boolean,
        isLast: Boolean
    ): LinearLayout {
        val isSelected = item.route == currentSelectedRoute

        val container = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 48.toPx()).apply {
                topMargin = 3.toPx()
                bottomMargin = 3.toPx()
            }
            setPadding(14.toPx(), 0, 14.toPx(), 0)
        }

        val iconView = ImageView(context).apply {
            layoutParams = LayoutParams(24.toPx(), 24.toPx()).apply {
                rightMargin = 16.toPx()
            }
            scaleType = ImageView.ScaleType.FIT_CENTER

            // Load native vector icon
            val drawableId = when (item.route) {
                "home" -> R.drawable.ic_nav_home
                "discover" -> R.drawable.ic_nav_explore
                "livetv" -> R.drawable.ic_nav_livetv
                "search" -> R.drawable.ic_nav_search
                "library" -> R.drawable.ic_nav_library
                "settings" -> R.drawable.ic_nav_settings
                else -> item.iconRes ?: R.drawable.ic_nav_home
            }
            setImageDrawable(ContextCompat.getDrawable(context, drawableId))
        }
        container.addView(iconView)

        val textView = TextView(context).apply {
            text = item.label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            isSingleLine = true
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        container.addView(textView)

        updateItemVisual(container, isSelected, false)

        container.onFocusChangeListener = OnFocusChangeListener { v, hasFocus ->
            val sel = item.route == currentSelectedRoute
            updateItemVisual(v as LinearLayout, sel, hasFocus)
        }

        container.setOnClickListener {
            onDrawerItemClick?.invoke(item.route)
        }

        container.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        return@setOnKeyListener true // Consume to stay in sidebar
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onNavigateRight?.invoke()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (onNavigateBack?.invoke() == true) return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (isFirst) {
                            if (headerContainer.childCount > 0 && headerContainer.getChildAt(0).isFocusable) {
                                headerContainer.getChildAt(0).requestFocus()
                                return@setOnKeyListener true
                            }
                            return@setOnKeyListener true // Consume to prevent escape
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (isLast) {
                            return@setOnKeyListener true // Consume to prevent escape
                        }
                    }
                }
            }
            false
        }

        return container
    }

    private fun updateItemVisual(view: LinearLayout, isSelected: Boolean, isFocused: Boolean) {
        val iconView = view.getChildAt(0) as? ImageView ?: return
        val textView = view.getChildAt(1) as? TextView ?: return

        val pillBg = GradientDrawable().apply {
            cornerRadius = 24f.toPx()
            when {
                isFocused -> {
                    setColor(accentOrange)
                }
                isSelected -> {
                    setColor(selectedBgColor)
                }
                else -> {
                    setColor(Color.TRANSPARENT)
                }
            }
        }
        view.background = pillBg

        when {
            isFocused -> {
                iconView.setColorFilter(Color.WHITE)
                textView.setTextColor(Color.WHITE)
            }
            isSelected -> {
                iconView.setColorFilter(accentOrange)
                textView.setTextColor(accentOrange)
            }
            else -> {
                iconView.setColorFilter(textMuted)
                textView.setTextColor(textMuted)
            }
        }
    }
}
