// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/**
 * Append-only record of what the monitor observed, kept until the app has read it.
 *
 * Events go into segment files named after their first sequence number. Segments the app has read
 * are deleted on its next read, so the spool only grows while the app is not opened. Beyond
 * [limitBytes] (0: no limit), or when free space falls below [MIN_FREE_BYTES], the oldest segment
 * is dropped; the app sees the gap in sequence numbers and reports the loss.
 */
internal class EventSpool(private val directory: File, private val segmentBytes: Long = SEGMENT_BYTES) {
    @Volatile var limitBytes = DEFAULT_LIMIT_BYTES
    @Volatile var exclusions = MonitoringExclusions()

    private var seq: Long
    private var current: File
    private val epoch: Long
    private var rotateAfterWriteFailure = false

    init {
        directory.mkdirs()
        val existing = segments()
        val last = existing.lastOrNull()
        val tail = last?.let(::scanTail)
        seq = tail?.sequence ?: 0L
        // Leave a damaged line in its original segment so that later writes cannot join it.
        // An empty successor also records the gap if the probe exits again before writing.
        current = if (last != null && !endsWithNewline(last)) {
            if (tail?.validLastLine != true) seq++
            segmentFile(seq + 1).also { it.createNewFile() }
        } else last ?: segmentFile(seq + 1)
        val identity = File(directory, "events-epoch")
        epoch = (if (existing.isEmpty()) null else runCatching { identity.readText().toLong() }.getOrNull())
            ?: java.security.SecureRandom().nextLong().ushr(1).coerceAtLeast(1L).also {
                val temp = File(directory, ".events-epoch")
                temp.writeText(it.toString())
                java.nio.file.Files.move(temp.toPath(), identity.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
    }

    @Synchronized
    fun append(event: JSONObject) {
        if (exclusions.blocksEvent(event)) return
        event.put("seq", ++seq)
        if (current.length() >= segmentBytes || rotateAfterWriteFailure) current = segmentFile(seq)
        rotateAfterWriteFailure = true
        current.appendText(event.toString() + "\n")
        rotateAfterWriteFailure = false
        trim()
    }

    /**
     * Events after [after], oldest first, at most [max]. [after] is what the app has stored, so
     * segments that end at or before it are deleted. "first" is the oldest sequence still kept.
     */
    @Synchronized
    fun read(knownEpoch: Long, cursor: Long, max: Int): JSONObject {
        val after = cursor.takeIf { knownEpoch == epoch && it in 0..seq } ?: 0L
        val all = segments()
        all.zipWithNext().forEach { (segment, next) -> if (firstSequence(next) - 1 <= after) segment.delete() }
        val events = JSONArray()
        var more = false
        for (file in segments()) {
            file.useLines { lines ->
                for (line in lines) {
                    val event = runCatching { JSONObject(line) }.getOrNull() ?: continue
                    if (event.optLong("seq") <= after) continue
                    if (events.length() >= max) { more = true; break }
                    events.put(event)
                }
            }
            if (more) break
        }
        val first = segments().firstOrNull()?.let(::firstSequence) ?: (seq + 1)
        return JSONObject().put("epoch", epoch).put("events", events).put("last", seq).put("first", first).put("more", more)
    }

    @Synchronized
    fun sizeBytes(): Long = segments().sumOf { it.length() }

    /** Drops the oldest segments while over the limit or short of space; the current one stays. */
    private fun trim() {
        var all = segments()
        while (all.size > 1) {
            val over = limitBytes > 0 && all.sumOf { it.length() } > limitBytes
            val short = directory.usableSpace < MIN_FREE_BYTES
            if (!over && !short) break
            all.first().delete()
            all = all.drop(1)
        }
    }

    private fun segments(): List<File> =
        directory.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }.orEmpty()
            .sortedBy(::firstSequence)

    private fun segmentFile(firstSeq: Long) = File(directory, "$PREFIX$firstSeq$SUFFIX")

    private fun firstSequence(file: File): Long = file.name.removePrefix(PREFIX).removeSuffix(SUFFIX).toLongOrNull() ?: 0L

    private data class Tail(val sequence: Long, val validLastLine: Boolean)

    private fun scanTail(file: File): Tail {
        var last = firstSequence(file) - 1
        var valid = false
        file.forEachLine { line ->
            val sequence = sequenceOf(line)
            valid = sequence != null
            if (sequence != null) last = sequence
        }
        return Tail(last, valid)
    }

    private fun sequenceOf(line: String): Long? =
        runCatching { JSONObject(line).getLong("seq") }.getOrNull()

    private fun endsWithNewline(file: File): Boolean {
        val length = file.length()
        if (length == 0L) return true
        return RandomAccessFile(file, "r").use {
            it.seek(length - 1)
            it.read() == '\n'.code
        }
    }

    companion object {
        private const val PREFIX = "events-"
        private const val SUFFIX = ".jsonl"
        const val SEGMENT_BYTES = 8L * 1024 * 1024
        const val DEFAULT_LIMIT_BYTES = 256L * 1024 * 1024
        /** Whatever the limit, the monitor never fills the phone. */
        const val MIN_FREE_BYTES = 512L * 1024 * 1024
    }
}
