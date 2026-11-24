package com.nuvio.tv.ui.screens.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.nuvio.tv.core.network.IPv4FirstDns
import com.nuvio.tv.core.network.PlaybackSocketFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal object PlayerPlaybackNetworking {
    private const val LOOPBACK_READ_TIMEOUT_SECONDS = 65L

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private val playbackHostnameVerifier = HostnameVerifier { _, _ -> true }

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }
    }

    /**
     * Fallback OkHttpClient equipped with trust-all SSL configuration for self-signed
     * or untrusted local media servers (e.g. self-signed WebDAV / Plex / Jellyfin).
     */
    internal val trustAllPlaybackHttpClient: OkHttpClient by lazy {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 32
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .dns(IPv4FirstDns())
            .socketFactory(PlaybackSocketFactory)
            .eventListenerFactory(PlaybackConnectionEvents)
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier(playbackHostnameVerifier)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Primary OkHttpClient using standard system SSL certificates and full SNI support.
     * Includes an automatic fallback to [trustAllPlaybackHttpClient] if an [SSLException]
     * occurs on self-signed local media servers.
     */
    internal val playbackHttpClient: OkHttpClient by lazy {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 32
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .dns(IPv4FirstDns())
            .socketFactory(PlaybackSocketFactory)
            .eventListenerFactory(PlaybackConnectionEvents)
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request()
                try {
                    chain.proceed(request)
                } catch (e: SSLException) {
                    // Fallback to trust-all client if standard system SSL fails (e.g. self-signed local server)
                    trustAllPlaybackHttpClient.newCall(request).execute()
                }
            }
            .build()
    }

    /**
     * Hosts that rejected an HTTP/1.1 request with 403 but accepted the same request over HTTP/2.
     * Kept in memory only (never persisted) so later requests to that host skip the failing
     * HTTP/1.1 attempt. Bounded so it can never grow without limit.
     */
    private val http2Hosts: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private const val HTTP2_HOSTS_MAX = 64

    /**
     * Playback stays on HTTP/1.1. Only for streams that carry addon proxyHeaders: if a request
     * gets 403 over HTTP/1.1, it is repeated once over HTTP/2 (ALPN, falling back to HTTP/1.1).
     * A successful retry marks the host, so the following requests go straight to HTTP/2.
     * Cleartext URLs never negotiate HTTP/2 and are left alone. If the retry also fails, the
     * original 403 is returned unchanged.
     */
    private class Http2On403Interceptor(private val http2Client: OkHttpClient) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (!request.url.isHttps) return chain.proceed(request)
            val host = request.url.host
            if (host in http2Hosts) return http2Client.newCall(request).execute()

            val response = chain.proceed(request)
            if (response.code != 403) return response

            val retry = try {
                http2Client.newCall(request).execute()
            } catch (e: IOException) {
                return response
            }
            return if (retry.isSuccessful) {
                if (http2Hosts.size >= HTTP2_HOSTS_MAX) http2Hosts.clear()
                http2Hosts.add(host)
                response.close()
                retry
            } else {
                retry.close()
                response
            }
        }
    }

    internal fun withHttp2On403Fallback(http1Client: OkHttpClient): OkHttpClient {
        // Same client (same interceptors, pool and dispatcher) but allowed to negotiate HTTP/2.
        // It does not contain the retry interceptor below, so it cannot loop.
        val http2Client = http1Client.newBuilder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
        return http1Client.newBuilder()
            .addInterceptor(Http2On403Interceptor(http2Client))
            .build()
    }

    fun createHttpClient(
        defaultHeaders: Map<String, String> = emptyMap(),
        useLongReadTimeout: Boolean = false
    ): OkHttpClient {
        val builder = playbackHttpClient.newBuilder()
        if (useLongReadTimeout) {
            builder.readTimeout(LOOPBACK_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        val proxyHeaders = defaultHeaders.filterKeys { key ->
            !key.equals("Range", ignoreCase = true)
        }
        // Preserve essential headers across cross-host redirects (e.g. Pengu, VidFast, WebDAV)
        // and sanitize headers when redirecting to presigned S3/R2 storage.
        val authValue = defaultHeaders.entries
            .firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
            ?.value
        val refererValue = defaultHeaders.entries
            .firstOrNull { it.key.equals("Referer", ignoreCase = true) }
            ?.value
        val userAgentValue = defaultHeaders.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
        val originValue = defaultHeaders.entries
            .firstOrNull { it.key.equals("Origin", ignoreCase = true) }
            ?.value

        val customHeaders = defaultHeaders.filterKeys { key ->
            !key.equals("Authorization", ignoreCase = true) &&
                !key.equals("Referer", ignoreCase = true) &&
                !key.equals("User-Agent", ignoreCase = true) &&
                !key.equals("Origin", ignoreCase = true) &&
                !key.equals("Range", ignoreCase = true) &&
                !key.equals("Host", ignoreCase = true)
        }

        if (authValue != null || refererValue != null || userAgentValue != null || originValue != null || customHeaders.isNotEmpty()) {
            builder.addNetworkInterceptor { chain ->
                val request = chain.request()
                val requestUrl = request.url.toString()
                val isPresigned = PlayerMediaSourceFactory.isPresignedOrR2Url(requestUrl)

                var modified = false
                val reqBuilder = request.newBuilder()

                if (isPresigned) {
                    // S3/R2 presigned URLs reject requests with 400/403 if Authorization is sent.
                    if (request.header("Authorization") != null) {
                        reqBuilder.removeHeader("Authorization")
                        modified = true
                    }
                } else if (authValue != null && request.header("Authorization") == null) {
                    reqBuilder.header("Authorization", authValue)
                    modified = true
                }

                if (refererValue != null && request.header("Referer") == null) {
                    reqBuilder.header("Referer", refererValue)
                    modified = true
                }

                if (userAgentValue != null && request.header("User-Agent") == null) {
                    reqBuilder.header("User-Agent", userAgentValue)
                    modified = true
                }

                if (originValue != null && request.header("Origin") == null) {
                    reqBuilder.header("Origin", originValue)
                    modified = true
                }

                customHeaders.forEach { (k, v) ->
                    if (request.header(k) == null) {
                        reqBuilder.header(k, v)
                        modified = true
                    }
                }

                if (modified) {
                    chain.proceed(reqBuilder.build())
                } else {
                    chain.proceed(request)
                }
            }
        }
        val http1Client = builder
            .let { NuvioExoPlayerPerformanceHelper.applyNetworkOptimizations(it) }
            .build()
        if (proxyHeaders.isEmpty()) return http1Client

        return withHttp2On403Fallback(http1Client)
    }

    @UnstableApi
    fun createHttpDataSourceFactory(
        defaultHeaders: Map<String, String> = emptyMap(),
        streamUrl: String = "",
        useLongReadTimeout: Boolean = false
    ): DataSource.Factory {
        val requestHeaders = defaultHeaders.mapNotNull { (rawKey, rawValue) ->
            val key = rawKey.trim()
            val value = rawValue.trim()
            if (key.isEmpty() || value.isEmpty() || key.equals("Range", ignoreCase = true)) {
                null
            } else {
                key to value
            }
        }.toMap()

        // Auto-apply IPTV headers if needed
        val effectiveHeaders = IptvHeaderProvider.mergeWithDefaults(streamUrl, requestHeaders)

        val client = createHttpClient(effectiveHeaders, useLongReadTimeout)
        val httpFactory = OkHttpDataSource.Factory(client).apply {
            setDefaultRequestProperties(effectiveHeaders)
            if (effectiveHeaders.none { it.key.equals("User-Agent", ignoreCase = true) }) {
                setUserAgent(PlayerMediaSourceFactory.DEFAULT_USER_AGENT)
            }
        }
        return LoggingDataSourceFactory(httpFactory, "HTTP")
    }

    @UnstableApi
    fun createDataSourceFactory(
        context: android.content.Context,
        defaultHeaders: Map<String, String> = emptyMap(),
        streamUrl: String = "",
        useLongReadTimeout: Boolean = false
    ): DataSource.Factory {
        return DefaultDataSource.Factory(context, createHttpDataSourceFactory(defaultHeaders, streamUrl, useLongReadTimeout))
    }

    fun openConnection(
        url: String,
        headers: Map<String, String>,
        method: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        range: String? = null
    ): HttpURLConnection {
        // Auto-apply IPTV headers if needed
        val effectiveHeaders = IptvHeaderProvider.mergeWithDefaults(url, headers)
        
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            requestMethod = method
            setRequestProperty("User-Agent", effectiveHeaders["User-Agent"] ?: PlayerMediaSourceFactory.DEFAULT_USER_AGENT)
            effectiveHeaders.forEach { (key, value) ->
                if (key.equals("Range", ignoreCase = true)) return@forEach
                if (key.equals("User-Agent", ignoreCase = true)) return@forEach
                setRequestProperty(key, value)
            }
            range?.let { setRequestProperty("Range", it) }
        }
    }

    // The chunk client also uses the mutable extension cookie jar. Until its cookie version can
    // be bound to a warm entry, fall through to normal playback for these authenticated sources.
    internal fun canReusePrewarmFor(url: String, resolvedUrl: String = url): Boolean =
        listOf(url, resolvedUrl).all { value ->
            val parsed = value.toHttpUrlOrNull()
            parsed != null && com.nuvio.tv.NuvioApplication.extensionCookieJar.loadForRequest(parsed).isEmpty()
        }
}
