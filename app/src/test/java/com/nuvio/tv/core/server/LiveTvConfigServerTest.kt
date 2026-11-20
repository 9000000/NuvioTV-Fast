package com.nuvio.tv.core.server

import com.google.gson.Gson
import com.nuvio.tv.features.livetv.LiveTvPlaylist
import com.nuvio.tv.features.livetv.LiveTvPlaylistType
import com.nuvio.tv.features.livetv.LiveTvUiState
import com.nuvio.tv.features.livetv.decodePlaylists
import com.nuvio.tv.features.livetv.encodePlaylists
import com.nuvio.tv.features.livetv.playlistNameFromUrl
import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

class LiveTvConfigServerTest {

    @Test
    fun `add playlist preserves vietnamese utf8 name even when content-type omits charset`() {
        var addedName: String? = null
        var addedUrl: String? = null
        val server = LiveTvConfigServer(
            onAddUrlPlaylist = { name, url ->
                addedName = name
                addedUrl = url
            }
        )
        val expectedName = "Kênh Thể Thao Việt Nam · Đỉnh Cao 4K"
        val body = Gson().toJson(
            mapOf(
                "name" to expectedName,
                "url" to "https://example.com/kenh-viet.m3u"
            )
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/api/livetv/playlist/add",
                body = body,
                contentType = "application/json"
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals("application/json; charset=utf-8", response.mimeType)
        assertEquals(expectedName, addedName)
        assertEquals("https://example.com/kenh-viet.m3u", addedUrl)
    }

    @Test
    fun `add playlist parses and passes epgUrl correctly`() {
        var addedName: String? = null
        var addedUrl: String? = null
        var addedEpgUrl: String? = null
        val server = LiveTvConfigServer(
            onAddUrlPlaylist = { name, url, epgUrl ->
                addedName = name
                addedUrl = url
                addedEpgUrl = epgUrl
            }
        )
        val body = Gson().toJson(
            mapOf(
                "name" to "Kênh VTV",
                "url" to "https://example.com/vtv.m3u",
                "epgUrl" to "https://example.com/epg.xml.gz"
            )
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/api/livetv/playlist/add",
                body = body
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals("Kênh VTV", addedName)
        assertEquals("https://example.com/vtv.m3u", addedUrl)
        assertEquals("https://example.com/epg.xml.gz", addedEpgUrl)
    }

    @Test
    fun `save epg endpoint sets custom epg url`() {
        var savedEpgUrl: String? = null
        val server = LiveTvConfigServer(
            onSetCustomEpgUrl = { savedEpgUrl = it }
        )
        val body = Gson().toJson(
            mapOf(
                "epgUrl" to "https://example.com/global_epg.xml"
            )
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/api/livetv/epg",
                body = body
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals("https://example.com/global_epg.xml", savedEpgUrl)
    }

    @Test
    fun `upload playlist preserves vietnamese utf8 name and m3u content`() {
        var addedName: String? = null
        var addedFileName: String? = null
        var addedContent: String? = null
        val server = LiveTvConfigServer(
            onAddLocalPlaylist = { name, fileName, content ->
                addedName = name
                addedFileName = fileName
                addedContent = content
            }
        )
        val expectedName = "Truyền Hình Miền Bắc 2026"
        val expectedContent = "#EXTM3U\n#EXTINF:-1,VTV1 Thời Sự Tiếng Việt\nhttps://example.com/vtv1.m3u8"
        val body = Gson().toJson(
            mapOf(
                "name" to expectedName,
                "fileName" to "Truyền Hình Miền Bắc.m3u",
                "content" to expectedContent
            )
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.POST,
                uri = "/api/livetv/playlist/upload",
                body = body
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(expectedName, addedName)
        assertEquals("Truyền Hình Miền Bắc.m3u", addedFileName)
        assertEquals(expectedContent, addedContent)
    }

    @Test
    fun `serve config outputs utf8 json with vietnamese playlist names`() {
        val server = LiveTvConfigServer(
            uiStateProvider = {
                LiveTvUiState(
                    playlists = listOf(
                        LiveTvPlaylist(
                            id = "p1",
                            name = "Kênh Giải Trí Tổng Hợp",
                            type = LiveTvPlaylistType.Url,
                            source = "https://example.com/list.m3u"
                        )
                    )
                )
            }
        )

        val response = server.serve(
            FakeSession(
                method = NanoHTTPD.Method.GET,
                uri = "/api/livetv/config"
            )
        )

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals("application/json; charset=utf-8", response.mimeType)
        val jsonText = response.data.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        assertTrue(jsonText.contains("Kênh Giải Trí Tổng Hợp"))
    }

    @Test
    fun `playlist encoding uses xml-safe utf8 json and remains backward compatible`() {
        val playlists = listOf(
            LiveTvPlaylist(
                id = "pl_1",
                name = "Kênh Tiếng Việt · Thể thao & Giải trí",
                type = LiveTvPlaylistType.Url,
                source = "https://example.com/list.m3u",
                isEnabled = true,
                epgUrl = "https://example.com/epg.xml"
            )
        )
        val encoded = encodePlaylists(playlists)
        assertFalse(encoded.contains("\u001E"))
        assertFalse(encoded.contains("\u001F"))
        assertEquals(playlists, decodePlaylists(encoded))

        val legacyBlob = "pl_legacy\u001FKênh Cũ Tiếng Việt\u001FUrl\u001Fhttps://example.com/old.m3u\u001Ftrue\u001F"
        val decodedLegacy = decodePlaylists(legacyBlob)
        assertEquals(1, decodedLegacy.size)
        assertEquals("Kênh Cũ Tiếng Việt", decodedLegacy.first().name)
    }

    @Test
    fun `playlistNameFromUrl decodes utf8 percent-encoded filenames`() {
        val decoded = playlistNameFromUrl("https://example.com/K%C3%AAnh%20Vi%E1%BB%87t%20Nam.m3u?token=1")
        assertEquals("Kênh Việt Nam", decoded)
    }

    private class FakeSession(
        private val method: NanoHTTPD.Method,
        private val uri: String,
        body: String = "",
        private val contentType: String = "application/json; charset=utf-8"
    ) : NanoHTTPD.IHTTPSession {
        private val bytes = body.toByteArray(StandardCharsets.UTF_8)

        override fun execute() = Unit
        override fun getCookies(): NanoHTTPD.CookieHandler? = null
        override fun getHeaders(): Map<String, String> = mapOf(
            "content-length" to bytes.size.toString(),
            "content-type" to contentType
        )
        override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)
        override fun getMethod(): NanoHTTPD.Method = method
        override fun getParms(): Map<String, String> = emptyMap()
        override fun getParameters(): Map<String, List<String>> = emptyMap()
        override fun getQueryParameterString(): String? = null
        override fun getUri(): String = uri
        override fun parseBody(files: MutableMap<String, String>) {
            error("parseBody should not be used because NanoHTTPD defaults to US-ASCII")
        }
        override fun getRemoteIpAddress(): String = "127.0.0.1"
        override fun getRemoteHostName(): String = "localhost"
    }
}
