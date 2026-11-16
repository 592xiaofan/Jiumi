// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.dns

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's choice to record domains, kept across restarts. On, the DNS-only VPN runs whenever
 * the app's process does; the system asks the user once before the first VPN is set up.
 */
class DomainCapture(private val context: Context) {
    private val prefs = context.getSharedPreferences("jiumi_settings", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    val running: StateFlow<Boolean> get() = DomainVpnService.running
    val strictPrivateDns: StateFlow<Boolean> get() = DomainVpnService.strictPrivateDns

    private val vpns = java.util.concurrent.ConcurrentHashMap.newKeySet<Network>()
    private val _vpnPresent = MutableStateFlow(false)
    /**
     * A VPN is up on the phone. While this app's own is not running it is another app's: domains
     * cannot be recorded then, and a proxy VPN hides where apps really connect.
     */
    val vpnPresent: StateFlow<Boolean> = _vpnPresent.asStateFlow()

    init {
        runCatching {
            context.getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) { vpns += network; _vpnPresent.value = vpns.isNotEmpty() }
                    override fun onLost(network: Network) { vpns -= network; _vpnPresent.value = vpns.isNotEmpty() }
                })
        }
    }

    /** The system's consent screen to show first, or null when the VPN may be set up now. */
    fun consentIntent(): Intent? = runCatching { VpnService.prepare(context) }.getOrNull()

    fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
        if (on) DomainVpnService.start(context) else DomainVpnService.stop(context)
    }

    /** At process start: picks recording up again if it was on and the consent still stands. */
    fun resume() {
        if (_enabled.value && consentIntent() == null) DomainVpnService.start(context)
    }

    /** The VPN was taken away from outside; the choice follows, so it is not set up again unasked. */
    fun onRevoked() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        _enabled.value = false
    }

    private companion object {
        const val KEY_ENABLED = "record_domains"
    }
}
