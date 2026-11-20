// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ybblab.jiumi.MonitorController
import com.ybblab.jiumi.R
import com.ybblab.jiumi.data.AppKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MonitoringExclusionsScreen(controller: MonitorController, labels: AppLabels, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf<String?>(null) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val policy by controller.monitoringExclusions.collectAsStateWithLifecycle()
    val apps by produceState<List<Pair<AppKey, String>>?>(null, controller, labels) {
        value = withContext(Dispatchers.IO) {
            controller.appsForMonitoring().map { it to labels.label(it) }.sortedWith(
                compareBy(java.text.Collator.getInstance()) { it.second }
            )
        }
    }
    val rows = apps?.filter { (key, label) ->
        label.contains(query.trim(), ignoreCase = true) || key.pkg.orEmpty().contains(query.trim(), ignoreCase = true)
    }
    SubScreen(stringResource(R.string.monitoring_exclusions), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                SearchField(query) { query = it }
                GroupFooter(stringResource(R.string.monitoring_exclusions_description))
                if (saveFailed) GroupFooter(stringResource(R.string.monitoring_exclusions_save_error), warning = true)
            }
            when {
                rows == null -> item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                rows.isEmpty() -> item { GroupFooter(stringResource(R.string.apps_empty_search)) }
                else -> items(rows, key = { requireNotNull(it.first.pkg) }) { (key, label) ->
                    val selected = key.pkg in policy.active
                    Card {
                        ListRow(label, subtitle = key.pkg, leading = { AppIcon(labels, key.pkg, 32) }) {
                            JiumiSwitch(selected, enabled = saving == null) { excluded ->
                                saving = key.pkg
                                saveFailed = false
                                scope.launch {
                                    try {
                                        saveFailed = !controller.setMonitoringExcluded(key, excluded)
                                    } finally {
                                        saving = null
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
