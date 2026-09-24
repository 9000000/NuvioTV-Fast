package com.nuvio.tv.features.livetv

import android.content.Context
import android.util.Log
import com.nuvio.tv.R
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.core.server.DeviceIpAddress
import com.nuvio.tv.core.server.LiveTvConfigServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import com.nuvio.tv.ui.screens.player.ClearKeyUtil
import com.nuvio.tv.ui.screens.player.IptvHeaderProvider
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Utility to fetch text from a URL.
 * In a real app, this would use OkHttp or similar.
 */
suspend fun httpGetText(url: String): String = httpGetTextWithHeaders(url, emptyMap())

suspend fun httpGetTextWithHeaders(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
    // Follow up to 5 redirects, including cross-protocol HTTP → HTTPS
    var currentUrl = url
    var redirectCount = 0
    while (true) {
        val connection = URL(currentUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false // handle manually for cross-protocol
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        try {
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank() || redirectCount >= 5) {
                    error("Too many redirects or missing Location for $url")
                }
                // Resolve relative redirects
                currentUrl = if (location.startsWith("http")) location
                             else URL(URL(currentUrl), location).toString()
                redirectCount++
                continue
            }
            return@withContext connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
    @Suppress("UNREACHABLE_CODE")
    error("unreachable")
}

object LiveTvRepository {
    private const val TAG = "LiveTvRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

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
        _uiState.value = LiveTvUiState(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            stalkerSettings = LiveTvStorage.loadStalkerSettings(),
            xtreamSettings = LiveTvStorage.loadXtreamSettings(),
            favoriteChannelIds = loadFavoriteChannelIds(),
            lastWatchedChannelId = lastWatched,
            recentChannelIds = recentIds,
            isNavigationEnabled = LiveTvStorage.loadNavigationEnabled() ?: true,
        )
        publishNavigationVisibility()
        if (_uiState.value.hasPlaylist) {
            refresh()
        }
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
            refresh()
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
        if (playlists.any { it.isEnabled }) {
            refresh()
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

    suspend fun prepareForPlayback(channel: LiveTvChannel): LiveTvChannel {
        var prepared = if (channel.stalkerCommand.isNullOrBlank()) channel
        else preparePortalChannelForPlayback(channel, _uiState.value.stalkerSettings)

        // Resolve ClearKey HTTP URL to JWK JSON if needed
        val drmKey = prepared.drmKey
        val isClearKey = prepared.drmType?.contains("clearkey", ignoreCase = true) == true ||
            (prepared.drmType.isNullOrBlank() && drmKey != null && !drmKey.startsWith("http", ignoreCase = true))

        if (isClearKey && drmKey != null && (drmKey.startsWith("http://", ignoreCase = true) || drmKey.startsWith("https://", ignoreCase = true))) {
            val resolvedJwk = withContext(Dispatchers.IO) {
                ClearKeyUtil.fetchClearKeyJson(drmKey, prepared.headers)
            }
            if (!resolvedJwk.isNullOrBlank()) {
                prepared = prepared.copy(drmKey = resolvedJwk)
            }
        }

        return prepared
    }

    fun refresh() {
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
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        scope.launch {
            val loadedChannels = mutableListOf<LiveTvChannel>()
            val failedPlaylistNames = mutableListOf<String>()

            enabledPlaylists.forEach { playlist ->
                val result = runCatching {
                    val payload = when (playlist.type) {
                        LiveTvPlaylistType.Url -> withContext(Dispatchers.Default) { httpGetText(playlist.source) }
                        LiveTvPlaylistType.LocalFile -> playlist.source
                    }
                    parseM3uPlaylist(payload, playlist)
                }

                result.fold(
                    onSuccess = { channels -> loadedChannels += channels },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        failedPlaylistNames += playlist.name
                        Log.w(TAG, "Failed to load live TV playlist ${playlist.name}", error)
                    },
                )
            }

            if (currentState.xtreamSettings.isConfigured && currentState.xtreamSettings.isEnabled) {
                runCatching {
                    fetchXtreamChannels(currentState.xtreamSettings)
                }.fold(
                    onSuccess = { channels -> loadedChannels += channels },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        failedPlaylistNames += "Xtream Codes"
                        Log.w(TAG, "Failed to load Xtream channels", error)
                    }
                )
            }

            if (currentState.stalkerSettings.isConfigured && currentState.stalkerSettings.isEnabled) {
                runCatching {
                    fetchStalkerChannels(currentState.stalkerSettings)
                }.fold(
                    onSuccess = { channels -> loadedChannels += channels },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        failedPlaylistNames += "Stalker Portal"
                        Log.w(TAG, "Failed to load Stalker channels", error)
                    }
                )
            }

            val channels = loadedChannels.distinctBy { it.streamUrl }
            _uiState.value = _uiState.value.copy(
                playlistUrl = playlists.firstEnabledUrlSource(),
                playlists = playlists,
                channels = channels,
                isLoading = false,
                errorMessage = when {
                    channels.isEmpty() && failedPlaylistNames.isNotEmpty() -> "Playlist could not be loaded."
                    channels.isEmpty() -> "No channels found in these playlists."
                    failedPlaylistNames.isNotEmpty() -> "Some playlists could not be loaded: ${failedPlaylistNames.joinToString()}"
                    else -> null
                },
            )
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

private data class PendingM3uEntry(
    var info: M3uInfo? = null,
    val headers: MutableMap<String, String> = mutableMapOf(),
    var licenseType: String? = null,
    var licenseKey: String? = null,
    var manifestType: String? = null,
    var group: String? = null,
)

internal fun parseM3uPlaylist(
    payload: String,
    playlist: LiveTvPlaylist? = null,
): List<LiveTvChannel> {
    val channels = mutableListOf<LiveTvChannel>()
    val playlistDefaultHeaders = mutableMapOf<String, String>()
    var firstChannelAdded = false
    var pending = PendingM3uEntry()

    payload.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .forEach { line ->
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val ua = readM3uAttribute(line, "http-user-agent") ?: readM3uAttribute(line, "user-agent")
                    if (!ua.isNullOrBlank()) playlistDefaultHeaders["User-Agent"] = ua
                    val ref = readM3uAttribute(line, "http-referrer") ?: readM3uAttribute(line, "referrer") ?: readM3uAttribute(line, "referer")
                    if (!ref.isNullOrBlank()) playlistDefaultHeaders["Referer"] = ref
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val info = parseExtInf(line)
                    pending.info = info
                    info.licenseType?.let { pending.licenseType = it }
                    info.licenseKey?.let { pending.licenseKey = it }
                    info.manifestType?.let { pending.manifestType = it }
                    info.group?.let { pending.group = it }
                    pending.headers.putAll(info.headers)
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val grp = line.substringAfter("#EXTGRP:", "").trim()
                    if (grp.isNotBlank()) {
                        pending.group = grp
                    }
                }
                line.startsWith("#KODIPROP:", ignoreCase = true) ||
                line.startsWith("#EXT-X-KODI:", ignoreCase = true) ||
                line.startsWith("#EXT-X-KODIPROP:", ignoreCase = true) ||
                line.startsWith("#EXTKODI:", ignoreCase = true) -> {
                    val kodiProp = extractKodiProp(line)
                    if (kodiProp != null) {
                        val (key, value) = kodiProp
                        when {
                            key.equals("inputstream.adaptive.license_type", ignoreCase = true) ||
                            key.equals("license_type", ignoreCase = true) -> {
                                pending.licenseType = value
                            }
                            key.equals("inputstream.adaptive.license_key", ignoreCase = true) ||
                            key.equals("license_key", ignoreCase = true) -> {
                                pending.licenseKey = value
                            }
                            key.equals("inputstream.adaptive.manifest_type", ignoreCase = true) ||
                            key.equals("manifest_type", ignoreCase = true) -> {
                                pending.manifestType = value
                            }
                            key.equals("inputstream.adaptive.stream_headers", ignoreCase = true) ||
                            key.equals("stream_headers", ignoreCase = true) -> {
                                value.split('&').forEach { param ->
                                    val hKey = param.substringBefore('=').trim()
                                    val hVal = param.substringAfter('=', "").trim()
                                    if (hKey.isNotBlank() && hVal.isNotBlank()) {
                                        val normalizedKey = when (hKey.lowercase()) {
                                            "user-agent" -> "User-Agent"
                                            "referer" -> "Referer"
                                            "origin" -> "Origin"
                                            "authorization" -> "Authorization"
                                            else -> hKey
                                        }
                                        pending.headers[normalizedKey] = hVal
                                    }
                                }
                            }
                        }
                    }
                }
                line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                    val keyFormat = readM3uAttribute(line, "KEYFORMAT")?.lowercase().orEmpty()
                    val uri = readM3uAttribute(line, "URI")
                    if (!uri.isNullOrBlank()) {
                        pending.licenseKey = uri
                        when {
                            keyFormat.contains("widevine") || keyFormat.contains("edef8ba9") -> {
                                pending.licenseType = "widevine"
                            }
                            keyFormat.contains("clearkey") || keyFormat.contains("1077efec") || keyFormat == "identity" -> {
                                pending.licenseType = "clearkey"
                            }
                            keyFormat.contains("playready") || keyFormat.contains("9a04f079") -> {
                                pending.licenseType = "playready"
                            }
                        }
                    }
                }
                line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                    val jsonStr = line.substringAfter("#EXTHTTP:", "").trim()
                    runCatching {
                        val json = JSONObject(jsonStr)
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val v = json.optString(k)
                            if (v.isNotBlank()) {
                                val normalizedKey = when (k.lowercase()) {
                                    "user-agent" -> "User-Agent"
                                    "referer" -> "Referer"
                                    "origin" -> "Origin"
                                    "authorization" -> "Authorization"
                                    else -> k
                                }
                                pending.headers[normalizedKey] = v
                            }
                        }
                    }
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter("#EXTVLCOPT:", "").trim()
                    val key = opt.substringBefore('=').trim()
                    val value = opt.substringAfter('=', "").trim()
                    when {
                        key.equals("http-user-agent", ignoreCase = true) || key.equals("user-agent", ignoreCase = true) -> {
                            pending.headers["User-Agent"] = value
                            if (!firstChannelAdded) playlistDefaultHeaders["User-Agent"] = value
                        }
                        key.equals("http-referrer", ignoreCase = true) || key.equals("referrer", ignoreCase = true) || key.equals("referer", ignoreCase = true) -> {
                            pending.headers["Referer"] = value
                            if (!firstChannelAdded) playlistDefaultHeaders["Referer"] = value
                        }
                        key.equals("http-origin", ignoreCase = true) || key.equals("origin", ignoreCase = true) -> {
                            pending.headers["Origin"] = value
                            if (!firstChannelAdded) playlistDefaultHeaders["Origin"] = value
                        }
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    if (isM3uNoiseOrDivider(line) || !isValidStreamUrl(line)) {
                        return@forEach
                    }

                    val (rawUrl, pipeHeaders) = parseUrlAndPipeHeaders(line)
                    val (cleanLicenseKey, licensePipeHeaders) = pending.licenseKey?.let { parseUrlAndPipeHeaders(it) }
                        ?: (null to emptyMap())

                    val info = pending.info
                    val streamUrl = rawUrl
                    val name = info?.name?.takeIf(String::isNotBlank)
                        ?: streamUrl.substringAfterLast('/').substringBefore('?').ifBlank { "Channel" }

                    val effectiveManifestType = (pending.manifestType ?: info?.manifestType)?.trim()?.lowercase()
                    val detectedStreamType = when {
                        effectiveManifestType == "mpd" || streamUrl.contains(".mpd", ignoreCase = true) -> "mpd"
                        effectiveManifestType == "hls" || effectiveManifestType == "m3u8" || streamUrl.contains(".m3u8", ignoreCase = true) -> "m3u8"
                        effectiveManifestType == "ism" || effectiveManifestType == "isml" || streamUrl.contains(".ism", ignoreCase = true) -> "ism"
                        else -> null
                    }

                    val rawDrmType = (pending.licenseType ?: info?.licenseType)?.trim()?.lowercase()
                    val effectiveKey = cleanLicenseKey ?: info?.licenseKey
                    val detectedDrmType = when {
                        rawDrmType != null -> when {
                            rawDrmType.contains("clearkey") || rawDrmType == "org.w3.clearkey" -> "clearkey"
                            rawDrmType.contains("widevine") || rawDrmType == "com.widevine.alpha" -> "widevine"
                            rawDrmType.contains("playready") || rawDrmType == "com.microsoft.playready" -> "playready"
                            else -> rawDrmType
                        }
                        !effectiveKey.isNullOrBlank() -> {
                            if (effectiveKey.startsWith("http://", ignoreCase = true) ||
                                effectiveKey.startsWith("https://", ignoreCase = true)) {
                                "widevine"
                            } else {
                                "clearkey"
                            }
                        }
                        else -> null
                    }

                    val combinedHeaders = buildMap {
                        putAll(playlistDefaultHeaders)
                        putAll(pending.headers)
                        putAll(pipeHeaders)
                        putAll(licensePipeHeaders)
                        if (!containsKey("User-Agent")) {
                            if (detectedDrmType != null || detectedStreamType == "mpd" || IptvHeaderProvider.isIptvStream(streamUrl)) {
                                put("User-Agent", IptvHeaderProvider.DEFAULT_IPTV_USER_AGENT)
                            }
                        }
                    }

                    val group = pending.group ?: info?.group?.takeIf(String::isNotBlank)

                    channels += LiveTvChannel(
                        id = stableChannelId(playlist?.id, streamUrl),
                        name = name,
                        streamUrl = streamUrl,
                        logoUrl = info?.logoUrl?.takeIf(String::isNotBlank),
                        group = group,
                        playlistId = playlist?.id,
                        playlistName = playlist?.name,
                        headers = combinedHeaders,
                        streamType = detectedStreamType,
                        drmType = detectedDrmType,
                        drmKey = effectiveKey,
                    )
                    firstChannelAdded = true
                    pending = PendingM3uEntry()
                    pending.headers.putAll(playlistDefaultHeaders)
                }
            }
        }

    return channels.distinctBy { it.streamUrl }
}

