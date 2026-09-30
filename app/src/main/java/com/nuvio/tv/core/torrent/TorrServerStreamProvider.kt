package com.nuvio.tv.core.torrent

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.network.safeApiCall
import com.nuvio.tv.data.mapper.toDomain
import com.nuvio.tv.data.remote.api.AddonApi
import com.nuvio.tv.domain.model.AddonStreams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrServerStreamProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val addonConfig: TorrServerAddonConfig,
    private val addonApi: AddonApi
) {
    companion object {
        private const val TAG = "TorrServerStreamProvider"
        const val PROVIDER_NAME = "TorrServer"
    }

    suspend fun isEnabled(): Boolean {
        val config = addonConfig.config.first()
        return config.enabled && config.addonUrl.isNotBlank()
    }

    suspend fun getStreams(type: String, videoId: String): AddonStreams? {
        val config = addonConfig.config.first()
        if (!config.enabled || config.addonUrl.isBlank()) {
            return null
        }

        val trimmedUrl = config.addonUrl.trim()
        val queryStart = trimmedUrl.indexOf('?')
        val cleanBaseUrl = if (queryStart >= 0) trimmedUrl.substring(0, queryStart) else trimmedUrl
        val queryParams = if (queryStart >= 0) trimmedUrl.substring(queryStart) else ""

        val basePath = cleanBaseUrl.trimEnd('/').removeSuffix("/manifest.json").trimEnd('/')
        val encodedType = encodePathSegment(type)
        val encodedVideoId = encodePathSegment(videoId)
        val streamUrl = "$basePath/stream/$encodedType/$encodedVideoId.json$queryParams"

        Log.d(TAG, "Fetching TorrServer streams from addon: $streamUrl")
        return when (val result = safeApiCall(context) { addonApi.getStreams(streamUrl) }) {
            is NetworkResult.Success -> {
                val streams = result.data.streams?.map { dto ->
                    dto.toDomain(PROVIDER_NAME, null)
                } ?: emptyList()
                Log.d(TAG, "TorrServer streams success, count=${streams.size}")
                if (streams.isNotEmpty()) {
                    AddonStreams(
                        addonName = PROVIDER_NAME,
                        addonLogo = null,
                        streams = streams
                    )
                } else null
            }
            is NetworkResult.Error -> {
                Log.w(TAG, "TorrServer stream fetch failed: code=${result.code} msg=${result.message}")
                null
            }
            NetworkResult.Loading -> null
        }
    }

    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
