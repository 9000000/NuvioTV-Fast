package com.nuvio.tv.features.livetv

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CatchupSession(
    val channel: LiveTvChannel,
    val program: EpgProgram,
    val offsetMs: Long = 0L,
    val recordingId: String? = null
)

data class RecordingItem(
    val id: String = "",
    val channelKey: String = "",
    val channelName: String = "",
    val channelLogo: String? = null,
    val programTitle: String = "",
    val startedAtMs: Long = 0L,
    val stopAtMs: Long = 0L,
    val isRecording: Boolean = false,
    val durationSec: Int = 0,
    val fromCatchup: Boolean = false,
    val programStartMs: Long = 0L
)

data class ScheduleItem(
    val channel: LiveTvChannel,
    val program: EpgProgram,
    val startMs: Long
)

data class SeriesRule(
    val channel: LiveTvChannel,
    val norm: String
)

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    val epgDatabase: EpgDatabase,
    val playback: LiveTvPlaybackController
) : ViewModel() {

    val repository = LiveTvRepository
    val playbackState: StateFlow<LivePlaybackState> = playback.state

    val uiState: StateFlow<LiveTvUiState> = repository.uiState
    val settings: StateFlow<LiveTvSettings> = uiState.map { it.settings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LiveTvStorage.loadLiveTvSettings())

    val settingsLoaded = MutableStateFlow(true)
    val menuStyleLoaded = MutableStateFlow(true)

    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now.asStateFlow()

    private val _programs = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val programs: StateFlow<Map<String, List<EpgProgram>>> = _programs.asStateFlow()

    private val _userState = MutableStateFlow(LiveUserState())
    val userState: StateFlow<LiveUserState> = _userState.asStateFlow()

    private val _catchup = MutableStateFlow<CatchupSession?>(null)
    val catchup: StateFlow<CatchupSession?> = _catchup.asStateFlow()

    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    private val _schedules = MutableStateFlow<List<ScheduleItem>>(emptyList())
    val schedules: StateFlow<List<ScheduleItem>> = _schedules.asStateFlow()

    private val _seriesRules = MutableStateFlow<List<SeriesRule>>(emptyList())
    val seriesRules: StateFlow<List<SeriesRule>> = _seriesRules.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _pictureShapes = MutableStateFlow<Map<String, String>>(emptyMap())
    val pictureShapes: StateFlow<Map<String, String>> = _pictureShapes.asStateFlow()

    init {
        LiveTvRepository.ensureLoaded()

        // Ticker updating now timestamp
        viewModelScope.launch {
            while (isActive) {
                _now.value = System.currentTimeMillis()
                delay(10_000L)
            }
        }

        // Sync user state with repository favorites and recents
        viewModelScope.launch {
            uiState.collect { state ->
                _userState.update { current ->
                    current.copy(
                        favorites = state.favoriteChannelIds.toList(),
                        recent = state.recentChannelIds,
                        lastChannelKey = state.lastWatchedChannelId
                    )
                }
            }
        }

        // Auto trigger EPG sync and reload programs when channels or playlists change
        viewModelScope.launch(Dispatchers.IO) {
            uiState.collect { state ->
                if (state.channels.isNotEmpty()) {
                    loadEpgWindow(state.channels)
                    LiveTvEpgSyncService.syncAll(state.playlists, state.channels, epgDatabase) {
                        loadEpgWindow(state.channels)
                    }
                }
            }
        }
    }

    fun loadEpgWindow(channels: List<LiveTvChannel>) {
        viewModelScope.launch(Dispatchers.IO) {
            val nowMs = System.currentTimeMillis()
            val fromMs = nowMs - 6 * 3600 * 1000L
            val toMs = nowMs + 18 * 3600 * 1000L
            val keys = channels.map { it.key }
            val loaded = epgDatabase.range(fromMs, toMs, channels = keys)
            _programs.update { prev ->
                val combined = prev.toMutableMap()
                combined.putAll(loaded)
                combined
            }
        }
    }

    fun play(channel: LiveTvChannel, watchedInGroupId: String? = null) {
        viewModelScope.launch {
            _catchup.value = null
            playback.play(channel)
            repository.recordLastWatched(channel, group = watchedInGroupId)
            if (watchedInGroupId != null) {
                _userState.update { current ->
                    current.copy(
                        lastChannelKey = channel.key,
                        lastGroupId = watchedInGroupId,
                        recentGroups = current.recentGroups + (channel.key to watchedInGroupId)
                    )
                }
            }
        }
    }

    fun playCatchup(channel: LiveTvChannel, program: EpgProgram, offsetMs: Long = 0L): Boolean {
        val catchup = channel.catchup ?: return false
        val nowMs = System.currentTimeMillis()
        if (!CatchupUrlBuilder.isAvailable(catchup, program.startMs, nowMs)) return false

        val url = CatchupUrlBuilder.build(
            liveUrl = channel.url,
            catchup = catchup,
            startMs = program.startMs,
            stopMs = program.stopMs,
            nowMs = nowMs
        ) ?: return false

        playback.play(
            channel = channel,
            overrideUrl = url,
            catchupTitle = program.title,
            catchupStartMs = program.startMs
        )
        _catchup.value = CatchupSession(channel, program, offsetMs)
        return true
    }

    fun backToLive(channel: LiveTvChannel) {
        _catchup.value = null
        playback.play(channel)
    }

    fun stopPlayback() {
        playback.stop()
    }

    fun stopRecordingPlayback() {
        _catchup.value = null
        playback.stop()
    }

    fun toggleFavorite(channel: LiveTvChannel) {
        repository.toggleFavoriteChannel(channel.id)
    }

    fun toggleFavorite(channelKey: String) {
        repository.toggleFavoriteChannel(channelKey)
    }

    fun updateSettings(newSettings: LiveTvSettings) {
        repository.updateLiveTvSettings(newSettings)
    }

    fun refreshChannels() {
        repository.refresh(force = true, showLoadingIfHasChannels = true)
    }

    fun toggleRecording(channel: LiveTvChannel): String {
        return "Recording not configured"
    }

    fun toggleScheduled(channel: LiveTvChannel, program: EpgProgram): String {
        return "Scheduled recording"
    }

    fun toggleSeries(channel: LiveTvChannel, program: EpgProgram): String {
        return "Series recording"
    }
}
