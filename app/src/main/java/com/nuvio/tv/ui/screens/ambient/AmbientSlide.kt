package com.nuvio.tv.ui.screens.ambient

data class AmbientSlide(
    val id: String,
    val title: String,
    val backdropUrl: String,
    val logoUrl: String?,
    val genres: List<String>,
    val itemId: String = id.substringAfter(':', missingDelimiterValue = id),
    val imdbId: String? = null,
    val apiType: String = id.substringBefore(':', missingDelimiterValue = "movie"),
    val year: String? = null,
    val trailerYtIds: List<String> = emptyList()
)
