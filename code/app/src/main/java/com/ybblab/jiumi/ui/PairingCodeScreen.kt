// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.adb.AirModePairingManager.PairingInput
import com.ybblab.jiumi.adb.AirModePairingManager.PairingState
import com.ybblab.jiumi.adb.AirModePairingManager.PairingTrustState
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.Ink

/** The code pad on a page of its own, kept compact for one half of a split screen or a small window. */
@Composable
internal fun PairingCodeScreen(
    controller: MonitorController,
    onBack: () -> Unit,
    onOpenWirelessDebugging: () -> Unit
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val paired = state.trust == PairingTrustState.PAIRED
    val working = state.pairingState == PairingState.PAIRING || state.pairingState == PairingState.CONNECTING

    LaunchedEffect(controller) {
        if (!controller.isPaired) controller.startPairing(PairingInput.APP)
    }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(JiumiIcons.ArrowBack, stringResource(R.string.action_back), tint = Ink.primary)
            }
            Text(stringResource(R.string.pairing_code_title), style = MaterialTheme.typography.titleMedium,
                color = Ink.primary, modifier = Modifier.weight(1f))
            when {
                paired && !working -> TextButton(onClick = onBack) {
                    Text(stringResource(R.string.action_done), color = Ink.primary)
                }
                !paired && !working -> TextButton(onClick = onOpenWirelessDebugging) {
                    Text(stringResource(R.string.action_open_wireless_debugging), color = Ink.primary)
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(bottom = 12.dp)) {
                PairingCodePad(controller)
            }
        }
    }
}
