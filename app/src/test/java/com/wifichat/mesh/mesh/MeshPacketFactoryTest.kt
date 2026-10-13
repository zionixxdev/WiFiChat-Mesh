package com.wifichat.mesh.mesh

import com.google.common.truth.Truth.assertThat
import com.wifichat.mesh.crypto.BouncyCastleCryptoManager
import com.wifichat.mesh.crypto.StoredIdentity
import org.junit.Test

/**
 * End-to-end crypto handshake at the packet level: A seals a message for B,
 * B verifies the sender's signature and decrypts it. No device required.
 */
class MeshPacketFactoryTest {

    private val crypto = BouncyCastleCryptoManager()

    private fun identity(): StoredIdentity {
        val id = crypto.generateIdentityKeyPair()
        val sess = crypto.generateSessionKeyPair()
        return StoredIdentity(id.privateKey, id.publicKey, sess.privateKey, sess.publicKey)
    }

    @Test
    fun dataPacketVerifiesAndDecrypts() {
        val a = identity()
        val b = identity()
        val factory = MeshPacketFactory(crypto, a, "peerA")

        val packet = factory.data(
            recipientId = "peerB",
            recipientX25519 = b.sessionPublic,
            plaintext = "hello B".toByteArray(),
            ttlMs = 60_000
        )

        // Sender signature is valid and covers the header + payload.
        assertThat(crypto.verify(PacketCodec.signableBytes(packet), packet.signature, a.identityPublic))
            .isTrue()

        // B can reconstruct the same session key and decrypt.
        val aad = MeshPacketFactory.headerAad(
            packet.type, packet.messageId, packet.senderId, packet.recipientId,
            packet.createdAt, packet.expiresAt, packet.maxHops
        )
        val key = crypto.deriveSessionKey(
            b.sessionPrivate, a.sessionPublic,
            packet.messageId.toByteArray(), MeshPacketFactory.HKDF_INFO
        )
        val plaintext = crypto.decrypt(packet.payload, key, packet.nonce, aad)
        assertThat(String(plaintext)).isEqualTo("hello B")
    }

    @Test
    fun announceCarriesIdentityAndSessionKeys() {
        val a = identity()
        val factory = MeshPacketFactory(crypto, a, "peerA")
        val packet = factory.announce("Alice", 60_000)

        val info = MeshPacketFactory.parseAnnounce(packet.payload)
        assertThat(info).isNotNull()
        assertThat(info!!.displayName).isEqualTo("Alice")
        assertThat(info.identityPublicKey).isEqualTo(a.identityPublic)
        assertThat(info.sessionPublicKey).isEqualTo(a.sessionPublic)

        assertThat(crypto.verify(PacketCodec.signableBytes(packet), packet.signature, a.identityPublic))
            .isTrue()
    }

    @Test
    fun encodeDecodeRoundTripThroughCodec() {
        val a = identity()
        val b = identity()
        val factory = MeshPacketFactory(crypto, a, "peerA")
        val packet = factory.data("peerB", b.sessionPublic, "x".toByteArray(), 1000)

        val decoded = PacketCodec.decode(PacketCodec.encode(packet))
        assertThat(decoded.messageId).isEqualTo(packet.messageId)
        assertThat(decoded.payload).isEqualTo(packet.payload)
        assertThat(decoded.signature).isEqualTo(packet.signature)
    }
}
