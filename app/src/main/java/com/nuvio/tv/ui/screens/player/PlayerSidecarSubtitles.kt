@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.nuvio.tv.ui.screens.player

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.ui.SubtitleView
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Subtitle
import com.nuvio.tv.ui.screens.player.autosync.AutoSyncLibassSubtitles
import com.nuvio.tv.ui.screens.player.autosync.AutoSyncSyncedSubtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Buffer-preserving sidecar path for external addon subtitles on ExoPlayer.
 *
 * Media3 requires a full [androidx.media3.exoplayer.ExoPlayer.setMediaSource] reload to add
 * [androidx.media3.common.MediaItem.SubtitleConfiguration] tracks mid-playback, which wipes the
 * progressive/VOD buffer. Fast-startup preferred-language auto-select hits that path often.
 *
 * Instead we download + parse with Media3's [DefaultSubtitleParserFactory], then push active
 * [Cue]s to [SubtitleView] while leaving the video MediaPeriod untouched.
 *
 * Failures stay on this path (retry download / multi-mime parse / lenient SRT·VTT fallback).
 * We intentionally do **not** fall back to media reload — that was the main source of mid-play
 * infinite buffering when a flaky subtitle host failed once.
 */
private val sidecarParserFactory = DefaultSubtitleParserFactory()
private val mainHandler = Handler(Looper.getMainLooper())

/**
 * Whether [subtitle] can be hot-attached without [androidx.media3.exoplayer.ExoPlayer.setMediaSource].
 *
 * ASS/SSA are excluded when libass is requested/active: those must stay on the media-source path so
 * [com.nuvio.tv.libass.media.parser.AssSubtitleParserFactory] / AssSubtitleSurfaceView keep full
 * styling. SRT/VTT/TTML still use the buffer-preserving sidecar.
 */
internal fun PlayerRuntimeController.canAttachAddonSubtitleViaSidecar(subtitle: Subtitle): Boolean {
    val mime = PlayerSubtitleUtils.mimeTypeFromUrl(subtitle.url)
    // Preserve pre-sidecar libass behavior for external ASS/SSA addons.
    if (mime == MimeTypes.TEXT_SSA && shouldKeepAssOnLibassMediaPath()) {
        return false
    }
    val format = Format.Builder().setSampleMimeType(mime).build()
    return sidecarParserFactory.supportsFormat(format)
}

internal fun PlayerRuntimeController.canAttachAddonSubtitleViaSidecar(
    url: String,
    useLibass: Boolean,
): Boolean {
    val mime = PlayerSubtitleUtils.mimeTypeFromUrl(url)
    if (mime == MimeTypes.TEXT_SSA && useLibass) return false
    return sidecarParserFactory.supportsFormat(
        Format.Builder().setSampleMimeType(mime).build(),
    )
}

/**
 * User wants libass (or the current Exo player already runs the libass pipeline).
 * In that case external ASS/SSA must not be rendered via Media3's basic SsaParser sidecar.
 */
private fun PlayerRuntimeController.shouldKeepAssOnLibassMediaPath(): Boolean {
    if (isUsingMpvEngine()) return false
    return requestedUseLibassByUser || activePlayerUsesLibass
}

internal fun PlayerRuntimeController.isSidecarAddonSubtitleActive(): Boolean {
    return activeSidecarSubtitleKey != null && _uiState.value.selectedAddonSubtitle != null
}

internal fun PlayerRuntimeController.bindExoSubtitleView(subtitleView: SubtitleView?) {
    exoSubtitleViewRef = subtitleView?.let { WeakReference(it) }
    if (subtitleView != null && _uiState.value.shouldHideVisualSubtitles) {
        subtitleView.visibility = android.view.View.GONE
    }
    if (subtitleView == null && activeSidecarSubtitleKey != null) {
        // View unbound while sidecar still active; ticker will no-op until rebound.
        return
    }
    if (activeSidecarSubtitleKey != null && sidecarTimedCues.isNotEmpty()) {
        renderSidecarCuesAtCurrentPosition()
    }
}

