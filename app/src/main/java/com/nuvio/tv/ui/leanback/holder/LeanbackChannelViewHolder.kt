package com.nuvio.tv.ui.leanback.holder

import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import coil3.Image
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.target.Target
import com.nuvio.tv.R
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import com.nuvio.tv.ui.leanback.core.PlayStoreGlowView
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem

/**
 * Android Leanback Native ViewHolder for Live TV Channels in Grid View.
 * Hardware-accelerated, zero Compose recomposition, 60fps locked navigation on Android TV.
 */
class LeanbackChannelViewHolder(
    root: LinearLayout,
    cardContainer: FrameLayout,
    glowView: PlayStoreGlowView,
    val logoImage: ImageView,
    val fallbackIcon: ImageView,
    val channelNameText: TextView,
    val starContainer: FrameLayout,
    val starImage: ImageView,
    val launchingSpinner: ProgressBar,
    cornerRadiusPx: Float,
    borderWidthPx: Int,
    glowMarginPx: Int,
    adapter: LeanbackUniversalAdapter
) : LeanbackBaseViewHolder(
    root, cardContainer, glowView, cornerRadiusPx, borderWidthPx, glowMarginPx, adapter
) {
    private var imageDisposable: coil3.request.Disposable? = null

    init {
        cardContainer.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (cornerRadiusPx > 0f) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                } else {
                    outline.setRect(0, 0, view.width, view.height)
                }
            }
        }
        cardContainer.clipToOutline = true

        // Key handler for TV remote shortcuts (Yellow / Menu / Star to toggle favorite)
        root.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP) {
                when (keyCode) {
                    KeyEvent.KEYCODE_MENU,
                    KeyEvent.KEYCODE_STAR,
                    KeyEvent.KEYCODE_BUTTON_Y,
                    KeyEvent.KEYCODE_BOOKMARK,
                    KeyEvent.KEYCODE_PROG_YELLOW -> {
                        currentItem?.let { adapter.onItemLongClick(it) }
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }

    override fun bind(
        item: LeanbackCardItem,
        cardWidthPx: Int,
        cardHeightPx: Int,
        cornerRadiusPx: Float,
        showLabels: Boolean
    ) {
        val channelItem = item as? LeanbackCardItem.Channel
        if (currentItem?.key != item.key) {
            logoImage.setImageDrawable(null)
            fallbackIcon.visibility = View.VISIBLE
        }
        currentItem = item
        updateCornerRadius(cornerRadiusPx)

        val colorCacheKey = item.imageUrl ?: item.key
        val cachedColor = LeanbackColorEngine.getCachedColor(colorCacheKey)
        currentDominantColor = cachedColor ?: LeanbackColorEngine.defaultFallbackColor

        // Update layout dimensions
        val cardLp = cardContainer.layoutParams
        if (cardLp.width != cardWidthPx || cardLp.height != cardHeightPx) {
            cardLp.width = cardWidthPx
            cardLp.height = cardHeightPx
            cardContainer.layoutParams = cardLp

            val glowLp = glowView.layoutParams as FrameLayout.LayoutParams
            glowLp.width = cardWidthPx + (glowMarginPx * 2)
            glowLp.height = cardHeightPx + (glowMarginPx * 2)
            glowView.layoutParams = glowLp

            val rootLp = root.layoutParams
            if (rootLp != null && rootLp.width != cardWidthPx) {
                rootLp.width = cardWidthPx
                root.layoutParams = rootLp
            }
        }

        // Channel Name
        channelNameText.text = item.title

        // Favorite Star
        val isFav = channelItem?.isFavorite == true
        if (isFav) {
            starContainer.visibility = View.VISIBLE
            starImage.setColorFilter(Color.parseColor("#FFD700")) // Gold
        } else if (root.hasFocus()) {
            starContainer.visibility = View.VISIBLE
            starImage.setColorFilter(Color.parseColor("#80FFFFFF")) // Translucent white on focus
        } else {
            starContainer.visibility = View.GONE
        }

        // Launching indicator
        val isLaunching = channelItem?.isLaunching == true
        launchingSpinner.visibility = if (isLaunching) View.VISIBLE else View.GONE

        // Coil Logo Loading
        val logoUrl = item.imageUrl
        if (!logoUrl.isNullOrBlank()) {
            val ctx = logoImage.context
            val boundKey = item.key
            val request = ImageRequest.Builder(ctx)
                .data(logoUrl)
                .size(cardWidthPx.coerceAtLeast(1), cardHeightPx.coerceAtLeast(1))
                .allowHardware(false)
                .crossfade(false)
                .target(
                    object : Target {
                        override fun onStart(placeholder: Image?) {
                            if (currentItem?.key != boundKey) return
                            if (placeholder != null) {
                                logoImage.setImageDrawable(placeholder.asDrawable(ctx.resources))
                                fallbackIcon.visibility = View.GONE
                            }
                        }

                        override fun onSuccess(result: Image) {
                            if (currentItem?.key != boundKey) return
                            logoImage.setImageDrawable(result.asDrawable(ctx.resources))
                            fallbackIcon.visibility = View.GONE
                        }

                        override fun onError(error: Image?) {
                            if (currentItem?.key != boundKey) return
                            logoImage.setImageDrawable(null)
                            fallbackIcon.visibility = View.VISIBLE
                        }
                    }
                )
                .build()
            imageDisposable?.dispose()
            imageDisposable = SingletonImageLoader.get(ctx).enqueue(request)
        } else {
            logoImage.setImageDrawable(null)
            fallbackIcon.visibility = View.VISIBLE
        }
    }

    override fun updateFocusVisual(hasFocus: Boolean) {
        super.updateFocusVisual(hasFocus)
        val channelItem = currentItem as? LeanbackCardItem.Channel
        val isFav = channelItem?.isFavorite == true
        if (isFav) {
            starContainer.visibility = View.VISIBLE
            starImage.setColorFilter(Color.parseColor("#FFD700"))
        } else if (hasFocus) {
            starContainer.visibility = View.VISIBLE
            starImage.setColorFilter(Color.parseColor("#80FFFFFF"))
        } else {
            starContainer.visibility = View.GONE
        }
    }

    override fun unbind() {
        super.unbind()
        imageDisposable?.dispose()
        imageDisposable = null
        logoImage.setImageDrawable(null)
        fallbackIcon.visibility = View.VISIBLE
        launchingSpinner.visibility = View.GONE
    }

    companion object {
        fun create(
            parent: LinearLayout,
            cardWidthPx: Int,
            cardHeightPx: Int,
            cornerRadiusPx: Float,
            borderWidthPx: Int,
            glowMarginPx: Int,
            showLabels: Boolean,
            adapter: LeanbackUniversalAdapter
        ): LeanbackChannelViewHolder {
            val context = parent.context
            val dp = context.resources.displayMetrics.density

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false
                clipToPadding = false
                layoutParams = ViewGroup.LayoutParams(cardWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            // Outer wrapper for Glow + CardContainer
            val outerWrapper = FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            root.addView(outerWrapper)

            val glowSpreadPx = 18f * dp
            val glowView = PlayStoreGlowView(context).apply {
                this.cornerRadiusPx = cornerRadiusPx
                this.glowSpreadPx = glowSpreadPx
                this.glowMarginPx = glowMarginPx.toFloat()
                layoutParams = FrameLayout.LayoutParams(
                    cardWidthPx + (glowMarginPx * 2),
                    cardHeightPx + (glowMarginPx * 2)
                ).apply {
                    leftMargin = -glowMarginPx
                    topMargin = -glowMarginPx
                }
                visibility = View.GONE
            }
            outerWrapper.addView(glowView)

            val cardContainer = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(cardWidthPx, cardHeightPx)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1B1B22"))
                    cornerRadius = cornerRadiusPx
                }
            }
            outerWrapper.addView(cardContainer)

            // Channel Logo Image
            val logoImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    val pad = (12f * dp).toInt()
                    val botPad = (28f * dp).toInt()
                    setMargins(pad, pad, pad, botPad)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            cardContainer.addView(logoImage)

            // Fallback Live TV Icon
            val fallbackIcon = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (36f * dp).toInt(),
                    (36f * dp).toInt()
                ).apply {
                    gravity = Gravity.CENTER
                    bottomMargin = (12f * dp).toInt()
                }
                setImageResource(R.drawable.ic_nav_livetv)
                setColorFilter(Color.parseColor("#666670"))
                visibility = View.VISIBLE
            }
            cardContainer.addView(fallbackIcon)

            // Bottom Gradient Bar with Channel Name
            val bottomBar = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM
                }
                val topColor = Color.TRANSPARENT
                val botColor = Color.parseColor("#E6000000")
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(topColor, botColor)
                )
                setPadding((8f * dp).toInt(), (8f * dp).toInt(), (8f * dp).toInt(), (6f * dp).toInt())
            }
            cardContainer.addView(bottomBar)

            val channelNameText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                }
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
            }
            bottomBar.addView(channelNameText)

            // Star Badge Container (Top-End)
            val starSizePx = (24f * dp).toInt()
            val starContainer = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    starSizePx,
                    starSizePx
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = (6f * dp).toInt()
                    rightMargin = (6f * dp).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#80000000"))
                }
                visibility = View.GONE
            }
            cardContainer.addView(starContainer)

            val starImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (16f * dp).toInt(),
                    (16f * dp).toInt()
                ).apply {
                    gravity = Gravity.CENTER
                }
                setImageResource(R.drawable.ic_star_filled)
                setColorFilter(Color.parseColor("#FFD700"))
            }
            starContainer.addView(starImage)

            // Launching Spinner (Centered)
            val launchingSpinner = ProgressBar(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (32f * dp).toInt(),
                    (32f * dp).toInt()
                ).apply {
                    gravity = Gravity.CENTER
                }
                isIndeterminate = true
                visibility = View.GONE
            }
            cardContainer.addView(launchingSpinner)

            return LeanbackChannelViewHolder(
                root = root,
                cardContainer = cardContainer,
                glowView = glowView,
                logoImage = logoImage,
                fallbackIcon = fallbackIcon,
                channelNameText = channelNameText,
                starContainer = starContainer,
                starImage = starImage,
                launchingSpinner = launchingSpinner,
                cornerRadiusPx = cornerRadiusPx,
                borderWidthPx = borderWidthPx,
                glowMarginPx = glowMarginPx,
                adapter = adapter
            )
        }
    }
}
