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
import android.view.ViewGroup
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
import coil3.request.transformations
import coil3.target.Target
import com.nuvio.tv.R
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import com.nuvio.tv.ui.leanback.core.PillProgressBarDrawable
import com.nuvio.tv.ui.leanback.core.PlayStoreGlowView
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem
import com.nuvio.tv.ui.util.BlurTransformation

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
    val watchedBadgeView: TextView?,
    val titleText: TextView,
    val descriptionText: TextView?,
    val dateText: TextView?,
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

        val epItem = item as? LeanbackCardItem.Episode

        // Episode Badge (e.g. "S1 E3") plus availability state.
        val baseBadge = epItem?.episodeLabel
            ?: if (epItem?.episodeNumber != null) "EP ${epItem.episodeNumber}" else null
        val isUnavailable = epItem?.isAvailable == false
        val unavailableLabel = if (isUnavailable) {
            root.context.getString(R.string.episodes_unavailable)
        } else {
            null
        }
        val badge = listOfNotNull(baseBadge, unavailableLabel)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
            .takeIf { it.isNotBlank() }
        if (!badge.isNullOrBlank()) {
            episodeBadge.visibility = View.VISIBLE
            episodeBadge.text = badge
            (episodeBadge.background as? GradientDrawable)?.setColor(
                if (isUnavailable) Color.parseColor("#CC5D4037") else Color.parseColor("#B3202024")
            )
        } else {
            episodeBadge.visibility = View.GONE
        }

        // Title
        titleText.text = item.title
        titleText.isSelected = root.hasFocus() // Enables horizontal marquee on focus

        // Episode overview
        if (descriptionText != null) {
            val description = epItem?.description?.trim().orEmpty()
            if (description.isNotEmpty()) {
                descriptionText.visibility = View.VISIBLE
                descriptionText.text = description
            } else {
                descriptionText.visibility = View.GONE
            }
        }

        // Release date chip. It gets its own view so runtime/rating can never push it out of
        // the single-line meta row below the title.
        if (dateText != null) {
            val dateLabel = epItem?.releaseDate?.takeIf { it.isNotBlank() }
            if (dateLabel != null) {
                dateText.visibility = View.VISIBLE
                dateText.text = dateLabel
            } else {
                dateText.visibility = View.GONE
            }
        }

        // Meta (runtime and rating)
        if (metaText != null) {
            val parts = mutableListOf<String>()
            formatRuntimeMinutes(epItem?.runtimeMinutes)?.let(parts::add)
            epItem?.imdbRating?.takeIf { it.isNotBlank() }?.let { parts.add("★ $it") }

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

        // Watched indicator badge (top-end)
        if (epItem?.isWatched == true && watchedBadgeView != null) {
            watchedBadgeView.visibility = View.VISIBLE
        } else {
            watchedBadgeView?.visibility = View.GONE
        }

        // Coil image loading
        val imageUrl = item.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            val ctx = thumbnailImage.context
            val hasCachedColor = cachedColor != null
            val shouldBlurThumbnail = epItem?.blurThumbnail == true
            val boundKey = item.key
            val request = ImageRequest.Builder(ctx)
                .data(imageUrl)
                .size(cardWidthPx.coerceAtLeast(1), cardHeightPx.coerceAtLeast(1))
                .allowHardware(false)
                .crossfade(false)
                .apply {
                    if (shouldBlurThumbnail) {
                        transformations(BlurTransformation())
                    }
                }
                .target(
                    object : Target {
                        override fun onStart(placeholder: Image?) {
                            if (currentItem?.key != boundKey) return
                            thumbnailImage.setImageDrawable(placeholder?.asDrawable(ctx.resources))
                        }

                        override fun onSuccess(result: Image) {
                            if (currentItem?.key != boundKey) return
                            thumbnailImage.setImageDrawable(result.asDrawable(ctx.resources))
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

                        override fun onError(error: Image?) {
                            if (currentItem?.key != boundKey) return
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
        thumbnailImage.setImageDrawable(null)
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

            val glowSpreadDp = 18f
            val glowSpreadPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, glowSpreadDp, context.resources.displayMetrics)
            val effectiveGlowMarginPx = (glowSpreadPx * 1.35f).toInt()
            val effectiveBorderWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2.0f, context.resources.displayMetrics).toInt().coerceAtLeast(2)

            val glowView = PlayStoreGlowView(context).apply {
                this.cornerRadiusPx = cornerRadiusPx
                this.glowSpreadPx = glowSpreadPx
                this.glowMarginPx = effectiveGlowMarginPx.toFloat()
                layoutParams = FrameLayout.LayoutParams(
                    cardWidthPx + (effectiveGlowMarginPx * 2),
                    cardHeightPx + (effectiveGlowMarginPx * 2)
                ).apply {
                    leftMargin = -effectiveGlowMarginPx
                    topMargin = -effectiveGlowMarginPx
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

            // Episode text block (badge, title, overview, meta) anchored above the progress bar.
            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    leftMargin = (10f * dp).toInt()
                    rightMargin = (10f * dp).toInt()
                    bottomMargin = (8f * dp).toInt()
                }
            }

            // Episode Badge (e.g. "EP 1")
            val episodeBadge = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (4f * dp).toInt()
                }
                setPadding((6f * dp).toInt(), (2f * dp).toInt(), (6f * dp).toInt(), (2f * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#B3202024"))
                    cornerRadius = 4f * dp
                }
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            }
            textContainer.addView(episodeBadge)

            // Title inside card bottom
            val titleText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
                isSingleLine = true
            }
            textContainer.addView(titleText)

            // Episode overview. The old Compose card showed it, and it is what lets viewers
            // decide which episode to watch without opening the options overlay.
            val descriptionText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (2f * dp).toInt()
                }
                setTextColor(Color.parseColor("#D8FFFFFF"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setLineSpacing(0f, 1.15f)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                visibility = View.GONE
            }
            textContainer.addView(descriptionText)

            // Meta text (runtime, rating)
            val metaText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (2f * dp).toInt()
                }
                setTextColor(Color.parseColor("#A0A0A5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            textContainer.addView(metaText)

            cardContainer.addView(textContainer)

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

            // Release date chip (top-start). It is a separate view so runtime/rating can never
            // push the air date out of the card's single-line meta row.
            val dateText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    leftMargin = (8f * dp).toInt()
                    rightMargin = (38f * dp).toInt()
                    topMargin = (8f * dp).toInt()
                }
                setPadding((6f * dp).toInt(), (2f * dp).toInt(), (6f * dp).toInt(), (2f * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#B3202024"))
                    cornerRadius = 4f * dp
                }
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                visibility = View.GONE
            }
            cardContainer.addView(dateText)

            // Watched indicator badge (top-end)
            val watchedSizePx = (22f * dp).toInt()
            val watchedBadgeView = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    watchedSizePx,
                    watchedSizePx
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = (8f * dp).toInt()
                    rightMargin = (8f * dp).toInt()
                }
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#D910B981"))
                }
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                text = "✓"
                visibility = View.GONE
            }
            cardContainer.addView(watchedBadgeView)

            root.addView(cardContainer)

            return LeanbackEpisodeViewHolder(
                root = root,
                cardContainer = cardContainer,
                glowView = glowView,
                thumbnailImage = thumbnailImage,
                episodeBadge = episodeBadge,
                watchedBadgeView = watchedBadgeView,
                titleText = titleText,
                descriptionText = descriptionText,
                dateText = dateText,
                metaText = metaText,
                progressBar = progressBar,
                cornerRadiusPx = cornerRadiusPx,
                borderWidthPx = effectiveBorderWidthPx,
                glowMarginPx = effectiveGlowMarginPx,
                adapter = adapter
            )
        }
    }
}

private fun formatRuntimeMinutes(runtimeMinutes: Int?): String? {
    if (runtimeMinutes == null || runtimeMinutes <= 0) return null
    val hours = runtimeMinutes / 60
    val minutes = runtimeMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

