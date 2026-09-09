package com.nuvio.tv.core.torrent

import androidx.compose.runtime.Immutable

@Immutable
sealed class TorrentState {
    data object Idle : TorrentState()
    data object Connecting : TorrentState()

    data class Streaming(
        val localUrl: String,
        val downloadSpeed: Long,
        val uploadSpeed: Long,
        val peers: Int,
        val seeds: Int,
        val bufferProgress: Float,
        val totalProgress: Float,
        val preloadedBytes: Long = 0L,
        val preloadSize: Long = 0L,
        val stat: Int = 0,
        val statString: String? = null
    ) : TorrentState() {
        val isPreloadReady: Boolean
            get() = stat == 3 || (preloadSize > 0 && preloadedBytes >= preloadSize)

        val preloadProgress: Float
            get() {
                if (stat == 3) return 1f
                val target = if (preloadSize > 0) preloadSize else if (stat == 2 && preloadedBytes > 0) 33_554_432L else 0L
                return if (target > 0) (preloadedBytes.toFloat() / target).coerceIn(0f, 1f) else 0f
            }
    }

    data class Error(val message: String) : TorrentState()
}
