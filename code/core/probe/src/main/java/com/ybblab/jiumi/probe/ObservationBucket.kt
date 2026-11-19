// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

import org.json.JSONObject

internal data class ObservationState(val process: String, val screen: Boolean)

/** Repeated observations of the same action while its process and screen state agree. */
internal class ObservationBucket(
    private val event: JSONObject,
    val first: Long,
    private val state: ObservationState
) {
    private var last = first
    private var count = 1

    fun add(time: Long, observed: ObservationState, window: Long): Boolean {
        if (observed != state || time - first !in 0 until window) return false
        last = maxOf(last, time)
        count++
        return true
    }

    fun event(): JSONObject = event.put("t", first).put("end", last).put("count", count)
        .put("state", state.process).put("screen", state.screen)
}
