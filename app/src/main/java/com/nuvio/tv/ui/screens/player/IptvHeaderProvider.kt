package com.nuvio.tv.ui.screens.player

import android.net.Uri
import java.util.Locale

/**
 * Provides automatic default headers for IPTV streams that require them.
 * Many IPTV servers require User-Agent and Referer headers for access control.
 */
object IptvHeaderProvider {

    /**
     * Default User-Agent that mimics VLC player for maximum compatibility
     */
    private const val DEFAULT_IPTV_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

    /**
     * Known IPTV patterns that typically require headers
     */
    private val KNOWN_IPTV_PATTERNS = listOf(
        // Common IPTV patterns
        "iptv",
        "/live/",
        "/hls/",
        "/stream/",
        ".ts", // Transport Stream files
        ".m3u8" // HLS playlists
    )

    /**
     * Checks if a URL appears to be an IPTV stream based on known patterns
     */
    fun isIptvStream(url: String): Boolean {
        if (url.isBlank()) return false
        
        val urlLower = url.lowercase(Locale.ROOT)
        
        // Check against known patterns
        return KNOWN_IPTV_PATTERNS.any { pattern ->
            urlLower.contains(pattern.lowercase(Locale.ROOT))
        }
    }

    /**
     * Gets default headers for IPTV streams.
     * Returns empty map if URL doesn't appear to be IPTV.
     */
    fun getDefaultHeaders(url: String): Map<String, String> {
        if (!isIptvStream(url)) return emptyMap()
        
        return buildMap {
            put("User-Agent", DEFAULT_IPTV_USER_AGENT)
            
            // Try to extract base domain for Referer
            val referer = extractBaseUrl(url)
            if (referer != null) {
                put("Referer", referer)
            }
        }
    }

    /**
     * Merges provided headers with default IPTV headers.
     * User-provided headers take precedence over defaults.
     * 
     * @param url The stream URL
     * @param userHeaders Headers provided by the user/playlist
     * @return Merged headers map
     */
    fun mergeWithDefaults(url: String, userHeaders: Map<String, String>): Map<String, String> {
        // If user already provided headers, trust them
        if (userHeaders.isNotEmpty()) return userHeaders
        
        // Only add defaults for IPTV streams with empty headers
        if (!isIptvStream(url)) return emptyMap()
        
        return getDefaultHeaders(url)
    }

    /**
     * Extracts base URL (scheme + host) for use as Referer
     * Example: https://example.com/path/live/stream.ts -> https://example.com/
     */
    private fun extractBaseUrl(url: String): String? {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme ?: return null
            val host = uri.host ?: return null
            "$scheme://$host/"
        } catch (e: Exception) {
            null
        }
    }
}
