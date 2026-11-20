package com.nuvio.tv.ui.leanback.holder

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
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
import com.nuvio.tv.domain.model.isPlaceholder
import com.nuvio.tv.ui.leanback.adapter.LeanbackUniversalAdapter
import com.nuvio.tv.ui.leanback.core.LeanbackArtworkLoader
import com.nuvio.tv.ui.leanback.core.LeanbackColorEngine
import com.nuvio.tv.ui.leanback.core.PillProgressBarDrawable
import com.nuvio.tv.ui.leanback.core.PlayStoreGlowView
import com.nuvio.tv.ui.leanback.model.LeanbackCardItem

/**
 * Leanback ViewHolder for standard Poster cards (Portrait 2:3 or Landscape 16:9).
 * Used across Classic Home, Library, Catalog, Search, Cast Filmography, Collection, and More Like This.
 */
class LeanbackPosterViewHolder(
    root: LinearLayout,
    cardContainer: FrameLayout,
    glowView: PlayStoreGlowView,
    val posterImage: ImageView,
    val titleText: TextView?,
    val subtitleText: TextView?,
    val progressBar: ProgressBar?,
    val badgeView: TextView?,
    val watchedBadgeView: TextView?,
    cornerRadiusPx: Float,
    borderWidthPx: Int,
    glowMarginPx: Int,
    adapter: LeanbackUniversalAdapter
) : LeanbackBaseViewHolder(
    root, cardContainer, glowView, cornerRadiusPx, borderWidthPx, glowMarginPx, adapter
) {
    private var imageDisposable: Disposable? = null
    private var boundImageUrl: String? = null
    private var boundFallbackUrl: String? = null
    private var boundWidthPx: Int = 0
    private var boundHeightPx: Int = 0
    private var lastLoadedColorKey: String? = null

    init {
        posterImage.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = this@LeanbackPosterViewHolder.cornerRadiusPx
                if (radius > 0f) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
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

    override fun updateFocusVisual(hasFocus: Boolean) {
        val item = currentItem
        if (hasFocus && item != null) {
            val loadableUrl = item.imageUrl?.takeIf { it.isNotBlank() && !it.trim().isPlaceholder() }
            val colorCacheKey = loadableUrl ?: item.key
            val cachedColor = LeanbackColorEngine.getCachedColor(colorCacheKey)
            if (cachedColor != null) {
                currentDominantColor = cachedColor
                lastLoadedColorKey = colorCacheKey
            } else if (loadableUrl != null && lastLoadedColorKey != colorCacheKey) {
                val bmp = (posterImage.drawable as? BitmapDrawable)?.bitmap
                if (bmp != null && !bmp.isRecycled) {
                    lastLoadedColorKey = colorCacheKey
                    val boundKey = item.key
                    LeanbackColorEngine.extractDominantColorAsync(colorCacheKey, bmp) { extracted ->
                        if (currentItem?.key == boundKey) {
                            val changed = currentDominantColor != extracted
                            currentDominantColor = extracted
                            if (root.hasFocus() && changed) {
                                super.updateFocusVisual(true)
                            }
                        }
                    }
                }
            }
        }
        super.updateFocusVisual(hasFocus)
    }

    override fun bind(
        item: LeanbackCardItem,
        cardWidthPx: Int,
        cardHeightPx: Int,
        cornerRadiusPx: Float,
        showLabels: Boolean
    ) {
        val posterItem = item as? LeanbackCardItem.Poster
        val rawImageUrl = item.imageUrl
        val hasLoadableImage = !rawImageUrl.isNullOrBlank() && !rawImageUrl.trim().isPlaceholder()
        val imageUrl = if (hasLoadableImage) rawImageUrl else null
        val fallbackUrl = posterItem?.fallbackImageUrl?.takeIf { !it.isBlank() && !it.trim().isPlaceholder() }
        val wPx = cardWidthPx.coerceAtLeast(1)
        val hPx = cardHeightPx.coerceAtLeast(1)
        val sameImageRequest = hasLoadableImage &&
            currentItem?.key == item.key &&
            boundImageUrl == imageUrl &&
            boundFallbackUrl == fallbackUrl &&
            boundWidthPx == wPx &&
            boundHeightPx == hPx &&
            (posterImage.drawable != null || imageDisposable?.isDisposed == false)

        currentItem = item
        updateCornerRadius(cornerRadiusPx)

        if (!sameImageRequest) {
            lastLoadedColorKey = null
            posterImage.setImageDrawable(null)
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

        // Title & Subtitle text
        if (showLabels && titleText != null && item.title.isNotBlank()) {
            titleText.visibility = View.VISIBLE
            titleText.text = item.title
        } else {
            titleText?.visibility = View.GONE
        }

        val subtitle = posterItem?.subtitle
        if (showLabels && subtitleText != null && !subtitle.isNullOrBlank()) {
            subtitleText.visibility = View.VISIBLE
            subtitleText.text = subtitle
        } else {
            subtitleText?.visibility = View.GONE
        }

        // Progress bar
        val progress = posterItem?.progressPercent
        if (progress != null && progress > 0 && progressBar != null) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = progress.coerceIn(0, 100)
        } else {
            progressBar?.visibility = View.GONE
        }

        // Badge text (top-end)
        val badge = posterItem?.badgeText
        if (!badge.isNullOrBlank() && badgeView != null) {
            badgeView.visibility = View.VISIBLE
            badgeView.text = badge
        } else {
            badgeView?.visibility = View.GONE
        }

        // Watched indicator badge (top-start)
        if (posterItem?.isWatched == true && watchedBadgeView != null) {
            watchedBadgeView.visibility = View.VISIBLE
        } else {
            watchedBadgeView?.visibility = View.GONE
        }

        // Coil image loading with zero GPU overdraw & MemoryCache fast-path
        if (imageUrl != null) {
            if (!sameImageRequest) {
                val boundKey = item.key
                boundImageUrl = imageUrl
                boundFallbackUrl = fallbackUrl
                boundWidthPx = wPx
                boundHeightPx = hPx
                imageDisposable?.dispose()
                imageDisposable = LeanbackArtworkLoader.loadIntoImageView(
                    imageView = posterImage,
                    imageUrl = imageUrl,
                    fallbackUrl = fallbackUrl,
                    widthPx = wPx,
                    heightPx = hPx,
                    isStillBound = { currentItem?.key == boundKey },
                    onCacheHit = { result ->
                        cardContainer.background = null
                        if (root.hasFocus() || bindingAdapterPosition == 0) {
                            lastLoadedColorKey = colorCacheKey
                            resolveDominantColorFromImage(
                                result = result,
                                boundKey = boundKey,
                                colorCacheKey = colorCacheKey,
                                notifyIfCachedAndFocused = false
                            )
                        }
                    },
                    onAsyncSuccess = { result ->
                        cardContainer.background = null
                        if (root.hasFocus() || bindingAdapterPosition == 0) {
                            lastLoadedColorKey = colorCacheKey
                            resolveDominantColorFromImage(
                                result = result,
                                boundKey = boundKey,
                                colorCacheKey = colorCacheKey,
                                notifyIfCachedAndFocused = true
                            )
                        }
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
            boundFallbackUrl = null
            boundWidthPx = 0
            boundHeightPx = 0
            lastLoadedColorKey = null
            posterImage.setImageDrawable(null)
            ensurePlaceholderBackground()
        }

        updateFocusVisual(root.hasFocus())
    }

    override fun unbind() {
        super.unbind()
        imageDisposable?.dispose()
        imageDisposable = null
        boundImageUrl = null
        boundFallbackUrl = null
        boundWidthPx = 0
        boundHeightPx = 0
        lastLoadedColorKey = null
        posterImage.setImageDrawable(null)
    }

    companion object {
        fun create(
            context: Context,
            cardWidthPx: Int,
            cardHeightPx: Int,
            cornerRadiusPx: Float,
            showLabels: Boolean,
            adapter: LeanbackUniversalAdapter
        ): LeanbackPosterViewHolder {
            val chrome = computeLeanbackCardChromeMetrics(context)
            val dp = context.resources.displayMetrics.density

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

            val posterImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cardContainer.addView(posterImage)

            val pbHeightPx = 4f * dp
            val horizMargin = (cornerRadiusPx * 0.75f).toInt().coerceAtLeast(6)
            val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    pbHeightPx.toInt().coerceAtLeast(2)
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

            val watchedSizePx = (20f * dp).toInt()
            val watchedBadgeView = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    watchedSizePx,
                    watchedSizePx
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    topMargin = (6f * dp).toInt()
                    leftMargin = (6f * dp).toInt()
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

            val titleText = TextView(context).apply {
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
                visibility = if (showLabels) View.VISIBLE else View.GONE
            }.also { root.addView(it) }

            val subtitleText = TextView(context).apply {
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
                gravity = Gravity.CENTER_HORIZONTAL
                visibility = View.GONE
            }.also { root.addView(it) }

            return LeanbackPosterViewHolder(
                root = root,
                cardContainer = cardContainer,
                glowView = glowView,
                posterImage = posterImage,
                titleText = titleText,
                subtitleText = subtitleText,
                progressBar = progressBar,
                badgeView = badgeView,
                watchedBadgeView = watchedBadgeView,
                cornerRadiusPx = cornerRadiusPx,
                borderWidthPx = chrome.borderWidthPx,
                glowMarginPx = chrome.glowMarginPx,
                adapter = adapter
            ).also { it.ensurePlaceholderBackground() }
        }
    }
}
