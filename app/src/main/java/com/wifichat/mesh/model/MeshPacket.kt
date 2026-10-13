package com.wifichat.mesh.model

/**
 * The wire format for everything the mesh moves. One [MeshPacket] is the unit
 * of store-and-forward routing. It is serialised by mesh/PacketCodec.
 *
 * Security model:
 *  - [payload] is ciphertext (AES-256-GCM) for the destination only.
 *  - [signature] is an Ed25519 signature over the canonical header + payload,
 *    made with the sender's identity key. Relay nodes verify it but cannot
 *    decrypt [payload].
 *  - [nonce] is the AES-GCM nonce.
 *
 * The header fields below (except hopCount) are covered by the signature, so a
 * relay cannot tamper with sender/recipient/expiry without detection.
 */
data class MeshPacket(
    val protocolVersion: Int,
    val type: PacketType,
    val messageId: String,        // UUID; used for duplicate suppression
    val senderId: String,
    val recipientId: String,      // peerId, or group id for GROUP messages
    val createdAt: Long,
    val expiresAt: Long,
    val hopCount: Int,
    val maxHops: Int,
    val nonce: ByteArray,
    val payload: ByteArray,       // ciphertext
    val signature: ByteArray      // Ed25519 over header+payload
) {
    /** A packet may be forwarded only while under the hop budget. */
    fun canForward(): Boolean = hopCount < maxHops

    /** A fresh copy with the hop counter incremented, for relaying. */
    fun relayed(): MeshPacket = copy(hopCount = hopCount + 1)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshPacket) return false
        return protocolVersion == other.protocolVersion &&
            type == other.type &&
            messageId == other.messageId &&
            senderId == other.senderId &&
            recipientId == other.recipientId &&
            createdAt == other.createdAt &&
            expiresAt == other.expiresAt &&
            hopCount == other.hopCount &&
            maxHops == other.maxHops &&
            nonce.contentEquals(other.nonce) &&
            payload.contentEquals(other.payload) &&
            signature.contentEquals(other.signature)
    }

    override fun hashCode(): Int {
        var result = protocolVersion
        result = 31 * result + type.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + senderId.hashCode()
        result = 31 * result + recipientId.hashCode()
        result = 31 * result + hopCount
        result = 31 * result + maxHops
        result = 31 * result + nonce.contentHashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
