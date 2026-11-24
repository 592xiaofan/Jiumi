// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.ybblab.jiumi.ui.theme.AppShapes
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.ybblab.jiumi.PairingTileService
import com.ybblab.jiumi.R
import com.ybblab.jiumi.ui.icons.JiumiIcons
import com.ybblab.jiumi.ui.theme.Ink

/** The two other ways; one is chosen at a time so its guide is the only one on the page. */
private enum class OtherWay { SPLIT, TILE }

/**
 * Other ways to enter a pairing code, for phones where Jiumi's notification can't be used while
 * the pairing dialog is open. Like choosing how to start in first setup: pick one way above, and
 * only that way's sketch, steps and action follow below.
 */
@Composable
internal fun PairingOptionsScreen(onBack: () -> Unit, onEnterPairingCode: () -> Unit) {
    var way by rememberSaveable { mutableStateOf(OtherWay.SPLIT) }
    SubScreen(stringResource(R.string.pairing_options), onBack, bottomBar = { PrivacyNote() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(stringResource(R.string.pairing_options_intro), style = MaterialTheme.typography.bodySmall,
                color = Ink.secondary, modifier = Modifier.padding(horizontal = 4.dp))
            Column(Modifier.padding(top = 16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WayChoice(
                    title = stringResource(R.string.pairing_method_split),
                    subtitle = stringResource(R.string.pairing_method_split_desc),
                    icon = JiumiIcons.SplitScreen,
                    selected = way == OtherWay.SPLIT
                ) { way = OtherWay.SPLIT }
                WayChoice(
                    title = stringResource(R.string.pairing_method_tile),
                    subtitle = stringResource(R.string.pairing_method_tile_desc),
                    icon = ImageVector.vectorResource(R.drawable.ic_pairing_tile),
                    selected = way == OtherWay.TILE
                ) { way = OtherWay.TILE }
            }
            Text(stringResource(R.string.pairing_way_how), style = MaterialTheme.typography.labelMedium,
                color = Ink.tertiary, modifier = Modifier.padding(start = 4.dp, top = 28.dp, bottom = 8.dp))
            AnimatedContent(way, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "way") { shown ->
                when (shown) {
                    OtherWay.SPLIT -> GuideCard(
                        PairingGuide.SPLIT,
                        listOf(R.string.pairing_split_step1, R.string.pairing_split_step2,
                            R.string.pairing_split_step3, R.string.pairing_split_step4)
                    ) { CardAction(stringResource(R.string.pairing_method_split_action), onClick = onEnterPairingCode) }
                    OtherWay.TILE -> TileGuide()
                }
            }
        }
    }
}

/** A way to choose, drawn like the start methods in first setup: an outline and a dot when chosen. */
@Composable
private fun WayChoice(title: String, subtitle: String, icon: ImageVector, selected: Boolean, onSelect: () -> Unit) {
    val edge by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, label = "wayEdge")
    Surface(
        shape = AppShapes.Card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, edge),
        modifier = Modifier.fillMaxWidth().clip(AppShapes.Card)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(Ink.fill, AppShapes.Control), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(icon, null, tint = Ink.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        SelectionDot()
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Ink.primary)
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Ink.secondary,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun TileGuide() {
    val context = LocalContext.current
    val label = stringResource(R.string.pairing_tile_label)
    var tileAdded by rememberSaveable { mutableStateOf(false) }
    // Before Android 13 an app can't offer its tile; the shade's own editor is the only way.
    var manual by rememberSaveable { mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) }
    GuideCard(
        PairingGuide.TILE,
        listOf(R.string.pairing_tile_step1, R.string.pairing_tile_step2,
            R.string.pairing_tile_step3, R.string.pairing_tile_step4),
        note = if (manual) stringResource(R.string.pairing_tile_manual) else null
    ) {
        when {
            tileAdded -> CardAction(stringResource(R.string.pairing_tile_added), enabled = false) {}
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> CardAction(stringResource(R.string.pairing_tile_add)) {
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

/**
 * The chosen way, read top to bottom: a sketch of it, its steps on the same numbered rail as first
 * setup, and only then its action, since the steps happen outside Jiumi.
 */
@Composable
private fun GuideCard(guide: PairingGuide, steps: List<Int>, note: String? = null, action: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            PairingGuideAnimation(guide)
            Spacer(Modifier.height(16.dp))
            steps.forEachIndexed { index, step ->
                StepLine(index, stringResource(step), last = index == steps.lastIndex)
            }
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = Ink.secondary,
                modifier = Modifier.padding(bottom = 12.dp))
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

/** The action that ends a method's card, across its full width. */
@Composable
private fun CardAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled, shape = AppShapes.Control,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else Ink.fill,
        contentColor = if (enabled) MaterialTheme.colorScheme.surface else Ink.secondary,
        modifier = Modifier.fillMaxWidth().height(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) { Text(text, style = MaterialTheme.typography.titleSmall) }
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
