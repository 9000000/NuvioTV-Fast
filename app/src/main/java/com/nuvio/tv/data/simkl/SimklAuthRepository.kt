package com.nuvio.tv.data.simkl

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimklAuthRepository(
    private val apiClient: SimklApiClient,
    private val configuration: SimklApiConfiguration,
    private val storage: SimklAuthStorage,
    private val nowEpochMs: () -> Long = System::currentTimeMillis
) {
    @Inject
    constructor(
        apiClient: SimklApiClient,
        configuration: SimklApiConfiguration,
        storage: SimklAuthStorage
    ) : this(apiClient, configuration, storage, System::currentTimeMillis)

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    val state: StateFlow<SimklAuthState> = storage.state

    fun hasRequiredCredentials(): Boolean = configuration.clientId.isNotBlank()

    suspend fun startPinAuth(): SimklPinSession = mutex.withLock {
        val authScope = storage.currentScope()
        if (!hasRequiredCredentials()) throw SimklAuthException(SimklAuthError.MISSING_CLIENT_ID)

        // Try OAuth 2.0 V2 device authorization first (POST /oauth2/device)
        val v2Session = runCatching {
            val response = executeAuthRequest(
                SimklApiRequest(
                    method = SimklHttpMethod.POST,
                    path = "/oauth2/device",
                    body = json.encodeToString(mapOf("client_id" to configuration.clientId)),
                    requiresAuthentication = false,
                    retryPolicy = SimklRetryPolicy.NEVER
                )
            )
            val payload = decodePinResponse(response.body)
            val userCode = payload.userCode?.trim()?.takeIf(String::isNotBlank)
            val verificationUri = (payload.verificationUri ?: payload.verificationUrl)
                ?.trim()
                ?.takeIf(String::isNotBlank)
            val expiresIn = payload.expiresIn?.takeIf { it > 0L }
            val interval = payload.interval?.coerceAtLeast(1) ?: 5
            val deviceCode = payload.deviceCode?.trim()?.takeIf(String::isNotBlank)
            if (userCode != null && verificationUri != null && expiresIn != null && deviceCode != null) {
                SimklPinSession(
                    userCode = userCode,
                    verificationUri = verificationUri,
                    expiresAtEpochMs = nowEpochMs() + expiresIn * 1_000L,
                    intervalSeconds = interval,
                    deviceCode = deviceCode
                )
            } else null
        }.getOrNull()

        val session = v2Session ?: run {
            // Fallback to legacy V1 pin authorization (GET /oauth/pin)
            val response = executeAuthRequest(
                SimklApiRequest(SimklHttpMethod.GET, "/oauth/pin", requiresAuthentication = false)
            )
            val payload = decodePinResponse(response.body)
            val userCode = payload.userCode?.trim()?.takeIf(String::isNotBlank)
            val verificationUri = (payload.verificationUri ?: payload.verificationUrl)
                ?.trim()
                ?.takeIf(String::isNotBlank)
            val expiresIn = payload.expiresIn?.takeIf { it > 0L }
            val interval = payload.interval?.coerceAtLeast(1)
            if (payload.result != "OK" || userCode == null || verificationUri == null || expiresIn == null || interval == null) {
                throw SimklAuthException(SimklAuthError.INVALID_PIN_RESPONSE)
            }
            SimklPinSession(
                userCode = userCode,
                verificationUri = verificationUri,
                expiresAtEpochMs = nowEpochMs() + expiresIn * 1_000L,
                intervalSeconds = interval,
                deviceCode = null
            )
        }

        if (!storage.savePinSession(session, authScope)) {
            throw SimklAuthException(SimklAuthError.PIN_INVALIDATED)
        }
        session
    }

    suspend fun pollPin(): SimklPinPollResult = mutex.withLock {
        val authScope = storage.currentScope()
        val session = storage.state.value.pinSession ?: return@withLock SimklPinPollResult.Invalidated
        if (nowEpochMs() >= session.expiresAtEpochMs) {
            storage.clearPinSession(SimklAuthError.PIN_EXPIRED, authScope)
            return@withLock SimklPinPollResult.Expired
        }
        if (!PIN_PATTERN.matches(session.userCode)) {
            storage.clearPinSession(SimklAuthError.PIN_INVALIDATED, authScope)
            return@withLock SimklPinPollResult.Invalidated
        }

        // V2 OAuth 2.0 Device Flow Polling
        if (!session.deviceCode.isNullOrBlank()) {
            val body = json.encodeToString(
                mapOf(
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id" to configuration.clientId,
                    "device_code" to session.deviceCode
                )
            )
            return@withLock try {
                val response = executeAuthRequest(
                    SimklApiRequest(
                        method = SimklHttpMethod.POST,
                        path = "/oauth2/token",
                        body = body,
                        requiresAuthentication = false,
                        retryPolicy = SimklRetryPolicy.NEVER
                    )
                )
                val payload = decodePinResponse(response.body)
                val token = payload.accessToken?.trim()?.takeIf(String::isNotBlank)
                if (token != null) {
                    if (!storage.completePinAuthorization(token, authScope)) {
                        SimklPinPollResult.Invalidated
                    } else {
                        try {
                            refreshUserSettings(scope = authScope)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Throwable) {
                            Unit
                        }
                        SimklPinPollResult.Authorized
                    }
                } else {
                    SimklPinPollResult.Invalidated
                }
            } catch (error: SimklApiException) {
                when (error.errorCode) {
                    "authorization_pending", "slow_down" -> SimklPinPollResult.Pending
                    "expired_token" -> {
                        storage.clearPinSession(SimklAuthError.PIN_EXPIRED, authScope)
                        SimklPinPollResult.Expired
                    }
                    "access_denied" -> {
                        storage.clearPinSession(SimklAuthError.PIN_INVALIDATED, authScope)
                        SimklPinPollResult.Invalidated
                    }
                    else -> throw SimklAuthException(SimklAuthError.INVALID_PIN_RESPONSE, error)
                }
            }
        }

        // Legacy V1 Pin Polling
        val response = executeAuthRequest(
            SimklApiRequest(
                method = SimklHttpMethod.GET,
                path = "/oauth/pin/${session.userCode}",
                requiresAuthentication = false
            )
        )
        val payload = decodePinResponse(response.body)
        if (!payload.deviceCode.isNullOrBlank()) {
            storage.clearPinSession(SimklAuthError.PIN_INVALIDATED, authScope)
            return@withLock SimklPinPollResult.Invalidated
        }
        val token = payload.accessToken?.trim()?.takeIf(String::isNotBlank)
        if (payload.result == "OK" && token != null) {
            if (!storage.completePinAuthorization(token, authScope)) {
                return@withLock SimklPinPollResult.Invalidated
            }
            try {
                refreshUserSettings(scope = authScope)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                Unit
            }
            return@withLock SimklPinPollResult.Authorized
        }
        if (payload.result == "KO") return@withLock SimklPinPollResult.Pending
        throw SimklAuthException(SimklAuthError.INVALID_PIN_RESPONSE)
    }

    suspend fun refreshUserSettings(
        activityWatermark: String? = null,
        scope: SimklAuthScope = storage.currentScope()
    ): String? {
        if (!storage.isCurrent(scope) || !storage.state.value.isAuthenticated) return null
        val response = apiClient.execute(
            request = SimklApiRequest(SimklHttpMethod.POST, "/users/settings"),
            expectedAuthScope = scope
        )
        val settings = runCatching { json.decodeFromString<SimklUserSettingsResponse>(response.body) }.getOrNull()
            ?: return null
        val username = settings.user?.name?.trim()?.takeIf(String::isNotBlank)
        val saved = storage.saveIdentity(
            username = username,
            accountId = settings.account?.id,
            settingsActivityWatermark = activityWatermark,
            scope = scope
        )
        return username.takeIf { saved }
    }

    suspend fun synchronizeUserSettings(activityWatermark: String?) {
        val authScope = storage.currentScope()
        if (!storage.state.value.isAuthenticated) return
        try {
            when (simklSettingsRefreshAction(storage.state.value, activityWatermark)) {
                SimklSettingsRefreshAction.NONE -> Unit
                SimklSettingsRefreshAction.RECORD_WATERMARK -> {
                    storage.recordSettingsActivityWatermark(requireNotNull(activityWatermark), authScope)
                }
                SimklSettingsRefreshAction.FETCH -> refreshUserSettings(activityWatermark, authScope)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Unit
        }
    }

    fun cancelPinAuth() = storage.clearPinSession()

    fun disconnect() = storage.clearAuth()

    private suspend fun executeAuthRequest(request: SimklApiRequest): SimklApiResponse = try {
        apiClient.execute(request)
    } catch (error: CancellationException) {
        throw error
    } catch (error: SimklApiException) {
        throw error
    } catch (error: SimklAuthException) {
        throw error
    } catch (error: Throwable) {
        throw SimklAuthException(SimklAuthError.NETWORK, error)
    }

    private fun decodePinResponse(body: String): SimklPinResponse =
        runCatching { json.decodeFromString<SimklPinResponse>(body) }
            .getOrElse { throw SimklAuthException(SimklAuthError.INVALID_PIN_RESPONSE, it) }

    private companion object {
        val PIN_PATTERN = Regex("[A-Za-z0-9-]{4,16}")
    }
}
