package com.nuvio.tv.core.torrent

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrServerRemoteApiUrlNormalizationTest {

    @Test
    fun normalizeServerUrl_prependsHttpWhenSchemeIsMissing() {
        assertEquals("http://192.168.1.100:8090", TorrServerRemoteApi.normalizeServerUrl("192.168.1.100:8090"))
        assertEquals("http://localhost:8090", TorrServerRemoteApi.normalizeServerUrl("localhost:8090"))
        assertEquals("http://10.0.0.5:8090", TorrServerRemoteApi.normalizeServerUrl("  10.0.0.5:8090/  "))
    }

    @Test
    fun normalizeServerUrl_preservesExistingHttpAndHttpsSchemes() {
        assertEquals("http://192.168.1.100:8090", TorrServerRemoteApi.normalizeServerUrl("http://192.168.1.100:8090/"))
        assertEquals("https://my-torrserver.ts.net", TorrServerRemoteApi.normalizeServerUrl("https://my-torrserver.ts.net/"))
        assertEquals("HTTP://127.0.0.1:8090", TorrServerRemoteApi.normalizeServerUrl("HTTP://127.0.0.1:8090"))
    }

    @Test
    fun normalizeServerUrl_handlesEmptyAndNull() {
        assertEquals("", TorrServerRemoteApi.normalizeServerUrl(null))
        assertEquals("", TorrServerRemoteApi.normalizeServerUrl(""))
        assertEquals("", TorrServerRemoteApi.normalizeServerUrl("   "))
    }

    @Test
    fun healthCheck_withRawIpWithoutScheme_doesNotThrowException() = runTest {
        val mockConfig = mockk<TorrServerAddonConfig>(relaxed = true) {
            every { config } returns flowOf(TorrServerAddonConfigData())
        }
        val api = TorrServerRemoteApi(mockConfig)

        // Calling healthCheck with an unreachable or schemeless IP should return Result.failure, NEVER crash
        val result = api.healthCheck(serverUrl = "127.0.0.1:9999")
        assertTrue(result.isFailure)
    }

    @Test
    fun checkGStreamerSupport_withRawIpWithoutScheme_doesNotThrowException() = runTest {
        val mockConfig = mockk<TorrServerAddonConfig>(relaxed = true) {
            every { config } returns flowOf(TorrServerAddonConfigData())
        }
        val api = TorrServerRemoteApi(mockConfig)

        val result = api.checkGStreamerSupport(serverUrl = "127.0.0.1:9999")
        assertTrue(result.isFailure)
    }
}
