package com.wifichat.mesh.model

/**
 * A locally generated, persistent identity. It is NOT derived from the
 * Bluetooth MAC address or any hardware identifier.
 *
 * [peerId] is a random UUID created on first launch. [publicKey] is the
 * Ed25519 identity public key (32 bytes) used to sign packets; the matching
 * private key never leaves the device (see crypto/IdentityKeyStore).
 */
data class DeviceIdentity(
    val peerId: String,
    val displayName: String,
    val publicKey: ByteArray,
    val createdAt: Long
) {
    // ByteArray in a data class needs explicit equals/hashCode to behave.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceIdentity) return false
        return peerId == other.peerId &&
            displayName == other.displayName &&
            publicKey.contentEquals(other.publicKey) &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = peerId.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + publicKey.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
