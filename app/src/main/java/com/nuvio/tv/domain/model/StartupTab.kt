package com.nuvio.tv.domain.model

enum class StartupTab {
    HOME,
    LIVE_TV,
    DISCOVER,
    SEARCH,
    LIBRARY;

    fun toRoute(): String = when (this) {
        HOME -> "home"
        LIVE_TV -> "livetv"
        DISCOVER -> "discover"
        SEARCH -> "search"
        LIBRARY -> "library"
    }

    companion object {
        fun fromRoute(route: String?): StartupTab = when (route) {
            "livetv" -> LIVE_TV
            "discover" -> DISCOVER
            "search" -> SEARCH
            "library" -> LIBRARY
            else -> HOME
        }
    }
}

