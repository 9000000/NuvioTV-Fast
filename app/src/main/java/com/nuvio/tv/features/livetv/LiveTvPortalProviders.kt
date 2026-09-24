package com.nuvio.tv.features.livetv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

internal const val STALKER_PLAYLIST_ID = "provider:stalker"
internal const val XTREAM_PLAYLIST_ID = "provider:xtream"

private val portalJson = Json { ignoreUnknownKeys = true; isLenient = true }
private val playlistRequestHeaders = mapOf("Accept" to "application/json, text/plain, */*", "User-Agent" to "Nuvio/1.0")
private val streamRequestHeaders = mapOf("User-Agent" to "Mozilla/5.0", "Accept" to "*/*")

private fun String.urlEncode(): String = try {
    URLEncoder.encode(this, "UTF-8")
} catch (e: Exception) {
    this
}

internal suspend fun fetchXtreamChannels(settings: LiveTvXtreamSettings): List<LiveTvChannel> {
    val normalized = settings.normalized()
    val categories = runCatching {
        xtreamRequest(normalized, "get_live_categories").arrayOrEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("category_id") ?: item.string("id") ?: return@mapNotNull null
            id to (item.string("category_name") ?: item.string("name") ?: id)
        }.toMap()
    }.getOrDefault(emptyMap())

    return xtreamRequest(normalized, "get_live_streams").arrayOrEmpty().mapIndexedNotNull { index, element ->
        val item = element as? JsonObject ?: return@mapIndexedNotNull null
        val name = item.string("name") ?: return@mapIndexedNotNull null
        val streamId = item.string("stream_id") ?: item.string("id") ?: return@mapIndexedNotNull null
        val extension = item.string("container_extension")?.trim()?.trimStart('.')?.ifBlank { null } ?: "ts"
        val directSource = item.string("direct_source")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        LiveTvChannel(
            id = "xtream:$streamId",
            name = name,
            streamUrl = directSource ?: normalized.liveStreamUrl(streamId, extension),
            logoUrl = item.string("stream_icon") ?: item.string("logo"),
            group = item.string("category_id")?.let(categories::get),
            playlistId = XTREAM_PLAYLIST_ID,
            playlistName = "Xtream",
            headers = streamRequestHeaders,
            streamType = extension,
        )
    }.distinctBy { it.streamUrl }
}

private suspend fun xtreamRequest(settings: LiveTvXtreamSettings, action: String): JsonElement {
    val url = "${settings.serverUrl}/player_api.php?username=${settings.username.urlEncode()}&password=${settings.password.urlEncode()}&action=${action.urlEncode()}"
    val response = httpGetTextWithHeaders(url, playlistRequestHeaders)
    return portalJson.parseToJsonElement(response)
}

private data class StalkerSession(
    val settings: LiveTvStalkerSettings,
    val token: String,
    val endpoint: String
)

@Volatile
private var cachedStalkerSession: StalkerSession? = null

private val PLAYABLE_URL_REGEX = Regex("""(https?://[^\s"']+|rtmps?://[^\s"']+|rtsps?://[^\s"']+)""", RegexOption.IGNORE_CASE)

