package com.nuvio.tv.ui.leanback.holder

import android.content.Context
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
 * Leanback ViewHolder for TV Episode cards (16:9 Landscape).
 * Displays episode thumbnail, episode badge (e.g. "EP 1" / "S1 E1"),
 * runtime, title, progress bar, and watched marker.
 */
class LeanbackEpisodeViewHolder(
    root: LinearLayout,
    cardContainer: FrameLayout,
    glowView: PlayStoreGlowView,
    val thumbnailImage: ImageView,
    val episodeBadge: TextView,
    val titleText: TextView,
    val metaText: TextView?,
    val progressBar: ProgressBar?,
    cornerRadiusPx: Float,
    borderWidthPx: Int,
    glowMarginPx: Int,
    adapter: LeanbackUniversalAdapter
) : LeanbackBaseViewHolder(
    root, cardContainer, glowView, cornerRadiusPx, borderWidthPx, glowMarginPx, adapter
) {
    private var imageDisposable: coil3.request.Disposable? = null

    init {
        thumbnailImage.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (cornerRadiusPx > 0f) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                } else {
                    outline.setRect(0, 0, view.width, view.height)
                }
            }
        }
        thumbnailImage.clipToOutline = true
    }

    override fun updateCornerRadius(newRadiusPx: Float) {
        super.updateCornerRadius(newRadiusPx)
        thumbnailImage.invalidateOutline()
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
            thumbnailImage.setImageDrawable(null)
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

        val epItem = item as? LeanbackCardItem.Episode

        // Episode Badge (e.g. "EP 3" or "S1 E3")
        val badge = epItem?.episodeLabel
            ?: if (epItem?.episodeNumber != null) "EP ${epItem.episodeNumber}" else null
        if (!badge.isNullOrBlank()) {
            episodeBadge.visibility = View.VISIBLE
            episodeBadge.text = badge
        } else {
            episodeBadge.visibility = View.GONE
        }

        // Title
        titleText.text = item.title
        titleText.isSelected = root.hasFocus() // Enables horizontal marquee on focus

        // Meta (runtime or release date)
        if (metaText != null) {
            val parts = mutableListOf<String>()
            epItem?.runtimeMinutes?.let { if (it > 0) parts.add("${it}m") }
            epItem?.imdbRating?.let { if (it.isNotBlank()) parts.add("★ $it") }
            epItem?.releaseDate?.let { if (it.isNotBlank()) parts.add(it) }

            if (parts.isNotEmpty()) {
                metaText.visibility = View.VISIBLE
                metaText.text = parts.joinToString(" • ")
            } else {
                metaText.visibility = View.GONE
            }
        }

        // Progress bar
        val progress = epItem?.progressPercent
        if (progress != null && progress > 0 && progressBar != null) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = progress.coerceIn(0, 100)
        } else {
            progressBar?.visibility = View.GONE
        }

        // Coil image loading
        val imageUrl = item.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            val ctx = thumbnailImage.context
            val hasCachedColor = cachedColor != null
            val request = ImageRequest.Builder(ctx)
                .data(imageUrl)
                .size(cardWidthPx.coerceAtLeast(1), cardHeightPx.coerceAtLeast(1))
                .allowHardware(hasCachedColor)
                .crossfade(false)
                .target(
                    object : Target {
                        override fun onStart(placeholder: Image?) {
                            thumbnailImage.setImageDrawable(placeholder?.asDrawable(ctx.resources))
                        }

                        override fun onSuccess(result: Image) {
                            thumbnailImage.setImageDrawable(result.asDrawable(ctx.resources))
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
                            thumbnailImage.setImageDrawable(error?.asDrawable(ctx.resources))
                        }
                    }
                )
                .build()
            imageDisposable?.dispose()
            imageDisposable = SingletonImageLoader.get(ctx).enqueue(request)
        } else {
            thumbnailImage.setImageDrawable(null)
        }

        updateFocusVisual(root.hasFocus())
    }

    override fun updateFocusVisual(hasFocus: Boolean) {
        super.updateFocusVisual(hasFocus)
        titleText.isSelected = hasFocus
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
        ): LeanbackEpisodeViewHolder {
            val context = parent.context
            val dp = context.resources.displayMetrics.density

            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
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

            val glowSpreadPx = 28f * dp
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

            val thumbnailImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cardContainer.addView(thumbnailImage)

            // Gradient scrim for text legibility
            val gradientView = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        Color.TRANSPARENT,
                        Color.parseColor("#10000000"),
                        Color.parseColor("#90000000"),
                        Color.parseColor("#E6000000")
                    )
                )
            }
            cardContainer.addView(gradientView)

            // Episode Badge (e.g. "EP 1")
            val episodeBadge = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    leftMargin = (10f * dp).toInt()
                    bottomMargin = (42f * dp).toInt()
                }
                setPadding((6f * dp).toInt(), (2f * dp).toInt(), (6f * dp).toInt(), (2f * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#B3202024"))
                    cornerRadius = 4f * dp
                }
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            }
            cardContainer.addView(episodeBadge)

            // Title inside card bottom
            val titleText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    leftMargin = (10f * dp).toInt()
                    rightMargin = (10f * dp).toInt()
                    bottomMargin = (22f * dp).toInt()
                }
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isSingleLine = true
            }
            cardContainer.addView(titleText)

            // Meta text (runtime, rating)
            val metaText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    leftMargin = (10f * dp).toInt()
                    rightMargin = (10f * dp).toInt()
                    bottomMargin = (8f * dp).toInt()
                }
                setTextColor(Color.parseColor("#A0A0A5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            cardContainer.addView(metaText)

            // Progress bar
            val pbHeightPx = (3.5f * dp)
            val horizMargin = (cornerRadiusPx * 0.75f).toInt().coerceAtLeast(6)
            val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    pbHeightPx.toInt()
                ).apply {
                    gravity = Gravity.BOTTOM
                    leftMargin = horizMargin
                    rightMargin = horizMargin
                    bottomMargin = (2f * dp).toInt()
                }
                progressDrawable = PillProgressBarDrawable.create(pbHeightPx)
                visibility = View.GONE
            }
            cardContainer.addView(progressBar)

            root.addView(cardContainer)

            return LeanbackEpisodeViewHolder(
                root = root,
                cardContainer = cardContainer,
                glowView = glowView,
                thumbnailImage = thumbnailImage,
                episodeBadge = episodeBadge,
                titleText = titleText,
                metaText = metaText,
                progressBar = progressBar,
                cornerRadiusPx = cornerRadiusPx,
                borderWidthPx = borderWidthPx,
                glowMarginPx = glowMarginPx,
                adapter = adapter
            )
        }
    }
}

