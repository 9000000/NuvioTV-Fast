package com.nuvio.tv.ui.screens.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.nuvio.tv.core.player.BufferBudgetControl
import com.nuvio.tv.core.player.BufferMemoryKeeper
import com.nuvio.tv.core.player.BufferSizing
import com.nuvio.tv.core.player.MemoryPressure

/** One keeper per load control; null (stock buffers, or no player) detaches it. */
@OptIn(UnstableApi::class)
internal fun PlayerRuntimeController.attachBufferMemoryKeeper(control: BufferBudgetControl?) {
    val current = bufferMemoryKeeper
    if (current != null && current.control === control) return
    current?.detach()
    bufferMemoryKeeper = control?.let { budgetControl ->
        val appContext = context.applicationContext
        MemoryPressure.register(appContext)
        BufferMemoryKeeper(budgetControl, { BufferSizing.read(appContext) }).also { it.attach() }
    }
}
