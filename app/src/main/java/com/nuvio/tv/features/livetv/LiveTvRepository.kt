package com.nuvio.tv.features.livetv

import android.content.Context
import android.util.Log
import com.nuvio.tv.R
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.core.server.DeviceIpAddress
import com.nuvio.tv.core.server.LiveTvConfigServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.nuvio.tv.core.network.DynamicHostFallback
import com.nuvio.tv.core.network.IPv4FirstDns
import com.nuvio.tv.ui.screens.player.ClearKeyUtil
import com.nuvio.tv.ui.screens.player.IptvHeaderProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Lightweight OkHttpClient for Live TV playlist fetching.
 * Uses permissive SSL to support self-signed IPTV servers.
 * Configured with IPv4FirstDns and reasonable timeouts for large M3U files.
 */
internal val liveTvHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .dns(IPv4FirstDns())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

/**
 * Backward-compatible text fetcher for portal providers (Xtream/Stalker).
 * Uses OkHttp instead of raw HttpURLConnection.
 */
suspend fun httpGetText(url: String): String = httpGetTextWithHeaders(url, emptyMap())

suspend fun httpGetTextWithHeaders(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
    val requestBuilder = Request.Builder().url(url)
    headers.forEach { (k, v) -> requestBuilder.header(k, v) }
    val response = liveTvHttpClient.newCall(requestBuilder.build()).execute()
    if (!response.isSuccessful) {
        response.close()
        error("HTTP ${response.code} ${response.message} for $url")
    }
    response.body.string()
}

object LiveTvRepository {
    private const val TAG = "LiveTvRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private val _navigationResetEvent = MutableStateFlow(0L)
    val navigationResetEvent: StateFlow<Long> = _navigationResetEvent.asStateFlow()

    fun requestResetToNavigationDefault() {
        _navigationResetEvent.value = System.currentTimeMillis()
    }

    private var hasLoaded = false
    private var liveTvConfigServer: LiveTvConfigServer? = null