internal fun PlayerRuntimeController.stopSidecarAddonSubtitle(clearView: Boolean = true) {
    sidecarSubtitleJob?.cancel()
    sidecarSubtitleJob = null
    activeSidecarSubtitleKey = null
    activeSidecarGeneration = 0L
    AutoSyncSyncedSubtitle.clear() // AutoSync hook
    AutoSyncLibassSubtitles.clear(_exoPlayer?.getAssHandlerCompat()) // AutoSync hook
    sidecarTimedCues = emptyList()
    originalSidecarSubtitleKey = null
    originalSidecarTimedCues = emptyList()
    lastSidecarCueSignature = null
    if (clearView) {
        postToSubtitleView { view ->
            view.setTag(R.id.player_view_sidecar_generation_tag, null)
            showSidecarCues(view, emptyList())
        }
    }
}

/**
 * Downloads, parses, and starts rendering [subtitle] without reloading the media source.
 * Returns false if the format is unsupported or a newer selection superseded this request.
 */
internal fun PlayerRuntimeController.startSidecarAddonSubtitle(
    subtitle: Subtitle,
    rawBodyLoader: (suspend () -> String)? = null,
): Boolean {
    if (!canAttachAddonSubtitleViaSidecar(subtitle)) return false

    val subtitleKey = subtitle.url
    val urlMimeHint = PlayerSubtitleUtils.mimeTypeFromUrl(subtitle.url)

    sidecarSubtitleJob?.cancel()
    val generation = ++sidecarGenerationCounter
    activeSidecarGeneration = generation
    AutoSyncSyncedSubtitle.clear() // AutoSync hook
    activeSidecarSubtitleKey = subtitleKey
    lastSidecarCueSignature = null
    sidecarTimedCues = emptyList()
    postToSubtitleView { view ->
        view.setTag(R.id.player_view_sidecar_generation_tag, subtitleKey)
        showSidecarCues(view, emptyList())
    }
    fun isCurrent() =
        activeSidecarSubtitleKey == subtitleKey && activeSidecarGeneration == generation

    sidecarSubtitleJob = scope.launch {
        try {
            val rawBody = rawBodyLoader?.invoke()
                ?: downloadSubtitleBody(subtitle.url, subtitle.lang, subtitle.headers)
            if (!isCurrent()) return@launch

            val resolvedMime = PlayerSubtitleUtils.sniffSubtitleMimeType(rawBody, subtitle.url)
            val parseResult = withContext(Dispatchers.Default) {
                parseSidecarTimedCuesRobust(rawBody, subtitle.url)
            }
            if (!isCurrent()) return@launch

            if (parseResult.cues.isEmpty()) {
                Log.w(
                    PlayerRuntimeController.TAG,
                    "Sidecar subtitle parse empty for id=${subtitle.id} " +
                        "urlMime=$urlMimeHint sniffed=$resolvedMime " +
                        "(buffer preserved; no media reload)"
                )
                // Stay on sidecar path — do not wipe progressive buffer via setMediaSource.
                activeSidecarSubtitleKey = null
                activeSidecarGeneration = 0L
                sidecarTimedCues = emptyList()
                postToSubtitleView { view ->
                    view.setTag(R.id.player_view_sidecar_generation_tag, null)
                    showSidecarCues(view, emptyList())
                }
                return@launch
            }

            originalSidecarSubtitleKey = subtitleKey
            originalSidecarTimedCues = parseResult.cues
            sidecarTimedCues = parseResult.cues
            Log.d(
                PlayerRuntimeController.TAG,
                "Sidecar subtitle ready id=${subtitle.id} cues=${parseResult.cues.size} " +
                    "mime=${parseResult.effectiveMime} source=${parseResult.source} " +
                    "(buffer preserved)"
            )

            while (isActive && isCurrent()) {
                renderSidecarCuesAtCurrentPosition()
                delay(SIDECAR_RENDER_INTERVAL_MS)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isCurrent()) return@launch
            Log.w(
                PlayerRuntimeController.TAG,
                "Sidecar subtitle failed id=${subtitle.id}: ${e.message} " +
                    "(buffer preserved; no media reload)"
            )
            activeSidecarSubtitleKey = null
            activeSidecarGeneration = 0L
            sidecarTimedCues = emptyList()
            postToSubtitleView { view ->
                view.setTag(R.id.player_view_sidecar_generation_tag, null)
                showSidecarCues(view, emptyList())
            }
            // Intentionally no attachAddonSubtitleViaMediaReload — keeps A/V buffer intact.
        }
    }
    return true
}

/** Identifies one attachment of [url]; changes whenever the sidecar restarts or stops. */
internal fun PlayerRuntimeController.currentSidecarGenerationFor(url: String): Long? =
    activeSidecarGeneration.takeIf { activeSidecarSubtitleKey == url && it != 0L }

