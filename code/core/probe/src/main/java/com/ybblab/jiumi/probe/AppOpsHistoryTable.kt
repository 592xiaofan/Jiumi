// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/** The table printed by the SQLite AppOps history service. */
internal object AppOpsHistoryTable {
    const val DISCRETE = "------------Discrete App Ops-------------"
    const val AGGREGATED = "------------Aggregated App Ops-------------"
    private const val HEADER = "UID|PACKAGE_NAME|DEVICE_ID|OP_NAME|ATTRIBUTION_TAG|ACCESS_TIME|ACCESS_COUNTS" +
        "|REJECT_COUNTS|DURATION|UID_STATE|OP_FLAGS|ATTRIBUTION_CHAIN_ID|ATTRIBUTION_FLAGS"
    private val longInterval = Regex("LONG_INTERVAL\\(Aggregation Bucket\\)=(\\d+) Minutes?")
    private val opFlags = setOf("s", "tp", "up", "tpd", "upd")
    private val states = setOf("pers", "top", "fgsvc", "fg", "bg", "cch", "gone")

    data class Row(
        val uid: Int, val pkg: String, val op: String, val attr: String?, val time: Long,
        val accesses: Int, val rejects: Int, val duration: Long, val state: String, val via: String
    )

    fun contains(dump: String, section: String): Boolean = dump.lineSequence().any { it.trim() == section }

    fun interval(dump: String): Long {
        val minutes = longInterval.find(dump)?.groupValues?.get(1)?.toIntOrNull()
        check(minutes != null && minutes > 0) { "Missing AppOps aggregation interval" }
        return minutes * 60_000L
    }

    fun read(dump: String, section: String): Sequence<Row> {
        val table = dump.lineSequence().map(String::trim).dropWhile { it != section }
            .drop(1).dropWhile(String::isEmpty)
        check(table.firstOrNull() == HEADER) { "Missing AppOps history table header" }
        return sequence {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).apply { isLenient = false }
            for (line in table.drop(1).takeWhile(String::isNotEmpty)) {
                val fields = line.split('|')
                if (fields.size < 13) continue
                // flagsToString can also use pipes between multiple flags.
                val flags = fields.subList(10, fields.size - 2)
                if (flags.any { it !in opFlags }) continue
                val uid = fields[0].toIntOrNull()?.takeIf { it >= 0 } ?: continue
                val pkg = fields[1].takeIf { it.isNotBlank() && it != "null" } ?: continue
                val op = DumpText.operation(fields[3]) ?: continue
                val stamp = DumpText.normalize(fields[5])
                val position = ParsePosition(0)
                val time = format.parse(stamp, position)?.time?.takeIf { position.index == stamp.length }
                    ?: continue
                val accesses = fields[6].toIntOrNull()?.takeIf { it >= 0 } ?: continue
                val rejects = fields[7].toIntOrNull()?.takeIf { it >= 0 } ?: continue
                val duration = fields[8].toLongOrNull()?.coerceAtLeast(0L) ?: continue
                val state = DumpText.state(fields[9]).takeIf { it in states } ?: continue
                val via = flags.firstOrNull { it.endsWith('d') } ?: flags.first()
                yield(Row(uid, pkg, op, fields[4].takeUnless { it == "null" }, time, accesses, rejects, duration, state, via))
            }
        }
    }
}
