// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.adb.AirModePairingManager.PairingInput
import com.ybblab.jiumi.adb.AirModePairingManager.PairingState
import com.ybblab.jiumi.adb.AirModePairingManager.PairingTrustState
import com.ybblab.jiumi.adb.messageRes
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.AppShapes
import com.ybblab.jiumi.ui.theme.Ink
import com.ybblab.jiumi.ui.theme.StatusWarning

@Composable
internal fun PairingCodeScreen(
    controller: MonitorController,
    onBack: () -> Unit,
    onOpenWirelessDebugging: () -> Unit,
    floatingWindow: Boolean = false,
    compact: Boolean = false
) {
    val state by controller.state.collectAsStateWithLifecycle()
    // A code is valid for one system dialog, so keep it out of saved instance state.
    var code by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pairingState = state.pairingState
    val paired = state.trust == PairingTrustState.PAIRED
    val working = pairingState == PairingState.PAIRING || pairingState == PairingState.CONNECTING
    val canSubmit = !paired && code.length == 6 &&
        pairingState in setOf(PairingState.DISCOVERING, PairingState.AWAITING_CODE)

    LaunchedEffect(controller) {
        if (!controller.isPaired) controller.startPairing(PairingInput.APP)
    }

    fun submit() {
        if (canSubmit && controller.submitInAppPairingCode(code)) {
            code = ""
            focus.clearFocus()
            keyboard?.hide()
        }
    }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
    ) {
        if (!compact) {
            Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(JiumiIcons.ArrowBack, stringResource(R.string.action_back), tint = Ink.primary)
                }
                Text(
                    stringResource(if (floatingWindow) R.string.pairing_window_title else R.string.row_pairing),
                    style = MaterialTheme.typography.titleMedium, color = Ink.primary
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(top = if (compact) 8.dp else 0.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!working && !paired && (!floatingWindow || compact)) {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = code,
                                onValueChange = { code = it.filter { digit -> digit in '0'..'9' }.take(6) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text(stringResource(R.string.pairing_in_app_code_label)) },
                                textStyle = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = FontFamily.Monospace, letterSpacing = 2.sp
                                ),
                                shape = AppShapes.Control,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submit() })
                            )
                            Button(
                                onClick = ::submit,
                                enabled = canSubmit,
                                modifier = Modifier.fillMaxWidth(),
                                shape = AppShapes.Large,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Text(stringResource(if (compact) R.string.pairing_tile_submit else R.string.action_start_pairing))
                            }
                        }
                    }
                }
                if (floatingWindow && !compact && !paired && !working) {
                    Text(stringResource(R.string.pairing_window_instructions), style = MaterialTheme.typography.bodyMedium,
                        color = Ink.secondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
                }
                if (!compact && !working && !paired) {
                    TextButton(onClick = onOpenWirelessDebugging) {
                        Text(stringResource(R.string.action_open_wireless_debugging), color = Ink.primary)
                    }
                }
                val failure = state.pairingFailure
                val status = when {
                    failure != null -> failure.messageRes()
                    pairingState == PairingState.PAIRING -> R.string.pairing_in_progress
                    pairingState == PairingState.CONNECTING -> R.string.pairing_connecting
                    paired -> R.string.pairing_paired
                    pairingState == PairingState.AWAITING_CODE -> R.string.pairing_in_app_ready
                    else -> R.string.pairing_in_app_window_hint
                }
                if (failure != null || working || paired || !floatingWindow) Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (working) CircularProgressIndicator(Modifier.size(16.dp), color = Ink.secondary, strokeWidth = 2.dp)
                    else if (paired && failure == null) Icon(JiumiIcons.Check, null, Modifier.size(16.dp), tint = Ink.primary)
                    Text(stringResource(status), style = MaterialTheme.typography.bodySmall,
                        color = if (failure == null) Ink.secondary else StatusWarning)
                }
                if (paired && !working) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.action_done), color = Ink.primary)
                    }
                }
            }
        }
    }
}
