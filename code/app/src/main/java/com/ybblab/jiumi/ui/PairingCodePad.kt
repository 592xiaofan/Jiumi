// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.adb.AirModePairingManager.PairingState
import com.ybblab.jiumi.adb.AirModePairingManager.PairingTrustState
import com.ybblab.jiumi.adb.messageRes
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.AppShapes
import com.ybblab.jiumi.ui.theme.Ink
import com.ybblab.jiumi.ui.theme.StatusSuccess
import com.ybblab.jiumi.ui.theme.StatusWarning

/**
 * The pairing code typed inside Jiumi, for split screen, a small window or the Quick Settings tile:
 * a status line, six digit boxes and a phone keypad. The sixth digit sends the code, so there is no
 * separate button; a wrong code clears the boxes for another try.
 */
@Composable
internal fun PairingCodePad(controller: MonitorController, trailing: @Composable () -> Unit = {}) {
    val state by controller.state.collectAsStateWithLifecycle()
    // A code is valid for one system dialog, so keep it out of saved instance state.
    var code by remember { mutableStateOf("") }
    val pairing = state.pairingState
    val paired = state.trust == PairingTrustState.PAIRED
    val working = pairing == PairingState.PAIRING || pairing == PairingState.CONNECTING
    val editable = !paired && (pairing == PairingState.DISCOVERING || pairing == PairingState.AWAITING_CODE)
    val failure = state.pairingFailure

    fun type(digit: Char) {
        if (!editable || code.length >= 6) return
        code += digit
        if (code.length == 6) {
            controller.submitInAppPairingCode(code)
            code = ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 32.dp).semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                working -> CircularProgressIndicator(Modifier.size(14.dp), color = Ink.secondary, strokeWidth = 2.dp)
                paired && failure == null -> Icon(JiumiIcons.Check, null, Modifier.size(16.dp), tint = StatusSuccess)
                else -> Box(Modifier.size(8.dp).background(
                    when {
                        failure != null -> StatusWarning
                        pairing == PairingState.AWAITING_CODE -> StatusSuccess
                        else -> Ink.tertiary
                    }, CircleShape))
            }
            Text(
                stringResource(when {
                    failure != null -> failure.messageRes()
                    pairing == PairingState.PAIRING -> R.string.pairing_in_progress
                    pairing == PairingState.CONNECTING -> R.string.pairing_connecting
                    paired -> R.string.pairing_paired
                    pairing == PairingState.AWAITING_CODE -> R.string.pairing_code_found
                    else -> R.string.pairing_code_waiting
                }),
                style = MaterialTheme.typography.bodySmall,
                color = if (failure == null) Ink.secondary else StatusWarning,
                modifier = Modifier.weight(1f)
            )
            trailing()
        }
        if (paired && !working) return@Column

        val codeLabel = stringResource(R.string.pairing_in_app_code_label)
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = if (code.isEmpty()) codeLabel else "$codeLabel, ${code.toList().joinToString(" ")}"
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(6) { index ->
                val digit = code.getOrNull(index)
                val next = editable && index == code.length
                Surface(
                    Modifier.weight(1f).height(44.dp), shape = AppShapes.Small,
                    color = if (digit == null) Color.Transparent else Ink.fill,
                    border = BorderStroke(if (next) 1.5.dp else 1.dp,
                        if (next) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (digit != null) Text(digit.toString(), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium, color = Ink.primary)
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        for (row in listOf("123", "456", "789", " 0<")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (key in row) when (key) {
                    ' ' -> Spacer(Modifier.weight(1f))
                    '<' -> PadKey(Modifier.weight(1f), enabled = editable && code.isNotEmpty(), quiet = true,
                        onClick = { code = code.dropLast(1) }) {
                        Icon(JiumiIcons.Backspace, stringResource(R.string.pairing_tile_delete), Modifier.size(20.dp))
                    }
                    else -> PadKey(Modifier.weight(1f), enabled = editable, onClick = { type(key) }) {
                        Text(key.toString(), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        Text(stringResource(R.string.pairing_code_auto), style = MaterialTheme.typography.labelMedium,
            color = Ink.tertiary, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun PadKey(
    modifier: Modifier,
    enabled: Boolean,
    quiet: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick, modifier = modifier.height(48.dp), enabled = enabled,
        shape = AppShapes.Control,
        color = if (quiet) Color.Transparent else Ink.fill,
        contentColor = if (enabled) Ink.primary else Ink.tertiary
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
