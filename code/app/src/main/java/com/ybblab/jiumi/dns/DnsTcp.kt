// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.dns

import java.security.SecureRandom

/**
 * TCP endpoint for the private DNS address routed to the VPN. The peer is the local kernel.
 * One unacknowledged segment per connection keeps retransmission and flow control bounded.
 */
internal class DnsTcp(
    private val clock: () -> Long,
    private val send: (ByteArray) -> Unit,
    private val query: (DnsPacket.TcpSegment, ByteArray, (ByteArray?) -> Unit) -> Unit
) {
    private data class Key(val source: Int, val port: Int)
    private data class Sent(val seq: Int, val flags: Int, val data: ByteArray, var at: Long, var retries: Int = 0) {
        val end: Int get() = seq + data.size + if (flags and (SYN or FIN) != 0) 1 else 0
    }

    private class Connection(val peer: DnsPacket.TcpSegment, start: Int, now: Long) {
        var receive = peer.seq + 1
        var next = start
        var window = peer.window
        var touched = now
        var pending: Sent? = null
        var established = false
        var peerClosed = false
        var closing = false
        var inFlight = 0
        val header = ByteArray(2)
        var headerBytes = 0
        var message = EMPTY
        var messageBytes = 0
        val replies = ArrayDeque<ByteArray>()
        var replyOffset = 0
    }

    private val random = SecureRandom()
    private val connections = HashMap<Key, Connection>()

    @Synchronized
    fun receive(segment: DnsPacket.TcpSegment) {
        val key = Key(segment.src, segment.srcPort)
        val now = clock()
        val connection = connections[key]
        val flags = segment.flags
        if (flags and RST != 0) {
            if (segment.seq == connection?.receive) connections.remove(key)
            return
        }
        if (flags and SYN != 0 && flags and ACK == 0) {
            if (connection != null && !connection.established && connection.peer.seq == segment.seq) {
                connection.pending?.let { emit(connection, it.seq, it.flags, it.data) }
            } else if (connection == null && connections.size < MAX_CONNECTIONS) {
                val created = Connection(segment, random.nextInt(), now)
                connections[key] = created
                sendTracked(created, SYN or ACK, EMPTY)
            }
            return
        }
        if (connection == null) {
            if (flags and ACK != 0) send(DnsPacket.writeTcpReply(segment, segment.ack, 0, RST, EMPTY))
            return
        }

        if (flags and ACK == 0 || segment.ack - connection.next > 0) return
        if (!connection.established && (segment.ack != connection.next || segment.seq != connection.receive)) return
        connection.touched = now
        val pending = connection.pending
        if (pending != null && segment.ack == pending.end) {
            connection.pending = null
            if (pending.flags and FIN != 0) {
                connections.remove(key)
                return
            }
            connection.established = true
        }
        if (segment.ack == connection.next) connection.window = segment.window

        val data = segment.payload
        val seen = connection.receive - segment.seq
        // Ignore gaps; the peer retransmits once it sees our cumulative acknowledgement.
        if (!connection.peerClosed && seen in 0..data.size) {
            connection.receive += data.size - seen
            if (flags and FIN != 0) {
                connection.receive++
                connection.peerClosed = true
            }
            if (seen < data.size) accept(key, connection, data, seen)
        }
        if (connections[key] !== connection) return
        if (data.isNotEmpty() || flags and FIN != 0) emit(connection, connection.next, ACK, EMPTY)
        pump(connection)
    }

    private fun accept(key: Key, connection: Connection, bytes: ByteArray, start: Int) {
        var at = start
        while (at < bytes.size) {
            if (connection.headerBytes < 2) {
                connection.header[connection.headerBytes++] = bytes[at++]
                if (connection.headerBytes < 2) continue
                val size = DnsPacket.u16(connection.header, 0)
                if (size < DNS_HEADER || connection.inFlight + connection.replies.size >= MAX_QUERIES) {
                    reset(key, connection)
                    return
                }
                connection.message = ByteArray(size)
            }
            val take = minOf(bytes.size - at, connection.message.size - connection.messageBytes)
            bytes.copyInto(connection.message, connection.messageBytes, at, at + take)
            at += take
            connection.messageBytes += take
            if (connection.messageBytes == connection.message.size) {
                val message = connection.message
                connection.message = EMPTY
                connection.messageBytes = 0
                connection.headerBytes = 0
                connection.inFlight++
                query(connection.peer, message) { reply -> complete(key, connection, message, reply) }
            }
        }
    }

    @Synchronized
    private fun complete(key: Key, connection: Connection, request: ByteArray, reply: ByteArray?) {
        if (connections[key] !== connection) return
        connection.inFlight--
        val response = reply?.takeIf { it.size in DNS_HEADER..65_535 } ?: DnsPacket.errorReply(request, 2)
        connection.replies.addLast(ByteArray(response.size + 2).apply {
            this[0] = (response.size shr 8).toByte()
            this[1] = response.size.toByte()
            response.copyInto(this, 2)
        })
        connection.touched = clock()
        pump(connection)
    }

    private fun pump(c: Connection) {
        if (!c.established || c.pending != null || c.closing) return
        val nextReply = c.replies.firstOrNull()
        if (nextReply != null && c.window > 0) {
            val end = minOf(nextReply.size, c.replyOffset + minOf(c.window, c.peer.mss, MAX_SEGMENT))
            val data = nextReply.copyOfRange(c.replyOffset, end)
            c.replyOffset = end
            if (end == nextReply.size) {
                c.replies.removeFirst()
                c.replyOffset = 0
            }
            sendTracked(c, ACK or PSH, data)
        } else if (nextReply == null && c.inFlight == 0 && c.peerClosed) {
            c.closing = true
            sendTracked(c, ACK or FIN, EMPTY)
        }
    }

    @Synchronized
    fun tick() {
        val now = clock()
        val iterator = connections.values.iterator()
        while (iterator.hasNext()) {
            val c = iterator.next()
            val pending = c.pending
            if (now - c.touched >= IDLE_MS || pending != null && pending.retries >= MAX_RETRIES && now - pending.at >= RETRANSMIT_MS) {
                emit(c, c.next, RST or ACK, EMPTY)
                iterator.remove()
            } else if (pending != null && now - pending.at >= RETRANSMIT_MS) {
                pending.retries++
                pending.at = now
                emit(c, pending.seq, pending.flags, pending.data)
            }
        }
    }

    @Synchronized
    fun clear() = connections.clear()

    private fun reset(key: Key, c: Connection) {
        emit(c, c.next, RST or ACK, EMPTY)
        connections.remove(key)
    }

    private fun sendTracked(c: Connection, flags: Int, data: ByteArray) {
        val sent = Sent(c.next, flags, data, clock())
        c.pending = sent
        c.next = sent.end
        emit(c, sent.seq, flags, data)
    }

    private fun emit(c: Connection, sequence: Int, flags: Int, data: ByteArray) {
        send(DnsPacket.writeTcpReply(c.peer, sequence, c.receive, flags, data))
    }

    private companion object {
        const val FIN = 1
        const val SYN = 2
        const val RST = 4
        const val PSH = 8
        const val ACK = 16
        const val MAX_CONNECTIONS = 64
        const val MAX_QUERIES = 8
        const val MAX_SEGMENT = 1_200
        const val DNS_HEADER = 12
        const val RETRANSMIT_MS = 500L
        const val MAX_RETRIES = 6
        const val IDLE_MS = 30_000L
        val EMPTY = ByteArray(0)
    }
}
