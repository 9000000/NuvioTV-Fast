package com.nuvio.tv.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultAllocator

/** A load control whose byte budget can be lowered while the box is short of memory. */
@UnstableApi
interface BufferBudgetControl {
    val bufferAllocator: DefaultAllocator

    /** Budget without the memory-pressure cap. */
    val uncappedBudgetBytes: Long

    /** Negative removes the cap. */
    fun setPressureCapBytes(bytes: Long)

    /** The allocator target the load control would set now. */
    fun allocatorTargetBytes(): Int
}
