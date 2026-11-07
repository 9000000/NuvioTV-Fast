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
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import coil3.BitmapImage
import coil3.Image
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.target.Target
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import com.nuvio.tv.ui.leanback.core.PillProgressBarDrawable
import com.nuvio.tv.ui.leanback.core.PlayStoreGlowView
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem

/**
 * Leanback ViewHolder for standard Poster cards (Portrait 2:3 or Landscape 16:9).
 * Used across Home, Library, Catalog, Search, and More Like This.
 */
class LeanbackPosterViewHolder(
    root: LinearLayout,
    cardContainer: FrameLayout,
    glowView: PlayStoreGlowView,
    val posterImage: ImageView,
    val titleText: TextView?,
    val progressBar: ProgressBar?,
    val badgeView: TextView?,
    cornerRadiusPx: Float,
    borderWidthPx: Int,
    glowMarginPx: Int,
    adapter: LeanbackUniversalAdapter
) : LeanbackBaseViewHolder(
    root, cardContainer, glowView, cornerRadiusPx, borderWidthPx, glowMarginPx, adapter
) {
    private var imageDisposable: coil3.request.Disposable? = null

    init {
        posterImage.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (cornerRadiusPx > 0f) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                } else {
                    outline.setRect(0, 0, view.width, view.height)
                }
            }
        }
        posterImage.clipToOutline = true
    }

    override fun updateCornerRadius(newRadiusPx: Float) {
        super.updateCornerRadius(newRadiusPx)
        posterImage.invalidateOutline()
        val horizMargin = (newRadiusPx * 0.75f).toInt().coerceAtLeast(6)
        (progressBar?.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.leftMargin != horizMargin || lp.rightMargin != horizMargin) {
                lp.leftMargin = horizMargin
                lp.rightMargin = horizMargin
                progressBar.layoutParams = lp
            }
        }
    }

    override fun bind(
        item: LeanbackCardItem,
        cardWidthPx: Int,
        cardHeightPx: Int,
        cornerRadiusPx: Float,
        showLabels: Boolean
    ) {
        if (currentItem?.key != item.key) {
            posterImage.setImageDrawable(null)
        }
        currentItem = item
        updateCornerRadius(cornerRadiusPx)

        val cachedColor = LeanbackColorEngine.getCachedColor(item.key)
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

        // Title text
        if (showLabels && titleText != null) {
            titleText.visibility = View.VISIBLE
            titleText.text = item.title
        } else {
            titleText?.visibility = View.GONE
        }

        // Progress bar for Continue Watching
        val progress = (item as? LeanbackCardItem.Poster)?.progressPercent
        if (progress != null && progress > 0 && progressBar != null) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = progress.coerceIn(0, 100)
        } else {
            progressBar?.visibility = View.GONE
        }

        // Badge text
        val badge = (item as? LeanbackCardItem.Poster)?.badgeText
        if (!badge.isNullOrBlank() && badgeView != null) {
            badgeView.visibility = View.VISIBLE
            badgeView.text = badge
        } else {
            badgeView?.visibility = View.GONE
        }

        // Coil image loading with zero GPU overdraw
        val imageUrl = item.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            val ctx = posterImage.context
            val hasCachedColor = cachedColor != null
            val request = ImageRequest.Builder(ctx)
                .data(imageUrl)
                .size(cardWidthPx.coerceAtLeast(1), cardHeightPx.coerceAtLeast(1))
                .allowHardware(hasCachedColor)
                .crossfade(false)
                .target(
                    object : Target {
                        override fun onStart(placeholder: Image?) {
                            if (placeholder != null) {
                                posterImage.setImageDrawable(placeholder.asDrawable(ctx.resources))
                            }
                        }

                        override fun onSuccess(result: Image) {
                            posterImage.setImageDrawable(result.asDrawable(ctx.resources))
                            // Zero GPU Overdraw: remove background when image covers container
                            cardContainer.background = null
                            val existingColor = LeanbackColorEngine.getCachedColor(item.key)
                            if (existingColor != null) {
                                currentDominantColor = existingColor
                            } else {
                                val bmp = (result as? BitmapImage)?.bitmap
                                if (bmp != null && !bmp.isRecycled && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || bmp.config != Bitmap.Config.HARDWARE)) {
                                    val extracted = LeanbackColorEngine.extractDominantColor(bmp)
                                    LeanbackColorEngine.putCachedColor(item.key, extracted)
                                    currentDominantColor = extracted
                                }
                            }
                            if (root.hasFocus()) {
                                updateFocusVisual(true)
                            }
                        }

                        override fun onError(error: Image?) {
                            posterImage.setImageDrawable(error?.asDrawable(ctx.resources))
                        }
                    }
                )
                .build()
            imageDisposable?.dispose()
            imageDisposable = SingletonImageLoader.get(ctx).enqueue(request)
        } else {
            posterImage.setImageDrawable(null)
        }

        updateFocusVisual(root.hasFocus())
    }

    override fun unbind() {
        super.unbind()
        imageDisposable?.dispose()
        imageDisposable = null
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
        ): LeanbackPosterViewHolder {
            val context = parent.context

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
                layoutParams = LinearLayout.LayoutParams(cardWidthPx, cardHeightPx)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A1A1A"))
                    cornerRadius = cornerRadiusPx
                }
            }

            val glowSpreadPx = 28f * context.resources.displayMetrics.density
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
            cardContainer.addView(glowView)

            val posterImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cardContainer.addView(posterImage)

            // Progress bar
            val dp = context.resources.displayMetrics.density
            val pbHeightPx = (4f * dp)
            val horizMargin = (cornerRadiusPx * 0.75f).toInt().coerceAtLeast(6)
            val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    pbHeightPx.toInt()
                ).apply {
                    gravity = Gravity.BOTTOM
                    leftMargin = horizMargin
                    rightMargin = horizMargin
                    bottomMargin = (6f * dp).toInt()
                }
                progressDrawable = PillProgressBarDrawable.create(pbHeightPx)
                visibility = View.GONE
            }
            cardContainer.addView(progressBar)

            // Badge overlay (top-end)
            val badgeView = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = (6f * dp).toInt()
                    rightMargin = (6f * dp).toInt()
                }
                setPadding((6f * dp).toInt(), (2f * dp).toInt(), (6f * dp).toInt(), (2f * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#CC101012"))
                    cornerRadius = 4f * dp
                }
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                visibility = View.GONE
            }
            cardContainer.addView(badgeView)

            root.addView(cardContainer)

            val titleText: TextView? = if (showLabels) {
                TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (8f * dp).toInt()
                    }
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER_HORIZONTAL
                }.also { root.addView(it) }
            } else null

            return LeanbackPosterViewHolder(
                root = root,
                cardContainer = cardContainer,
                glowView = glowView,
                posterImage = posterImage,
                titleText = titleText,
                progressBar = progressBar,
                badgeView = badgeView,
                cornerRadiusPx = cornerRadiusPx,
                borderWidthPx = borderWidthPx,
                glowMarginPx = glowMarginPx,
                adapter = adapter
            )
        }
    }
}

