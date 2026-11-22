package com.nuvio.tv.features.livetv

import androidx.annotation.Keep

@Keep
data class CatchupInfo(
    val type: String = "default",
    val source: String? = null,
    val days: Int = 0,
    val correctionHours: Double = 0.0
)

@Keep
data class DrmInfo(
    val scheme: String,
    val license: String,
    val licenseHeaders: Map<String, String> = emptyMap(),
    val manifestType: String? = null
)

data class EpgProgram(
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val episode: String? = null,
    val icon: String? = null,
    val year: Int? = null,
    val people: List<String> = emptyList(),
    val providerProgramId: String? = null,
    val providerSourceIdentity: String? = null
) {
    fun isLive(nowMs: Long): Boolean = nowMs in startMs until stopMs
    fun progress(nowMs: Long): Float {
        val span = (stopMs - startMs).coerceAtLeast(1L)
        return ((nowMs - startMs).toFloat() / span).coerceIn(0f, 1f)
    }
}

data class ChannelGroup(
    val id: String,
    val name: String,
    val playlistId: String? = null,
    val channelCount: Int = 0
) {
    companion object {
        const val ALL = "group:all"
        const val FAVORITES = "group:favorites"
        const val RECENT = "group:recent"
        const val SEARCH = "group:search"
    }
}

@Keep
data class EpgAssignment(
    val sourceId: String,
    val xmltvId: String,
    val shiftMinutes: Int = 0
)

@Keep
data class LiveTvChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val playlistId: String? = null,
    val playlistName: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val streamType: String? = null,
    val stalkerCommand: String? = null,
    val drmType: String? = null,
    val drmKey: String? = null,
    // EPG and channel management fields
    val tvgId: String? = null,
    val tvgName: String? = null,
    val number: Int = 0,
    val catchup: CatchupInfo? = null,
    val drm: DrmInfo? = null,
    val tvgShiftMinutes: Int = 0,
    val autoNumber: Boolean = false,
) {
    val key: String get() = id
    val logo: String? get() = logoUrl
    val url: String get() = streamUrl
}

@Keep
data class LiveTvStalkerSettings(
    val portalUrl: String = "",
    val macAddress: String = "",
    val username: String = "",
    val password: String = "",
    val isEnabled: Boolean = true,
) {
    val isConfigured: Boolean get() = portalUrl.isNotBlank() && macAddress.isNotBlank()
}

@Keep
data class LiveTvXtreamSettings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isEnabled: Boolean = true,
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

@Keep
enum class LiveTvPlaylistType {
    Url,
    LocalFile,
}

@Keep
data class LiveTvPlaylist(
    val id: String,
    val name: String,
    val type: LiveTvPlaylistType,
    val source: String,
    val isEnabled: Boolean = true,
    var epgUrl: String? = null,
)

@Keep
data class LiveTvSettings(
    val showInSidebar: Boolean = true,
    val showChannelNames: Boolean = true,
    val twoLineChannelNames: Boolean = false,
    val showChannelNumbers: Boolean = true,
    val markPlayingChannel: Boolean = true,
    val showChannelLogos: Boolean = true,
    val showPreview: Boolean = true, // Default true per user instruction
    val showProgramDetails: Boolean = true,
    val showFavoritesGroup: Boolean = true,
    val showRecentGroup: Boolean = true,
    val compactRows: Boolean = false,
    val showProgramTimes: Boolean = false,
    val showEpisodeNumbers: Boolean = true,
    val guideSize: String = "STANDARD", // Default STANDARD per user instruction
    val epgOffsetMinutes: Int = 0,
    val autoPlayLastChannel: Boolean = true,
    val rememberLastGroup: Boolean = true,
    val openFullscreenOnSelect: Boolean = false,
    val autoReconnect: Boolean = true,
    val autoBackupChannel: Boolean = true,
    val catchupCache: Boolean = true,
    val showAllChannelsGroup: Boolean = true,
    val resumeLastInPreview: Boolean = true,
    val showGroupCounts: Boolean = true,
    val smallHeader: Boolean = false,
    val aspectMode: String = "ORIGINAL",
    val sequentialNumbers: Boolean = false,
    val overlayMode: Boolean = true,
    val overlayKeepOpen: Boolean = false,
    val overlayShowDetails: Boolean = true,
    val epgRefreshHours: Int = 12,
    val epgPastHours: Int = 24,
    val epgFutureDays: Int = 3,
    val customEpgUrl: String = "",
)

data class CustomGroup(val id: String, val name: String, val icon: String? = null)
data class Reminder(val channelKey: String, val startMs: Long, val title: String = "")

data class LiveUserState(
    val hiddenChannels: Set<String> = emptySet(),
    val hiddenGroups: Set<String> = emptySet(),
    val favorites: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    val recentGroups: Map<String, String> = emptyMap(),
    val lastChannelKey: String? = null,
    val previousChannelKey: String? = null,
    val lastGroupId: String? = null,
    val channelNames: Map<String, String> = emptyMap(),
    val channelLogos: Map<String, String> = emptyMap(),
    val channelNumbers: Map<String, Int> = emptyMap(),
    val groupNames: Map<String, String> = emptyMap(),
    val groupOrder: List<String> = emptyList(),
    val customGroups: List<CustomGroup> = emptyList(),
    val epgOverrides: Map<String, EpgAssignment> = emptyMap(),
    val channelOrder: Map<String, List<String>> = emptyMap(),
    val channelCopies: Map<String, List<String>> = emptyMap(),
    val lockedGroups: Set<String> = emptySet(),
    val reminders: List<Reminder> = emptyList(),
    val channelQuality: Map<String, String> = emptyMap(),
    val channelAudio: Map<String, String> = emptyMap(),
    val channelCaptions: Map<String, String> = emptyMap()
)

val LiveTvSettings.effectiveGuideSize: String
    get() = guideSize.ifBlank { "STANDARD" }

data class LivePlaybackState(
    val channelKey: String? = null,
    val isBuffering: Boolean = false,
    val isPlaying: Boolean = false,
    val error: String? = null,
    val reconnectAttempt: Int = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val pixelRatio: Float = 1f,
    val catchupTitle: String? = null,
    val userPaused: Boolean = false
)

data class LiveTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
    val kind: String? = null,
    val language: String? = null,
    val channels: Int = 0
)

data class LiveTvUiState(
    val playlistUrl: String = "",
    val playlists: List<LiveTvPlaylist> = emptyList(),
    val stalkerSettings: LiveTvStalkerSettings = LiveTvStalkerSettings(),
    val xtreamSettings: LiveTvXtreamSettings = LiveTvXtreamSettings(),
    val channels: List<LiveTvChannel> = emptyList(),
    val favoriteChannelIds: Set<String> = emptySet(),
    val lastWatchedChannelId: String? = null,
    val lastWatchedPlaylistId: String? = null,
    val lastWatchedGroup: String? = null,
    val lastWatchedFilterType: String? = null,
    val recentChannelIds: List<String> = emptyList(),
    val isNavigationEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isQrModeActive: Boolean = false,
    val qrCodeBitmap: android.graphics.Bitmap? = null,
    val serverUrl: String? = null,
    val settings: LiveTvSettings = LiveTvSettings(),
) {
    val hasPlaylist: Boolean
        get() = playlists.isNotEmpty() || playlistUrl.isNotBlank() ||
            stalkerSettings.isConfigured || xtreamSettings.isConfigured

    val showInNavigation: Boolean
        get() = hasPlaylist && isNavigationEnabled
}
