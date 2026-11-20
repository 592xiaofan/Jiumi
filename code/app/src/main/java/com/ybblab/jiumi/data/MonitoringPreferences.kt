// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

import android.content.Context
import com.ybblab.jiumi.probe.MonitoringExclusions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class MonitoringPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("jiumi_settings", Context.MODE_PRIVATE)
    private val _exclusions = MutableStateFlow(MonitoringExclusions.parse(prefs.getString(KEY, null)?.let(::JSONArray)))
    val exclusions = _exclusions.asStateFlow()

    /** Called under the controller's sync lock. Publish only rules that survive a restart. */
    fun save(next: MonitoringExclusions): Boolean {
        if (next === _exclusions.value) return true
        if (!prefs.edit().putString(KEY, next.toJson().toString()).commit()) return false
        _exclusions.value = next
        return true
    }

    private companion object {
        const val KEY = "monitoring_exclusions"
    }
}
