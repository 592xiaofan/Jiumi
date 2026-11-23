// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ybblab.jiumi.PairingTileService
import com.ybblab.jiumi.R
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.Ink

@Composable
internal fun PairingOptionsScreen(
    onBack: () -> Unit,
    onOpenWirelessDebugging: () -> Unit,
    onEnterPairingCode: () -> Unit
) {
    val context = LocalContext.current
    val label = stringResource(R.string.pairing_tile_label)
    var tileAdded by rememberSaveable { mutableStateOf(false) }
    var showManualHelp by rememberSaveable { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) }
    SubScreen(stringResource(R.string.pairing_options), onBack, bottomBar = {
        BottomAction(stringResource(R.string.action_open_wireless_debugging), onClick = onOpenWirelessDebugging)
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card {
                ListRow(stringResource(R.string.pairing_tile_title), leading = { IconTile(JiumiIcons.Settings) }) {
                    if (tileAdded) DoneMark()
                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PillButton(stringResource(R.string.pairing_tile_add)) {
                            val statusBar = context.getSystemService(StatusBarManager::class.java)
                            showManualHelp = statusBar == null || runCatching {
                                statusBar.requestAddTileService(
                                    ComponentName(context, PairingTileService::class.java),
                                    label, Icon.createWithResource(context, R.drawable.ic_pairing_tile),
                                    context.mainExecutor
                                ) { result ->
                                    tileAdded = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                                        result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                                    showManualHelp = !tileAdded
                                }
                            }.isFailure
                        }
                    }
                }
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showManualHelp) {
                        Text(stringResource(R.string.pairing_tile_manual), style = MaterialTheme.typography.bodySmall, color = Ink.secondary)
                    }
                    Text(stringResource(R.string.pairing_tile_steps), style = MaterialTheme.typography.bodySmall, color = Ink.secondary)
                }
            }
            Card {
                ListRow(
                    stringResource(R.string.action_enter_pairing_code_in_app),
                    subtitle = stringResource(R.string.pairing_in_app_window_hint),
                    leading = { IconTile(JiumiIcons.Smartphone) },
                    onClick = onEnterPairingCode
                ) { androidx.compose.material3.Icon(JiumiIcons.ChevronRight, null, tint = Ink.tertiary) }
            }
        }
    }
}
