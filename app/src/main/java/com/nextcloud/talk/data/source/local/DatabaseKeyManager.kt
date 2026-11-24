/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.source.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.nextcloud.talk.R
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File
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

/**
 * Thrown when the stored database passphrase exists but cannot be unwrapped right now, for example
 * because the AndroidKeyStore is temporarily unavailable. Callers must not react by recreating the
 * database: the passphrase is left untouched so a later launch can recover it. After
 * [DatabaseKeyManager.MAX_TRANSIENT_UNWRAP_FAILURES] consecutive failures it is treated as lost.
 */
class PassphraseUnavailableException(message: String) : Exception(message)

/**
 * Supplies the SQLCipher passphrase for [TalkDatabase].
 *
 * The passphrase is a 256-bit random value generated once per installation and never stored in
 * plaintext. It is wrapped with an AES-GCM key held in the AndroidKeyStore, which is device-bound
 * and non-exportable, and only the wrapped form is kept in [SharedPreferences].
 *
 * A database created under the earlier static key ([R.string.nc_talk_database_encryption_key]) is
 * re-keyed to the per-install passphrase on first launch under this scheme. The static key is read
 * only for that one-time migration.
 */
object DatabaseKeyManager {

    private const val TAG = "DatabaseKeyManager"

    @VisibleForTesting
    const val PREFS_NAME = "talk_db_key"

    @VisibleForTesting
    const val PREF_PASSPHRASE = "wrapped_passphrase"

    @VisibleForTesting
    const val PREF_IV = "wrapped_passphrase_iv"

    @VisibleForTesting
    const val PREF_PENDING_PASSPHRASE = "pending_wrapped_passphrase"

    @VisibleForTesting
    const val PREF_PENDING_IV = "pending_wrapped_passphrase_iv"

    @VisibleForTesting
    const val PREF_UNWRAP_FAILURES = "unwrap_failures"

    const val MAX_TRANSIENT_UNWRAP_FAILURES = 3

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    @VisibleForTesting
    const val WRAPPING_KEY_ALIAS = "talk_db_passphrase_wrapping_key"
    private const val WRAPPING_KEY_SIZE = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private val TRANSFORMATION =
        KeyProperties.KEY_ALGORITHM_AES + "/" +
            KeyProperties.BLOCK_MODE_GCM + "/" +
            KeyProperties.ENCRYPTION_PADDING_NONE

    private const val PASSPHRASE_BYTES = 32

    private val lock = Any()

    /**
     * Returns the SQLCipher passphrase for the database at [databasePath], provisioning it on first
     * use and migrating a legacy database to it. Must be called before Room opens the file.
     *
     * [prefsName] and [keyAlias] identify the storage and the AndroidKeyStore entry used; the
     * defaults are the production values and are only overridden by tests so they cannot touch the
     * real key.
     *
     * @throws PassphraseUnavailableException if a passphrase is stored but cannot be unwrapped right
     * now; the stored value is left intact for a later attempt.
     */
    fun getPassphrase(
        context: Context,
        databasePath: String,
        prefsName: String = PREFS_NAME,
        keyAlias: String = WRAPPING_KEY_ALIAS
    ): ByteArray =
        synchronized(lock) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val dbFile = File(databasePath)

