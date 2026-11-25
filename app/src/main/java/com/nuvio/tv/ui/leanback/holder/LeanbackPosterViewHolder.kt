package com.nuvio.tv.ui.leanback.holder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
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
import com.nuvio.tv.ui.leanback.container.InteropHorizontalGridView
import com.nuvio.tv.ui.leanback.core.LeanbackArtworkLoader
import com.nuvio.tv.ui.leanback.core.LeanbackCardExpandSpec
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
    val seeAllOverlay: LinearLayout? = null,
    val seeAllArrowText: TextView? = null,
    val seeAllLabelText: TextView? = null,
    val fallbackTitleText: TextView? = null,
    private val progressBarHeightPx: Float = 8f,
    cornerRadiusPx: Float,
    borderWidthPx: Int,
    glowMarginPx: Int,
    adapter: LeanbackUniversalAdapter,
    val mediaClipContainer: FrameLayout? = null,
    val backdropImage: ImageView? = null,
    val trailerContainer: FrameLayout? = null,
    val trailerCoverImage: ImageView? = null,
    val expandedGradientView: View? = null,
    val expandedLogoImage: ImageView? = null,
    val expandedTitleFallback: TextView? = null,
    val expandedLabelContainer: LinearLayout? = null,
    val expandedMetaText: TextView? = null,
    val expandedAgeRatingBadge: TextView? = null,
    val expandedDescriptionText: TextView? = null
) : LeanbackBaseViewHolder(
    root, cardContainer, glowView, cornerRadiusPx, borderWidthPx, glowMarginPx, adapter
) {
    private var imageDisposable: Disposable? = null
    private var boundImageUrl: String? = null
    private var boundFallbackUrl: String? = null
    private var boundWidthPx: Int = 0
    private var boundHeightPx: Int = 0
    private var lastLoadedColorKey: String? = null
    private var lastProgressAccentColor: Int = 0

    var isBackdropExpanded: Boolean = false
        private set
    private var widthAnimator: ValueAnimator? = null
    private var animatingTargetWidthPx: Int = -1
    val isAnimatingWidth: Boolean
        get() = widthAnimator?.isRunning == true
    private var backdropDisposable: Disposable? = null
    private var logoDisposable: Disposable? = null
    private var boundBackdropUrl: String? = null
    private var boundLogoUrl: String? = null
    private var logoLoadFailed: Boolean = false
    private var trailerFirstFrameRendered: Boolean = false
    private var activeTrailerOwnerKey: String? = null
    private var lastBoundCardWidthPx: Int = 0
    private var lastBoundCardHeightPx: Int = 0
    private var lastBoundShowLabels: Boolean = true

    init {
        val clipOutlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = this@LeanbackPosterViewHolder.cornerRadiusPx
                if (radius > 0f) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                } else {
                    outline.setRect(0, 0, view.width, view.height)
                }
            }
        }
        if (mediaClipContainer != null) {
            mediaClipContainer.outlineProvider = clipOutlineProvider
            mediaClipContainer.clipToOutline = true
        } else {
            posterImage.outlineProvider = clipOutlineProvider
            posterImage.clipToOutline = true
        }
    }

    override fun updateCornerRadius(newRadiusPx: Float) {
        super.updateCornerRadius(newRadiusPx)
        mediaClipContainer?.invalidateOutline()
        posterImage.invalidateOutline()
        val horizMargin = (newRadiusPx * 0.75f).toInt().coerceAtLeast(6)
        (progressBar?.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
            if (lp.marginStart != horizMargin || lp.marginEnd != horizMargin) {
                lp.marginStart = horizMargin
                lp.marginEnd = horizMargin
                progressBar.layoutParams = lp
            }
        }
    }

    override fun updateFocusVisual(hasFocus: Boolean) {
        val gridView = adapter.attachedRecyclerView as? InteropHorizontalGridView
        val isHoldingHorizontal = gridView?.isHoldingHorizontalKey == true
        if (!hasFocus && isBackdropExpanded) {
            val shouldAnimateCollapse = adapter.animationsEnabled() &&
                gridView?.isAttachedToWindow == true &&
                gridView.mayTakeFocus() &&
                !isHoldingHorizontal &&
                gridView.scrollState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE
            if (shouldAnimateCollapse) {
                collapseToBaseWidth(animated = true)
            } else {
                resetExpandedStateImmediate()
            }
        }
        val item = currentItem
        if (hasFocus && item != null) {
            if (!isHoldingHorizontal && item is LeanbackCardItem.Poster) {
                prefetchExpandedBackdropIfNeeded(item)
            }
            val loadableUrl = item.imageUrl?.takeIf { it.isNotBlank() && !it.trim().isPlaceholder() }
            val colorCacheKey = loadableUrl ?: item.key
            val cachedColor = LeanbackColorEngine.getCachedColor(colorCacheKey)
            if (cachedColor != null) {
                currentDominantColor = cachedColor
                lastLoadedColorKey = colorCacheKey
            } else if (!isHoldingHorizontal && loadableUrl != null && lastLoadedColorKey != colorCacheKey) {
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
        if (widthAnimator != null) {
            glowView.visibility = View.GONE
        }
        val shouldMarquee = hasFocus && !isHoldingHorizontal
        if (titleText?.isSelected != shouldMarquee) {
            titleText?.isSelected = shouldMarquee
        }
    }

    private fun currentEffectiveCardWidthPx(): Int {
        val baseW = lastBoundCardWidthPx.takeIf { it > 0 } ?: adapter.cardWidthPx
        return LeanbackCardExpandSpec.resolveEffectiveCardDimensionPx(
            explicitPx = null,
            layoutParamPx = cardContainer.layoutParams?.width,
            measuredPx = cardContainer.width,
            isLayoutRequested = cardContainer.isLayoutRequested,
            fallbackPx = baseW
        )
    }

    private fun applyWidthPx(widthPx: Int, heightPx: Int) {
        val safeW = widthPx.coerceAtLeast(1)
        val safeH = heightPx.coerceAtLeast(1)
        var layoutMutated = false
        val cardLp = cardContainer.layoutParams
        if (cardLp != null && (cardLp.width != safeW || cardLp.height != safeH || cardContainer.width != safeW || cardContainer.height != safeH)) {
            cardLp.width = safeW
            cardLp.height = safeH
            cardContainer.layoutParams = cardLp
            layoutMutated = true
        }
        val glowLp = glowView.layoutParams as? FrameLayout.LayoutParams
        val targetGlowW = safeW + (glowMarginPx * 2)
        val targetGlowH = safeH + (glowMarginPx * 2)
        if (glowLp != null && (glowLp.width != targetGlowW || glowLp.height != targetGlowH || glowView.width != targetGlowW || glowView.height != targetGlowH)) {
            glowLp.width = targetGlowW
            glowLp.height = targetGlowH
            glowView.layoutParams = glowLp
            layoutMutated = true
        }
        val rootLp = root.layoutParams
        if (rootLp != null && (rootLp.width != safeW || root.width != safeW)) {
            rootLp.width = safeW
            root.layoutParams = rootLp
            layoutMutated = true
        }
        mediaClipContainer?.invalidateOutline()
        cardContainer.invalidate()
        if (layoutMutated) {
            val gridView = adapter.attachedRecyclerView
            if (gridView != null && gridView.isAttachedToWindow && !gridView.isInLayout) {
                gridView.requestLayout()
            }
        }
    }

    private fun prefetchExpandedBackdropIfNeeded(item: LeanbackCardItem.Poster) {
        if (!adapter.backdropExpandEnabled || item.isSeeAll || item.key.startsWith("__placeholder_")) return
        val baseW = lastBoundCardWidthPx.takeIf { it > 0 } ?: adapter.cardWidthPx
        val baseH = lastBoundCardHeightPx.takeIf { it > 0 } ?: adapter.cardHeightPx
        val expandedW = LeanbackCardExpandSpec.computeExpandedCardWidthPx(
            baseWidthPx = baseW,
            cardHeightPx = baseH,
            isLandscape = item.isLandscape
        )
        val resolution = LeanbackCardExpandSpec.resolveExpandedBackdropArtwork(
            posterUrl = item.imageUrl,
            backdropUrl = item.backdropUrl,
            landscapePosterUrl = item.landscapePosterUrl,
            rawPosterFallbackUrl = item.fallbackImageUrl,
            alwaysShowLandscapeClearlogo = adapter.alwaysShowLandscapeClearlogo,
            isLandscape = item.isLandscape
        )
        val ctx = root.context
        if (!resolution.imageUrl.isNullOrBlank()) {
            LeanbackArtworkLoader.prefetch(
                context = ctx,
                imageUrl = resolution.imageUrl,
                fallbackUrl = resolution.fallbackUrl,
                widthPx = expandedW,
                heightPx = baseH
            )
        }
        val logo = item.logoUrl?.takeIf { it.isNotBlank() && !it.isPlaceholder() }
        if (resolution.showOverlay && logo != null) {
            val logoH = (baseH * LeanbackCardExpandSpec.EXPANDED_LOGO_HEIGHT_FRACTION).toInt().coerceAtLeast(1)
            val logoW = (expandedW * LeanbackCardExpandSpec.EXPANDED_LOGO_WIDTH_FRACTION).toInt().coerceAtLeast(1)
            LeanbackArtworkLoader.prefetch(
                context = ctx,
                imageUrl = logo,
                widthPx = logoW,
                heightPx = logoH
            )
        }
    }

    private fun updateExpandedOverlayVisibility(
        item: LeanbackCardItem.Poster,
        showOverlay: Boolean,
        expandedWidthPx: Int,
        cardHeightPx: Int
    ) {
        val grad = expandedGradientView
        val logoView = expandedLogoImage
        val fallbackTitle = expandedTitleFallback
        if (!showOverlay) {
            grad?.visibility = View.GONE
            logoView?.visibility = View.GONE
            fallbackTitle?.visibility = View.GONE
            return
        }
        grad?.visibility = View.VISIBLE
        val logoUrl = item.logoUrl?.takeIf { it.isNotBlank() && !it.isPlaceholder() }
        val hasLogo = logoUrl != null && !logoLoadFailed && logoView != null
        if (hasLogo) {
            fallbackTitle?.visibility = View.GONE
            val logoH = (cardHeightPx * LeanbackCardExpandSpec.EXPANDED_LOGO_HEIGHT_FRACTION).toInt().coerceAtLeast(1)
            val logoW = (expandedWidthPx * LeanbackCardExpandSpec.EXPANDED_LOGO_WIDTH_FRACTION).toInt().coerceAtLeast(1)
            val lp = logoView.layoutParams as? FrameLayout.LayoutParams
            if (lp != null && (lp.width != logoW || lp.height != logoH)) {
                lp.width = logoW
                lp.height = logoH
                logoView.layoutParams = lp
            }
            logoView.visibility = View.VISIBLE
            if (boundLogoUrl != logoUrl || logoView.drawable == null) {
                boundLogoUrl = logoUrl
                logoDisposable?.dispose()
                val boundKey = item.key
                logoDisposable = LeanbackArtworkLoader.loadIntoImageView(
                    imageView = logoView,
                    imageUrl = logoUrl,
                    widthPx = logoW,
                    heightPx = logoH,
                    isStillBound = { currentItem?.key == boundKey && isBackdropExpanded },
                    onCacheHit = {
                        logoView.visibility = View.VISIBLE
                        fallbackTitle?.visibility = View.GONE
                    },
                    onAsyncSuccess = {
                        logoView.visibility = View.VISIBLE
                        fallbackTitle?.visibility = View.GONE
                    },
                    onAsyncError = {
                        logoLoadFailed = true
                        logoView.visibility = View.GONE
                        if (isBackdropExpanded && currentItem?.key == boundKey) {
                            fallbackTitle?.text = item.title
                            fallbackTitle?.visibility = if (item.title.isNotBlank()) View.VISIBLE else View.GONE
                        }
                    }
                )
            }
        } else {
            logoView?.visibility = View.GONE
            if (fallbackTitle != null && item.title.isNotBlank()) {
                val maxW = (expandedWidthPx * LeanbackCardExpandSpec.EXPANDED_LOGO_WIDTH_FRACTION).toInt().coerceAtLeast(1)
                val lp = fallbackTitle.layoutParams
                if (lp != null && lp.width != maxW) {
                    lp.width = maxW
                    fallbackTitle.layoutParams = lp
                }
                fallbackTitle.text = item.title
                fallbackTitle.visibility = View.VISIBLE
            } else {
                fallbackTitle?.visibility = View.GONE
            }
        }
    }

    private fun updateExpandedLabels(item: LeanbackCardItem.Poster, expanded: Boolean) {
        val style = adapter.themeStyle
        if (expanded && lastBoundShowLabels && expandedLabelContainer != null) {
            titleText?.visibility = View.GONE
            subtitleText?.visibility = View.GONE
            expandedLabelContainer.visibility = View.VISIBLE

            val tokensText = item.expandedMetaTokens.joinToString("  •  ")
            val ageRating = item.ageRating?.trim()?.takeIf { it.isNotEmpty() }
            if (expandedMetaText != null) {
                expandedMetaText.setTextColor(style.subtitleColor)
                style.typeface?.let { if (expandedMetaText.typeface !== it) expandedMetaText.typeface = it }
                if (tokensText.isNotEmpty()) {
                    expandedMetaText.text = tokensText
                    expandedMetaText.visibility = View.VISIBLE
                } else {
                    expandedMetaText.visibility = View.GONE
                }
            }
            if (expandedAgeRatingBadge != null) {
                if (ageRating != null) {
                    expandedAgeRatingBadge.text = ageRating
                    expandedAgeRatingBadge.setTextColor(style.subtitleColor)
                    expandedAgeRatingBadge.visibility = View.VISIBLE
                } else {
                    expandedAgeRatingBadge.visibility = View.GONE
                }
            }
            if (expandedDescriptionText != null) {
                expandedDescriptionText.setTextColor(style.titleColor)
                style.typeface?.let { if (expandedDescriptionText.typeface !== it) expandedDescriptionText.typeface = it }
                val desc = item.description?.trim()
                if (!desc.isNullOrEmpty()) {
                    expandedDescriptionText.text = desc
                    expandedDescriptionText.visibility = View.VISIBLE
                } else {
                    expandedDescriptionText.visibility = View.GONE
                }
            }
        } else {
            expandedLabelContainer?.visibility = View.GONE
            if (lastBoundShowLabels && !item.isSeeAll) {
                if (titleText != null && item.title.isNotBlank()) {
                    titleText.visibility = View.VISIBLE
                    titleText.text = item.title
                } else {
                    titleText?.visibility = View.GONE
                }
                val subtitle = item.subtitle
                if (subtitleText != null && !subtitle.isNullOrBlank()) {
                    subtitleText.visibility = View.VISIBLE
                    subtitleText.text = subtitle
                } else {
                    subtitleText?.visibility = View.GONE
                }
            }
        }
    }

    internal fun applyExpandedState(
        expanded: Boolean,
        animated: Boolean,
        trailerUrl: String? = null,
        trailerAudioUrl: String? = null
    ) {
        val posterItem = currentItem as? LeanbackCardItem.Poster
        val canExpand = expanded &&
            posterItem != null &&
            !posterItem.isSeeAll &&
            !posterItem.key.startsWith("__placeholder_") &&
            adapter.backdropExpandEnabled &&
            root.hasFocus()

        if (!canExpand) {
            if (isBackdropExpanded || widthAnimator != null || activeTrailerOwnerKey != null) {
                collapseToBaseWidth(animated = animated)
            }
            return
        }

        val item = posterItem
        val ownerKey = item.key
        val baseW = lastBoundCardWidthPx.takeIf { it > 0 } ?: adapter.cardWidthPx
        val baseH = lastBoundCardHeightPx.takeIf { it > 0 } ?: adapter.cardHeightPx
        val targetW = LeanbackCardExpandSpec.computeExpandedCardWidthPx(
            baseWidthPx = baseW,
            cardHeightPx = baseH,
            isLandscape = item.isLandscape
        )
        val wasExpanded = isBackdropExpanded
        isBackdropExpanded = true

        val resolution = LeanbackCardExpandSpec.resolveExpandedBackdropArtwork(
            posterUrl = item.imageUrl,
            backdropUrl = item.backdropUrl,
            landscapePosterUrl = item.landscapePosterUrl,
            rawPosterFallbackUrl = item.fallbackImageUrl,
            alwaysShowLandscapeClearlogo = adapter.alwaysShowLandscapeClearlogo,
            isLandscape = item.isLandscape,
            trailerFirstFrameRendered = trailerFirstFrameRendered
        )

        val bdView = backdropImage
        val resolvedArtworkUrl = resolution.imageUrl
        if (bdView != null && !resolvedArtworkUrl.isNullOrBlank()) {
            if (boundBackdropUrl != resolvedArtworkUrl || bdView.drawable == null) {
                boundBackdropUrl = resolvedArtworkUrl
                backdropDisposable?.dispose()
                backdropDisposable = LeanbackArtworkLoader.loadIntoImageView(
                    imageView = bdView,
                    imageUrl = resolvedArtworkUrl,
                    fallbackUrl = resolution.fallbackUrl,
                    widthPx = targetW,
                    heightPx = baseH,
                    isStillBound = { currentItem?.key == ownerKey && isBackdropExpanded },
                    onCacheHit = {
                        bdView.animate().cancel()
                        bdView.visibility = View.VISIBLE
                        if (!wasExpanded && animated && adapter.animationsEnabled()) {
                            bdView.alpha = 0f
                            bdView.animate()
                                .alpha(1f)
                                .setDuration(LeanbackCardExpandSpec.BACKDROP_FADE_IN_DURATION_MS)
                                .start()
                        } else {
                            bdView.alpha = 1f
                        }
                        if (trailerContainer?.visibility == View.VISIBLE && !trailerFirstFrameRendered) {
                            trailerCoverImage?.setImageDrawable(bdView.drawable)
                        }
                    },
                    onAsyncSuccess = {
                        bdView.animate().cancel()
                        bdView.visibility = View.VISIBLE
                        if (adapter.animationsEnabled()) {
                            bdView.alpha = 0f
                            bdView.animate()
                                .alpha(1f)
                                .setDuration(LeanbackCardExpandSpec.BACKDROP_FADE_IN_DURATION_MS)
                                .start()
                        } else {
                            bdView.alpha = 1f
                        }
                        if (trailerContainer?.visibility == View.VISIBLE && !trailerFirstFrameRendered) {
                            trailerCoverImage?.setImageDrawable(bdView.drawable)
                        }
                    },
                    onAsyncError = {
                        bdView.visibility = View.GONE
                    }
                )
            } else {
                bdView.alpha = 1f
                bdView.visibility = View.VISIBLE
            }
        } else {
            bdView?.visibility = View.GONE
        }

        updateExpandedOverlayVisibility(
            item = item,
            showOverlay = resolution.showOverlay,
            expandedWidthPx = targetW,
            cardHeightPx = baseH
        )
        updateExpandedLabels(item = item, expanded = true)

        val host = adapter.expandedTrailerHost
        val container = trailerContainer
        val shouldPlayTrailer = adapter.playTrailerInExpandedCard &&
            !trailerUrl.isNullOrBlank() &&
            host != null &&
            container != null
        if (shouldPlayTrailer) {
            val cover = trailerCoverImage
            if (!host.isPlayingFor(ownerKey)) {
                trailerFirstFrameRendered = false
                if (cover != null) {
                    cover.animate().cancel()
                    cover.setImageDrawable(backdropImage?.drawable ?: posterImage.drawable)
                    cover.alpha = 1f
                    cover.visibility = View.VISIBLE
                }
            }
            activeTrailerOwnerKey = ownerKey
            host.attachAndPlay(
                container = container,
                ownerKey = ownerKey,
                videoUrl = trailerUrl,
                audioUrl = trailerAudioUrl,
                muted = adapter.trailerMuted,
                cropToFill = false,
                overscanZoom = 1f,
                onFirstFrameRendered = {
                    if (currentItem?.key != ownerKey || !isBackdropExpanded) return@attachAndPlay
                    trailerFirstFrameRendered = true
                    cover?.let { coverView ->
                        coverView.animate().cancel()
                        if (adapter.animationsEnabled()) {
                            coverView.animate()
                                .alpha(0f)
                                .setDuration(LeanbackCardExpandSpec.TRAILER_COVER_FADE_OUT_DURATION_MS)
                                .withEndAction { coverView.visibility = View.GONE }
                                .start()
                        } else {
                            coverView.alpha = 0f
                            coverView.visibility = View.GONE
                        }
                    }
                    val postFrameResolution = LeanbackCardExpandSpec.resolveExpandedBackdropArtwork(
                        posterUrl = item.imageUrl,
                        backdropUrl = item.backdropUrl,
                        landscapePosterUrl = item.landscapePosterUrl,
                        rawPosterFallbackUrl = item.fallbackImageUrl,
                        alwaysShowLandscapeClearlogo = adapter.alwaysShowLandscapeClearlogo,
                        isLandscape = item.isLandscape,
                        trailerFirstFrameRendered = true
                    )
                    updateExpandedOverlayVisibility(
                        item = item,
                        showOverlay = postFrameResolution.showOverlay,
                        expandedWidthPx = targetW,
                        cardHeightPx = baseH
                    )
                },
                onEnded = {
                    if (activeTrailerOwnerKey == ownerKey) {
                        activeTrailerOwnerKey = null
                    }
                    trailerFirstFrameRendered = false
                    trailerCoverImage?.visibility = View.GONE
                    adapter.onExpandedTrailerEnded()
                },
                onError = {
                    if (activeTrailerOwnerKey == ownerKey) {
                        activeTrailerOwnerKey = null
                    }
                    trailerFirstFrameRendered = false
                    trailerCoverImage?.visibility = View.GONE
                }
            )
        } else {
            detachTrailerIfOwned()
        }

        val isAlreadyAnimatingToTarget = LeanbackCardExpandSpec.shouldRetainRunningWidthAnimation(
            isRunning = widthAnimator?.isRunning == true,
            animatingTargetWidthPx = animatingTargetWidthPx,
            desiredTargetWidthPx = targetW
        )
        if (!isAlreadyAnimatingToTarget && (!wasExpanded || cardContainer.layoutParams?.width != targetW || cardContainer.width != targetW)) {
            animateOrSnapWidth(targetWidthPx = targetW, heightPx = baseH, animated = animated, expanded = true)
        }
    }

    private fun detachTrailerIfOwned() {
        val owner = activeTrailerOwnerKey
        if (owner != null) {
            adapter.expandedTrailerHost?.detach(owner)
            activeTrailerOwnerKey = null
        }
        trailerFirstFrameRendered = false
        trailerContainer?.visibility = View.GONE
        trailerCoverImage?.let {
            it.animate().cancel()
            it.setImageDrawable(null)
            it.visibility = View.GONE
        }
    }

    private fun hideExpandedVisuals() {
        detachTrailerIfOwned()
        backdropDisposable?.dispose()
        backdropDisposable = null
        logoDisposable?.dispose()
        logoDisposable = null
        boundBackdropUrl = null
        boundLogoUrl = null
        logoLoadFailed = false
        backdropImage?.let {
            it.animate().cancel()
            it.setImageDrawable(null)
            it.visibility = View.GONE
        }
        expandedGradientView?.visibility = View.GONE
        expandedLogoImage?.let {
            it.setImageDrawable(null)
            it.visibility = View.GONE
        }
        expandedTitleFallback?.visibility = View.GONE
        (currentItem as? LeanbackCardItem.Poster)?.let { updateExpandedLabels(it, expanded = false) }
            ?: run { expandedLabelContainer?.visibility = View.GONE }
    }

    internal fun collapseToBaseWidth(animated: Boolean) {
        isBackdropExpanded = false
        hideExpandedVisuals()
        val baseW = lastBoundCardWidthPx.takeIf { it > 0 } ?: adapter.cardWidthPx
        val baseH = lastBoundCardHeightPx.takeIf { it > 0 } ?: adapter.cardHeightPx
        if (baseW <= 0 || baseH <= 0) return
        val isAlreadyCollapsingToBase = LeanbackCardExpandSpec.shouldRetainRunningWidthAnimation(
            isRunning = widthAnimator?.isRunning == true,
            animatingTargetWidthPx = animatingTargetWidthPx,
            desiredTargetWidthPx = baseW
        )
        if (animated && isAlreadyCollapsingToBase) {
            return
        }
        animateOrSnapWidth(targetWidthPx = baseW, heightPx = baseH, animated = animated, expanded = false)
    }

    internal fun resetExpandedStateImmediate() {
        widthAnimator?.cancel()
        widthAnimator = null
        animatingTargetWidthPx = -1
        isBackdropExpanded = false
        hideExpandedVisuals()
        val baseW = lastBoundCardWidthPx.takeIf { it > 0 } ?: adapter.cardWidthPx
        val baseH = lastBoundCardHeightPx.takeIf { it > 0 } ?: adapter.cardHeightPx
        if (baseW > 0 && baseH > 0) {
            applyWidthPx(baseW, baseH)
        }
    }

    private fun animateOrSnapWidth(
        targetWidthPx: Int,
        heightPx: Int,
        animated: Boolean,
        expanded: Boolean
    ) {
        widthAnimator?.cancel()
        widthAnimator = null
        animatingTargetWidthPx = -1
        val startW = currentEffectiveCardWidthPx().takeIf { it > 0 } ?: targetWidthPx
        val gridView = adapter.attachedRecyclerView as? InteropHorizontalGridView

        if (!animated || !adapter.animationsEnabled() || startW == targetWidthPx || !root.isAttachedToWindow) {
            applyWidthPx(targetWidthPx, heightPx)
            if (root.hasFocus()) {
                super.updateFocusVisual(true)
                if (expanded) {
                    cardContainer.post {
                        if (root.hasFocus() && isBackdropExpanded) {
                            gridView?.ensureExpandedCardVisible(cardContainer)
                        }
                    }
                }
            }
            return
        }

        // Suspend PlayStoreGlowView software blur during the width tween to keep 60fps.
        glowView.visibility = View.GONE
        val durationMs = if (expanded) {
            LeanbackCardExpandSpec.CARD_EXPAND_ANIMATION_DURATION_MS
        } else {
            LeanbackCardExpandSpec.CARD_COLLAPSE_ANIMATION_DURATION_MS
        }
        val animator = ValueAnimator.ofInt(startW, targetWidthPx).apply {
            duration = durationMs
            interpolator = LeanbackCardExpandSpec.createExpandInterpolator()
            addUpdateListener { anim ->
                val animatedW = anim.animatedValue as Int
                applyWidthPx(animatedW, heightPx)
            }
            addListener(object : AnimatorListenerAdapter() {
                private var isCancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    isCancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (widthAnimator === animation) {
                        widthAnimator = null
                        animatingTargetWidthPx = -1
                    }
                    // Android always dispatches onAnimationEnd after cancel(); never overwrite
                    // the caller's new target width or re-enable glow after cancellation.
                    if (isCancelled) return

                    applyWidthPx(targetWidthPx, heightPx)
                    if (root.hasFocus()) {
                        super@LeanbackPosterViewHolder.updateFocusVisual(true)
                        if (isBackdropExpanded) {
                            gridView?.ensureExpandedCardVisible(cardContainer)
                        }
                    }
                }
            })
        }
        widthAnimator = animator
        animatingTargetWidthPx = targetWidthPx
        animator.start()
    }

    override fun needsImageRebind(): Boolean {
        val item = currentItem as? LeanbackCardItem.Poster ?: return false
        if (item.isSeeAll) return false
        val url = item.imageUrl?.trim()
        if (url.isNullOrEmpty() || url.isPlaceholder()) return false
        return posterImage.drawable == null && (imageDisposable == null || imageDisposable?.isDisposed == true)
    }

    override fun bind(
        item: LeanbackCardItem,
        cardWidthPx: Int,
        cardHeightPx: Int,
        cornerRadiusPx: Float,
        showLabels: Boolean
    ) {
        val posterItem = item as? LeanbackCardItem.Poster
        val isSeeAll = posterItem?.isSeeAll == true
        val style = adapter.themeStyle
        val rawImageUrl = if (isSeeAll) null else item.imageUrl
        val hasLoadableImage = !rawImageUrl.isNullOrBlank() && !rawImageUrl.trim().isPlaceholder()
        val imageUrl = if (hasLoadableImage) rawImageUrl else null
        val fallbackUrl = if (isSeeAll) null else posterItem?.fallbackImageUrl?.takeIf { !it.isBlank() && !it.trim().isPlaceholder() }
        val wPx = cardWidthPx.coerceAtLeast(1)
        val hPx = cardHeightPx.coerceAtLeast(1)
        val sameImageRequest = hasLoadableImage &&
            currentItem?.key == item.key &&
            boundImageUrl == imageUrl &&
            boundFallbackUrl == fallbackUrl &&
            boundWidthPx == wPx &&
            boundHeightPx == hPx &&
            (posterImage.drawable != null || imageDisposable?.isDisposed == false)

        val previousKey = currentItem?.key
        if (previousKey != item.key && (isBackdropExpanded || widthAnimator != null || activeTrailerOwnerKey != null)) {
            resetExpandedStateImmediate()
        }
        lastBoundCardWidthPx = cardWidthPx
        lastBoundCardHeightPx = cardHeightPx
        lastBoundShowLabels = showLabels

        currentItem = item
        updateCornerRadius(cornerRadiusPx)

        // Apply theme colors & typography
        titleText?.setTextColor(style.titleColor)
        subtitleText?.setTextColor(style.subtitleColor)
        seeAllArrowText?.setTextColor(style.titleColor)
        seeAllLabelText?.setTextColor(style.titleColor)
        fallbackTitleText?.setTextColor(style.titleColor)
        style.typeface?.let { tf ->
            if (titleText?.typeface !== tf) titleText?.typeface = tf
            if (subtitleText?.typeface !== tf) subtitleText?.typeface = tf
            if (badgeView?.typeface !== tf) badgeView?.typeface = tf
            if (seeAllLabelText?.typeface !== tf) seeAllLabelText?.typeface = tf
            if (fallbackTitleText?.typeface !== tf) fallbackTitleText?.typeface = tf
        }
        if (progressBar != null && lastProgressAccentColor != style.accentColor) {
            progressBar.progressDrawable = PillProgressBarDrawable.create(
                heightPx = progressBarHeightPx,
                progressColor = style.accentColor
            )
            lastProgressAccentColor = style.accentColor
        }

        if (!sameImageRequest) {
            lastLoadedColorKey = null
            posterImage.setImageDrawable(null)
            ensurePlaceholderBackground()
        }

        val colorCacheKey = imageUrl ?: item.key
        val cachedColor = LeanbackColorEngine.getCachedColor(colorCacheKey)
        currentDominantColor = cachedColor ?: LeanbackColorEngine.defaultFallbackColor

        // Update layout dimensions (preserve expanded width if this exact focused item is expanded)
        val shouldStayExpanded = isBackdropExpanded &&
            adapter.backdropExpandEnabled &&
            !isSeeAll &&
            item.key == adapter.expandedItemKey
        val desiredWidthPx = if (shouldStayExpanded && posterItem != null) {
            LeanbackCardExpandSpec.computeExpandedCardWidthPx(cardWidthPx, cardHeightPx, posterItem.isLandscape)
        } else {
            if (isBackdropExpanded) {
                resetExpandedStateImmediate()
            }
            cardWidthPx
        }
        applyWidthPx(desiredWidthPx, cardHeightPx)

        val subtitle = posterItem?.subtitle
        if (isSeeAll) {
            seeAllOverlay?.visibility = View.VISIBLE
            seeAllLabelText?.text = item.title
            fallbackTitleText?.visibility = View.GONE
            titleText?.visibility = View.GONE
            subtitleText?.visibility = View.GONE
            placeholderBackgroundDrawable.shape = GradientDrawable.RECTANGLE
            placeholderBackgroundDrawable.setColor(style.avatarPlaceholderColor)
            placeholderBackgroundDrawable.cornerRadius = cornerRadiusPx
            cardContainer.background = placeholderBackgroundDrawable
        } else {
            seeAllOverlay?.visibility = View.GONE
            // Title & Subtitle text
            if (showLabels && titleText != null && item.title.isNotBlank()) {
                titleText.visibility = View.VISIBLE
                titleText.text = item.title
            } else {
                titleText?.visibility = View.GONE
            }

            if (showLabels && subtitleText != null && !subtitle.isNullOrBlank()) {
                subtitleText.visibility = View.VISIBLE
                subtitleText.text = subtitle
            } else {
                subtitleText?.visibility = View.GONE
            }
        }

        // Progress bar
        val progress = posterItem?.progressPercent
        if (!isSeeAll && progress != null && progress > 0 && progressBar != null) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = progress.coerceIn(0, 100)
        } else {
            progressBar?.visibility = View.GONE
        }

        // Badge text (top-end)
        val badge = posterItem?.badgeText
        if (!isSeeAll && !badge.isNullOrBlank() && badgeView != null) {
            badgeView.visibility = View.VISIBLE
            badgeView.text = badge
        } else {
            badgeView?.visibility = View.GONE
        }

        // Watched indicator badge (top-start)
        if (!isSeeAll && posterItem?.isWatched == true && watchedBadgeView != null) {
            watchedBadgeView.visibility = View.VISIBLE
        } else {
            watchedBadgeView?.visibility = View.GONE
        }

        // Accessibility content description
        val descParts = ArrayList<String>(3)
        if (item.title.isNotBlank()) descParts.add(item.title)
        if (!subtitle.isNullOrBlank()) descParts.add(subtitle)
        if (posterItem?.isWatched == true) descParts.add("Watched")
        root.contentDescription = descParts.joinToString(", ").ifEmpty { null }

        val shouldShowFallbackTitle = !isSeeAll && !showLabels && item.title.isNotBlank() &&
            !item.key.startsWith("__placeholder_")

        // Coil image loading with zero GPU overdraw & MemoryCache fast-path
        if (imageUrl != null) {
            if (!sameImageRequest) {
                fallbackTitleText?.visibility = View.GONE
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
                        fallbackTitleText?.visibility = View.GONE
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
                        fallbackTitleText?.visibility = View.GONE
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
                        if (shouldShowFallbackTitle && fallbackTitleText != null) {
                            fallbackTitleText.text = item.title
                            fallbackTitleText.visibility = View.VISIBLE
                        }
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
            if (!isSeeAll) {
                ensurePlaceholderBackground()
                if (shouldShowFallbackTitle && fallbackTitleText != null) {
                    fallbackTitleText.text = item.title
                    fallbackTitleText.visibility = View.VISIBLE
                } else {
                    fallbackTitleText?.visibility = View.GONE
                }
            }
        }

        updateFocusVisual(root.hasFocus())
        if (root.hasFocus() &&
            adapter.backdropExpandEnabled &&
            !isSeeAll &&
            item.key == adapter.expandedItemKey
        ) {
            applyExpandedState(
                expanded = true,
                animated = false,
                trailerUrl = adapter.expandedTrailerUrl,
                trailerAudioUrl = adapter.expandedTrailerAudioUrl
            )
        }
    }

    override fun unbind() {
        resetExpandedStateImmediate()
        super.unbind()
        imageDisposable?.dispose()
        imageDisposable = null
        boundImageUrl = null
        boundFallbackUrl = null
        boundWidthPx = 0
        boundHeightPx = 0
        lastLoadedColorKey = null
        posterImage.setImageDrawable(null)
        titleText?.isSelected = false
        badgeView?.visibility = View.GONE
        watchedBadgeView?.visibility = View.GONE
        progressBar?.visibility = View.GONE
        seeAllOverlay?.visibility = View.GONE
        fallbackTitleText?.visibility = View.GONE
        root.contentDescription = null
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

            val mediaClipContainer = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                clipChildren = true
                clipToOutline = true
            }

            val posterImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            mediaClipContainer.addView(posterImage)

            val backdropImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
            }
            mediaClipContainer.addView(backdropImage)

            val trailerContainer = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(Color.BLACK)
                visibility = View.GONE
            }
            mediaClipContainer.addView(trailerContainer)

            val trailerCoverImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
            }
            mediaClipContainer.addView(trailerCoverImage)

            val expandedGradientView = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                background = GradientDrawable(
                    GradientDrawable.Orientation.BOTTOM_TOP,
                    intArrayOf(Color.argb(194, 0, 0, 0), Color.argb(60, 0, 0, 0), Color.TRANSPARENT)
                )
                visibility = View.GONE
            }
            mediaClipContainer.addView(expandedGradientView)

            val overlayPadPx = (10f * dp).toInt()
            val expandedLogoImage = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START
                ).apply {
                    marginStart = overlayPadPx
                    marginEnd = overlayPadPx
                    bottomMargin = (8f * dp).toInt()
                }
                scaleType = ImageView.ScaleType.FIT_START
                visibility = View.GONE
            }
            mediaClipContainer.addView(expandedLogoImage)

            val expandedTitleFallback = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START
                ).apply {
                    marginStart = overlayPadPx
                    marginEnd = overlayPadPx
                    bottomMargin = overlayPadPx
                }
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setShadowLayer(4f, 0f, 1f, Color.argb(180, 0, 0, 0))
                visibility = View.GONE
            }
            mediaClipContainer.addView(expandedTitleFallback)

            val themeStyle = adapter.themeStyle
            val pbHeightPx = 4f * dp
            val horizMargin = (cornerRadiusPx * 0.75f).toInt().coerceAtLeast(6)
            val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    pbHeightPx.toInt().coerceAtLeast(2)
                ).apply {
                    gravity = Gravity.BOTTOM
                    marginStart = horizMargin
                    marginEnd = horizMargin
                    bottomMargin = (6f * dp).toInt()
                }
                progressDrawable = PillProgressBarDrawable.create(
                    heightPx = pbHeightPx,
                    progressColor = themeStyle.accentColor
                )
                visibility = View.GONE
            }
            mediaClipContainer.addView(progressBar)

            val badgeView = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    topMargin = (6f * dp).toInt()
                    marginEnd = (6f * dp).toInt()
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
            mediaClipContainer.addView(badgeView)

            val watchedSizePx = (20f * dp).toInt()
            val watchedBadgeView = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    watchedSizePx,
                    watchedSizePx
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    topMargin = (6f * dp).toInt()
                    marginStart = (6f * dp).toInt()
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
            mediaClipContainer.addView(watchedBadgeView)

            val seeAllOverlay = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val pad = (12f * dp).toInt()
                setPadding(pad, pad, pad, pad)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                visibility = View.GONE
            }
            val seeAllArrowText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "→"
                setTextColor(themeStyle.titleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            val seeAllLabelText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (6f * dp).toInt()
                }
                setTextColor(themeStyle.titleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
            }
            seeAllOverlay.addView(seeAllArrowText)
            seeAllOverlay.addView(seeAllLabelText)
            mediaClipContainer.addView(seeAllOverlay)

            val fallbackTitleText = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                val pad = (10f * dp).toInt()
                setPadding(pad, pad, pad, pad)
                gravity = Gravity.CENTER
                setTextColor(themeStyle.titleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                visibility = View.GONE
            }
            mediaClipContainer.addView(fallbackTitleText)

            cardContainer.addView(mediaClipContainer)
            root.addView(cardContainer)

            val titleText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (8f * dp).toInt()
                }
                setTextColor(themeStyle.titleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = 1
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.MARQUEE
                marqueeRepeatLimit = -1
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
                setTextColor(themeStyle.subtitleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_HORIZONTAL
                visibility = View.GONE
            }.also { root.addView(it) }

            val expandedLabelContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (8f * dp).toInt()
                }
                visibility = View.GONE
            }
            val expandedMetaRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val expandedMetaText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setTextColor(themeStyle.subtitleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                visibility = View.GONE
            }
            val expandedAgeRatingBadge = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (6f * dp).toInt()
                }
                setPadding((6f * dp).toInt(), (1f * dp).toInt(), (6f * dp).toInt(), (1f * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke((1f * dp).toInt().coerceAtLeast(1), Color.argb(140, 200, 200, 210))
                    cornerRadius = 4f * dp
                }
                setTextColor(themeStyle.subtitleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                visibility = View.GONE
            }
            expandedMetaRow.addView(expandedMetaText)
            expandedMetaRow.addView(expandedAgeRatingBadge)
            expandedLabelContainer.addView(expandedMetaRow)

            val expandedDescriptionText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (3f * dp).toInt()
                }
                setTextColor(themeStyle.titleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                visibility = View.GONE
            }
            expandedLabelContainer.addView(expandedDescriptionText)
            root.addView(expandedLabelContainer)

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
                seeAllOverlay = seeAllOverlay,
                seeAllArrowText = seeAllArrowText,
                seeAllLabelText = seeAllLabelText,
                fallbackTitleText = fallbackTitleText,
                progressBarHeightPx = pbHeightPx,
                cornerRadiusPx = cornerRadiusPx,
                borderWidthPx = chrome.borderWidthPx,
                glowMarginPx = chrome.glowMarginPx,
                adapter = adapter,
                mediaClipContainer = mediaClipContainer,
                backdropImage = backdropImage,
                trailerContainer = trailerContainer,
                trailerCoverImage = trailerCoverImage,
                expandedGradientView = expandedGradientView,
                expandedLogoImage = expandedLogoImage,
                expandedTitleFallback = expandedTitleFallback,
                expandedLabelContainer = expandedLabelContainer,
                expandedMetaText = expandedMetaText,
                expandedAgeRatingBadge = expandedAgeRatingBadge,
                expandedDescriptionText = expandedDescriptionText
            ).also {
                it.lastProgressAccentColor = themeStyle.accentColor
                it.ensurePlaceholderBackground()
            }
        }
    }
}
