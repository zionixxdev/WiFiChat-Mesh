package com.wifichat.mesh.mesh

import com.wifichat.mesh.model.MeshPacket
import com.wifichat.mesh.model.PacketType
import com.wifichat.mesh.util.Constants
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

class MalformedPacketException(message: String) : Exception(message)

/**
 * Deterministic binary codec for [MeshPacket].
 *
 * Layout (all integers big-endian):
 *   magic(4) | version(4) | type(1) | msgId(str) | sender(str) | recipient(str)
 *   | createdAt(8) | expiresAt(8) | hopCount(4) | maxHops(4)
 *   | nonce(bytes) | payload(bytes) | signature(bytes)
 *
 * A string is a UTF-8 length (4) followed by that many bytes; a byte array is
 * the same. The whole frame is length-prefixed by FrameCodec when it goes on
 * the wire, so decode() also enforces a hard size ceiling.
 *
 * [signableBytes] produces the canonical byte string the sender signs. It
 * covers every header field except hopCount (which relays legitimately mutate)
 * and the signature itself. This is what stops a relay from tampering with the
 * sender, recipient or expiry.
 */
object PacketCodec {

    private val MAGIC = byteArrayOf('W'.code.toByte(), 'C'.code.toByte(), 'M'.code.toByte(), 0x01)

    fun encode(packet: MeshPacket): ByteArray {
        val header = signableBytes(packet)
        val out = ByteArrayOutputStream(header.size + packet.signature.size + 8)
        val dos = DataOutputStream(out)
        dos.write(header)
        writeBytes(dos, packet.signature)
        dos.flush()
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): MeshPacket {
        if (bytes.size > Constants.MAX_PACKET_BYTES) {
            throw MalformedPacketException("frame exceeds max size")
        }
        val dis = DataInputStream(ByteArrayInputStream(bytes))
        try {
            val magic = ByteArray(4)
            dis.readFully(magic)
            if (!magic.contentEquals(MAGIC)) throw MalformedPacketException("bad magic")

            val version = dis.readInt()
            val typeCode = dis.readByte().toInt()
            val type = PacketType.fromCode(typeCode)
                ?: throw MalformedPacketException("unknown packet type $typeCode")

            val messageId = readString(dis)
            val senderId = readString(dis)
            val recipientId = readString(dis)
            val createdAt = dis.readLong()
            val expiresAt = dis.readLong()
            val hopCount = dis.readInt()
            val maxHops = dis.readInt()
            val nonce = readBytes(dis)
            val payload = readBytes(dis)
            val signature = readBytes(dis)

            if (hopCount < 0 || maxHops < 0) throw MalformedPacketException("negative hop field")
            if (messageId.isEmpty() || senderId.isEmpty() || recipientId.isEmpty()) {
                throw MalformedPacketException("missing identity field")
            }

            return MeshPacket(
                protocolVersion = version,
                type = type,
                messageId = messageId,
                senderId = senderId,
                recipientId = recipientId,
                createdAt = createdAt,
                expiresAt = expiresAt,
                hopCount = hopCount,
                maxHops = maxHops,
                nonce = nonce,
                payload = payload,
                signature = signature
            )
        } catch (e: EOFException) {
            throw MalformedPacketException("truncated packet")
        } catch (e: NegativeArraySizeException) {
            throw MalformedPacketException("negative length field")
        }
    }

    /** Canonical bytes covered by the signature (header + nonce + payload). */
    fun signableBytes(packet: MeshPacket): ByteArray {
        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)
        dos.write(MAGIC)
        dos.writeInt(packet.protocolVersion)
        dos.writeByte(packet.type.code)
        writeString(dos, packet.messageId)
        writeString(dos, packet.senderId)
        writeString(dos, packet.recipientId)
        dos.writeLong(packet.createdAt)
        dos.writeLong(packet.expiresAt)
        dos.writeInt(packet.maxHops)
        writeBytes(dos, packet.nonce)
        writeBytes(dos, packet.payload)
        dos.flush()
        return out.toByteArray()
    }

    // ---- helpers ----

    private fun writeString(dos: DataOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        dos.writeInt(b.size)
        dos.write(b)
    }

    private fun writeBytes(dos: DataOutputStream, b: ByteArray) {
        dos.writeInt(b.size)
        dos.write(b)
    }

    private fun readString(dis: DataInputStream): String {
        val len = dis.readInt()
        if (len < 0 || len > Constants.MAX_PACKET_BYTES) {
            throw MalformedPacketException("bad string length $len")
        }
        val b = ByteArray(len)
        dis.readFully(b)
        return String(b, Charsets.UTF_8)
    }

    private fun readBytes(dis: DataInputStream): ByteArray {
        val len = dis.readInt()
        if (len < 0 || len > Constants.MAX_PACKET_BYTES) {
            throw MalformedPacketException("bad array length $len")
        }
        val b = ByteArray(len)
        dis.readFully(b)
        return b
    }
}
