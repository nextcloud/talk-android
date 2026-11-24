/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.source.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** State of a stored, wrapped database passphrase. */
internal sealed interface StoredPassphrase {
    class Present(val value: ByteArray) : StoredPassphrase
    object NotProvisioned : StoredPassphrase
    object TemporarilyUnavailable : StoredPassphrase
    object PermanentlyLost : StoredPassphrase
}

internal class WrappedPassphrase(val ciphertext: ByteArray, val iv: ByteArray)

/**
 * Generates database passphrases and wraps and unwraps them with an AES-GCM key held in the
 * AndroidKeyStore, which is device-bound and non-exportable.
 */
internal object PassphraseCipher {

    private const val TAG = "PassphraseCipher"

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val WRAPPING_KEY_SIZE = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private val TRANSFORMATION =
        KeyProperties.KEY_ALGORITHM_AES + "/" +
            KeyProperties.BLOCK_MODE_GCM + "/" +
            KeyProperties.ENCRYPTION_PADDING_NONE

    private const val PASSPHRASE_BYTES = 32

    fun randomPassphrase(): ByteArray {
        val raw = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(raw)
        // encode so the passphrase is printable ASCII with no embedded NUL bytes
        return Base64.encode(raw, Base64.NO_WRAP)
    }

    /**
     * Unwraps the Base64-encoded [ciphertext] and [iv] with the key under [keyAlias], classifying
     * a failure as temporary (keystore unavailable) or permanent (key or data unrecoverable).
     */
    @Suppress("TooGenericExceptionCaught")
    fun unwrap(keyAlias: String, iv: String, ciphertext: String): StoredPassphrase {
        val key = try {
            loadWrappingKey(keyAlias)
        } catch (e: Exception) {
            return keyLoadFailure(e)
        }
        return if (key == null) StoredPassphrase.PermanentlyLost else decryptStored(key, iv, ciphertext)
    }

    private fun keyLoadFailure(e: Exception): StoredPassphrase =
        if (e is UnrecoverableKeyException) {
            Log.w(TAG, "Wrapping key is unrecoverable; stored passphrase is lost", e)
            StoredPassphrase.PermanentlyLost
        } else {
            Log.w(TAG, "Keystore unavailable while loading the wrapping key", e)
            StoredPassphrase.TemporarilyUnavailable
        }

    @Suppress("TooGenericExceptionCaught")
    private fun decryptStored(key: SecretKey, iv: String, ciphertext: String): StoredPassphrase =
        try {
            val decoded = decrypt(
                key,
                Base64.decode(iv, Base64.NO_WRAP),
                Base64.decode(ciphertext, Base64.NO_WRAP)
            )
            StoredPassphrase.Present(decoded)
        } catch (e: Exception) {
            if (isPermanentUnwrapFailure(e)) {
                Log.w(TAG, "Stored passphrase is unrecoverable", e)
                StoredPassphrase.PermanentlyLost
            } else {
                Log.w(TAG, "Could not unwrap stored passphrase; keeping it for a later attempt", e)
                StoredPassphrase.TemporarilyUnavailable
            }
        }

    /**
     * Whether [e] means the stored passphrase can never be unwrapped: malformed Base64 or IV,
     * truncated or tampered ciphertext (including a failed GCM tag), or an invalidated key.
     */
    private fun isPermanentUnwrapFailure(e: Exception): Boolean =
        e is IllegalArgumentException ||
            e is KeyPermanentlyInvalidatedException ||
            e is InvalidAlgorithmParameterException ||
            e is IllegalBlockSizeException ||
            e is BadPaddingException

    /** Like [wrap], but returns null instead of throwing when the AndroidKeyStore is unusable. */
    @Suppress("TooGenericExceptionCaught")
    fun tryWrap(keyAlias: String, passphrase: ByteArray): WrappedPassphrase? =
        try {
            wrap(keyAlias, passphrase)
        } catch (e: Exception) {
            Log.w(TAG, "Keystore unavailable; using the legacy key until a later launch", e)
            null
        }

    /**
     * Wraps [passphrase] with the AndroidKeyStore key. If the existing key is unusable it is deleted
     * and regenerated once so a single broken key cannot wedge startup permanently.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun wrap(keyAlias: String, passphrase: ByteArray): WrappedPassphrase =
        try {
            encrypt(getOrCreateWrappingKey(keyAlias), passphrase)
        } catch (e: Exception) {
            Log.w(TAG, "Wrapping key unusable; regenerating it", e)
            deleteWrappingKey(keyAlias)
            encrypt(createWrappingKey(keyAlias), passphrase)
        }

    @Suppress("TooGenericExceptionCaught")
    fun deleteWrappingKey(keyAlias: String) {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete the wrapping key", e)
        }
    }

    private fun encrypt(key: SecretKey, passphrase: ByteArray): WrappedPassphrase {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return WrappedPassphrase(cipher.doFinal(passphrase), cipher.iv)
    }

    private fun decrypt(key: SecretKey, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getOrCreateWrappingKey(keyAlias: String): SecretKey =
        loadWrappingKey(keyAlias) ?: createWrappingKey(keyAlias)

    private fun loadWrappingKey(keyAlias: String): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.getKey(keyAlias, null) as? SecretKey
    }

    private fun createWrappingKey(keyAlias: String): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(WRAPPING_KEY_SIZE)
                .build()
        )
        return generator.generateKey()
    }
}