    fun startQrMode(context: Context) {
        ensureLoaded()
        val ip = DeviceIpAddress.get(context)
        if (ip == null) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.error_network_required)) }
            return
        }

        stopQrMode()

        liveTvConfigServer = LiveTvConfigServer.startOnAvailablePort(context)
        val activeServer = liveTvConfigServer
        if (activeServer == null) {
            _uiState.update { it.copy(errorMessage = context.getString(R.string.error_server_ports_unavailable)) }
            return
        }

        val url = "http://$ip:${activeServer.listeningPort}"
        val qrBitmap = QrCodeGenerator.generate(url, 512)

        _uiState.update {
            it.copy(
                isQrModeActive = true,
                qrCodeBitmap = qrBitmap,
                serverUrl = url,
                errorMessage = null
            )
        }
    }

    fun stopQrMode() {
        liveTvConfigServer?.stop()
        liveTvConfigServer = null
        _uiState.update {
            it.copy(
                isQrModeActive = false,
                qrCodeBitmap = null,
                serverUrl = null
            )
        }
    }

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        val playlists = loadSavedPlaylists()
        val recentIds = loadRecentChannelIds()
        val lastWatched = recentIds.firstOrNull() ?: LiveTvStorage.loadLastWatchedChannelId()
        val stalker = LiveTvStorage.loadStalkerSettings()
        val xtream = LiveTvStorage.loadXtreamSettings()
        val cachedChannels = LiveTvStorage.loadChannelsCache()

        _uiState.value = LiveTvUiState(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            stalkerSettings = stalker,
            xtreamSettings = xtream,
            channels = cachedChannels,
            favoriteChannelIds = loadFavoriteChannelIds(),
            lastWatchedChannelId = lastWatched,
            recentChannelIds = recentIds,
            isNavigationEnabled = LiveTvStorage.loadNavigationEnabled() ?: true,
            isLoading = false,
        )
        publishNavigationVisibility()

        if (_uiState.value.hasPlaylist) {
            val signature = computeConfigSignature(playlists, stalker, xtream)
            val savedSignature = LiveTvStorage.loadCacheConfigSignature()
            val isExpired = LiveTvStorage.isCacheExpired()
            val shouldRefresh = cachedChannels.isEmpty() || signature != savedSignature || isExpired
            if (shouldRefresh) {
                refresh(force = true, showLoadingIfHasChannels = cachedChannels.isEmpty())
            }
        }
    }

    fun onScreenEntered() {
        ensureLoaded()
        if (_uiState.value.hasPlaylist) {
            val signature = computeConfigSignature()
            val savedSignature = LiveTvStorage.loadCacheConfigSignature()
            val isExpired = LiveTvStorage.isCacheExpired()
            val shouldRefresh = _uiState.value.channels.isEmpty() || signature != savedSignature || isExpired
            if (shouldRefresh) {
                refresh(force = true, showLoadingIfHasChannels = _uiState.value.channels.isEmpty())
            }
        }
    }

    fun computeConfigSignature(
        playlists: List<LiveTvPlaylist> = _uiState.value.playlists,
        stalker: LiveTvStalkerSettings = _uiState.value.stalkerSettings,
        xtream: LiveTvXtreamSettings = _uiState.value.xtreamSettings,
    ): String {
        val enabledPlaylists = playlists
            .filter { it.isEnabled }
            .map { "${it.id}:${it.type.name}:${it.source}" }
            .sorted()
            .joinToString(";")
        val stalkerPart = if (stalker.isConfigured && stalker.isEnabled) {
            "${stalker.portalUrl}|${stalker.macAddress}"
        } else ""
        val xtreamPart = if (xtream.isConfigured && xtream.isEnabled) {
            "${xtream.serverUrl}|${xtream.username}"
        } else ""
        return "$enabledPlaylists##$stalkerPart##$xtreamPart"
    }

    fun savePlaylistUrl(url: String) {
        ensureLoaded()
        val normalized = url.trim()
        val playlists = if (normalized.isBlank()) {
            emptyList()
        } else {
            listOf(createUrlPlaylist(normalized))
        }
        persistPlaylists(playlists)
        _uiState.value = _uiState.value.copy(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            channels = emptyList(),
            isLoading = false,
            errorMessage = null,
        )
        publishNavigationVisibility()
        if (playlists.isNotEmpty()) {
            refresh()
        }
    }

    fun addPlaylistUrl(url: String) {
        addPlaylistUrl(name = null, url = url)
    }

    fun addPlaylistUrl(name: String?, url: String) {
        ensureLoaded()
        val normalized = url.trim()
        if (normalized.isBlank()) return

        val current = _uiState.value.playlists
        if (current.any { it.type == LiveTvPlaylistType.Url && it.source.equals(normalized, ignoreCase = true) }) {
            return
        }

        val playlists = current + createUrlPlaylist(normalized, name)
        persistPlaylists(playlists)
        _uiState.value = _uiState.value.copy(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            errorMessage = null,
        )
        publishNavigationVisibility()
        refresh()
    }

    fun addLocalPlaylist(fileName: String?, content: String) {
        addLocalPlaylist(name = null, fileName = fileName, content = content)
    }

    fun addLocalPlaylist(name: String?, fileName: String?, content: String) {
        ensureLoaded()
        val normalizedContent = content.trim()
        if (normalizedContent.isBlank()) return

        val fallbackName = name?.trim()?.takeIf(String::isNotBlank)
            ?: fileName
                ?.let { file -> file.substringBeforeLast('.', missingDelimiterValue = file) }
                ?.trim()
                ?.takeIf(String::isNotBlank)
            ?: "Local playlist"
        val playlist = LiveTvPlaylist(
            id = stablePlaylistId("local:${fallbackName}:${normalizedContent.hashCode()}:${Random.nextInt()}", _uiState.value.playlists.size),
            name = fallbackName,
            type = LiveTvPlaylistType.LocalFile,
            source = normalizedContent,
            isEnabled = true,
        )
        val playlists = _uiState.value.playlists + playlist
        persistPlaylists(playlists)
        _uiState.value = _uiState.value.copy(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            errorMessage = null,
        )
        publishNavigationVisibility()
        refresh()
    }

    fun updatePlaylist(playlistId: String, name: String, source: String) {
        ensureLoaded()
        val current = _uiState.value.playlists
        val existing = current.firstOrNull { it.id == playlistId } ?: return
        val normalizedName = name.trim().ifBlank { existing.name }
        val normalizedSource = source.trim()
        if (normalizedSource.isBlank()) return
        if (existing.type == LiveTvPlaylistType.Url && current.any {
                it.id != playlistId &&
                    it.type == LiveTvPlaylistType.Url &&
                    it.source.equals(normalizedSource, ignoreCase = true)
            }
        ) {
            return
        }

        val playlists = current.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(
                    name = normalizedName,
                    source = normalizedSource,
                )
            } else {
                playlist
            }
        }
        persistPlaylists(playlists)
        _uiState.value = _uiState.value.copy(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            errorMessage = null,
        )
        publishNavigationVisibility()
        refresh()
    }

    fun removePlaylist(playlistId: String) {
        ensureLoaded()
        val playlists = _uiState.value.playlists.filterNot { it.id == playlistId }
        persistPlaylists(playlists)
        _uiState.value = _uiState.value.copy(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            channels = emptyList(),
            isLoading = false,
            errorMessage = null,
        )
        publishNavigationVisibility()
        if (playlists.any { it.isEnabled }) {
            refresh(force = true)
        } else if (!_uiState.value.stalkerSettings.isConfigured && !_uiState.value.xtreamSettings.isConfigured) {
            LiveTvStorage.clearChannelsCache()
        }
    }

    fun setPlaylistEnabled(playlistId: String, isEnabled: Boolean) {
        ensureLoaded()
        val current = _uiState.value.playlists
        if (current.none { it.id == playlistId }) return

        val playlists = current.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(isEnabled = isEnabled)
            } else {
                playlist
            }
        }
        persistPlaylists(playlists)
        _uiState.value = _uiState.value.copy(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            channels = emptyList(),
            isLoading = false,
            errorMessage = null,
        )
        publishNavigationVisibility()
        if (playlists.any { it.isEnabled } || _uiState.value.stalkerSettings.isConfigured || _uiState.value.xtreamSettings.isConfigured) {
            refresh(force = true)
        } else {
            LiveTvStorage.clearChannelsCache()
        }
    }

    fun setNavigationEnabled(enabled: Boolean) {
        ensureLoaded()
        if (_uiState.value.isNavigationEnabled == enabled) return

        LiveTvStorage.saveNavigationEnabled(enabled)
        _uiState.value = _uiState.value.copy(isNavigationEnabled = enabled)
        publishNavigationVisibility()
    }

    fun saveStalkerSettings(settings: LiveTvStalkerSettings) {
        ensureLoaded()
        val normalized = settings.copy(
            portalUrl = settings.portalUrl.trim().trimEnd('/'),
            macAddress = settings.macAddress.trim().uppercase(),
            username = settings.username.trim(),
            password = settings.password.trim(),
        )
        LiveTvStorage.saveStalkerSettings(normalized)
        _uiState.value = _uiState.value.copy(stalkerSettings = normalized, errorMessage = null)
        publishNavigationVisibility()
        refresh()
    }

    fun saveXtreamSettings(settings: LiveTvXtreamSettings) {
        ensureLoaded()
        val normalized = settings.copy(
            serverUrl = settings.serverUrl.trim().trimEnd('/').substringBefore("/player_api.php").trimEnd('/'),
            username = settings.username.trim(),
            password = settings.password.trim(),
        )
        LiveTvStorage.saveXtreamSettings(normalized)
        _uiState.value = _uiState.value.copy(xtreamSettings = normalized, errorMessage = null)
        publishNavigationVisibility()
        refresh()
    }

    fun removeStalker() = saveStalkerSettings(LiveTvStalkerSettings())
    fun removeXtream() = saveXtreamSettings(LiveTvXtreamSettings())

    suspend fun testAndSaveXtreamSettings(settings: LiveTvXtreamSettings): Result<Int> = withContext(Dispatchers.IO) {
        val normalized = settings.copy(
            serverUrl = settings.serverUrl.trim().trimEnd('/').substringBefore("/player_api.php").trimEnd('/'),
            username = settings.username.trim(),
            password = settings.password.trim(),
        )
        runCatching {
            val channels = fetchXtreamChannels(normalized)
            if (channels.isEmpty()) {
                throw IllegalStateException("Đăng nhập thành công nhưng không tìm thấy kênh nào (0 kênh).")
            }
            withContext(Dispatchers.Main) {
                saveXtreamSettings(normalized)
            }
            channels.size
        }
    }

    suspend fun testAndSaveStalkerSettings(settings: LiveTvStalkerSettings): Result<Int> = withContext(Dispatchers.IO) {
        val normalized = settings.copy(
            portalUrl = settings.portalUrl.trim().trimEnd('/'),
            macAddress = settings.macAddress.trim().uppercase(),
            username = settings.username.trim(),
            password = settings.password.trim(),
        )
        runCatching {
            val channels = fetchStalkerChannels(normalized)
            if (channels.isEmpty()) {
                throw IllegalStateException("Bắt tay thành công nhưng không tìm thấy kênh nào (0 kênh).")
            }
            withContext(Dispatchers.Main) {
                saveStalkerSettings(normalized)
            }
            channels.size
        }
    }

    suspend fun prepareForPlayback(channel: LiveTvChannel): LiveTvChannel {
        var prepared = channel

        val isStalker = prepared.playlistId == STALKER_PLAYLIST_ID || !prepared.stalkerCommand.isNullOrBlank()
        if (isStalker) {
            prepared = preparePortalChannelForPlayback(prepared, _uiState.value.stalkerSettings)
        } else {
            val playlistSource = _uiState.value.playlists.firstOrNull { it.id == prepared.playlistId }?.source
                ?: _uiState.value.playlistUrl
            val resolution = resolveStreamMetadata(prepared.streamUrl, prepared.headers, playlistSource)
            if (resolution.finalUrl != prepared.streamUrl || resolution.detectedType != null) {
                prepared = prepared.copy(
                    streamUrl = resolution.finalUrl,
                    streamType = resolution.detectedType ?: prepared.streamType
                )
            }
        }

        // Resolve ClearKey HTTP URL to JWK JSON if needed (with short timeout to avoid blocking)
        val drmKey = prepared.drmKey
        val isClearKey = prepared.drmType?.contains("clearkey", ignoreCase = true) == true ||
            (prepared.drmType.isNullOrBlank() && drmKey != null && !drmKey.startsWith("http", ignoreCase = true))

        if (isClearKey && drmKey != null && (drmKey.startsWith("http://", ignoreCase = true) || drmKey.startsWith("https://", ignoreCase = true))) {
            val resolvedJwk = withContext(Dispatchers.IO) {
                runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(1500L) {
                        ClearKeyUtil.fetchClearKeyJson(drmKey, prepared.headers)
                    }
                }.getOrNull()
            }
            if (!resolvedJwk.isNullOrBlank()) {
                prepared = prepared.copy(drmKey = resolvedJwk)
            }
        }

        return prepared
    }

    private data class StreamResolutionResult(
        val finalUrl: String,
        val detectedType: String?
    )

    private suspend fun resolveStreamMetadata(
        initialUrl: String,
        headers: Map<String, String>,
        playlistSourceUrl: String?
    ): StreamResolutionResult = withContext(Dispatchers.IO) {
        runCatching {
            // Register playlist host as known-good so DynamicHostFallback can fallback to it
            if (!playlistSourceUrl.isNullOrBlank()) {
                DynamicHostFallback.registerWorkingHost(playlistSourceUrl)
            }

            // 1. Smart DNS Fallback via DynamicHostFallback (zero hardcoded domains)
            var currentUrl = DynamicHostFallback.normalizeUrlWithFallback(
                url = initialUrl,
                preferredFallbackHost = playlistSourceUrl?.let {
                    runCatching { URL(it).host }.getOrNull()
                }
            )

            // If fallback changed nothing, also try DNS resolution check
            if (currentUrl == initialUrl) {
                val streamHost = runCatching { URL(initialUrl).host }.getOrNull()
                if (!streamHost.isNullOrBlank()) {
                    val isResolvable = runCatching {
                        java.net.InetAddress.getByName(streamHost) != null
                    }.getOrDefault(false)
                    if (!isResolvable) {
                        val fallback = DynamicHostFallback.getFallbackHost(streamHost)
                        if (!fallback.isNullOrBlank()) {
                            currentUrl = initialUrl.replaceFirst(streamHost, fallback)
                        }
                    }
                }
            }

            // 2. Early return for known static extensions (avoid unnecessary network probe)
            if (!DynamicHostFallback.isDynamicLiveStreamUrl(currentUrl) && currentUrl == initialUrl) {
                val type = when {
                    currentUrl.contains(".m3u8", ignoreCase = true) -> "m3u8"
                    currentUrl.contains(".mpd", ignoreCase = true) -> "mpd"
                    currentUrl.contains(".ts", ignoreCase = true) -> "ts"
                    currentUrl.contains(".flv", ignoreCase = true) -> "flv"
                    currentUrl.contains(".mp4", ignoreCase = true) -> "mp4"
                    currentUrl.contains(".mkv", ignoreCase = true) -> "mkv"
                    else -> null
                }
                return@withContext StreamResolutionResult(currentUrl, type)
            }

            // 3. Dynamic Stream Resolver: follow redirects and detect MIME from server
            var redirectCount = 0
            var detectedType: String? = null
            while (redirectCount < 5) {
                val connection = (URL(currentUrl).openConnection() as? HttpURLConnection) ?: break
                connection.instanceFollowRedirects = false
                headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                try {
                    val code = connection.responseCode
                    val contentType = connection.contentType?.lowercase()
                    if (code in 300..399) {
                        val location = connection.getHeaderField("Location")
                        connection.disconnect()
                        if (location.isNullOrBlank()) break
                        currentUrl = if (location.startsWith("http")) location else URL(URL(currentUrl), location).toString()
                        redirectCount++
                    } else {
                        if (contentType != null) {
                            detectedType = when {
                                contentType.contains("application/vnd.apple.mpegurl") || contentType.contains("application/x-mpegurl") || contentType.contains("mpegurl") -> "m3u8"
                                contentType.contains("video/mp2t") -> "ts"
                                contentType.contains("application/dash+xml") -> "mpd"
                                contentType.contains("video/x-flv") || contentType.contains("video/flv") || contentType.contains("flv") -> "flv"
                                contentType.contains("video/mp4") -> "mp4"
                                contentType.contains("video/x-matroska") -> "mkv"
                                else -> null
                            }
                        }
                        connection.disconnect()
                        break
                    }
                } catch (e: Exception) {
                    connection.disconnect()
                    break
                }
            }

            if (detectedType == null) {
                detectedType = when {
                    currentUrl.contains(".m3u8", ignoreCase = true) -> "m3u8"
                    currentUrl.contains(".mpd", ignoreCase = true) -> "mpd"
                    currentUrl.contains(".ts", ignoreCase = true) -> "ts"
                    currentUrl.contains(".flv", ignoreCase = true) -> "flv"
                    currentUrl.contains(".mp4", ignoreCase = true) -> "mp4"
                    currentUrl.contains(".mkv", ignoreCase = true) -> "mkv"
                    else -> null
                }
            }

            StreamResolutionResult(currentUrl, detectedType)
        }.getOrDefault(StreamResolutionResult(initialUrl, null))
    }

    private var refreshJob: Job? = null

    fun refresh(force: Boolean = true, showLoadingIfHasChannels: Boolean = true) {
        ensureLoaded()
        val currentState = _uiState.value
        val playlists = currentState.playlists
        if (!currentState.hasPlaylist) {
            _uiState.value = _uiState.value.copy(
                playlistUrl = "",
                playlists = emptyList(),
                channels = emptyList(),
                isLoading = false,
                errorMessage = null,
            )
            LiveTvStorage.clearChannelsCache()
            publishNavigationVisibility()
            return
        }

        val enabledPlaylists = playlists.filter { it.isEnabled }
        val hasEnabledPortal = (currentState.xtreamSettings.isConfigured && currentState.xtreamSettings.isEnabled) ||
            (currentState.stalkerSettings.isConfigured && currentState.stalkerSettings.isEnabled)
        if (enabledPlaylists.isEmpty() && !hasEnabledPortal) {
            _uiState.value = _uiState.value.copy(
                playlistUrl = "",
                playlists = playlists,
                channels = emptyList(),
                isLoading = false,
                errorMessage = null,
            )
            LiveTvStorage.clearChannelsCache()
            return
        }

        val signature = computeConfigSignature()
        val savedSignature = LiveTvStorage.loadCacheConfigSignature()
        val isExpired = LiveTvStorage.isCacheExpired()

        if (!force && !isExpired && signature == savedSignature && currentState.channels.isNotEmpty()) {
            return
        }

        val showLoading = showLoadingIfHasChannels || currentState.channels.isEmpty()
        if (showLoading) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        }

        refreshJob?.cancel()
        refreshJob = scope.launch {
            val loadedChannels = mutableListOf<LiveTvChannel>()
            val failedPlaylistNames = mutableListOf<String>()

            // Phase 1+2: Load all sources (M3U playlists, Xtream, Stalker) concurrently in parallel
            coroutineScope {
                val tasks = mutableListOf<Deferred<*>>()

                if (enabledPlaylists.isNotEmpty()) {
                    tasks.add(async {
                        val (m3uChannels, m3uFailed) = M3uPlaylistService.loadPlaylistsParallel(
                            liveTvHttpClient, enabledPlaylists
                        )
                        synchronized(loadedChannels) { loadedChannels += m3uChannels }
                        synchronized(failedPlaylistNames) { failedPlaylistNames += m3uFailed }
                    })
                }

                if (currentState.xtreamSettings.isConfigured && currentState.xtreamSettings.isEnabled) {
                    tasks.add(async {
                        runCatching {
                            fetchXtreamChannels(currentState.xtreamSettings)
                        }.fold(
                            onSuccess = { channels -> synchronized(loadedChannels) { loadedChannels += channels } },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                synchronized(failedPlaylistNames) { failedPlaylistNames += "Xtream Codes" }
                                Log.w(TAG, "Failed to load Xtream channels", error)
                            }
                        )
                    })
                }

                if (currentState.stalkerSettings.isConfigured && currentState.stalkerSettings.isEnabled) {
                    tasks.add(async {
                        runCatching {
                            fetchStalkerChannels(currentState.stalkerSettings)
                        }.fold(
                            onSuccess = { channels -> synchronized(loadedChannels) { loadedChannels += channels } },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                synchronized(failedPlaylistNames) { failedPlaylistNames += "Stalker Portal" }
                                Log.w(TAG, "Failed to load Stalker channels", error)
                            }
                        )
                    })
                }

                tasks.awaitAll()
            }

            val channels = loadedChannels.distinctBy { it.id.ifBlank { it.streamUrl } }
            val finalChannels = if (channels.isEmpty() && currentState.channels.isNotEmpty()) {
                currentState.channels
            } else {
                channels
            }

            _uiState.value = _uiState.value.copy(
                playlistUrl = playlists.firstEnabledUrlSource(),
                playlists = playlists,
                channels = finalChannels,
                isLoading = false,
                errorMessage = when {
                    finalChannels.isEmpty() && failedPlaylistNames.isNotEmpty() -> "Playlist could not be loaded."
                    finalChannels.isEmpty() -> "No channels found in these playlists."
                    failedPlaylistNames.isNotEmpty() -> "Some playlists could not be loaded: ${failedPlaylistNames.joinToString()}"
                    else -> null
                },
            )

            if (channels.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    LiveTvStorage.saveChannelsCache(channels, signature)
                }
            }
        }
    }

    fun toggleFavoriteChannel(channelId: String) {
        ensureLoaded()
        val favorites = _uiState.value.favoriteChannelIds
            .let { current ->
                if (channelId in current) {
                    current - channelId
                } else {
                    current + channelId
                }
            }
        persistFavoriteChannelIds(favorites)
        _uiState.value = _uiState.value.copy(favoriteChannelIds = favorites)
    }

    fun markChannelWatched(channel: LiveTvChannel) {
        ensureLoaded()
        val currentRecent = _uiState.value.recentChannelIds
        val updatedRecent = (listOf(channel.id) + currentRecent.filterNot { it == channel.id }).take(MAX_RECENT_CHANNELS)
        persistRecentChannelIds(updatedRecent)
        LiveTvStorage.saveLastWatchedChannelId(channel.id)
        _uiState.value = _uiState.value.copy(
            lastWatchedChannelId = channel.id,
            recentChannelIds = updatedRecent
        )
    }

    fun recordLastWatched(channel: LiveTvChannel) = markChannelWatched(channel)

    private fun publishNavigationVisibility() {
        LiveTvStorage.publishNavigationVisibility(_uiState.value.showInNavigation)
    }

    private fun loadSavedPlaylists(): List<LiveTvPlaylist> {
        val saved = decodePlaylists(LiveTvStorage.loadPlaylistsBlob().orEmpty())
        if (saved.isNotEmpty()) return saved

        val legacyUrl = LiveTvStorage.loadPlaylistUrl()?.trim().orEmpty()
        return if (legacyUrl.isBlank()) emptyList() else listOf(createUrlPlaylist(legacyUrl))
    }

    private fun persistPlaylists(playlists: List<LiveTvPlaylist>) {
        LiveTvStorage.savePlaylistsBlob(encodePlaylists(playlists))
        LiveTvStorage.savePlaylistUrl(playlists.firstUrlSource())
    }

    private fun loadFavoriteChannelIds(): Set<String> =
        LiveTvStorage.loadFavoriteChannelIdsBlob()
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()

    private fun persistFavoriteChannelIds(channelIds: Set<String>) {
        LiveTvStorage.saveFavoriteChannelIdsBlob(channelIds.sorted().joinToString("\n"))
    }

    private const val MAX_RECENT_CHANNELS = 15

    private fun loadRecentChannelIds(): List<String> {
        val blob = LiveTvStorage.loadRecentChannelIdsBlob()
        if (!blob.isNullOrBlank()) {
            return blob.lineSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .take(MAX_RECENT_CHANNELS)
                .toList()
        }
        val legacyLastWatched = LiveTvStorage.loadLastWatchedChannelId()?.trim()
        return if (!legacyLastWatched.isNullOrBlank()) listOf(legacyLastWatched) else emptyList()
    }

    private fun persistRecentChannelIds(channelIds: List<String>) {
        LiveTvStorage.saveRecentChannelIdsBlob(channelIds.take(MAX_RECENT_CHANNELS).joinToString("\n"))
    }
}

