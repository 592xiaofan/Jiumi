// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.dns

import java.net.InetAddress

/**
 * The little of IPv4, UDP and DNS that the domain recorder needs: taking a query out of a packet
 * read from the VPN interface, wrapping the answer back into one, and reading names and addresses
 * from a DNS message. Nothing here changes a query or an answer.
 */
object DnsPacket {

    /** A UDP datagram read from the interface. */
    class Datagram(val src: ByteArray, val srcPort: Int, val dst: ByteArray, val dstPort: Int, val payload: ByteArray)

    /** Null for anything but an unfragmented IPv4 UDP datagram. */
    fun readUdp4(packet: ByteArray, length: Int): Datagram? {
        if (length < IP_HEADER + UDP_HEADER) return null
        if ((packet[0].toInt() shr 4) and 0xF != 4) return null
        val ihl = (packet[0].toInt() and 0xF) * 4
        if (ihl < IP_HEADER || packet[9].toInt() and 0xFF != PROTO_UDP) return null
        // Fragments are never DNS queries of the size the resolver sends.
        if (u16(packet, 6) and 0x3FFF != 0) return null
        val total = minOf(u16(packet, 2), length)
        if (total < ihl + UDP_HEADER) return null
        val udpLength = u16(packet, ihl + 4)
        val end = minOf(total, ihl + udpLength)
        if (end < ihl + UDP_HEADER) return null
        return Datagram(
            packet.copyOfRange(12, 16), u16(packet, ihl), packet.copyOfRange(16, 20), u16(packet, ihl + 2),
            packet.copyOfRange(ihl + UDP_HEADER, end)
        )
    }

    /** An IPv4 UDP packet carrying [payload]; the UDP checksum is left out, as IPv4 allows. */
    fun writeUdp4(src: ByteArray, srcPort: Int, dst: ByteArray, dstPort: Int, payload: ByteArray): ByteArray {
        val total = IP_HEADER + UDP_HEADER + payload.size
        val p = ByteArray(total)
        p[0] = 0x45
        put16(p, 2, total)
        put16(p, 6, 0x4000) // don't fragment
        p[8] = 64
        p[9] = PROTO_UDP.toByte()
        System.arraycopy(src, 0, p, 12, 4)
        System.arraycopy(dst, 0, p, 16, 4)
        put16(p, 10, checksum(p, 0, IP_HEADER))
        put16(p, IP_HEADER, srcPort)
        put16(p, IP_HEADER + 2, dstPort)
        put16(p, IP_HEADER + 4, UDP_HEADER + payload.size)
        System.arraycopy(payload, 0, p, IP_HEADER + UDP_HEADER, payload.size)
        return p
    }

    data class TcpSegment(
        val src: Int, val srcPort: Int, val dst: Int, val dstPort: Int,
        val seq: Int, val ack: Int, val flags: Int, val window: Int, val mss: Int, val payload: ByteArray
    )

    fun readTcp4(packet: ByteArray, length: Int): TcpSegment? {
        if (length < IP_HEADER + TCP_HEADER || packet[0].toInt() ushr 4 != 4 || packet[9].toInt() != PROTO_TCP) return null
        if (u16(packet, 6) and 0x3FFF != 0) return null
        val ihl = (packet[0].toInt() and 15) * 4
        val end = u16(packet, 2)
        if (ihl < IP_HEADER || end !in (ihl + TCP_HEADER)..length) return null
        val header = ((packet[ihl + 12].toInt() and 255) ushr 4) * 4
        if (header < TCP_HEADER || ihl + header > end) return null
        var mss = 536
        var at = ihl + TCP_HEADER
        while (at < ihl + header) {
            val kind = packet[at++].toInt() and 255
            if (kind == 0) break
            if (kind == 1) continue
            if (at == ihl + header) return null
            val size = packet[at].toInt() and 255
            if (size < 2 || at - 1 + size > ihl + header) return null
            if (kind == 2 && size == 4) mss = u16(packet, at + 1).coerceAtLeast(1)
            at += size - 1
        }
        return TcpSegment(u32(packet, 12), u16(packet, ihl), u32(packet, 16), u16(packet, ihl + 2),
            u32(packet, ihl + 4), u32(packet, ihl + 8), packet[ihl + 13].toInt() and 255,
            u16(packet, ihl + 14), mss, packet.copyOfRange(ihl + header, end))
    }

    fun writeTcpReply(peer: TcpSegment, seq: Int, ack: Int, flags: Int, payload: ByteArray): ByteArray {
        val size = TCP_HEADER + payload.size
        val p = ByteArray(IP_HEADER + size)
        p[0] = 0x45
        put16(p, 2, p.size)
        put16(p, 6, 0x4000)
        p[8] = 64
        p[9] = PROTO_TCP.toByte()
        put32(p, 12, peer.dst)
        put32(p, 16, peer.src)
        put16(p, 10, checksum(p, 0, IP_HEADER))
        put16(p, IP_HEADER, peer.dstPort)
        put16(p, IP_HEADER + 2, peer.srcPort)
        put32(p, IP_HEADER + 4, seq)
        put32(p, IP_HEADER + 8, ack)
        p[IP_HEADER + 12] = 0x50
        p[IP_HEADER + 13] = flags.toByte()
        put16(p, IP_HEADER + 14, 32_768)
        payload.copyInto(p, IP_HEADER + TCP_HEADER)
        val pseudoSum = u16(p, 12) + u16(p, 14) + u16(p, 16) + u16(p, 18) + PROTO_TCP + size
        put16(p, IP_HEADER + 16, checksum(p, IP_HEADER, size, pseudoSum.toLong()))
        return p
    }

