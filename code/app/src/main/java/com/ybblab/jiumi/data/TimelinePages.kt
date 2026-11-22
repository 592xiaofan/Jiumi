// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class TimelineSource<T>(private val rows: List<T>, private val itemOf: (T) -> TimelineItem) {
    suspend fun select(matches: (T) -> Boolean): TimelineIndex<T> = TimelineIndex.build(rows, itemOf, matches)
}

/** An immutable, newest-first record index. Reading a page only touches that page's rows. */
internal class TimelinePages<T> private constructor(
    private val rows: List<T>, private val positions: IntArray?, private val itemOf: (T) -> TimelineItem,
    private val duplicateActions: Map<Int, Int>
) {
    val size: Int get() = positions?.size ?: rows.size

    fun keyAt(index: Int): String = recordKey(itemOf(rowAt(index))) + "#${duplicateActions[index] ?: 1}"

    private fun rowAt(index: Int) = rows[positions?.get(index) ?: index]

    // Cache by page across days so opening more days never keeps all visited pages resident.
    private val cache = LinkedHashMap<Int, List<T>>(MAX_CACHED_PAGES, 0.75f, true)

    @Synchronized
    fun at(index: Int): T {
        val start = index / PAGE_SIZE * PAGE_SIZE
        val page = cache[start] ?: (start until minOf(start + PAGE_SIZE, size)).map(::rowAt).also {
            if (cache.size == MAX_CACHED_PAGES) cache.remove(cache.keys.first())
            cache[start] = it
        }
        return page[index - start]
    }

    companion object {
        internal const val PAGE_SIZE = 150
        private const val MAX_CACHED_PAGES = 6

        fun <T> from(rows: List<T>, positions: IntArray? = null, itemOf: (T) -> TimelineItem): TimelinePages<T> {
            val duplicates = HashMap<String, Int>()
            val repeated = HashMap<Int, Int>()
            for (index in 0 until (positions?.size ?: rows.size)) {
                val item = itemOf(rows[positions?.get(index) ?: index])
                // Raw access, call, process and network records have unique storage keys.
                if (item !is TimelineItem.ActionItem) continue
                val occurrence = duplicates.merge(recordKey(item), 1, Int::plus)!!
                if (occurrence > 1) repeated[index] = occurrence
            }
            return TimelinePages(rows, positions, itemOf, repeated)
        }
    }
}

/** A folded day can be displayed without requesting any of its record pages. */
internal data class TimelineDay(
    val time: Long, val start: Int, val count: Int, val actions: List<Pair<TimelineItem, Int>>, val destinations: Int
)

internal class TimelineIndex<T>(val days: List<TimelineDay>, val records: TimelinePages<T>) {
    companion object {
        suspend fun <T> build(
            rows: List<T>, itemOf: (T) -> TimelineItem, matches: (T) -> Boolean, zone: ZoneId = ZoneId.systemDefault()
        ): TimelineIndex<T> {
            val context = currentCoroutineContext()
            val selected = IntArray(rows.size)
            var size = 0
            val days = ArrayList<TimelineDay>()
            var dayStart = Long.MAX_VALUE
            var start = 0
            val addresses = HashSet<String>()
            val actions = LinkedHashMap<Pair<String?, Set<List<String>>>, Pair<TimelineItem, Int>>()
            fun finishDay() {
                if (size == start) return
                days += TimelineDay(dayStart, start, size - start, actions.values.toList(), addresses.size)
                actions.clear()
                addresses.clear()
                start = size
            }
            for ((position, row) in rows.withIndex()) {
                if (position % TimelinePages.PAGE_SIZE == 0) context.ensureActive()
                if (!matches(row)) continue
                val item = itemOf(row)
                if (item.t < dayStart) {
                    finishDay()
                    dayStart = Instant.ofEpochMilli(item.t).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                }
                selected[size++] = position
                val head = (item as? TimelineItem.Segment)?.head ?: item
                when (head) {
                    is TimelineItem.NetItem -> addresses.addAll((item as? TimelineItem.Segment)?.destinations ?: listOf(head.label))
                    else -> {
                        val category = when (head) {
                            is TimelineItem.AccessItem -> head.category
                            is TimelineItem.CallItem -> head.category
                            else -> null
                        }
                        val samples = (item as? TimelineItem.Segment)?.actionSamples ?: listOf(item)
                        val key = category to samples.mapTo(HashSet(), TimelineItem::actionKey)
                        val prior = actions[key]
                        actions[key] = (prior?.first ?: item) to ((prior?.second ?: 0) + Segments.count(item))
                    }
                }
            }
            finishDay()
            val positions = if (size == rows.size) null else selected.copyOf(size)
            return TimelineIndex(days, TimelinePages.from(rows, positions, itemOf))
        }
    }
}

private fun recordKey(item: TimelineItem): String = when (item) {
    is TimelineItem.AccessItem -> with(item.access) { key("access", t, uid, pkg, op, attr, source) }
    is TimelineItem.CallItem -> with(item.call) { key("call", t, uid, process, thread, iface, method) }
    is TimelineItem.ProcessItem -> with(item.start) { key("process", t, pid) }
    is TimelineItem.ActionItem -> with(item.action) { key("action", t, uid, pkg, action) }
    is TimelineItem.NetItem -> with(item.connection) { key("net", t, uid, proto, addr, port) }
    // The same anchor works after a single record grows into a segment.
    is TimelineItem.Segment -> recordKey(item.members.last())
}

private fun key(vararg fields: Any?): String = fields.joinToString("") {
    val value = it?.toString()
    if (value == null) "-1:" else "${value.length}:$value"
}
