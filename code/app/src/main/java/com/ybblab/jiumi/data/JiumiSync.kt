// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.data

import com.ybblab.jiumi.probe.IProbeService
import org.json.JSONArray
import org.json.JSONObject

/** Copies the monitor's spool and the system access history into the local store. */
class JiumiSync(private val store: JiumiStore, private val rules: AccessRules) {

    private var discreteRetryAfter = 0L
    private var countsRetryAfter = 0L

    /**
     * Returns how many new events it stored. Now and then it also reads the system's own records,
     * which reach back before monitoring began: the per-access record (about a week, a few kinds of
     * data, with times) and the long-term counts (every kind of data, per day).
     */
    fun sync(service: IProbeService, now: Long = System.currentTimeMillis()): Int {
        val stored = pullEvents(service)
        val elapsed = android.os.SystemClock.elapsedRealtime()
        if (elapsed >= discreteRetryAfter && now - store.meta(KEY_DISCRETE_READ_AT) >= DISCRETE_EVERY_MS) {
            discreteRetryAfter = elapsed + HISTORY_RETRY_MS
            runCatching { pullDiscrete(service) }
                .onSuccess { store.setMeta(store.writableDatabase, KEY_DISCRETE_READ_AT, now) }
                .onFailure { android.util.Log.w(TAG, "system access record not read", it) }
        }
        if (elapsed >= countsRetryAfter && now - store.meta(KEY_COUNTS_READ_AT) >= COUNTS_EVERY_MS) {
            countsRetryAfter = elapsed + HISTORY_RETRY_MS
            runCatching { pullCounts(service, now) }
                .onSuccess { store.setMeta(store.writableDatabase, KEY_COUNTS_READ_AT, now) }
                .onFailure { android.util.Log.w(TAG, "system access counts not read", it) }
        }
        return stored
    }

