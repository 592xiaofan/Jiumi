// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

import org.json.JSONArray
import org.json.JSONObject

/** A snapshot of the user's exclusions, including closed intervals for system history imports. */
class MonitoringExclusions(val rules: List<Rule> = emptyList()) {
    data class Rule(val pkg: String, val uid: Int, val from: Long, val until: Long? = null) {
        fun matches(uid: Int, pkg: String?): Boolean = if (pkg == null) uid < 0 || this.uid == uid
            else uid >= 0 && this.uid / PER_USER_RANGE == uid / PER_USER_RANGE && this.pkg == pkg
    }

    val active = rules.filter { it.until == null }.associateBy { it.pkg }
    private val byUid = rules.groupBy { it.uid }
    private val byPackage = rules.groupBy { it.pkg }

    private fun matching(uid: Int, pkg: String?): List<Rule> = when {
        uid < 0 && pkg == null -> rules
        pkg == null -> byUid[uid].orEmpty()
        else -> byPackage[pkg].orEmpty()
    }

    fun hides(uid: Int, pkg: String? = null): Boolean =
        matching(uid, pkg).any { it.until == null && it.matches(uid, pkg) }

    /** Record timestamps are inclusive. The exclusion ends at the first millisecond we may record again. */
    fun blocks(uid: Int, pkg: String?, from: Long, to: Long = from): Boolean = matching(uid, pkg).any {
        it.matches(uid, pkg) && it.from <= maxOf(from, to) && (it.until == null || it.until > maxOf(from, it.from))
    }

    fun resumedAt(uid: Int, pkg: String?, t: Long): Long = matching(uid, pkg).asSequence()
        .filter { it.matches(uid, pkg) }.mapNotNull { it.until }.filter { it <= t }.maxOrNull() ?: 0

    fun hidesProcess(uid: Int, process: String?): Boolean =
        hides(uid) || process?.let { hides(uid, it.substringBefore(':')) } == true

    internal fun blocksProcess(uid: Int, process: String?, t: Long, end: Long = t): Boolean =
        blocks(uid, null, t, end) || process?.let { blocks(uid, it.substringBefore(':'), t, end) } == true

    fun blocksEvent(event: JSONObject): Boolean {
        if (!event.has("uid") || rules.isEmpty()) return false
        val uid = event.getInt("uid")
        val from = event.getLong("t")
        val end = event.optLong("end", from)
        val process = event.optString("process", "").ifEmpty { null }
        return when (event.optString("kind")) {
            "proc_start" -> hostingPackage(event.optString("component"))?.let { blocks(uid, it, from, end) }
                ?: blocksProcess(uid, process, from, end)
            "call" -> blocksProcess(uid, process, from, end)
            else -> blocks(uid, event.optString("pkg", "").ifEmpty { null }, from, end)
        }
    }

    fun set(pkg: String, uid: Int, excluded: Boolean, now: Long): MonitoringExclusions {
        val previous = active[pkg]
        if ((!excluded && previous == null) || (excluded && previous?.uid == uid)) return this
        val updated = rules.map { if (it === previous) it.copy(until = now) else it }
        return MonitoringExclusions(if (excluded) updated + Rule(pkg, uid, previous?.from ?: now) else updated)
    }

    fun toJson() = JSONArray().apply {
        for (r in rules) put(JSONObject().put("pkg", r.pkg).put("uid", r.uid).put("from", r.from).put("until", r.until))
    }

    companion object {
        private const val PER_USER_RANGE = 100_000
        private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*")

        fun hostingPackage(component: String): String? = component.substringBefore('/', missingDelimiterValue = "")
            .trimStart('{').takeIf(PACKAGE_NAME::matches)

        fun parse(array: JSONArray?) = MonitoringExclusions(List(array?.length() ?: 0) { i ->
            val row = checkNotNull(array).getJSONObject(i)
            Rule(row.getString("pkg"), row.getInt("uid"), row.getLong("from"),
                if (row.isNull("until")) null else row.getLong("until"))
        })
    }
}
