package com.nuvio.tv.ui.screens.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.DoubleArrow
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.HdrAuto
import androidx.compose.material.icons.filled.HdrOff
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.ShutterSpeed
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Start
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.ui.graphics.vector.ImageVector

internal object PlaybackIcons {
    object Player {
        val defaultPlayer: ImageVector get() = Icons.Default.PlayCircle
        val forwardSubtitles: ImageVector get() = Icons.Default.ClosedCaption
        val sendSkipSegments: ImageVector get() = Icons.Default.FastForward
        val internalEngine: ImageVector get() = Icons.Default.Memory
        val autoSwitchEngine: ImageVector get() = Icons.Default.SwapHoriz
    }

    object StreamSelection {
        val mode: ImageVector get() = Icons.Default.TouchApp
        val regex: ImageVector get() = Icons.Default.Pattern
        val sourceScope: ImageVector get() = Icons.Default.FilterList
        val allowedAddons: ImageVector get() = Icons.Default.Extension
        val allowedPlugins: ImageVector get() = Icons.Default.Power
        val timeout: ImageVector get() = Icons.Default.Timer
        val reuseLastLink: ImageVector get() = Icons.Default.History
        val lastLinkCacheDuration: ImageVector get() = Icons.Default.HourglassBottom
    }

    object UpNext {
        val autoplayNextEpisode: ImageVector get() = Icons.Default.SkipNext
        val manualFallback: ImageVector get() = Icons.AutoMirrored.Filled.ViewList
        val thresholdMode: ImageVector get() = Icons.Default.Straighten
        val thresholdPercent: ImageVector get() = Icons.Default.Percent
        val thresholdMinutes: ImageVector get() = Icons.Default.Timelapse
        val stillWatching: ImageVector get() = Icons.Default.Visibility
        val stillWatchingThreshold: ImageVector get() = Icons.Default.Repeat
        val preferBingeGroup: ImageVector get() = Icons.Default.Workspaces
        val reuseBingeGroup: ImageVector get() = Icons.Default.Replay
        val postPlayRecommendations: ImageVector get() = Icons.Default.Recommend
        val postPlayMovieThreshold: ImageVector get() = Icons.Default.Movie
    }

    object SkipSegments {
        val skipIntro: ImageVector get() = Icons.Default.DoubleArrow
        val autoSkipIntro: ImageVector get() = Icons.Default.Start
        val autoSkipRecap: ImageVector get() = Icons.Default.History
        val autoSkipOutro: ImageVector get() = Icons.AutoMirrored.Filled.LastPage
        val autoSkipMovieCredits: ImageVector get() = Icons.Default.Theaters
    }

    object PlayerInterface {
        val loadingOverlay: ImageVector get() = Icons.Default.Image
        val pauseOverlay: ImageVector get() = Icons.Default.PauseCircle
        val clock: ImageVector get() = Icons.Default.Schedule
        val parentalGuide: ImageVector get() = Icons.Default.GppMaybe
        val loadingStatus: ImageVector get() = Icons.Default.Checklist
    }

    object Audio {
        val preferredLanguage: ImageVector get() = Icons.Default.RecordVoiceOver
        val secondaryLanguage: ImageVector get() = Icons.Default.Translate
        val skipSilence: ImageVector get() = Icons.Default.Speed
        val rememberDelay: ImageVector get() = Icons.Default.AvTimer
        val decoderPriority: ImageVector get() = Icons.Default.DeveloperBoard
        val downmix: ImageVector get() = Icons.AutoMirrored.Filled.CallMerge
        val outputChannels: ImageVector get() = Icons.Default.SurroundSound
        val keepOriginalLoudness: ImageVector get() = Icons.Default.GraphicEq
        val tunneling: ImageVector get() = Icons.Default.Route
        val opticalPassthrough: ImageVector get() = Icons.Default.Speaker
    }

    object Subtitles {
        val preferredLanguage: ImageVector get() = Icons.Default.Language
        val secondaryLanguage: ImageVector get() = Icons.Default.Translate
        val forced: ImageVector get() = Icons.Default.ClosedCaption
        val onlyPreferredLanguages: ImageVector get() = Icons.Default.FilterList
        val stripSdh: ImageVector get() = Icons.Default.HearingDisabled
        val size: ImageVector get() = Icons.Default.FormatSize
        val verticalOffset: ImageVector get() = Icons.Default.VerticalAlignBottom
        val bold: ImageVector get() = Icons.Default.FormatBold
        val textColor: ImageVector get() = Icons.Default.FormatColorText
        val backgroundColor: ImageVector get() = Icons.Default.FormatColorFill
        val outline: ImageVector get() = Icons.Default.BorderOuter
        val outlineColor: ImageVector get() = Icons.Default.BorderColor
        val libass: ImageVector get() = Icons.Default.AutoFixHigh
        val libassRenderMode: ImageVector get() = Icons.Default.Brush
    }

    object Video {
        val frameRateMatching: ImageVector get() = Icons.Default.ShutterSpeed
        val resolutionMatching: ImageVector get() = Icons.Default.Hd
        val dv7Handling: ImageVector get() = Icons.Default.HdrAuto
        val dv7PreserveMapping: ImageVector get() = Icons.Default.ColorLens
        val dv5ToDv81: ImageVector get() = Icons.Default.Transform
        val stripHdr10Plus: ImageVector get() = Icons.Default.HdrOff
        val mpvHardwareDecode: ImageVector get() = Icons.Default.Speed
        val mpvHi10pFallback: ImageVector get() = Icons.Default.SettingsBackupRestore
    }

    object BufferNetwork {
        val performanceMode: ImageVector get() = Icons.Default.Bolt
        val customBuffers: ImageVector get() = Icons.Default.DataUsage
        val minBuffer: ImageVector get() = Icons.Default.HourglassEmpty
        val maxBuffer: ImageVector get() = Icons.Default.HourglassFull
        val initialBuffer: ImageVector get() = Icons.Default.PlayArrow
        val rebuffer: ImageVector get() = Icons.Default.Refresh
        val backBuffer: ImageVector get() = Icons.Default.History
        val managedBudget: ImageVector get() = Icons.Default.Balance
        val targetSize: ImageVector get() = Icons.Default.Inventory2
        val allowLargeTarget: ImageVector get() = Icons.Default.OpenInFull
        val diskCache: ImageVector get() = Icons.Default.Storage
        val autoCacheSize: ImageVector get() = Icons.Default.AutoMode
        val manualCacheSize: ImageVector get() = Icons.Default.SdCard
        val parallelNetwork: ImageVector get() = Icons.Default.SettingsEthernet
        val http2: ImageVector get() = Icons.Default.Http
        val parallelConnections: ImageVector get() = Icons.AutoMirrored.Filled.CallSplit
        val connectionCount: ImageVector get() = Icons.Default.Tag
        val chunkSize: ImageVector get() = Icons.AutoMirrored.Filled.Segment
    }

    object P2p {
        val enabled: ImageVector get() = Icons.Default.SwapVert
        val hideStats: ImageVector get() = Icons.Default.VisibilityOff
    }
}
