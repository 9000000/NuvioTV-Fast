package com.nuvio.tv.features.livetv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Service to automatically download, parse, and match XMLTV EPG data
 * for IPTV channels, saving them into EpgDatabase.
 */
object LiveTvEpgSyncService {
    private const val TAG = "LiveTvEpgSyncService"

    /**
     * Probes playlists for EPG URLs (even from cached sources) and syncs all discovered EPG data.
     */
    suspend fun syncAll(
        playlists: List<LiveTvPlaylist>,
        channels: List<LiveTvChannel>,
        epgDatabase: EpgDatabase,
        onComplete: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext

        val urls = mutableSetOf<String>()
        var needPersist = false

        val customEpg = runCatching { LiveTvStorage.loadLiveTvSettings().customEpgUrl.trim() }.getOrDefault("")
        if (customEpg.isNotBlank()) {
            urls.add(customEpg)
        }

        playlists.forEach { pl ->
            val direct = pl.epgUrl?.trim().orEmpty()
            if (direct.isNotBlank()) {
                urls.add(direct)
            } else if (pl.type == LiveTvPlaylistType.Url && pl.isEnabled && pl.source.isNotBlank()) {
                val probed = probeEpgUrlFromPlaylist(pl.source)
                if (!probed.isNullOrBlank()) {
                    pl.epgUrl = probed
                    urls.add(probed)
                    needPersist = true
                    Log.d(TAG, "Discovered EPG URL for playlist ${pl.name}: $probed")
                }
            }
        }

        if (needPersist) {
            runCatching { LiveTvRepository.persistPlaylists(playlists) }
        }

        if (urls.isEmpty()) {
            Log.d(TAG, "No EPG URLs found in playlists")
            return@withContext
        }

        urls.forEach { epgUrl ->
            syncEpg(epgUrl, channels, epgDatabase, onComplete)
        }
    }

    private fun probeEpgUrlFromPlaylist(playlistUrl: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(playlistUrl)
                .header("Range", "bytes=0-8192")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) NuvioTV/1.0")
                .build()
            val resp = liveTvHttpClient.newCall(req).execute()
            if (!resp.isSuccessful && resp.code != 206) {
                resp.close()
                return null
            }
            val stream = resp.body?.byteStream()
            if (stream == null) {
                resp.close()
                return null
            }
            stream.bufferedReader().use { reader ->
                var count = 0
                while (count < 30) {
                    val line = reader.readLine() ?: break
                    count++
                    if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                        val epg = readM3uAttribute(line, "url-tvg")
                            ?: readM3uAttribute(line, "x-tvg-url")
                            ?: readM3uAttribute(line, "tvg-url")
                        if (!epg.isNullOrBlank()) return epg.trim()
                    }
                }
                null
            }
        }.getOrNull()
    }

    suspend fun syncEpg(
        epgUrl: String,
        channels: List<LiveTvChannel>,
        epgDatabase: EpgDatabase,
        onComplete: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val trimmedUrl = epgUrl.trim()
        if (trimmedUrl.isBlank() || channels.isEmpty()) return@withContext
        Log.d(TAG, "Starting EPG sync from $trimmedUrl for ${channels.size} channels")

        runCatching {
            val request = Request.Builder()
                .url(trimmedUrl)
                .header("Accept", "*/*")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) NuvioTV/1.0")
                .build()

            val response = liveTvHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                Log.w(TAG, "EPG download failed with HTTP ${response.code}")
                return@withContext
            }

            val body = response.body ?: return@withContext
            val isGzip = trimmedUrl.endsWith(".gz", ignoreCase = true) ||
                    response.header("Content-Encoding")?.contains("gzip", ignoreCase = true) == true

            val rawStream: InputStream = body.byteStream()
            val stream: InputStream = if (isGzip) GZIPInputStream(rawStream) else rawStream

            val now = System.currentTimeMillis()
            val windowStart = now - 48 * 3600 * 1000L
            val windowEnd = now + 7 * 24 * 3600 * 1000L

            val result = stream.use { inStream ->
                XmltvParser.parse(
                    input = inStream,
                    windowStartMs = windowStart,
                    windowEndMs = windowEnd,
                    selectChannels = { null }
                )
            }

            Log.d(TAG, "Parsed ${result.programs.size} program groups from XMLTV")

            if (result.programs.isEmpty()) {
                Log.w(TAG, "No programs found in XMLTV: ${result.error}")
                return@withContext
            }

            // Normalization maps for flexible channel matching
            val lowerPrograms = HashMap<String, List<EpgProgram>>(result.programs.size)
            result.programs.forEach { (k, v) ->
                lowerPrograms[k.lowercase().trim()] = v
            }

            val xmlChannelsByNorm = HashMap<String, String>()
            result.channels.forEach { (xid, xc) ->
                val lowId = xid.lowercase().trim()
                xmlChannelsByNorm[normalize(xid)] = lowId
                xc.displayNames.forEach { name ->
                    xmlChannelsByNorm[normalize(name)] = lowId
                }
            }

            var matchedCount = 0
            channels.forEach { ch ->
                val tvgId = ch.tvgId?.trim()?.lowercase()
                val tvgName = ch.tvgName?.trim()?.lowercase()
                val chName = ch.name.trim()

                // 1. Direct match by tvgId
                var progs = if (!tvgId.isNullOrBlank()) lowerPrograms[tvgId] else null

                // 2. Match by normalized tvgId
                if (progs.isNullOrEmpty() && !tvgId.isNullOrBlank()) {
                    val normId = xmlChannelsByNorm[normalize(tvgId)]
                    if (normId != null) progs = lowerPrograms[normId]
                }

                // 3. Match by tvgName
                if (progs.isNullOrEmpty() && !tvgName.isNullOrBlank()) {
                    val normName = xmlChannelsByNorm[normalize(tvgName)]
                    if (normName != null) progs = lowerPrograms[normName]
                    if (progs.isNullOrEmpty()) progs = lowerPrograms[tvgName]
                }

                // 4. Match by channel name
                if (progs.isNullOrEmpty()) {
                    val normCh = xmlChannelsByNorm[normalize(chName)]
                    if (normCh != null) progs = lowerPrograms[normCh]
                }

                if (!progs.isNullOrEmpty()) {
                    epgDatabase.putChannelRange(ch.key, windowStart, windowEnd, progs)
                    matchedCount++
                }
            }

            Log.d(TAG, "Successfully matched EPG for $matchedCount/${channels.size} channels")
            onComplete?.invoke()
        }.onFailure { e ->
            Log.e(TAG, "EPG sync error for $trimmedUrl", e)
        }
    }

    private fun normalize(name: String): String =
        name.lowercase()
            .replace(Regex("\\b(hd|fhd|4k|sd|2k|vip|raw|hevc|h265|plus)\\b"), "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
}
