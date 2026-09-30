package com.nuvio.tv.core.torrent

import android.util.Base64
import android.util.Log
import com.nuvio.tv.core.network.IPv4FirstDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class TorrServerRemoteFile(
    val id: Int,
    val path: String,
    val length: Long
)

internal fun torrServerDisplayTitle(title: String?): String? =
    title?.trim()?.takeIf { it.isNotBlank() }?.let { "[NuvioF] $it" }

data class TorrServerRemoteStatus(
    val hash: String,
    val title: String?,
    val stat: Int,
    val statString: String?,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val preloadedBytes: Long,
    val preloadSize: Long,
    val loadedSize: Long,
    val torrentSize: Long,
    val activePeers: Int,
    val connectedSeeders: Int,
    val totalPeers: Int,
    val files: List<TorrServerRemoteFile>
) {
    val isPreloadReady: Boolean
        get() {
            val target = if (preloadSize > 0) preloadSize else if (torrentSize in 1..33_554_432L) torrentSize else 33_554_432L
            val buffered = if (preloadedBytes > 0) preloadedBytes else loadedSize

            // Reached target preload threshold (95%)
            if (target > 0 && buffered >= (target * 95 / 100)) {
                return true
            }

            // Explicitly in preloading or metadata getting state: not ready
            if (stat == 1 || stat == 2 || statString?.contains("preload", ignoreCase = true) == true) {
                return false
            }

            // If preloadSize is known and we have not buffered enough yet: not ready
            if (preloadSize > 0) {
                return false
            }

            // When server status is active / Torrent working, but preloadSize is unknown (0):
            // The server might have already been active right when we started polling.
            // Only consider ready if we have buffered a reasonable amount of data (>= 10MB or 95% of small file)
            if (stat == 3 || statString.equals("active", ignoreCase = true) || statString.equals("Torrent working", ignoreCase = true)) {
                val minReady = if (torrentSize in 1..10_485_760L) torrentSize * 95 / 100 else 10_485_760L
                return buffered >= minReady
            }

            return false
        }

    val preloadProgress: Float
        get() {
            if (isPreloadReady) return 1f
            val target = if (preloadSize > 0) preloadSize else if (torrentSize in 1..33_554_432L) torrentSize else 33_554_432L
            val buffered = if (preloadedBytes > 0) preloadedBytes else loadedSize
            return if (target > 0) (buffered.toFloat() / target).coerceIn(0f, 1f) else 0f
        }
}

