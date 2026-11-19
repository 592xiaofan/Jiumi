// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat

/**
 * Keeps the persistent notification while a monitoring session runs: how long it has run, the
 * session's summary when the user wants it, a tap to open the record and an action to stop. The
 * monitor itself runs outside the app; this only shows it. What it says is built by [MonitorNotice].
 */
class MonitorNotificationService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (MonitorNotice.isStop(intent)) {
            (application as JiumiApp).controller.stopMonitor()
            stop()
            return START_NOT_STICKY
        }
        val running = shown ?: MonitorNotice.Running(System.currentTimeMillis(), null)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, MonitorNotice.RUNNING_ID, MonitorNotice.running(this, running), type)
        return START_NOT_STICKY
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        /** What the notification shows now; null while it is not shown. */
        @Volatile private var shown: MonitorNotice.Running? = null
        private val lock = Any()

        /** Shows the notification for [running]; refused while the app is in the background. */
        fun show(context: Context, running: MonitorNotice.Running): Boolean = synchronized(lock) {
            shown = running
            runCatching { context.startForegroundService(Intent(context, MonitorNotificationService::class.java)) }
                .onFailure { shown = null }.isSuccess
        }

        /** Changes what the shown notification says; nothing when it is not shown for the same session. */
        fun update(context: Context, running: MonitorNotice.Running) = synchronized(lock) {
            if (shown?.since != running.since || shown == running) return@synchronized
            shown = running
            MonitorNotice.update(context, running)
        }

        fun hide(context: Context) = synchronized(lock) {
            shown = null
            context.stopService(Intent(context, MonitorNotificationService::class.java))
        }
    }
}
