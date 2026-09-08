package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.torrent.TorrServerAddonConfig
import com.nuvio.tv.core.torrent.TorrServerAddonConfigData
import com.nuvio.tv.core.torrent.TorrServerRemoteApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TorrServerSettingsUiState(
    val enabled: Boolean = false,
    val serverUrl: String = "http://127.0.0.1:8090",
    val addonUrl: String = "",
    val authUsername: String = "",
    val authPassword: String = "",
    val preload: Boolean = true,
    val saveToDb: Boolean = false,
    val gst: Boolean = false,
    val isTestingServer: Boolean = false,
    val serverStatusMessage: String? = null,
    val serverStatusSuccess: Boolean? = null,
    val isTestingAddon: Boolean = false,
    val addonStatusMessage: String? = null,
    val addonStatusSuccess: Boolean? = null,
    val isCheckingGst: Boolean = false,
    val gstStatusMessage: String? = null,
    val gstStatusSuccess: Boolean? = null
)

sealed class TorrServerSettingsEvent {
    data class ToggleEnabled(val enabled: Boolean) : TorrServerSettingsEvent()
    data class UpdateServerUrl(val url: String) : TorrServerSettingsEvent()
    data class UpdateAddonUrl(val url: String) : TorrServerSettingsEvent()
    data class UpdateCredentials(val user: String, val pass: String) : TorrServerSettingsEvent()
    data class TogglePreload(val enabled: Boolean) : TorrServerSettingsEvent()
    data class ToggleSaveToDb(val enabled: Boolean) : TorrServerSettingsEvent()
    data class ToggleGst(val enabled: Boolean) : TorrServerSettingsEvent()
    data object TestServerConnection : TorrServerSettingsEvent()
    data object TestAddon : TorrServerSettingsEvent()
    data object CheckGstSupport : TorrServerSettingsEvent()
}

@HiltViewModel
class TorrServerSettingsViewModel @Inject constructor(
    private val addonConfig: TorrServerAddonConfig,
    private val remoteApi: TorrServerRemoteApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(TorrServerSettingsUiState())
    val uiState: StateFlow<TorrServerSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            addonConfig.config.collectLatest { config ->
                _uiState.update {
                    it.copy(
                        enabled = config.enabled,
                        serverUrl = config.serverUrl,
                        addonUrl = config.addonUrl,
                        authUsername = config.authUsername,
                        authPassword = config.authPassword,
                        preload = config.preload,
                        saveToDb = config.saveToDb,
                        gst = config.gst
                    )
                }
            }
        }
    }

    fun onEvent(event: TorrServerSettingsEvent) {
        when (event) {
            is TorrServerSettingsEvent.ToggleEnabled -> {
                addonConfig.setEnabled(event.enabled)
            }
            is TorrServerSettingsEvent.UpdateServerUrl -> {
                addonConfig.setServerUrl(event.url)
                _uiState.update { it.copy(serverStatusMessage = null, serverStatusSuccess = null) }
            }
            is TorrServerSettingsEvent.UpdateAddonUrl -> {
                addonConfig.setAddonUrl(event.url)
                _uiState.update { it.copy(addonStatusMessage = null, addonStatusSuccess = null) }
            }
            is TorrServerSettingsEvent.UpdateCredentials -> {
                addonConfig.setCredentials(event.user, event.pass)
                _uiState.update { it.copy(serverStatusMessage = null, serverStatusSuccess = null) }
            }
            is TorrServerSettingsEvent.TogglePreload -> {
                addonConfig.setPreload(event.enabled)
            }
            is TorrServerSettingsEvent.ToggleSaveToDb -> {
                addonConfig.setSaveToDb(event.enabled)
            }
            is TorrServerSettingsEvent.ToggleGst -> {
                addonConfig.setGst(event.enabled)
                if (event.enabled) {
                    checkGstSupport()
                } else {
                    _uiState.update { it.copy(gstStatusMessage = null, gstStatusSuccess = null) }
                }
            }
            TorrServerSettingsEvent.TestServerConnection -> testServerConnection()
            TorrServerSettingsEvent.TestAddon -> testAddon()
            TorrServerSettingsEvent.CheckGstSupport -> checkGstSupport()
        }
    }

    fun checkGstSupport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingGst = true, gstStatusMessage = null, gstStatusSuccess = null) }
            val current = _uiState.value
            val result = remoteApi.checkGStreamerSupport(
                serverUrl = current.serverUrl,
                username = current.authUsername,
                password = current.authPassword
            )
            result.fold(
                onSuccess = { supported ->
                    _uiState.update {
                        it.copy(
                            isCheckingGst = false,
                            gstStatusMessage = if (supported) "Máy chủ hỗ trợ GStreamer" else "Máy chủ không hỗ trợ GStreamer (thiếu build -gst)",
                            gstStatusSuccess = supported
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isCheckingGst = false,
                            gstStatusMessage = error.message ?: "Không thể kiểm tra GStreamer",
                            gstStatusSuccess = false
                        )
                    }
                }
            )
        }
    }

    fun testServerConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingServer = true, serverStatusMessage = null, serverStatusSuccess = null) }
            val current = _uiState.value
            val result = remoteApi.healthCheck(
                serverUrl = current.serverUrl,
                username = current.authUsername,
                password = current.authPassword
            )
            result.fold(
                onSuccess = { version ->
                    _uiState.update {
                        it.copy(
                            isTestingServer = false,
                            serverStatusMessage = version,
                            serverStatusSuccess = true
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isTestingServer = false,
                            serverStatusMessage = error.message ?: "Unknown error",
                            serverStatusSuccess = false
                        )
                    }
                }
            )
        }
    }

    fun testAddon() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingAddon = true, addonStatusMessage = null, addonStatusSuccess = null) }
            val current = _uiState.value
            val result = remoteApi.testAddonUrl(current.addonUrl)
            result.fold(
                onSuccess = { info ->
                    _uiState.update {
                        it.copy(
                            isTestingAddon = false,
                            addonStatusMessage = info,
                            addonStatusSuccess = true
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isTestingAddon = false,
                            addonStatusMessage = error.message ?: "Invalid addon URL",
                            addonStatusSuccess = false
                        )
                    }
                }
            )
        }
    }
}
