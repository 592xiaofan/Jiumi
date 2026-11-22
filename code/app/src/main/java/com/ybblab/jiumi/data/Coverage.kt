// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

import com.ybblab.jiumi.probe.ProbeProtocol

/**
 * How much of a monitoring session the monitor actually covered. The monitor notes "start" when it
 * launches, "alive" every few minutes and "stop" when it is stopped; two consecutive notes no more
 * than [MAX_GAP_MS] apart mean it ran in between. A longer silence, or a fresh start within the
 * session, is an interruption (a reboot, Wireless debugging turned off, a crash).
 */
object Coverage {
    /** Heartbeats come every five minutes; one and a half periods of silence is a gap. */
    const val MAX_GAP_MS = 7 * 60_000L + 30_000L

    /** [spans]: the stretches the monitor ran, in order; [coveredMs] is their total. */
    data class Result(
        val coveredMs: Long, val interruptions: Int, val lostEvents: Long,
        val lastSeen: Long?, val partialTrace: Boolean = false, val spans: List<LongRange> = emptyList()
    )

    /** [events] may begin up to [MAX_GAP_MS] before [start]. */
    fun compute(start: Long, end: Long?, events: List<JiumiStore.MonitorEvent>, now: Long): Result {
        val until = end ?: now
        var covered = 0L
        var interruptions = 0
        var lost = 0L
        var last: Long? = null
        var stopped = false
        var partialTrace = false
        val spans = ArrayList<LongRange>()
        fun run(from: Long, to: Long) {
            covered += to - from
            val joined = spans.lastOrNull()
            if (joined != null && joined.last == from) spans[spans.lastIndex] = joined.first..to else spans += from..to
        }
        for (e in events.sortedBy { it.t }) {
            if (e.t > until) continue
            // The monitor usually starts just before the session opens: it was running at the start.
            if (e.t < start) {
                if (start - e.t <= MAX_GAP_MS && e.state != "lost") { last = start; stopped = e.state == "stop" }
                continue
            }
            if (e.state == "lost") { lost += e.reason?.toLongOrNull() ?: 0L; continue }
            val previous = last
            when {
                previous == null -> if (e.t - start <= MAX_GAP_MS) run(start, e.t)
                e.state == "start" -> interruptions++
                stopped -> interruptions++
                e.t - previous <= MAX_GAP_MS -> run(previous, e.t)
                else -> interruptions++
            }
            last = e.t
            stopped = e.state == "stop"
            if (stopped && e.reason == ProbeProtocol.STOP_PARTIAL_TRACE) partialTrace = true
        }
        // Still running: the time since the last note counts while it is recent.
        val tail = last
        if (end == null && tail != null && !stopped && now - tail <= MAX_GAP_MS) run(tail, now)
        return Result(covered, interruptions, lost, last, partialTrace, spans)
    }
}