private fun formatMacAddress(mac: String): String {
    val clean = mac.trim().uppercase().replace("-", ":")
    if (clean.matches(Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$"))) {
        return clean
    }
    val hexOnly = clean.replace(":", "")
    if (hexOnly.length == 12 && hexOnly.all { it in '0'..'9' || it in 'A'..'F' }) {
        return hexOnly.chunked(2).joinToString(":")
    }
    return clean
}

private fun extractPlayableUrl(raw: String, serverBaseUrl: String): String {
    val trimmed = raw.trim().trim('"', '\'')
    if (trimmed.isBlank()) return ""

    val match = PLAYABLE_URL_REGEX.find(trimmed)
    if (match != null) {
        return match.value.trim().trim('"', '\'')
    }

    val cleaned = trimmed
        .removePrefix("ffmpeg ")
        .removePrefix("ffrt ")
        .removePrefix("ffrt2 ")
        .removePrefix("ffrt3 ")
        .removePrefix("auto ")
        .removePrefix("spdif ")
        .trim()
        .substringBefore(' ')
        .trim()
        .trim('"', '\'')

    if (cleaned.startsWith("/") || cleaned.contains(".m3u8", ignoreCase = true) || cleaned.contains(".ts", ignoreCase = true)) {
        val base = serverBaseUrl.trimEnd('/')
        val path = if (cleaned.startsWith("/")) cleaned else "/$cleaned"
        return "$base$path"
    }

    return cleaned
}

private fun extractLinkFromCreateResponse(element: JsonElement, serverBaseUrl: String): String? {
    if (element is JsonPrimitive) {
        val str = element.contentOrNull?.trim()
        if (!str.isNullOrBlank()) {
            val url = extractPlayableUrl(str, serverBaseUrl)
            if (url.isNotBlank()) return url
        }
        return null
    }

    val obj = element as? JsonObject ?: return null

    val jsField = obj["js"]
    if (jsField is JsonPrimitive) {
        val str = jsField.contentOrNull?.trim()
        if (!str.isNullOrBlank()) {
            val url = extractPlayableUrl(str, serverBaseUrl)
            if (url.isNotBlank()) return url
        }
    } else if (jsField is JsonObject) {
        val cmd = jsField.string("cmd") ?: jsField.string("url") ?: jsField.string("stream_url")
        if (!cmd.isNullOrBlank()) {
            val url = extractPlayableUrl(cmd, serverBaseUrl)
            if (url.isNotBlank()) return url
        }
        val dataArray = jsField["data"] as? JsonArray
        val firstItem = dataArray?.firstOrNull() as? JsonObject
        val firstCmd = firstItem?.string("cmd") ?: firstItem?.string("url") ?: firstItem?.string("stream_url")
        if (!firstCmd.isNullOrBlank()) {
            val url = extractPlayableUrl(firstCmd, serverBaseUrl)
            if (url.isNotBlank()) return url
        }
    } else if (jsField is JsonArray) {
        val firstItem = jsField.firstOrNull()
        if (firstItem is JsonPrimitive) {
            val str = firstItem.contentOrNull?.trim()
            if (!str.isNullOrBlank()) {
                val url = extractPlayableUrl(str, serverBaseUrl)
                if (url.isNotBlank()) return url
            }
        } else if (firstItem is JsonObject) {
            val cmd = firstItem.string("cmd") ?: firstItem.string("url") ?: firstItem.string("stream_url")
            if (!cmd.isNullOrBlank()) {
                val url = extractPlayableUrl(cmd, serverBaseUrl)
                if (url.isNotBlank()) return url
            }
        }
    }

    val directCmd = obj.string("cmd") ?: obj.string("url") ?: obj.string("stream_url")
    if (!directCmd.isNullOrBlank()) {
        val url = extractPlayableUrl(directCmd, serverBaseUrl)
        if (url.isNotBlank()) return url
    }

    return null
}

internal suspend fun fetchStalkerChannels(
    settings: LiveTvStalkerSettings,
    maxPagesLimit: Int = 500
): List<LiveTvChannel> {
    val session = stalkerSession(settings.normalized())
    val serverBaseUrl = session.settings.portalServerBaseUrl()

    val genres = runCatching {
        val genresResp = stalkerRequest(session.settings, session.token, "itv", "get_genres", endpointOverride = session.endpoint)
        val jsObj = genresResp.jsObject()
        val dataArray = (jsObj?.get("data") as? JsonArray)?.toList()
            ?: (genresResp.jsElement() as? JsonArray)?.toList()
            ?: emptyList()
        dataArray.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("id") ?: item.string("alias") ?: return@mapNotNull null
            id to (item.string("title") ?: item.string("name") ?: id)
        }.toMap()
    }.getOrDefault(emptyMap())

    // 1. Thử lấy tất cả kênh bằng get_all_channels (chỉ mất 1 request duy nhất)
    val allChannelsResp = runCatching {
        stalkerRequest(session.settings, session.token, "itv", "get_all_channels", endpointOverride = session.endpoint)
    }.getOrNull()

    if (allChannelsResp != null) {
        val js = allChannelsResp.jsElement()
        val data = (js.asJsonObjectOrNull()?.get("data") as? JsonArray)?.toList()
            ?: (js as? JsonArray)?.toList()
            ?: (allChannelsResp.asJsonObjectOrNull()?.get("data") as? JsonArray)?.toList()
            ?: emptyList()

        if (data.isNotEmpty()) {
            val channels = parseChannelsFromStalkerData(data, genres, session, serverBaseUrl)
            if (channels.isNotEmpty()) {
                return channels.distinctBy { it.id }
            }
        }
    }

    // 2. Fallback duyệt phân trang qua get_ordered_list
    val channels = mutableListOf<LiveTvChannel>()
    var page = 1
    var maxPages = maxPagesLimit
    var consecutiveEmptyCount = 0

    while (page <= maxPages) {
        val response = runCatching {
            stalkerRequest(
                settings = session.settings,
                token = session.token,
                type = "itv",
                action = "get_ordered_list",
                extra = mapOf(
                    "p" to page.toString(),
                    "genre" to "0",
                    "force_ch_link_check" to "0"
                ),
                endpointOverride = session.endpoint
            )
        }.getOrNull()

        if (response == null) {
            consecutiveEmptyCount++
            if (consecutiveEmptyCount >= 2) break
            page++
            continue
        }

        val js = response.jsElement()
        val jsObj = js.asJsonObjectOrNull() ?: response.asJsonObjectOrNull()
        val dataArray = (jsObj?.get("data") as? JsonArray)?.toList()
            ?: (js as? JsonArray)?.toList()
            ?: emptyList()

        if (dataArray.isEmpty()) {
            consecutiveEmptyCount++
            if (consecutiveEmptyCount >= 2) break
            page++
            continue
        }
        consecutiveEmptyCount = 0

        val totalItems = jsObj?.string("total_items")?.toIntOrNull()
        val maxPageItems = jsObj?.string("max_page_items")?.toIntOrNull()?.takeIf { it > 0 } ?: 14
        if (totalItems != null && totalItems > 0) {
            val calculatedPages = (totalItems + maxPageItems - 1) / maxPageItems
            maxPages = calculatedPages.coerceIn(1, maxPagesLimit)
        }

        val pageChannels = parseChannelsFromStalkerData(dataArray, genres, session, serverBaseUrl, pageHint = page)
        channels.addAll(pageChannels)

        if (totalItems != null && channels.size >= totalItems) {
            break
        }

        page++
    }

    return channels.distinctBy { it.id }
}

