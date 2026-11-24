package com.nuvio.tv.data.trailer

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InAppYouTubeExtractorProbeAndVisitorTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `isUrlReachable preserves Range header for video and audio probes`() {
        server.enqueue(MockResponse().setResponseCode(206).setBody("x"))
        server.enqueue(MockResponse().setResponseCode(206).setBody("x".repeat(64)))
        server.enqueue(MockResponse().setResponseCode(403))

        val extractor = InAppYouTubeExtractor(
            canDecodeVideo = { _, _, _, _ -> true },
            youtubeBaseUrl = server.url("").toString().trimEnd('/')
        )

        assertTrue(extractor.isUrlReachable(server.url("/video").toString(), probeEndByte = 0))
        val videoReq = server.takeRequest()
        assertEquals("bytes=0-0", videoReq.getHeader("Range"))
        assertNotNull(videoReq.getHeader("User-Agent"))

        assertTrue(extractor.isUrlReachable(server.url("/audio").toString(), probeEndByte = 1023))
        val audioReq = server.takeRequest()
        assertEquals("bytes=0-1023", audioReq.getHeader("Range"))

        assertFalse(extractor.isUrlReachable(server.url("/blocked").toString(), probeEndByte = 1023))
    }

    @Test
    fun `ensureWatchConfig recovers visitorData via visitor_id API when watch page omits it`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/watch") -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""<html>"INNERTUBE_API_KEY":"TEST_KEY_123"</html>""")
                    path.startsWith("/youtubei/v1/visitor_id") -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"responseContext":{"visitorData":"RECOVERED_VISITOR_TOKEN"}}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val extractor = InAppYouTubeExtractor(
            canDecodeVideo = { _, _, _, _ -> true },
            youtubeBaseUrl = server.url("").toString().trimEnd('/')
        )

        val config = extractor.ensureWatchConfig(forceRefresh = true)

        assertEquals("TEST_KEY_123", config.apiKey)
        assertEquals("RECOVERED_VISITOR_TOKEN", config.visitorData)
        assertEquals(InAppYouTubeExtractor.CONFIG_TTL_MS, config.ttlMs)
    }

    @Test
    fun `ensureWatchConfig uses short fallback ttl when visitorData remains missing`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/watch") -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""<html>consent page without visitor</html>""")
                    path.startsWith("/youtubei/v1/visitor_id") -> MockResponse()
                        .setResponseCode(500)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val extractor = InAppYouTubeExtractor(
            canDecodeVideo = { _, _, _, _ -> true },
            youtubeBaseUrl = server.url("").toString().trimEnd('/')
        )

        val config = extractor.ensureWatchConfig(forceRefresh = true)

        assertNull(config.visitorData)
        assertEquals(InAppYouTubeExtractor.FALLBACK_CONFIG_TTL_MS, config.ttlMs)
    }

    @Test
    fun `extractPlaybackSource selects 1080p adaptive stream when capped at 1080p`() = runTest {
        val baseUrl = server.url("").toString().trimEnd('/')
        val url4k = "$baseUrl/stream-2160p"
        val url1080 = "$baseUrl/stream-1080p"
        val url720 = "$baseUrl/stream-720p"
        val urlAudio = "$baseUrl/stream-audio"

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.startsWith("/watch") -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""<html>consent</html>""")
                    path.startsWith("/youtubei/v1/visitor_id") -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"responseContext":{"visitorData":"FRESH_VD"}}""")
                    path.startsWith("/youtubei/v1/player") -> {
                        val visitorHeader = request.getHeader("x-goog-visitor-id")
                        if (visitorHeader != "FRESH_VD") {
                            return MockResponse().setResponseCode(200).setBody(
                                """{"playabilityStatus":{"status":"LOGIN_REQUIRED"}}"""
                            )
                        }
                        MockResponse().setResponseCode(200).setBody(
                            """
                            {
                              "playabilityStatus": { "status": "OK" },
                              "streamingData": {
                                "adaptiveFormats": [
                                  {
                                    "itag": 313,
                                    "url": "$url4k",
                                    "mimeType": "video/webm; codecs=\"vp9\"",
                                    "width": 3840,
                                    "height": 2160,
                                    "fps": 24,
                                    "bitrate": 15000000
                                  },
                                  {
                                    "itag": 137,
                                    "url": "$url1080",
                                    "mimeType": "video/mp4; codecs=\"avc1.640028\"",
                                    "width": 1920,
                                    "height": 1080,
                                    "fps": 24,
                                    "bitrate": 4300000
                                  },
                                  {
                                    "itag": 136,
                                    "url": "$url720",
                                    "mimeType": "video/mp4; codecs=\"avc1.4D401F\"",
                                    "width": 1280,
                                    "height": 720,
                                    "fps": 24,
                                    "bitrate": 1300000
                                  },
                                  {
                                    "itag": 140,
                                    "url": "$urlAudio",
                                    "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                                    "bitrate": 130000,
                                    "audioSampleRate": "44100"
                                  }
                                ]
                              }
                            }
                            """.trimIndent()
                        )
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val extractor = InAppYouTubeExtractor(
            canDecodeVideo = { _, _, _, _ -> true },
            youtubeBaseUrl = baseUrl
        )

        val capped1080 = extractor.extractPlaybackSource("TcMBFSGVi1c", maxVideoHeight = 1080)
        assertNotNull(capped1080)
        assertEquals(url1080, capped1080?.videoUrl)
        assertEquals(urlAudio, capped1080?.audioUrl)

        val uncapped4k = extractor.extractPlaybackSource("TcMBFSGVi1c", maxVideoHeight = Int.MAX_VALUE)
        assertNotNull(uncapped4k)
        assertEquals(url4k, uncapped4k?.videoUrl)
        assertEquals(urlAudio, uncapped4k?.audioUrl)
    }
}
