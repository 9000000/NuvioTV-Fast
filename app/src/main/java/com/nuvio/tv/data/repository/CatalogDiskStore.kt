package com.nuvio.tv.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.ForwardingSink
import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * One file per catalog key. A file's mtime is the time its body was last confirmed by a
 * successful fetch, which drives both expiry and eviction.
 */
internal class CatalogDiskStore(
    private val dir: File,
    private val legacyFiles: List<File>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntryBytes: Long = MAX_ENTRY_BYTES,
    private val maxTotalBytes: Long = MAX_TOTAL_BYTES,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxAgeMs: Long = MAX_AGE_MS,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val protectedMs: Long = PROTECTED_MS
) {
    companion object {
        const val MAX_ENTRY_BYTES = 1L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 32L * 1024 * 1024
        const val MAX_ENTRIES = 128
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val DEBOUNCE_MS = 2_000L

        // Rows refreshed this recently are never evicted, so a Home set larger than the
        // folder cap keeps the rows it already has instead of cycling through all of them.
        const val PROTECTED_MS = 10L * 60 * 1000

        private const val SUFFIX = ".cat"
        private const val TMP_SUFFIX = ".tmp"
        private const val MAGIC = "NVCAT1 "
        private const val HEADER_LIMIT = 80L
    }

    private class Meta(val bytes: Long, val storedAtMs: Long, val bodyHash: String?)
    private class Pending(val sizeHint: Int, val encode: (BufferedSink) -> Unit)
    private object EntryTooLarge : IOException()

    private val mutex = Mutex()
    private val index = ConcurrentHashMap<String, Meta>()
    private val pending = ConcurrentHashMap<String, Pending>()
    private val scheduled = ConcurrentHashMap.newKeySet<String>()
    private val oversizeAt = ConcurrentHashMap<String, Int>()
    @Volatile private var indexed = false

    internal val filesWritten = AtomicInteger()

    suspend fun warm() = ensureIndexed()

    suspend fun <T> read(key: String, decode: (BufferedSource) -> T?): T? {
        ensureIndexed()
        val name = fileName(key)
        val meta = index[name] ?: return null
        if (clock() - meta.storedAtMs > maxAgeMs) {
            withContext(ioDispatcher) { mutex.withLock { if (index[name] === meta) removeLocked(name) } }
            return null
        }
        return withContext(ioDispatcher) {
            try {
                File(dir, name).source().buffer().use { file ->
                    val header = file.readUtf8LineStrict(HEADER_LIMIT)
                    if (!header.startsWith(MAGIC)) throw IOException("bad header")
                    val hashing = HashingSource.sha256(file)
                    val body = hashing.buffer()
                    val value = decode(body)
                    body.readAll(blackholeSink())
                    if (hashing.hash.hex() != header.substring(MAGIC.length)) throw IOException("body changed")
                    value
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutex.withLock { if (index[name] === meta) removeLocked(name) }
                null
            }
        }
    }

    /**
     * Queues [encode] for [key]. Within the debounce window the latest call wins. [sizeHint]
     * (for example an item count) lets a key that was too large once skip encoding until it shrinks.
     */
    fun put(key: String, sizeHint: Int, encode: (BufferedSink) -> Unit) {
        val name = fileName(key)
        oversizeAt[name]?.let { if (sizeHint >= it) return }
        pending[name] = Pending(sizeHint, encode)
        if (scheduled.add(name)) {
            scope.launch(ioDispatcher) {
                delay(debounceMs)
                runCatching { flush(name) }
            }
        }
    }

    suspend fun clear() {
        mutex.withLock {
            pending.clear()
            oversizeAt.clear()
            index.clear()
            withContext(ioDispatcher) {
                dir.listFiles()?.forEach { it.delete() }
            }
        }
    }

    private suspend fun flush(name: String) {
        val job = pending.remove(name) ?: return
        scheduled.remove(name)
        val body = Buffer()
        val fits = try {
            val sink = CappedSink(body, maxEntryBytes).buffer()
            job.encode(sink)
            sink.flush()
            true
        } catch (e: EntryTooLarge) {
            false
        }
        if (!fits) {
            body.clear()
            oversizeAt[name] = job.sizeHint
            mutex.withLock { removeLocked(name) }
            return
        }
        oversizeAt.remove(name)
        val bodyHash = body.sha256().hex()
        val header = MAGIC + bodyHash + "\n"
        val fileBytes = header.length + body.size
        mutex.withLock {
            withContext(ioDispatcher) {
                val file = File(dir, name)
                val now = clock()
                val meta = index[name]
                if (meta != null && meta.bytes == fileBytes &&
                    (meta.bodyHash ?: readBodyHash(file)) == bodyHash &&
                    file.setLastModified(now)
                ) {
                    index[name] = Meta(fileBytes, now, bodyHash)
                    return@withContext
                }
                if (!fitsLocked(name, fileBytes, now)) {
                    oversizeAt[name] = job.sizeHint
                    removeLocked(name)
                    return@withContext
                }
                dir.mkdirs()
                val tmp = File(dir, name + TMP_SUFFIX)
                try {
                    tmp.sink().buffer().use { out ->
                        out.writeUtf8(header)
                        out.writeAll(body)
                    }
                } catch (e: IOException) {
                    tmp.delete()
                    throw e
                }
                if (!tmp.renameTo(file)) {
                    file.delete()
                    if (!tmp.renameTo(file)) {
                        tmp.delete()
                        index.remove(name)
                        return@withContext
                    }
                }
                file.setLastModified(now)
                index[name] = Meta(fileBytes, now, bodyHash)
                filesWritten.incrementAndGet()
                evictLocked(keep = name, now = now)
            }
        }
    }

    private suspend fun ensureIndexed() {
        if (indexed) return
        mutex.withLock {
            if (indexed) return
            withContext(ioDispatcher) {
                runCatching {
                    legacyFiles.forEach { if (it.exists()) it.delete() }
                    val now = clock()
                    dir.listFiles()?.forEach { file ->
                        when {
                            file.name.endsWith(TMP_SUFFIX) -> file.delete()
                            file.name.endsWith(SUFFIX) -> {
                                val storedAt = file.lastModified()
                                if (now - storedAt > maxAgeMs) {
                                    file.delete()
                                } else {
                                    index[file.name] = Meta(file.length(), storedAt, null)
                                }
                            }
                        }
                    }
                    evictLocked(keep = null, now = now)
                }
            }
            indexed = true
        }
    }

    /** Whether [name] can be stored once every row outside the protected window is evicted. */
    private fun fitsLocked(name: String, fileBytes: Long, now: Long): Boolean {
        val kept = index.entries.filter { it.key != name && now - it.value.storedAtMs < protectedMs }
        return kept.sumOf { it.value.bytes } + fileBytes <= maxTotalBytes && kept.size + 1 <= maxEntries
    }

    private fun evictLocked(keep: String?, now: Long) {
        var total = index.values.sumOf { it.bytes }
        if (total <= maxTotalBytes && index.size <= maxEntries) return
        val oldestFirst = index.entries
            .filter { it.key != keep && (keep == null || now - it.value.storedAtMs >= protectedMs) }
            .sortedBy { it.value.storedAtMs }
        for ((name, meta) in oldestFirst) {
            if (total <= maxTotalBytes && index.size <= maxEntries) break
            File(dir, name).delete()
            index.remove(name)
            total -= meta.bytes
        }
    }

    private fun removeLocked(name: String) {
        index.remove(name)
        runCatching { File(dir, name).delete() }
    }

    private fun readBodyHash(file: File): String? = runCatching {
        file.source().buffer().use { it.readUtf8LineStrict(HEADER_LIMIT) }
            .takeIf { it.startsWith(MAGIC) }
            ?.substring(MAGIC.length)
    }.getOrNull()

    private fun fileName(key: String): String = key.encodeUtf8().sha256().hex() + SUFFIX

    private class CappedSink(delegate: Buffer, private val cap: Long) : ForwardingSink(delegate) {
        private var written = 0L
        override fun write(source: Buffer, byteCount: Long) {
            written += byteCount
            if (written > cap) throw EntryTooLarge
            super.write(source, byteCount)
        }
    }
}
