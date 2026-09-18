package com.nuvio.tv.core.server

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nuvio.tv.features.livetv.LiveTvPlaylistType
import com.nuvio.tv.features.livetv.LiveTvRepository
import com.nuvio.tv.features.livetv.LiveTvStalkerSettings
import com.nuvio.tv.features.livetv.LiveTvXtreamSettings
import fi.iki.elonen.NanoHTTPD

class LiveTvConfigServer(
    private val context: Context,
    port: Int = 8092
) : NanoHTTPD(port) {

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
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveWebPage(): Response {
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", LiveTvWebPage.getHtml(context))
    }

    private fun serveConfig(): Response {
        val uiState = LiveTvRepository.uiState.value
        val config = mapOf(
            "playlists" to uiState.playlists.map { p ->
                mapOf(
                    "id" to p.id,
                    "name" to p.name,
                    "url" to p.source,
                    "isLocal" to (p.type == LiveTvPlaylistType.LocalFile),
                    "isEnabled" to p.isEnabled
                )
            },
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
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", gson.toJson(config))
    }

    private fun handleAddPlaylist(session: IHTTPSession): Response {
        val body = parseBody(session)
        val name = body["name"]?.toString()?.trim().orEmpty()
        val url = body["url"]?.toString()?.trim().orEmpty()

        if (url.isBlank()) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"URL cannot be empty"}""")
        }

        LiveTvRepository.addPlaylistUrl(name = name.ifBlank { null }, url = url)
        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
    }

    private fun handleUploadPlaylist(session: IHTTPSession): Response {
        val body = parseBody(session)
        val name = body["name"]?.toString()?.trim().orEmpty()
        val fileName = body["fileName"]?.toString()?.trim().orEmpty().ifBlank { "phone_upload.m3u" }
        val content = body["content"]?.toString()?.trim().orEmpty()

        if (content.isBlank()) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"error":"Content cannot be empty"}""")
        }

        LiveTvRepository.addLocalPlaylist(name = name.ifBlank { null }, fileName = fileName, content = content)
        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
    }

    private fun handleDeletePlaylist(session: IHTTPSession): Response {
        val body = parseBody(session)
        val id = body["id"]?.toString()?.trim().orEmpty()

        if (id.isNotBlank()) {
            LiveTvRepository.removePlaylist(id)
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
    }

    private fun handleTogglePlaylist(session: IHTTPSession): Response {
        val body = parseBody(session)
        val id = body["id"]?.toString()?.trim().orEmpty()
        val isEnabled = (body["isEnabled"] as? Boolean) ?: true

        if (id.isNotBlank()) {
            LiveTvRepository.setPlaylistEnabled(id, isEnabled)
        }

        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
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
        LiveTvRepository.saveXtreamSettings(settings)

        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
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
        LiveTvRepository.saveStalkerSettings(settings)

        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
    }

    private fun handleToggleNavigation(session: IHTTPSession): Response {
        val body = parseBody(session)
        val isEnabled = (body["isEnabled"] as? Boolean) ?: true
        LiveTvRepository.setNavigationEnabled(isEnabled)
        return newFixedLengthResponse(Response.Status.OK, "application/json", """{"status":"ok"}""")
    }

    private fun parseBody(session: IHTTPSession): Map<String, Any?> {
        val bodyMap = HashMap<String, String>()
        try {
            session.parseBody(bodyMap)
            val json = bodyMap["postData"] ?: ""
            if (json.isNotBlank()) {
                return gson.fromJson(json, mapType) ?: emptyMap()
            }
        } catch (e: Exception) {
            Log.e("LiveTvConfigServer", "Failed to parse body", e)
        }
        return emptyMap()
    }

    companion object {
        private const val SOCKET_READ_TIMEOUT = 10000

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
