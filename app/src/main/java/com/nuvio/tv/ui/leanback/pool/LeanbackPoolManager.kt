package com.nuvio.tv.ui.leanback.pool

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.recyclerview.widget.RecyclerView

/**
 * RecycledViewPool Manager for Android Leanback Native Lists and Grids.
 * Sharing pools across rows and screens completely eliminates ViewHolder inflation stutter during fast navigation.
 */
object LeanbackPoolManager {
    const val VIEW_TYPE_POSTER = 0
    const val VIEW_TYPE_EPISODE = 1
    const val VIEW_TYPE_AVATAR = 2

    fun createSharedPool(maxPerType: Int = 30): RecyclerView.RecycledViewPool {
        return RecyclerView.RecycledViewPool().apply {
            setMaxRecycledViews(VIEW_TYPE_POSTER, maxPerType)
            setMaxRecycledViews(VIEW_TYPE_EPISODE, maxPerType)
            setMaxRecycledViews(VIEW_TYPE_AVATAR, maxPerType)
        }
    }
}

val LocalLeanbackRecycledViewPool = staticCompositionLocalOf<RecyclerView.RecycledViewPool?> { null }

@Composable
fun rememberSharedLeanbackPool(maxPerType: Int = 30): RecyclerView.RecycledViewPool {
    return remember { LeanbackPoolManager.createSharedPool(maxPerType) }
}