private fun parseChannelsFromStalkerData(
    dataArray: List<JsonElement>,
    genres: Map<String, String>,
    session: StalkerSession,
    serverBaseUrl: String,
    pageHint: Int = 1
): List<LiveTvChannel> {
    val result = mutableListOf<LiveTvChannel>()
    val streamHeaders = stalkerStreamHeaders(session.settings)
    dataArray.forEachIndexed { index, element ->
        val item = element as? JsonObject ?: return@forEachIndexed
        val name = item.string("name") ?: item.string("title") ?: return@forEachIndexed
        val id = item.string("id") ?: item.string("ch_id") ?: "$pageHint:$index"
        val command = item.string("cmd") ?: item.string("mc_cmd") ?: item.string("url") ?: id
        val rawUrl = extractPlayableUrl(command, serverBaseUrl)
        val streamUrl = if (rawUrl.isNotBlank()) rawUrl else command

        val detectedType = when {
            streamUrl.contains(".m3u8", ignoreCase = true) -> "m3u8"
            streamUrl.contains(".mpd", ignoreCase = true) -> "mpd"
            streamUrl.contains(".ts", ignoreCase = true) -> "ts"
            else -> "m3u8"
        }

        result.add(
            LiveTvChannel(
                id = "stalker:$id",
                name = name,
                streamUrl = streamUrl,
                logoUrl = item.string("logo") ?: item.string("logo_url"),
                group = (item.string("tv_genre_id") ?: item.string("genre_id"))?.let(genres::get),
                playlistId = STALKER_PLAYLIST_ID,
                playlistName = "Stalker Portal",
                headers = streamHeaders,
                stalkerCommand = command,
                streamType = detectedType,
            )
        )
    }
    return result
}

