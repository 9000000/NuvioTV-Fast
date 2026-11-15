package com.nuvio.tv.core.image

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.Extras
import coil3.imageLoader
import coil3.network.HttpException
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Failed Home tiles wait here for a retry turn. A trigger (network back, Home in front again) starts a round:
 * each waiting tile gets at most one turn per round, a few at a time, one by one while Home has no visible
 * poster loading. A tile that first fails within [lateFailureWindowMs] of a trigger asks for one more round, once
 * per trigger, so a slow failure after the round has passed is not left until the next visit.
 */
internal class PosterRecoveryCoordinator(
    private val perPass: Int = 24,
    private val staggerMs: Long = 150,
    private val settleMs: Long = 1_000,
    private val passGapMs: Long = 3_000,
    private val roundGapMs: Long = 5_000,
    private val lateFailureWindowMs: Long = 60_000,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private class Waiter(val key: String, val rank: () -> Int) {
        val turn = CompletableDeferred<Unit>()
    }

    private val waiters = LinkedHashSet<Waiter>()
    private val served = HashSet<String>()
    private val triggers = Channel<Unit>(Channel.CONFLATED)
    private var triggerPending = false
    private var triggeredAt = 0L
    private var extraRoundAsked = true
    private var passesDone = true

    val waiting: Int get() = synchronized(waiters) { waiters.size }

    fun trigger() {
        synchronized(waiters) { triggerPending = true }
        triggers.trySend(Unit)
    }

    suspend fun awaitTurn(key: String, rank: () -> Int) {
        val waiter = Waiter(key, rank)
        val askExtraRound = synchronized(waiters) {
            waiters += waiter
            val late = passesDone && key !in served && !extraRoundAsked && now() - triggeredAt <= lateFailureWindowMs
            if (late) extraRoundAsked = true
            late
        }
        if (askExtraRound) triggers.trySend(Unit)
        try {
            waiter.turn.await()
        } finally {
            synchronized(waiters) { waiters -= waiter }
        }
    }

    /** Runs rounds until cancelled; [ready] suspends while Home is busy with visible work. */
    suspend fun run(ready: suspend () -> Unit) {
        while (true) {
            triggers.receive()
            synchronized(waiters) {
                served.clear()
                passesDone = false
                if (triggerPending) {
                    triggerPending = false
                    triggeredAt = now()
                    extraRoundAsked = false
                }
            }
            delay(settleMs)
            while (true) {
                val batch = synchronized(waiters) {
                    waiters.filter { it.key !in served }.sortedBy { it.rank() }.take(perPass)
                }
                if (batch.isEmpty()) {
                    synchronized(waiters) { passesDone = true }
                    break
                }
                batch.forEachIndexed { index, waiter ->
                    if (index > 0) delay(staggerMs)
                    ready()
                    synchronized(waiters) { served += waiter.key }
                    waiter.turn.complete(Unit)
                }
                delay(passGapMs)
            }
            delay(roundGapMs)
        }
    }
}

private val HomePosterRecoveryKey = Extras.Key(false)

internal fun ImageRequest.Builder.homePosterRecovery(): ImageRequest.Builder = apply {
    extras[HomePosterRecoveryKey] = true
}

internal val ImageRequest.isHomePosterRecovery: Boolean get() = extras[HomePosterRecoveryKey] == true

/** A 404 or 410 means the server has no such picture; asking again in the same session cannot help. */
internal fun isPermanentImageFailure(error: Throwable): Boolean =
    error is HttpException && (error.response.code == 404 || error.response.code == 410)

/** Home tiles (by memory key, so a changed address is a new tile) whose picture the server said does not exist. */
internal class MissingPosterMemory(private val cap: Int = 2_000) {
    private val keys = LinkedHashSet<String>()

    fun record(key: String, error: Throwable) {
        if (!isPermanentImageFailure(error)) return
        synchronized(keys) {
            if (key !in keys && keys.size >= cap) keys.remove(keys.first())
            keys += key
        }
    }

    operator fun contains(key: String): Boolean = synchronized(keys) { key in keys }
}

object HomePosterRecovery {
    internal val coordinator = PosterRecoveryCoordinator()
    internal val missing = MissingPosterMemory()
    private val watching = AtomicBoolean(false)

    internal fun watchNetwork(context: Context) {
        if (!watching.compareAndSet(false, true)) return
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                private var validated = false

                override fun onAvailable(network: Network) {
                    coordinator.trigger()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val now = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (now && !validated) coordinator.trigger()
                    validated = now
                }

                override fun onLost(network: Network) {
                    validated = false
                }
            })
        }
    }
}

internal suspend fun awaitHomeIdle(viewport: HomePosterViewport) {
    while (viewport.scrolling || viewport.visibleRequests.get() > 0) delay(100)
}

/**
 * Returns once a retry of a failed Home tile has loaded. The tile keeps its no-artwork look until then, and the
 * retry lands in the memory cache, so the card shows it straight away.
 */
suspend fun recoverHomePoster(context: Context, request: ImageRequest, priority: HomePosterPriority?) {
    val retry = request.newBuilder().homePosterRecovery().build()
    recoverPoster(
        key = "${priority?.row}:${request.memoryCacheKey ?: request.data}",
        missingKey = request.memoryCacheKey,
        rank = { priority?.rank() ?: Int.MAX_VALUE },
        coordinator = HomePosterRecovery.coordinator,
        missing = HomePosterRecovery.missing,
    ) { context.imageLoader.execute(retry) is SuccessResult }
}

/** A tile the server answered 404 / 410 for keeps its no-artwork look without further turns this session. */
internal suspend fun recoverPoster(
    key: String,
    missingKey: String?,
    rank: () -> Int,
    coordinator: PosterRecoveryCoordinator,
    missing: MissingPosterMemory,
    load: suspend () -> Boolean,
) {
    while (true) {
        if (missingKey != null && missingKey in missing) awaitCancellation()
        coordinator.awaitTurn(key, rank)
        if (load()) return
    }
}

/** One per Home viewport: retry rounds while Home is in front, started on every return to Home, and the visit summary. */
@Composable
fun HomePosterVisit(viewport: HomePosterViewport) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewport) {
        HomePosterRecovery.watchNetwork(context.applicationContext)
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            HomePosterVisitStats.begin()
            try {
                HomePosterRecovery.coordinator.trigger()
                HomePosterRecovery.coordinator.run(ready = { awaitHomeIdle(viewport) })
            } finally {
                HomePosterVisitStats.end()
            }
        }
    }
}
