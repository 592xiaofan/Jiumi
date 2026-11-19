// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

import java.net.InetAddress

/** Matches network destinations to prior DNS observations made by the same UID. */
class DomainNames(lookups: List<JiumiStore.Lookup>) {
    private data class Key(val uid: Int, val address: String)

    private val byAddress = buildMap<Key, MutableList<JiumiStore.Lookup>> {
        for (lookup in lookups.filter { it.uid >= 0 && it.rcode == 0 }.sortedBy { it.end }) {
            for (answer in lookup.answers) canonical(answer)?.let { address ->
                getOrPut(Key(lookup.uid, address)) { ArrayList() } += lookup
            }
        }
    }

    fun domainOf(uid: Int, addr: String, connectedAt: Long): String? {
        if (uid < 0 || byAddress.isEmpty()) return null
        val address = canonical(addr) ?: return null
        val matches = byAddress[Key(uid, address)] ?: return null
        var low = 0
        var high = matches.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (matches[middle].end <= connectedAt) low = middle + 1 else high = middle
        }
        val latest = matches.getOrNull(low - 1) ?: return null
        if (connectedAt - latest.end > LOOKBACK_MS) return null
        // Several answers at the same observed time give no way to decide between their names.
        val tied = matches.subList(0, low - 1).asReversed().takeWhile { it.end == latest.end }
        return latest.domain.takeIf { tied.all { it.domain == latest.domain } }
    }

    companion object {
        /**
         * One spelling per address, so the probe's compressed IPv6 and Java's full form compare
         * equal. Only literal addresses are parsed; nothing is looked up.
         */
        fun canonical(addr: String): String? {
            val literal = if (':' in addr) addr.all { it in "0123456789abcdefABCDEF:." }
                else addr.count { it == '.' } == 3 && addr.split('.').all { part ->
                    part.isNotEmpty() && part.all { it in '0'..'9' } && part.toIntOrNull() in 0..255
                }
            if (!literal) return null
            return runCatching { InetAddress.getByName(addr).hostAddress }.getOrNull()
        }

        /** Lookups this far before a window still name addresses inside it: answers are cached a while. */
        const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
    }
}

/** A domain an app looked up over a window: how often, when last, and whether any lookup found it. */
data class DomainSummary(val domain: String, val lookups: Int, val last: Long, val found: Boolean)