    fun address(ip: Int): ByteArray = ByteArray(4).also { put32(it, 0, ip) }
    private fun u32(b: ByteArray, at: Int): Int = (u16(b, at) shl 16) or u16(b, at + 2)
    private fun put32(b: ByteArray, at: Int, value: Int) {
        put16(b, at, value ushr 16)
        put16(b, at + 2, value)
    }

    fun udpLimit(query: ByteArray, mtu: Int): Int {
        val standard = 512
        if (query.size < DNS_HEADER) return standard
        var at = DNS_HEADER
        repeat(u16(query, 4)) {
            val next = readName(query, at)?.second ?: return standard
            at = next + 4
            if (at > query.size) return standard
        }
        val nonAdditional = u16(query, 6) + u16(query, 8)
        repeat(nonAdditional + u16(query, 10)) { i ->
            val next = readName(query, at)?.second ?: return standard
            if (next + 10 > query.size) return standard
            if (i >= nonAdditional && u16(query, next) == 41) {
                return u16(query, next + 2).coerceIn(standard, mtu - IP_HEADER - UDP_HEADER)
            }
            at = next + 10 + u16(query, next + 8)
            if (at > query.size) return standard
        }
        return standard
    }

    /** A resolver failure or truncation response containing only the original questions. */
    fun errorReply(query: ByteArray, rcode: Int, truncated: Boolean = false): ByteArray {
        var end = DNS_HEADER
        var questions = u16(query, 4)
        for (i in 0 until questions) {
            val next = readName(query, end)?.second
            if (next == null || next + 4 > query.size) {
                end = DNS_HEADER
                questions = 0
                break
            }
            end = next + 4
        }
        return query.copyOf(end).apply {
            put16(this, 2, (u16(query, 2) and 0x7910) or 0x8080 or (if (truncated) 0x0200 else 0) or rcode)
            put16(this, 4, questions)
            fill(0, 6, DNS_HEADER)
        }
    }

    /**
     * What a DNS message says: the name and type asked for, and for an answer its result code and
     * the addresses it gives (CNAME chains followed). [truncated]: the answer did not fit in UDP.
     */
    data class Message(
        val id: Int, val response: Boolean, val truncated: Boolean, val rcode: Int,
        val name: String?, val type: Int, val addresses: List<String>
    )

    fun readMessage(bytes: ByteArray): Message? = runCatching { parse(bytes) }.getOrNull()

    private fun parse(bytes: ByteArray): Message? {
        if (bytes.size < DNS_HEADER) return null
        val id = u16(bytes, 0)
        val flags = u16(bytes, 2)
        val questions = u16(bytes, 4)
        val answers = u16(bytes, 6)
        var at = DNS_HEADER
        var name: String? = null
        var type = 0
        for (i in 0 until questions) {
            val (n, next) = readName(bytes, at) ?: return null
            if (next + 4 > bytes.size) return null
            if (i == 0) { name = n.lowercase(); type = u16(bytes, next) }
            at = next + 4
        }
        // Records after the questions are read as far as they are whole.
        val addresses = ArrayList<String>()
        for (i in 0 until answers) {
            val (_, next) = readName(bytes, at) ?: break
            if (next + 10 > bytes.size) break
            val rrType = u16(bytes, next)
            val length = u16(bytes, next + 8)
            val data = next + 10
            if (data + length > bytes.size) break
            if (rrType == TYPE_A && length == 4 || rrType == TYPE_AAAA && length == 16) {
                InetAddress.getByAddress(bytes.copyOfRange(data, data + length)).hostAddress?.let(addresses::add)
            }
            at = data + length
        }
        return Message(id, flags and 0x8000 != 0, flags and 0x0200 != 0, flags and 0xF, name, type, addresses)
    }

    /** A name and the offset just past it where it is written; compression pointers are followed. */
    private fun readName(bytes: ByteArray, start: Int): Pair<String, Int>? {
        val labels = ArrayList<String>()
        var at = start
        var end = -1
        var jumps = 0
        while (true) {
            if (at >= bytes.size) return null
            val len = bytes[at].toInt() and 0xFF
            when {
                len == 0 -> { if (end < 0) end = at + 1; break }
                len and 0xC0 == 0xC0 -> {
                    if (at + 1 >= bytes.size || ++jumps > MAX_JUMPS) return null
                    if (end < 0) end = at + 2
                    at = ((len and 0x3F) shl 8) or (bytes[at + 1].toInt() and 0xFF)
                }
                len and 0xC0 != 0 -> return null
                else -> {
                    if (at + 1 + len > bytes.size) return null
                    labels += String(bytes, at + 1, len, Charsets.US_ASCII)
                    at += 1 + len
                }
            }
        }
        return labels.joinToString(".") to end
    }

    fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun put16(b: ByteArray, at: Int, v: Int) {
        b[at] = (v shr 8).toByte(); b[at + 1] = v.toByte()
    }

    private fun checksum(b: ByteArray, from: Int, length: Int, initial: Long = 0L): Int {
        var sum = initial
        var i = from
        while (i + 1 < from + length) { sum += u16(b, i); i += 2 }
        if (i < from + length) sum += (b[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv().toInt() and 0xFFFF
    }

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    private const val IP_HEADER = 20
    private const val UDP_HEADER = 8
    private const val DNS_HEADER = 12
    private const val TCP_HEADER = 20
    private const val PROTO_TCP = 6
    private const val PROTO_UDP = 17
    private const val MAX_JUMPS = 20
}
