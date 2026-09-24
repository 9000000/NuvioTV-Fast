package com.nuvio.tv.ui.screens.player

import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal object ClearKeyUtil {
    private const val TAG = "ClearKeyUtil"

    /**
     * Normalizes a raw DRM key string into a standard W3C ClearKey JWK JSON payload.
     * Supported inputs:
     * 1. Already standard JWK JSON: `{"keys":[{"kty":"oct","k":"...","kid":"..."}],"type":"temporary"}`
     * 2. Key-value JSON: `{"<kid>": "<key>"}`
     * 3. Delimited pairs (hex or base64): `kid:key` or `kid1:key1,kid2:key2`
     */
    fun normalizeToJwkJson(rawDrmKey: String?): String? {
        if (rawDrmKey.isNullOrBlank()) return null
        val trimmed = rawDrmKey.trim()

        // Case 1: Already JSON
        if (trimmed.startsWith("{")) {
            return runCatching {
                val json = JSONObject(trimmed)
                if (json.has("keys")) {
                    if (!json.has("type")) {
                        json.put("type", "temporary")
                    }
                    json.toString()
                } else {
                    val keysArray = JSONArray()
                    val it = json.keys()
                    while (it.hasNext()) {
                        val kid = it.next()
                        val key = json.getString(kid)
                        val keyObj = buildJwkKeyEntry(kid, key)
                        if (keyObj != null) {
                            keysArray.put(keyObj)
                        }
                    }
                    if (keysArray.length() > 0) {
                        JSONObject().apply {
                            put("keys", keysArray)
                            put("type", "temporary")
                        }.toString()
                    } else {
                        trimmed
                    }
                }
            }.getOrElse {
                Log.w(TAG, "Failed to parse JSON ClearKey, fallback to raw", it)
                trimmed
            }
        }

        // Case 2: Delimited pairs: kid:key or kid1:key1,kid2:key2 or kid1:key1 kid2:key2
        val pairs = trimmed.split(Regex("[,;\\s]+")).filter { it.contains(':') }
        if (pairs.isNotEmpty()) {
            return runCatching {
                val keysArray = JSONArray()
                for (pair in pairs) {
                    val parts = pair.split(':')
                    if (parts.size == 2) {
                        val kid = parts[0].trim()
                        val key = parts[1].trim()
                        val keyObj = buildJwkKeyEntry(kid, key)
                        if (keyObj != null) {
                            keysArray.put(keyObj)
                        }
                    }
                }
                if (keysArray.length() > 0) {
                    JSONObject().apply {
                        put("keys", keysArray)
                        put("type", "temporary")
                    }.toString()
                } else null
            }.getOrNull()
        }

        // Case 3: URL containing embedded id parameter (e.g. key.php?id=kid:key)
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val queryId = runCatching {
                android.net.Uri.parse(trimmed).getQueryParameter("id")
            }.getOrNull()
            if (!queryId.isNullOrBlank() && queryId.contains(':')) {
                val parsed = normalizeToJwkJson(queryId)
                if (parsed != null) return parsed
            }
        }

        return null
    }

    /**
     * Fetches ClearKey JWK JSON from an HTTP URL endpoint (e.g. cleankey.php).
     */
    fun fetchClearKeyJson(url: String, headers: Map<String, String> = emptyMap()): String? {
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return null
        }

        // Fast path: embedded id parameter
        val queryId = runCatching {
            android.net.Uri.parse(url).getQueryParameter("id")
        }.getOrNull()
        if (!queryId.isNullOrBlank() && queryId.contains(':')) {
            val parsed = normalizeToJwkJson(queryId)
            if (parsed != null) return parsed
        }

        return runCatching {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.instanceFollowRedirects = true
            val effectiveUa = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                ?: IptvHeaderProvider.DEFAULT_IPTV_USER_AGENT
            conn.setRequestProperty("User-Agent", effectiveUa)
            headers.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true)) {
                    conn.setRequestProperty(k, v)
                }
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                normalizeToJwkJson(body)
            } else {
                Log.w(TAG, "ClearKey URL returned HTTP ${conn.responseCode}: $url")
                null
            }
        }.getOrElse {
            Log.w(TAG, "Failed to fetch ClearKey from URL: $url", it)
            null
        }
    }

    private fun buildJwkKeyEntry(rawKid: String, rawKey: String): JSONObject? {
        val kidB64 = toBase64Url(rawKid) ?: return null
        val keyB64 = toBase64Url(rawKey) ?: return null
        return JSONObject().apply {
            put("kty", "oct")
            put("kid", kidB64)
            put("k", keyB64)
        }
    }

    private fun toBase64Url(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null

        // Check if value is Hex (e.g. 32-char hex string)
        if (isHex(trimmed)) {
            val bytes = hexToByteArray(trimmed) ?: return null
            return Base64.encodeToString(
                bytes,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
        }

        // Check if value is already Base64
        return runCatching {
            val bytes = Base64.decode(trimmed, Base64.DEFAULT)
            Base64.encodeToString(
                bytes,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
        }.getOrNull()
    }

    private fun isHex(value: String): Boolean {
        val clean = value.replace("-", "")
        if (clean.length % 2 != 0 || clean.isEmpty()) return false
        return clean.all { c ->
            c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
        }
    }

    private fun hexToByteArray(hex: String): ByteArray? {
        val clean = hex.replace("-", "")
        if (clean.length % 2 != 0) return null
        return ByteArray(clean.length / 2).apply {
            for (i in indices) {
                val index = i * 2
                val byteValue = clean.substring(index, index + 2).toIntOrNull(16) ?: return null
                this[i] = byteValue.toByte()
            }
        }
    }
}
