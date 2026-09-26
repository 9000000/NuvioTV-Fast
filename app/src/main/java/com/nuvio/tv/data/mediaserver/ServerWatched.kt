package com.nuvio.tv.data.mediaserver

import android.content.Context
import android.widget.Toast
import com.nuvio.tv.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServerWatchMark(
    val contentId: String,
    val contentType: String,
    val videoId: String?,
    val season: Int?,
    val episode: Int?
)

@Singleton
class ServerWatched internal constructor(
    private val repository: ServerRepository,
    private val matcher: ServerMatcher,
    private val catalog: ServerCatalog,
    private val scope: CoroutineScope,
    private val notifyFailure: suspend () -> Unit
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        repository: ServerRepository,
        matcher: ServerMatcher,
        catalog: ServerCatalog
    ) : this(
        repository = repository,
        matcher = matcher,
        catalog = catalog,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        notifyFailure = {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.servers_watched_failed), Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun apply(marks: List<ServerWatchMark>, played: Boolean, onFailed: suspend (List<ServerWatchMark>) -> Unit): Job? {
        val targets = marks.mapNotNull { mark -> mark.serverRef()?.let { it to mark } }.distinctBy { it.first }
        if (targets.isEmpty()) return null
        return scope.launch {
            val failed = write(targets, played)
            if (failed.isEmpty()) return@launch
            notifyFailure()
            onFailed(failed)
            failed.mapNotNull { ServerItemRef.parse(it.contentId) }.distinct().forEach { resync(it) }
        }
    }

    fun mirror(marks: List<ServerWatchMark>, played: Boolean): Job? {
        val connections = repository.enabledConnections().filter { it.useCatalogMetadata }
        val catalogMarks = marks.filterNot { ServerItemRef.isServerId(it.contentId) }
        if (connections.isEmpty() || catalogMarks.isEmpty()) return null
        return scope.launch {
            var lookupFailures = 0
            val targets = catalogMarks.flatMap { mark ->
                val request = matcher.request(mark.contentType, mark.videoId ?: mark.contentId, mark.season, mark.episode)
                    ?: return@flatMap emptyList()
                connections.filter { matcher.supports(it, request.kind) }.flatMap { connection ->
                    try {
                        matcher.match(connection, request, forceRefresh = false).map { it to mark }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        lookupFailures++
                        emptyList()
                    }
                }
            }.distinctBy { it.first }
            if (lookupFailures > 0 || write(targets, played).isNotEmpty()) notifyFailure()
        }
    }

    internal suspend fun write(targets: List<Pair<ServerItemRef, ServerWatchMark>>, played: Boolean): List<ServerWatchMark> {
        val failed = mutableListOf<ServerWatchMark>()
        targets.chunked(WRITE_BATCH).forEach { batch ->
            if (failed.isNotEmpty()) {
                failed += batch.map { it.second }
                return@forEach
            }
            failed += coroutineScope {
                batch.map { (ref, mark) -> async { mark.takeUnless { setPlayed(ref, played) } } }.awaitAll().filterNotNull()
            }
        }
        return failed
    }

    private fun ServerWatchMark.serverRef(): ServerItemRef? = when {
        !ServerItemRef.isServerId(contentId) -> null
        episode != null -> ServerItemRef.parse(videoId)
        ServerMediaKind.fromContentType(contentType) == ServerMediaKind.MOVIE -> ServerItemRef.parse(contentId)
        else -> null
    }

    private suspend fun setPlayed(ref: ServerItemRef, played: Boolean): Boolean =
        try {
            repository.call(ref.connectionId) { provider, session ->
                if (!provider.supports(ServerCapability.USER_STATE_WRITE)) throw ServerException(ServerFailure.UNSUPPORTED)
                provider.setPlayed(session, ref.itemId, played)
            }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }

    private suspend fun resync(ref: ServerItemRef) {
        try {
            catalog.details(ref)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val WRITE_BATCH = 6
    }
}
