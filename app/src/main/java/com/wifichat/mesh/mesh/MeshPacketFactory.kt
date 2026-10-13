package com.wifichat.mesh.mesh

import com.wifichat.mesh.crypto.CryptoManager
import com.wifichat.mesh.crypto.StoredIdentity
import com.wifichat.mesh.model.MeshPacket
import com.wifichat.mesh.model.PacketType
import com.wifichat.mesh.util.Constants
import com.wifichat.mesh.util.Ids
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Builds and seals outgoing packets. Encryption scheme (see docs/PROTOCOL.md):
 *
 *   sessionKey = HKDF-SHA256( X25519(ourStaticPriv, peerStaticPub),
 *                             salt = messageId,
 *                             info = "wifichat-mesh-v1" )
 *   aad        = canonical header (version,type,msgId,sender,recipient,created,expires,maxHops)
 *   payload    = AES-256-GCM(plaintext, sessionKey, nonce, aad)
 *   signature  = Ed25519( signableBytes(header + nonce + payload) )
 *
 * Using the messageId as the HKDF salt gives every message a distinct key while
 * still deriving from one long-lived X25519 pair, so no extra round trip is
 * needed to start a conversation.
 */
class MeshPacketFactory(
    private val crypto: CryptoManager,
    private val identity: StoredIdentity,
    private val myPeerId: String
) {

    fun announce(displayName: String, ttlMs: Long): MeshPacket {
        val now = System.currentTimeMillis()
        val body = ByteArrayOutputStream().also { bos ->
            val dos = DataOutputStream(bos)
            dos.writeInt(Constants.PROTOCOL_VERSION)
            writeString(dos, displayName)
            writeBytes(dos, identity.identityPublic)   // Ed25519 identity key
            writeBytes(dos, identity.sessionPublic)    // X25519 static key
            dos.flush()
        }.toByteArray()
        return seal(
            type = PacketType.ANNOUNCE,
            messageId = Ids.newMessageId(),
            recipientId = BROADCAST,
            plaintext = body,
            recipientX25519 = null, // announce is broadcast in clear (no session key yet)
            ttlMs = ttlMs,
            createdAt = now
        )
    }

    fun data(
        recipientId: String,
        recipientX25519: ByteArray,
        plaintext: ByteArray,
        ttlMs: Long,
        replyToId: String? = null,
        messageId: String = Ids.newMessageId()
    ): MeshPacket {
        val now = System.currentTimeMillis()
        val withReply = if (replyToId == null) plaintext else {
            ByteArrayOutputStream().also { bos ->
                val dos = DataOutputStream(bos)
                dos.writeInt(1) // reply present flag
                val r = replyToId.toByteArray(Charsets.UTF_8)
                dos.writeInt(r.size); dos.write(r)
                dos.writeInt(plaintext.size); dos.write(plaintext)
                dos.flush()
            }.toByteArray()
        }
        return seal(
            type = PacketType.DATA,
            messageId = messageId,
            recipientId = recipientId,
            plaintext = withReply,
            recipientX25519 = recipientX25519,
            ttlMs = ttlMs,
            createdAt = now
        )
    }

    /** A small control packet (ACK_DELIVERED / ACK_READ). Payload is empty. */
    fun ack(type: PacketType, recipientId: String, recipientX25519: ByteArray, forMessageId: String): MeshPacket {
        val now = System.currentTimeMillis()
        return seal(
            type = type,
            messageId = Ids.newMessageId(),
            recipientId = recipientId,
            plaintext = forMessageId.toByteArray(Charsets.UTF_8),
            recipientX25519 = recipientX25519,
            ttlMs = Constants.DEFAULT_MESSAGE_TTL_MS,
            createdAt = now
        )
    }

    private fun seal(
        type: PacketType,
        messageId: String,
        recipientId: String,
        plaintext: ByteArray,
        recipientX25519: ByteArray?,
        ttlMs: Long,
        createdAt: Long
    ): MeshPacket {
        val expiresAt = if (ttlMs > 0) createdAt + ttlMs else 0L
        val maxHops = Constants.DEFAULT_MAX_HOPS
        val nonce = Ids.randomBytes(NONCE_LEN)

        val aad = headerAad(type, messageId, myPeerId, recipientId, createdAt, expiresAt, maxHops)

        val payload: ByteArray = if (recipientX25519 == null) {
            // Broadcast announce: no confidentiality needed (name + public key).
            plaintext
        } else {
            val key = crypto.deriveSessionKey(
                identity.sessionPrivate, recipientX25519, messageId.toByteArray(Charsets.UTF_8), HKDF_INFO
            )
            crypto.encrypt(plaintext, key, nonce, aad)
        }

        val unsigned = MeshPacket(
            protocolVersion = Constants.PROTOCOL_VERSION,
            type = type,
            messageId = messageId,
            senderId = myPeerId,
            recipientId = recipientId,
            createdAt = createdAt,
            expiresAt = expiresAt,
            hopCount = 0,
            maxHops = maxHops,
            nonce = nonce,
            payload = payload,
            signature = ByteArray(0)
        )
        val sig = crypto.sign(PacketCodec.signableBytes(unsigned), identity.identityPrivate)
        return unsigned.copy(signature = sig)
    }

    companion object {
        const val BROADCAST = "*"
        const val NONCE_LEN = 12
        val HKDF_INFO = "wifichat-mesh-v1".toByteArray(Charsets.UTF_8)

        /** Canonical header bytes used as AES-GCM associated data. */
        fun headerAad(
            type: PacketType,
            messageId: String,
            senderId: String,
            recipientId: String,
            createdAt: Long,
            expiresAt: Long,
            maxHops: Int
        ): ByteArray {
            val bos = ByteArrayOutputStream()
            val dos = DataOutputStream(bos)
            dos.writeInt(Constants.PROTOCOL_VERSION)
            dos.writeByte(type.code)
            for (s in listOf(messageId, senderId, recipientId)) {
                val b = s.toByteArray(Charsets.UTF_8)
                dos.writeInt(b.size); dos.write(b)
            }
            dos.writeLong(createdAt)
            dos.writeLong(expiresAt)
            dos.writeInt(maxHops)
            dos.flush()
            return bos.toByteArray()
        }

        /** Parsed contents of an ANNOUNCE payload. */
        class AnnounceInfo(
            val displayName: String,
            val identityPublicKey: ByteArray,
            val sessionPublicKey: ByteArray
        )

        /** Parses an ANNOUNCE payload, or null if it is malformed. */
        fun parseAnnounce(payload: ByteArray): AnnounceInfo? {
            return try {
                val dis = DataInputStream(ByteArrayInputStream(payload))
                dis.readInt() // protocol version
                val name = readString(dis)
                val ed = readBytes(dis)
                val x = readBytes(dis)
                if (ed.size != 32 || x.size != 32) null else AnnounceInfo(name, ed, x)
            } catch (t: Throwable) {
                null
            }
        }

        private fun writeString(dos: DataOutputStream, s: String) {
            val b = s.toByteArray(Charsets.UTF_8)
            dos.writeInt(b.size); dos.write(b)
        }

        private fun writeBytes(dos: DataOutputStream, b: ByteArray) {
            dos.writeInt(b.size); dos.write(b)
        }

        private fun readString(dis: DataInputStream): String {
            val n = dis.readInt()
            if (n < 0 || n > 4096) throw IllegalArgumentException("bad name length")
            val b = ByteArray(n); dis.readFully(b)
            return String(b, Charsets.UTF_8)
        }

        private fun readBytes(dis: DataInputStream): ByteArray {
            val n = dis.readInt()
            if (n < 0 || n > 4096) throw IllegalArgumentException("bad key length")
            val b = ByteArray(n); dis.readFully(b)
            return b
        }
    }
}
