package com.nuvio.tv.ui.screens.player

import androidx.annotation.OptIn
import androidx.media3.common.ByteBufferDataReader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.nuvio.tv.core.player.thumbnail.ConnectionLedger
import java.io.IOException
import java.nio.ByteBuffer
import java.util.IdentityHashMap

/** Playback's single-connection stream, counted in the [ConnectionLedger] from open to close. Never waits. */
@OptIn(UnstableApi::class)
internal class LedgerDataSource(private val upstream: DataSource) : DataSource by upstream, ByteBufferDataReader {
    @Volatile private var slot: ConnectionLedger.Slot? = null

    override fun supportsByteBufferRead(): Boolean =
        upstream is ByteBufferDataReader && upstream.supportsByteBufferRead()

    override fun open(dataSpec: DataSpec): Long {
        slot?.close()
        val s = runCatching {
            val scheme = dataSpec.uri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") ConnectionLedger.app.playback(dataSpec.uri.toString(), stream = true) else null
        }.getOrNull()
        slot = s
        return try {
            upstream.open(dataSpec).also { s?.moveTo(runCatching { upstream.uri?.toString() }.getOrNull()) }
        } catch (e: IOException) {
            s?.failed(e)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try {
        upstream.read(buffer, offset, length)
    } catch (e: IOException) {
        slot?.failed(e)
        throw e
    }

    override fun read(buffer: ByteBuffer, length: Int): Int = try {
        (upstream as ByteBufferDataReader).read(buffer, length)
    } catch (e: IOException) {
        slot?.failed(e)
        throw e
    }

    override fun close() {
        try {
            upstream.close()
        } finally {
            slot?.close()
            slot = null
        }
    }
}

@OptIn(UnstableApi::class)
internal class LedgerDataSourceFactory(private val upstream: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = LedgerDataSource(upstream.createDataSource())
}

/** Counts a playback source in the [ConnectionLedger] from its transfer start to its end. Never throws. */
@OptIn(UnstableApi::class)
internal class LedgerTransferListener(private val stream: Boolean = true) : TransferListener {
    private val slots = IdentityHashMap<DataSource, ConnectionLedger.Slot>()

    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (!isNetwork) return
        runCatching {
            val slot = ConnectionLedger.app.playback((source.uri ?: dataSpec.uri).toString(), stream) ?: return
            synchronized(slots) { slots.put(source, slot) }?.close()
        }
    }

    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) = Unit

    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        runCatching { synchronized(slots) { slots.remove(source) }?.close() }
    }
}
