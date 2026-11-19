// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

import org.json.JSONArray
import org.json.JSONObject

internal object PackageInventory {
    private val name = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)*""")
    private val moduleLine = Regex("""package:(.+)=([^\s]+)(?:\s+versionCode:(\d+))?""")
    private const val PER_USER_RANGE = 100_000
    private const val FACTORY_WINDOW_MS = 5 * 60_000L

    fun acceptsName(pkg: String) = name.matches(pkg)

    fun packages(
        user: Int,
        dump: PackageDump?,
        all: Map<String, Int>,
        installed: Map<String, Int>?,
        system: Map<String, Int>?,
        installers: Map<String, String>,
        launcher: Set<String>?
    ): JSONArray {
        val firstBoot = dump?.firstInstall?.get("android")
        val listed = all.keys + installed.orEmpty().keys + system.orEmpty().keys
        val names = listed + dump?.userStates.orEmpty().keys
        val json = JSONArray()
        for (pkg in names) {
            if (!acceptsName(pkg)) continue
            val entry = dump?.packages?.get(pkg)
            val state = dump?.userStates?.get(pkg)
            val present = state?.installed ?: installed?.containsKey(pkg) ?: continue
            val preinstalled = entry?.system ?: system?.containsKey(pkg)
            // Uninstalled entries for another user alone do not establish a package for this user.
            if (!present && pkg !in listed && preinstalled != true) continue
            val uid = listOf(all[pkg], installed?.get(pkg), system?.get(pkg)).firstOrNull { it != null && it >= 0 }
                ?: entry?.appId?.let { user * PER_USER_RANGE + it }
            json.put(JSONObject()
                .put("pkg", pkg)
                .put("uid", uid)
                .put("system", preinstalled)
                .put("installed", present)
                .put("enabled", state?.enabled)
                .put("suspended", state?.suspendedByShell == true)
                .put("hasCode", entry?.hasCode)
                .put("persistent", entry?.persistent)
                .put("sharedUser", entry?.sharedUser)
                .put("codePath", entry?.codePath)
                .put("installer", installers[pkg])
                .put("launcher", launcher?.contains(pkg))
                .apply {
                    if (firstBoot != null) dump?.firstInstall?.get(pkg)?.let {
                        put("factory", it - firstBoot in 0..FACTORY_WINDOW_MS)
                    }
                })
        }
        return json
    }

    fun modules(output: String): JSONArray? {
        val json = JSONArray()
        val seen = HashSet<String>()
        for (line in output.lineSequence()) {
            if (line.isBlank()) continue
            val match = moduleLine.matchEntire(line.trim()) ?: return null
            val (path, pkg, version) = match.destructured
            if (!acceptsName(pkg)) return null
            if (!seen.add(pkg)) continue
            json.put(JSONObject().put("pkg", pkg).put("path", path).put("version", version.toLongOrNull()))
        }
        return json
    }
}
