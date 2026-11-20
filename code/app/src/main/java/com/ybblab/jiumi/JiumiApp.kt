// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.ybblab.jiumi.adb.AdbPairingHost
import com.ybblab.jiumi.adb.AirModeNotificationController
import com.ybblab.jiumi.adb.AirModePairingManager
import com.ybblab.jiumi.adb.PairingLog
import com.ybblab.jiumi.data.AccessRules
import com.ybblab.jiumi.data.JiumiStore
import com.ybblab.jiumi.probe.ProbeClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class JiumiApp : Application(), AdbPairingHost {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log by lazy { PairingLog(File(filesDir, "logs/adb.log")) }
    val notifications by lazy { AirModeNotificationController(this, log) }
    override val pairingManager by lazy { AirModePairingManager(this, appScope, notifications, log) }
    val probeClient by lazy { ProbeClient(this) }
    val rules by lazy { AccessRules.load(this) }
    val monitoring by lazy { com.ybblab.jiumi.data.MonitoringPreferences(this) }
    val store by lazy { JiumiStore(this, monitoring.exclusions) }
    val preferences by lazy { AppPreferences(this) }
    val domains by lazy { com.ybblab.jiumi.dns.DomainCapture(this) }
    val access by lazy { com.ybblab.jiumi.access.Access(this) }
    val controller by lazy { MonitorController(this, appScope, pairingManager, probeClient, rules, store, access, monitoring) }

    private val wifiNetworks = ConcurrentHashMap.newKeySet<Network>()

    override fun onCreate() {
        super.onCreate()
        preferences.applyTheme()
        probeClient.register()
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.data?.schemeSpecificPart in monitoring.exclusions.value.active) {
                    controller.refreshMonitoringPackages()
                }
            }
        }, IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }, ContextCompat.RECEIVER_EXPORTED)
        watchWifi()
        domains.resume()
    }

    /** Pairing and the ADB connection run over Wi-Fi; the manager only needs to know if it exists. */
    private fun watchWifi() {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        connectivity.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifiNetworks += network
                pairingManager.onWifiNetworkChanged("available", true)
            }

            override fun onLost(network: Network) {
                wifiNetworks -= network
                pairingManager.onWifiNetworkChanged("lost", wifiNetworks.isNotEmpty())
            }
        })
    }
}
