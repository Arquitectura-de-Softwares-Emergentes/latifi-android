package com.latifi.wallet.spike

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Security
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric

/**
 * Spike for ROADMAP Decision 1: where does a secp256k1 wallet key live on Android?
 *
 * Q1 asks whether AndroidKeyStore can hold a secp256k1 key directly.
 * Q2 proves the envelope design: an AES-256-GCM key that never leaves the Keystore
 * wraps the web3j secp256k1 private key, and the unwrapped key still signs.
 * Q3 checks that no plaintext private key lands in app storage.
 *
 * The keys are throwaway values created on a throwaway emulator. Nothing in this
 * class passes key material to a logging call; the only log lines carry exception
 * class names, exception messages and Keystore security-level flags.
 */
class KeystoreEnvelopeSpikeTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val envelopeFile: File
        get() = File(context.filesDir, ENVELOPE_FILE_NAME)

    @Before
    fun setUp() {
        // Android ships a stripped provider named "BC". web3j resolves "BC" by name, so
        // the bundled BouncyCastle must replace the platform one before web3j runs.
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        deleteAesKeyIfPresent()
        envelopeFile.delete()
    }

    @After
    fun tearDown() {
        deleteAesKeyIfPresent()
        envelopeFile.delete()
    }

    @Test
    fun q1DirectSecp256k1IsRejectedByKeystore() {
        var failure: Exception? = null
        try {
            val generator =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            generator.initialize(
                KeyGenParameterSpec.Builder(EC_ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256k1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            generator.generateKeyPair()
        } catch (e: Exception) {
            failure = e
        } finally {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(EC_ALIAS)
        }

        val rejected = failure
        assertNotNull("AndroidKeyStore unexpectedly generated a secp256k1 key", rejected)
        Log.i(TAG, "Q1 exception class: ${rejected!!.javaClass.name}")
        Log.i(TAG, "Q1 exception message: ${rejected.message}")
        assertTrue(
            "Unexpected failure type ${rejected.javaClass.name}",
            rejected is GeneralSecurityException || rejected is java.security.ProviderException,
        )
    }

    @Test
    fun q2EnvelopeRoundTripSignsWithRecoveredKey() {
        val aesKey = createAesKeyInKeystore()

        // The wrapping key is not exportable: the platform returns no key bytes.
        assertNull("AES key must not be exportable", aesKey.encoded)
        val keyInfo =
            SecretKeyFactory.getInstance(aesKey.algorithm, ANDROID_KEYSTORE)
                .getKeySpec(aesKey, KeyInfo::class.java) as KeyInfo
        assertEquals(256, keyInfo.keySize)
        Log.i(TAG, "Q2 AES key insideSecureHardware=${keyInfo.isInsideSecureHardware}")
        Log.i(TAG, "Q2 AES key securityLevel=${keyInfo.securityLevel}")

        val original = Keys.createEcKeyPair()
        val expectedAddress = Keys.getAddress(original.publicKey)
        val privateKeyBytes = Numeric.toBytesPadded(original.privateKey, KEY_LENGTH)

        wrapToFile(aesKey, privateKeyBytes)
        privateKeyBytes.fill(0)

        // Only the wrapped file and the Keystore alias remain; decrypt and sign.
        val recoveredBytes = unwrapFromFile()
        val recovered = ECKeyPair.create(recoveredBytes)
        recoveredBytes.fill(0)

        val message = "LatiFi spike message".toByteArray(Charsets.UTF_8)
        val signature = Sign.signPrefixedMessage(message, recovered)
        val recoveredPublicKey = Sign.signedPrefixedMessageToKey(message, signature)

        assertEquals(expectedAddress, Keys.getAddress(recoveredPublicKey))
        assertEquals(original.publicKey, recovered.publicKey)
    }

    @Test
    fun q3NoPlaintextKeyInAppStorage() {
        val aesKey = createAesKeyInKeystore()
        val original = Keys.createEcKeyPair()
        val privateKeyBytes = Numeric.toBytesPadded(original.privateKey, KEY_LENGTH)
        val privateKeyHex = Numeric.toHexStringNoPrefixZeroPadded(original.privateKey, KEY_LENGTH * 2)

        wrapToFile(aesKey, privateKeyBytes)
        assertTrue("Wrapped file must exist", envelopeFile.exists())

        val roots =
            listOf(context.filesDir, context.cacheDir, File(context.dataDir, "databases"))
        val files = roots.filter { it.exists() }.flatMap { root -> root.walkTopDown().filter { it.isFile }.toList() }
        assertTrue("The scan must cover the wrapped file", files.contains(envelopeFile))

        for (file in files) {
            val content = file.readBytes()
            assertFalse(
                "Raw private key bytes found in ${file.name}",
                containsSequence(content, privateKeyBytes),
            )
            val text = String(content, Charsets.ISO_8859_1)
            assertFalse(
                "Hex private key found in ${file.name}",
                text.contains(privateKeyHex, ignoreCase = true),
            )
        }

        // The wrapped bytes still decrypt to the same key.
        assertArrayEquals(privateKeyBytes, unwrapFromFile())
    }

    private fun createAesKeyInKeystore(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                    AES_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun loadAesKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return store.getKey(AES_ALIAS, null) as SecretKey
    }

    private fun wrapToFile(aesKey: SecretKey, plaintext: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = cipher.iv
        assertEquals("GCM nonce must be 12 bytes", IV_LENGTH, iv.size)
        envelopeFile.writeBytes(iv + cipher.doFinal(plaintext))
    }

    private fun unwrapFromFile(): ByteArray {
        val stored = envelopeFile.readBytes()
        val iv = stored.copyOfRange(0, IV_LENGTH)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, loadAesKey(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(stored, IV_LENGTH, stored.size - IV_LENGTH)
    }

    private fun deleteAesKeyIfPresent() {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (store.containsAlias(AES_ALIAS)) {
            store.deleteEntry(AES_ALIAS)
        }
    }

    private fun containsSequence(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty() || haystack.size < needle.size) return false
        for (start in 0..haystack.size - needle.size) {
            var match = true
            for (i in needle.indices) {
                if (haystack[start + i] != needle[i]) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }

    private companion object {
        const val TAG = "KeystoreSpike"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_ALIAS = "latifi_spike_aes_wrap"
        const val EC_ALIAS = "latifi_spike_ec_secp256k1"
        const val ENVELOPE_FILE_NAME = "spike-wrapped-key.bin"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_LENGTH = 32
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