/**
 * Replaces the cues of the attached subtitle with already-parsed [cues], switching the
 * attachment to [newUrl], only if [expectedCurrentUrl] (and [expectedGeneration]) is still
 * the active attachment. Keeps the buffer and avoids re-downloading or re-parsing.
 */
internal fun PlayerRuntimeController.commitPreparedSidecarSubtitle(
    expectedCurrentUrl: String,
    newUrl: String,
    cues: List<CuesWithTiming>,
    expectedGeneration: Long? = null,
): Boolean {
    if (cues.isEmpty() || activeSidecarSubtitleKey != expectedCurrentUrl) return false
    if (expectedGeneration != null && activeSidecarGeneration != expectedGeneration) return false

    val urlChanged = newUrl != expectedCurrentUrl
    if (urlChanged) {
        sidecarSubtitleJob?.cancel()
        activeSidecarSubtitleKey = newUrl
        activeSidecarGeneration = ++sidecarGenerationCounter
    }

    val committedGeneration = activeSidecarGeneration
    sidecarTimedCues = cues
    lastSidecarCueSignature = null
    postToSubtitleView { view ->
        view.setTag(R.id.player_view_sidecar_generation_tag, newUrl)
    }
    renderSidecarCuesAtCurrentPosition()

    if (urlChanged) {
        sidecarSubtitleJob = scope.launch {
            while (
                isActive &&
                activeSidecarSubtitleKey == newUrl &&
                activeSidecarGeneration == committedGeneration
            ) {
                renderSidecarCuesAtCurrentPosition()
                delay(SIDECAR_RENDER_INTERVAL_MS)
            }
        }
    }
    return true
}

/**
 * Restores the un-retimed cues saved before AutoSync modified the active sidecar subtitle.
 * Returns true if original cues were restored.
 */
internal fun PlayerRuntimeController.revertSidecarAutoSyncTimeline(): Boolean {
    val activeKey = activeSidecarSubtitleKey ?: return false
    val originalKey = originalSidecarSubtitleKey ?: return false
    val originalCues = originalSidecarTimedCues
    if (originalCues.isEmpty() || activeKey != originalKey) return false
    if (sidecarTimedCues === originalCues) return false

    sidecarTimedCues = originalCues
    lastSidecarCueSignature = null
    AutoSyncSyncedSubtitle.clear()
    renderSidecarCuesAtCurrentPosition()
    return true
}

internal fun PlayerRuntimeController.renderSidecarCuesAtCurrentPosition() {
    val cues = sidecarTimedCues
    if (cues.isEmpty() || activeSidecarSubtitleKey == null) return
    val player = _exoPlayer ?: return
    val positionUs = (
        player.currentPosition.coerceAtLeast(0L) * 1_000L +
            audioDelayUs.get() -
            subtitleDelayUs.get()
        ).coerceAtLeast(0L)
    val prefixMaxEndUs = sidecarCuePrefixMaxEndUs
    val active = collectActiveSidecarCues(cues, positionUs, prefixMaxEndUs)
    val stripSdh = currentPlayerSettingsForReport.subtitleStyle.stripSdh
    // Sign before sanitising, filtering and merging to skip that work while cues are
    // unchanged. stripSdh is included because it changes what filtering removes.
    val signature = activeCueSignature(active, stripSdh)
    if (signature == lastSidecarCueSignature) return
    lastSidecarCueSignature = signature
    val sanitized = active.map { SubtitleMojibakeSanitizer.sanitizeCue(it) }
    val filtered = if (stripSdh) SubtitleSdhFilter.filterCues(sanitized) else sanitized
    val merged = PlayerSubtitleUtils.mergeOverlappingCues(filtered)
    // Skip timing-context & next-cue scan entirely when subtitle TTS is disabled.
    val timingContext = if (_uiState.value.ttsConfig.enabled) {
        val activeTimed = collectActiveSidecarTimedCues(cues, positionUs, prefixMaxEndUs)
        if (activeTimed.isNotEmpty()) {
            val earliestStartUs = activeTimed.minOf { it.startTimeUs }
            val earliestStartMs = earliestStartUs / 1_000L
            val latestEndUs = activeTimed.maxOf {
                if (it.durationUs != androidx.media3.common.C.TIME_UNSET) {
                    it.startTimeUs + it.durationUs
                } else {
                    it.startTimeUs + 2_000_000L
                }
            }
            val latestEndMs = latestEndUs / 1_000L
            val nextCueStartMs = findNextSidecarCueStartMs(cues, earliestStartUs, positionUs)
            val text = merged.mapNotNull { it.text?.toString()?.takeIf(String::isNotBlank) }.joinToString("\n")
            val speed = player.playbackParameters.speed
            com.nuvio.tv.core.tts.SubtitleTimelineTimingHelper.createTimingContext(
                rawText = text,
                startMs = earliestStartMs,
                endMs = latestEndMs,
                playbackSpeed = speed,
                nextCueStartMs = nextCueStartMs,
                positionMs = positionUs / 1_000L
            )
        } else {
            null
        }
    } else {
        null
    }

    val currentKey = activeSidecarSubtitleKey ?: return
    val currentGeneration = activeSidecarGeneration.takeIf { it != 0L } ?: return
    postToSubtitleView { view ->
        if (
            activeSidecarSubtitleKey == currentKey &&
            activeSidecarGeneration == currentGeneration &&
            view.getTag(R.id.player_view_sidecar_generation_tag) == currentKey
        ) {
            showSidecarCues(view, merged, timingContext)
        }
    }
}

