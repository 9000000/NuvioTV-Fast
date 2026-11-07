package com.nuvio.tv.ui.leanback.model

/**
 * Universal polymorphic item model for Android Leanback Native Cards.
 * Decouples screens (Home, Details, Catalog, Search, Library) from specific ViewModel models.
 */
sealed interface LeanbackCardItem {
    val key: String
    val title: String
    val imageUrl: String?
    val payload: Any?

    /**
     * Standard movie/show/collection poster card.
     * Supports both Portrait (2:3) and Landscape (16:9).
     */
    data class Poster(
        override val key: String,
        override val title: String,
        override val imageUrl: String?,
        val subtitle: String? = null,
        val isLandscape: Boolean = false,
        val progressPercent: Int? = null,
        val badgeText: String? = null,
        val isWatched: Boolean = false,
        override val payload: Any? = null
    ) : LeanbackCardItem

    /**
     * TV Show Episode card with 16:9 landscape still thumbnail,
     * episode badge (e.g. "S1 E5" or "EP 5"), runtime, and watched status.
     */
    data class Episode(
        override val key: String,
        override val title: String,
        override val imageUrl: String?,
        val episodeNumber: Int? = null,
        val seasonNumber: Int? = null,
        val episodeLabel: String? = null,
        val runtimeMinutes: Int? = null,
        val progressPercent: Int? = null,
        val isWatched: Boolean = false,
        val releaseDate: String? = null,
        val imdbRating: String? = null,
        override val payload: Any? = null
    ) : LeanbackCardItem

    /**
     * Actor / Director / Creator circular avatar card with name and role.
     */
    data class Avatar(
        override val key: String,
        override val title: String,
        override val imageUrl: String?,
        val subtitle: String? = null,
        override val payload: Any? = null
    ) : LeanbackCardItem
}

