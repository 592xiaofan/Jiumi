// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

/**
 * Folds repeated records into behavior segments: records of the same category from the same caller
 * and with the same outcome, no more than [GAP_MS] apart, become one entry; so do an app's network
 * conversations. The trigger may differ
 * between members (a scan, then reading its results); each member keeps its own for the detail view.
 */
object Segments {
    const val GAP_MS = 5 * 60_000L

    fun merge(items: List<TimelineItem>): List<TimelineItem> =
        items.groupBy(::key).values.flatMap { same ->
            val runs = ArrayList<MutableList<TimelineItem>>()
            var runEnd = Long.MIN_VALUE
            for (item in same.sortedBy { it.t }) {
                val run = runs.lastOrNull()
                val end = end(item)
                if (run != null && item.t - runEnd <= GAP_MS) {
                    run += item
                    runEnd = maxOf(runEnd, end)
                } else {
                    runs += mutableListOf(item)
                    runEnd = end
                }
            }
            runs.map { run -> run.singleOrNull() ?: TimelineItem.Segment(run.sortedByDescending { it.t }) }
        }.sortedByDescending { it.t }

    /** Records with the same key describe the same behavior. */
    internal fun key(item: TimelineItem): String = when (item) {
        is TimelineItem.AccessItem -> with(item.access) { "a|${item.category}|$attr|$denied|$proxied" }
        // Threads of one process share a segment; each member still names its thread.
        is TimelineItem.CallItem -> with(item.call) { "c|${item.category}|$process" }
        // An app's network activity close together is one run, whatever it reached: a list shows a
        // burst of connections as one line, and the run's page lists each destination.
        is TimelineItem.NetItem -> "n"
        is TimelineItem.ProcessItem -> with(item.start) { "p|$reason|$component|$process" }
        // Each thing the user did stands on its own.
        is TimelineItem.ActionItem -> with(item.action) { "u|$pkg|$action|$t" }
        is TimelineItem.Segment -> key(item.head)
    }

    private fun end(item: TimelineItem): Long = when (item) {
        is TimelineItem.AccessItem -> maxOf(item.access.t, item.access.end)
        is TimelineItem.CallItem -> maxOf(item.call.t, item.call.end)
        is TimelineItem.NetItem -> maxOf(item.connection.t, item.connection.end)
        else -> item.t
    }

    /** How many times the behavior happened in a record. */
    fun count(item: TimelineItem): Int = when (item) {
        is TimelineItem.AccessItem -> item.access.count
        is TimelineItem.CallItem -> item.call.count
        is TimelineItem.NetItem -> 1
        is TimelineItem.ProcessItem -> 1
        is TimelineItem.ActionItem -> 1
        is TimelineItem.Segment -> item.count
    }
}
