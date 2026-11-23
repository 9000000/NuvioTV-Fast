package com.nuvio.tv.ui.screens.ambient

import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.mediaserver.ServerItemRef
import com.nuvio.tv.data.trailer.TrailerPlaybackSource
import com.nuvio.tv.data.trailer.TrailerService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmbientTrailerResolverTest {

    private fun sampleSlide(
        itemId: String = "tt1375666",
        imdbId: String? = null,
        apiType: String = "movie",
        year: String? = "2010",
        trailerYtIds: List<String> = emptyList()
    ) = AmbientSlide(
        id = "$apiType:$itemId",
        title = "Inception",
        backdropUrl = "https://example.com/backdrop.jpg",
        logoUrl = null,
        genres = listOf("Action"),
        itemId = itemId,
        imdbId = imdbId,
        apiType = apiType,
        year = year,
        trailerYtIds = trailerYtIds
    )

    @Test
    fun `resolve prefers TMDB lookup with ignoreUseTrailersGate enabled`() = runTest {
        val tmdbService = mockk<TmdbService>()
        val trailerService = mockk<TrailerService>()
        val expected = TrailerPlaybackSource(videoUrl = "https://cdn.example.com/inception.mp4")

        every { trailerService.isRateLimited() } returns false
        coEvery { tmdbService.ensureTmdbId("tt1375666", "movie") } returns "27205"
        coEvery {
            trailerService.getTrailerPlaybackSource(
                title = "Inception",
                year = "2010",
                tmdbId = "27205",
                type = "movie",
                ignoreUseTrailersGate = true
            )
        } returns expected

        val resolver = AmbientTrailerResolver(
            tmdbService = tmdbService,
            trailerService = trailerService,
            inAppTrailerPlaybackEnabled = { true }
        )

        val actual = resolver.resolve(sampleSlide(trailerYtIds = listOf("YoHD9XEInc0")))

        assertEquals(expected, actual)
        coVerify(exactly = 0) { trailerService.getTrailerPlaybackSourceFromYouTubeUrl(any(), any(), any()) }
    }

    @Test
    fun `resolve falls back to slide trailerYtIds when TMDB yields no trailer`() = runTest {
        val tmdbService = mockk<TmdbService>()
        val trailerService = mockk<TrailerService>()
        val fallback = TrailerPlaybackSource(videoUrl = "https://cdn.example.com/fallback.mp4")

        every { trailerService.isRateLimited() } returns false
        coEvery { tmdbService.ensureTmdbId("tt1375666", "movie") } returns "27205"
        coEvery {
            trailerService.getTrailerPlaybackSource(any(), any(), any(), any(), ignoreUseTrailersGate = true)
        } returns null
        coEvery {
            trailerService.getTrailerPlaybackSourceFromYouTubeUrl(
                youtubeUrl = "https://www.youtube.com/watch?v=YoHD9XEInc0",
                title = "Inception",
                year = "2010"
            )
        } returns fallback

        val resolver = AmbientTrailerResolver(
            tmdbService = tmdbService,
            trailerService = trailerService,
            inAppTrailerPlaybackEnabled = { true }
        )

        val actual = resolver.resolve(sampleSlide(trailerYtIds = listOf("YoHD9XEInc0")))

        assertEquals(fallback, actual)
    }

    @Test
    fun `resolve skips TMDB lookup for server items without imdbId and still checks fallback YouTube IDs`() = runTest {
        val tmdbService = mockk<TmdbService>()
        val trailerService = mockk<TrailerService>()
        val fallback = TrailerPlaybackSource(videoUrl = "https://cdn.example.com/server-trailer.mp4")

        every { trailerService.isRateLimited() } returns false
        coEvery {
            trailerService.getTrailerPlaybackSourceFromYouTubeUrl(
                youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                title = "Inception",
                year = "2010"
            )
        } returns fallback

        val resolver = AmbientTrailerResolver(
            tmdbService = tmdbService,
            trailerService = trailerService,
            inAppTrailerPlaybackEnabled = { true }
        )

        val actual = resolver.resolve(
            sampleSlide(
                itemId = ServerItemRef(connectionId = "plex-1", itemId = "item-42").encode(),
                imdbId = null,
                trailerYtIds = listOf("dQw4w9WgXcQ")
            )
        )

        assertEquals(fallback, actual)
        coVerify(exactly = 0) { tmdbService.ensureTmdbId(any(), any()) }
    }

    @Test
    fun `resolve returns null immediately when in-app trailers are disabled or rate limited`() = runTest {
        val tmdbService = mockk<TmdbService>()
        val trailerService = mockk<TrailerService>()
        every { trailerService.isRateLimited() } returns true

        val rateLimitedResolver = AmbientTrailerResolver(
            tmdbService = tmdbService,
            trailerService = trailerService,
            inAppTrailerPlaybackEnabled = { true }
        )
        assertNull(rateLimitedResolver.resolve(sampleSlide(trailerYtIds = listOf("YoHD9XEInc0"))))

        every { trailerService.isRateLimited() } returns false
        val playstoreResolver = AmbientTrailerResolver(
            tmdbService = tmdbService,
            trailerService = trailerService,
            inAppTrailerPlaybackEnabled = { false }
        )
        assertNull(playstoreResolver.resolve(sampleSlide(trailerYtIds = listOf("YoHD9XEInc0"))))

        coVerify(exactly = 0) { tmdbService.ensureTmdbId(any(), any()) }
        coVerify(exactly = 0) { trailerService.getTrailerPlaybackSource(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { trailerService.getTrailerPlaybackSourceFromYouTubeUrl(any(), any(), any()) }
    }
}
