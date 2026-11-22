// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

/** Occurrences per time bucket starting at [start], each [stepMs] long (minutes, an hour or a local day). */
class Buckets(val start: Long, val stepMs: Long, val total: IntArray) {
    val size: Int get() = total.size
}

/**
 * One kind of data an app reached over a window, as its row tells it: how often, when last, how
 * often while not in use, and how often refused.
 */
data class BehaviorSummary(
    val category: String,
    val count: Int,
    val unattended: Int,
    val denied: Int,
    val last: Long
)

object Behavior {
    private class Record(val t: Long, val end: Long, val count: Int, val category: String,
                         val unattended: Boolean, val denied: Boolean)

    /**
     * One summary per category in [items], most worrying first: unattended and
     * refused reads before plain counts. [routine] callers have no meaningful app state, so none of
     * their reads count as unattended.
     */
    fun summaries(items: List<TimelineItem>, routine: Boolean = false): List<BehaviorSummary> =
        records(items, routine).groupBy { it.category }.map { (category, list) ->
            BehaviorSummary(
                category = category,
                count = list.sumOf { it.count },
                unattended = list.filter { it.unattended }.sumOf { it.count },
                denied = list.filter { it.denied }.sumOf { it.count },
                last = list.maxOf { maxOf(it.t, it.end) }
            )
        }.sortedWith(compareByDescending<BehaviorSummary> { it.unattended + it.denied }.thenByDescending { it.count })

    /** Every behavior in [items] over [from, to], per bucket. */
    fun chart(items: List<TimelineItem>, from: Long, to: Long, offsetMs: (Long) -> Long = ::localOffset): Buckets =
        buckets(records(items, false), from, to, offsetMs)

    private fun flat(items: List<TimelineItem>) = items.flatMap { (it as? TimelineItem.Segment)?.members ?: listOf(it) }

    private fun records(items: List<TimelineItem>, routine: Boolean): List<Record> = flat(items).mapNotNull { item ->
        when (item) {
            is TimelineItem.AccessItem -> with(item.access) {
                // A refused attempt gave the app nothing, wherever it was.
                Record(t, end, count, item.category, !routine && !denied && unattended(state, screen), denied)
            }
            is TimelineItem.CallItem -> with(item.call) {
                Record(t, end, count, item.category, !routine && unattended(state, screen), false)
            }
            else -> null
        }
    }

    fun unattended(state: String?, screen: Boolean?): Boolean = ReportBuilder.isUnattended(state, screen)

    /**
     * Local days for a window longer than a day. Up to a day, the finest of [STEPS] that keeps it within
     * [MAX_SHORT] buckets, so a short session is told in minutes and a long one in hours; a window
     * starting at local midnight shows all 24 hours, the ones still to come empty, as a clock face would.
     */
    fun buckets(from: Long, to: Long, offsetMs: (Long) -> Long = ::localOffset): Buckets {
        val offset = offsetMs(from)
        val wholeDay = to - from <= DAY && Math.floorMod(from + offset, DAY) == 0L
        val step = when {
            to - from > DAY -> DAY
            wholeDay -> HOUR
            else -> STEPS.firstOrNull { step -> count(from, to, step, offset) <= MAX_SHORT } ?: HOUR
        }
        val bars = if (wholeDay) 24 else count(from, to, step, offset).coerceIn(1, MAX_BARS)
        return Buckets(Math.floorDiv(from + offset, step) * step - offset, step, IntArray(bars))
    }

    private fun count(from: Long, to: Long, step: Long, offset: Long): Int {
        val start = Math.floorDiv(from + offset, step) * step - offset
        return ((to - start + step - 1) / step).toInt()
    }

    private fun buckets(records: List<Record>, from: Long, to: Long, offsetMs: (Long) -> Long): Buckets {
        val b = buckets(from, to, offsetMs)
        records.forEach { r ->
            val i = index(b, r.t, offsetMs)
            if (i !in 0 until b.size) return@forEach
            b.total[i] += r.count
        }
        return b
    }

    /** The bucket [t] falls in. Days are counted on the local calendar, so a daylight-saving change does not shift them. */
    fun index(b: Buckets, t: Long, offsetMs: (Long) -> Long = ::localOffset): Int =
        if (b.stepMs < DAY) Math.floorDiv(t - b.start, b.stepMs).toInt()
        else (Math.floorDiv(t + offsetMs(t), DAY) - Math.floorDiv(b.start + offsetMs(b.start), DAY)).toInt()

    /** Where bucket [i] begins: local midnight for days, so a daylight-saving day is 23 or 25 hours. */
    fun startOf(b: Buckets, i: Int, offsetMs: (Long) -> Long = ::localOffset): Long {
        if (b.stepMs < DAY) return b.start + i * b.stepMs
        val guess = b.start + i * DAY
        return Math.floorDiv(guess + offsetMs(guess) + HOUR * 12, DAY) * DAY - offsetMs(guess)
    }

    fun localOffset(t: Long): Long = java.util.TimeZone.getDefault().getOffset(t).toLong()

    /** A month of days at most; longer windows are not offered. */
    const val MAX_BARS = 31
    /** Most buckets a window of under a day is cut into. */
    const val MAX_SHORT = 24
    private val STEPS = listOf(60_000L, 5 * 60_000L, 15 * 60_000L, 30 * 60_000L, 60 * 60_000L, 2 * 60 * 60_000L)
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR
}
