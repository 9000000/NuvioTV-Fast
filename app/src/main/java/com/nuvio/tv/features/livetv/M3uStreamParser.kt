package com.nuvio.tv.features.livetv

import com.nuvio.tv.core.network.DynamicHostFallback
import com.nuvio.tv.ui.screens.player.IptvHeaderProvider
import org.json.JSONObject
import java.io.BufferedReader
import java.net.URL

/**
 * Stream-based M3U parser: reads line-by-line from a BufferedReader.
 * Eliminates the need to hold the entire payload in memory.
 *
 * For backward compat, also accepts a raw String via [parseFromString].
 */
internal object M3uStreamParser {

    /**
     * Parse M3U channels from a [BufferedReader] stream.
     * Memory-efficient: only one line + the accumulated channel list is held at any time.
     */
    fun parse(
        reader: BufferedReader,
        playlist: LiveTvPlaylist? = null,
    ): List<LiveTvChannel> {
        val channels = mutableListOf<LiveTvChannel>()
        val playlistDefaultHeaders = mutableMapOf<String, String>()
        var firstChannelAdded = false
        var pending = PendingM3uEntry()

        reader.useLines { lines ->
            lines
                .map(String::trim)
                .filter(String::isNotBlank)
                .forEach { line ->
                    processLine(
                        line = line,
                        playlist = playlist,
                        channels = channels,
                        playlistDefaultHeaders = playlistDefaultHeaders,
                        firstChannelAdded = firstChannelAdded,
                        pending = pending,
                        onFirstChannelAdded = { firstChannelAdded = true },
                        onPendingReset = { newPending ->
                            pending = newPending
                            pending.headers.putAll(playlistDefaultHeaders)
                        }
                    )
                }
        }

        return channels.distinctBy { it.streamUrl }
    }

    /**
     * Backward-compatible: parse from a String payload.
     * Uses [String.lineSequence] which is already lazy.
     */
    fun parseFromString(
        payload: String,
        playlist: LiveTvPlaylist? = null,
    ): List<LiveTvChannel> {
        val channels = mutableListOf<LiveTvChannel>()
        val playlistDefaultHeaders = mutableMapOf<String, String>()
        var firstChannelAdded = false
        var pending = PendingM3uEntry()

        payload.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { line ->
                processLine(
                    line = line,
                    playlist = playlist,
                    channels = channels,
                    playlistDefaultHeaders = playlistDefaultHeaders,
                    firstChannelAdded = firstChannelAdded,
                    pending = pending,
                    onFirstChannelAdded = { firstChannelAdded = true },
                    onPendingReset = { newPending ->
                        pending = newPending
                        pending.headers.putAll(playlistDefaultHeaders)
                    }
                )
            }

        return channels.distinctBy { it.streamUrl }
    }

    // ─── Core line processing (shared between stream and string paths) ───