private fun PlayerRuntimeController.showSidecarCues(
    view: SubtitleView,
    cues: List<Cue>,
    timingContext: com.nuvio.tv.core.tts.SubtitleCuePlaybackContext? = null
) {
    view.setCues(presentExoCues(cues))
    subtitleCueTracker.updateWithTiming(cues, timingContext)
}

private fun PlayerRuntimeController.postToSubtitleView(block: (SubtitleView) -> Unit) {
    val view = exoSubtitleViewRef?.get() ?: return
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block(view)
    } else {
        mainHandler.post {
            exoSubtitleViewRef?.get()?.let(block)
        }
    }
}

internal data class SidecarParseResult(
    val cues: List<CuesWithTiming>,
    val effectiveMime: String,
    val source: String
)

/**
 * Tries Media3 parsers across sniffed + common mime types, then a lenient SRT/VTT fallback.
 */
internal fun parseSidecarTimedCuesRobust(rawText: String, sourceUrl: String): SidecarParseResult {
    val cleaned = SubtitleMojibakeSanitizer.sanitize(rawText.replace("\uFEFF", "")).toString()
    val candidates = PlayerSubtitleUtils.sidecarMimeCandidates(cleaned, sourceUrl)
    for (mime in candidates) {
        val parsed = parseSidecarTimedCuesWithMime(cleaned, mime)
        if (parsed.isNotEmpty()) {
            val fixed = PlayerSubtitleRtlFix.fixTimedCues(parsed, isBuiltInSubtitle = false)
            val normalized = if (mime == MimeTypes.TEXT_VTT) normalizeTimedCuePositions(fixed) else fixed
            return SidecarParseResult(
                normalized,
                mime,
                source = "media3"
            )
        }
    }

    val lenient = parseSidecarTimedCuesLenient(cleaned, sourceUrl)
    if (lenient.isNotEmpty()) {
        val mime = PlayerSubtitleUtils.sniffSubtitleMimeType(cleaned, sourceUrl)
        val fixed = PlayerSubtitleRtlFix.fixTimedCues(lenient, isBuiltInSubtitle = false)
        val normalized = if (mime == MimeTypes.TEXT_VTT) normalizeTimedCuePositions(fixed) else fixed
        return SidecarParseResult(
            normalized,
            mime,
            source = "lenient"
        )
    }

    return SidecarParseResult(
        cues = emptyList(),
        effectiveMime = candidates.firstOrNull() ?: MimeTypes.APPLICATION_SUBRIP,
        source = "none"
    )
}

private fun normalizeTimedCuePositions(cues: List<CuesWithTiming>): List<CuesWithTiming> {
    return cues.map { entry ->
        val normalized = entry.cues.map { PlayerSubtitleUtils.normalizeBottomCuePosition(it) }
        val durationUs = when {
            entry.durationUs != C.TIME_UNSET -> entry.durationUs
            entry.endTimeUs != C.TIME_UNSET && entry.startTimeUs != C.TIME_UNSET ->
                (entry.endTimeUs - entry.startTimeUs).coerceAtLeast(1L)
            else -> C.TIME_UNSET
        }
        CuesWithTiming(normalized, entry.startTimeUs, durationUs)
    }
}

