package com.nuvio.tv.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Test

class InAppYouTubeExtractorVideoCapTest {

    private val extractor = InAppYouTubeExtractor()

    @Test
    fun `cap drops streams above 1080p`() {
        val streams = listOf(
            videoCandidate(url = "2160p", width = 3840, height = 2160),
            videoCandidate(url = "1080p", width = 1920, height = 1080)
        )

        val capped = extractor.capVideoHeight(streams, maxHeight = 1080)

        assertEquals(listOf("1080p"), capped.map { it.url })
    }

    @Test
    fun `cap uses width for widescreen trailers`() {
        val streams = listOf(
            videoCandidate(url = "1440p-scope", width = 2560, height = 1072),
            videoCandidate(url = "1080p-scope", width = 1920, height = 804)
        )

        val capped = extractor.capVideoHeight(streams, maxHeight = 1080)

        assertEquals(listOf("1080p-scope"), capped.map { it.url })
    }

    @Test
    fun `cap keeps every stream when none fits`() {
        val streams = listOf(videoCandidate(url = "2160p", width = 3840, height = 2160))

        val capped = extractor.capVideoHeight(streams, maxHeight = 1080)

        assertEquals(listOf("2160p"), capped.map { it.url })
    }

    @Test
    fun `no cap keeps 4K`() {
        val streams = listOf(
            videoCandidate(url = "2160p", width = 3840, height = 2160),
            videoCandidate(url = "1080p", width = 1920, height = 1080)
        )

        val capped = extractor.capVideoHeight(streams, maxHeight = Int.MAX_VALUE)

        assertEquals(listOf("2160p", "1080p"), capped.map { it.url })
    }

    @Test
    fun `rankAdaptiveVideos steps down smoothly when 1080p is unavailable`() {
        val streams = listOf(
            videoCandidate(url = "360p", width = 640, height = 360),
            videoCandidate(url = "720p", width = 1280, height = 720),
            videoCandidate(url = "480p", width = 854, height = 480)
        )

        val ranked = extractor.rankAdaptiveVideos(streams, maxVideoHeight = 1080)

        assertEquals(listOf("720p", "480p", "360p"), ranked.map { it.url })
    }

    @Test
    fun `rankAdaptiveVideos skips failed urls and rotates to next codec or client`() {
        val streams = listOf(
            videoCandidate(url = "visionos-1080p-avc", width = 1920, height = 1080, score = 1080_000_000_000.0 + 4_000_000.0, client = "visionos", priority = 0),
            videoCandidate(url = "visionos-1080p-vp9", width = 1920, height = 1080, score = 1080_000_000_000.0 + 2_000_000.0, client = "visionos", priority = 0),
            videoCandidate(url = "android-1080p-avc", width = 1920, height = 1080, score = 1080_000_000_000.0 + 4_000_000.0, client = "android", priority = 1)
        )

        val afterFirstFailure = extractor.rankAdaptiveVideos(
            items = streams,
            maxVideoHeight = 1080,
            failedUrls = setOf("visionos-1080p-avc")
        )
        assertEquals(
            listOf("visionos-1080p-vp9", "android-1080p-avc"),
            afterFirstFailure.map { it.url }
        )

        val afterAllVisionOsFailed = extractor.rankAdaptiveVideos(
            items = streams,
            maxVideoHeight = 1080,
            failedUrls = setOf("visionos-1080p-avc", "visionos-1080p-vp9")
        )
        assertEquals(listOf("android-1080p-avc"), afterAllVisionOsFailed.map { it.url })
    }

    @Test
    fun `rankAdaptiveAudios prefers matching client default track and skips failed urls`() {
        val audios = listOf(
            audioCandidate(url = "android-default", client = "android", score = 130_000.0, isDefault = true),
            audioCandidate(url = "visionos-failed", client = "visionos", score = 150_000.0, isDefault = true),
            audioCandidate(url = "visionos-fallback", client = "visionos", score = 128_000.0, isDefault = true),
            audioCandidate(url = "visionos-dub", client = "visionos", score = 256_000.0, isDefault = false)
        )

        val ranked = extractor.rankAdaptiveAudios(
            items = audios,
            preferredClient = "visionos",
            failedUrls = setOf("visionos-failed")
        )

        assertEquals(
            listOf("visionos-fallback", "visionos-dub", "android-default"),
            ranked.map { it.url }
        )
    }

    private fun videoCandidate(
        url: String,
        width: Int,
        height: Int,
        score: Double = height.toDouble(),
        client: String = "visionos",
        priority: Int = 0
    ): StreamCandidate {
        return StreamCandidate(
            client = client,
            priority = priority,
            url = url,
            score = score,
            hasN = false,
            itag = "0",
            height = height,
            fps = 30,
            ext = "webm",
            width = width
        )
    }

    private fun audioCandidate(
        url: String,
        client: String,
        score: Double,
        isDefault: Boolean
    ): StreamCandidate {
        return StreamCandidate(
            client = client,
            priority = if (client == "visionos") 0 else 1,
            url = url,
            score = score,
            hasN = false,
            itag = "140",
            height = 0,
            fps = 0,
            ext = "m4a",
            isDefaultAudioTrack = isDefault
        )
    }
}
