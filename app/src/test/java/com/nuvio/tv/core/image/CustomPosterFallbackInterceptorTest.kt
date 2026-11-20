package com.nuvio.tv.core.image

import android.content.Context
import android.graphics.Canvas
import coil3.Image
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Size
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomPosterFallbackInterceptorTest {

    private val context: Context = mockk(relaxed = true)

    private val fakeImage: Image = object : Image {
        override val size: Long = 0L
        override val width: Int = 10
        override val height: Int = 10
        override val shareable: Boolean = true
        override fun draw(canvas: Canvas) = Unit
    }

    private fun buildRequest(url: String, fallbackUrl: String? = null): ImageRequest {
        val builder = ImageRequest.Builder(context).data(url)
        if (fallbackUrl != null) {
            builder.memoryCacheKeyExtras(
                mapOf(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to fallbackUrl)
            )
        }
        return builder.build()
    }

    private class RecordingChain(
        override val request: ImageRequest,
        override val size: Size = Size.ORIGINAL,
        private val proceedHistory: MutableList<ImageRequest>,
        private val handler: (ImageRequest) -> ImageResult
    ) : Interceptor.Chain {
        override fun withRequest(request: ImageRequest): Interceptor.Chain =
            RecordingChain(request, size, proceedHistory, handler)

        override fun withSize(size: Size): Interceptor.Chain =
            RecordingChain(request, size, proceedHistory, handler)

        override suspend fun proceed(): ImageResult {
            proceedHistory.add(request)
            return handler(request)
        }
    }

    @Test
    fun `negative cache records failure and subsequent request skips primary URL`() = runTest {
        var now = 1_000L
        val interceptor = CustomPosterFallbackInterceptor(
            ttlMs = 10 * 60 * 1000L,
            nowMs = { now }
        )
        val customUrl = "https://custom.example/poster-1.jpg"
        val fallbackUrl = "https://tmdb.example/poster-1.jpg"
        val proceeded = mutableListOf<ImageRequest>()

        val firstResult = interceptor.intercept(
            RecordingChain(buildRequest(customUrl, fallbackUrl), proceedHistory = proceeded) { req ->
                if (req.data.toString() == customUrl) {
                    ErrorResult(null, req, RuntimeException("HTTP 404"))
                } else {
                    SuccessResult(fakeImage, req, DataSource.NETWORK)
                }
            }
        )

        assertTrue(firstResult is SuccessResult)
        assertEquals(listOf(customUrl, fallbackUrl), proceeded.map { it.data.toString() })
        assertFalse(
            proceeded.last().memoryCacheKeyExtras.containsKey(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY)
        )

        // Subsequent request within TTL must skip customUrl and proceed directly with fallbackUrl
        proceeded.clear()
        now += 60_000L
        val secondResult = interceptor.intercept(
            RecordingChain(buildRequest(customUrl, fallbackUrl), proceedHistory = proceeded) { req ->
                SuccessResult(fakeImage, req, DataSource.NETWORK)
            }
        )

        assertTrue(secondResult is SuccessResult)
        assertEquals(listOf(fallbackUrl), proceeded.map { it.data.toString() })
        assertFalse(
            proceeded.single().memoryCacheKeyExtras.containsKey(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY)
        )
    }

    @Test
    fun `expired TTL evicts negative cache entry and retries primary URL`() = runTest {
        var now = 10_000L
        val ttlMs = 5_000L
        val interceptor = CustomPosterFallbackInterceptor(
            ttlMs = ttlMs,
            nowMs = { now }
        )
        val customUrl = "https://custom.example/poster-ttl.jpg"
        val fallbackUrl = "https://tmdb.example/poster-ttl.jpg"
        val proceeded = mutableListOf<ImageRequest>()

        // Initial failure populates cache at t = 10_000
        interceptor.intercept(
            RecordingChain(buildRequest(customUrl, fallbackUrl), proceedHistory = proceeded) { req ->
                if (req.data.toString() == customUrl) {
                    ErrorResult(null, req, RuntimeException("timeout"))
                } else {
                    SuccessResult(fakeImage, req, DataSource.NETWORK)
                }
            }
        )
        assertEquals(listOf(customUrl, fallbackUrl), proceeded.map { it.data.toString() })

        // Still cached just before TTL boundary
        proceeded.clear()
        now = 14_999L
        interceptor.intercept(
            RecordingChain(buildRequest(customUrl, fallbackUrl), proceedHistory = proceeded) { req ->
                SuccessResult(fakeImage, req, DataSource.NETWORK)
            }
        )
        assertEquals(listOf(fallbackUrl), proceeded.map { it.data.toString() })

        // At TTL boundary (15_000), entry expires and primary URL is retried
        proceeded.clear()
        now = 15_000L
        val recoveredResult = interceptor.intercept(
            RecordingChain(buildRequest(customUrl, fallbackUrl), proceedHistory = proceeded) { req ->
                SuccessResult(fakeImage, req, DataSource.NETWORK)
            }
        )
        assertTrue(recoveredResult is SuccessResult)
        assertEquals(listOf(customUrl), proceeded.map { it.data.toString() })
    }

    @Test
    fun `CancellationException is not negatively cached and does not trigger fallback`() = runTest {
        var now = 1_000L
        val interceptor = CustomPosterFallbackInterceptor(
            ttlMs = 60_000L,
            nowMs = { now }
        )
        val customUrl = "https://custom.example/poster-cancel.jpg"
        val fallbackUrl = "https://tmdb.example/poster-cancel.jpg"
        val proceeded = mutableListOf<ImageRequest>()
        val cancelError = CancellationException("request cancelled")

        val firstResult = interceptor.intercept(
            RecordingChain(buildRequest(customUrl, fallbackUrl), proceedHistory = proceeded) { req ->
                ErrorResult(null, req, cancelError)
            }
        )

        assertTrue(firstResult is ErrorResult)
        assertSame(cancelError, (firstResult as ErrorResult).throwable)
        assertEquals(listOf(customUrl), proceeded.map { it.data.toString() })

        // Next request must still hit customUrl first
        proceeded.clear()
        now += 1_000L
        val secondResult = interceptor.intercept(
            RecordingChain(buildRequest(customUrl, fallbackUrl), proceedHistory = proceeded) { req ->
                SuccessResult(fakeImage, req, DataSource.NETWORK)
            }
        )
        assertTrue(secondResult is SuccessResult)
        assertEquals(listOf(customUrl), proceeded.map { it.data.toString() })
    }

    @Test
    fun `does not negatively cache when primary data equals fallbackUrl`() = runTest {
        val interceptor = CustomPosterFallbackInterceptor(ttlMs = 60_000L, nowMs = { 1_000L })
        val sameUrl = "https://tmdb.example/same.jpg"
        val proceeded = mutableListOf<ImageRequest>()

        interceptor.intercept(
            RecordingChain(buildRequest(sameUrl, sameUrl), proceedHistory = proceeded) { req ->
                ErrorResult(null, req, RuntimeException("boom"))
            }
        )

        proceeded.clear()
        interceptor.intercept(
            RecordingChain(buildRequest(sameUrl, sameUrl), proceedHistory = proceeded) { req ->
                SuccessResult(fakeImage, req, DataSource.NETWORK)
            }
        )

        assertEquals(listOf(sameUrl), proceeded.map { it.data.toString() })
        assertTrue(
            proceeded.single().memoryCacheKeyExtras.containsKey(CustomPosterFallbackInterceptor.FALLBACK_URL_KEY)
        )
    }

    @Test
    fun `bounded LRU evicts oldest entry beyond 512 failed URLs and zero-arg constructor works`() = runTest {
        val zeroArgInterceptor = CustomPosterFallbackInterceptor()
        val proceeded = mutableListOf<ImageRequest>()
        val fallbackUrl = "https://tmdb.example/fallback.jpg"

        // Fill 513 distinct failed primary URLs -> entry 0 should be evicted
        for (i in 0 until CustomPosterFallbackInterceptor.MAX_NEGATIVE_CACHE_ENTRIES + 1) {
            val url = "https://custom.example/poster-$i.jpg"
            zeroArgInterceptor.intercept(
                RecordingChain(buildRequest(url, fallbackUrl), proceedHistory = proceeded) { req ->
                    if (req.data.toString() == url) {
                        ErrorResult(null, req, RuntimeException("fail-$i"))
                    } else {
                        SuccessResult(fakeImage, req, DataSource.NETWORK)
                    }
                }
            )
        }

        // Oldest entry (poster-0) was evicted, so primary is attempted again
        proceeded.clear()
        zeroArgInterceptor.intercept(
            RecordingChain(buildRequest("https://custom.example/poster-0.jpg", fallbackUrl), proceedHistory = proceeded) { req ->
                SuccessResult(fakeImage, req, DataSource.NETWORK)
            }
        )
        assertEquals(listOf("https://custom.example/poster-0.jpg"), proceeded.map { it.data.toString() })
    }
}
