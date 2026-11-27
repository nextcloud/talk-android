/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.source.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.nextcloud.talk.R
import java.io.File

/**
 * Thrown when the stored database passphrase exists but cannot be unwrapped right now, for example
 * because the AndroidKeyStore is temporarily unavailable. Callers must not react by recreating the
 * database: the passphrase is left untouched so a later launch can recover it. After
 * [DatabaseKeyManager.MAX_TRANSIENT_UNWRAP_FAILURES] consecutive failures it is treated as lost.
 */
class PassphraseUnavailableException(message: String) : Exception(message)

/**
 * Supplies the SQLCipher key for [TalkDatabase].
 *
 * The passphrase is a 256-bit random value generated once per installation and never stored in
 * plaintext. It is wrapped with an AES-GCM key held in the AndroidKeyStore, which is device-bound
 * and non-exportable, and only the wrapped form is kept in [SharedPreferences]. The database is
 * opened with its raw key ([PassphraseCipher.rawKeyOf]), and a database still on the passphrase is
 * re-keyed to it once.
 *
 * A database created under the earlier static key ([R.string.nc_talk_database_encryption_key]) is
 * re-keyed to the raw key of the per-install passphrase on first launch under this scheme. The static key is read
 * only for that one-time migration.
 */
@Suppress("TooManyFunctions")
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

    @VisibleForTesting
    const val WRAPPING_KEY_ALIAS = "talk_db_passphrase_wrapping_key"

    private val lock = Any()

    /**
     * Returns the SQLCipher key for the database at [databasePath], provisioning it on first use and
     * migrating a legacy database to it. Must be called before Room opens the file.
     *
     * [prefsName] and [keyAlias] identify the storage and the AndroidKeyStore entry used; the
     * defaults are the production values and are only overridden by tests so they cannot touch the
     * real key.
     *
     * @throws PassphraseUnavailableException if a passphrase is stored but cannot be unwrapped right
     * now; the stored value is left intact for a later attempt.
     */
    fun getDatabaseKey(
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
                is StoredPassphrase.Present -> keyFor(dbFile, stored.value)
                StoredPassphrase.NotProvisioned -> provision(appContext, prefs, keyAlias, dbFile)
                StoredPassphrase.PermanentlyLost -> recoverFromLostKey(appContext, prefs, keyAlias, dbFile)
                StoredPassphrase.TemporarilyUnavailable ->
                    throw PassphraseUnavailableException(
                        "Stored database passphrase could not be unwrapped; refusing to replace it"
                    )
            }
        }

    /**
     * Re-keys the database at [databasePath] from the legacy key to the raw key of the stored
     * passphrase, for a file that the stored key cannot open. Returns whether the file is now on it;
     * false if none is stored or the file is not on the legacy key.
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
                SqlCipherRekeyer.rekey(dbFile, legacyPassphrase(appContext), PassphraseCipher.rawKeyOf(stored.value))
        }

    /**
     * Returns the raw key of [passphrase] for [dbFile], re-keying a file that is still on the
     * passphrase itself. If that fails, e.g. as the file is on neither, the passphrase is returned and
     * a later launch retries.
     */
    private fun keyFor(dbFile: File, passphrase: ByteArray): ByteArray {
        val rawKey = PassphraseCipher.rawKeyOf(passphrase)
        return if (
            !dbFile.exists() ||
            SqlCipherRekeyer.canOpen(dbFile, rawKey) ||
            SqlCipherRekeyer.rekey(dbFile, passphrase, rawKey)
        ) {
            rawKey
        } else {
            passphrase
        }
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

    private fun readWrapped(prefs: SharedPreferences, keyAlias: String, slot: Slot): StoredPassphrase {
        val ciphertext = prefs.getString(slot.passphraseKey, null)
        val iv = prefs.getString(slot.ivKey, null)
        return if (ciphertext == null || iv == null) {
            StoredPassphrase.NotProvisioned
        } else {
            PassphraseCipher.unwrap(keyAlias, iv, ciphertext)
        }
    }

    /**
     * Provisions the passphrase when none is stored. A legacy database is re-keyed to the raw key of a
     * pending passphrase that is persisted before the re-key, so an interrupted migration never leaves
     * the file on a key that is not stored; the pending passphrase becomes active once the file is on
     * its raw key.
     * Until then the legacy key is returned and a later launch retries.
     */
    private fun provision(context: Context, prefs: SharedPreferences, keyAlias: String, dbFile: File): ByteArray {
        if (!dbFile.exists()) {
            clearSlot(prefs, Slot.PENDING)
            return provisionFresh(context, prefs, keyAlias, dbFile)
        }

        val legacy = legacyPassphrase(context)
        val pending = pendingPassphrase(prefs, keyAlias)

        // only a pending passphrase from an earlier, interrupted attempt can already be on the file
        return if (pending != null && isOnPending(dbFile, legacy, pending)) {
            promotePending(prefs)
            keyFor(dbFile, pending.value)
        } else {
            legacy
        }
    }

    /**
     * Whether [dbFile] is on [pending] or its raw key, re-keying it from [legacy] to the raw key if it
     * is on neither yet.
     */
    private fun isOnPending(dbFile: File, legacy: ByteArray, pending: PendingPassphrase): Boolean {
        val rawKey = PassphraseCipher.rawKeyOf(pending.value)
        return (
            pending.resumed &&
                (SqlCipherRekeyer.canOpen(dbFile, rawKey) || SqlCipherRekeyer.canOpen(dbFile, pending.value))
            ) ||
            SqlCipherRekeyer.rekey(dbFile, legacy, rawKey)
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
        val passphrase = PassphraseCipher.randomPassphrase()
        val wrapped = PassphraseCipher.tryWrap(keyAlias, passphrase)
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
    private fun provisionFresh(context: Context, prefs: SharedPreferences, keyAlias: String, dbFile: File): ByteArray {
        val passphrase = PassphraseCipher.randomPassphrase()
        val wrapped = PassphraseCipher.tryWrap(keyAlias, passphrase)
        return if (wrapped != null && writeWrapped(prefs, Slot.ACTIVE, wrapped)) {
            keyFor(dbFile, passphrase)
        } else {
            legacyPassphrase(context)
        }
    }

    /**
     * Recovers from a wrapping key that is gone or permanently invalidated: the stored passphrase
     * can never be unwrapped again, so it and the dead key are discarded and a fresh passphrase is
     * provisioned. The now-undecryptable database file is recreated by [TalkDatabase.build].
     */
    private fun recoverFromLostKey(
        context: Context,
        prefs: SharedPreferences,
        keyAlias: String,
        dbFile: File
    ): ByteArray {
        clearSlot(prefs, Slot.ACTIVE)
        PassphraseCipher.deleteWrappingKey(keyAlias)
        return provisionFresh(context, prefs, keyAlias, dbFile)
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

    private fun legacyPassphrase(context: Context): ByteArray =
        context.getString(R.string.nc_talk_database_encryption_key).toByteArray(Charsets.UTF_8)

    private enum class Slot(val passphraseKey: String, val ivKey: String) {
        ACTIVE(PREF_PASSPHRASE, PREF_IV),
        PENDING(PREF_PENDING_PASSPHRASE, PREF_PENDING_IV)
    }

    /** A pending passphrase; [resumed] is true if it was stored by an earlier launch. */
    private class PendingPassphrase(val value: ByteArray, val resumed: Boolean)
}
