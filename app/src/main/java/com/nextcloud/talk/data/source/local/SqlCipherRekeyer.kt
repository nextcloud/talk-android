/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.source.local

import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/** Opens and re-keys SQLCipher database files directly, outside of Room. */
internal object SqlCipherRekeyer {

    private const val TAG = "SqlCipherRekeyer"

    @Suppress("TooGenericExceptionCaught")
    fun canOpen(dbFile: File, passphrase: ByteArray): Boolean {
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
    fun rekey(dbFile: File, oldPassphrase: ByteArray, newPassphrase: ByteArray): Boolean {
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
}
