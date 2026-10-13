package com.wifichat.mesh.mesh

import com.google.common.truth.Truth.assertThat
import com.wifichat.mesh.model.MeshPacket
import com.wifichat.mesh.model.PacketType
import org.junit.Assert.assertThrows
import org.junit.Test

class PacketCodecTest {

    private fun packet(hop: Int = 0, max: Int = 5) = MeshPacket(
        protocolVersion = 1,
        type = PacketType.DATA,
        messageId = "msg-1",
        senderId = "sender-1",
        recipientId = "recipient-1",
        createdAt = 1000L,
        expiresAt = 2000L,
        hopCount = hop,
        maxHops = max,
        nonce = ByteArray(12) { 1 },
        payload = "hello".toByteArray(),
        signature = ByteArray(64) { 2 }
    )

    @Test
    fun roundTripPreservesAllFields() {
        val original = packet()
        val decoded = PacketCodec.decode(PacketCodec.encode(original))
        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun signableBytesIgnoresHopCount() {
        // A relay bumps hopCount; the signed bytes must not change, or every
        // relay would invalidate the sender's signature.
        val a = PacketCodec.signableBytes(packet(hop = 0))
        val b = PacketCodec.signableBytes(packet(hop = 4))
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun rejectsBadMagic() {
        val bytes = PacketCodec.encode(packet())
        bytes[0] = 'X'.code.toByte()
        assertThrows(MalformedPacketException::class.java) { PacketCodec.decode(bytes) }
    }

    @Test
    fun rejectsTruncatedPacket() {
        val bytes = PacketCodec.encode(packet())
        val truncated = bytes.copyOfRange(0, bytes.size - 10)
        assertThrows(MalformedPacketException::class.java) { PacketCodec.decode(truncated) }
    }

    @Test
    fun rejectsNegativeLengthField() {
        val bytes = PacketCodec.encode(packet())
        // Corrupt the first length field (string length for messageId) to a
        // negative value: header is 4 magic + 4 version + 1 type = offset 9.
        bytes[9] = 0xFF.toByte()
        bytes[10] = 0xFF.toByte()
        bytes[11] = 0xFF.toByte()
        bytes[12] = 0xFF.toByte()
        assertThrows(MalformedPacketException::class.java) { PacketCodec.decode(bytes) }
    }

    @Test
    fun rejectsUnknownPacketType() {
        val bytes = PacketCodec.encode(packet())
        bytes[8] = 0x7F // unknown type code
        assertThrows(MalformedPacketException::class.java) { PacketCodec.decode(bytes) }
    }
}
