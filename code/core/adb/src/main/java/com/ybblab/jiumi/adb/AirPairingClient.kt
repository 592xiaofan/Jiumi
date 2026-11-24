// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.adb

import android.net.ssl.SSLSockets
import moe.shizuku.manager.adb.PairingContext
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

/**
 * Implements the ADB wireless pairing protocol:
 * TLS handshake → TLS keying material export → SPAKE2+ exchange → PeerInfo exchange
 */
class AirPairingClient(
    private val host: String,
    private val port: Int,
    private val pairCode: String,
    private val adbKey: AirAdbKey
) : Closeable {

    companion object {
        private const val SPAKE2_MSG: Byte = 0
        private const val PEER_INFO: Byte = 1
        private const val HEADER_VERSION: Byte = 1
        private const val PEER_INFO_SIZE = 8192
        private const val PEER_DATA_SIZE = 8191
        private const val ADB_RSA_PUB_KEY: Byte = 0
        private const val KEYING_MATERIAL_LABEL = "adb-label\u0000"
        private const val KEYING_MATERIAL_LENGTH = 64
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val READ_TIMEOUT_MS = 8_000
    }

    private lateinit var inputStream: DataInputStream
    private lateinit var outputStream: DataOutputStream
    private val socket = Socket()
    private var pairingContext: PairingContext? = null

    override fun close() = socket.close()

    fun pair(): Boolean = use {
        val sslSocket = try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            adbKey.sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
        } catch (error: IOException) {
            throw PairingEndpointException(error)
        }
        sslSocket.use { tls ->
            try {
                tls.soTimeout = READ_TIMEOUT_MS
                tls.startHandshake()
            } catch (error: IOException) {
                throw PairingEndpointException(error)
            }
            try {
                setupTlsConnection(tls)
                doExchangeMsgs() && doExchangePeerInfo()
            } finally {
                pairingContext?.destroy()
            }
        }
    }

    private fun setupTlsConnection(sslSocket: SSLSocket) {
        inputStream = DataInputStream(sslSocket.inputStream)
        outputStream = DataOutputStream(sslSocket.outputStream)

        val codeBytes = pairCode.toByteArray(Charsets.UTF_8)
        val keyingMaterial = exportKeyingMaterial(sslSocket)
        val password = codeBytes + keyingMaterial

        pairingContext = PairingContext.create(password)
            ?: throw IllegalStateException("Failed to create SPAKE2+ PairingContext")
    }

    private fun doExchangeMsgs(): Boolean {
        val ctx = pairingContext ?: return false
        writePacket(SPAKE2_MSG, ctx.msg)
        val (type, payload) = readPacket() ?: return false
        if (type != SPAKE2_MSG) return false
        return ctx.initCipher(payload)
    }

    private fun doExchangePeerInfo(): Boolean {
        val ctx = pairingContext ?: return false

        val peerInfoBuf = ByteBuffer.allocate(PEER_INFO_SIZE).order(ByteOrder.BIG_ENDIAN)
        peerInfoBuf.put(ADB_RSA_PUB_KEY)
        val dataBuf = ByteArray(PEER_DATA_SIZE)
        val copyLen = minOf(adbKey.publicKeyBytes.size, PEER_DATA_SIZE)
        System.arraycopy(adbKey.publicKeyBytes, 0, dataBuf, 0, copyLen)
        peerInfoBuf.put(dataBuf)

        val encrypted = ctx.encrypt(peerInfoBuf.array()) ?: return false
        writePacket(PEER_INFO, encrypted)

        val (type, payload) = readPacket() ?: return false
        if (type != PEER_INFO) return false
        val decrypted = ctx.decrypt(payload) ?: return false
        return decrypted.size == PEER_INFO_SIZE
    }

    private fun writePacket(type: Byte, payload: ByteArray) {
        val header = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN)
        header.put(HEADER_VERSION)
        header.put(type)
        header.putInt(payload.size)
        outputStream.write(header.array())
        outputStream.write(payload)
        outputStream.flush()
    }

    private fun readPacket(): Pair<Byte, ByteArray>? {
        val headerBuf = ByteArray(6)
        inputStream.readFully(headerBuf)
        val buf = ByteBuffer.wrap(headerBuf).order(ByteOrder.BIG_ENDIAN)
        val version = buf.get()
        val type = buf.get()
        val size = buf.getInt()
        if (version != HEADER_VERSION || size <= 0 || size > 16384) return null
        val payload = ByteArray(size)
        inputStream.readFully(payload)
        return Pair(type, payload)
    }

    @Throws(IOException::class)
    private fun exportKeyingMaterial(socket: SSLSocket): ByteArray {
        return SSLSockets.exportKeyingMaterial(
            socket,
            KEYING_MATERIAL_LABEL,
            null,
            KEYING_MATERIAL_LENGTH
        ) ?: throw IOException("SSLSockets.exportKeyingMaterial returned null")
    }
}

internal class PairingEndpointException(cause: IOException) : IOException("Pairing endpoint unavailable", cause)
