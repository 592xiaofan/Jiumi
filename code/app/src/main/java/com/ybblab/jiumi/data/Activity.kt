// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

/** What one slot raised for attention: records per attention item, and the apps behind them. */
data class Hits(val byItem: Map<String, Int>, val apps: Set<AppKey>) {
    val total: Int get() = byItem.values.sum()
}

/**
 * A window told slot by slot: the reads and calls of personal data in each ([buckets]), whether the
 * monitor watched any of it ([covered]), and what each slot raised for attention ([hits]). A slot the
 * monitor did not watch and that holds no record is a gap, not a quiet stretch; slots after [until]
 * are still to come.
 */
class Activity(val buckets: Buckets, val covered: BooleanArray, val hits: List<Hits>, val until: Long,
               private val offsetMs: (Long) -> Long = Behavior::localOffset,
               /** Of each slot's records, those made while the app was not in front of the user. */
               val background: IntArray = IntArray(buckets.size)) {
    val size: Int get() = buckets.size

    /** Records per slot; null for a gap or a slot still to come. */
    val values: List<Int?> = (0 until buckets.size).map { i ->
        val count = buckets.total[i]
        when {
            startOf(i) > until -> null
            count == 0 && !covered[i] -> null
            else -> count
        }
    }

    fun startOf(i: Int): Long = Behavior.startOf(buckets, i, offsetMs)

    /** Every record over the window, gaps aside. */
    val total: Int get() = values.sumOf { it ?: 0 }

    /** Records made in the background over the window, gaps aside. */
    val backgroundTotal: Int get() = values.indices.sumOf { if (values[it] == null) 0 else background[it] }

    /** Whether slot [i] holds no record and was not watched, as opposed to being still to come. */
    fun unrecorded(i: Int): Boolean = values[i] == null && startOf(i) <= until

    companion object {
        /**
         * [entries]: the window's records, every app's or one app's; [attention] and [apps] tell which
         * apps were raised for what, so a slot only counts what made an app's item. [spans] are the
         * stretches the monitor ran. Attention by background reads and by uploads is placed at each
         * record; frequent reads at the start of the busiest 60 minutes that reached the threshold. The
         * system's long-term counts ([system]) are placed in the slot that holds the middle of theirs, when
         * theirs is no longer than ours; and
         * where they count more than the records do, they stand for the slot: the two overlap, so they are
         * never added up. [category], when given, keeps only that kind of data.
         */
        fun of(
            entries: List<LogEntry>, attention: List<Attention>, apps: List<AppSummary>,
            from: Long, to: Long, spans: List<LongRange>, system: List<SystemSlot> = emptyList(),
            category: String? = null, routineKinds: Set<String> = emptySet(),
            offsetMs: (Long) -> Long = Behavior::localOffset
        ): Activity {
            // Routine system callers have no app state to speak of: their records never count as background.
            val routine = apps.filter { it.kind.id in routineKinds }.map { it.key }.toSet()
            fun keep(item: TimelineItem) = category == null || when (item) {
                is TimelineItem.AccessItem -> item.category == category
                is TimelineItem.CallItem -> item.category == category
                else -> false
            }
            val flat = entries.flatMap { e ->
                ((e.item as? TimelineItem.Segment)?.members ?: listOf(e.item)).filter(::keep).map { LogEntry(e.key, it) }
            }
            val buckets = Behavior.chart(flat.map { it.item }, from, to, offsetMs = offsetMs)
            val covered = BooleanArray(buckets.size)
            spans.forEach { span ->
                val a = maxOf(span.first, from)
                val b = minOf(span.last, to)
                if (a >= b) return@forEach
                val first = Behavior.index(buckets, a, offsetMs).coerceAtLeast(0)
                val last = Behavior.index(buckets, b - 1, offsetMs).coerceAtMost(buckets.size - 1)
                for (i in first..last) covered[i] = true
            }

            val raised = attention.associate { it.id to it.apps.toSet() }
            val counts = Array(buckets.size) { HashMap<String, Int>() }
            val background = IntArray(buckets.size)
            fun behind(key: AppKey, t: Long, n: Int) {
                if (key in routine) return
                val i = Behavior.index(buckets, t, offsetMs)
                if (i in 0 until buckets.size) background[i] += n
            }
            val who = Array(buckets.size) { HashSet<AppKey>() }
            fun add(item: String, key: AppKey, t: Long, n: Int) {
                if (key !in raised[item].orEmpty() || n <= 0) return
                val i = Behavior.index(buckets, t, offsetMs)
                if (i !in 0 until buckets.size) return
                counts[i].merge(item, n, Int::plus)
                who[i] += key
            }
            flat.forEach { entry ->
                entry.item.let { item ->
                    when (item) {
                        is TimelineItem.AccessItem -> with(item.access) {
                            if (!denied && ReportBuilder.isUnattended(state, screen)) {
                                add(AttentionRules.BACKGROUND, entry.key, t, count)
                                behind(entry.key, t, count)
                            }
                        }
                        is TimelineItem.CallItem -> with(item.call) {
                            if (ReportBuilder.isUnattended(state, screen)) {
                                add(AttentionRules.BACKGROUND, entry.key, t, count)
                                behind(entry.key, t, count)
                            }
                        }
                        is TimelineItem.NetItem -> if (item.sentInBackground) add(AttentionRules.SENT_IN_BACKGROUND, entry.key, item.t, 1)
                        else -> Unit
                    }
                }
            }
            val keys = entries.map { it.key }.toSet()
            apps.filter { it.key in keys }.forEach { app ->
                app.frequent?.takeIf { category == null || it.category == category }?.let { add(AttentionRules.FREQUENT, app.key, it.from, it.count) }
                app.frequentDenied?.takeIf { category == null || it.category == category }
                    ?.let { add(AttentionRules.FREQUENT_DENIED, app.key, it.from, it.count) }
            }
            // The system's slots, each within one of ours.
            val systemCount = LongArray(buckets.size)
            val systemBackground = LongArray(buckets.size)
            val systemApps = Array(buckets.size) { HashSet<AppKey>() }
            val systemBehind = LongArray(buckets.size)
            val raisedBackground = raised[AttentionRules.BACKGROUND].orEmpty()
            system.filter { category == null || it.category == category }.forEach { s ->
                // The system cuts its own slots (a quarter hour lately, about a day or longer before),
                // not on our boundaries: one no longer than ours, give or take a tenth, goes where its
                // middle falls; a longer one would have to be shared out by guess, so it is left out.
                if (s.to - s.from > buckets.stepMs + buckets.stepMs / 10) return@forEach
                val i = Behavior.index(buckets, (s.from + s.to) / 2, offsetMs)
                if (i !in 0 until buckets.size) return@forEach
                systemCount[i] += s.count
                if (s.key !in routine) systemBehind[i] += s.background
                if (s.key in raisedBackground && s.background > 0) { systemBackground[i] += s.background; systemApps[i] += s.key }
            }
            for (i in 0 until buckets.size) {
                // Where the system's count stands for the slot, so does its background part.
                if (systemCount[i] > buckets.total[i]) {
                    buckets.total[i] = systemCount[i].coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    background[i] = systemBehind[i].coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
                if (systemBackground[i] > (counts[i][AttentionRules.BACKGROUND] ?: 0)) {
                    counts[i][AttentionRules.BACKGROUND] = systemBackground[i].coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    who[i] += systemApps[i]
                }
            }
            val hits = (0 until buckets.size).map { Hits(counts[it], who[it]) }
            return Activity(buckets, covered, hits, to, offsetMs, background)
        }
    }
}
