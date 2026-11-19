// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.probe

import android.os.SystemClock
import org.json.JSONObject
import java.io.File

/**
 * Runs consecutive, slightly overlapping Perfetto sessions for two independent records: calls to
 * selected system-service methods, and packet metadata (Android 14+) that gives each connection
 * its byte counts. Each finished trace is read once and deleted; only matching calls, folded per
 * minute, reach the spool, and packet bytes go to [connections]. Tracing reads trace points that
 * already exist; it does not change how any process runs.
 */
internal class PerfettoTracer(
    private val packageName: String,
    private val spool: EventSpool,
    private val states: UidStates,
    private val connections: ConnectionWatcher,
    private val register: (Process) -> Unit,
    private val unregister: (Process) -> Unit
) : Thread("perfetto-tracer") {
    init { isDaemon = true }

    private val lock = Object()
    @Volatile private var callsOn = false
    @Volatile private var packetsOn = false
    /** Whether the platform registered the packet data source; asked once, as the capability check does. */
    private val packetSourceRegistered: Boolean by lazy {
        runCatching { Shell.run(listOf("perfetto", "--query")).output.contains(PACKET_SOURCE) }.getOrDefault(false)
    }
    private val enabled get() = callsOn || packetsOn
    @Volatile private var methods: Set<String> = emptySet()
    @Volatile private var interfaces: Set<String> = emptySet()
    /** Processes hosting content providers named in the rules, and IContentProvider transaction codes. */
    @Volatile private var providerHosts: Set<String> = emptySet()
    @Volatile private var providerCodes: Map<Int, String> = emptyMap()
    private var sessionCounter = 0
    private var watermarkNs = 0L
    private var packetWatermarkNs = 0L
    private val clients = HashMap<Int, Client?>()
    private val buckets = LinkedHashMap<CallKey, ObservationBucket>()
    @Volatile private var finishing = false
    private var incomplete = false

    /** [thread] is the calling thread's name, which often tells an SDK apart from its host app. */
    private data class Client(val pid: Int, val uid: Int, val process: String, val thread: String?)
    private data class CallKey(val uid: Int, val pid: Int?, val process: String?, val thread: String?, val iface: String, val method: String)

    /**
     * [selected] holds "IInterface.method" or "IInterface.*"; an empty set stops call tracing.
     * [packets] needs the platform's packet data source, which exists from Android 14.
     */
    fun configure(calls: Boolean, selected: Collection<String>, providerOperations: Map<Int, String>, packets: Boolean) {
        synchronized(lock) {
            methods = selected.toSet()
            providerHosts = selected.filter { it.startsWith(PROVIDER_PREFIX) }
                .map { it.removePrefix(PROVIDER_PREFIX).substringBeforeLast('.') }.toSet()
            providerCodes = providerOperations
            interfaces = selected.filter { it.endsWith(".*") }.map { it.removeSuffix(".*") }.toSet()
            callsOn = calls && selected.isNotEmpty()
            // Some ROMs ship Android 14+ without the data source; byte counts are then unknown, not zero.
            packetsOn = packets && android.os.Build.VERSION.SDK_INT >= 34 && packetSourceRegistered
            connections.countingBytes = packetsOn
            lock.notifyAll()
        }
        ProbeMain.log("call tracing ${if (callsOn) "on, ${selected.size} methods" else "off"}, " +
            "packet trace ${if (packetsOn) "on" else "off"}")
    }

    override fun run() {
        removeLeftoverTraces()
        var running: Session? = null
        while (!finishing) {
            if (!enabled) {
                running?.let { finish(it) }
                running = null
                buckets.values.forEach(::flush)
                buckets.clear()
                synchronized(lock) { while (!enabled && !finishing) lock.wait() }
                continue
            }
            val current = running ?: startSession() ?: run { waitUntilFinishing(RETRY_MS); null } ?: continue
            // Start the next session before this one ends so no call falls between them.
            waitUntilFinishing(SESSION_MS - OVERLAP_MS)
            val next = if (enabled && !finishing) startSession() else null
            finish(current)
            running = next
        }
        running?.let(::finish)
        buckets.values.forEach(::flush)
        buckets.clear()
    }

    private fun waitUntilFinishing(ms: Long) {
        val end = SystemClock.elapsedRealtime() + ms
        synchronized(lock) {
            var remaining = end - SystemClock.elapsedRealtime()
            while (!finishing && remaining > 0) {
                lock.wait(remaining)
                remaining = end - SystemClock.elapsedRealtime()
            }
        }
    }

    fun finishRecording(): Boolean {
        synchronized(lock) {
            finishing = true
            lock.notifyAll()
        }
        join(SESSION_MS + 10_000L)
        check(!isAlive) { "Perfetto trace could not be drained" }
        return incomplete
    }

    private class Session(val process: Process, val file: File)

    private fun startSession(): Session? = runCatching {
        val file = File(TRACE_DIRECTORY, "$packageName-calls-${sessionCounter++ % 4}.pftrace")
        file.delete()
        val process = ProcessBuilder("perfetto", "--txt", "-c", "-", "-o", file.path)
            .redirectErrorStream(true).start()
        register(process)
        try {
            process.outputStream.use { it.write(config().toByteArray()) }
            Session(process, file)
        } catch (error: Exception) {
            process.destroy()
            unregister(process)
            file.delete()
            throw error
        }
    }.onFailure { markIncomplete("trace start", it) }.getOrNull()

    private fun finish(session: Session) {
        try {
            session.process.inputStream.use { it.readBytes() }
            check(session.process.waitFor() == 0) { "Perfetto did not finish successfully" }
            check(session.file.isFile && session.file.length() > 0L) { "Perfetto did not produce a trace" }
            record(session.file.readBytes())
        } catch (error: Exception) {
            markIncomplete("trace read", error)
        } finally {
            unregister(session.process)
            session.file.delete()
        }
    }

    private fun record(trace: ByteArray) {
        if (packetsOn) {
            val packets = NetworkPackets.parse(trace, packetWatermarkNs)
            packetWatermarkNs = packets.watermarkNs
            // Trace timestamps use the boot clock.
            val offset = System.currentTimeMillis() - SystemClock.elapsedRealtimeNanos() / 1_000_000
            connections.addTraffic(packets.flows) { ns -> offset + ns / 1_000_000 }
        }
        if (!callsOn) return
        val hostNames = HashMap<Int, String?>()
        val result = BinderCalls.parse(trace, watermarkNs,
            selected = { iface, method -> iface in interfaces || "$iface.$method" in methods },
            providerOperation = { pid, code ->
                val host = hostNames.getOrPut(pid) { commandLine(pid)?.takeIf { it in providerHosts } }
                val operation = providerCodes[code]
                if (host != null && operation != null) "$PROVIDER_PREFIX$host" to operation else null
            })
        watermarkNs = result.watermarkNs
        // Trace timestamps use the boot clock; convert once per trace.
        val offsetMs = System.currentTimeMillis() - SystemClock.elapsedRealtimeNanos() / 1_000_000
        // Thread ids are reused, so they are resolved afresh for every trace.
        clients.clear()
        val tree = ProcessTreeScan.parse(trace)
        for (call in result.calls) {
            val client = clients.getOrPut(call.clientTid) { fromTree(tree, call.clientTid) ?: resolve(call.clientTid) }
            // The monitor's own lookups are not part of the record.
            if (client?.pid == android.os.Process.myPid()) continue
            val uid = client?.uid ?: -1
            val t = offsetMs + call.timestampNs / 1_000_000
            val key = CallKey(uid, client?.pid, client?.process, client?.thread, call.iface, call.method)
            val observed = ObservationState(if (uid < 0) "unknown" else states.stateAt(uid, t), states.screenAt(t))
            val bucket = buckets[key]
            if (bucket?.add(t, observed, WINDOW_MS) == true) continue
            bucket?.let(::flush)
            val event = JSONObject().put("kind", "call").put("uid", uid).put("pid", client?.pid ?: -1)
                .put("process", client?.process).put("thread", client?.thread).put("iface", call.iface).put("method", call.method)
            buckets[key] = ObservationBucket(event, t, observed)
        }
        val now = System.currentTimeMillis()
        buckets.values.removeAll { bucket -> (now - bucket.first >= WINDOW_MS).also { if (it) flush(bucket) } }
    }

    private fun flush(bucket: ObservationBucket) {
        runCatching { spool.append(bucket.event()) }.onFailure { markIncomplete("trace write", it) }
    }

    private fun markIncomplete(operation: String, error: Throwable) {
        incomplete = true
        ProbeMain.log("$operation failed: ${error.javaClass.simpleName}")
    }

    /** The caller as the trace recorded it; null when the trace has no entry for it. */
    private fun fromTree(tree: ProcessTreeScan.Tree, tid: Int): Client? {
        val thread = tree.threads[tid]
        val pid = thread?.pid ?: tid
        val process = tree.processes[pid] ?: return null
        return Client(pid, process.uid, process.name, thread?.name?.takeIf { tid != pid })
    }

    /** Thread to process and user, from /proc; a thread that has already exited stays unknown. */
    private fun resolve(tid: Int): Client? = runCatching {
        val status = File("/proc/$tid/status").readLines()
        fun field(name: String) = status.first { it.startsWith("$name:") }.substringAfter(':').trim().split(Regex("\\s+"))[0]
        val pid = field("Tgid").toInt()
        val uid = field("Uid").toInt()
        val process = commandLine(pid) ?: field("Name")
        val thread = if (tid == pid) null else runCatching { File("/proc/$tid/comm").readText().trim() }.getOrNull()
        Client(pid, uid, process, thread)
    }.getOrNull()

    private fun commandLine(pid: Int): String? =
        runCatching { File("/proc/$pid/cmdline").readText().substringBefore('\u0000') }.getOrNull()?.ifEmpty { null }

    /**
     * Packets get a buffer of their own: binder events are frequent enough to overwrite the
     * packet bundles in a shared ring buffer.
     */
    private fun config(): String = buildString {
        var packetBuffer = 0
        if (callsOn) {
            appendLine("buffers { size_kb: $BUFFER_KB fill_policy: RING_BUFFER }")
            packetBuffer = 1
        }
        if (packetsOn) appendLine("buffers { size_kb: $PACKET_BUFFER_KB fill_policy: RING_BUFFER }")
        if (callsOn) appendLine("""data_sources { config { name: "linux.ftrace" target_buffer: 0 ftrace_config {
          ftrace_events: "binder/binder_transaction"
          ftrace_events: "binder/binder_transaction_received"
          atrace_categories: "aidl"
        } } }""")
        // Reads /proc once for each process and thread that appears in the trace, so short-lived
        // callers keep their name and uid.
        if (callsOn) appendLine("""data_sources { config { name: "linux.process_stats" target_buffer: 0
          process_stats_config { scan_all_processes_on_start: false record_thread_names: true }
        } }""")
        // Interning off keeps each packet context inline; busy flows are summed into bundles.
        if (packetsOn) appendLine("""data_sources { config { name: "android.network_packets" target_buffer: $packetBuffer
          network_packet_trace_config { poll_ms: $PACKET_POLL_MS aggregation_threshold: $PACKET_AGGREGATION intern_limit: 0 }
        } }""")
        appendLine("duration_ms: $SESSION_MS")
    }

    private fun removeLeftoverTraces() {
        File(TRACE_DIRECTORY).listFiles { f -> f.name.startsWith("$packageName-calls-") }.orEmpty().forEach { it.delete() }
    }

    private companion object {
        const val PACKET_SOURCE = "android.network_packets"
        /** Prefix of provider operations in the rules: ContentProvider@<host process>.<operation>. */
        const val PROVIDER_PREFIX = "ContentProvider@"
        const val TRACE_DIRECTORY = "/data/misc/perfetto-traces"
        const val SESSION_MS = 30_000L
        const val OVERLAP_MS = 2_000L
        const val RETRY_MS = 300_000L
        const val BUFFER_KB = 6_144
        const val WINDOW_MS = 60_000L
        const val PACKET_POLL_MS = 2_000
        const val PACKET_BUFFER_KB = 1_024
        const val PACKET_AGGREGATION = 8
    }
}
