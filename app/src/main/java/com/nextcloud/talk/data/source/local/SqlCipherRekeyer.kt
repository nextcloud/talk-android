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

    private const val JOURNAL_SUFFIX = "-journal"

    /**
     * Tables of the chat message cache, which is loaded again from the server when it is needed. They also hold the
     * messages that are not sent yet.
     */
    private val CACHE_TABLES = listOf("ChatBlocks", "ChatMessages")

    /**
     * Whether [passphrase] opens [dbFile]. A rollback journal left by an interrupted write makes a read-only open fail
     * whatever the key is, so the file is then opened writable, which rolls the journal back.
     */
    fun canOpen(dbFile: File, passphrase: ByteArray): Boolean =
        canOpen(dbFile, passphrase, SQLiteDatabase.OPEN_READONLY) ||
            (File(dbFile.path + JOURNAL_SUFFIX).exists() && canOpen(dbFile, passphrase, SQLiteDatabase.OPEN_READWRITE))

    @Suppress("TooGenericExceptionCaught")
    private fun canOpen(dbFile: File, passphrase: ByteArray, flags: Int): Boolean {
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, passphrase, null, flags, null)
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
     *
     * A re-key rewrites every page of the file, so the cached chat messages are removed before, which
     * keeps this one-time migration short also for a large database. They stay removed if the re-key
     * fails afterwards.
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
            clearCache(db)
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
     * Empties the [CACHE_TABLES] that exist, a database of an older version may not have all yet. A failure is only
     * logged: the re-key works without it, it just takes longer.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun clearCache(db: SQLiteDatabase) {
        try {
            // SQLCipher overwrites deleted rows by default, which takes as long as rewriting the whole file. Not
            // needed here: VACUUM removes the freed pages from the file.
            db.rawQuery("PRAGMA secure_delete = OFF", emptyArray()).use { it.moveToFirst() }
            db.beginTransaction()
            try {
                CACHE_TABLES
                    .filter { table ->
                        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
                            .use { it.moveToFirst() }
                    }
                    .forEach { table -> db.execSQL("DELETE FROM $table") }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            // the rekey rewrites all pages of the file, also the ones the deleted rows leave empty
            db.execSQL("VACUUM")
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear the chat message cache before re-keying", e)
        }
    }
}
