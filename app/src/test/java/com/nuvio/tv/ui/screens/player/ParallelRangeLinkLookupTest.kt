package com.nuvio.tv.ui.screens.player

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.NuvioEngineConfig
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.ExecutionException

/** Dead redirect targets: production source, scripted transport, no sockets. */
class ParallelRangeLinkLookupTest {
    private val chunk = 262_144L
    private val total = 600_000L
    private val seekTo = 300_000L

    @Before fun start() {
        NuvioEngineConfig.set(NuvioEngineConfig.stockMode())
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { System.nanoTime() / 1_000_000L }
        every { SystemClock.uptimeMillis() } answers { System.nanoTime() / 1_000_000L }
    }

    @After fun finish() {
        unmockkStatic(SystemClock::class)
        NuvioEngineConfig.set(NuvioEngineConfig.stockMode())
    }

    private fun uri(name: String): Uri = mockk(relaxed = true) {
        every { host } returns name
        every { this@mockk.toString() } returns "https://$name/file.mkv"
        every { getQueryParameter(any()) } returns null
    }

    /** Download threads currently waiting for another download's look-up of the link. */
    private fun waitingForLookUp(): Int = Thread.getAllStackTraces().count { (thread, stack) ->
        thread.name == "parallel-test-worker" && stack.any { it.methodName == "awaitLookUp" }
    }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out waiting until $what" }
            Thread.sleep(5)
        }
    }

    private sealed class Answer {
        class Serve(val servedUri: Uri, val total: Long) : Answer()
        class Fail(val error: Exception) : Answer()
    }

    private fun byteAt(position: Long) = (position % 251).toByte()

    private fun unreachable(spec: DataSpec) = HttpDataSource.HttpDataSourceException(
        IOException(ExecutionException(SocketTimeoutException(
            "failed to connect to ${spec.uri.host}/192.0.2.1 (port 443) from /192.168.1.20 (port 40000) after 6000ms"
        ))),
        spec,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        HttpDataSource.HttpDataSourceException.TYPE_OPEN
    )

    private inner class Fixture(
        allowContinuationReopen: Boolean = true,
        connections: Int = 1,
        val server: Fixture.(DataSpec) -> Answer
    ) {
        val link = uri("link.example")
        val opened: MutableList<Uri> = Collections.synchronizedList(mutableListOf())
        val factory = mockk<OkHttpDataSource.Factory> {
            every { createDataSource() } answers { transport() }
        }
        val source = ParallelRangeDataSource(
            factory,
            parallelConnections = connections,
            chunkSize = chunk,
            shouldAllowBackgroundPrefetch = { false },
            allowContinuationReopen = allowContinuationReopen,
            isolateSession = true
        )

        private fun transport(): OkHttpDataSource {
            val ds = mockk<OkHttpDataSource>(relaxed = true)
            var position = 0L
            var end = 0L
            var served: Answer.Serve? = null
            every { ds.open(any()) } answers {
                val spec = firstArg<DataSpec>()
                opened += spec.uri
                when (val answer = server(spec)) {
                    is Answer.Fail -> throw answer.error
                    is Answer.Serve -> {
                        served = answer
                        position = spec.position
                        end = if (spec.length > 0) spec.position + spec.length else answer.total
                        end - position
                    }
                }
            }
            every { ds.uri } answers { served?.servedUri }
            every { ds.responseCode } answers { if (served != null) 206 else -1 }
            every { ds.responseHeaders } answers {
                val s = served ?: return@answers emptyMap()
                mapOf("Content-Range" to listOf("bytes ${position}-${end - 1}/${s.total}"))
            }
            every { ds.read(any(), any(), any()) } answers {
                if (position >= end) return@answers -1
                val target = firstArg<ByteArray>()
                val offset = secondArg<Int>()
                val n = minOf(thirdArg<Int>().toLong(), end - position).toInt()
                for (i in 0 until n) target[offset + i] = byteAt(position + i)
                position += n
                n
            }
            return ds
        }

        fun spec(position: Long) = DataSpec.Builder().setUri(link).setPosition(position).build()

        fun startAndSeek() {
            assertEquals(total, source.open(spec(0L)))
            assertEquals(total - seekTo, source.open(spec(seekTo)))
        }

        fun target(): Uri {
            val resources = source.javaClass.getDeclaredField("resources").apply { isAccessible = true }.get(source)
            val session = resources.javaClass.getDeclaredField("currentChunkSession")
                .apply { isAccessible = true }.get(resources)!!
            return session.javaClass.getDeclaredMethod("requestTarget").invoke(session) as Uri
        }

        fun adopt(resolved: Uri) {
            val resources = source.javaClass.getDeclaredField("resources").apply { isAccessible = true }.get(source)
            val session = resources.javaClass.getDeclaredField("currentChunkSession")
                .apply { isAccessible = true }.get(resources)!!
            session.javaClass.getDeclaredMethod("adoptResolvedUri", Uri::class.java).invoke(session, resolved)
        }

        private fun session(): Any {
            val resources = source.javaClass.getDeclaredField("resources").apply { isAccessible = true }.get(source)
            return resources.javaClass.getDeclaredField("currentChunkSession").apply { isAccessible = true }.get(resources)!!
        }

        fun schedule(indices: List<Long>) {
            val method = source.javaClass.getDeclaredMethod("ensureChunkScheduled", Long::class.javaPrimitiveType)
                .apply { isAccessible = true }
            indices.forEach { method.invoke(source, it) }
        }

        @Suppress("UNCHECKED_CAST")
        fun futures(): Map<Long, java.util.concurrent.Future<*>> =
            session().javaClass.getMethod("getFutures").invoke(session()) as Map<Long, java.util.concurrent.Future<*>>

        /** Starts downloads of [indices] side by side and waits for each; true where it succeeded. */
        fun downloadTogether(indices: List<Long>): List<Boolean> {
            schedule(indices)
            val futures = futures()
            return indices.map { index ->
                runCatching { futures.getValue(index).get(10, java.util.concurrent.TimeUnit.SECONDS) }.isSuccess
            }
        }

        fun readAll(length: Int): ByteArray {
            val out = ByteArray(length)
            var done = 0
            while (done < length) {
                val n = source.read(out, done, length - done)
                assertTrue("read returned $n", n > 0)
                done += n
            }
            return out
        }
    }

    @Test fun `an unreachable cdn node is dropped and the original link is asked again`() {
        val deadNode = uri("node-1.cdn.example")
        val liveNode = uri("node-2.cdn.example")
        val f = Fixture { spec ->
            when (spec.uri) {
                link -> if (opened.size == 1) Answer.Serve(deadNode, total) else Answer.Serve(liveNode, total)
                deadNode -> Answer.Fail(unreachable(spec))
                else -> Answer.Fail(IOException("unexpected ${spec.uri.host}"))
            }
        }
        try {
            f.startAndSeek()
            val bytes = f.readAll(1_000)
            for (i in bytes.indices) assertEquals(byteAt(seekTo + i), bytes[i])
            assertEquals(listOf(f.link, deadNode, f.link), f.opened.toList())
            assertSame(liveNode, f.target())
        } finally { f.source.close() }
    }

    @Test fun `an unreachable server on the link's own host fails the load without another wait`() {
        val sameHost = uri("link.example")
        val f = Fixture { spec ->
            if (spec.uri == link && opened.size == 1) Answer.Serve(sameHost, total) else Answer.Fail(unreachable(spec))
        }
        try {
            f.startAndSeek()
            val failure = assertThrows(HttpDataSource.HttpDataSourceException::class.java) { f.source.read(ByteArray(16), 0, 16) }
            assertEquals(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, failure.reason)
            assertTrue(PlaybackNetworkFailure.isConnectFailure(failure))
            assertEquals(listOf(f.link, sameHost), f.opened.toList())
            assertSame(sameHost, f.target())
        } finally { f.source.close() }
    }

    @Test fun `a chunk is not tried twice on the same unreachable server`() {
        val f = Fixture(allowContinuationReopen = false) { spec ->
            if (opened.size == 1) Answer.Serve(link, total) else Answer.Fail(unreachable(spec))
        }
        try {
            f.startAndSeek()
            assertThrows(IOException::class.java) { f.source.read(ByteArray(16), 0, 16) }
            assertEquals(2, f.opened.size)
        } finally { f.source.close() }
    }

    @Test fun `a chunk retry after a dead node goes through the original link`() {
        val deadNode = uri("node-1.cdn.example")
        val liveNode = uri("node-2.cdn.example")
        val f = Fixture(allowContinuationReopen = false) { spec ->
            when (spec.uri) {
                link -> if (opened.size == 1) Answer.Serve(deadNode, total) else Answer.Serve(liveNode, total)
                else -> Answer.Fail(unreachable(spec))
            }
        }
        try {
            f.startAndSeek()
            val bytes = f.readAll(1_000)
            for (i in bytes.indices) assertEquals(byteAt(seekTo + i), bytes[i])
            assertEquals(listOf(f.link, deadNode, f.link), f.opened.toList())
            assertSame(liveNode, f.target())
        } finally { f.source.close() }
    }

    @Test fun `an expired cdn address is looked up again`() {
        val expired = uri("node-1.cdn.example")
        val fresh = uri("node-1.cdn.example")
        val f = Fixture(allowContinuationReopen = false) { spec ->
            when (spec.uri) {
                link -> if (opened.size == 1) Answer.Serve(expired, total) else Answer.Serve(fresh, total)
                else -> Answer.Fail(HttpDataSource.InvalidResponseCodeException(
                    403, "Forbidden", null, emptyMap(), spec, byteArrayOf()
                ))
            }
        }
        try {
            f.startAndSeek()
            f.readAll(100)
            assertEquals(listOf(f.link, expired, f.link), f.opened.toList())
            assertSame(fresh, f.target())
        } finally { f.source.close() }
    }

    @Test fun `a fresh answer is size checked even when a parallel download adopted its own first`() {
        val deadNode = uri("node-1.cdn.example")
        val liveNode = uri("node-2.cdn.example")
        val otherFile = uri("node-3.cdn.example")
        val f = Fixture(allowContinuationReopen = false) { spec ->
            when (spec.uri) {
                link -> if (opened.size == 1) {
                    Answer.Serve(deadNode, total)
                } else {
                    adopt(liveNode)
                    Answer.Serve(otherFile, total + 1)
                }
                else -> Answer.Fail(unreachable(spec))
            }
        }
        try {
            f.startAndSeek()
            assertThrows(IOException::class.java) { f.source.read(ByteArray(16), 0, 16) }
            assertSame(liveNode, f.target())
        } finally { f.source.close() }
    }

    @Test fun `a slow first byte keeps the address and tries the same server again`() {
        val node = uri("node-1.cdn.example")
        var slow = true
        val f = Fixture(allowContinuationReopen = false) { spec ->
            when (spec.uri) {
                link -> Answer.Serve(node, total)
                node -> if (slow) {
                    slow = false
                    Answer.Fail(HttpDataSource.HttpDataSourceException(
                        IOException(ExecutionException(SocketTimeoutException("timeout"))),
                        spec,
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                        HttpDataSource.HttpDataSourceException.TYPE_OPEN
                    ))
                } else {
                    Answer.Serve(node, total)
                }
                else -> Answer.Fail(IOException("unexpected ${spec.uri.host}"))
            }
        }
        try {
            f.startAndSeek()
            val bytes = f.readAll(1_000)
            for (i in bytes.indices) assertEquals(byteAt(seekTo + i), bytes[i])
            assertEquals(listOf(f.link, node, node), f.opened.toList())
            assertSame(node, f.target())
        } finally { f.source.close() }
    }

    @Test fun `a fresh link that serves a different file is not used`() {
        val deadNode = uri("node-1.cdn.example")
        val otherFile = uri("node-2.cdn.example")
        val f = Fixture(allowContinuationReopen = false) { spec ->
            when (spec.uri) {
                link -> if (opened.size == 1) Answer.Serve(deadNode, total) else Answer.Serve(otherFile, total + 1)
                else -> Answer.Fail(unreachable(spec))
            }
        }
        try {
            f.startAndSeek()
            assertThrows(IOException::class.java) { f.source.read(ByteArray(16), 0, 16) }
            assertSame(f.link, f.target())
        } finally { f.source.close() }
    }

    @Test fun `parallel downloads that lose the same node ask the link only once`() {
        val deadNode = uri("node-1.cdn.example")
        val liveNode = uri("node-2.cdn.example")
        val allFailing = java.util.concurrent.CountDownLatch(3)
        val f = Fixture(allowContinuationReopen = false, connections = 3) { spec ->
            when (spec.uri) {
                link -> if (opened.count { it == link } == 1) Answer.Serve(deadNode, total) else {
                    waitUntil("the other two downloads wait for this look-up") { waitingForLookUp() == 2 }
                    Answer.Serve(liveNode, total)
                }
                deadNode -> {
                    allFailing.countDown()
                    allFailing.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    Answer.Fail(unreachable(spec))
                }
                liveNode -> Answer.Serve(liveNode, total)
                else -> Answer.Fail(IOException("unexpected ${spec.uri.host}"))
            }
        }
        try {
            f.startAndSeek()
            assertEquals(listOf(true, true, true), f.downloadTogether(listOf(0L, 1L, 2L)))
            assertEquals(2, f.opened.count { it == f.link })
            assertEquals(3, f.opened.count { it == deadNode })
            assertEquals(2, f.opened.count { it == liveNode })
            assertSame(liveNode, f.target())
        } finally { f.source.close() }
    }

    @Test fun `when the one look-up fails the waiting downloads fail without asking themselves`() {
        val deadNode = uri("node-1.cdn.example")
        val allFailing = java.util.concurrent.CountDownLatch(3)
        val f = Fixture(allowContinuationReopen = false, connections = 3) { spec ->
            when (spec.uri) {
                link -> if (opened.count { it == link } == 1) Answer.Serve(deadNode, total) else {
                    waitUntil("the other two downloads wait for this look-up") { waitingForLookUp() == 2 }
                    Answer.Fail(unreachable(spec))
                }
                deadNode -> {
                    allFailing.countDown()
                    allFailing.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    Answer.Fail(unreachable(spec))
                }
                else -> Answer.Fail(IOException("unexpected ${spec.uri.host}"))
            }
        }
        try {
            f.startAndSeek()
            assertEquals(listOf(false, false, false), f.downloadTogether(listOf(0L, 1L, 2L)))
            assertEquals(2, f.opened.count { it == f.link })
            assertEquals(3, f.opened.count { it == deadNode })
            assertSame(f.link, f.target())
        } finally { f.source.close() }
    }

    @Test fun `a download waiting for the look-up stops when its chunk is cancelled`() {
        val deadNode = uri("node-1.cdn.example")
        val liveNode = uri("node-2.cdn.example")
        val bothFailing = java.util.concurrent.CountDownLatch(2)
        val release = java.util.concurrent.CountDownLatch(1)
        val askedFor = java.util.concurrent.atomic.AtomicLong(-1)
        val f = Fixture(allowContinuationReopen = false, connections = 2) { spec ->
            when (spec.uri) {
                link -> if (opened.count { it == link } == 1) Answer.Serve(deadNode, total) else {
                    askedFor.set(spec.position / chunk)
                    release.await(10, java.util.concurrent.TimeUnit.SECONDS)
                    Answer.Serve(liveNode, total)
                }
                deadNode -> {
                    bothFailing.countDown()
                    bothFailing.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    Answer.Fail(unreachable(spec))
                }
                liveNode -> Answer.Serve(liveNode, total)
                else -> Answer.Fail(IOException("unexpected ${spec.uri.host}"))
            }
        }
        try {
            f.startAndSeek()
            f.schedule(listOf(0L, 1L))
            waitUntil("one download asks the link") { askedFor.get() >= 0 }
            waitUntil("one download waits for the other's look-up") { waitingForLookUp() == 1 }
            val asker = askedFor.get()
            val waiter = if (asker == 0L) 1L else 0L
            f.futures().getValue(waiter).cancel(true)
            waitUntil("the cancelled download stops waiting") { waitingForLookUp() == 0 }
            assertEquals(1L, release.count)
            release.countDown()
            f.futures().getValue(asker).get(10, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(2, f.opened.count { it == f.link })
            assertEquals(0, f.opened.count { it == liveNode })
            assertSame(liveNode, f.target())
        } finally {
            release.countDown()
            f.source.close()
        }
    }
}
