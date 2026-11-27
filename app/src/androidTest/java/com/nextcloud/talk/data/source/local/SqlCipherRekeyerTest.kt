/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.source.local

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SqlCipherRekeyerTest {

    companion object {
        private const val TEST_DB_NAME = "sqlcipher-rekeyer-test.sqlite"
        private val OLD_KEY = "old-key".toByteArray()
        private val NEW_KEY = "new-key".toByteArray()
    }

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary(TalkDatabase.SQL_CIPHER_LIBRARY)
        dbFile = context.getDatabasePath(TEST_DB_NAME)
        context.deleteDatabase(TEST_DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB_NAME)
    }

    @Test
    fun rekeyClearsTheCachedChatMessagesAndKeepsTheOtherTables() {
        createDatabase(listOf("User", "Conversations", "ChatMessages", "ChatBlocks"))

        assertTrue(SqlCipherRekeyer.rekey(dbFile, OLD_KEY, NEW_KEY))

        assertFalse(SqlCipherRekeyer.canOpen(dbFile, OLD_KEY))
        assertEquals(1, rowCount("User"))
        assertEquals(1, rowCount("Conversations"))
        assertEquals(0, rowCount("ChatMessages"))
        assertEquals(0, rowCount("ChatBlocks"))
    }

    @Test
    fun rekeySucceedsWithoutCacheTables() {
        // a database of an older version, created before the chat messages were cached
        createDatabase(listOf("User"))

        assertTrue(SqlCipherRekeyer.rekey(dbFile, OLD_KEY, NEW_KEY))

        assertEquals(1, rowCount("User"))
    }

    @Test
    fun rekeyWithTheWrongKeyChangesNothing() {
        createDatabase(listOf("User", "ChatMessages"))

        assertFalse(SqlCipherRekeyer.rekey(dbFile, NEW_KEY, OLD_KEY))

        assertTrue(SqlCipherRekeyer.canOpen(dbFile, OLD_KEY))
        assertEquals(1, rowCount("ChatMessages", OLD_KEY))
    }

    private fun createDatabase(tables: List<String>) {
        SQLiteDatabase.openOrCreateDatabase(dbFile, OLD_KEY, null, null, null).use { db ->
            tables.forEach { table ->
                db.execSQL("CREATE TABLE $table (id INTEGER PRIMARY KEY, value TEXT)")
                db.execSQL("INSERT INTO $table (value) VALUES ('row')")
            }
        }
    }

    private fun rowCount(table: String, key: ByteArray = NEW_KEY): Int =
        SQLiteDatabase.openDatabase(dbFile.absolutePath, key, null, SQLiteDatabase.OPEN_READONLY, null).use { db ->
            db.rawQuery("SELECT count(*) FROM $table", emptyArray()).use {
                it.moveToFirst()
                it.getInt(0)
            }
        }
}