    private fun processLine(
        line: String,
        playlist: LiveTvPlaylist?,
        channels: MutableList<LiveTvChannel>,
        playlistDefaultHeaders: MutableMap<String, String>,
        firstChannelAdded: Boolean,
        pending: PendingM3uEntry,
        onFirstChannelAdded: () -> Unit,
        onPendingReset: (PendingM3uEntry) -> Unit,
    ) {
        when {
            line.startsWith("#EXTM3U", ignoreCase = true) -> {
                val ua = readM3uAttribute(line, "http-user-agent") ?: readM3uAttribute(line, "user-agent")
                if (!ua.isNullOrBlank()) playlistDefaultHeaders["User-Agent"] = ua
                val ref = readM3uAttribute(line, "http-referrer") ?: readM3uAttribute(line, "referrer") ?: readM3uAttribute(line, "referer")
                if (!ref.isNullOrBlank()) playlistDefaultHeaders["Referer"] = ref
            }
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                val info = parseExtInf(line)
                pending.info = info
                info.licenseType?.let { pending.licenseType = it }
                info.licenseKey?.let { pending.licenseKey = it }
                info.manifestType?.let { pending.manifestType = it }
                info.group?.let { pending.group = it }
                pending.headers.putAll(info.headers)
            }
            line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                val grp = line.substringAfter("#EXTGRP:", "").trim()
                if (grp.isNotBlank()) {
                    pending.group = grp
                }
            }
            line.startsWith("#KODIPROP:", ignoreCase = true) ||
            line.startsWith("#EXT-X-KODI:", ignoreCase = true) ||
            line.startsWith("#EXT-X-KODIPROP:", ignoreCase = true) ||
            line.startsWith("#EXTKODI:", ignoreCase = true) -> {
                processKodiProp(line, pending)
            }
            line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                processExtXKey(line, pending)
            }
            line.startsWith("#EXTHTTP:", ignoreCase = true) -> {
                processExtHttp(line, pending)
            }
            line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                processVlcOpt(line, pending, playlistDefaultHeaders, firstChannelAdded)
            }
            line.startsWith("#") -> Unit
            else -> {
                if (isM3uNoiseOrDivider(line) || !isValidStreamUrl(line)) {
                    return
                }
                val channel = buildChannel(line, pending, playlist, playlistDefaultHeaders)
                if (channel != null) {
                    channels += channel
                    if (!firstChannelAdded) onFirstChannelAdded()
                    onPendingReset(PendingM3uEntry())
                }
            }
        }
    }

    private fun buildChannel(
        line: String,
        pending: PendingM3uEntry,
        playlist: LiveTvPlaylist?,
        playlistDefaultHeaders: Map<String, String>,
    ): LiveTvChannel? {
        val (rawUrl, pipeHeaders) = parseUrlAndPipeHeaders(line)
        val (cleanLicenseKey, licensePipeHeaders) = pending.licenseKey?.let { parseUrlAndPipeHeaders(it) }
            ?: (null to emptyMap())

        val info = pending.info
        val playlistHost = playlist?.source?.let { runCatching { URL(it).host }.getOrNull() }
        val streamUrl = DynamicHostFallback.normalizeUrlWithFallback(rawUrl, preferredFallbackHost = playlistHost)
        val name = info?.name?.takeIf(String::isNotBlank)
            ?: streamUrl.substringAfterLast('/').substringBefore('?').ifBlank { "Channel" }

        val effectiveManifestType = (pending.manifestType ?: info?.manifestType)?.trim()?.lowercase()
        val detectedStreamType = detectStreamType(effectiveManifestType, streamUrl)

        val rawDrmType = (pending.licenseType ?: info?.licenseType)?.trim()?.lowercase()
        val effectiveKey = cleanLicenseKey ?: info?.licenseKey
        val detectedDrmType = detectDrmType(rawDrmType, effectiveKey)

        val combinedHeaders = buildMap {
            putAll(playlistDefaultHeaders)
            putAll(pending.headers)
            putAll(pipeHeaders)
            putAll(licensePipeHeaders)
            if (!containsKey("User-Agent")) {
                if (detectedDrmType != null || detectedStreamType == "mpd" || IptvHeaderProvider.isIptvStream(streamUrl)) {
                    put("User-Agent", IptvHeaderProvider.DEFAULT_IPTV_USER_AGENT)
                }
            }
        }

        val group = pending.group ?: info?.group?.takeIf(String::isNotBlank)

        return LiveTvChannel(
            id = stableChannelId(playlist?.id, streamUrl),
            name = name,
            streamUrl = streamUrl,
            logoUrl = info?.logoUrl?.takeIf(String::isNotBlank),
            group = group,
            playlistId = playlist?.id,
            playlistName = playlist?.name,
            headers = combinedHeaders,
            streamType = detectedStreamType,
            drmType = detectedDrmType,
            drmKey = effectiveKey,
        )
    }

    // ─── Directive processors ───

    private fun processKodiProp(line: String, pending: PendingM3uEntry) {
        val kodiProp = extractKodiProp(line) ?: return
        val (key, value) = kodiProp
        when {
            key.equals("inputstream.adaptive.license_type", ignoreCase = true) ||
            key.equals("license_type", ignoreCase = true) -> {
                pending.licenseType = value
            }
            key.equals("inputstream.adaptive.license_key", ignoreCase = true) ||
            key.equals("license_key", ignoreCase = true) -> {
                pending.licenseKey = value
            }
            key.equals("inputstream.adaptive.manifest_type", ignoreCase = true) ||
            key.equals("manifest_type", ignoreCase = true) -> {
                pending.manifestType = value
            }
            key.equals("inputstream.adaptive.stream_headers", ignoreCase = true) ||
            key.equals("stream_headers", ignoreCase = true) -> {
                value.split('&').forEach { param ->
                    val hKey = param.substringBefore('=').trim()
                    val hVal = param.substringAfter('=', "").trim()
                    if (hKey.isNotBlank() && hVal.isNotBlank()) {
                        pending.headers[normalizeHeaderKey(hKey)] = hVal
                    }
                }
            }
        }
    }

    private fun processExtXKey(line: String, pending: PendingM3uEntry) {
        val keyFormat = readM3uAttribute(line, "KEYFORMAT")?.lowercase().orEmpty()
        val uri = readM3uAttribute(line, "URI")
        if (!uri.isNullOrBlank()) {
            pending.licenseKey = uri
            when {
                keyFormat.contains("widevine") || keyFormat.contains("edef8ba9") -> pending.licenseType = "widevine"
                keyFormat.contains("clearkey") || keyFormat.contains("1077efec") || keyFormat == "identity" -> pending.licenseType = "clearkey"
                keyFormat.contains("playready") || keyFormat.contains("9a04f079") -> pending.licenseType = "playready"
            }
        }
    }

    private fun processExtHttp(line: String, pending: PendingM3uEntry) {
        val jsonStr = line.substringAfter("#EXTHTTP:", "").trim()
        runCatching {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = json.optString(k)
                if (v.isNotBlank()) {
                    pending.headers[normalizeHeaderKey(k)] = v
                }
            }
        }
    }

    private fun processVlcOpt(
        line: String,
        pending: PendingM3uEntry,
        playlistDefaultHeaders: MutableMap<String, String>,
        firstChannelAdded: Boolean,
    ) {
        val opt = line.substringAfter("#EXTVLCOPT:", "").trim()
        val key = opt.substringBefore('=').trim()
        val value = opt.substringAfter('=', "").trim()
        when {
            key.equals("http-user-agent", ignoreCase = true) || key.equals("user-agent", ignoreCase = true) -> {
                pending.headers["User-Agent"] = value
                if (!firstChannelAdded) playlistDefaultHeaders["User-Agent"] = value
            }
            key.equals("http-referrer", ignoreCase = true) || key.equals("referrer", ignoreCase = true) || key.equals("referer", ignoreCase = true) -> {
                pending.headers["Referer"] = value
                if (!firstChannelAdded) playlistDefaultHeaders["Referer"] = value
            }
            key.equals("http-origin", ignoreCase = true) || key.equals("origin", ignoreCase = true) -> {
                pending.headers["Origin"] = value
                if (!firstChannelAdded) playlistDefaultHeaders["Origin"] = value
            }
        }
    }

    // ─── Detection helpers ───

    private fun detectStreamType(manifestType: String?, streamUrl: String): String? = when {
        manifestType == "mpd" || streamUrl.contains(".mpd", ignoreCase = true) -> "mpd"
        manifestType == "hls" || manifestType == "m3u8" || streamUrl.contains(".m3u8", ignoreCase = true) -> "m3u8"
        manifestType == "ism" || manifestType == "isml" || streamUrl.contains(".ism", ignoreCase = true) -> "ism"
        manifestType == "flv" || streamUrl.contains(".flv", ignoreCase = true) -> "flv"
        manifestType == "ts" || streamUrl.contains(".ts", ignoreCase = true) -> "ts"
        manifestType == "mp4" || streamUrl.contains(".mp4", ignoreCase = true) -> "mp4"
        manifestType == "mkv" || streamUrl.contains(".mkv", ignoreCase = true) -> "mkv"
        DynamicHostFallback.isDynamicLiveStreamUrl(streamUrl) -> "m3u8"
        else -> null
    }

    private fun detectDrmType(rawDrmType: String?, effectiveKey: String?): String? = when {
        rawDrmType != null -> when {
            rawDrmType.contains("clearkey") || rawDrmType == "org.w3.clearkey" -> "clearkey"
            rawDrmType.contains("widevine") || rawDrmType == "com.widevine.alpha" -> "widevine"
            rawDrmType.contains("playready") || rawDrmType == "com.microsoft.playready" -> "playready"
            else -> rawDrmType
        }
        !effectiveKey.isNullOrBlank() -> {
            if (effectiveKey.startsWith("http://", ignoreCase = true) ||
                effectiveKey.startsWith("https://", ignoreCase = true)) "widevine" else "clearkey"
        }
        else -> null
    }

    private fun normalizeHeaderKey(key: String): String = when (key.lowercase()) {
        "user-agent" -> "User-Agent"
        "referer" -> "Referer"
        "origin" -> "Origin"
        "authorization" -> "Authorization"
        else -> key
    }
}