private fun extractKodiProp(line: String): Pair<String, String>? {
    val prefix = when {
        line.startsWith("#KODIPROP:", ignoreCase = true) -> "#KODIPROP:"
        line.startsWith("#EXT-X-KODI:", ignoreCase = true) -> "#EXT-X-KODI:"
        line.startsWith("#EXT-X-KODIPROP:", ignoreCase = true) -> "#EXT-X-KODIPROP:"
        line.startsWith("#EXTKODI:", ignoreCase = true) -> "#EXTKODI:"
        else -> return null
    }
    val prop = line.substringAfter(prefix, "").trim()
    val key = prop.substringBefore('=').trim()
    val value = prop.substringAfter('=', "").trim()
    return if (key.isNotEmpty()) key to value else null
}

private fun isM3uNoiseOrDivider(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return true
    if (trimmed.startsWith("//")) return true

    val nonDividerChars = trimmed.count { char ->
        char != '=' && char != '-' && char != '_' && char != '*' &&
            char != '~' && char != '<' && char != '>' && char != '#' &&
            char != '|' && char != '/' && char != '\\' && !char.isWhitespace()
    }
    if (nonDividerChars == 0) return true

    if (!trimmed.contains("://") && (trimmed.contains("====") || trimmed.contains("----") || trimmed.contains("____"))) {
        return true
    }
    return false
}

