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
import java.security.KeyStore
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class TalkDatabaseTest {

    companion object {
        private const val TEST_DB_NAME = "talkdatabase-corruption-test.sqlite"

        // a dedicated prefs file and keystore alias so the test never touches the app's real key
        private const val TEST_PREFS = "talk_db_key_corruption_test"
        private const val TEST_KEY_ALIAS = "talk_db_passphrase_wrapping_key_corruption_test"
    }

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        dbFile = context.getDatabasePath(TEST_DB_NAME)
        resetKeyManagerState()
        context.deleteDatabase(TEST_DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB_NAME)
        resetKeyManagerState()
    }

    private fun resetKeyManagerState() {
        context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(TEST_KEY_ALIAS)) {
            keyStore.deleteEntry(TEST_KEY_ALIAS)
        }
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
            TalkDatabase.buildDatabase(context, TEST_DB_NAME, TEST_PREFS, TEST_KEY_ALIAS).close()
            fail("Expected opening a corrupted database file to throw")
        } catch (e: SQLiteException) {
            // expected, e.g. net.zetetic.database.sqlcipher.SQLiteNotADatabaseException
        }
    }

    @Test
    fun buildRecoversFromCorruptedDatabaseFile() {
        val corruptedSize = corruptDatabaseFile()

        val database = TalkDatabase.build(context, TEST_DB_NAME, TEST_PREFS, TEST_KEY_ALIAS)
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
