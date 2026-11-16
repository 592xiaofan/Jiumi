// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.access

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How the monitor gets the shell identity it runs as. Each way ends in the same place: the monitor
 * running as the Android shell (uid 2000), started from this APK; only the door differs.
 * - [WIRELESS]: Wireless debugging, paired once from this phone. No root, no other app.
 * - [ROOT]: the phone's root manager (Magisk, KernelSU, APatch) drops to the shell identity.
 * - [SHIZUKU]: the Shizuku app, already running as the shell, starts it on request.
 */
enum class AccessMethod { WIRELESS, ROOT, SHIZUKU }

/** The chosen [AccessMethod], kept across restarts, and the two ways besides Wireless debugging. */
class Access(context: Context) {
    private val prefs = context.getSharedPreferences("jiumi_settings", Context.MODE_PRIVATE)
    private val _method = MutableStateFlow(
        AccessMethod.entries.firstOrNull { it.name == prefs.getString(KEY_METHOD, null) } ?: AccessMethod.WIRELESS
    )
    val method: StateFlow<AccessMethod> = _method.asStateFlow()
    val root = RootShell(context)
    val shizuku = ShizukuShell(context)

    fun select(method: AccessMethod) {
        prefs.edit().putString(KEY_METHOD, method.name).apply()
        _method.value = method
    }

    private companion object {
        const val KEY_METHOD = "access_method"
    }
}
