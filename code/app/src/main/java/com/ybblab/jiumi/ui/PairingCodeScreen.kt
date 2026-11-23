// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.MultiWindowModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.adb.AirModePairingManager.PairingInput
import com.ybblab.jiumi.adb.AirModePairingManager.PairingState
import com.ybblab.jiumi.adb.AirModePairingManager.PairingTrustState
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.AppShapes
import com.ybblab.jiumi.ui.theme.Ink
import com.ybblab.jiumi.ui.theme.StatusSuccess

/**
 * The code pad on a page of its own, for one half of a split screen or a small window. Until the
 * system's code dialog shows up, a bar at the bottom says what has to be true first: the dialog
 * and this pad on screen together. Its button opens Wireless debugging, beside Jiumi when split.
 */
@Composable
internal fun PairingCodeScreen(
    controller: MonitorController,
    onBack: () -> Unit,
    onOpenWirelessDebugging: () -> Unit
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val paired = state.trust == PairingTrustState.PAIRED
    val waiting = !paired && state.pairingState == PairingState.DISCOVERING
    val multiWindow = rememberMultiWindowMode()

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
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(Modifier.widthIn(max = 400.dp).fillMaxWidth().padding(bottom = 12.dp)) {
                PairingCodePad(controller, onDone = onBack)
            }
        }
        // Once the dialog is found the pad's own status line takes over, and the keys get the room.
        AnimatedVisibility(waiting) {
            SameScreenBar(multiWindow, onOpenWirelessDebugging)
        }
    }
}

@Composable
private fun SameScreenBar(multiWindow: Boolean, onOpenWirelessDebugging: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (multiWindow) {
            // A small window has little room: one line, then the button.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(JiumiIcons.Check, null, Modifier.size(14.dp), tint = StatusSuccess)
                Text(stringResource(R.string.pairing_split_ready_desc), style = MaterialTheme.typography.bodySmall,
                    color = Ink.secondary)
            }
        } else {
            Row(
                Modifier.fillMaxWidth().background(Ink.fill, AppShapes.Card).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(32.dp).background(MaterialTheme.colorScheme.surface, AppShapes.Small),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(JiumiIcons.SplitScreen, null, Modifier.size(18.dp), tint = Ink.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.pairing_split_needed_title), style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium, color = Ink.primary)
                    Text(stringResource(R.string.pairing_split_needed_desc), style = MaterialTheme.typography.bodySmall,
                        color = Ink.secondary)
                }
            }
        }
        // Full screen, Settings would cover the pad, so the button stays quiet until the window is shared.
        Surface(
            onClick = onOpenWirelessDebugging,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = AppShapes.Control,
            color = if (multiWindow) MaterialTheme.colorScheme.onSurface else Color.Transparent,
            contentColor = if (multiWindow) MaterialTheme.colorScheme.surface else Ink.primary,
            border = if (multiWindow) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.action_open_wireless_debugging), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Whether Jiumi shares the screen, in split screen or a small window; follows the user as they switch. */
@Composable
private fun rememberMultiWindowMode(): Boolean {
    val activity = LocalContext.current.componentActivity() ?: return false
    var multiWindow by remember(activity) { mutableStateOf(activity.isInMultiWindowMode) }
    DisposableEffect(activity) {
        val listener = Consumer<MultiWindowModeChangedInfo> { multiWindow = it.isInMultiWindowMode }
        activity.addOnMultiWindowModeChangedListener(listener)
        onDispose { activity.removeOnMultiWindowModeChangedListener(listener) }
    }
    return multiWindow
}

private tailrec fun Context.componentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.componentActivity()
    else -> null
}
