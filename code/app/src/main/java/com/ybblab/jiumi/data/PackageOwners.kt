// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/** Resolves UID-only records to a package only when that UID has exactly one known package. */
class PackageOwners(
    private val pm: PackageManager,
    private val catalogPackages: (Int) -> List<String>,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class Snapshot(val packages: List<String>, val readAt: Long)
    private val cache = ConcurrentHashMap<Int, Snapshot>()

    fun ownerOf(uid: Int, process: String?): String? {
        val system = uid % PER_USER_RANGE == SYSTEM_UID
        if (process == "system_server" && system) return "android"
        val packages = packagesOf(uid)
        return packages.firstOrNull { process == it || process?.startsWith("$it:") == true }
            ?: packages.singleOrNull()?.takeUnless { system }
    }

    fun packagesOf(uid: Int): List<String> {
        if (uid < 0) return emptyList()
        return (visiblePackages(uid) + catalogPackages(uid)).distinct()
    }

    private fun visiblePackages(uid: Int): List<String> {
        val now = clock()
        cache[uid]?.let { if (now - it.readAt in 0 until CACHE_MS) return it.packages }
        val packages = runCatching { pm.getPackagesForUid(uid) }.getOrNull().orEmpty().toList()
        cache[uid] = Snapshot(packages, now)
        return packages
    }

    private companion object {
        const val SYSTEM_UID = 1000
        const val PER_USER_RANGE = 100_000
        const val CACHE_MS = 60_000L
    }
}