    /** Each refresh pages through one snapshot. Commit the time cursor only after its last page. */
    private fun pullDiscrete(service: IProbeService) {
        val from = store.meta(KEY_DISCRETE_FROM)
        val exclusions = store.exclusions
        var offset = 0
        while (true) {
            val page = JSONObject(service.readDiscreteHistory(from, offset, PAGE_HISTORY))
            val events = page.getJSONArray("events")
            val next = page.getInt("next")
            val more = page.getBoolean("more")
            check(next == offset + events.length() && (!more || next > offset)) { "Invalid discrete history page" }
            if (events.length() == 0) break
            val db = store.writableDatabase
            db.beginTransaction()
            try {
                for (i in 0 until events.length()) {
                    val e = events.getJSONObject(i)
                    val uid = e.getInt("uid")
                    val pkg = e.str("pkg")
                    if (exclusions.hides(uid, pkg) || exclusions.blocks(uid, pkg, e.getLong("t"), e.getLong("end"))) continue
                    if (rules.categoryOf(e.getString("op")) == null) continue
                    store.insertAccess(db, JiumiStore.Access(
                        t = e.getLong("t"), end = e.getLong("end"), op = e.getString("op"), uid = uid,
                        pkg = pkg, attr = e.str("attr"), count = 1, state = e.str("state"), screen = null,
                        result = 0, proxied = e.optString("via").endsWith("d"), source = "system"
                    ))
                }
                if (!more) store.setMeta(db, KEY_DISCRETE_FROM, events.getJSONObject(events.length() - 1).getLong("t"))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            if (!more) break
            offset = next
        }
    }

    /**
     * The system's long-term counts for the last [COUNT_DAYS] days. Its slots are merged into coarser
     * ones as they age, so the whole stretch is read again and replaces what was read before.
     */
    private fun pullCounts(service: IProbeService, now: Long) {
        val since = now - COUNT_DAYS * DAY_MS
        val exclusions = store.exclusions
        val counts = ArrayList<JiumiStore.OpCount>()
        val slots = ArrayList<LongRange>()
        var skip = 0
        while (true) {
            val page = JSONArray(service.readHistoricalOps(since, skip, PAGE_SLOTS))
            for (i in 0 until page.length()) {
                val slot = page.getJSONObject(i)
                val from = slot.getLong("from")
                val to = slot.getLong("to")
                slots += from..maxOf(from, to - 1)
                val ops = slot.getJSONArray("ops")
                for (j in 0 until ops.length()) {
                    val o = ops.getJSONObject(j)
                    // A maker's own operations are kept too: their meaning is not published, but that a
                    // component used them, and when the user was not using it, is still worth showing.
                    val op = o.getString("op")
                    if (rules.categoryOf(op) == null && !SystemCount.isVendor(op)) continue
                    val uid = o.getInt("uid")
                    val pkg = o.getString("pkg")
                    if (exclusions.hides(uid, pkg) || exclusions.blocks(uid, pkg, from, to - 1)) continue
                    counts += JiumiStore.OpCount(from, to, uid, pkg,
                        o.getString("op"), o.getLong("fg"), o.getLong("bg"), o.getLong("fgRejected"), o.getLong("bgRejected"),
                        o.optLong("durationMs"))
                }
            }
            if (page.length() < PAGE_SLOTS) break
            skip += page.length()
        }
        if (counts.isNotEmpty()) store.replaceOpCounts(counts, since, slots)
    }

    fun pullEvents(service: IProbeService): Int {
        val exclusions = store.exclusions
        var stored = 0
        var epoch = store.meta(KEY_EVENT_EPOCH)
        var after = store.meta(KEY_EVENT_SEQ)
        while (true) {
            val page = JSONObject(service.readEvents(epoch, after, PAGE))
            val pageEpoch = page.getLong("epoch")
            val last = page.getLong("last")
            val more = page.getBoolean("more")
            val events = page.getJSONArray("events")
            val cursorValid = pageEpoch == epoch && after in 0..last
            if (!cursorValid) {
                // A pre-epoch database already acknowledged the old spool's missing prefix.
                after = if (epoch == 0L && after > 0) (page.getLong("first") - 1).coerceIn(0L, last) else 0L
            }
            if (events.length() == 0 && after == last && cursorValid) break
            check(!more || events.length() > 0) { "Empty event page with more rows" }
            val db = store.writableDatabase
            db.beginTransaction()
            try {
                var lost = 0L
                var lossTime = 0L
                for (i in 0 until events.length()) {
                    val event = events.getJSONObject(i)
                    val sequence = event.getLong("seq")
                    check(sequence > after && sequence <= last) { "Invalid event sequence" }
                    if (sequence - after > 1L) {
                        lost += sequence - after - 1
                        if (lossTime == 0L) lossTime = event.getLong("t")
                    }
                    if (!exclusions.blocksEvent(event)) {
                        apply(db, event)
                        stored++
                    }
                    after = sequence
                }
                if (!more && last > after) {
                    lost += last - after
                    after = last
                    if (lossTime == 0L) lossTime = System.currentTimeMillis()
                }
                if (lost > 0) store.insertMonitorEvent(db, JiumiStore.MonitorEvent(lossTime, "lost", lost.toString()))
                epoch = pageEpoch
                store.setMeta(db, KEY_EVENT_EPOCH, epoch)
                store.setMeta(db, KEY_EVENT_SEQ, after)
                db.setTransactionSuccessful()
                if (lost > 0) stored++
            } finally {
                db.endTransaction()
            }
            if (!more) break
        }
        return stored
    }

    private fun apply(db: android.database.sqlite.SQLiteDatabase, e: JSONObject) {
        when (e.getString("kind")) {
            "access" -> store.insertAccess(db, JiumiStore.Access(
                t = e.getLong("t"), end = e.optLong("end", e.getLong("t")), op = e.getString("op"),
                uid = e.getInt("uid"), pkg = e.str("pkg"), attr = e.str("attr"), count = e.optInt("count", 1),
                state = e.str("state"), screen = if (e.has("screen")) e.getBoolean("screen") else null,
                result = e.optInt("result"), proxied = e.optInt("flags") and PROXIED_FLAGS != 0, source = "live"
            ))
            "active" -> if (e.getBoolean("active")) {
                store.insertAccess(db, JiumiStore.Access(
                    t = e.getLong("t"), end = e.getLong("t"), op = e.getString("op"), uid = e.getInt("uid"),
                    pkg = e.str("pkg"), attr = e.str("attr"), count = 1, state = e.str("state"),
                    screen = if (e.has("screen")) e.getBoolean("screen") else null, result = 0, proxied = false,
                    source = "active"
                ))
            } else {
                store.closeSession(db, e.getString("op"), e.getInt("uid"), e.str("pkg"), e.str("attr"), e.getLong("t"))
            }
            "call" -> store.insertCall(db, JiumiStore.Call(
                t = e.getLong("t"), end = e.optLong("end", e.getLong("t")), uid = e.getInt("uid"), pid = e.optInt("pid", -1),
                process = e.str("process"), iface = e.getString("iface"), method = e.getString("method"),
                count = e.optInt("count", 1), state = e.str("state"), screen = if (e.has("screen")) e.getBoolean("screen") else null,
                thread = e.str("thread")
            ))
            "net" -> store.insertConnection(db, JiumiStore.Connection(
                t = e.getLong("t"), end = e.optLong("end", e.getLong("t")), uid = e.getInt("uid"), proto = e.getString("proto"),
                addr = e.getString("addr"), port = e.getInt("port"), count = e.optInt("count", 1), state = e.str("state"),
                screen = if (e.has("screen")) e.getBoolean("screen") else null,
                sent = if (e.has("sent")) e.getLong("sent") else null,
                received = if (e.has("received")) e.getLong("received") else null,
                sentUnattended = if (e.has("sentUnattended")) e.getLong("sentUnattended") else null
            ))
            "monitor" -> store.insertMonitorEvent(db, JiumiStore.MonitorEvent(e.getLong("t"), e.getString("state"), e.str("reason")))
            "proc_start" -> store.insertProcess(db, JiumiStore.ProcessStart(
                e.getLong("t"), e.getInt("pid"), e.getInt("uid"), e.getString("process"),
                e.optString("reason"), e.optString("component")
            ))
        }
    }

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifEmpty { null }

    private companion object {
        const val TAG = "JiumiSync"
        const val KEY_EVENT_SEQ = "event_seq"
        const val KEY_EVENT_EPOCH = "event_epoch"
        const val KEY_DISCRETE_FROM = "system_access_from"
        const val KEY_DISCRETE_READ_AT = "system_access_read_at"
        const val KEY_COUNTS_READ_AT = "system_counts_read_at"
        const val HISTORY_RETRY_MS = 60_000L
        const val DISCRETE_EVERY_MS = 10 * 60_000L
        const val COUNTS_EVERY_MS = 30 * 60_000L
        const val COUNT_DAYS = 31
        const val DAY_MS = 24 * 3_600_000L
        const val PAGE_HISTORY = 2_000
        const val PAGE_SLOTS = 8
        const val PAGE = 500
        // AppOpsManager.OP_FLAG_TRUSTED_PROXIED | OP_FLAG_UNTRUSTED_PROXIED
        const val PROXIED_FLAGS = 8 or 16
    }
}
