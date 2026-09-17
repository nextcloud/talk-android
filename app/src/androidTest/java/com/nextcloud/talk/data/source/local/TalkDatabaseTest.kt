/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.source.local

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class TalkDatabaseTest {

    companion object {
        private const val TEST_DB_NAME = "talkdatabase-corruption-test.sqlite"
    }

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        dbFile = context.getDatabasePath(TEST_DB_NAME)
        context.deleteDatabase(TEST_DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB_NAME)
    }

    // writes bytes that are neither a valid SQLite header nor a valid SQLCipher header,
    // simulating a corrupted file or leftover file from an incompatible format
    private fun corruptDatabaseFile(): Long {
        dbFile.parentFile?.mkdirs()
        dbFile.writeBytes(Random.nextBytes(16))
        return dbFile.length()
    }

    @Test
    fun corruptedFileFailsToOpenDirectly() {
        corruptDatabaseFile()

        try {
            TalkDatabase.buildDatabase(context, TEST_DB_NAME).close()
            fail("Expected opening a corrupted database file to throw")
        } catch (e: SQLiteException) {
            // expected, e.g. net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
        }
    }

    @Test
    fun buildRecoversFromCorruptedDatabaseFile() {
        val corruptedSize = corruptDatabaseFile()

        val database = TalkDatabase.build(context, TEST_DB_NAME)
        try {
            // a fresh database has no users, which proves the file was rebuilt from
            // scratch rather than somehow read in its unreadable state
            assertNull(database.usersDao().getActiveUserSynchronously())
        } finally {
            database.close()
        }

        assertTrue(dbFile.exists())
        assertTrue(dbFile.length() > corruptedSize)
    }
}