internal suspend fun preparePortalChannelForPlayback(channel: LiveTvChannel, settings: LiveTvStalkerSettings): LiveTvChannel {
    val normalized = settings.normalized()
    val serverBaseUrl = normalized.portalServerBaseUrl()
    val streamHeaders = stalkerStreamHeaders(normalized)

    val existingHttpUrl = channel.streamUrl.takeIf {
        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }

    val rawCommand = channel.stalkerCommand?.takeIf(String::isNotBlank)
        ?: channel.id.removePrefix("stalker:").takeIf(String::isNotBlank)
        ?: channel.streamUrl.takeIf(String::isNotBlank)

    val resolvedUrl = if (!rawCommand.isNullOrBlank()) {
        requestCreateLink(normalized, rawCommand, serverBaseUrl, forceRefreshSession = false)
            ?: requestCreateLink(normalized, rawCommand, serverBaseUrl, forceRefreshSession = true)
    } else null

    val channelIdOnly = channel.id.removePrefix("stalker:")
    var finalUrl = resolvedUrl?.takeIf(String::isNotBlank)
        ?: existingHttpUrl
        ?: channel.streamUrl

    if (finalUrl.contains("stream=&") && channelIdOnly.isNotBlank()) {
        finalUrl = finalUrl.replace("stream=&", "stream=$channelIdOnly&")
    }

    val detectedType = when {
        finalUrl.contains(".m3u8", ignoreCase = true) -> "m3u8"
        finalUrl.contains(".mpd", ignoreCase = true) -> "mpd"
        finalUrl.contains(".ts", ignoreCase = true) -> "ts"
        else -> channel.streamType ?: "m3u8"
    }

    return channel.copy(
        streamUrl = finalUrl,
        headers = streamHeaders,
        streamType = detectedType,
    )
}

private suspend fun requestCreateLink(
    settings: LiveTvStalkerSettings,
    command: String,
    serverBaseUrl: String,
    forceRefreshSession: Boolean
): String? {
    val session = runCatching { stalkerSession(settings, forceRefresh = forceRefreshSession) }.getOrNull() ?: return null

    val candidateCmds = linkedSetOf<String>().apply {
        val trimmed = command.trim()
        if (trimmed.isNotBlank()) add(trimmed)
        val cleaned = trimmed
            .removePrefix("ffmpeg ")
            .removePrefix("ffrt ")
            .removePrefix("ffrt2 ")
            .removePrefix("ffrt3 ")
            .removePrefix("auto ")
            .removePrefix("spdif ")
            .trim()
            .trim('"', '\'')
        if (cleaned.isNotBlank()) {
            add(cleaned)
            if (cleaned.startsWith("/media/")) {
                add("auto $cleaned")
                val numOnly = cleaned.substringAfter("/media/").substringBefore('.').trim()
                if (numOnly.isNotEmpty() && numOnly.all { it.isDigit() }) {
                    add(numOnly)
                    add("auto $numOnly")
                    add("/media/$numOnly")
                }
            } else if (cleaned.all { it.isDigit() }) {
                add("auto /media/$cleaned.m3u8")
                add("/media/$cleaned.m3u8")
            }
        }
    }

    for (cmd in candidateCmds) {
        val resolved = runCatching {
            val response = stalkerRequest(
                settings = session.settings,
                token = session.token,
                type = "itv",
                action = "create_link",
                extra = mapOf(
                    "cmd" to cmd,
                    "forced_storage" to "0",
                    "disable_ad" to "0"
                ),
                endpointOverride = session.endpoint
            )
            extractLinkFromCreateResponse(response, serverBaseUrl)
        }.getOrNull()

        if (!resolved.isNullOrBlank()) {
            return resolved
        }
    }

    return null
}

