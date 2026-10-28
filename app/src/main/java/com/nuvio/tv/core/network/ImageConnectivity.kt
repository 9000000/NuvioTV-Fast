package com.nuvio.tv.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import coil3.annotation.ExperimentalCoilApi
import coil3.network.ConnectivityChecker

internal data class ImageNetworkReport(
    val hasInternetCapability: Boolean,
)

/**
 * Coil 3 only asks the active network for [NetworkCapabilities.NET_CAPABILITY_INTERNET].
 * A local DNS VPN can be that network and omit the capability while Wi-Fi still has it.
 * Coil then sends `Cache-Control: only-if-cached`, OkHttp answers HTTP 504, and posters stay blank.
 * Any reported network with internet is enough. If capabilities are missing, a connected legacy
 * network still counts so a VPN-only report does not hide a working connection.
 */
internal fun imageNetworksAreOnline(
    networks: List<ImageNetworkReport>,
    legacyConnectionActive: Boolean,
): Boolean {
    if (networks.any { it.hasInternetCapability }) return true
    return legacyConnectionActive
}

@OptIn(ExperimentalCoilApi::class)
class ImageConnectivityChecker(
    private val context: Context,
) : ConnectivityChecker {
    override fun isOnline(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val reports = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            @Suppress("DEPRECATION")
            manager.allNetworks.map { network ->
                val capabilities = manager.getNetworkCapabilities(network)
                ImageNetworkReport(
                    hasInternetCapability = capabilities?.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET,
                    ) == true,
                )
            }
        } else {
            emptyList()
        }
        @Suppress("DEPRECATION")
        val legacyConnectionActive = manager.activeNetworkInfo?.isConnected == true
        return imageNetworksAreOnline(reports, legacyConnectionActive)
    }
}
