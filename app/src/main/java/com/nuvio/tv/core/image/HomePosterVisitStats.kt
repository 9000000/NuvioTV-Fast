package com.nuvio.tv.core.image

import coil3.decode.DataSource

/** Outcome per on-screen Home tile, keyed by its memory key; warmers and prefetches are not counted. */
internal class HomePosterVisitCounters {
    private class Tile(var firstSource: DataSource?, var failed: Boolean, var missing: Boolean)

    private val tiles = HashMap<Int, Tile>()
    private var retried = 0

    @Synchronized fun success(key: String, source: DataSource, retry: Boolean) {
        // A tile keeps its retry mark, so its later redraws from memory are not new tries.
        if (retry && source != DataSource.MEMORY_CACHE) retried++
        val tile = tiles.getOrPut(key.hashCode()) { Tile(null, false, false) }
        if (tile.firstSource == null) tile.firstSource = source
        tile.missing = false
    }

    @Synchronized fun failure(key: String, retry: Boolean) {
        if (retry) retried++
        val tile = tiles.getOrPut(key.hashCode()) { Tile(null, false, false) }
        tile.failed = true
        tile.missing = true
    }

    @Synchronized fun takeSummary(savedCopies: Int, seconds: Long): String {
        val shown = tiles.values.count { !it.missing }
        val memory = tiles.values.count { it.firstSource == DataSource.MEMORY_CACHE || it.firstSource == DataSource.MEMORY }
        val disk = tiles.values.count { it.firstSource == DataSource.DISK }
        val network = tiles.values.count { it.firstSource == DataSource.NETWORK }
        val failed = tiles.values.count { it.failed }
        val recovered = tiles.values.count { it.failed && !it.missing }
        val missing = tiles.values.count { it.missing }
        val line = "HOME_POSTERS seconds=$seconds tiles=${tiles.size} shown=$shown memory=$memory disk=$disk " +
            "network=$network failed=$failed retried=$retried recovered=$recovered missing=$missing savedCopy=$savedCopies"
        tiles.clear()
        retried = 0
        return line
    }
}

/** One summary line per Home visit: adb logcat -s HomePosterCache | grep HOME_POSTERS */
object HomePosterVisitStats {
    private const val TAG = "HomePosterCache"
    internal val counters = HomePosterVisitCounters()
    private var startedAt = 0L
    private var savedCopiesAtStart = 0

    internal fun begin() {
        startedAt = android.os.SystemClock.elapsedRealtime()
        savedCopiesAtStart = SavedCopyOnErrorFetcher.savedCopiesServed.get()
    }

    internal fun end() {
        val seconds = (android.os.SystemClock.elapsedRealtime() - startedAt) / 1000
        val savedCopies = SavedCopyOnErrorFetcher.savedCopiesServed.get() - savedCopiesAtStart
        android.util.Log.i(TAG, counters.takeSummary(savedCopies, seconds))
    }
}