private fun isValidStreamUrl(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("rtmp://", ignoreCase = true) ||
        trimmed.startsWith("rtsp://", ignoreCase = true) ||
        trimmed.startsWith("udp://", ignoreCase = true) ||
        trimmed.startsWith("rtp://", ignoreCase = true) ||
        trimmed.startsWith("mms://", ignoreCase = true)
    ) {
        return true
    }
    if (trimmed.contains("://")) return true
    val lower = trimmed.lowercase()
    return lower.endsWith(".m3u8") || lower.endsWith(".mpd") || lower.endsWith(".ts") ||
        lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".ism")
}

private fun parseUrlAndPipeHeaders(line: String): Pair<String, Map<String, String>> {
    if (!line.contains('|')) return line to emptyMap()
    val url = line.substringBefore('|').trim()
    val rawHeaders = line.substringAfter('|').trim()
    val headers = mutableMapOf<String, String>()
    rawHeaders.split('&').forEach { param ->
        val key = param.substringBefore('=').trim()
        val value = param.substringAfter('=', "").trim()
        if (key.isNotBlank() && value.isNotBlank()) {
            val normalizedKey = when (key.lowercase()) {
                "user-agent" -> "User-Agent"
                "referer" -> "Referer"
                "origin" -> "Origin"
                "authorization" -> "Authorization"
                else -> key
            }
            headers[normalizedKey] = value
        }
    }
    return url to headers
}

