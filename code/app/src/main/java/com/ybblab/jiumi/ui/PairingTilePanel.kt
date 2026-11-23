// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.ybblab.jiumi.adb.AirModePairingManager.PairingState
import com.ybblab.jiumi.adb.AirModePairingManager.PairingTrustState
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.AppShapes
import com.ybblab.jiumi.ui.theme.Ink

/** The window a Quick Settings tile opens over the system's pairing dialog; it closes itself once paired. */
@Composable
internal fun PairingTilePanel(controller: MonitorController, onClose: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    val done = state.trust == PairingTrustState.PAIRED && state.pairingState != PairingState.PAIRING &&
        state.pairingState != PairingState.CONNECTING
    LaunchedEffect(done) {
        if (done) {
            kotlinx.coroutines.delay(CLOSE_DELAY_MS)
            onClose()
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            shape = AppShapes.Large,
            color = MaterialTheme.colorScheme.surfaceBright,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp
        ) {
            Box(Modifier.verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 12.dp)) {
                Box(Modifier.padding(end = 8.dp)) {
                    PairingCodePad(controller) {
                        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                            Icon(JiumiIcons.Close, stringResource(R.string.action_close), Modifier.size(18.dp), tint = Ink.secondary)
                        }
                    }
                }
            }
        }
    }
}

/** Long enough to read "Paired" before the window goes. */
private const val CLOSE_DELAY_MS = 1_200L
