package com.nuvio.tv.core.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.features.livetv.LiveTvPlaylistType
import com.nuvio.tv.features.livetv.LiveTvRepository
import com.nuvio.tv.features.livetv.LiveTvStalkerSettings
import com.nuvio.tv.features.livetv.LiveTvUiState
import com.nuvio.tv.features.livetv.LiveTvXtreamSettings
import fi.iki.elonen.NanoHTTPD
import java.nio.charset.StandardCharsets

class LiveTvConfigServer(
    private val context: Context? = null,
    port: Int = 8092,
    private val uiStateProvider: () -> LiveTvUiState = { LiveTvRepository.uiState.value },
    private val onAddUrlPlaylist: (name: String?, url: String, epgUrl: String?) -> Unit = { name, url, epgUrl ->
        LiveTvRepository.addPlaylistUrl(name = name, url = url, epgUrl = epgUrl)
    },
    private val onAddLocalPlaylist: (name: String?, fileName: String?, content: String) -> Unit = { name, fileName, content ->
        LiveTvRepository.addLocalPlaylist(name = name, fileName = fileName, content = content)
    },
    private val onRemovePlaylist: (String) -> Unit = { LiveTvRepository.removePlaylist(it) },
    private val onSetPlaylistEnabled: (String, Boolean) -> Unit = { id, enabled ->
        LiveTvRepository.setPlaylistEnabled(id, enabled)
    },
    private val onSaveXtream: (LiveTvXtreamSettings) -> Unit = { LiveTvRepository.saveXtreamSettings(it) },
    private val onSaveStalker: (LiveTvStalkerSettings) -> Unit = { LiveTvRepository.saveStalkerSettings(it) },
    private val onSetNavigationEnabled: (Boolean) -> Unit = { LiveTvRepository.setNavigationEnabled(it) },
    private val onSetCustomEpgUrl: (String) -> Unit = { LiveTvRepository.setCustomEpgUrl(it) }
) : NanoHTTPD(port) {

    constructor(
        context: Context? = null,
        port: Int = 8092,
        uiStateProvider: () -> LiveTvUiState = { LiveTvRepository.uiState.value },
        onAddUrlPlaylist: (name: String?, url: String) -> Unit,
        onAddLocalPlaylist: (name: String?, fileName: String?, content: String) -> Unit = { name, fileName, content ->
            LiveTvRepository.addLocalPlaylist(name = name, fileName = fileName, content = content)
        },
        onRemovePlaylist: (String) -> Unit = { LiveTvRepository.removePlaylist(it) },
        onSetPlaylistEnabled: (String, Boolean) -> Unit = { id, enabled ->
            LiveTvRepository.setPlaylistEnabled(id, enabled)
        },
        onSaveXtream: (LiveTvXtreamSettings) -> Unit = { LiveTvRepository.saveXtreamSettings(it) },
        onSaveStalker: (LiveTvStalkerSettings) -> Unit = { LiveTvRepository.saveStalkerSettings(it) },
        onSetNavigationEnabled: (Boolean) -> Unit = { LiveTvRepository.setNavigationEnabled(it) }
    ) : this(
        context = context,
        port = port,
        uiStateProvider = uiStateProvider,
        onAddUrlPlaylist = { name, url, _ -> onAddUrlPlaylist(name, url) },
        onAddLocalPlaylist = onAddLocalPlaylist,
        onRemovePlaylist = onRemovePlaylist,
        onSetPlaylistEnabled = onSetPlaylistEnabled,
        onSaveXtream = onSaveXtream,
        onSaveStalker = onSaveStalker,
        onSetNavigationEnabled = onSetNavigationEnabled
    )

    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Any?>>() {}.type

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return when {
            method == Method.GET && uri == "/" -> serveWebPage()
            method == Method.GET && uri == "/api/livetv/config" -> serveConfig()
            method == Method.POST && uri == "/api/livetv/playlist/add" -> handleAddPlaylist(session)
            method == Method.POST && uri == "/api/livetv/playlist/upload" -> handleUploadPlaylist(session)
            method == Method.POST && uri == "/api/livetv/playlist/delete" -> handleDeletePlaylist(session)
            method == Method.POST && uri == "/api/livetv/playlist/toggle" -> handleTogglePlaylist(session)
            method == Method.POST && uri == "/api/livetv/xtream" -> handleSaveXtream(session)
            method == Method.POST && uri == "/api/livetv/stalker" -> handleSaveStalker(session)
            method == Method.POST && uri == "/api/livetv/navigation" -> handleToggleNavigation(session)
            method == Method.POST && uri == "/api/livetv/epg" -> handleSaveEpg(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveWebPage(): Response {
        val html = context?.let { LiveTvWebPage.getHtml(it) }.orEmpty()
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
    }

    private fun serveConfig(): Response {
        val uiState = uiStateProvider()
        val config = mapOf(
            "playlists" to uiState.playlists.map { p ->
                mapOf(
                    "id" to p.id,
                    "name" to p.name,
                    "url" to p.source,
                    "epgUrl" to p.epgUrl,
                    "isLocal" to (p.type == LiveTvPlaylistType.LocalFile),
                    "isEnabled" to p.isEnabled
                )
            },
            "customEpgUrl" to uiState.settings.customEpgUrl,
            "xtream" to mapOf(
                "serverUrl" to uiState.xtreamSettings.serverUrl,
                "username" to uiState.xtreamSettings.username,
                "password" to uiState.xtreamSettings.password,
                "isEnabled" to uiState.xtreamSettings.isEnabled
            ),
            "stalker" to mapOf(
                "portalUrl" to uiState.stalkerSettings.portalUrl,
                "macAddress" to uiState.stalkerSettings.macAddress,
                "isEnabled" to uiState.stalkerSettings.isEnabled
            ),
            "isNavigationEnabled" to uiState.isNavigationEnabled
        )
        return jsonResponse(Response.Status.OK, gson.toJson(config))
    }

    private fun handleAddPlaylist(session: IHTTPSession): Response {
        val body = parseBody(session)
        val name = body["name"]?.toString()?.trim().orEmpty()
        val url = body["url"]?.toString()?.trim().orEmpty()
        val epgUrl = body["epgUrl"]?.toString()?.trim().orEmpty()

        if (url.isBlank()) {
            return jsonResponse(Response.Status.BAD_REQUEST, """{"error":"URL cannot be empty"}""")
        }

        onAddUrlPlaylist(name.ifBlank { null }, url, epgUrl.ifBlank { null })
        return jsonResponse(Response.Status.OK, """{"status":"ok"}""")
    }

    private fun handleUploadPlaylist(session: IHTTPSession): Response {
        val body = parseBody(session)
        val name = body["name"]?.toString()?.trim().orEmpty()
        val fileName = body["fileName"]?.toString()?.trim().orEmpty().ifBlank { "phone_upload.m3u" }
        val content = body["content"]?.toString()?.trim().orEmpty()

        if (content.isBlank()) {
            return jsonResponse(Response.Status.BAD_REQUEST, """{"error":"Content cannot be empty"}""")
        }

        onAddLocalPlaylist(name.ifBlank { null }, fileName, content)
        return jsonResponse(Response.Status.OK, """{"status":"ok"}""")
    }

    private fun handleDeletePlaylist(session: IHTTPSession): Response {
        val body = parseBody(session)
        val id = body["id"]?.toString()?.trim().orEmpty()

        if (id.isNotBlank()) {
            onRemovePlaylist(id)
        }

        return jsonResponse(Response.Status.OK, """{"status":"ok"}""")
    }

    private fun handleTogglePlaylist(session: IHTTPSession): Response {
        val body = parseBody(session)
        val id = body["id"]?.toString()?.trim().orEmpty()
        val isEnabled = (body["isEnabled"] as? Boolean) ?: true

        if (id.isNotBlank()) {
            onSetPlaylistEnabled(id, isEnabled)
        }

        return jsonResponse(Response.Status.OK, """{"status":"ok"}""")
    }

    private fun handleSaveXtream(session: IHTTPSession): Response {
        val body = parseBody(session)
        val serverUrl = body["serverUrl"]?.toString()?.trim().orEmpty()
        val username = body["username"]?.toString()?.trim().orEmpty()
        val password = body["password"]?.toString()?.trim().orEmpty()
        val isEnabled = (body["isEnabled"] as? Boolean) ?: serverUrl.isNotBlank()

        val settings = LiveTvXtreamSettings(
            serverUrl = serverUrl,
            username = username,
            password = password,
            isEnabled = isEnabled
        )
        onSaveXtream(settings)

        return jsonResponse(Response.Status.OK, """{"status":"ok"}""")
    }

    private fun handleSaveStalker(session: IHTTPSession): Response {
        val body = parseBody(session)
        val portalUrl = body["portalUrl"]?.toString()?.trim().orEmpty()
        val macAddress = body["macAddress"]?.toString()?.trim().orEmpty()
        val isEnabled = (body["isEnabled"] as? Boolean) ?: portalUrl.isNotBlank()

        val settings = LiveTvStalkerSettings(
            portalUrl = portalUrl,
            macAddress = macAddress,
            isEnabled = isEnabled
        )
        onSaveStalker(settings)

        return jsonResponse(Response.Status.OK, """{"status":"ok"}""")
    }

    private fun handleToggleNavigation(session: IHTTPSession): Response {
        val body = parseBody(session)
        val isEnabled = (body["isEnabled"] as? Boolean) ?: true
        onSetNavigationEnabled(isEnabled)
        return jsonResponse(Response.Status.OK, """{"status":"ok"}""")
    }

    private fun handleSaveEpg(session: IHTTPSession): Response {
        val body = parseBody(session)
        val epgUrl = body["epgUrl"]?.toString()?.trim().orEmpty()
        onSetCustomEpgUrl(epgUrl)
        return jsonResponse(Response.Status.OK, """{"status":"ok"}""")
    }

    private fun jsonResponse(status: Response.Status, body: String): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body)

    private fun parseBody(session: IHTTPSession): Map<String, Any?> {
        return try {
            val json = readUtf8Body(session)
            if (json.isNotBlank()) {
                gson.fromJson(json, mapType) ?: emptyMap()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            Log.e("LiveTvConfigServer", "Failed to parse body", e)
            emptyMap()
        }
    }

    private fun readUtf8Body(session: IHTTPSession): String {
        val length = session.headers["content-length"]?.trim()?.toIntOrNull() ?: return ""
        if (length <= 0 || length > MAX_BODY_BYTES) return ""
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = session.inputStream.read(buffer, offset, length - offset)
            if (read <= 0) break
            offset += read
        }
        return String(buffer, 0, offset, StandardCharsets.UTF_8)
    }

    companion object {
        private const val SOCKET_READ_TIMEOUT = 10000
        private const val MAX_BODY_BYTES = 16 * 1024 * 1024

        fun startOnAvailablePort(
            context: Context,
            startPort: Int = 8092,
            maxAttempts: Int = 10
        ): LiveTvConfigServer? {
            for (port in startPort until startPort + maxAttempts) {
                try {
                    val server = LiveTvConfigServer(context, port)
                    server.start(SOCKET_READ_TIMEOUT, false)
                    return server
                } catch (e: Exception) {
                    // Port occupied, try next
                }
            }
            return null
        }
    }
}