            when (val stored = readTracked(prefs, keyAlias, Slot.ACTIVE)) {
                is StoredPassphrase.Present -> stored.value
                StoredPassphrase.NotProvisioned -> provision(appContext, prefs, keyAlias, dbFile)
                StoredPassphrase.PermanentlyLost -> recoverFromLostKey(appContext, prefs, keyAlias)
                StoredPassphrase.TemporarilyUnavailable ->
                    throw PassphraseUnavailableException(
                        "Stored database passphrase could not be unwrapped; refusing to replace it"
                    )
            }
        }

    /**
     * Re-keys the database at [databasePath] from the legacy key to the stored passphrase, for a file
     * that the stored passphrase cannot open. Returns whether the file is now on the stored
     * passphrase; false if none is stored or the file is not on the legacy key.
     */
    fun migrateLegacyDatabase(
        context: Context,
        databasePath: String,
        prefsName: String = PREFS_NAME,
        keyAlias: String = WRAPPING_KEY_ALIAS
    ): Boolean =
        synchronized(lock) {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val dbFile = File(databasePath)
            val stored = readWrapped(prefs, keyAlias, Slot.ACTIVE)
            stored is StoredPassphrase.Present &&
                dbFile.exists() &&
                rekey(dbFile, legacyPassphrase(appContext), stored.value)
        }

    /**
     * Reads [slot] and counts consecutive transient unwrap failures across launches, so a failure
     * that never clears is eventually treated as a permanently lost passphrase instead of blocking
     * every launch.
     */
    private fun readTracked(prefs: SharedPreferences, keyAlias: String, slot: Slot): StoredPassphrase {
        val state = readWrapped(prefs, keyAlias, slot)
        val failures = prefs.getInt(PREF_UNWRAP_FAILURES, 0)
        return when {
            state != StoredPassphrase.TemporarilyUnavailable -> {
                if (failures > 0) prefs.edit(commit = true) { remove(PREF_UNWRAP_FAILURES) }
                state
            }
            failures + 1 >= MAX_TRANSIENT_UNWRAP_FAILURES -> {
                Log.w(TAG, "Passphrase could not be unwrapped on ${failures + 1} attempts; treating it as lost")
                prefs.edit(commit = true) { remove(PREF_UNWRAP_FAILURES) }
                StoredPassphrase.PermanentlyLost
            }
            else -> {
                prefs.edit(commit = true) { putInt(PREF_UNWRAP_FAILURES, failures + 1) }
                state
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun readWrapped(prefs: SharedPreferences, keyAlias: String, slot: Slot): StoredPassphrase {
        val ciphertext = prefs.getString(slot.passphraseKey, null) ?: return StoredPassphrase.NotProvisioned
        val iv = prefs.getString(slot.ivKey, null) ?: return StoredPassphrase.NotProvisioned

        val key = try {
            loadWrappingKey(keyAlias)
        } catch (e: UnrecoverableKeyException) {
            Log.w(TAG, "Wrapping key is unrecoverable; stored passphrase is lost", e)
            return StoredPassphrase.PermanentlyLost
        } catch (e: Exception) {
            Log.w(TAG, "Keystore unavailable while loading the wrapping key", e)
            return StoredPassphrase.TemporarilyUnavailable
        } ?: return StoredPassphrase.PermanentlyLost

        return try {
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

    /**
     * Provisions the passphrase when none is stored. A legacy database is re-keyed to a pending
     * passphrase that is persisted before the re-key, so an interrupted migration never leaves the
     * file on a key that is not stored; the pending passphrase becomes active once the file is on it.
     * Until then the legacy key is returned and a later launch retries.
     */
    private fun provision(context: Context, prefs: SharedPreferences, keyAlias: String, dbFile: File): ByteArray {
        if (!dbFile.exists()) {
            clearSlot(prefs, Slot.PENDING)
            return provisionFresh(context, prefs, keyAlias)
        }

        val legacy = legacyPassphrase(context)
        val pending = pendingPassphrase(prefs, keyAlias)

        // only a pending passphrase from an earlier, interrupted attempt can already be on the file
        return if (
            pending != null &&
            ((pending.resumed && canOpen(dbFile, pending.value)) || rekey(dbFile, legacy, pending.value))
        ) {
            promotePending(prefs)
            pending.value
        } else {
            legacy
        }
    }

    /**
     * Returns the passphrase a legacy database is being migrated to, creating and persisting one if
     * no migration is in progress. Returns null if it cannot be created or persisted.
     */
    private fun pendingPassphrase(prefs: SharedPreferences, keyAlias: String): PendingPassphrase? =
        when (val pending = readTracked(prefs, keyAlias, Slot.PENDING)) {
            is StoredPassphrase.Present -> PendingPassphrase(pending.value, resumed = true)
            StoredPassphrase.NotProvisioned, StoredPassphrase.PermanentlyLost ->
                createPending(prefs, keyAlias)?.let { PendingPassphrase(it, resumed = false) }
            StoredPassphrase.TemporarilyUnavailable ->
                throw PassphraseUnavailableException(
                    "Pending database passphrase could not be unwrapped; refusing to replace it"
                )
        }

    private fun createPending(prefs: SharedPreferences, keyAlias: String): ByteArray? {
        val passphrase = randomPassphrase()
        val wrapped = tryWrap(keyAlias, passphrase)
        return if (wrapped != null && writeWrapped(prefs, Slot.PENDING, wrapped)) passphrase else null
    }

    private fun promotePending(prefs: SharedPreferences) {
        val committed = prefs.edit()
            .putString(PREF_PASSPHRASE, prefs.getString(PREF_PENDING_PASSPHRASE, null))
            .putString(PREF_IV, prefs.getString(PREF_PENDING_IV, null))
            .remove(PREF_PENDING_PASSPHRASE)
            .remove(PREF_PENDING_IV)
            .commit()
        if (!committed) {
            Log.w(TAG, "Could not activate the migrated passphrase; it stays pending until a later launch")
        }
    }

    /**
     * Provisions a passphrase for a database that does not exist yet. If it cannot be wrapped or
     * persisted the legacy key is returned instead, and the database created with it is migrated on a
     * later launch.
     */
    private fun provisionFresh(context: Context, prefs: SharedPreferences, keyAlias: String): ByteArray {
        val passphrase = randomPassphrase()
        val wrapped = tryWrap(keyAlias, passphrase)
        return if (wrapped != null && writeWrapped(prefs, Slot.ACTIVE, wrapped)) {
            passphrase
        } else {
            legacyPassphrase(context)
        }
    }

    /**
     * Recovers from a wrapping key that is gone or permanently invalidated: the stored passphrase
     * can never be unwrapped again, so it and the dead key are discarded and a fresh passphrase is
     * provisioned. The now-undecryptable database file is recreated by [TalkDatabase.build].
     */
    private fun recoverFromLostKey(context: Context, prefs: SharedPreferences, keyAlias: String): ByteArray {
        clearSlot(prefs, Slot.ACTIVE)
        deleteWrappingKey(keyAlias)
        return provisionFresh(context, prefs, keyAlias)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun canOpen(dbFile: File, passphrase: ByteArray): Boolean {
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READONLY,
                null
            )
            db.query("SELECT count(*) FROM sqlite_master").use { it.moveToFirst() }
            true
        } catch (e: Exception) {
            false
        } finally {
            db?.close()
        }
    }

    /**
     * Re-keys [dbFile] from [oldPassphrase] to [newPassphrase], returning whether it succeeded. The
     * database is left on its existing key on any failure.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun rekey(dbFile: File, oldPassphrase: ByteArray, newPassphrase: ByteArray): Boolean {
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                oldPassphrase,
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null
            )
            db.changePassword(newPassphrase)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not re-key database; keeping the existing key", e)
            false
        } finally {
            db?.close()
        }
    }

    /**
     * Persists [wrapped] into [slot], returning whether it reached disk. On failure the slot is
     * cleared again so the in-memory preferences never hold a passphrase that is not stored.
     */
    private fun writeWrapped(prefs: SharedPreferences, slot: Slot, wrapped: WrappedPassphrase): Boolean {
        val committed = prefs.edit()
            .putString(slot.passphraseKey, Base64.encodeToString(wrapped.ciphertext, Base64.NO_WRAP))
            .putString(slot.ivKey, Base64.encodeToString(wrapped.iv, Base64.NO_WRAP))
            .commit()
        if (!committed) {
            Log.w(TAG, "Could not persist the wrapped passphrase")
            clearSlot(prefs, slot)
        }
        return committed
    }

    private fun clearSlot(prefs: SharedPreferences, slot: Slot) {
        prefs.edit(commit = true) {
            remove(slot.passphraseKey)
            remove(slot.ivKey)
        }
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

    /** Like [wrap], but returns null instead of throwing when the AndroidKeyStore is unusable. */
    @Suppress("TooGenericExceptionCaught")
    private fun tryWrap(keyAlias: String, passphrase: ByteArray): WrappedPassphrase? =
        try {
            wrap(keyAlias, passphrase)
        } catch (e: Exception) {
            Log.w(TAG, "Keystore unavailable; using the legacy key until a later launch", e)
            null
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

    private fun randomPassphrase(): ByteArray {
        val raw = ByteArray(PASSPHRASE_BYTES)
        SecureRandom().nextBytes(raw)
        // encode so the passphrase is printable ASCII with no embedded NUL bytes
        return Base64.encode(raw, Base64.NO_WRAP)
    }

    private fun legacyPassphrase(context: Context): ByteArray =
        context.getString(R.string.nc_talk_database_encryption_key).toByteArray(Charsets.UTF_8)

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

    @Suppress("TooGenericExceptionCaught")
    private fun deleteWrappingKey(keyAlias: String) {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete the wrapping key", e)
        }
    }

    private sealed interface StoredPassphrase {
        class Present(val value: ByteArray) : StoredPassphrase
        object NotProvisioned : StoredPassphrase
        object TemporarilyUnavailable : StoredPassphrase
        object PermanentlyLost : StoredPassphrase
    }

    private enum class Slot(val passphraseKey: String, val ivKey: String) {
        ACTIVE(PREF_PASSPHRASE, PREF_IV),
        PENDING(PREF_PENDING_PASSPHRASE, PREF_PENDING_IV)
    }

    /** A pending passphrase; [resumed] is true if it was stored by an earlier launch. */
    private class PendingPassphrase(val value: ByteArray, val resumed: Boolean)

    private class WrappedPassphrase(val ciphertext: ByteArray, val iv: ByteArray)
}
