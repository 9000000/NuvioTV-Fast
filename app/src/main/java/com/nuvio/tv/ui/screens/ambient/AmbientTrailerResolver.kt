package com.nuvio.tv.ui.screens.ambient

import com.nuvio.tv.core.build.AppFeaturePolicy
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.mediaserver.ServerItemRef
import com.nuvio.tv.data.trailer.TrailerPlaybackSource
import com.nuvio.tv.data.trailer.TrailerService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

private const val DEFAULT_TRAILER_RESOLVE_TIMEOUT_MS = 10_000L
private const val MAX_FALLBACK_YT_CANDIDATES = 2

@Singleton
class AmbientTrailerResolver(
    private val tmdbService: TmdbService,
    private val trailerService: TrailerService,
    private val inAppTrailerPlaybackEnabled: () -> Boolean = { AppFeaturePolicy.inAppTrailerPlaybackEnabled },
    private val resolveTimeoutMs: Long = DEFAULT_TRAILER_RESOLVE_TIMEOUT_MS
) {
    @Inject
    constructor(
        tmdbService: TmdbService,
        trailerService: TrailerService
    ) : this(
        tmdbService = tmdbService,
        trailerService = trailerService,
        inAppTrailerPlaybackEnabled = { AppFeaturePolicy.inAppTrailerPlaybackEnabled },
        resolveTimeoutMs = DEFAULT_TRAILER_RESOLVE_TIMEOUT_MS
    )

    suspend fun resolve(slide: AmbientSlide): TrailerPlaybackSource? {
        if (!inAppTrailerPlaybackEnabled()) return null
        if (trailerService.isRateLimited()) return null

        return withTimeoutOrNull(resolveTimeoutMs) {
            resolveFromTmdb(slide) ?: resolveFromFallbackYouTubeIds(slide)
        }
    }

    private suspend fun resolveFromTmdb(slide: AmbientSlide): TrailerPlaybackSource? {
        val lookupId = slide.imdbId?.takeIf { it.isNotBlank() }
            ?: slide.itemId.takeIf { it.isNotBlank() && !ServerItemRef.isServerId(it) }
            ?: return null

        val tmdbId = try {
            tmdbService.ensureTmdbId(lookupId, slide.apiType)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return null

        val source = try {
            trailerService.getTrailerPlaybackSource(
                title = slide.title,
                year = slide.year,
                tmdbId = tmdbId,
                type = slide.apiType,
                ignoreUseTrailersGate = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return source?.takeIf { it.videoUrl.isNotBlank() }
    }

    private suspend fun resolveFromFallbackYouTubeIds(slide: AmbientSlide): TrailerPlaybackSource? {
        for (ytId in slide.trailerYtIds.take(MAX_FALLBACK_YT_CANDIDATES)) {
            if (trailerService.isRateLimited()) break
            val source = try {
                trailerService.getTrailerPlaybackSourceFromYouTubeUrl(
                    youtubeUrl = "https://www.youtube.com/watch?v=$ytId",
                    title = slide.title,
                    year = slide.year
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (source != null && source.videoUrl.isNotBlank()) {
                return source
            }
        }
        return null
    }
}