// ─── Shared M3U utilities (kept package-private for LiveTvRepository backward compat) ───

private data class PendingM3uEntry(
    var info: M3uInfo? = null,
    val headers: MutableMap<String, String> = mutableMapOf(),
    var licenseType: String? = null,
    var licenseKey: String? = null,
    var manifestType: String? = null,
    var group: String? = null,
)

private data class M3uInfo(
    val name: String,
    val logoUrl: String?,
    val group: String?,
    val licenseType: String? = null,
    val licenseKey: String? = null,
    val manifestType: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

private fun parseExtInf(line: String): M3uInfo {
    val name = line.substringAfter(',', missingDelimiterValue = "")
        .trim()
        .ifBlank {
            readM3uAttribute(line, "tvg-name").orEmpty()
        }
    val logoUrl = readM3uAttribute(line, "tvg-logo") ?: readM3uAttribute(line, "logo")
    val group = readM3uAttribute(line, "group-title") ?: readM3uAttribute(line, "group")

    val inlineLicenseType = readM3uAttribute(line, "license_type")
        ?: readM3uAttribute(line, "drm_type")
        ?: readM3uAttribute(line, "kodi-license-type")
        ?: readM3uAttribute(line, "inputstream.adaptive.license_type")

    val inlineLicenseKey = readM3uAttribute(line, "license_key")
        ?: readM3uAttribute(line, "drm_key")
        ?: readM3uAttribute(line, "kodi-license-key")
        ?: readM3uAttribute(line, "inputstream.adaptive.license_key")

    val inlineManifestType = readM3uAttribute(line, "manifest_type")
        ?: readM3uAttribute(line, "stream_type")
        ?: readM3uAttribute(line, "inputstream.adaptive.manifest_type")

    val inlineHeaders = mutableMapOf<String, String>()
    (readM3uAttribute(line, "http-user-agent") ?: readM3uAttribute(line, "user-agent"))
        ?.takeIf(String::isNotBlank)?.let { inlineHeaders["User-Agent"] = it }
    (readM3uAttribute(line, "http-referrer") ?: readM3uAttribute(line, "referrer") ?: readM3uAttribute(line, "referer"))
        ?.takeIf(String::isNotBlank)?.let { inlineHeaders["Referer"] = it }
    (readM3uAttribute(line, "http-origin") ?: readM3uAttribute(line, "origin"))
        ?.takeIf(String::isNotBlank)?.let { inlineHeaders["Origin"] = it }

    return M3uInfo(
        name = name,
        logoUrl = logoUrl,
        group = group,
        licenseType = inlineLicenseType,
        licenseKey = inlineLicenseKey,
        manifestType = inlineManifestType,
        headers = inlineHeaders,
    )
}

internal fun readM3uAttribute(line: String, key: String): String? {
    var searchIndex = 0
    val keyLen = key.length
    while (searchIndex < line.length) {
        val idx = line.indexOf(key, startIndex = searchIndex, ignoreCase = true)
        if (idx < 0) return null

        val isWordBoundaryBefore = idx == 0 ||
            line[idx - 1].isWhitespace() ||
            line[idx - 1] == '#' ||
            line[idx - 1] == ',' ||
            line[idx - 1] == ':'

        if (!isWordBoundaryBefore) {
            searchIndex = idx + keyLen
            continue
        }

        var afterKey = idx + keyLen
        while (afterKey < line.length && line[afterKey].isWhitespace()) {
            afterKey++
        }
        if (afterKey >= line.length || line[afterKey] != '=') {
            searchIndex = idx + keyLen
            continue
        }

        var valueStart = afterKey + 1
        while (valueStart < line.length && line[valueStart].isWhitespace()) {
            valueStart++
        }
        if (valueStart >= line.length) return null

        val quote = line[valueStart]
        return if (quote == '"' || quote == '\'') {
            val valueEnd = line.indexOf(quote, startIndex = valueStart + 1)
            if (valueEnd >= 0) {
                line.substring(valueStart + 1, valueEnd).trim()
            } else {
                null
            }
        } else {
            var end = valueStart
            while (end < line.length && !line[end].isWhitespace() && line[end] != ',') {
                end++
            }
            line.substring(valueStart, end).trim().takeIf { it.isNotEmpty() }
        }
    }
    return null
}

private fun extractKodiProp(line: String): Pair<String, String>? {
    val prefix = when {
        line.startsWith("#KODIPROP:", ignoreCase = true) -> "#KODIPROP:"
        line.startsWith("#EXT-X-KODI:", ignoreCase = true) -> "#EXT-X-KODI:"
        line.startsWith("#EXT-X-KODIPROP:", ignoreCase = true) -> "#EXT-X-KODIPROP:"
        line.startsWith("#EXTKODI:", ignoreCase = true) -> "#EXTKODI:"
        else -> return null
    }
    val prop = line.substringAfter(prefix, "").trim()
    val key = prop.substringBefore('=').trim()
    val value = prop.substringAfter('=', "").trim()
    return if (key.isNotEmpty()) key to value else null
}

private fun isM3uNoiseOrDivider(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return true
    if (trimmed.startsWith("//")) return true

    val nonDividerChars = trimmed.count { char ->
        char != '=' && char != '-' && char != '_' && char != '*' &&
            char != '~' && char != '<' && char != '>' && char != '#' &&
            char != '|' && char != '/' && char != '\\' && !char.isWhitespace()
    }
    if (nonDividerChars == 0) return true

    if (!trimmed.contains("://") && (trimmed.contains("====") || trimmed.contains("----") || trimmed.contains("____"))) {
        return true
    }
    return false
}

private fun isValidStreamUrl(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("rtmp://", ignoreCase = true) ||
        trimmed.startsWith("rtsp://", ignoreCase = true) ||
        trimmed.startsWith("udp://", ignoreCase = true) ||
        trimmed.startsWith("rtp://", ignoreCase = true) ||
        trimmed.startsWith("mms://", ignoreCase = true)
    ) {
        return true
    }
    if (trimmed.contains("://")) return true
    val lower = trimmed.lowercase()
    return lower.endsWith(".m3u8") || lower.endsWith(".mpd") || lower.endsWith(".ts") ||
        lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".ism") || lower.endsWith(".flv")
}

