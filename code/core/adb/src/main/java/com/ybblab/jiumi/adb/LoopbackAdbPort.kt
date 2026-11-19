// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.adb

internal object LoopbackAdbPort {
    private val ipv4 = Regex("""tcp:127\.0\.0\.1:(\d+)""")

    /** Trust an explicitly configured loopback socket only when it is actually listening there. */
    fun verified(config: String, sockets: String): Int? {
        val port = config.trim().split(',').mapNotNull { ipv4.matchEntire(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
            .distinct().singleOrNull()?.takeIf { it in 1..65535 } ?: return null
        val endpoints = sockets.lineSequence().map { it.trim().split(Regex("\\s+")) }
            .filter { it.firstOrNull() == "LISTEN" }
            .mapNotNull { it.getOrNull(3) }
            .filter { it.endsWith(":$port") }
            .toList()
        val local = "127.0.0.1:$port"
        return port.takeIf { local in endpoints && endpoints.all { it == local || it == "[::1]:$port" } }
    }
}