private fun createUrlPlaylist(url: String, customName: String? = null): LiveTvPlaylist =
    LiveTvPlaylist(
        id = stablePlaylistId(url, 0),
        name = customName?.trim()?.takeIf(String::isNotBlank) ?: playlistNameFromUrl(url),
        type = LiveTvPlaylistType.Url,
        source = url,
    )

private fun playlistNameFromUrl(url: String): String {
    val trimmed = url.trim()
    val fileName = trimmed
        .substringBefore('?')
        .substringAfterLast('/')
        .substringBeforeLast('.', missingDelimiterValue = "")
        .trim()
    if (fileName.isNotBlank()) return fileName

    return trimmed
        .substringAfter("://", missingDelimiterValue = trimmed)
        .substringBefore('/')
        .trim()
        .ifBlank { "M3U playlist" }
}

private fun List<LiveTvPlaylist>.firstUrlSource(): String =
    firstOrNull { it.type == LiveTvPlaylistType.Url }?.source.orEmpty()

private fun List<LiveTvPlaylist>.firstEnabledUrlSource(): String =
    firstOrNull { it.isEnabled && it.type == LiveTvPlaylistType.Url }?.source.orEmpty()

private fun decodePlaylistEnabled(value: String?): Boolean =
    value?.equals("false", ignoreCase = true) != true

