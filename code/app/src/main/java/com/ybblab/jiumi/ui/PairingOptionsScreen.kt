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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.ybblab.jiumi.ui.theme.Ink

/**
 * Other ways to enter a pairing code, for phones where Jiumi's notification can't be used while
 * the pairing dialog is open: one card per way, each with its action and its own steps.
 */
@Composable
internal fun PairingOptionsScreen(onBack: () -> Unit, onEnterPairingCode: () -> Unit) {
    SubScreen(stringResource(R.string.pairing_options), onBack, bottomBar = { PrivacyNote() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.pairing_options_intro), style = MaterialTheme.typography.bodySmall,
                color = Ink.secondary, modifier = Modifier.padding(horizontal = 16.dp))
            MethodCard(
                title = stringResource(R.string.pairing_method_split),
                subtitle = stringResource(R.string.pairing_method_split_desc),
                icon = JiumiIcons.SplitScreen,
                steps = listOf(R.string.pairing_split_step1, R.string.pairing_split_step2,
                    R.string.pairing_split_step3, R.string.pairing_split_step4)
            ) { PillButton(stringResource(R.string.pairing_method_split_action), onClick = onEnterPairingCode) }
            TileCard()
        }
    }
}

@Composable
private fun TileCard() {
    val context = LocalContext.current
    val label = stringResource(R.string.pairing_tile_label)
    var tileAdded by rememberSaveable { mutableStateOf(false) }
    // Before Android 13 an app can't offer its tile; the shade's own editor is the only way.
    var manual by rememberSaveable { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) }
    MethodCard(
        title = stringResource(R.string.pairing_method_tile),
        subtitle = stringResource(if (manual) R.string.pairing_tile_manual else R.string.pairing_method_tile_desc),
        icon = JiumiIcons.Tiles,
        steps = listOf(R.string.pairing_tile_step1, R.string.pairing_tile_step2,
            R.string.pairing_tile_step3, R.string.pairing_tile_step4)
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

/** One way to pair: what it is and its action, then its steps on the same numbered rail as first setup. */
@Composable
private fun MethodCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    steps: List<Int>,
    action: @Composable () -> Unit
) {
    Card {
        ListRow(title, subtitle = subtitle, leading = { IconTile(icon) }) {
            Spacer(Modifier.width(8.dp))
            action()
        }
        InsetDivider(start = 16.dp)
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)) {
            steps.forEachIndexed { index, step ->
                StepLine(index, stringResource(step), last = index == steps.lastIndex)
            }
        }
    }
}

@Composable
private fun StepLine(index: Int, text: String, last: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.width(LEADING_SIZE.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(22.dp).background(Ink.fill, CircleShape), contentAlignment = Alignment.Center) {
                Text((index + 1).toString(), style = MaterialTheme.typography.labelMedium, color = Ink.secondary)
            }
            if (!last) Box(Modifier.width(1.5.dp).weight(1f).background(Ink.track))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Ink.primary,
            modifier = Modifier.weight(1f).padding(top = 2.dp, bottom = if (last) 8.dp else 14.dp))
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
