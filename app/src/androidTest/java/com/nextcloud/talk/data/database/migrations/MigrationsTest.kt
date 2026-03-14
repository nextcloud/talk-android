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
}