private const val playlistRecordSeparator = "\u001E"
private const val playlistFieldSeparator = "\u001F"

private fun encodePlaylists(playlists: List<LiveTvPlaylist>): String =
    playlists.joinToString(playlistRecordSeparator) { playlist ->
        listOf(
            playlist.id,
            playlist.name,
            playlist.type.name,
            playlist.source,
            playlist.isEnabled.toString(),
        ).joinToString(playlistFieldSeparator) { escapePlaylistField(it) }
    }

private fun decodePlaylists(blob: String): List<LiveTvPlaylist> =
    blob
        .split(playlistRecordSeparator)
        .mapNotNull { record ->
            if (record.isBlank()) return@mapNotNull null
            val fields = record.split(playlistFieldSeparator).map(::unescapePlaylistField)
            val type = fields.getOrNull(2)?.let { raw ->
                runCatching { LiveTvPlaylistType.valueOf(raw) }.getOrNull()
            } ?: return@mapNotNull null
            LiveTvPlaylist(
                id = fields.getOrNull(0)?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                name = fields.getOrNull(1)?.takeIf(String::isNotBlank) ?: "M3U playlist",
                type = type,
                source = fields.getOrNull(3)?.takeIf(String::isNotBlank) ?: return@mapNotNull null,
                isEnabled = decodePlaylistEnabled(fields.getOrNull(4)),
            )
        }

private fun escapePlaylistField(value: String): String =
    value
        .replace("%", "%25")
        .replace(playlistRecordSeparator, "%1E")
        .replace(playlistFieldSeparator, "%1F")

private fun unescapePlaylistField(value: String): String =
    value
        .replace("%1F", playlistFieldSeparator)
        .replace("%1E", playlistRecordSeparator)
        .replace("%25", "%")

private fun stablePlaylistId(source: String, index: Int): String =
    "playlist_${source.hashCode()}_$index"
