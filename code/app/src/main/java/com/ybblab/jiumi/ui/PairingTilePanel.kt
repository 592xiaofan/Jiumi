// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.drawable.toBitmap
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
            Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 12.dp)) {
                // Jiumi's own mark and name, so the window over the system dialog is plainly ours.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    rememberAppIcon()?.let { Image(it, null, Modifier.size(24.dp).clip(AppShapes.Small)) }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pairing_tile_label), style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium, color = Ink.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        Icon(JiumiIcons.Close, stringResource(R.string.action_close), Modifier.size(18.dp), tint = Ink.secondary)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.padding(end = 8.dp)) { PairingCodePad(controller) }
            }
        }
    }
}

/** The launcher icon as a bitmap, for the places Jiumi signs its own UI. */
@Composable
internal fun rememberAppIcon(): ImageBitmap? {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(192, 192).asImageBitmap() }
            .getOrNull()
    }
}

/** Long enough to see the success mark before the window goes. */
private const val CLOSE_DELAY_MS = 1_600L