private data class M3uInfo(
    val name: String,
    val logoUrl: String?,
    val group: String?,
    val licenseType: String? = null,
    val licenseKey: String? = null,
    val manifestType: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

private fun parseExtInf(line: String): M3uInfo {
    val name = line.substringAfter(',', missingDelimiterValue = "")
        .trim()
        .ifBlank {
            readM3uAttribute(line, "tvg-name").orEmpty()
        }
    val logoUrl = readM3uAttribute(line, "tvg-logo") ?: readM3uAttribute(line, "logo")
    val group = readM3uAttribute(line, "group-title") ?: readM3uAttribute(line, "group")

    val inlineLicenseType = readM3uAttribute(line, "license_type")
        ?: readM3uAttribute(line, "drm_type")
        ?: readM3uAttribute(line, "kodi-license-type")
        ?: readM3uAttribute(line, "inputstream.adaptive.license_type")

    val inlineLicenseKey = readM3uAttribute(line, "license_key")
        ?: readM3uAttribute(line, "drm_key")
        ?: readM3uAttribute(line, "kodi-license-key")
        ?: readM3uAttribute(line, "inputstream.adaptive.license_key")

    val inlineManifestType = readM3uAttribute(line, "manifest_type")
        ?: readM3uAttribute(line, "stream_type")
        ?: readM3uAttribute(line, "inputstream.adaptive.manifest_type")

    val inlineHeaders = mutableMapOf<String, String>()
    (readM3uAttribute(line, "http-user-agent") ?: readM3uAttribute(line, "user-agent"))
        ?.takeIf(String::isNotBlank)?.let { inlineHeaders["User-Agent"] = it }
    (readM3uAttribute(line, "http-referrer") ?: readM3uAttribute(line, "referrer") ?: readM3uAttribute(line, "referer"))
        ?.takeIf(String::isNotBlank)?.let { inlineHeaders["Referer"] = it }
    (readM3uAttribute(line, "http-origin") ?: readM3uAttribute(line, "origin"))
        ?.takeIf(String::isNotBlank)?.let { inlineHeaders["Origin"] = it }

    return M3uInfo(
        name = name,
        logoUrl = logoUrl,
        group = group,
        licenseType = inlineLicenseType,
        licenseKey = inlineLicenseKey,
        manifestType = inlineManifestType,
        headers = inlineHeaders,
    )
}

private fun readM3uAttribute(line: String, key: String): String? {
    var searchIndex = 0
    val keyLen = key.length
    while (searchIndex < line.length) {
        val idx = line.indexOf(key, startIndex = searchIndex, ignoreCase = true)
        if (idx < 0) return null

        val isWordBoundaryBefore = idx == 0 ||
            line[idx - 1].isWhitespace() ||
            line[idx - 1] == '#' ||
            line[idx - 1] == ',' ||
            line[idx - 1] == ':'

        if (!isWordBoundaryBefore) {
            searchIndex = idx + keyLen
            continue
        }

        var afterKey = idx + keyLen
        while (afterKey < line.length && line[afterKey].isWhitespace()) {
            afterKey++
        }
        if (afterKey >= line.length || line[afterKey] != '=') {
            searchIndex = idx + keyLen
            continue
        }

        var valueStart = afterKey + 1
        while (valueStart < line.length && line[valueStart].isWhitespace()) {
            valueStart++
        }
        if (valueStart >= line.length) return null

        val quote = line[valueStart]
        return if (quote == '"' || quote == '\'') {
            val valueEnd = line.indexOf(quote, startIndex = valueStart + 1)
            if (valueEnd >= 0) {
                line.substring(valueStart + 1, valueEnd).trim()
            } else {
                null
            }
        } else {
            var end = valueStart
            while (end < line.length && !line[end].isWhitespace() && line[end] != ',') {
                end++
            }
            line.substring(valueStart, end).trim().takeIf { it.isNotEmpty() }
        }
    }
    return null
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

private fun stableChannelId(playlistId: String?, url: String): String {
    val prefix = playlistId?.takeIf(String::isNotBlank) ?: "m3u"
    return "channel_${prefix}_${url.hashCode()}"
}
