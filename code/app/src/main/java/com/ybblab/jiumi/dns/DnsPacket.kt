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

    private fun checksum(b: ByteArray, from: Int, length: Int): Int {
        var sum = 0L
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
    private const val PROTO_UDP = 17
    private const val MAX_JUMPS = 20
}
