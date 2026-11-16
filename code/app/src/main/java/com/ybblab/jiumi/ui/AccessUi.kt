// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.access.AccessMethod
import com.ybblab.jiumi.access.ShizukuState

@Composable
fun accessName(method: AccessMethod): String = stringResource(when (method) {
    AccessMethod.WIRELESS -> R.string.access_wireless
    AccessMethod.ROOT -> R.string.access_root
    AccessMethod.SHIZUKU -> R.string.access_shizuku
})

@Composable
fun accessDescription(method: AccessMethod): String = stringResource(when (method) {
    AccessMethod.WIRELESS -> R.string.access_wireless_desc
    AccessMethod.ROOT -> R.string.access_root_desc
    AccessMethod.SHIZUKU -> R.string.access_shizuku_desc
})

/** Where Shizuku stands, in the order the user has to fix it. */
@Composable
fun shizukuStatus(state: ShizukuState): String = stringResource(when {
    state.ready -> R.string.shizuku_ready
    !state.installed && !state.running -> R.string.shizuku_not_installed
    !state.running -> R.string.shizuku_not_running
    !state.supportedVersion -> R.string.shizuku_old
    !state.shellIdentity -> R.string.shizuku_not_shell
    state.permissionBlocked -> R.string.shizuku_blocked
    else -> R.string.shizuku_needs_auth
})

/** The one thing to do next for Shizuku: get it, open it to start it, or allow this app. */
fun shizukuNextAction(state: ShizukuState): Int = when {
    !state.installed && !state.running -> R.string.action_get_shizuku
    !state.running || !state.supportedVersion || !state.shellIdentity || state.permissionBlocked -> R.string.action_open_shizuku
    else -> R.string.action_authorize
}

fun runShizukuNextAction(controller: MonitorController, state: ShizukuState) {
    val shizuku = controller.access.shizuku
    when (shizukuNextAction(state)) {
        R.string.action_get_shizuku -> shizuku.openDownload()
        R.string.action_open_shizuku -> shizuku.openManager()
        else -> controller.authorizeShizuku()
    }
}
