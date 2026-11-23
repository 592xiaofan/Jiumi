// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.adb.AirModePairingManager.PairingState
import com.ybblab.jiumi.adb.AirModePairingManager.PairingTrustState
import com.ybblab.jiumi.adb.messageRes
import com.ybblab.jiumi.ui.theme.AppShapes
import com.ybblab.jiumi.ui.theme.Ink
import com.ybblab.jiumi.ui.theme.StatusWarning

@Composable
internal fun PairingTilePanel(controller: MonitorController, onClose: () -> Unit) {
    val state by controller.state.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    val pairing = state.pairingState
    val paired = state.trust == PairingTrustState.PAIRED
    val working = pairing == PairingState.PAIRING || pairing == PairingState.CONNECTING
    val editable = pairing == PairingState.DISCOVERING || pairing == PairingState.AWAITING_CODE
    val failure = state.pairingFailure
    val status = when {
        failure != null -> failure.messageRes()
        pairing == PairingState.PAIRING -> R.string.pairing_in_progress
        pairing == PairingState.CONNECTING -> R.string.pairing_connecting
        paired -> R.string.pairing_paired
        else -> null
    }

    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            shape = AppShapes.Large,
            color = MaterialTheme.colorScheme.surfaceBright,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 8.dp
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!working && !paired) {
                    val codeLabel = stringResource(R.string.pairing_in_app_code_label)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            Modifier.weight(1f).clearAndSetSemantics {
                                contentDescription = if (code.isEmpty()) codeLabel else "$codeLabel, ${code.toList().joinToString(" ")}"
                            },
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(6) { index ->
                                val digit = code.getOrNull(index)
                                val next = editable && index == code.length
                                Surface(
                                    Modifier.weight(1f).height(34.dp), shape = AppShapes.Small,
                                    color = if (digit == null) Color.Transparent else Ink.fill,
                                    border = BorderStroke(1.dp, if (next) Ink.secondary else MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (digit != null) Text(digit.toString(), style = MaterialTheme.typography.titleLarge,
                                            color = Ink.primary)
                                    }
                                }
                            }
                        }
                        PairingTileKey(stringResource(R.string.pairing_tile_delete), Modifier,
                            enabled = editable && code.isNotEmpty()) { code = code.dropLast(1) }
                    }
                    for (digits in listOf("12345", "67890")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (digit in digits) {
                                PairingTileKey(digit.toString(), Modifier.weight(1f), enabled = editable && code.length < 6) {
                                    code += digit
                                }
                            }
                        }
                    }
                    PairingTileKey(stringResource(R.string.pairing_tile_submit), Modifier.fillMaxWidth(),
                        enabled = editable && code.length == 6, primary = true) {
                        if (controller.submitInAppPairingCode(code)) code = ""
                    }
                }
                if (status != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (working) CircularProgressIndicator(Modifier.size(16.dp), color = Ink.secondary, strokeWidth = 2.dp)
                        Text(stringResource(status), style = MaterialTheme.typography.bodySmall,
                            color = if (failure == null) Ink.secondary else StatusWarning)
                    }
                }
                if (paired && !working) {
                    TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.action_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingTileKey(
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
        shape = AppShapes.Control,
        color = if (primary && enabled) MaterialTheme.colorScheme.primary else Ink.fill,
        border = if (primary && !enabled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        contentColor = when {
            !enabled -> Ink.tertiary
            primary -> MaterialTheme.colorScheme.onPrimary
            else -> Ink.primary
        }
    ) {
        Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
            Text(label, style = if (label.length == 1 && label[0] in '0'..'9') MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.labelLarge)
        }
    }
}
