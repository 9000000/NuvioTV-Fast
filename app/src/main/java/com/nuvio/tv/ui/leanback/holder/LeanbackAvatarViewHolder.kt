package com.nuvio.tv.ui.leanback.holder

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.target.Target
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import com.nuvio.tv.ui.leanback.core.PlayStoreGlowView
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem

/**
 * Leanback ViewHolder for Cast and Crew circular avatar cards.
 * Provides circular outline clipping, circular glow, name and character/role labels.
 */
class LeanbackAvatarViewHolder(
    root: LinearLayout,
    cardContainer: FrameLayout,
    glowView: PlayStoreGlowView,
    val avatarImage: ImageView,
    val nameText: TextView,
    val roleText: TextView?,
    cornerRadiusPx: Float,
    borderWidthPx: Int,
    glowMarginPx: Int,
    adapter: LeanbackUniversalAdapter
) : LeanbackBaseViewHolder(
    root, cardContainer, glowView, cornerRadiusPx, borderWidthPx, glowMarginPx, adapter
) {
    override val isCircle: Boolean get() = true
    private var imageDisposable: coil3.request.Disposable? = null

    init {
        glowView.isCircle = true
        avatarImage.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        avatarImage.clipToOutline = true

        focusBorderDrawable.shape = GradientDrawable.OVAL
    }

    override fun bind(
        item: LeanbackCardItem,
        cardWidthPx: Int,
        cardHeightPx: Int,
        cornerRadiusPx: Float,
        showLabels: Boolean
    ) {
        if (currentItem?.key != item.key) {
            avatarImage.setImageDrawable(null)
        }
        currentItem = item

        val colorCacheKey = item.imageUrl ?: item.key
        val cachedColor = LeanbackColorEngine.getCachedColor(colorCacheKey)
        currentDominantColor = cachedColor ?: LeanbackColorEngine.defaultFallbackColor

        // Update layout dimensions (for avatar, diameter is min(cardWidth, cardHeight))
        val diameter = minOf(cardWidthPx, cardHeightPx)
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

        nameText.text = item.title
        val subtitle = (item as? LeanbackCardItem.Avatar)?.subtitle
        if (!subtitle.isNullOrBlank() && roleText != null) {
            roleText.visibility = View.VISIBLE
            roleText.text = subtitle
        } else {
            roleText?.visibility = View.GONE
        }

        // Coil image loading
        val imageUrl = item.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            val ctx = avatarImage.context
            val hasCachedColor = cachedColor != null
            val boundKey = item.key
            val request = ImageRequest.Builder(ctx)
                .data(imageUrl)
                .size(diameter.coerceAtLeast(1), diameter.coerceAtLeast(1))
                .allowHardware(false)
                .crossfade(false)
                .target(
                    object : Target {
                        override fun onStart(placeholder: coil3.Image?) {
                            if (currentItem?.key != boundKey) return
                            avatarImage.setImageDrawable(placeholder?.asDrawable(ctx.resources))
                        }

                        override fun onSuccess(result: coil3.Image) {
                            if (currentItem?.key != boundKey) return
                            avatarImage.setImageDrawable(result.asDrawable(ctx.resources))
                            cardContainer.background = null
                            val existingColor = LeanbackColorEngine.getCachedColor(colorCacheKey)
                            if (existingColor != null) {
                                currentDominantColor = existingColor
                                if (root.hasFocus()) {
                                    updateFocusVisual(true)
                                }
                            } else {
                                val bmp = (result as? BitmapImage)?.bitmap
                                if (bmp != null && !bmp.isRecycled && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || bmp.config != Bitmap.Config.HARDWARE)) {
                                    LeanbackColorEngine.extractDominantColorAsync(colorCacheKey, bmp) { extracted ->
                                        if (currentItem?.key == boundKey) {
                                            currentDominantColor = extracted
                                            if (root.hasFocus()) {
                                                updateFocusVisual(true)
                                            }
                                        }
                                    }
                                } else if (root.hasFocus()) {
                                    updateFocusVisual(true)
                                }
                            }
                        }

                        override fun onError(error: coil3.Image?) {
                            if (currentItem?.key != boundKey) return
                            avatarImage.setImageDrawable(error?.asDrawable(ctx.resources))
                        }
                    }
                )
                .build()
            imageDisposable?.dispose()
            imageDisposable = SingletonImageLoader.get(ctx).enqueue(request)
        } else {
            avatarImage.setImageDrawable(null)
        }

        updateFocusVisual(root.hasFocus())
    }

    override fun unbind() {
        super.unbind()
        imageDisposable?.dispose()
        imageDisposable = null
        avatarImage.setImageDrawable(null)
    }

    companion object {
        fun create(
            parent: ViewGroup,
            cardWidthPx: Int,
            cardHeightPx: Int,
            cornerRadiusPx: Float,
            borderWidthPx: Int,
            glowMarginPx: Int,
            showLabels: Boolean,
            adapter: LeanbackUniversalAdapter
        ): LeanbackAvatarViewHolder {
            val context = parent.context
            val dp = context.resources.displayMetrics.density
            val glowSpreadDp = 18f
            val glowSpreadPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, glowSpreadDp, context.resources.displayMetrics)
            val effectiveGlowMarginPx = (glowSpreadPx * 1.35f).toInt()
            val effectiveBorderWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2.0f, context.resources.displayMetrics).toInt().coerceAtLeast(2)
            val diameter = minOf(cardWidthPx, cardHeightPx)

            val root = LinearLayout(context).apply {
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
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#222226"))
                }
            }

            val glowView = PlayStoreGlowView(context).apply {
                this.isCircle = true
                this.glowSpreadPx = glowSpreadPx
                this.glowMarginPx = effectiveGlowMarginPx.toFloat()
                layoutParams = FrameLayout.LayoutParams(
                    diameter + (effectiveGlowMarginPx * 2),
                    diameter + (effectiveGlowMarginPx * 2)
                ).apply {
                    leftMargin = -effectiveGlowMarginPx
                    topMargin = -effectiveGlowMarginPx
                }
                visibility = View.GONE
            }
            cardContainer.addView(glowView)

            val avatarImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cardContainer.addView(avatarImage)

            root.addView(cardContainer)

            val nameText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (8f * dp).toInt()
                }
                setTextColor(Color.WHITE)
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
                setTextColor(Color.parseColor("#909096"))
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
                cornerRadiusPx = cornerRadiusPx,
                borderWidthPx = effectiveBorderWidthPx,
                glowMarginPx = effectiveGlowMarginPx,
                adapter = adapter
            )
        }
    }
}

