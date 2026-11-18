package com.nuvio.tv.ui.leanback.pool

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.recyclerview.widget.RecyclerView

/**
 * RecycledViewPool Manager for Android Leanback Native Lists and Grids.
 * Right-sizes view pools per ViewType so heavy Episode/Avatar holders never over-allocate
 * while Poster rows and 2D grids eliminate inflation stutter during rapid navigation.
 */
object LeanbackPoolManager {
    const val VIEW_TYPE_POSTER = 0
    const val VIEW_TYPE_EPISODE = 1
    const val VIEW_TYPE_AVATAR = 2
    const val VIEW_TYPE_POSTER_LANDSCAPE = 3
    // Dedicated view type for Modern Home cards so they never collide with LeanbackUniversalAdapter view types.
    const val VIEW_TYPE_MODERN_CARD = 100

    fun createSharedPool(
        maxPosters: Int = 40,
        maxLandscapePosters: Int = 32,
        maxAvatars: Int = 16,
        maxEpisodes: Int = 12
    ): RecyclerView.RecycledViewPool {
        return RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(VIEW_TYPE_POSTER, maxPosters)
            setMaxRecycledViews(VIEW_TYPE_POSTER_LANDSCAPE, maxLandscapePosters)
            setMaxRecycledViews(VIEW_TYPE_AVATAR, maxAvatars)
            setMaxRecycledViews(VIEW_TYPE_EPISODE, maxEpisodes)
        }
    }

    fun createGridPool(
        maxPosters: Int = 30,
        maxLandscapePosters: Int = 24
    ): RecyclerView.RecycledViewPool {
        return RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(VIEW_TYPE_POSTER, maxPosters)
            setMaxRecycledViews(VIEW_TYPE_POSTER_LANDSCAPE, maxLandscapePosters)
        }
    }

    fun createModernHomePool(maxPerType: Int = 48): RecyclerView.RecycledViewPool {
        return RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(VIEW_TYPE_MODERN_CARD, maxPerType)
        }
    }
}

val LocalLeanbackRecycledViewPool = staticCompositionLocalOf<RecyclerView.RecycledViewPool?> { null }

@Composable
fun rememberSharedLeanbackPool(
    maxPosters: Int = 40,
    maxLandscapePosters: Int = 32,
    maxAvatars: Int = 16,
    maxEpisodes: Int = 12
): RecyclerView.RecycledViewPool {
    return remember(maxPosters, maxLandscapePosters, maxAvatars, maxEpisodes) {
        LeanbackPoolManager.createSharedPool(
            maxPosters = maxPosters,
            maxLandscapePosters = maxLandscapePosters,
            maxAvatars = maxAvatars,
            maxEpisodes = maxEpisodes
        )
    }
}
