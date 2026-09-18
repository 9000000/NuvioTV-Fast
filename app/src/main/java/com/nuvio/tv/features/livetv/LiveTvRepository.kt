package com.nuvio.tv.features.livetv

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

/**
 * Utility to fetch text from a URL.
 * In a real app, this would use OkHttp or similar.
 */
suspend fun httpGetText(url: String): String = httpGetTextWithHeaders(url, emptyMap())

suspend fun httpGetTextWithHeaders(url: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

object LiveTvRepository {
    private const val TAG = "LiveTvRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        val playlists = loadSavedPlaylists()
        _uiState.value = LiveTvUiState(
            playlistUrl = playlists.firstEnabledUrlSource(),
            playlists = playlists,
            stalkerSettings = LiveTvStorage.loadStalkerSettings(),
            xtreamSettings = LiveTvStorage.loadXtreamSettings(),
            favoriteChannelIds = loadFavoriteChannelIds(),
            lastWatchedChannelId = LiveTvStorage.loadLastWatchedChannelId(),
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

    suspend fun prepareForPlayback(channel: LiveTvChannel): LiveTvChannel =
        if (channel.stalkerCommand.isNullOrBlank()) channel
        else preparePortalChannelForPlayback(channel, _uiState.value.stalkerSettings)

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
        LiveTvStorage.saveLastWatchedChannelId(channel.id)
        _uiState.value = _uiState.value.copy(lastWatchedChannelId = channel.id)
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
}

private data class PendingM3uEntry(
    var info: M3uInfo? = null,
    val headers: MutableMap<String, String> = mutableMapOf(),
    var licenseType: String? = null,
    var licenseKey: String? = null,
    var manifestType: String? = null,
)

internal fun parseM3uPlaylist(
    payload: String,
    playlist: LiveTvPlaylist? = null,
): List<LiveTvChannel> {
    val channels = mutableListOf<LiveTvChannel>()
    var pending = PendingM3uEntry()

    payload.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .forEach { line ->
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending.info = parseExtInf(line)
                }
                line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                    val prop = line.substringAfter("#KODIPROP:", "").trim()
                    val key = prop.substringBefore('=').trim()
                    val value = prop.substringAfter('=', "").trim()
                    when {
                        key.equals("inputstream.adaptive.license_type", ignoreCase = true) -> {
                            pending.licenseType = value
                        }
                        key.equals("inputstream.adaptive.license_key", ignoreCase = true) -> {
                            pending.licenseKey = value
                        }
                        key.equals("inputstream.adaptive.manifest_type", ignoreCase = true) -> {
                            pending.manifestType = value
                        }
                    }
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter("#EXTVLCOPT:", "").trim()
                    val key = opt.substringBefore('=').trim()
                    val value = opt.substringAfter('=', "").trim()
                    when {
                        key.equals("http-user-agent", ignoreCase = true) -> pending.headers["User-Agent"] = value
                        key.equals("http-referrer", ignoreCase = true) -> pending.headers["Referer"] = value
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val (rawUrl, pipeHeaders) = parseUrlAndPipeHeaders(line)
                    val combinedHeaders = (pending.headers + pipeHeaders).toMap()
                    val info = pending.info
                    val streamUrl = rawUrl
                    val name = info?.name?.takeIf(String::isNotBlank)
                        ?: streamUrl.substringAfterLast('/').substringBefore('?').ifBlank { "Channel" }

                    val detectedStreamType = when {
                        pending.manifestType?.equals("mpd", ignoreCase = true) == true || streamUrl.contains(".mpd", ignoreCase = true) -> "mpd"
                        pending.manifestType?.equals("hls", ignoreCase = true) == true || streamUrl.contains(".m3u8", ignoreCase = true) -> "m3u8"
                        else -> null
                    }

                    val detectedDrmType = when {
                        !pending.licenseType.isNullOrBlank() -> pending.licenseType
                        !pending.licenseKey.isNullOrBlank() -> "clearkey"
                        else -> null
                    }

                    channels += LiveTvChannel(
                        id = stableChannelId(streamUrl, channels.size),
                        name = name,
                        streamUrl = streamUrl,
                        logoUrl = info?.logoUrl?.takeIf(String::isNotBlank),
                        group = info?.group?.takeIf(String::isNotBlank),
                        playlistId = playlist?.id,
                        playlistName = playlist?.name,
                        headers = combinedHeaders,
                        streamType = detectedStreamType,
                        drmType = detectedDrmType,
                        drmKey = pending.licenseKey,
                    )
                    pending = PendingM3uEntry()
                }
            }
        }

    return channels.distinctBy { it.streamUrl }
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
)

private fun parseExtInf(line: String): M3uInfo {
    val name = line.substringAfter(',', missingDelimiterValue = "")
        .trim()
        .ifBlank {
            readM3uAttribute(line, "tvg-name").orEmpty()
        }
    return M3uInfo(
        name = name,
        logoUrl = readM3uAttribute(line, "tvg-logo"),
        group = readM3uAttribute(line, "group-title"),
    )
}

private fun readM3uAttribute(line: String, key: String): String? {
    val marker = "$key=\""
    val start = line.indexOf(marker, ignoreCase = true)
    if (start < 0) return null
    val valueStart = start + marker.length
    val valueEnd = line.indexOf('"', startIndex = valueStart).takeIf { it >= 0 } ?: return null
    return line.substring(valueStart, valueEnd).trim()
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

private fun stableChannelId(url: String, index: Int): String =
    "channel_${url.hashCode()}_$index"
