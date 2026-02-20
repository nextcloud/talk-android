/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.migrations

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nextcloud.talk.data.source.local.Migrations
import com.nextcloud.talk.data.source.local.TalkDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationsTest {

    companion object {
        private const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TalkDatabase::class.java
    )

    private fun insertMessage(
        db: SupportSQLiteDatabase,
        internalId: String,
        referenceId: String?,
        isTemporary: Int,
        timestamp: Long
    ) {
        db.execSQL(
            """
            INSERT INTO ChatMessages (
                internalId,
                accountId,
                token,
                id,
                internalConversationId,
                threadId,
                isThread,
                actorDisplayName,
                message,
                actorId,
                actorType,
                deleted,
                expirationTimestamp,
                isReplyable,
                isTemporary,
                lastEditActorDisplayName,
                lastEditActorId,
                lastEditActorType,
                lastEditTimestamp,
                markdown,
                messageParameters,
                messageType,
                parent,
                reactions,
                reactionsSelf,
                referenceId,
                sendStatus,
                silent,
                systemMessage,
                threadTitle,
                threadReplies,
                timestamp,
                pinnedActorType,
                pinnedActorId,
                pinnedActorDisplayName,
                pinnedAt,
                pinnedUntil,
                sendAt
            ) VALUES (
                '$internalId',
                1,
                'token',
                1,
                'conv',
                NULL,
                0,
                'User',
                'Hello',
                'actor1',
                'USER',
                0,
                0,
                0,
                $isTemporary,
                NULL,
                NULL,
                NULL,
                0,
                0,
                NULL,
                'comment',
                NULL,
                NULL,
                NULL,
                ${if (referenceId != null) "'$referenceId'" else "NULL"},
                NULL,
                0,
                0,
                NULL,
                0,
                $timestamp,
                NULL,
                NULL,
                NULL,
                NULL,
                NULL,
                0
            )
        """
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrate10To11() {
        helper.createDatabase(TEST_DB, 10).apply {
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 11, true, Migrations.MIGRATION_10_11)
    }

    @Test
    @Throws(IOException::class)
    fun migrate11To12() {
        helper.createDatabase(TEST_DB, 11).apply {
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 12, true, Migrations.MIGRATION_11_12)
    }

    @Test
    @Throws(IOException::class)
    fun migrate12To13() {
        helper.createDatabase(TEST_DB, 12).apply {
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 13, true, Migrations.MIGRATION_12_13)
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14() {
        helper.createDatabase(TEST_DB, 13).apply {
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 14, true, Migrations.MIGRATION_13_14)
    }

    @Test
    @Throws(IOException::class)
    fun migrate14To15() {
        helper.createDatabase(TEST_DB, 14).apply {
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 15, true, Migrations.MIGRATION_14_15)
    }

    @Test
    @Throws(IOException::class)
    fun migrate15To16() {
        helper.createDatabase(TEST_DB, 15).apply {
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 16, true, Migrations.MIGRATION_15_16)
    }

    @Test
    @Throws(IOException::class)
    fun migrate17To19() {
        helper.createDatabase(TEST_DB, 17).apply {
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 19, true, Migrations.MIGRATION_17_19)
    }

    @Test
    fun migrate23To24_prefersNonTemporary() {
        var db = helper.createDatabase(TEST_DB, 23)

        insertMessage(db, "1", "ref1", 1, 1000)
        insertMessage(db, "2", "ref1", 0, 2000)

        db.close()

        db = helper.runMigrationsAndValidate(
            TEST_DB,
            24,
            true,
            Migrations.MIGRATION_23_24
        )

        val cursor = db.query(
            """
            SELECT internalId, isTemporary, timestamp 
            FROM ChatMessages 
            WHERE referenceId = 'ref1'
        """
        )

        assertEquals(1, cursor.count)
        assertTrue(cursor.moveToFirst())

        val internalId = cursor.getString(0)
        val isTemporary = cursor.getInt(1)
        val timestamp = cursor.getLong(2)

        cursor.close()

        assertEquals("2", internalId)
        assertEquals(0, isTemporary)
        assertEquals(2000L, timestamp)
    }

    @Test
    fun migrate23To24_keepsNewestWhenAllTemporary() {
        var db = helper.createDatabase(TEST_DB, 23)

        insertMessage(db, "1", "ref2", 1, 1000)
        insertMessage(db, "2", "ref2", 1, 2000)

        db.close()

        db = helper.runMigrationsAndValidate(
            TEST_DB,
            24,
            true,
            Migrations.MIGRATION_23_24
        )

        val cursor = db.query(
            """
            SELECT internalId, timestamp 
            FROM ChatMessages 
            WHERE referenceId = 'ref2'
        """
        )

        assertEquals(1, cursor.count)
        assertTrue(cursor.moveToFirst())

        val internalId = cursor.getString(0)
        val timestamp = cursor.getLong(1)

        cursor.close()

        assertEquals("2", internalId)
        assertEquals(2000L, timestamp)
    }

    @Test
    fun migrate23To24_allowsMultipleNullReferenceIds() {
        var db = helper.createDatabase(TEST_DB, 23)

        insertMessage(db, "1", null, 0, 1000)
        insertMessage(db, "2", null, 0, 2000)

        db.close()

        db = helper.runMigrationsAndValidate(
            TEST_DB,
            24,
            true,
            Migrations.MIGRATION_23_24
        )

        val cursor = db.query(
            """
            SELECT COUNT(*) FROM ChatMessages WHERE referenceId IS NULL
        """
        )

        assertTrue(cursor.moveToFirst())
        val count = cursor.getInt(0)

        cursor.close()

        assertEquals(2, count)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun migrate23To24_enforcesUniqueIndex() {
        var db = helper.createDatabase(TEST_DB, 23)
        db.close()

        db = helper.runMigrationsAndValidate(
            TEST_DB,
            24,
            true,
            Migrations.MIGRATION_23_24
        )

        insertMessage(db, "1", "dup", 0, 1000)
        insertMessage(db, "2", "dup", 0, 2000)
    }
}
