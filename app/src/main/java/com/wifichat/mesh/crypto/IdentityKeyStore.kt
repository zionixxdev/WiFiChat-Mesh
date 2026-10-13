package com.wifichat.mesh.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.wifichat.mesh.util.Constants
import com.wifichat.mesh.util.MeshLog
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the long-lived identity key pair, wrapped by a hardware-backed AES key
 * that lives in the Android Keystore and cannot be exported.
 *
 * Flow:
 *   masterKey (Keystore, non-exportable)
 *     └─ AES-GCM wraps -> identity private key (Ed25519)
 *     └─ AES-GCM wraps -> X25519 static private key
 *
 * Public keys are stored in the clear (they are public). If the Keystore is
 * unavailable, [isHardwareBacked] reports false and the caller should surface
 * that to the user in Settings.
 */
class IdentityKeyStore(
    context: Context,
    private val crypto: CryptoManager
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun masterKey(): SecretKey {
        val existing = keyStore.getEntry(MASTER_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        gen.init(spec)
        return gen.generateKey()
    }

    private fun wrap(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain)
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun unwrap(blob: String): ByteArray? {
        return try {
            val (ivB64, ctB64) = blob.split(":")
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val ct = Base64.decode(ctB64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ct)
        } catch (t: Throwable) {
            MeshLog.e("Failed to unwrap key material", t)
            null
        }
    }

    fun hasIdentity(): Boolean = prefs.contains(KEY_IDENTITY_PRIVATE)

    /** Creates and persists a fresh identity if none exists. Idempotent. */
    fun ensureIdentity(displayName: String): StoredIdentity {
        if (hasIdentity()) return loadIdentity()!!
        val pair = crypto.generateIdentityKeyPair()
        val session = crypto.generateSessionKeyPair()
        prefs.edit()
            .putString(KEY_IDENTITY_PRIVATE, wrap(pair.privateKey))
            .putString(KEY_IDENTITY_PUBLIC, Base64.encodeToString(pair.publicKey, Base64.NO_WRAP))
            .putString(KEY_SESSION_PRIVATE, wrap(session.privateKey))
            .putString(KEY_SESSION_PUBLIC, Base64.encodeToString(session.publicKey, Base64.NO_WRAP))
            .apply()
        return StoredIdentity(
            identityPrivate = pair.privateKey,
            identityPublic = pair.publicKey,
            sessionPrivate = session.privateKey,
            sessionPublic = session.publicKey
        )
    }

    fun loadIdentity(): StoredIdentity? {
        val ip = prefs.getString(KEY_IDENTITY_PRIVATE, null) ?: return null
        val ipub = prefs.getString(KEY_IDENTITY_PUBLIC, null) ?: return null
        val sp = prefs.getString(KEY_SESSION_PRIVATE, null) ?: return null
        val spub = prefs.getString(KEY_SESSION_PUBLIC, null) ?: return null
        return StoredIdentity(
            identityPrivate = unwrap(ip) ?: return null,
            identityPublic = Base64.decode(ipub, Base64.NO_WRAP),
            sessionPrivate = unwrap(sp) ?: return null,
            sessionPublic = Base64.decode(spub, Base64.NO_WRAP)
        )
    }

    /** Irreversibly deletes all key material (used by "Clear local data"). */
    fun wipe() {
        prefs.edit().clear().apply()
        try {
            if (keyStore.containsAlias(MASTER_ALIAS)) keyStore.deleteEntry(MASTER_ALIAS)
        } catch (t: Throwable) {
            MeshLog.w("Keystore wipe failed: ${t.message}")
        }
    }

    val isHardwareBacked: Boolean
        get() = try {
            val entry = keyStore.getEntry(MASTER_ALIAS, null) as? KeyStore.SecretKeyEntry
            entry != null
        } catch (t: Throwable) {
            false
        }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_ALIAS = "wifichat_master_key"
        private const val KEY_IDENTITY_PRIVATE = "identity_private"
        private const val KEY_IDENTITY_PUBLIC = "identity_public"
        private const val KEY_SESSION_PRIVATE = "session_private"
        private const val KEY_SESSION_PUBLIC = "session_public"
    }
}

/** Decrypted key material handed to the mesh at runtime. */
class StoredIdentity(
    val identityPrivate: ByteArray,
    val identityPublic: ByteArray,
    val sessionPrivate: ByteArray,
    val sessionPublic: ByteArray
)
