package com.nuvio.tv.core.network

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.nuvio.tv.ui.screens.player.ParallelRangeDataSource
import com.nuvio.tv.ui.screens.player.PlayerPlaybackNetworking
import okhttp3.Request

@UnstableApi
object StreamSpeedTester {

    // 1. Measures single connection baseline speed (standard OkHttp)
    suspend fun runBaselineTest(
        url: String,
        headers: Map<String, String>
    ): Double = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val testDurationMs = 8000L
        var totalBytes = 0L
        val tStart = System.currentTimeMillis()
        val tDeadline = tStart + testDurationMs

        try {
            val request = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()

            PlayerPlaybackNetworking.playbackHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext 0.0
                val inputStream = response.body.byteStream()
                val buffer = ByteArray(64 * 1024)
                while (System.currentTimeMillis() < tDeadline) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext 0.0
        }

        val elapsed = System.currentTimeMillis() - tStart
        if (elapsed > 0) (totalBytes * 8.0) / (elapsed * 1000.0) else 0.0
    }

    // 2. Measures parallel connection speed at a specific chunk size
    suspend fun runParallelChunkTest(
        url: String,
        headers: Map<String, String>,
        chunkSizeBytes: Long
    ): Double = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val testDurationMs = 8000L
        var totalBytesRead = 0L
        var totalBytesDownloaded = 0L
        val tStart = System.currentTimeMillis()
        val tDeadline = tStart + testDurationMs

        val transferListener = object : TransferListener {
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                if (isNetwork) {
                    totalBytesDownloaded += bytesTransferred
                }
            }
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {}
        }

        try {
            val okHttpFactory = OkHttpDataSource.Factory(PlayerPlaybackNetworking.playbackHttpClient).apply {
                setDefaultRequestProperties(headers)
            }
            // Use existing ParallelRangeDataSource from the app
            val dataSource = ParallelRangeDataSource(
                upstreamFactory = okHttpFactory,
                parallelConnections = 3,
                chunkSize = chunkSizeBytes,
                useNativeMemory = true
            ).apply {
                addTransferListener(transferListener)
            }
            dataSource.open(DataSpec(android.net.Uri.parse(url)))
            val buffer = ByteArray(64 * 1024)
            while (System.currentTimeMillis() < tDeadline) {
                val read = dataSource.read(buffer, 0, buffer.size)
                if (read == -1) break
                totalBytesRead += read
            }
            dataSource.close()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext 0.0
        }

        val finalBytes = if (totalBytesDownloaded > 0L) totalBytesDownloaded else totalBytesRead
        val elapsed = System.currentTimeMillis() - tStart
        if (elapsed > 0) (finalBytes * 8.0) / (elapsed * 1000.0) else 0.0
    }

    suspend fun getStreamContentLength(
        url: String,
        headers: Map<String, String>
    ): Long = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val rangeRequest = Request.Builder().url(url).get().header("Range", "bytes=0-0").apply {
                headers.forEach { (k, v) ->
                    if (!k.equals("Range", ignoreCase = true)) {
                        header(k, v)
                    }
                }
            }.build()
            PlayerPlaybackNetworking.playbackHttpClient.newCall(rangeRequest).execute().use { response ->
                if (response.code == 206 || response.code == 200) {
                    val contentRange = response.header("Content-Range")
                    val totalFromRange = contentRange?.substringAfter('/', missingDelimiterValue = "")?.trim()?.toLongOrNull()
                    val len = totalFromRange ?: response.header("Content-Length")?.toLongOrNull() ?: response.body.contentLength()
                    if (len > 0) return@withContext len
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        0L
    }
}
