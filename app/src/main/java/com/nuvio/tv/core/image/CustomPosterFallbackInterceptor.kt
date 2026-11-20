package com.nuvio.tv.core.image

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import kotlinx.coroutines.CancellationException

/**
 * Coil interceptor that detects failed custom poster loads and retries with the
 * original poster URL stored in [ImageRequest.memoryCacheKeyExtra] under the
 * [FALLBACK_URL_KEY] key.
 *
 * A short-lived, thread-safe LRU negative cache avoids waiting on repeated network
 * timeouts for custom poster URLs that recently failed.
 */
class CustomPosterFallbackInterceptor(
    private val ttlMs: Long = DEFAULT_NEGATIVE_CACHE_TTL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis
) : Interceptor {

    companion object {
        const val FALLBACK_URL_KEY = "custom_poster_fallback_url"
        const val DEFAULT_NEGATIVE_CACHE_TTL_MS = 10 * 60 * 1000L
        internal const val MAX_NEGATIVE_CACHE_ENTRIES = 512
    }

    private val lock = Any()
    private val negativeCache = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_NEGATIVE_CACHE_ENTRIES
    }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val fallbackUrl = request.memoryCacheKeyExtras[FALLBACK_URL_KEY]
        val primaryUrl = request.data.toString()

        if (!fallbackUrl.isNullOrBlank() && isNegativelyCached(primaryUrl)) {
            return chain.withRequest(buildFallbackRequest(request, fallbackUrl)).proceed()
        }

        val result = chain.proceed()

        if (result is ErrorResult &&
            result.throwable !is CancellationException &&
            !fallbackUrl.isNullOrBlank()
        ) {
            if (primaryUrl != fallbackUrl) {
                recordNegativeCache(primaryUrl)
            }
            return chain.withRequest(buildFallbackRequest(request, fallbackUrl)).proceed()
        }

        return result
    }

    private fun isNegativelyCached(url: String): Boolean {
        if (ttlMs <= 0L || url.isBlank()) return false
        val now = nowMs()
        synchronized(lock) {
            val failedAtMs = negativeCache[url] ?: return false
            if (now - failedAtMs >= ttlMs) {
                negativeCache.remove(url)
                return false
            }
            return true
        }
    }

    private fun recordNegativeCache(url: String) {
        if (ttlMs <= 0L || url.isBlank()) return
        val now = nowMs()
        synchronized(lock) {
            negativeCache[url] = now
        }
    }

    private fun buildFallbackRequest(request: ImageRequest, fallbackUrl: String): ImageRequest =
        request.newBuilder()
            .data(fallbackUrl)
            .memoryCacheKeyExtras(
                request.memoryCacheKeyExtras - FALLBACK_URL_KEY
            )
            .build()
}
