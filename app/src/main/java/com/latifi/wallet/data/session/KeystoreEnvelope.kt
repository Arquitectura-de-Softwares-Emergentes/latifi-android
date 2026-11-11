package com.latifi.wallet.data.session

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals and opens small byte blobs. [open] throws [GeneralSecurityException] for a blob that was
 * changed, cut short or sealed with another key. Nothing that goes through here is a private key:
 * the app holds only the address and the short lived token of the person.
 */
interface Envelope {
    fun seal(plain: ByteArray): ByteArray

    fun open(sealed: ByteArray): ByteArray

    /** Forgets the key. Whatever was sealed with it can never be opened again. */
    fun reset()
}

/**
 * AES-256-GCM with a key that lives in the Android Keystore and cannot be exported (`encoded` is
 * null). The layout of a sealed blob is the 12 byte random IV, then the ciphertext and the tag. The
 * pattern comes from the retired Keystore spike, reused for the session only (ADR-0001, envelope
 * part). `androidx.security:security-crypto` is deprecated and deliberately not used.
 *
 * A key the platform lost or invalidated is replaced on [seal]. On [open] the key entry is deleted
 * after a failure, so the caller treats the person as signed out and the next save starts clean.
 */
class KeystoreEnvelope(private val alias: String = ALIAS) : Envelope {
    // One lock for seal, open and reset: two threads that both find no key would otherwise make two
    // keys with the same alias, and the second one would invalidate what the first one sealed.
    private val lock = Any()

    override fun seal(plain: ByteArray): ByteArray = synchronized(lock) {
        try {
            sealWith(key(), plain)
        } catch (e: GeneralSecurityException) {
            // The stored key is unusable, for instance after a lock screen reset. Start over once.
            reset()
            sealWith(key(), plain)
        } catch (e: ProviderException) {
            // An unstable Keystore reports a failed key or cipher setup this way. One fresh try.
            reset()
            sealWith(key(), plain)
        }
    }

    private fun sealWith(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected IV length" }
        return iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray = synchronized(lock) {
        try {
            if (sealed.size <= IV_BYTES + TAG_BYTES) throw GeneralSecurityException("The sealed blob is too short")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(TAG_BITS, sealed.copyOfRange(0, IV_BYTES)))
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (e: GeneralSecurityException) {
            reset()
            throw e
        }
    }

    override fun reset() {
        synchronized(lock) {
            try {
                keyStore().deleteEntry(alias)
            } catch (e: GeneralSecurityException) {
                Log.w(TAG, "Session key not deleted: ${e.javaClass.simpleName}")
            } catch (e: IOException) {
                Log.w(TAG, "Session key not deleted: ${e.javaClass.simpleName}")
            } catch (e: ProviderException) {
                Log.w(TAG, "Session key not deleted: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey =
        keyStore().getKey(alias, null) as? SecretKey ?: throw GeneralSecurityException("No session key")

    private fun key(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        val created = generator.generateKey()
        logSecurityLevel(created)
        return created
    }

    /** Logs the number of the security level only (0 software, 1 trusted environment, 2 strongbox). */
    private fun logSecurityLevel(key: SecretKey) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        try {
            val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
            Log.i(TAG, "Session key security level ${info.securityLevel}")
        } catch (e: GeneralSecurityException) {
            Log.i(TAG, "Session key security level unknown: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        const val ALIAS = "latifi_session_wrap"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val KEY_BITS = 256
        private const val TAG = "KeystoreEnvelope"
    }
}