@Singleton
class TorrServerRemoteApi @Inject constructor(
    private val addonConfig: TorrServerAddonConfig
) {
    companion object {
        private const val TAG = "TorrServerRemoteApi"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client = OkHttpClient.Builder()
        .dns(IPv4FirstDns())
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun addAuthHeader(builder: Request.Builder, user: String?, pass: String?) {
        if (!user.isNullOrBlank() || !pass.isNullOrBlank()) {
            val credentials = "${user.orEmpty()}:${pass.orEmpty()}"
            val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
            builder.addHeader("Authorization", "Basic $encoded")
        }
    }

    suspend fun newStreamCall(streamUrl: String): okhttp3.Call = withContext(Dispatchers.IO) {
        val config = addonConfig.config.first()
        val requestBuilder = Request.Builder().url(streamUrl).get()
        addAuthHeader(requestBuilder, config.authUsername, config.authPassword)
        client.newCall(requestBuilder.build())
    }

    suspend fun healthCheck(
        serverUrl: String? = null,
        username: String? = null,
        password: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = addonConfig.config.first()
        val targetUrl = (serverUrl ?: config.serverUrl).trim().trimEnd('/')
        val targetUser = username ?: config.authUsername
        val targetPass = password ?: config.authPassword

        if (targetUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }

        val requestBuilder = Request.Builder()
            .url("$targetUrl/echo")
            .get()
        addAuthHeader(requestBuilder, targetUser, targetPass)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val version = response.body.string().trim().ifEmpty { "Connected" }
                    Result.success(version)
                } else {
                    Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "healthCheck failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun addTorrent(
        magnetLink: String,
        title: String? = null,
        poster: String? = null,
        serverUrlOverride: String? = null,
        saveToDbOverride: Boolean? = null
    ): String? = withContext(Dispatchers.IO) {
        val config = addonConfig.config.first()
        val serverUrl = (serverUrlOverride ?: config.serverUrl).trim().trimEnd('/')
        if (serverUrl.isBlank()) return@withContext null
        val saveToDb = saveToDbOverride ?: config.saveToDb

        val body = JSONObject().apply {
            put("action", "add")
            put("link", magnetLink)
            put("save_to_db", saveToDb)
            if (!title.isNullOrBlank()) {
                put("title", title)
            }
            if (!poster.isNullOrBlank()) {
                put("poster", poster)
            }
        }

        val requestBuilder = Request.Builder()
            .url("$serverUrl/torrents")
            .post(body.toString().toRequestBody(JSON_TYPE))
        addAuthHeader(requestBuilder, config.authUsername, config.authPassword)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "addTorrent failed HTTP ${response.code}")
                    return@withContext null
                }
                val json = JSONObject(response.body.string().ifEmpty { "{}" })
                val hash = json.optString("hash", "")
                hash.ifEmpty { null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "addTorrent exception", e)
            null
        }
    }

    suspend fun getTorrentDetails(
        hash: String,
        serverUrlOverride: String? = null
    ): TorrServerRemoteStatus? = withContext(Dispatchers.IO) {
        val config = addonConfig.config.first()
        val serverUrl = (serverUrlOverride ?: config.serverUrl).trim().trimEnd('/')
        if (serverUrl.isBlank() || hash.isBlank()) return@withContext null

        val body = JSONObject().apply {
            put("action", "get")
            put("hash", hash)
        }

        val requestBuilder = Request.Builder()
            .url("$serverUrl/torrents")
            .post(body.toString().toRequestBody(JSON_TYPE))
        addAuthHeader(requestBuilder, config.authUsername, config.authPassword)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val rawBody = response.body.string()
                if (rawBody.isBlank()) return@withContext null
                val json = JSONObject(rawBody)

                val fileList = json.optJSONArray("file_stats") ?: JSONArray()
                val files = ArrayList<TorrServerRemoteFile>(fileList.length())
                for (i in 0 until fileList.length()) {
                    val f = fileList.optJSONObject(i) ?: continue
                    files.add(
                        TorrServerRemoteFile(
                            id = f.optInt("id", i + 1),
                            path = f.optString("path", ""),
                            length = f.optLong("length", 0L)
                        )
                    )
                }

                TorrServerRemoteStatus(
                    hash = json.optString("hash", hash),
                    title = json.optString("title", "").ifBlank { null },
                    stat = json.optInt("stat", 0),
                    statString = json.optString("stat_string", "").ifBlank { null },
                    downloadSpeed = json.optDouble("download_speed", 0.0).toLong(),
                    uploadSpeed = json.optDouble("upload_speed", 0.0).toLong(),
                    preloadedBytes = json.optLong("preloaded_bytes", 0L),
                    preloadSize = json.optLong("preload_size", 0L),
                    loadedSize = json.optLong("loaded_size", 0L),
                    torrentSize = json.optLong("torrent_size", 0L),
                    activePeers = json.optInt("active_peers", 0),
                    connectedSeeders = json.optInt("connected_seeders", 0),
                    totalPeers = json.optInt("total_peers", 0),
                    files = files
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "getTorrentDetails exception: ${e.message}")
            null
        }
    }

    suspend fun dropTorrent(
        hash: String,
        serverUrlOverride: String? = null
    ) = withContext(Dispatchers.IO) {
        val config = addonConfig.config.first()
        if (config.saveToDb) {
            // Keep torrent saved on server if saveToDb is enabled
            return@withContext
        }
        val serverUrl = (serverUrlOverride ?: config.serverUrl).trim().trimEnd('/')
        if (serverUrl.isBlank() || hash.isBlank()) return@withContext

        val body = JSONObject().apply {
            put("action", "drop")
            put("hash", hash)
        }

        val requestBuilder = Request.Builder()
            .url("$serverUrl/torrents")
            .post(body.toString().toRequestBody(JSON_TYPE))
        addAuthHeader(requestBuilder, config.authUsername, config.authPassword)

        try {
            client.newCall(requestBuilder.build()).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "dropTorrent exception: ${e.message}")
        }
    }

    fun buildStreamUrl(
        serverUrl: String,
        magnetLink: String,
        fileIdx: Int,
        preload: Boolean = false,
        save: Boolean = false,
        gst: Boolean = false,
        hash: String? = null
    ): String {
        val base = serverUrl.trim().trimEnd('/')
        if (gst && !hash.isNullOrBlank()) {
            return "$base/gst/$hash/master.m3u8?index=$fileIdx"
        }
        val encodedLink = URLEncoder.encode(magnetLink, "UTF-8")
        val sb = StringBuilder("$base/stream?link=$encodedLink&index=$fileIdx")
        if (preload) {
            sb.append("&preload")
        } else {
            sb.append("&play")
        }
        if (save) {
            sb.append("&save")
        }
        return sb.toString()
    }

    fun buildDirectStreamUrl(serverUrl: String, hash: String, fileIdx: Int): String {
        val base = serverUrl.trim().trimEnd('/')
        return "$base/play/$hash/$fileIdx"
    }

    fun buildGstStreamUrl(serverUrl: String, hash: String, fileIdx: Int): String {
        val base = serverUrl.trim().trimEnd('/')
        return "$base/gst/$hash/master.m3u8?index=$fileIdx"
    }

    suspend fun checkGStreamerSupport(
        serverUrl: String? = null,
        username: String? = null,
        password: String? = null
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val config = addonConfig.config.first()
        val targetUrl = (serverUrl ?: config.serverUrl).trim().trimEnd('/')
        val targetUser = username ?: config.authUsername
        val targetPass = password ?: config.authPassword

        if (targetUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Server URL is empty"))
        }

        val requestBuilder = Request.Builder()
            .url("$targetUrl/gst/settings")
            .get()
        addAuthHeader(requestBuilder, targetUser, targetPass)

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body.string()
                    val json = JSONObject(body)
                    val builtIn = json.optBoolean("built_in", false) || json.has("config")
                    Result.success(builtIn)
                } else {
                    Result.success(false)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "checkGStreamerSupport failed: ${e.message}")
            Result.failure(e)
        }
    }
}
