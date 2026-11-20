// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.MonitorController.AppState
import com.ybblab.jiumi.MonitorController.ListedApp
import com.ybblab.jiumi.R
import com.ybblab.jiumi.data.AppKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MonitoringExclusionsScreen(controller: MonitorController, labels: AppLabels, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val policy by controller.monitoringExclusions.collectAsStateWithLifecycle()
    val state by controller.state.collectAsStateWithLifecycle()
    val apps by produceState<List<Pair<ListedApp, String>>?>(null, state.running, labels) {
        suspend fun named(apps: List<ListedApp>) = withContext(Dispatchers.IO) {
            apps.map { it to labels.label(AppKey(it.uid, it.pkg)) }
        }
        value = named(controller.appsForMonitoring())
        if (controller.shellListStale()) value = named(controller.appsForMonitoring(fresh = true))
    }
    val search = query.trim()
    val rows = remember(apps, policy.active, search) {
        apps?.let { installed ->
            val installedPackages = installed.mapTo(HashSet()) { it.first.pkg }
            val removed = policy.active.values.filter { it.pkg !in installedPackages }.map {
                ListedApp(it.pkg, it.uid, AppState.HISTORICAL) to it.pkg
            }
            (installed + removed)
                .filter { (app, label) -> label.contains(search, ignoreCase = true) || app.pkg.contains(search, ignoreCase = true) }
                .sortedBy { it.second.lowercase() }
        }
    }
    val (excluded, monitored) = remember(rows, policy.active) { rows.orEmpty().partition { it.first.pkg in policy.active } }
    val groups = remember(monitored) { monitored.groupBy { AppGroup.of(it.first) } }
    val change: (ListedApp, Boolean) -> Unit = { app, excludedNow ->
        saving = true
        saveFailed = false
        scope.launch {
            try {
                saveFailed = !controller.setMonitoringExcluded(AppKey(app.uid, app.pkg), excludedNow)
            } finally {
                saving = false
            }
        }
    }
    SubScreen(stringResource(R.string.monitoring_exclusions), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item(key = "search") {
                SearchField(query) { query = it }
                if (saveFailed) GroupFooter(stringResource(R.string.monitoring_exclusions_save_error), warning = true)
                Spacer(Modifier.height(12.dp))
            }
            when {
                rows == null -> item {
                    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                rows.isEmpty() -> item { EmptyCard(stringResource(R.string.apps_empty_search)) }
                else -> {
                    if (excluded.isNotEmpty()) {
                        monitoringGroup(R.string.monitoring_exclusions_selected, excluded, controller, labels, true, !saving, change)
                    }
                    for (group in AppGroup.inventoryGroups) {
                        val list = groups[group] ?: continue
                        monitoringGroup(group.label, list, controller, labels, false, !saving, change)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.monitoringGroup(
    title: Int,
    apps: List<Pair<ListedApp, String>>,
    controller: MonitorController,
    labels: AppLabels,
    selected: Boolean,
    enabled: Boolean,
    onChange: (ListedApp, Boolean) -> Unit
) {
    item(key = "title-$title") {
        GroupTitle(stringResource(title) + " · " + formatCount(apps.size.toLong()))
    }
    apps.forEachIndexed { index, (app, name) ->
        item(key = app.pkg) {
            AppListRow(controller, labels, app, name, first = index == 0, last = index == apps.lastIndex) {
                JiumiSwitch(selected, enabled = enabled) { onChange(app, it) }
            }
        }
    }
    item(key = "gap-$title") { Spacer(Modifier.height(16.dp)) }
}