private fun parseSidecarTimedCuesWithMime(rawText: String, mimeType: String): List<CuesWithTiming> {
    val format = Format.Builder().setSampleMimeType(mimeType).build()
    if (!sidecarParserFactory.supportsFormat(format)) return emptyList()
    return try {
        val parser = sidecarParserFactory.create(format)
        val data = rawText.toByteArray(Charsets.UTF_8)
        val out = ArrayList<CuesWithTiming>(256)
        parser.parse(data, SubtitleParser.OutputOptions.allCues()) { cueGroup ->
            if (cueGroup.startTimeUs != C.TIME_UNSET) {
                out.add(cueGroup)
            }
        }
        out.sortBy { it.startTimeUs }
        out
    } catch (e: Exception) {
        Log.d(
            PlayerRuntimeController.TAG,
            "Sidecar Media3 parse failed mime=$mimeType: ${e.message}"
        )
        emptyList()
    }
}

/**
 * Lenient SRT/VTT path using [PlayerSubtitleCueParser] when Media3 rejects slightly malformed files.
 * Uses each cue's exact [SubtitleSyncCue.endTimeMs] (not stretched to the next cue start).
 */
internal fun parseSidecarTimedCuesLenient(rawText: String, sourceUrl: String): List<CuesWithTiming> {
    val syncCues = try {
        PlayerSubtitleCueParser.parseFromText(rawText, sourceUrl)
            .filter { it.text.isNotBlank() && it.startTimeMs >= 0L }
    } catch (_: Exception) {
        emptyList()
    }
    if (syncCues.isEmpty()) return emptyList()

    val out = ArrayList<CuesWithTiming>(syncCues.size)
    for (i in syncCues.indices) {
        val startUs = syncCues[i].startTimeMs * 1_000L
        val endUs = (syncCues[i].endTimeMs * 1_000L).coerceAtLeast(startUs + 1L)
        val durationUs = (endUs - startUs).coerceAtLeast(1L)
        val cue = Cue.Builder().setText(syncCues[i].text).build()
        out.add(CuesWithTiming(listOf(cue), startUs, durationUs))
    }
    // collectActiveSidecarCues stops at the first cue starting after the playhead, so the list
    // has to be ordered. PlayerSubtitleCueParser emits cues in file order.
    out.sortBy { it.startTimeUs }
    return out
}

private fun sidecarCueEndUs(entry: CuesWithTiming, defaultDurationIfUnsetUs: Long?): Long = when {
    defaultDurationIfUnsetUs != null && entry.durationUs != C.TIME_UNSET -> entry.startTimeUs + entry.durationUs
    entry.endTimeUs != C.TIME_UNSET -> entry.endTimeUs
    entry.durationUs != C.TIME_UNSET -> entry.startTimeUs + entry.durationUs
    defaultDurationIfUnsetUs != null -> entry.startTimeUs + defaultDurationIfUnsetUs
    else -> Long.MAX_VALUE
}

/**
 * Precomputes prefix maximum end timestamps for O(log N) active-cue interval lookups
 * over a [cues] list sorted by [CuesWithTiming.startTimeUs].
 */
internal fun buildSidecarPrefixMaxEndUs(cues: List<CuesWithTiming>): LongArray {
    if (cues.isEmpty()) return LongArray(0)
    val prefixMax = LongArray(cues.size)
    var runningMax = Long.MIN_VALUE
    for (i in cues.indices) {
        val endUs = maxOf(
            sidecarCueEndUs(cues[i], defaultDurationIfUnsetUs = null),
            sidecarCueEndUs(cues[i], defaultDurationIfUnsetUs = 2_000_000L)
        )
        if (endUs > runningMax) runningMax = endUs
        prefixMax[i] = runningMax
    }
    return prefixMax
}

/** Largest index `i` in [cues] where `cues[i].startTimeUs <= positionUs`, or -1 if none. */
private fun upperBoundStartIndex(cues: List<CuesWithTiming>, positionUs: Long): Int {
    var lo = 0
    var hi = cues.lastIndex
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (cues[mid].startTimeUs <= positionUs) {
            ans = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return ans
}

/**
 * Finds the start time (ms) of the next non-blank cue strictly after [earliestStartUs]
 * and at/after [positionUs] in O(log N) time.
 */
internal fun findNextSidecarCueStartMs(
    cues: List<CuesWithTiming>,
    earliestStartUs: Long,
    positionUs: Long
): Long? {
    if (cues.isEmpty()) return null
    val minStartUs = maxOf(earliestStartUs + 1L, positionUs)
    var lo = 0
    var hi = cues.size
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (cues[mid].startTimeUs < minStartUs) {
            lo = mid + 1
        } else {
            hi = mid
        }
    }
    for (i in lo until cues.size) {
        val candidate = cues[i]
        if (candidate.cues.any { !it.text.isNullOrBlank() }) {
            return candidate.startTimeUs / 1_000L
        }
    }
    return null
}

