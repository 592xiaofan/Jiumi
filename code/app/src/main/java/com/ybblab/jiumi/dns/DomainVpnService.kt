// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.dns

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.OsConstants
import com.ybblab.jiumi.JiumiApp
import com.ybblab.jiumi.R
import com.ybblab.jiumi.data.JiumiStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Records which app looks up which domain. The VPN it sets up carries nothing but DNS: its only
 * route is a private DNS address that the phone is told to use, so every other packet goes out as
 * before, without passing through this app. Each query is passed unchanged to the DNS servers of
 * the network the phone is on, the answer handed back unchanged, and the name, the addresses it
 * resolved to and the app that asked are kept on the phone.
 */
class DomainVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var excludedPackages: Map<String, Int> = emptyMap()
    private var output: FileOutputStream? = null
    private var tcpEndpoint: DnsTcp? = null
    /**
     * One thread per query in flight. A page asks dozens of names at once, and each waits on the
     * network; with a queue in front, queries would wait on each other and apps would time out and
     * ask again. Only beyond [MAX_IN_FLIGHT] at once is a query dropped, which the asking app retries.
     */
    private val pool = ThreadPoolExecutor(4, MAX_IN_FLIGHT, 30, TimeUnit.SECONDS, java.util.concurrent.SynchronousQueue(),
        ThreadPoolExecutor.AbortPolicy())
    private var flusher: ScheduledExecutorService? = null
    private lateinit var recorder: DomainRecorder
    private lateinit var connectivity: ConnectivityManager
    /** The networks the phone is on, apart from VPNs, with what the system says of each. */
    private val networks = java.util.concurrent.ConcurrentHashMap<Network, Pair<NetworkCapabilities?, LinkProperties?>>()
    @Volatile private var upstream: Network? = null
    @Volatile private var servers: List<InetAddress> = emptyList()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            networks[network] = caps to (networks[network]?.second ?: connectivity.getLinkProperties(network)); choose()
        }
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            networks[network] = (networks[network]?.first ?: connectivity.getNetworkCapabilities(network)) to lp; choose()
        }
        override fun onLost(network: Network) { networks.remove(network); choose() }
    }

    /**
     * Queries go to the DNS servers of the network the phone would use: a validated Wi-Fi first,
     * then any validated network, then any.
     */
    private fun choose() {
        fun has(caps: NetworkCapabilities?, c: Int) = caps?.hasCapability(c) == true
        val best = networks.entries.sortedWith(compareByDescending<Map.Entry<Network, Pair<NetworkCapabilities?, LinkProperties?>>> {
            has(it.value.first, NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.thenByDescending { it.value.first?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true })
            .firstOrNull { it.value.second?.dnsServers?.isNotEmpty() == true }
        upstream = best?.key
        servers = best?.value?.second?.dnsServers.orEmpty()
        _strictPrivateDns.value = best?.value?.second?.privateDnsServerName != null
    }

    override fun onCreate() {
        super.onCreate()
        connectivity = getSystemService(ConnectivityManager::class.java)
        recorder = DomainRecorder((application as JiumiApp).store)
        connectivity.registerNetworkCallback(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build(), networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            stopSelf()
            return START_NOT_STICKY
        }
        val exclusions = (application as JiumiApp).monitoring.exclusions.value.active.mapValues { it.value.uid }
        if (tun != null && excludedPackages != exclusions) shutdown()
        if (tun == null && !establish(exclusions)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun establish(exclusions: Map<String, Int>): Boolean {
        val fd = runCatching {
            Builder()
                .setMtu(MTU)
                .setSession(getString(R.string.domains_vpn_session))
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(DNS_ADDRESS)
                .addRoute(DNS_ADDRESS, 32)
                .addDisallowedApplication(packageName)
                .apply {
                    for (pkg in exclusions.keys) {
                        try {
                            addDisallowedApplication(pkg)
                        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                            // The rule stays saved if the app is removed.
                        }
                    }
                }
                .setBlocking(true)
                .setMetered(false)
                .establish()
        }.getOrNull() ?: return false
        excludedPackages = exclusions
        tun = fd
        output = FileOutputStream(fd.fileDescriptor)
        val input = FileInputStream(fd.fileDescriptor)
        val tcp = DnsTcp(SystemClock::elapsedRealtime, ::writePacket, ::answerTcp)
        tcpEndpoint = tcp
        reader = Thread({ readLoop(input, tcp) }, "dns-vpn").apply { start() }
        flusher = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleWithFixedDelay({ runCatching { recorder.flush() } }, FLUSH_S, FLUSH_S, TimeUnit.SECONDS)
            scheduleWithFixedDelay(tcp::tick, 250, 250, TimeUnit.MILLISECONDS)
        }
        _running.value = true
        return true
    }

    private fun readLoop(input: FileInputStream, tcp: DnsTcp) {
        val buffer = ByteArray(MAX_PACKET)
        while (!Thread.currentThread().isInterrupted) {
            val n = runCatching { input.read(buffer) }.getOrElse { return }
            if (n <= 0) continue
            val datagram = DnsPacket.readUdp4(buffer, n)
            if (datagram != null) {
                if (datagram.dstPort == 53 && datagram.dst.contentEquals(DNS_IP_BYTES)) submit { answerUdp(datagram) }
            } else {
                DnsPacket.readTcp4(buffer, n)?.takeIf { it.dstPort == 53 && it.dst == DNS_IP }?.let(tcp::receive)
            }
        }
    }

    private fun submit(task: () -> Unit): Boolean = try {
        pool.execute(task)
        true
    } catch (_: RejectedExecutionException) {
        false
    }

    private fun writePacket(packet: ByteArray) {
        runCatching { synchronized(this) { output?.write(packet) } }
    }

    private fun answerUdp(q: DnsPacket.Datagram) {
        val t = System.currentTimeMillis()
        var uid = -1
        // Look the caller up while the request is in flight and its socket is still open.
        val reply = forward(q.payload) { uid = ownerOf(OsConstants.IPPROTO_UDP, q.src, q.srcPort, q.dst, q.dstPort) }
        if (reply != null) {
            val payload = if (reply.size <= DnsPacket.udpLimit(q.payload, MTU)) reply
                else DnsPacket.errorReply(q.payload, 0, truncated = true)
            writePacket(DnsPacket.writeUdp4(q.dst, q.dstPort, q.src, q.srcPort, payload))
        }
        record(t, uid, q.payload, reply)
    }

    private fun answerTcp(peer: DnsPacket.TcpSegment, message: ByteArray, complete: (ByteArray?) -> Unit) {
        if (!submit {
            val t = System.currentTimeMillis()
            val uid = ownerOf(OsConstants.IPPROTO_TCP, DnsPacket.address(peer.src), peer.srcPort,
                DnsPacket.address(peer.dst), peer.dstPort)
            val network = upstream
            val reply = servers.take(2).firstNotNullOfOrNull { server -> runCatching { tcp(server, network, message) }.getOrNull() }
            complete(reply)
            record(t, uid, message, reply)
        }) complete(null)
    }

    private fun record(t: Long, uid: Int, message: ByteArray, reply: ByteArray?) {
        if ((application as JiumiApp).monitoring.exclusions.value.blocks(uid, null, t)) return
        val query = DnsPacket.readMessage(message) ?: return
        val name = query.name ?: return
        val result = reply?.let(DnsPacket::readMessage)
        recorder.add(t, uid, name, query.type, result?.addresses.orEmpty(), result?.rcode ?: -1)
    }

    /** The system resolver uses a socket owned by the app it asks for. */
    private fun ownerOf(protocol: Int, src: ByteArray, srcPort: Int, dst: ByteArray, dstPort: Int): Int = runCatching {
        connectivity.getConnectionOwnerUid(protocol, InetSocketAddress(InetAddress.getByAddress(src), srcPort),
            InetSocketAddress(InetAddress.getByAddress(dst), dstPort))
    }.getOrDefault(-1)

    /**
     * The answer from the network's own DNS servers, over TCP when it did not fit in UDP. The query
     * goes to the first two servers at once and the first answer is taken, so one server that does
     * not answer (often an IPv6 one the network does not route) costs no time; it is sent once more
     * when neither answered soon. [meanwhile] runs once the query is out.
     */
    private fun forward(query: ByteArray, meanwhile: () -> Unit): ByteArray? {
        var ran = false
        val once = { if (!ran) { ran = true; runCatching(meanwhile) } }
        try {
            val network = upstream
            val targets = servers.take(2)
            if (targets.isEmpty() || query.size < 12) return null
            val (server, reply) = runCatching { udp(targets, network, query, once) }.getOrNull() ?: return null
            val truncated = DnsPacket.readMessage(reply)?.truncated == true
            return if (truncated) runCatching { tcp(server, network, query) }.getOrNull() ?: reply else reply
        } finally {
            once()
        }
    }

    private fun udp(targets: List<InetAddress>, network: Network?, query: ByteArray, sent: () -> Unit): Pair<InetAddress, ByteArray>? {
        val socket = DatagramSocket()
        try {
            network?.bindSocket(socket)
            protect(socket)
            fun send() = targets.forEach { runCatching { socket.send(DatagramPacket(query, query.size, it, 53)) } }
            send()
            sent()
            val buffer = ByteArray(MAX_DNS)
            val start = System.currentTimeMillis()
            var resent = false
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                if (elapsed >= UDP_TIMEOUT_MS) return null
                if (!resent && elapsed >= RESEND_MS) { send(); resent = true }
                socket.soTimeout = ((if (resent) UDP_TIMEOUT_MS else RESEND_MS) - elapsed).toInt().coerceAtLeast(1)
                val packet = DatagramPacket(buffer, buffer.size)
                try { socket.receive(packet) } catch (_: java.net.SocketTimeoutException) { continue }
                // Only the answer to this query, from a server it was sent to.
                if (packet.port != 53 || !isAnswerTo(query, buffer, packet.length)) continue
                val from = packet.address
                if (targets.none { it == from }) continue
                return from to buffer.copyOf(packet.length)
            }
        } finally {
            socket.close()
        }
    }

    private fun tcp(server: InetAddress, network: Network?, query: ByteArray): ByteArray =
        Socket().use { socket ->
            network?.bindSocket(socket)
            protect(socket)
            socket.soTimeout = TCP_TIMEOUT_MS
            socket.connect(InetSocketAddress(server, 53), TCP_TIMEOUT_MS)
            DataOutputStream(socket.getOutputStream()).apply { writeShort(query.size); write(query); flush() }
            val input = DataInputStream(socket.getInputStream())
            ByteArray(input.readUnsignedShort()).also {
                input.readFully(it)
                check(isAnswerTo(query, it, it.size)) { "Unexpected DNS answer" }
            }
        }

    private fun isAnswerTo(query: ByteArray, response: ByteArray, length: Int): Boolean =
        length >= 12 && response[0] == query[0] && response[1] == query[1] && response[2].toInt() and 0x80 != 0

    override fun onRevoke() {
        // Another VPN took over, or the user turned this one off in the system's settings.
        (application as JiumiApp).domains.onRevoked()
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        pool.shutdownNow()
        super.onDestroy()
    }

    private fun shutdown() {
        reader?.interrupt(); reader = null
        flusher?.shutdownNow(); flusher = null
        runCatching { tun?.close() }; tun = null; output = null
        tcpEndpoint?.clear(); tcpEndpoint = null
        runCatching { recorder.flush() }
        _running.value = false
    }

    companion object {
        private const val ACTION_STOP = "com.ybblab.jiumi.dns.STOP"
        /** Private addresses of this VPN; only the DNS address is routed into it. */
        private const val VPN_ADDRESS = "10.215.173.1"
        private const val DNS_ADDRESS = "10.215.173.2"
        private const val DNS_IP = 0x0AD7AD02
        private val DNS_IP_BYTES = DnsPacket.address(DNS_IP)
        private const val MTU = 1_500
        private const val MAX_PACKET = 65_535
        private const val MAX_DNS = 65535
        /** The resolver itself asks again after about 5 s; an answer later than this is of no use. */
        private const val UDP_TIMEOUT_MS = 4_000L
        private const val RESEND_MS = 1_000L
        private const val TCP_TIMEOUT_MS = 3_000
        private const val MAX_IN_FLIGHT = 64
        private const val FLUSH_S = 5L

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _strictPrivateDns = MutableStateFlow(false)
        /** Private DNS is set to a named provider: lookups then go to it directly, past this VPN. */
        val strictPrivateDns: StateFlow<Boolean> = _strictPrivateDns.asStateFlow()

        fun start(context: Context) {
            runCatching { context.startService(Intent(context, DomainVpnService::class.java)) }
        }

        fun refresh(context: Context) {
            if (_running.value) start(context)
        }

        fun stop(context: Context) {
            if (!_running.value) return
            runCatching { context.startService(Intent(context, DomainVpnService::class.java).setAction(ACTION_STOP)) }
        }
    }
}

/**
 * Lookups waiting to be stored: repeats of one name by one app within a flush are kept as one row
 * with a count, as an app asks the same name again and again.
 */
class DomainRecorder(private val store: JiumiStore) {
    private val pending = LinkedHashMap<Triple<Int, String, Int>, JiumiStore.Lookup>()

    fun add(t: Long, uid: Int, name: String, type: Int, answers: List<String>, rcode: Int) {
        // Reverse lookups name an address, not a place an app goes.
        if (name.isEmpty() || name.endsWith(".arpa")) return
        val key = Triple(uid, name, type)
        synchronized(pending) {
            val seen = pending[key]
            pending[key] = if (seen == null) JiumiStore.Lookup(t, t, uid, name, type, answers.take(MAX_ANSWERS), rcode, 1)
            else seen.copy(end = maxOf(seen.end, t), answers = (seen.answers + answers).distinct().take(MAX_ANSWERS),
                rcode = rcode, count = seen.count + 1)
        }
    }

    fun flush() {
        val batch = synchronized(pending) { pending.values.toList().also { pending.clear() } }
        store.insertLookups(batch)
    }

    private companion object {
        const val MAX_ANSWERS = 16
    }
}