private fun parseUrlAndPipeHeaders(line: String): Pair<String, Map<String, String>> {
    if (!line.contains('|')) return line to emptyMap()
    val url = line.substringBefore('|').trim()
    val rawHeaders = line.substringAfter('|').trim()
    val headers = mutableMapOf<String, String>()
    rawHeaders.split('&').forEach { param ->
        val key = param.substringBefore('=').trim()
        val value = param.substringAfter('=', "").trim()
        if (key.isNotBlank() && value.isNotBlank()) {
            val normalizedKey = when (key.lowercase()) {
                "user-agent" -> "User-Agent"
                "referer" -> "Referer"
                "origin" -> "Origin"
                "authorization" -> "Authorization"
                else -> key
            }
            headers[normalizedKey] = value
        }
    }
    return url to headers
}

private fun stableChannelId(playlistId: String?, url: String): String {
    val prefix = playlistId?.takeIf(String::isNotBlank) ?: "m3u"
    return "channel_${prefix}_${url.hashCode()}"
}

/**
 * Backward-compatible parse function for tests and legacy callers.
 */
internal fun parseM3uPlaylist(
    payload: String,
    playlist: LiveTvPlaylist? = null
): List<LiveTvChannel> = M3uStreamParser.parseFromString(payload, playlist)
