package com.nuvio.tv.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.FocusFinder
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
 * Supports both Modern Floating Panel and Classic Full-Height TV Drawer modes.
 * Ensures 60fps instant focus transitions, 100% focus synchronization, and zero focus leaks.
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

    var isFloating: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                applySidebarStyle()
            }
        }

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
    private val itemCornerRadiusPx by lazy { 24f.toPx() }

    private val headerContainer: FrameLayout
    private val itemsContainer: LinearLayout

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        applySidebarStyle()

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
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        addView(itemsContainer)
    }

    private fun applySidebarStyle() {
        val bgDrawable = GradientDrawable().apply {
            if (isFloating) {
                setColor(Color.parseColor("#FA141416"))
                cornerRadii = floatArrayOf(
                    0f, 0f,
                    24f.toPx(), 24f.toPx(),
                    24f.toPx(), 24f.toPx(),
                    0f, 0f
                )
                setStroke(1.toPx(), Color.parseColor("#25FFFFFF"))
            } else {
                // Classic Full-Height solid dark TV drawer
                setColor(Color.parseColor("#FF101012"))
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
                setStroke(1.toPx(), Color.parseColor("#18FFFFFF"))
            }
        }
        background = bgDrawable
        if (isFloating) {
            setPadding(12.toPx(), 24.toPx(), 12.toPx(), 24.toPx())
        } else {
            setPadding(16.toPx(), 36.toPx(), 16.toPx(), 24.toPx())
        }
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        val nextFocus = FocusFinder.getInstance().findNextFocus(this, focused, direction)
        return nextFocus ?: focused
    }

    private var currentProfileName: String? = null
    private var currentAvatarColorHex: String? = null
    private var currentShowProfile: Boolean = false

    fun setHeader(profileName: String?, avatarColorHex: String?, showProfile: Boolean) {
        if (currentProfileName == profileName && currentAvatarColorHex == avatarColorHex && currentShowProfile == showProfile && headerContainer.childCount > 0) return
        currentProfileName = profileName
        currentAvatarColorHex = avatarColorHex
        currentShowProfile = showProfile
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
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                val firstRoute = currentItems.firstOrNull()?.route
                                firstRoute?.let { itemViews[it]?.requestFocus() }
                                return@setOnKeyListener true
                            }
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                clearFocus()
                                onNavigateRight?.invoke()
                                return@setOnKeyListener true
                            }
                            KeyEvent.KEYCODE_BACK -> {
                                if (onNavigateBack?.invoke() == true) {
                                    clearFocus()
                                    return@setOnKeyListener true
                                }
                            }
                            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                                return@setOnKeyListener true // Consume to prevent escape
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
        if (currentItems == items) {
            setSelectedRoute(selectedRoute)
            return
        }
        currentItems = items
        currentSelectedRoute = selectedRoute
        itemsContainer.removeAllViews()
        itemViews.clear()

        items.forEachIndexed { index, item ->
            val itemView = createNavigationItemView(index, item)
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == View.VISIBLE && !hasFocus()) {
            requestSidebarFocus()
        }
    }

    fun requestSidebarFocus(targetRoute: String? = null) {
        if (hasFocus()) return
        val target = targetRoute ?: currentSelectedRoute ?: currentItems.firstOrNull()?.route
        val targetView = target?.let { itemViews[it] } ?: itemViews.values.firstOrNull() ?: return
        if (!hasFocus() && targetView.isAttachedToWindow && !targetView.isInLayout) {
            targetView.requestFocus()
        } else {
            targetView.post {
                if (!hasFocus() && isAttachedToWindow) {
                    targetView.requestFocus()
                }
            }
        }
    }

    private fun createNavigationItemView(
        index: Int,
        item: DrawerItem
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
            background = GradientDrawable().apply {
                cornerRadius = itemCornerRadiusPx
                setColor(Color.TRANSPARENT)
            }
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
            clearFocus()
            onDrawerItemClick?.invoke(item.route)
        }

        container.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        return@setOnKeyListener true // Consume to stay in sidebar
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        clearFocus()
                        onNavigateRight?.invoke()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (onNavigateBack?.invoke() == true) {
                            clearFocus()
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (index > 0) {
                            val prevRoute = currentItems.getOrNull(index - 1)?.route
                            val prevView = prevRoute?.let { itemViews[it] }
                            if (prevView != null) {
                                prevView.requestFocus()
                                return@setOnKeyListener true
                            }
                        } else {
                            if (headerContainer.childCount > 0 && headerContainer.getChildAt(0).isFocusable) {
                                headerContainer.getChildAt(0).requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                        return@setOnKeyListener true // Consume to prevent escape to top
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (index < currentItems.size - 1) {
                            val nextRoute = currentItems.getOrNull(index + 1)?.route
                            val nextView = nextRoute?.let { itemViews[it] }
                            if (nextView != null) {
                                nextView.requestFocus()
                                return@setOnKeyListener true
                            }
                        }
                        return@setOnKeyListener true // Consume to prevent escape to bottom
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

        val pillBg = (view.background as? GradientDrawable) ?: GradientDrawable().apply {
            cornerRadius = itemCornerRadiusPx
            view.background = this
        }
        val targetColor = when {
            isFocused -> accentOrange
            isSelected -> selectedBgColor
            else -> Color.TRANSPARENT
        }
        pillBg.setColor(targetColor)

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
