package com.nuvio.tv.ui.leanback.holder

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
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
import coil3.request.Disposable
import com.nuvio.tv.R
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.core.LeanbackArtworkLoader
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import com.nuvio.tv.ui.leanback.core.PillProgressBarDrawable
import com.nuvio.tv.ui.leanback.core.PlayStoreGlowView
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem

/**
 * Leanback ViewHolder for TV Episode cards (16:9 Landscape).
 * Displays episode thumbnail, episode badge (e.g. "EP 1" / "S1 E1"),
 * runtime, title, progress bar, and watched marker with MemoryCache fast-path.
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
    private var imageDisposable: Disposable? = null
    private var boundImageUrl: String? = null
    private var boundBlurred: Boolean = false
    private var boundWidthPx: Int = 0
    private var boundHeightPx: Int = 0

    init {
        thumbnailImage.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = this@LeanbackEpisodeViewHolder.cornerRadiusPx
                if (radius > 0f) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
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
        val epItem = item as? LeanbackCardItem.Episode
        val imageUrl = item.imageUrl
        val shouldBlurThumbnail = epItem?.blurThumbnail == true
        val wPx = cardWidthPx.coerceAtLeast(1)
        val hPx = cardHeightPx.coerceAtLeast(1)
        val sameImageRequest = currentItem?.key == item.key &&
            boundImageUrl == imageUrl &&
            boundBlurred == shouldBlurThumbnail &&
            boundWidthPx == wPx &&
            boundHeightPx == hPx &&
            (thumbnailImage.drawable != null || imageDisposable?.isDisposed == false)

        currentItem = item
        updateCornerRadius(cornerRadiusPx)

        if (!sameImageRequest) {
            thumbnailImage.setImageDrawable(null)
            ensurePlaceholderBackground()
        }

        val colorCacheKey = imageUrl ?: item.key
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

        // Episode Badge (e.g. "S1 E3") plus availability state
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
        titleText.isSelected = root.hasFocus()

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

        // Release date chip
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
            val formattedRuntime = formatRuntimeMinutes(epItem?.runtimeMinutes)
            val formattedRating = epItem?.imdbRating?.takeIf { it.isNotBlank() }?.let { "★ $it" }
            val metaStr = when {
                formattedRuntime != null && formattedRating != null -> "$formattedRuntime • $formattedRating"
                formattedRuntime != null -> formattedRuntime
                formattedRating != null -> formattedRating
                else -> null
            }
            if (metaStr != null) {
                metaText.visibility = View.VISIBLE
                metaText.text = metaStr
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

        // Coil image loading with MemoryCache fast-path
        if (!imageUrl.isNullOrBlank()) {
            if (!sameImageRequest) {
                val boundKey = item.key
                boundImageUrl = imageUrl
                boundBlurred = shouldBlurThumbnail
                boundWidthPx = wPx
                boundHeightPx = hPx
                imageDisposable?.dispose()
                imageDisposable = LeanbackArtworkLoader.loadIntoImageView(
                    imageView = thumbnailImage,
                    imageUrl = imageUrl,
                    widthPx = wPx,
                    heightPx = hPx,
                    blurred = shouldBlurThumbnail,
                    isStillBound = { currentItem?.key == boundKey },
                    onCacheHit = { result ->
                        cardContainer.background = null
                        resolveDominantColorFromImage(
                            result = result,
                            boundKey = boundKey,
                            colorCacheKey = colorCacheKey,
                            notifyIfCachedAndFocused = false
                        )
                    },
                    onAsyncSuccess = { result ->
                        cardContainer.background = null
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
                    }
                )
            }
        } else {
            imageDisposable?.dispose()
            imageDisposable = null
            boundImageUrl = null
            boundBlurred = false
            thumbnailImage.setImageDrawable(null)
            ensurePlaceholderBackground()
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
        boundImageUrl = null
        boundBlurred = false
        boundWidthPx = 0
        boundHeightPx = 0
        thumbnailImage.setImageDrawable(null)
    }

    companion object {
        fun create(
            context: Context,
            cardWidthPx: Int,
            cardHeightPx: Int,
            cornerRadiusPx: Float,
            adapter: LeanbackUniversalAdapter
        ): LeanbackEpisodeViewHolder {
            val chrome = computeLeanbackCardChromeMetrics(context)
            val dp = context.resources.displayMetrics.density

            val root = FocusIsolatedRootLayout(context).apply {
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
            }

            val glowView = PlayStoreGlowView(context).apply {
                this.cornerRadiusPx = cornerRadiusPx
                this.glowSpreadPx = chrome.glowSpreadPx
                this.glowMarginPx = chrome.glowMarginPx.toFloat()
                layoutParams = FrameLayout.LayoutParams(
                    cardWidthPx + (chrome.glowMarginPx * 2),
                    cardHeightPx + (chrome.glowMarginPx * 2)
                ).apply {
                    leftMargin = -chrome.glowMarginPx
                    topMargin = -chrome.glowMarginPx
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

            val pbHeightPx = 3.5f * dp
            val horizMargin = (cornerRadiusPx * 0.75f).toInt().coerceAtLeast(6)
            val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    pbHeightPx.toInt().coerceAtLeast(2)
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
                borderWidthPx = chrome.borderWidthPx,
                glowMarginPx = chrome.glowMarginPx,
                adapter = adapter
            ).also { it.ensurePlaceholderBackground() }
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