private suspend fun stalkerSession(
    settings: LiveTvStalkerSettings,
    forceRefresh: Boolean = false
): StalkerSession {
    if (!forceRefresh) {
        cachedStalkerSession?.takeIf { it.settings == settings && it.token.isNotBlank() }?.let { return it }
    }

    val normalized = settings.normalized()
    val endpoints = normalized.portalCandidateEndpoints()
    var lastError: Throwable? = null

    for (endpoint in endpoints) {
        try {
            val handshakeResp = stalkerRequest(
                settings = normalized,
                token = null,
                type = "stb",
                action = "handshake",
                endpointOverride = endpoint
            )
            val js = handshakeResp.jsElement()
            val token = js.asJsonObjectOrNull()?.string("token")
                ?: (js as? JsonPrimitive)?.contentOrNull
                ?: handshakeResp.asJsonObjectOrNull()?.string("token")
                ?: ""

            if (token.isBlank()) {
                continue
            }

            // Kích hoạt profile thiết bị STB (MAG254 profile).
            // LƯU Ý: Không gửi auth_second_step=1 vì sẽ khiến server hủy kích hoạt token nếu không có pass hash.
            runCatching {
                stalkerRequest(
                    settings = normalized,
                    token = token,
                    type = "stb",
                    action = "get_profile",
                    extra = mapOf(
                        "hd" to "1",
                        "ver" to "ImageDescription: 0.2.18-r14-pub-250; ImageDate: Fri Jan 15 15:20:44 EET 2016; PORTAL version: 5.1.0; API Version: JS API version: 328; STB API version: 134; Player Engine version: 0x566",
                        "stb_type" to "MAG254",
                        "sn" to "0000000000000"
                    ),
                    endpointOverride = endpoint
                )
            }

            val session = StalkerSession(normalized, token, endpoint)
            cachedStalkerSession = session
            return session
        } catch (e: Exception) {
            lastError = e
        }
    }

    throw lastError ?: IllegalStateException("Stalker Portal did not return an authorization token.")
}

private suspend fun stalkerRequest(
    settings: LiveTvStalkerSettings,
    token: String?,
    type: String,
    action: String,
    extra: Map<String, String> = emptyMap(),
    endpointOverride: String? = null,
): JsonElement {
    val parameters = buildMap {
        put("type", type)
        put("action", action)
        put("JsHttpRequest", "1-xml")
        if (!token.isNullOrBlank()) put("token", token)
        if (settings.username.isNotBlank()) put("login", settings.username)
        if (settings.password.isNotBlank()) put("password", settings.password)
        putAll(extra)
    }
    val endpoint = endpointOverride ?: settings.portalEndpoint()
    val query = parameters.entries.joinToString("&", prefix = if ('?' in endpoint) "&" else "?") { (key, value) ->
        "${key.urlEncode()}=${value.urlEncode()}"
    }
    val response = httpGetTextWithHeaders(endpoint + query, stalkerApiHeaders(settings, token))
    val cleanedJson = response.trim().removePrefix("\uFEFF")
    return portalJson.parseToJsonElement(cleanedJson)
}

