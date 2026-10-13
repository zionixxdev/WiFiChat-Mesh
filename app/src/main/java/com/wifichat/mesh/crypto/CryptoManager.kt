package com.wifichat.mesh.crypto

/** An Ed25519 identity key pair. The private half never leaves the device. */
class IdentityKeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray
)

/** An ephemeral X25519 key pair used to derive a per-conversation session key. */
class SessionKeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray
)

/**
 * All cryptography used by the mesh goes through this interface so the
 * implementation can be swapped (and so the JVM unit tests can exercise the
 * real primitives without an Android device).
 *
 * Primitives (all from BouncyCastle, a reviewed, maintained library):
 *  - Ed25519   : packet signing / identity.
 *  - X25519    : ECDH session key agreement.
 *  - HKDF-SHA256 : key derivation.
 *  - AES-256-GCM : authenticated encryption of payloads.
 *
 * No algorithm here is home-made; the scheme is documented in docs/PROTOCOL.md.
 */
interface CryptoManager {

    fun generateIdentityKeyPair(): IdentityKeyPair

    fun generateSessionKeyPair(): SessionKeyPair

    /** Ed25519 signature over [data]. */
    fun sign(data: ByteArray, privateKey: ByteArray): ByteArray

    /** Verifies an Ed25519 [signature] over [data] with [publicKey]. */
    fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean

    /** X25519 ECDH -> HKDF-SHA256 -> 32-byte AES key. */
    fun deriveSessionKey(
        ownPrivate: ByteArray,
        peerPublic: ByteArray,
        salt: ByteArray,
        info: ByteArray
    ): ByteArray

    /**
     * AES-256-GCM encryption. Returns ciphertext WITH the 16-byte GCM tag
     * appended (the GCMBlockCipher convention).
     */
    fun encrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray

    /** AES-256-GCM decryption; throws on tag failure (tampered data). */
    fun decrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray

    /** Deterministic 60-digit safety number for out-of-band key verification. */
    fun safetyNumber(publicKeyA: ByteArray, publicKeyB: ByteArray): String
}
