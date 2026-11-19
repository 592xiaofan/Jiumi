// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

/**
 * What the persistent notification says about an open session: the same figures the Monitor tab
 * shows for it, apps and records. Items raised for attention are left to their own alerts.
 */
data class NoticeSummary(val apps: Int, val records: Int) {
    companion object {
        fun of(report: Report) = NoticeSummary(report.apps.size, report.timeline.size)
    }
}

/**
 * Which apps raised for attention have not been alerted yet in a session. An app is alerted once per
 * item and session; only items whose rule asks for it ([AttentionRules.Item.notify]) alert.
 */
object AttentionAlerts {
    fun key(id: String, app: AppKey): String = "$id|${app.uid}|${app.pkg.orEmpty()}"

    fun keys(attention: List<Attention>): Set<String> = attention.flatMap { a -> a.apps.map { key(a.id, it) } }.toSet()

    /** Per alerting item, its apps not in [seen], in report order; items with none are left out. */
    fun fresh(attention: List<Attention>, notify: Set<String>, seen: Set<String>): List<Attention> =
        attention.filter { it.id in notify }
            .map { a -> Attention(a.id, a.apps.filter { key(a.id, it) !in seen }) }
            .filter { it.apps.isNotEmpty() }
}
