package com.wifichat.mesh.crypto

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class CryptoManagerTest {

    private val crypto = BouncyCastleCryptoManager()

    @Test
    fun signAndVerifyRoundTrip() {
        val id = crypto.generateIdentityKeyPair()
        val data = "packet-bytes".toByteArray()
        val sig = crypto.sign(data, id.privateKey)
        assertThat(crypto.verify(data, sig, id.publicKey)).isTrue()
    }

    @Test
    fun verifyFailsOnTamperedData() {
        val id = crypto.generateIdentityKeyPair()
        val sig = crypto.sign("original".toByteArray(), id.privateKey)
        assertThat(crypto.verify("tampered".toByteArray(), sig, id.publicKey)).isFalse()
    }

    @Test
    fun verifyFailsWithWrongKey() {
        val a = crypto.generateIdentityKeyPair()
        val b = crypto.generateIdentityKeyPair()
        val sig = crypto.sign("data".toByteArray(), a.privateKey)
        assertThat(crypto.verify("data".toByteArray(), sig, b.publicKey)).isFalse()
    }

    @Test
    fun encryptDecryptRoundTrip() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12) { 7 }
        val aad = "header".toByteArray()
        val plaintext = "secret message".toByteArray()

        val ct = crypto.encrypt(plaintext, key, nonce, aad)
        assertThat(ct).isNotEqualTo(plaintext)
        val back = crypto.decrypt(ct, key, nonce, aad)
        assertThat(back).isEqualTo(plaintext)
    }

    @Test
    fun decryptRejectsTamperedCiphertext() {
        val key = ByteArray(32) { 1 }
        val nonce = ByteArray(12) { 2 }
        val aad = ByteArray(0)
        val ct = crypto.encrypt("data".toByteArray(), key, nonce, aad)
        ct[0] = (ct[0].toInt() xor 0xFF).toByte()

        // GCM tag failure must surface as an exception, not silent corruption.
        assertThrows(Exception::class.java) { crypto.decrypt(ct, key, nonce, aad) }
    }

    @Test
    fun decryptRejectsWrongAad() {
        val key = ByteArray(32) { 3 }
        val nonce = ByteArray(12) { 4 }
        val ct = crypto.encrypt("data".toByteArray(), key, nonce, "aad-a".toByteArray())
        assertThrows(Exception::class.java) {
            crypto.decrypt(ct, key, nonce, "aad-b".toByteArray())
        }
    }

    @Test
    fun ecdhDerivationIsSymmetric() {
        val a = crypto.generateSessionKeyPair()
        val b = crypto.generateSessionKeyPair()
        val salt = "msg".toByteArray()
        val info = "wifichat-mesh-v1".toByteArray()

        val keyA = crypto.deriveSessionKey(a.privateKey, b.publicKey, salt, info)
        val keyB = crypto.deriveSessionKey(b.privateKey, a.publicKey, salt, info)
        assertThat(keyA).isEqualTo(keyB)
        assertThat(keyA.size).isEqualTo(32)
    }

    @Test
    fun differentSaltProducesDifferentKey() {
        val a = crypto.generateSessionKeyPair()
        val b = crypto.generateSessionKeyPair()
        val info = "wifichat-mesh-v1".toByteArray()
        val k1 = crypto.deriveSessionKey(a.privateKey, b.publicKey, "m1".toByteArray(), info)
        val k2 = crypto.deriveSessionKey(a.privateKey, b.publicKey, "m2".toByteArray(), info)
        assertThat(k1).isNotEqualTo(k2)
    }

    @Test
    fun safetyNumberIsDeterministicAndOrderIndependent() {
        val a = crypto.generateIdentityKeyPair().publicKey
        val b = crypto.generateIdentityKeyPair().publicKey
        assertThat(crypto.safetyNumber(a, b)).isEqualTo(crypto.safetyNumber(b, a))
        assertThat(crypto.safetyNumber(a, b)).isEqualTo(crypto.safetyNumber(a, b))
    }
}
