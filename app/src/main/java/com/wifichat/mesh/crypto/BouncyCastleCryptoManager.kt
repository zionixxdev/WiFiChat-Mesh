package com.wifichat.mesh.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/**
 * BouncyCastle-backed implementation. Pure Java, so it adds no native
 * libraries and keeps the APK architecture-neutral.
 *
 * Note: this implementation is deliberately conservative and unoptimised; it
 * is not claimed to be audited. See docs/KNOWN_LIMITATIONS.md.
 */
class BouncyCastleCryptoManager : CryptoManager {

    private val random = SecureRandom()
    private val gcmTagBits = 128

    override fun generateIdentityKeyPair(): IdentityKeyPair {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(random))
        val pair = gen.generateKeyPair()
        val priv = (pair.private as Ed25519PrivateKeyParameters).encoded
        val pub = (pair.public as Ed25519PublicKeyParameters).encoded
        return IdentityKeyPair(priv, pub)
    }

    override fun generateSessionKeyPair(): SessionKeyPair {
        val gen = X25519KeyPairGenerator()
        gen.init(X25519KeyGenerationParameters(random))
        val pair = gen.generateKeyPair()
        val priv = (pair.private as X25519PrivateKeyParameters).encoded
        val pub = (pair.public as X25519PublicKeyParameters).encoded
        return SessionKeyPair(priv, pub)
    }

    override fun sign(data: ByteArray, privateKey: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    override fun verify(data: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean {
        return try {
            val signer = Ed25519Signer()
            signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
            signer.update(data, 0, data.size)
            signer.verifySignature(signature)
        } catch (t: Throwable) {
            false
        }
    }

    override fun deriveSessionKey(
        ownPrivate: ByteArray,
        peerPublic: ByteArray,
        salt: ByteArray,
        info: ByteArray
    ): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(ownPrivate, 0))
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPublic, 0), shared, 0)

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(shared, salt, info))
        val out = ByteArray(32)
        hkdf.generateBytes(out, 0, out.size)
        shared.fill(0) // wipe the raw ECDH secret
        return out
    }

    override fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cipher = GCMBlockCipher(AESEngine.newInstance())
        cipher.init(true, AEADParameters(KeyParameter(key), gcmTagBits, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val len = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, len)
        return out
    }

    override fun decrypt(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cipher = GCMBlockCipher(AESEngine.newInstance())
        cipher.init(false, AEADParameters(KeyParameter(key), gcmTagBits, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        val len = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
        cipher.doFinal(out, len) // throws InvalidCipherTextException on tamper
        return out
    }

    override fun safetyNumber(publicKeyA: ByteArray, publicKeyB: ByteArray): String {
        // Order-independent concatenation so both sides compute the same value.
        val (a, b) = if (compare(publicKeyA, publicKeyB) <= 0) {
            publicKeyA to publicKeyB
        } else {
            publicKeyB to publicKeyA
        }
        val digest = SHA256Digest()
        digest.update(a, 0, a.size)
        digest.update(b, 0, b.size)
        val out = ByteArray(32)
        digest.doFinal(out, 0)

        // Render the first 25 bytes as 60 decimal digits in 12 groups of 5.
        val sb = StringBuilder()
        var i = 0
        while (i < 25) {
            val value = ((out[i].toInt() and 0xFF).toLong() shl 16) or
                ((out[i + 1].toInt() and 0xFF).toLong() shl 8) or
                (out[i + 2].toInt() and 0xFF).toLong()
            sb.append(String.format("%05d", value % 100000))
            if ((i / 3 + 1) % 4 == 0) sb.append(' ')
            i += 3
        }
        return sb.toString().trim()
    }

    private fun compare(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a[i].toInt() and 0xFF
            val y = b[i].toInt() and 0xFF
            if (x != y) return x - y
        }
        return a.size - b.size
    }
}
