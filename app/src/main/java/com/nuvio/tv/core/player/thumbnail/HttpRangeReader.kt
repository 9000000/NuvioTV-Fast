package com.nuvio.tv.core.player.thumbnail

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * The server asked us to slow down (429/503). [retryAfterMs] is the server's Retry-After, null when it sent none;
 * [url] is the address that answered, after redirects.
 */
internal class RateLimitedException(
    val retryAfterMs: Long?,
    val status: Int = TOO_MANY_REQUESTS,
    val url: String? = null,
) : IOException("rate limited") {
    companion object {
        const val TOO_MANY_REQUESTS = 429
    }
}

/**
 * Bounded Range reads over the playback OkHttp client, so a title's thumbnail fetches reuse keep-alive connections.
 * Safe for reads from several threads at once. Never open-ended; a 200 (Range ignored) is refused without reading
 * the body. Never log URLs or headers. Each read takes a slot in the [ConnectionLedger] first.
 */
internal class HttpRangeReader(
    private val url: String,
    private val headers: Map<String, String>,
    private val client: OkHttpClient,
    private val ledger: ConnectionLedger = ConnectionLedger.app,
    /** How long a read waits for a free connection in the [ConnectionLedger]. */
    @Volatile var slotWaitMs: Long = ConnectionLedger.SLOT_WAIT_MS,
) : RangeReader {
    private companion object {
        val RESOLVED_URL_FAILURES = setOf(401, 403, 404, 410)
        const val TOO_MANY_PEEK_BYTES = 512L
    }

    @Volatile
    override var totalLength: Long = -1L
        private set

    private val inFlight: MutableSet<Call> = Collections.newSetFromMap(ConcurrentHashMap<Call, Boolean>())

    @Volatile
    private var closed = false

    /** URL after redirects, so later requests skip the redirect round trip (0.25-0.7 s each on debrid links). */
    @Volatile
    private var effectiveUrl: String = url

    val resolvedUrl: String get() = effectiveUrl

    private val requestCount = AtomicInteger()
    private val byteCount = AtomicLong()

    val requests: Int get() = requestCount.get()
    val bytes: Long get() = byteCount.get()

    /** Reads waiting for a ledger slot when [cancel] runs give up too. */
    @Volatile
    private var cancels = 0

    fun cancel() {
        cancels++
        for (call in inFlight) call.cancel()
    }

    /** Cancels every read under way and refuses new ones. */
    fun close() {
        closed = true
        cancel()
    }

    override fun read(offset: Long, length: Int): ByteArray {
        require(offset >= 0 && length > 0)
        val target = effectiveUrl
        return try {
            readFrom(target, offset, length)
        } catch (e: HttpStatusException) {
            // The redirect target may be short-lived: retry once from the original.
            if (target != url && e.code in RESOLVED_URL_FAILURES) {
                effectiveUrl = url
                readFrom(url, offset, length)
            } else {
                throw e
            }
        }
    }

    class HttpStatusException(val code: Int, val tooMany: Boolean) : IOException("HTTP $code")

    private fun readFrom(target: String, offset: Long, length: Int): ByteArray {
        val builder = Request.Builder().url(target)
        for ((k, v) in headers) {
            if (!k.equals("Range", ignoreCase = true)) builder.header(k, v)
        }
        val request = builder
            .header("Range", "bytes=$offset-${offset + length - 1}")
            .header("Accept-Encoding", "identity")
            .build()
        if (closed) throw IOException("closed")
        val cancels0 = cancels
        var slot: ConnectionLedger.Slot? = null
        var call: Call? = null
        try {
            val held = ledger.acquire(target, slotWaitMs) { closed || cancels != cancels0 }
            slot = held
            val c = client.newCall(request)
            call = c
            inFlight.add(c)
            if (closed) c.cancel()
            requestCount.incrementAndGet()
            c.execute().use { resp ->
                held.moveTo(resp.request.url.toString())
                when (resp.code) {
                    206 -> Unit
                    416 -> return ByteArray(0)
                    429, 503 -> throw RateLimitedException(
                        ThumbRateLimit.retryAfterMs(resp.header("Retry-After"), System.currentTimeMillis()),
                        resp.code,
                        resp.request.url.toString(),
                    )
                    200 -> throw UnsupportedMediaException("server ignored Range")
                    else -> throw HttpStatusException(resp.code, ConnectionLedger.saysTooMany(
                        runCatching { resp.peekBody(TOO_MANY_PEEK_BYTES).string() }.getOrNull()))
                }
                resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.let { totalLength = it }
                val finalUrl = resp.request.url.toString()
                if (finalUrl != target) effectiveUrl = finalUrl
                val body = resp.body ?: throw IOException("empty body")
                val out = ByteArray(length)
                var n = 0
                body.byteStream().use { input ->
                    while (n < length) {
                        val r = input.read(out, n, length - n)
                        if (r < 0) break
                        n += r
                    }
                }
                byteCount.addAndGet(n.toLong())
                held.succeeded()
                return if (n == length) out else out.copyOf(n)
            }
        } catch (e: IOException) {
            val refusal = ConnectionLedger.refusalOf(e) ?: (e as? HttpStatusException)?.let {
                when {
                    it.tooMany -> Refusal.TOO_MANY
                    // A 403 from a short-lived redirect target is an expired link, retried from the original.
                    it.code == 403 && target == url -> Refusal.CONNECTION
                    else -> null
                }
            }
            refusal?.let { slot?.refused(it) }
            throw e
        } finally {
            slot?.close()
            call?.let { inFlight.remove(it) }
        }
    }
}
