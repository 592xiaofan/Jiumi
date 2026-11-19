// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Sent by the system once this app has been updated or the phone has started; picks an open
 * monitoring session up again when the chosen way can start the monitor without asking anyone.
 */
class ResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> "app update"
            Intent.ACTION_BOOT_COMPLETED -> "boot"
            else -> return
        }
        val pending = goAsync()
        // Let the broadcast go as soon as the monitor is launched: its answer is a broadcast too, and
        // reaches this process only after this one is finished.
        val done = AtomicBoolean(false)
        val finish = { if (done.compareAndSet(false, true)) pending.finish() }
        (context.applicationContext as JiumiApp).controller.resumeInterrupted(trigger, finish).invokeOnCompletion { finish() }
    }
}