/** [cues] must be ordered by [CuesWithTiming.startTimeUs]. Uses O(log N) binary search when [prefixMaxEndUs] is provided. */
internal fun collectActiveSidecarTimedCues(
    cues: List<CuesWithTiming>,
    positionUs: Long,
    prefixMaxEndUs: LongArray? = null
): List<CuesWithTiming> {
    if (cues.isEmpty()) return emptyList()
    if (prefixMaxEndUs != null && prefixMaxEndUs.size == cues.size) {
        val hi = upperBoundStartIndex(cues, positionUs)
        if (hi < 0) return emptyList()
        val reversed = ArrayList<CuesWithTiming>(4)
        for (i in hi downTo 0) {
            if (prefixMaxEndUs[i] <= positionUs) break
            val entry = cues[i]
            val endUs = sidecarCueEndUs(entry, defaultDurationIfUnsetUs = 2_000_000L)
            if (positionUs < endUs) {
                reversed.add(entry)
            }
        }
        if (reversed.size > 1) reversed.reverse()
        return reversed
    }
    val out = ArrayList<CuesWithTiming>()
    for (entry in cues) {
        if (entry.startTimeUs > positionUs) break
        val endUs = sidecarCueEndUs(entry, defaultDurationIfUnsetUs = 2_000_000L)
        if (positionUs < endUs) {
            out.add(entry)
        }
    }
    return out
}

/** [cues] must be ordered by [CuesWithTiming.startTimeUs]. Uses O(log N) binary search when [prefixMaxEndUs] is provided. */
internal fun collectActiveSidecarCues(
    cues: List<CuesWithTiming>,
    positionUs: Long,
    prefixMaxEndUs: LongArray? = null
): List<Cue> {
    if (cues.isEmpty()) return emptyList()
    if (prefixMaxEndUs != null && prefixMaxEndUs.size == cues.size) {
        val hi = upperBoundStartIndex(cues, positionUs)
        if (hi < 0) return emptyList()
        val matchedIndices = ArrayList<Int>(4)
        for (i in hi downTo 0) {
            if (prefixMaxEndUs[i] <= positionUs) break
            val entry = cues[i]
            val end = sidecarCueEndUs(entry, defaultDurationIfUnsetUs = null)
            if (positionUs < end) {
                matchedIndices.add(i)
            }
        }
        if (matchedIndices.isEmpty()) return emptyList()
        val active = ArrayList<Cue>(matchedIndices.size)
        for (j in matchedIndices.lastIndex downTo 0) {
            active.addAll(cues[matchedIndices[j]].cues)
        }
        return active
    }
    val active = ArrayList<Cue>(4)
    for (entry in cues) {
        if (entry.startTimeUs > positionUs) break
        val end = sidecarCueEndUs(entry, defaultDurationIfUnsetUs = null)
        if (positionUs < end) {
            active.addAll(entry.cues)
        }
    }
    return active
}

internal fun activeCueSignature(cues: List<Cue>, stripSdh: Boolean): Long {
    if (cues.isEmpty()) return if (stripSdh) EMPTY_CUE_SIGNATURE_SDH else EMPTY_CUE_SIGNATURE
    var hash = cues.size.toLong()
    hash = 31L * hash + if (stripSdh) 1L else 0L
    for (cue in cues) {
        hash = 31L * hash + (cue.text?.hashCode()?.toLong() ?: 0L)
        hash = 31L * hash + cue.line.toBits().toLong()
        hash = 31L * hash + cue.position.toBits().toLong()
        hash = 31L * hash + cue.lineAnchor.toLong()
        hash = 31L * hash + cue.positionAnchor.toLong()
        hash = 31L * hash + cue.size.toBits().toLong()
    }
    return hash
}

private const val SIDECAR_RENDER_INTERVAL_MS = 100L
private const val EMPTY_CUE_SIGNATURE = 0x4E5556494FL // "NUVIO"
private const val EMPTY_CUE_SIGNATURE_SDH = 0x4E5556494F01L
