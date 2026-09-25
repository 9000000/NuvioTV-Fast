package com.nuvio.tv.core.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Custom DNS that reorders resolved addresses to place IPv4 (Inet4Address)
 * before IPv6 (Inet6Address). This avoids 60s timeout delays on networks
 * with broken IPv6 routing (issue #651).
 */
class IPv4FirstDns(private val delegate: Dns = Dns.SYSTEM) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = try {
            delegate.lookup(hostname)
        } catch (e: Exception) {
            val fallback = DynamicHostFallback.getFallbackHost(hostname)
            if (!fallback.isNullOrBlank() && !fallback.equals(hostname, ignoreCase = true)) {
                delegate.lookup(fallback)
            } else {
                throw e
            }
        }
        return addresses.sortedBy { if (it is Inet4Address) 0 else 1 }
    }
}
