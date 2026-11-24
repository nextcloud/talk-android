/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.source.local

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.UserEntity
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@Suppress("TooManyFunctions")
@RunWith(AndroidJUnit4::class)
class DatabaseKeyManagerTest {

    companion object {
        private const val TEST_DB_NAME = "talkdatabase-keymanager-test.sqlite"

        // a dedicated prefs file and keystore alias so the test never touches the app's real key
        private const val TEST_PREFS = "talk_db_key_test"
        private const val TEST_KEY_ALIAS = "talk_db_passphrase_wrapping_key_test"
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

    @Test
    fun freshDatabaseIsNotReadableWithTheStaticKey() {
        buildTestDatabase().close()

        assertFileNotReadableWith(legacyKey())
    }

    @Test
    fun passphraseIsStableAcrossReopen() {
        val first = buildTestDatabase()
        runBlocking { first.usersDao().saveUser(activeUser("alice")) }
        first.close()

        val second = buildTestDatabase()
        try {
            val user = second.usersDao().getActiveUserSynchronously()
            assertNotNull("User written before reopen must survive", user)
            assertEquals("alice", user?.userId)
        } finally {
            second.close()
        }
    }

    @Test
    fun legacyDatabaseIsMigratedToDeviceBoundKey() {
        seedLegacyDatabase("bob")

        val migrated = buildTestDatabase()
        try {
            val user = migrated.usersDao().getActiveUserSynchronously()
            assertNotNull("Data from the legacy database must survive migration", user)
            assertEquals("bob", user?.userId)
        } finally {
            migrated.close()
        }

        assertFileNotReadableWith(legacyKey())
    }

    @Test
    fun migrationInterruptedBeforeActivationResumes() {
        seedLegacyDatabase("erin")
        buildTestDatabase().close()

        // the file is on the new passphrase, but only the pending slot reached disk
        moveActiveToPending()

        val resumed = buildTestDatabase()
        try {
            val user = resumed.usersDao().getActiveUserSynchronously()
            assertNotNull("Data must survive a migration interrupted before activation", user)
            assertEquals("erin", user?.userId)
        } finally {
            resumed.close()
        }

        assertNotNull("The pending passphrase must be activated", storedPassphrase())
        assertNull(testPrefs().getString(DatabaseKeyManager.PREF_PENDING_PASSPHRASE, null))
    }

    @Test
    fun migrationInterruptedBeforeRekeyResumes() {
        // obtain a wrapped passphrase under the test key, then discard the database it was made for
        buildTestDatabase().close()
        context.deleteDatabase(TEST_DB_NAME)
        moveActiveToPending()

        // the pending passphrase is stored, but the file is still on the legacy key
        seedLegacyDatabase("frank", resetKeyState = false)

        val resumed = buildTestDatabase()
        try {
            val user = resumed.usersDao().getActiveUserSynchronously()
            assertNotNull("Data must survive a migration interrupted before the re-key", user)
            assertEquals("frank", user?.userId)
        } finally {
            resumed.close()
        }

        assertFileNotReadableWith(legacyKey())
    }

    @Test
    fun legacyDatabaseUnderExistingPassphraseIsMigratedNotWiped() {
        // a passphrase is already stored, but the file is still on the legacy key
        buildTestDatabase().close()
        context.deleteDatabase(TEST_DB_NAME)
        seedLegacyDatabase("grace", resetKeyState = false)

        val migrated = buildTestDatabase()
        try {
            val user = migrated.usersDao().getActiveUserSynchronously()
            assertNotNull("A legacy file must be migrated rather than recreated", user)
            assertEquals("grace", user?.userId)
        } finally {
            migrated.close()
        }

        assertFileNotReadableWith(legacyKey())
    }

    @Test
    fun corruptStoredPassphraseRecreatesDatabase() {
        val db = buildTestDatabase()
        runBlocking { db.usersDao().saveUser(activeUser("dave")) }
        db.close()

        val wrappedBefore = storedPassphrase()
        corruptStoredIv()

        val recreated = buildTestDatabase()
        try {
            assertNull(
                "A database whose passphrase can never be unwrapped is recreated empty",
                recreated.usersDao().getActiveUserSynchronously()
            )
        } finally {
            recreated.close()
        }

        assertNotEquals("A fresh passphrase must be provisioned", wrappedBefore, storedPassphrase())
    }

    private fun buildTestDatabase(): TalkDatabase =
        TalkDatabase.build(context, TEST_DB_NAME, TEST_PREFS, TEST_KEY_ALIAS)

    private fun seedLegacyDatabase(userId: String, resetKeyState: Boolean = true) {
        System.loadLibrary(TalkDatabase.SQL_CIPHER_LIBRARY)
        val legacyDb = Room
            .databaseBuilder(context.applicationContext, TalkDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(legacyKey()))
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking { legacyDb.usersDao().saveUser(activeUser(userId)) }
        } finally {
            legacyDb.close()
        }
        if (resetKeyState) {
            // the key manager must believe this is the first launch under the new scheme
            resetKeyManagerState()
        }
    }

    private fun assertFileNotReadableWith(passphrase: ByteArray) {
        System.loadLibrary(TalkDatabase.SQL_CIPHER_LIBRARY)
        try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READONLY,
                null
            ).use { db ->
                db.query("SELECT count(*) FROM sqlite_master").use { it.moveToFirst() }
            }
            fail("Database must not be readable with the static key")
        } catch (e: SQLiteException) {
            // expected: the file is encrypted with the per-install, device-bound passphrase
        }
    }

    private fun testPrefs() = context.getSharedPreferences(TEST_PREFS, Context.MODE_PRIVATE)

    private fun storedPassphrase(): String? = testPrefs().getString(DatabaseKeyManager.PREF_PASSPHRASE, null)

    private fun moveActiveToPending() {
        val prefs = testPrefs()
        val passphrase = prefs.getString(DatabaseKeyManager.PREF_PASSPHRASE, null)
        val iv = prefs.getString(DatabaseKeyManager.PREF_IV, null)
        prefs.edit()
            .putString(DatabaseKeyManager.PREF_PENDING_PASSPHRASE, passphrase)
            .putString(DatabaseKeyManager.PREF_PENDING_IV, iv)
            .remove(DatabaseKeyManager.PREF_PASSPHRASE)
            .remove(DatabaseKeyManager.PREF_IV)
            .commit()
    }

    private fun corruptStoredIv() {
        // '@' is outside the Base64 alphabet, so the stored IV can never be decoded
        testPrefs().edit()
            .putString(DatabaseKeyManager.PREF_IV, "@@@invalid@@@")
            .commit()
    }

    private fun resetKeyManagerState() {
        testPrefs().edit().clear().commit()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(TEST_KEY_ALIAS)) {
            keyStore.deleteEntry(TEST_KEY_ALIAS)
        }
    }

    private fun legacyKey(): ByteArray =
        context.getString(R.string.nc_talk_database_encryption_key).toByteArray(Charsets.UTF_8)

    private fun activeUser(userId: String) = UserEntity(userId = userId, username = userId, current = true)
}