/** Headers dành riêng cho các API call tới Stalker middleware PHP */
private fun stalkerApiHeaders(settings: LiveTvStalkerSettings, token: String?): Map<String, String> = buildMap {
    put("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; MAG254; en) AppleWebKit/533.3 MAG200 stbapp ver: 4 rev: 2721 Mobile Safari/533.3")
    put("X-User-Agent", "Model: MAG254; Link: Ethernet")
    put("Referer", settings.portalBaseUrl())
    put("Cookie", "mac=${settings.macAddress}; stb_lang=en; timezone=Europe%2FIstanbul")
    put("Accept", "application/json, text/javascript, */*; q=0.01")
    if (!token.isNullOrBlank()) put("Authorization", "Bearer $token")
}

/** Headers dành riêng cho ExoPlayer phát video stream (không chứa Accept json hay Authorization token để tránh bị từ chối 401/406) */
private fun stalkerStreamHeaders(settings: LiveTvStalkerSettings): Map<String, String> = buildMap {
    put("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; MAG254; en) AppleWebKit/533.3 MAG200 stbapp ver: 4 rev: 2721 Mobile Safari/533.3")
    put("X-User-Agent", "Model: MAG254; Link: Ethernet")
    put("Referer", settings.portalBaseUrl())
    put("Cookie", "mac=${settings.macAddress}; stb_lang=en; timezone=Europe%2FIstanbul")
    put("Accept", "*/*")
}

private fun LiveTvXtreamSettings.normalized() = copy(
    serverUrl = serverUrl.trim().trimEnd('/').substringBefore("/player_api.php").trimEnd('/'),
    username = username.trim(),
    password = password.trim(),
)

private fun LiveTvXtreamSettings.liveStreamUrl(id: String, extension: String) =
    "$serverUrl/live/${username.urlEncode()}/${password.urlEncode()}/${id.urlEncode()}.${extension.urlEncode()}"

private fun LiveTvStalkerSettings.normalized() = copy(
    portalUrl = portalUrl.trim().trimEnd('/'),
    macAddress = formatMacAddress(macAddress),
    username = username.trim(),
    password = password.trim(),
)

private fun LiveTvStalkerSettings.portalCandidateEndpoints(): List<String> {
    val normalized = portalUrl.trim().trimEnd('/')
    return when {
        normalized.contains("portal.php", ignoreCase = true) || normalized.contains("server/load.php", ignoreCase = true) -> {
            listOf(normalized)
        }
        normalized.endsWith("/c", ignoreCase = true) -> {
            val root = normalized.dropLast(2).trimEnd('/')
            listOf(
                "$normalized/portal.php",
                "$root/server/load.php",
                "$root/portal.php",
                "$normalized/server/load.php"
            )
        }
        else -> {
            listOf(
                "$normalized/portal.php",
                "$normalized/server/load.php",
                "$normalized/c/portal.php",
                "$normalized/c/server/load.php"
            )
        }
    }
}

private fun LiveTvStalkerSettings.portalEndpoint(): String =
    portalCandidateEndpoints().firstOrNull() ?: "${portalUrl.trim().trimEnd('/')}/portal.php"

private fun LiveTvStalkerSettings.portalServerBaseUrl(): String {
    val base = portalUrl.trim()
        .substringBefore("/portal.php")
        .substringBefore("/server/load.php")
        .trimEnd('/')
        .removeSuffix("/c")
        .trimEnd('/')
    return base
}

private fun LiveTvStalkerSettings.portalBaseUrl(): String {
    val base = portalServerBaseUrl()
    return "$base/c/"
}

private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
private fun JsonElement.string(key: String) = (this as? JsonObject)?.string(key)
private fun JsonElement.array(key: String) = ((this as? JsonObject)?.get(key) as? JsonArray)?.toList().orEmpty()
private fun JsonElement.arrayOrEmpty() = (this as? JsonArray)?.toList() ?: (this as? JsonObject)?.array("data").orEmpty()
private fun JsonElement.jsElement(): JsonElement = (this as? JsonObject)?.get("js") ?: this
private fun JsonElement.jsObject(): JsonObject? = (this as? JsonObject)?.let { (it["js"] as? JsonObject) ?: it }
private fun JsonElement.asJsonObjectOrNull(): JsonObject? = this as? JsonObject
