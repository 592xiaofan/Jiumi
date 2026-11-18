// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

/**
 * Which apps frozen from here are no longer frozen. Only the management record and the state the
 * monitor read are used: an app counts as frozen from here when its latest record is a freeze (the
 * user's, or a refreeze), and as lapsed when the monitor reads it installed, turned on and not
 * suspended. Who turned it on is not known, so nothing is said about it.
 */
object Freezes {
    /** Where an app's record leaves it. */
    enum class Standing { FROZEN, LAPSED, OTHER }

    /** The latest standing of each app in [records] (any order), with the time and uid of that record. */
    fun standings(records: List<JiumiStore.UserAction>): Map<String, Pair<Standing, JiumiStore.UserAction>> =
        records.asSequence().filter { standingOf(it.action) != null }.sortedBy { it.t }
            .associateBy { it.pkg }.mapValues { (_, r) -> standingOf(r.action)!! to r }

    /**
     * The apps whose latest record is a freeze but which [current] shows running free; [current] holds
     * only packages the monitor read, so an app it did not read is left alone.
     */
    fun lapsed(records: List<JiumiStore.UserAction>, current: List<ShellPackage>): List<ShellPackage> {
        val standings = standings(records)
        return current.filter { p ->
            standings[p.pkg]?.first == Standing.FROZEN && p.installed && !p.suspended &&
                p.enabledSetting != null && !Management.isDisabled(p.enabledSetting)
        }
    }

    /** Apps whose freeze lapsed and was not dealt with since, with when it was found. */
    fun lapsedSince(records: List<JiumiStore.UserAction>): Map<String, Long> =
        standings(records).filterValues { it.first == Standing.LAPSED }.mapValues { it.value.second.t }

    private fun standingOf(action: String): Standing? = when (action) {
        ManageAction.DISABLE.id, JiumiStore.PackageEvent.REFROZEN.id -> Standing.FROZEN
        JiumiStore.PackageEvent.LAPSED.id -> Standing.LAPSED
        in ManageAction.entries.map { it.id } -> Standing.OTHER
        else -> null
    }
}
