package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Subtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nuvio.tv.core.network.IPv4FirstDns
import com.nuvio.tv.core.player.SubtitleCharsetDetector
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

private fun OkHttpClient.Builder.subtitleDownloadDefaults(): OkHttpClient.Builder = this
    // Sidecar and auto-sync downloads use these clients; keep timeouts generous for flaky hosts.
    .connectTimeout(12_000, TimeUnit.MILLISECONDS)
    .readTimeout(15_000, TimeUnit.MILLISECONDS)
    .callTimeout(25_000, TimeUnit.MILLISECONDS)
    .retryOnConnectionFailure(true)
    .followRedirects(true)
    .followSslRedirects(true)
    .addNetworkInterceptor(ForwardedStreamHeaderGuard)

// Validating client. Interceptors are cleared so playbackHttpClient's trust-all SSL fallback can't
// resend stream credentials; executeSubtitleRequest handles the fallback instead.
internal val subtitleHttpClient: OkHttpClient by lazy {
    PlayerPlaybackNetworking.playbackHttpClient.newBuilder()
        .apply { interceptors().clear() }
        .subtitleDownloadDefaults()
        .build()
}

internal val subtitleUnvalidatedTlsHttpClient: OkHttpClient by lazy {
    PlayerPlaybackNetworking.trustAllPlaybackHttpClient.newBuilder()
        .subtitleDownloadDefaults()
        .build()
}

private const val SUBTITLE_DOWNLOAD_MAX_ATTEMPTS = 3
private const val SUBTITLE_DOWNLOAD_RETRY_DELAY_MS = 350L

private const val AUTO_SYNC_REACTION_COMPENSATION_MS = 300L

internal fun PlayerRuntimeController.showSubtitleTimingDialog() {
    _uiState.update {
        it.copy(
            showSubtitleTimingDialog = true,
            showSubtitleOverlay = false,
            showSubtitleStylePanel = false,
            showSubtitleDelayOverlay = false,
            showMoreDialog = false,
            showSpeedDialog = false,
            showAudioOverlay = false,
            showControls = false,
            subtitleAutoSyncCapturedVideoMs = null,
            subtitleAutoSyncStatus = null
        )
    }
    maybeLoadSubtitleAutoSyncCues(force = false)
}

internal fun PlayerRuntimeController.dismissSubtitleTimingDialog() {
    subtitleAutoSyncLoadJob?.cancel()
    subtitleAutoSyncLoadJob = null
    _uiState.update { it.copy(showSubtitleTimingDialog = false, subtitleAutoSyncStatus = null) }
    scheduleHideControls()
}

internal fun PlayerRuntimeController.captureSubtitleAutoSyncTime() {
    val capturePositionMs = currentPlaybackPositionMs()?.coerceAtLeast(0L) ?: 0L
    _uiState.update {
        it.copy(
            subtitleAutoSyncCapturedVideoMs = capturePositionMs,
            subtitleAutoSyncStatus = null,
            subtitleAutoSyncError = null
        )
    }
}

internal fun PlayerRuntimeController.applySubtitleAutoSyncCue(cueStartTimeMs: Long) {
    val capturePositionMs =
        _uiState.value.subtitleAutoSyncCapturedVideoMs ?: currentPlaybackPositionMs() ?: return
    val newDelayMs = (capturePositionMs - cueStartTimeMs - AUTO_SYNC_REACTION_COMPENSATION_MS)
        .toInt()
        .coerceIn(SUBTITLE_DELAY_MIN_MS, SUBTITLE_DELAY_MAX_MS)

    subtitleDelayUs.set(newDelayMs.toLong() * 1000L)
    _uiState.update {
        it.copy(
            subtitleDelayMs = newDelayMs,
            showSubtitleTimingDialog = false,
            showSubtitleDelayOverlay = true,
            showControls = false,
            subtitleAutoSyncStatus = context.getString(
                R.string.subtitle_auto_sync_applied,
                formatAutoSyncDelay(newDelayMs)
            ),
            subtitleAutoSyncError = null
        )
    }
    // Remember the delay so it survives to the next session (issue #1063).
    persistTrackPreference()
    refreshActiveSubtitleTrackAfterTimingChange()
    scheduleHideSubtitleDelayOverlay()
}

