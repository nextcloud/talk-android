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
import org.junit.Assert.assertTrue
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

        // the file is on the raw key of the new passphrase, but only the pending slot reached disk
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
    fun freshDatabaseIsOnTheRawKey() {
        buildTestDatabase().close()

        assertFileReadableWith(PassphraseCipher.rawKeyOf(unwrappedPassphrase()))
        assertFileNotReadableWith(unwrappedPassphrase())
    }

    @Test
    fun databaseOnThePassphraseIsRekeyedToTheRawKey() {
        // a database of an earlier version, which used the stored passphrase itself as key
        buildTestDatabase().close()
        context.deleteDatabase(TEST_DB_NAME)
        seedDatabase("heidi", unwrappedPassphrase())

        val rekeyed = buildTestDatabase()
        try {
            val user = rekeyed.usersDao().getActiveUserSynchronously()
            assertNotNull("Data must survive the re-key to the raw key", user)
            assertEquals("heidi", user?.userId)
        } finally {
            rekeyed.close()
        }

        assertFileReadableWith(PassphraseCipher.rawKeyOf(unwrappedPassphrase()))
        assertFileNotReadableWith(unwrappedPassphrase())
    }

    @Test
    fun migrationOfAnEarlierVersionInterruptedBeforeActivationResumes() {
        // an earlier version re-keyed the legacy file to the pending passphrase itself, then was interrupted
        buildTestDatabase().close()
        val passphrase = unwrappedPassphrase()
        context.deleteDatabase(TEST_DB_NAME)
        moveActiveToPending()
        seedDatabase("ivan", passphrase)

        val resumed = buildTestDatabase()
        try {
            val user = resumed.usersDao().getActiveUserSynchronously()
            assertNotNull("Data must survive a migration of an earlier version interrupted before activation", user)
            assertEquals("ivan", user?.userId)
        } finally {
            resumed.close()
        }

        assertFileReadableWith(PassphraseCipher.rawKeyOf(passphrase))
        assertFileNotReadableWith(passphrase)
        assertNotNull("The pending passphrase must be activated", storedPassphrase())
        assertNull(testPrefs().getString(DatabaseKeyManager.PREF_PENDING_PASSPHRASE, null))
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
        seedDatabase(userId, legacyKey())
        if (resetKeyState) {
            // the key manager must believe this is the first launch under the new scheme
            resetKeyManagerState()
        }
    }

    private fun seedDatabase(userId: String, key: ByteArray) {
        System.loadLibrary(TalkDatabase.SQL_CIPHER_LIBRARY)
        val db = Room
            .databaseBuilder(context.applicationContext, TalkDatabase::class.java, TEST_DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(key))
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking { db.usersDao().saveUser(activeUser(userId)) }
        } finally {
            db.close()
        }
    }

    private fun assertFileReadableWith(key: ByteArray) {
        System.loadLibrary(TalkDatabase.SQL_CIPHER_LIBRARY)
        assertTrue("Database must be readable with the key", SqlCipherRekeyer.canOpen(dbFile, key))
    }

    /** The stored passphrase, from the active slot or, if it was moved there, the pending one. */
    private fun unwrappedPassphrase(): ByteArray {
        val prefs = testPrefs()
        val ciphertext = prefs.getString(DatabaseKeyManager.PREF_PASSPHRASE, null)
            ?: prefs.getString(DatabaseKeyManager.PREF_PENDING_PASSPHRASE, null)
        val iv = prefs.getString(DatabaseKeyManager.PREF_IV, null)
            ?: prefs.getString(DatabaseKeyManager.PREF_PENDING_IV, null)
        val stored = PassphraseCipher.unwrap(TEST_KEY_ALIAS, iv!!, ciphertext!!)
        return (stored as StoredPassphrase.Present).value
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
            fail("Database must not be readable with this key")
        } catch (e: SQLiteException) {
            // expected: the file is on another key
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
