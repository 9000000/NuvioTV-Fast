package com.nuvio.tv.features.livetv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder

internal const val XTREAM_PLAYLIST_ID = "provider:xtream"

/**
 * Service dedicated to interacting with Xtream Codes IPTV API.
 * Handles category fetching, live stream discovery, and stream URL resolution.
 */
internal object XtreamPortalService {
    private val portalJson = Json { ignoreUnknownKeys = true; isLenient = true }
    private val playlistRequestHeaders = mapOf(
        "Accept" to "application/json, text/plain, */*",
        "User-Agent" to "Nuvio/1.0"
    )
    private val streamRequestHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0",
        "Accept" to "*/*"
    )

    /**
     * Fetch all live channels from the Xtream Codes server.
     */
    suspend fun fetchChannels(settings: LiveTvXtreamSettings): List<LiveTvChannel> {
        val normalized = settings.normalized()
        val categories = runCatching {
            xtreamRequest(normalized, "get_live_categories").arrayOrEmpty().mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val id = item.string("category_id") ?: item.string("id") ?: return@mapNotNull null
                id to (item.string("category_name") ?: item.string("name") ?: id)
            }.toMap()
        }.getOrDefault(emptyMap())

        return xtreamRequest(normalized, "get_live_streams").arrayOrEmpty().mapIndexedNotNull { _, element ->
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

    private fun LiveTvXtreamSettings.normalized() = copy(
        serverUrl = serverUrl.trim().trimEnd('/').substringBefore("/player_api.php").trimEnd('/'),
        username = username.trim(),
        password = password.trim()
    )

    private fun LiveTvXtreamSettings.liveStreamUrl(id: String, extension: String) =
        "$serverUrl/live/${username.urlEncode()}/${password.urlEncode()}/$id.$extension"

    private fun String.urlEncode(): String = try {
        URLEncoder.encode(this, "UTF-8")
    } catch (e: Exception) {
        this
    }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
    private fun JsonElement.string(key: String) = (this as? JsonObject)?.string(key)
    private fun JsonElement.array(key: String) = ((this as? JsonObject)?.get(key) as? JsonArray)?.toList().orEmpty()
    private fun JsonElement.arrayOrEmpty() = (this as? JsonArray)?.toList() ?: (this as? JsonObject)?.array("data").orEmpty()
}
