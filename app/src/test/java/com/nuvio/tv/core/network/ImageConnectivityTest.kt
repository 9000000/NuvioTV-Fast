package com.nuvio.tv.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageConnectivityTest {

    @Test
    fun `wifi internet stays online when the active vpn omits it`() {
        val online = imageNetworksAreOnline(
            networks = listOf(
                ImageNetworkReport(hasInternetCapability = false),
                ImageNetworkReport(hasInternetCapability = true),
            ),
            legacyConnectionActive = true,
        )
        assertTrue(online)
    }

    @Test
    fun `connected legacy network stays online when capabilities omit internet`() {
        val online = imageNetworksAreOnline(
            networks = listOf(ImageNetworkReport(hasInternetCapability = false)),
            legacyConnectionActive = true,
        )
        assertTrue(online)
    }

    @Test
    fun `no network is offline`() {
        val online = imageNetworksAreOnline(
            networks = emptyList(),
            legacyConnectionActive = false,
        )
        assertFalse(online)
    }

    @Test
    fun `capabilities without internet are offline when nothing is connected`() {
        val online = imageNetworksAreOnline(
            networks = listOf(ImageNetworkReport(hasInternetCapability = false)),
            legacyConnectionActive = false,
        )
        assertFalse(online)
    }
}
