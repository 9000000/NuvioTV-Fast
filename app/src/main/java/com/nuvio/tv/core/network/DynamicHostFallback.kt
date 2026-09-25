package com.nuvio.tv.core.network

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal dynamic host & stream resolution helper.
 * Zero hardcoded domains: dynamically associates working hosts by root domain
 * and identifies dynamic live gateway streams.
 */
object DynamicHostFallback {
    private val workingHostsByRootDomain = ConcurrentHashMap<String, String>()

    /**
     * Registers a known working host (e.g. from a successfully loaded playlist URL).
     */
    fun registerWorkingHost(urlOrHost: String) {
        val host = runCatching {
            if (urlOrHost.contains("://")) URI(urlOrHost).host else urlOrHost
        }.getOrNull()?.lowercase()?.trim() ?: return

        val root = extractRootDomain(host)
        if (root.isNotBlank()) {
            workingHostsByRootDomain[root] = host
        }
    }

    /**
     * Finds a known working fallback host sharing the same root domain.
     */
    fun getFallbackHost(deadHost: String): String? {
        val normalized = deadHost.lowercase().trim()
        val root = extractRootDomain(normalized)
        if (root.isBlank()) return null
        val candidate = workingHostsByRootDomain[root]
        return if (candidate != null && !candidate.equals(normalized, ignoreCase = true)) candidate else null
    }

    /**
     * Normalizes stream URL: if the stream host shares the root domain with a known working host
     * or the given [preferredFallbackHost], replaces the host with the working one.
     */
    fun normalizeUrlWithFallback(url: String, preferredFallbackHost: String? = null): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return url
        val host = uri.host ?: return url

        val targetHost = if (!preferredFallbackHost.isNullOrBlank() && !preferredFallbackHost.equals(host, ignoreCase = true)) {
            val streamRoot = extractRootDomain(host)
            val fallbackRoot = extractRootDomain(preferredFallbackHost)
            if (streamRoot.isNotBlank() && streamRoot.equals(fallbackRoot, ignoreCase = true)) {
                preferredFallbackHost
            } else {
                getFallbackHost(host)
            }
        } else {
            getFallbackHost(host)
        } ?: return url

        return if (!host.equals(targetHost, ignoreCase = true)) {
            url.replaceFirst(host, targetHost)
        } else {
            url
        }
    }

    /**
     * Extracts effective root domain from a hostname without hardcoded domain lists.
     * e.g., "vpsttt.vietanhtv.top" -> "vietanhtv.top"
     * e.g., "stream.server.com.vn" -> "server.com.vn"
     */
    fun extractRootDomain(host: String): String {
        val parts = host.lowercase().trim().split('.')
        if (parts.size <= 2) return host
        val commonSecondLevel = setOf("co", "com", "net", "org", "edu", "gov", "ac", "biz", "info")
        return if (parts.size >= 3 && commonSecondLevel.contains(parts[parts.size - 2])) {
            parts.takeLast(3).joinToString(".")
        } else {
            parts.takeLast(2).joinToString(".")
        }
    }

    /**
     * Checks if a stream URL is a dynamic live stream gateway (e.g. PHP/ASHX script, query token redirect)
     * rather than a static progressive video file (.mp4, .mkv).
     */
    fun isDynamicLiveStreamUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val cleanUrl = url.substringBefore('#')
        val path = cleanUrl.substringBefore('?')
        val fileName = path.substringAfterLast('/')
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()

        val staticVideoExtensions = setOf(
            "mp4", "mkv", "avi", "webm", "flv", "mov", "wmv", "3gp", "m4v", "mpg", "mpeg"
        )
        if (staticVideoExtensions.contains(extension)) {
            return false
        }

        val dynamicScriptExtensions = setOf("php", "ashx", "asp", "aspx", "jsp", "cgi", "do", "action")
        if (dynamicScriptExtensions.contains(extension)) {
            return true
        }

        val query = cleanUrl.substringAfter('?', "")
        if (query.isNotBlank() && (
                query.contains("id=") || query.contains("stream=") ||
                query.contains("channel=") || query.contains("live=") ||
                query.contains("play=") || query.contains("auth_key=") ||
                query.contains("txSecret=")
            )
        ) {
            return true
        }

        return extension.isBlank() || path.contains("/live/") || path.contains("/play/") || path.contains("/stream/")
    }
}
