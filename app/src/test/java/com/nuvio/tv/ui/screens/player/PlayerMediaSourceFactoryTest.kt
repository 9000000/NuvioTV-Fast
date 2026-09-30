package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PlayerMediaSourceFactoryTest {

    @Test
    fun `test Pengu segment redirect with createHttpClient`() {
        val segmentUrl = "https://pengu.uk/direct/external/resource/eyJ1cmwiOiJodHRwczovL3Zpdmlka2l0ZS50b3AvdmQvY1ZVMlpUUlFVSHBYVDB0NE1sQkpXbnBFY0RoNVp6cFdaME56Ym1VMFEwZEJSa051ZURCV1ZWSndSSHBSL3NlZy0xNDI2LXMxMDgwcC12MS1hMS5tNHMiLCJoZWFkZXJzIjp7IlVzZXItQWdlbnQiOiJNb3ppbGxhLzUuMCAoV2luZG93cyBOVCAxMC4wOyBXaW42NDsgeDY0KSBBcHBsZVdlYktpdC81MzcuMzYgKEtIVE1MLCBsaWtlIEdlY2tvKSBDaHJvbWUvMTIyLjAuMC4wIFNhZmFyaS81MzcuMzYiLCJSZWZlcmVyIjoiaHR0cHM6Ly92aWRmYXN0LnZjLyIsIlgtUmVxdWVzdGVkLVdpdGgiOiJYTUxIdHRwUmVxdWVzdCIsIlgtQ1NSRi1Ub2tlbiI6IlhtZ3B6dVZobk5yMnp3QTFwNHdtRzRrVlN3Ynd2d2l5In19/seg-1426-s1080p-v1-a1.m4s?psig=1790807488.ef1kUpaNJgFL-npYXqXLvuzH0Ox9_dVTWotqrEwpKt8:IomneGezu7llwK71JSVjnMJR0w-plCznxoKEtz-Bq2g"
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Referer" to "https://vidfast.vc/",
            "X-Requested-With" to "XMLHttpRequest",
            "X-CSRF-Token" to "XmgpzuVhnNr2zwA1p4wmG4kVSwbwvwiy"
        )
        val client = PlayerPlaybackNetworking.createHttpClient(headers)
        val reqBuilder = okhttp3.Request.Builder().url(segmentUrl)
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        val req = reqBuilder.build()
        try {
            val response = client.newCall(req).execute()
            println("PENGU SEGMENT RESPONSE: code=" + response.code + " url=" + response.request.url + " msg=" + response.message)
            val bodySample = response.body.byteStream().use {
                val b = ByteArray(16)
                val read = it.read(b)
                if (read > 0) b.take(read).joinToString { byte -> "%02x".format(byte) } else "empty"
            }
            println("PENGU SEGMENT BODY: " + bodySample)
        } catch (e: Exception) {
            println("PENGU EXCEPTION: " + e)
            e.printStackTrace()
        }
    }

    @Test
    fun `media segment 404 with an alternative prefers another HLS track`() {
        assertTrue(
            shouldPreferAlternativeHlsTrack(
                responseCode = 404,
                dataType = C.DATA_TYPE_MEDIA,
                alternativeTrackAvailable = true
            )
        )
    }

    @Test
    fun `manifest errors and missing alternatives do not trigger rendition fallback`() {
        assertFalse(
            shouldPreferAlternativeHlsTrack(
                responseCode = 404,
                dataType = C.DATA_TYPE_MANIFEST,
                alternativeTrackAvailable = true
            )
        )
        assertFalse(
            shouldPreferAlternativeHlsTrack(
                responseCode = 404,
                dataType = C.DATA_TYPE_MEDIA,
                alternativeTrackAvailable = false
            )
        )
    }

    @Test
    fun `inferMimeType prefers response content type for manifest urls without extension`() {
        val mimeType = PlayerMediaSourceFactory.inferMimeType(
            url = "https://example.com/playback?id=42",
            filename = null,
            responseHeaders = mapOf("Content-Type" to "application/vnd.apple.mpegurl; charset=UTF-8")
        )

        assertEquals(MimeTypes.APPLICATION_M3U8, mimeType)
    }

    @Test
    fun `inferMimeType uses content disposition filename when content type is missing`() {
        val mimeType = PlayerMediaSourceFactory.inferMimeType(
            url = "https://example.com/download?id=42",
            filename = null,
            responseHeaders = mapOf("Content-Disposition" to "attachment; filename=manifest.mpd")
        )

        assertEquals(MimeTypes.APPLICATION_MPD, mimeType)
    }

    @Test
    fun `inferMimeType ignores generic playlist path without manifest evidence`() {
        val mimeType = PlayerMediaSourceFactory.inferMimeType(
            url = "https://example.com/api/playlist/stream",
            filename = null
        )

        assertNull(mimeType)
    }

    @Test
    fun `inferMimeType recognizes explicit format query values`() {
        val mimeType = PlayerMediaSourceFactory.inferMimeType(
            url = "https://example.com/playback?format=m3u8",
            filename = null
        )

        assertEquals(MimeTypes.APPLICATION_M3U8, mimeType)
    }

    @Test
    fun `normalizeMimeType recognizes redirected matroska file responses`() {
        val mimeType = PlayerMediaSourceFactory.normalizeMimeType("video/x-matroska")

        assertEquals(MimeTypes.VIDEO_MATROSKA, mimeType)
    }

    @Test
    fun `inferMimeType uses filename star content disposition for octet stream responses`() {
        val mimeType = PlayerMediaSourceFactory.inferMimeType(
            url = "https://example.com/extract?id=42",
            filename = null,
            responseHeaders = mapOf(
                "Content-Type" to "application/octet-stream",
                "Content-Disposition" to "attachment; filename*=UTF-8''episode-04.mkv"
            )
        )

        assertEquals(MimeTypes.VIDEO_MATROSKA, mimeType)
    }

    @Test
    fun `inferMimeType prefers URL extension for adaptive formats even if headers specify different type`() {
        val mimeType = PlayerMediaSourceFactory.inferMimeType(
            url = "https://example.com/stream.m3u8",
            filename = null,
            responseHeaders = mapOf("Content-Type" to "video/mp4")
        )

        assertEquals(MimeTypes.APPLICATION_M3U8, mimeType)
    }

    @Test
    fun `inferMimeType prefers filename extension for adaptive formats even if headers specify different type`() {
        val mimeType = PlayerMediaSourceFactory.inferMimeType(
            url = "https://example.com/download?id=42",
            filename = "movie.mpd",
            responseHeaders = mapOf("Content-Type" to "video/mp4")
        )

        assertEquals(MimeTypes.APPLICATION_MPD, mimeType)
    }

    @Test
    fun `inferMimeType recognizes playlist endpoints with numeric or token ids as HLS`() {
        val urls = listOf(
            "https://example.com/playlist/759755?token=mock_token_123&expires=1788170323&h=1&lang=it",
            "https://example.com/playlist/123456",
            "https://example.com/playlist/a1b2c3d4e5f6?h=1",
            "https://example.com/hls/759755",
            "https://example.com/manifest/abc123456",
            "https://example.com/master/stream99",
            "https://example.com/live/stream.m3u",
            "https://example.com/playback?protocol=hls"
        )

        for (url in urls) {
            val mimeType = PlayerMediaSourceFactory.inferMimeType(
                url = url,
                filename = null
            )
            assertEquals("Expected HLS mimeType for $url", MimeTypes.APPLICATION_M3U8, mimeType)
        }
    }

    @Test
    fun `normalizePlaybackRequest converts URL userinfo to authorization header`() {
        val userInfo = "test-user:test-pass"
        val request = PlayerMediaSourceFactory.normalizePlaybackRequest(
            url = "https://" + userInfo + "@webdav.example.org/movies/title.mkv?download=1",
            headers = mapOf("Range" to "bytes=0-1")
        )

        assertEquals("https://webdav.example.org/movies/title.mkv?download=1", request.url)
        assertEquals(userInfo.basicAuthHeader(), request.headers["Authorization"])
        assertFalse(request.headers.containsKey("Range"))
    }

    @Test
    fun `normalizePlaybackRequest strips URL userinfo without replacing explicit authorization`() {
        val explicitAuth = "explicit:value".basicAuthHeader()
        val request = PlayerMediaSourceFactory.normalizePlaybackRequest(
            url = "https://" + "test-user:test-pass" + "@webdav.example.org/movies/title.mkv",
            headers = mapOf("Authorization" to explicitAuth)
        )

        assertEquals("https://webdav.example.org/movies/title.mkv", request.url)
        assertEquals(explicitAuth, request.headers["Authorization"])
    }

    @Test
    fun `normalizePlaybackRequest preserves encoded path query and fragment when stripping userinfo`() {
        val userInfo = "user:p@ss"
        val request = PlayerMediaSourceFactory.normalizePlaybackRequest(
            url = "https://" + userInfo.replace("@", "%40") + "@webdav.example.org/files/Show%2FSeason%201/Episode%2001.mkv?name=a%2Fb#frag%2Fment",
            headers = emptyMap()
        )

        assertEquals(
            "https://webdav.example.org/files/Show%2FSeason%201/Episode%2001.mkv?name=a%2Fb#frag%2Fment",
            request.url
        )
        assertEquals(userInfo.basicAuthHeader(), request.headers["Authorization"])
    }

    @Test
    fun `normalizePlaybackRequest strips userinfo containing a literal at sign`() {
        val userInfo = "user:p@ss"
        val request = PlayerMediaSourceFactory.normalizePlaybackRequest(
            url = "https://" + userInfo + "@webdav.example.org/files/title.mkv",
            headers = emptyMap()
        )

        assertEquals("https://webdav.example.org/files/title.mkv", request.url)
        assertEquals(userInfo.basicAuthHeader(), request.headers["Authorization"])
    }

    @Test
    fun `isLoopbackUrl returns true for engine and local proxy loopback streams`() {
        assertTrue(PlayerMediaSourceFactory.isLoopbackUrl("http://127.0.0.1:51234/stream/movie.mkv"))
        assertTrue(PlayerMediaSourceFactory.isLoopbackUrl("http://localhost:51234/stream/movie.mkv"))
        assertTrue(PlayerMediaSourceFactory.isLoopbackUrl("http://[::1]:51234/stream/movie.mkv"))
        assertTrue(PlayerMediaSourceFactory.isLoopbackUrl("http://127.0.0.1:8080/stream/video.mkv?token=abc"))
    }

    @Test
    fun `isLoopbackUrl returns false for external and non-loopback urls`() {
        assertFalse(PlayerMediaSourceFactory.isLoopbackUrl("https://debrid.example.com/stream/movie.mkv"))
        assertFalse(PlayerMediaSourceFactory.isLoopbackUrl("http://192.168.1.100:8091/stream?link=123"))
        assertFalse(PlayerMediaSourceFactory.isLoopbackUrl(""))
    }

    @Test
    fun `isPresignedOrR2Url detects S3 and Cloudflare R2 URLs correctly`() {
        assertTrue(PlayerMediaSourceFactory.isPresignedOrR2Url("https://fb507c8169932d200a2746bc267fdd8b.r2.cloudflarestorage.com/hub/123"))
        assertTrue(PlayerMediaSourceFactory.isPresignedOrR2Url("https://pub-123456.r2.dev/video.mp4"))
        assertTrue(PlayerMediaSourceFactory.isPresignedOrR2Url("https://s3.amazonaws.com/bucket/file?X-Amz-Signature=abcdef"))
        assertTrue(PlayerMediaSourceFactory.isPresignedOrR2Url("https://storage.googleapis.com/b/o?X-Amz-SignedHeaders=host"))
        assertTrue(PlayerMediaSourceFactory.isPresignedOrR2Url("https://myhost.com/download?X-Amz-Algorithm=AWS4-HMAC-SHA256"))

        assertFalse(PlayerMediaSourceFactory.isPresignedOrR2Url("https://example.com/movie.mkv"))
        assertFalse(PlayerMediaSourceFactory.isPresignedOrR2Url("http://127.0.0.1:8080/stream"))
        assertFalse(PlayerMediaSourceFactory.isPresignedOrR2Url(null))
        assertFalse(PlayerMediaSourceFactory.isPresignedOrR2Url(""))
    }

    @Test
    fun `sanitizeHeadersForUrl strips Authorization only for presigned or R2 URLs`() {
        val r2Url = "https://bucket.r2.cloudflarestorage.com/hub/video?X-Amz-Signature=sig"
        val normalUrl = "https://example.com/movie.mkv"
        val inputHeaders = mapOf(
            "Authorization" to "Bearer secret_token",
            "User-Agent" to "NuvioTV/1.0",
            "Referer" to "https://vidfast.vc/"
        )

        val r2Headers = PlayerMediaSourceFactory.sanitizeHeadersForUrl(r2Url, inputHeaders)
        assertFalse("Authorization should be stripped from R2/S3 presigned URLs", r2Headers.containsKey("Authorization"))
        assertEquals("NuvioTV/1.0", r2Headers["User-Agent"])
        assertEquals("https://vidfast.vc/", r2Headers["Referer"])

        val normalHeaders = PlayerMediaSourceFactory.sanitizeHeadersForUrl(normalUrl, inputHeaders)
        assertEquals("Bearer secret_token", normalHeaders["Authorization"])
        assertEquals("NuvioTV/1.0", normalHeaders["User-Agent"])
        assertEquals("https://vidfast.vc/", normalHeaders["Referer"])
    }

    @Test
    fun `inferMimeType extracts extension from response-content-disposition and filename query params`() {
        val r2MkvUrl = "https://fb507c8169932d200a2746bc267fdd8b.r2.cloudflarestorage.com/hub/a7f8b9" +
            "?response-content-disposition=attachment%3B%20filename%3D%22Movie.Title.2024.1080p.mkv%22" +
            "&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc"
        assertEquals(MimeTypes.VIDEO_MATROSKA, PlayerMediaSourceFactory.inferMimeType(url = r2MkvUrl, filename = null))

        val s3Mp4Url = "https://s3.amazonaws.com/bucket/stream?content-disposition=attachment%3B%20filename%3D%22video.mp4%22%3B%20size%3D12345"
        assertEquals(MimeTypes.VIDEO_MP4, PlayerMediaSourceFactory.inferMimeType(url = s3Mp4Url, filename = null))

        val directFilenameParamUrl = "https://cdn.example.com/stream?file=test_stream.m3u8"
        assertEquals(MimeTypes.APPLICATION_M3U8, PlayerMediaSourceFactory.inferMimeType(url = directFilenameParamUrl, filename = null))
    }

    private fun String.basicAuthHeader(): String =
        "Basic " + Base64.getEncoder().encodeToString(toByteArray(Charsets.UTF_8))
}
