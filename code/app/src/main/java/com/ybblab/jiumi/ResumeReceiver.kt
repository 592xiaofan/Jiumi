// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
        (context.applicationContext as JiumiApp).controller.resumeInterrupted(trigger).invokeOnCompletion { pending.finish() }
    }
}