internal fun PlayerRuntimeController.reloadSubtitleAutoSyncCues() {
    maybeLoadSubtitleAutoSyncCues(force = true)
}

internal fun PlayerRuntimeController.resetSubtitleAutoSyncState(clearLoadedTrack: Boolean = true) {
    subtitleAutoSyncLoadJob?.cancel()
    subtitleAutoSyncLoadJob = null
    _uiState.update {
        it.copy(
            subtitleAutoSyncCues = emptyList(),
            subtitleAutoSyncCapturedVideoMs = null,
            subtitleAutoSyncStatus = null,
            subtitleAutoSyncError = null,
            subtitleAutoSyncLoading = false,
            subtitleAutoSyncLoadedTrackKey = if (clearLoadedTrack) null else it.subtitleAutoSyncLoadedTrackKey
        )
    }
}

private fun PlayerRuntimeController.maybeLoadSubtitleAutoSyncCues(force: Boolean) {
    val selectedSubtitle = _uiState.value.selectedAddonSubtitle
    if (selectedSubtitle == null) {
        _uiState.update {
            it.copy(
                subtitleAutoSyncCues = emptyList(),
                subtitleAutoSyncCapturedVideoMs = null,
                subtitleAutoSyncLoading = false,
                subtitleAutoSyncError = context.getString(R.string.subtitle_auto_sync_select_addon_track),
                subtitleAutoSyncLoadedTrackKey = null
            )
        }
        return
    }

    val selectedTrackKey = selectedSubtitle.autoSyncTrackKey()
    val state = _uiState.value
    if (!force &&
        state.subtitleAutoSyncLoadedTrackKey == selectedTrackKey &&
        state.subtitleAutoSyncCues.isNotEmpty()
    ) {
        return
    }

    subtitleAutoSyncLoadJob?.cancel()
    subtitleAutoSyncLoadJob = scope.launch {
        _uiState.update {
            it.copy(
                subtitleAutoSyncLoading = true,
                subtitleAutoSyncError = null,
                subtitleAutoSyncStatus = null,
                subtitleAutoSyncCues = if (force) emptyList() else it.subtitleAutoSyncCues,
                subtitleAutoSyncCapturedVideoMs = if (force) null else it.subtitleAutoSyncCapturedVideoMs,
                subtitleAutoSyncLoadedTrackKey = selectedTrackKey
            )
        }

        try {
            val rawSubtitleBody = downloadSubtitleBody(
                selectedSubtitle.url,
                selectedSubtitle.lang,
                selectedSubtitle.headers
            )
            val parsedCues = PlayerSubtitleCueParser.parseFromText(
                rawText = rawSubtitleBody,
                sourceUrl = selectedSubtitle.url
            )
                .filter { cue -> cue.text.isNotBlank() }

            if (_uiState.value.selectedAddonSubtitle?.autoSyncTrackKey() != selectedTrackKey) {
                return@launch
            }

            _uiState.update {
                it.copy(
                    subtitleAutoSyncLoading = false,
                    subtitleAutoSyncCues = parsedCues,
                    subtitleAutoSyncError = if (parsedCues.isEmpty()) {
                        context.getString(com.nuvio.tv.R.string.subtitle_timing_file_no_lines)
                    } else {
                        null
                    },
                    subtitleAutoSyncLoadedTrackKey = selectedTrackKey
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_uiState.value.selectedAddonSubtitle?.autoSyncTrackKey() != selectedTrackKey) {
                return@launch
            }
            _uiState.update {
                it.copy(
                    subtitleAutoSyncLoading = false,
                    subtitleAutoSyncCues = emptyList(),
                    subtitleAutoSyncError = e.message ?: context.getString(com.nuvio.tv.R.string.subtitle_timing_load_lines_failed),
                    subtitleAutoSyncLoadedTrackKey = selectedTrackKey
                )
            }
        }
    }
}

/**
 * Downloads a remote subtitle body for sidecar rendering / auto-sync.
 *
 * Stream headers are scoped to the stream's host; see [subtitleStreamHeaders].
 */
internal suspend fun PlayerRuntimeController.downloadSubtitleBody(
    url: String,
    languageHint: String? = null,
    headers: Map<String, String>? = null
): String =
    withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        repeat(SUBTITLE_DOWNLOAD_MAX_ATTEMPTS) { attempt ->
            try {
                return@withContext executeSubtitleDownload(url, languageHint, headers)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < SUBTITLE_DOWNLOAD_MAX_ATTEMPTS - 1) {
                    delay(SUBTITLE_DOWNLOAD_RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw lastError ?: IllegalStateException("Subtitle download failed")
    }

// Stream request control headers, never copied.
private val SUBTITLE_EXCLUDED_STREAM_HEADERS = setOf("range", "host", "connection", "transfer-encoding")

// Stream headers allowed on other hosts (#3328).
private val SUBTITLE_CROSS_HOST_HEADERS = setOf("referer", "origin", "user-agent", "accept-language")

// Not forwarded on an HTTPS to HTTP downgrade.
private val SUBTITLE_DOWNGRADE_EXCLUDED_HEADERS = setOf("referer", "origin")

private fun isDowngrade(requestUrl: HttpUrl, streamUrl: HttpUrl?): Boolean =
    streamUrl != null && streamUrl.isHttps && !requestUrl.isHttps

/** Same host as the stream and no HTTPS to HTTP downgrade. Sibling subdomains are out of scope. */
internal fun mayCarryStreamCredentials(requestUrl: HttpUrl, streamUrl: HttpUrl?): Boolean {
    if (streamUrl == null) return false
    if (isDowngrade(requestUrl, streamUrl)) return false
    return requestUrl.host == streamUrl.host
}

/**
 * All stream headers in scope, only [SUBTITLE_CROSS_HOST_HEADERS] outside it. Credentials can use any
 * header name, so this is an allowlist.
 */
internal fun subtitleStreamHeaders(
    streamHeaders: Map<String, String>,
    subtitleUrl: HttpUrl,
    streamUrl: HttpUrl?
): Map<String, String> {
    val inScope = mayCarryStreamCredentials(subtitleUrl, streamUrl)
    val downgrade = isDowngrade(subtitleUrl, streamUrl)
    return streamHeaders.filterKeys { name ->
        val lower = name.lowercase()
        when {
            lower in SUBTITLE_EXCLUDED_STREAM_HEADERS -> false
            inScope -> true
            downgrade && lower in SUBTITLE_DOWNGRADE_EXCLUDED_HEADERS -> false
            else -> lower in SUBTITLE_CROSS_HOST_HEADERS
        }
    }
}

/**
 * [names] are removed on a hop outside the stream's scope, [downgradeNames] on an HTTPS to HTTP hop.
 * Subtitle-owned headers are never listed, so they survive redirects and the TLS retry.
 */
internal class ForwardedStreamHeaders(
    val streamUrl: HttpUrl,
    val names: Set<String>,
    val downgradeNames: Set<String> = emptySet()
)

/**
 * Applies [ForwardedStreamHeaders] per redirect hop; OkHttp itself only strips Authorization. It never
 * adds headers back, so an HTTP URL redirecting to HTTPS doesn't regain stream credentials.
 */
internal object ForwardedStreamHeaderGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val forwarded = request.tag(ForwardedStreamHeaders::class.java)
        if (forwarded == null || mayCarryStreamCredentials(request.url, forwarded.streamUrl)) {
            return chain.proceed(request)
        }
        var outgoing = request.withoutForwardedStreamHeaders()
        if (isDowngrade(request.url, forwarded.streamUrl) && forwarded.downgradeNames.isNotEmpty()) {
            outgoing = outgoing.newBuilder().apply { forwarded.downgradeNames.forEach { removeHeader(it) } }.build()
        }
        return chain.proceed(outgoing)
    }
}

/** The request without the headers its [ForwardedStreamHeaders] tag lists. */
internal fun Request.withoutForwardedStreamHeaders(): Request {
    val names = tag(ForwardedStreamHeaders::class.java)?.names ?: return this
    return newBuilder().apply { names.forEach { removeHeader(it) } }.build()
}

/** Scoped stream headers, then the subtitle's own headers, then defaults, tagged for the guard. */
internal fun buildSubtitleRequest(
    subtitleUrl: HttpUrl,
    streamUrl: HttpUrl?,
    streamHeaders: Map<String, String>,
    explicitHeaders: Map<String, String>?
): Request {
    val requestBuilder = Request.Builder().url(subtitleUrl)

    val scopedStreamHeaders = subtitleStreamHeaders(streamHeaders, subtitleUrl, streamUrl)
    scopedStreamHeaders.forEach { (key, value) -> requestBuilder.header(key, value) }

    // Explicit subtitle headers override stream headers.
    explicitHeaders?.forEach { (key, value) ->
        if (key.lowercase() !in SUBTITLE_EXCLUDED_STREAM_HEADERS) {
            requestBuilder.header(key, value)
        }
    }

    // A stream User-Agent is always allowed through, so this only fills the gap.
    if (requestBuilder.build().header("User-Agent") == null) {
        requestBuilder.header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
    }

    if (requestBuilder.build().header("Accept") == null) {
        requestBuilder.header("Accept", "text/plain, text/vtt, application/x-subrip, */*")
    }

    val explicitNames = explicitHeaders?.keys.orEmpty().map { it.lowercase() }.toSet()
    val forwardedNames = scopedStreamHeaders.keys
        .filter { it.lowercase() !in SUBTITLE_CROSS_HOST_HEADERS && it.lowercase() !in explicitNames }
        .toSet()
    val downgradeNames = scopedStreamHeaders.keys
        .filter { it.lowercase() in SUBTITLE_DOWNGRADE_EXCLUDED_HEADERS && it.lowercase() !in explicitNames }
        .toSet()
    if (streamUrl != null && (forwardedNames.isNotEmpty() || downgradeNames.isNotEmpty())) {
        requestBuilder.tag(
            ForwardedStreamHeaders::class.java,
            ForwardedStreamHeaders(streamUrl, forwardedNames, downgradeNames)
        )
    }
    return requestBuilder.build()
}

/**
 * Tries [validated] first. On any SSLException, reruns the whole redirect chain on [permissive] without
 * the forwarded stream headers, so self-signed hosts still work.
 */
internal fun executeSubtitleRequest(
    request: Request,
    validated: OkHttpClient = subtitleHttpClient,
    permissive: OkHttpClient = subtitleUnvalidatedTlsHttpClient
): okhttp3.Response =
    try {
        validated.newCall(request).execute()
    } catch (e: SSLException) {
        permissive.newCall(request.withoutForwardedStreamHeaders()).execute()
    }

private fun PlayerRuntimeController.executeSubtitleDownload(
    url: String,
    languageHint: String? = null,
    customHeaders: Map<String, String>? = null
): String {
    val explicitHeaders = customHeaders
        ?: streamSubtitles.firstOrNull { it.url == url }?.headers
        ?: _uiState.value.addonSubtitles.firstOrNull { it.url == url }?.headers
        ?: _uiState.value.selectedAddonSubtitle?.takeIf { it.url == url }?.headers
    val request = buildSubtitleRequest(
        subtitleUrl = url.toHttpUrl(),
        streamUrl = currentStreamUrl.toHttpUrlOrNull(),
        streamHeaders = currentHeaders,
        explicitHeaders = explicitHeaders
    )

    val response = executeSubtitleRequest(request)
    response.use {
        if (!response.isSuccessful) {
            error(context.getString(com.nuvio.tv.R.string.subtitle_download_failed_http, response.code))
        }
        val bodyBytes = response.body?.bytes()
            ?: error(context.getString(com.nuvio.tv.R.string.subtitle_download_empty_content))
        if (bodyBytes.isEmpty()) {
            error(context.getString(com.nuvio.tv.R.string.subtitle_download_empty_content))
        }
        val body = SubtitleCharsetDetector.decode(bodyBytes, languageHint = languageHint)
        if (body.isBlank()) {
            error(context.getString(com.nuvio.tv.R.string.subtitle_download_empty_content))
        }
        return body
    }
}

private fun Subtitle.autoSyncTrackKey(): String = "$id|$url"

internal fun formatAutoSyncTimestamp(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

internal fun formatAutoSyncDelay(delayMs: Int): String {
    val sign = if (delayMs >= 0) "+" else "-"
    val absMs = kotlin.math.abs(delayMs)
    val seconds = absMs / 1000
    val millis = absMs % 1000
    return "$sign${seconds}.${millis.toString().padStart(3, '0')}s"
}
