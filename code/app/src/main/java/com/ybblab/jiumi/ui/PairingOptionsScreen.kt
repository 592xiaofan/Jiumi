// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ybblab.jiumi.PairingTileService
import com.ybblab.jiumi.R
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.AppShapes
import com.ybblab.jiumi.ui.theme.Ink

/**
 * The ways to enter a pairing code: Jiumi's notification by default, then for systems where the
 * shade can't be used with the pairing dialog open, split screen or a small window, and the tile.
 */
@Composable
internal fun PairingOptionsScreen(onBack: () -> Unit, onEnterPairingCode: () -> Unit) {
    SubScreen(stringResource(R.string.pairing_methods), onBack, bottomBar = { PrivacyNote() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(stringResource(R.string.pairing_options_intro), style = MaterialTheme.typography.bodySmall,
                color = Ink.secondary, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp))
            Card {
                ListRow(
                    stringResource(R.string.pairing_method_notification),
                    subtitle = stringResource(R.string.pairing_method_notification_desc),
                    leading = { IconTile(JiumiIcons.Notifications) }
                ) { DefaultTag() }
                InsetDivider()
                ListRow(
                    stringResource(R.string.pairing_method_split),
                    subtitle = stringResource(R.string.pairing_method_split_desc),
                    leading = { IconTile(JiumiIcons.SplitScreen) }
                ) { PillButton(stringResource(R.string.pairing_method_split_action), onClick = onEnterPairingCode) }
                InsetDivider()
                TileRow()
            }
            GroupFooter(stringResource(R.string.pairing_options_why))
            GroupTitle(stringResource(R.string.pairing_split_how), Modifier.padding(top = 20.dp))
            Card {
                Text(stringResource(R.string.pairing_split_steps), style = MaterialTheme.typography.bodySmall,
                    color = Ink.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            GroupTitle(stringResource(R.string.pairing_tile_how), Modifier.padding(top = 20.dp))
            Card {
                Text(stringResource(R.string.pairing_tile_steps), style = MaterialTheme.typography.bodySmall,
                    color = Ink.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun TileRow() {
    val context = LocalContext.current
    val label = stringResource(R.string.pairing_tile_label)
    var tileAdded by rememberSaveable { mutableStateOf(false) }
    // Before Android 13 an app can't offer its tile; the shade's own editor is the only way.
    var manual by rememberSaveable { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) }
    ListRow(
        stringResource(R.string.pairing_method_tile),
        subtitle = stringResource(if (manual) R.string.pairing_tile_manual else R.string.pairing_method_tile_desc),
        leading = { IconTile(JiumiIcons.Tiles) }
    ) {
        when {
            tileAdded -> DoneMark()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> PillButton(stringResource(R.string.pairing_tile_add)) {
                val statusBar = context.getSystemService(StatusBarManager::class.java)
                manual = statusBar == null || runCatching {
                    statusBar.requestAddTileService(
                        ComponentName(context, PairingTileService::class.java),
                        label, Icon.createWithResource(context, R.drawable.ic_pairing_tile),
                        context.mainExecutor
                    ) { result ->
                        tileAdded = result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                        manual = !tileAdded
                    }
                }.isFailure
            }
        }
    }
}

@Composable
private fun DefaultTag() {
    Surface(shape = AppShapes.Small, color = Ink.fill) {
        Text(stringResource(R.string.pairing_method_default), style = MaterialTheme.typography.labelMedium,
            color = Ink.secondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

/** "Your data stays on this phone", the last line of a setup page. */
@Composable
internal fun PrivacyNote() {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding()
            .padding(top = 6.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(JiumiIcons.Lock, null, tint = Ink.tertiary,
            modifier = Modifier.padding(end = 4.dp).size(12.dp))
        Text(stringResource(R.string.setup_privacy), style = MaterialTheme.typography.labelMedium, color = Ink.tertiary)
    }
}
