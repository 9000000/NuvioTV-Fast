package com.nuvio.tv.ui.leanback.holder

import android.content.Context
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil3.request.Disposable
import com.nuvio.tv.domain.model.isPlaceholder
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.core.LeanbackArtworkLoader
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import com.nuvio.tv.ui.leanback.core.PlayStoreGlowView
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem

/**
 * Leanback ViewHolder for Cast and Crew circular avatar cards.
 * Provides circular outline clipping, circular glow, name and character/role labels
 * with synchronous MemoryCache fast-path.
 */
class LeanbackAvatarViewHolder(
    root: LinearLayout,
    cardContainer: FrameLayout,
    glowView: PlayStoreGlowView,
    val avatarImage: ImageView,
    val nameText: TextView,
    val roleText: TextView?,
    val initialsText: TextView? = null,
    cornerRadiusPx: Float,
    borderWidthPx: Int,
    glowMarginPx: Int,
    adapter: LeanbackUniversalAdapter
) : LeanbackBaseViewHolder(
    root, cardContainer, glowView, cornerRadiusPx, borderWidthPx, glowMarginPx, adapter
) {
    override val isCircle: Boolean get() = true
    private var imageDisposable: Disposable? = null
    private var boundImageUrl: String? = null
    private var boundDiameterPx: Int = 0

    init {
        glowView.isCircle = true
        avatarImage.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        avatarImage.clipToOutline = true
        focusBorderDrawable.isOval = true
    }

    override fun needsImageRebind(): Boolean {
        val item = currentItem ?: return false
        val url = item.imageUrl?.trim()
        if (url.isNullOrEmpty() || url.isPlaceholder()) return false
        return avatarImage.drawable == null && (imageDisposable == null || imageDisposable?.isDisposed == true)
    }

    override fun bind(
        item: LeanbackCardItem,
        cardWidthPx: Int,
        cardHeightPx: Int,
        cornerRadiusPx: Float,
        showLabels: Boolean
    ) {
        val imageUrl = item.imageUrl
        val diameter = minOf(cardWidthPx, cardHeightPx).coerceAtLeast(1)
        val sameImageRequest = currentItem?.key == item.key &&
            boundImageUrl == imageUrl &&
            boundDiameterPx == diameter &&
            (avatarImage.drawable != null || imageDisposable?.isDisposed == false)

        currentItem = item

        if (!sameImageRequest) {
            avatarImage.setImageDrawable(null)
            ensurePlaceholderBackground()
        }

        val colorCacheKey = imageUrl ?: item.key
        val cachedColor = LeanbackColorEngine.getCachedColor(colorCacheKey)
        currentDominantColor = cachedColor ?: LeanbackColorEngine.defaultFallbackColor

        // Update layout dimensions (for avatar, diameter is min(cardWidth, cardHeight))
        val cardLp = cardContainer.layoutParams
        if (cardLp.width != diameter || cardLp.height != diameter) {
            cardLp.width = diameter
            cardLp.height = diameter
            cardContainer.layoutParams = cardLp

            val glowLp = glowView.layoutParams as FrameLayout.LayoutParams
            glowLp.width = diameter + (glowMarginPx * 2)
            glowLp.height = diameter + (glowMarginPx * 2)
            glowView.layoutParams = glowLp

            val rootLp = root.layoutParams
            if (rootLp != null && rootLp.width != cardWidthPx) {
                rootLp.width = cardWidthPx
                root.layoutParams = rootLp
            }
        }

        val style = adapter.themeStyle
        nameText.setTextColor(style.titleColor)
        initialsText?.setTextColor(style.titleColor)
        roleText?.setTextColor(style.subtitleColor)
        style.typeface?.let { tf ->
            if (nameText.typeface !== tf) nameText.typeface = tf
            if (roleText != null && roleText.typeface !== tf) roleText.typeface = tf
        }

        nameText.text = item.title
        val subtitle = (item as? LeanbackCardItem.Avatar)?.subtitle
        if (!subtitle.isNullOrBlank() && roleText != null) {
            roleText.visibility = View.VISIBLE
            roleText.text = subtitle
        } else {
            roleText?.visibility = View.GONE
        }

        // Accessibility content description
        root.contentDescription = when {
            item.title.isNotBlank() && !subtitle.isNullOrBlank() -> "${item.title}, $subtitle"
            item.title.isNotBlank() -> item.title
            else -> null
        }

        val initials = computeInitials(item.title)
        fun showInitialsFallback() {
            if (initialsText != null && initials.isNotEmpty()) {
                initialsText.text = initials
                initialsText.visibility = View.VISIBLE
            } else {
                initialsText?.visibility = View.GONE
            }
        }

        // Coil image loading with MemoryCache fast-path
        if (!imageUrl.isNullOrBlank() && !imageUrl.trim().isPlaceholder()) {
            if (!sameImageRequest) {
                showInitialsFallback()
                val boundKey = item.key
                boundImageUrl = imageUrl
                boundDiameterPx = diameter
                imageDisposable?.dispose()
                imageDisposable = LeanbackArtworkLoader.loadIntoImageView(
                    imageView = avatarImage,
                    imageUrl = imageUrl,
                    widthPx = diameter,
                    heightPx = diameter,
                    isStillBound = { currentItem?.key == boundKey },
                    onCacheHit = { result ->
                        cardContainer.background = null
                        initialsText?.visibility = View.GONE
                        resolveDominantColorFromImage(
                            result = result,
                            boundKey = boundKey,
                            colorCacheKey = colorCacheKey,
                            notifyIfCachedAndFocused = false
                        )
                    },
                    onAsyncSuccess = { result ->
                        cardContainer.background = null
                        initialsText?.visibility = View.GONE
                        resolveDominantColorFromImage(
                            result = result,
                            boundKey = boundKey,
                            colorCacheKey = colorCacheKey,
                            notifyIfCachedAndFocused = true
                        )
                    },
                    onAsyncError = {
                        boundImageUrl = null
                        ensurePlaceholderBackground()
                        showInitialsFallback()
                    }
                )
            }
        } else {
            imageDisposable?.dispose()
            imageDisposable = null
            boundImageUrl = null
            avatarImage.setImageDrawable(null)
            ensurePlaceholderBackground()
            showInitialsFallback()
        }

        updateFocusVisual(root.hasFocus())
    }

    override fun unbind() {
        super.unbind()
        imageDisposable?.dispose()
        imageDisposable = null
        boundImageUrl = null
        boundDiameterPx = 0
        avatarImage.setImageDrawable(null)
        initialsText?.visibility = View.GONE
        initialsText?.text = ""
        root.contentDescription = null
    }

    companion object {
        fun create(
            context: Context,
            cardWidthPx: Int,
            cardHeightPx: Int,
            cornerRadiusPx: Float,
            adapter: LeanbackUniversalAdapter
        ): LeanbackAvatarViewHolder {
            val chrome = computeLeanbackCardChromeMetrics(context)
            val dp = context.resources.displayMetrics.density
            val diameter = minOf(cardWidthPx, cardHeightPx).coerceAtLeast(1)

            val root = FocusIsolatedRootLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                clipChildren = false
                clipToPadding = false
                layoutParams = LinearLayout.LayoutParams(
                    cardWidthPx,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val cardContainer = FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
                layoutParams = LinearLayout.LayoutParams(diameter, diameter)
            }

            val glowView = PlayStoreGlowView(context).apply {
                this.isCircle = true
                this.glowSpreadPx = chrome.glowSpreadPx
                this.glowMarginPx = chrome.glowMarginPx.toFloat()
                layoutParams = FrameLayout.LayoutParams(
                    diameter + (chrome.glowMarginPx * 2),
                    diameter + (chrome.glowMarginPx * 2)
                ).apply {
                    leftMargin = -chrome.glowMarginPx
                    topMargin = -chrome.glowMarginPx
                }
                visibility = View.GONE
            }
            cardContainer.addView(glowView)

            val themeStyle = adapter.themeStyle
            val avatarImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cardContainer.addView(avatarImage)

            val initialsText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                gravity = Gravity.CENTER
                setTextColor(themeStyle.titleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                typeface = Typeface.DEFAULT_BOLD
                visibility = View.GONE
            }
            cardContainer.addView(initialsText)

            root.addView(cardContainer)

            val nameText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (8f * dp).toInt()
                }
                setTextColor(themeStyle.titleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_HORIZONTAL
            }.also { root.addView(it) }

            val roleText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (2f * dp).toInt()
                }
                setTextColor(themeStyle.subtitleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_HORIZONTAL
            }.also { root.addView(it) }

            return LeanbackAvatarViewHolder(
                root = root,
                cardContainer = cardContainer,
                glowView = glowView,
                avatarImage = avatarImage,
                nameText = nameText,
                roleText = roleText,
                initialsText = initialsText,
                cornerRadiusPx = cornerRadiusPx,
                borderWidthPx = chrome.borderWidthPx,
                glowMarginPx = chrome.glowMarginPx,
                adapter = adapter
            ).also { it.ensurePlaceholderBackground() }
        }
    }
}

private fun computeInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return ""
    return if (parts.size == 1) {
        parts[0].take(2).uppercase()
    } else {
        "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
    }
}
