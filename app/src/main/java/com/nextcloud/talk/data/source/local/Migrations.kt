/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.source.local

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.sql.SQLException

@Suppress("MagicNumber")
object Migrations {
    val MIGRATION_6_8 = object : Migration(6, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i("Migrations", "Migrating 6 to 8")
            migrateToRoom(db)
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i("Migrations", "Migrating 7 to 8")
            migrateToRoom(db)
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i("Migrations", "Migrating 8 to 9")
            migrateToDualPrimaryKeyArbitraryStorage(db)
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i("Migrations", "Migrating 10 to 11")
            migrateToOfflineSupport(db)
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            Log.i("Migrations", "Migrating 11 to 12")
            addArchiveConversations(db)
        }
    }

    fun migrateToRoom(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE User_new (" +
                "id INTEGER NOT NULL, " +
                "userId TEXT, " +
                "username TEXT, " +
                "baseUrl TEXT, " +
                "token TEXT, " +
                "displayName TEXT, " +
                "pushConfigurationState TEXT, " +
                "capabilities TEXT, " +
                "clientCertificate TEXT, " +
                "externalSignalingServer TEXT, " +
                "current INTEGER NOT NULL, " +
                "scheduledForDeletion INTEGER NOT NULL, " +
                "PRIMARY KEY(id)" +
                ")"
        )

        db.execSQL(
            "CREATE TABLE ArbitraryStorage_new (" +
                "accountIdentifier INTEGER NOT NULL, " +
                "\"key\" TEXT, " +
                "object TEXT, " +
                "value TEXT, " +
                "PRIMARY KEY(accountIdentifier)" +
                ")"
        )
        // Copy the data
        db.execSQL(
            "INSERT INTO User_new (" +
                "id, userId, username, baseUrl, token, displayName, pushConfigurationState, capabilities, " +
                "clientCertificate, externalSignalingServer, current, scheduledForDeletion) " +
                "SELECT " +
                "id, userId, username, baseUrl, token, displayName, pushConfigurationState, capabilities, " +
                "clientCertificate, externalSignalingServer, current, scheduledForDeletion " +
                "FROM User"
        )
        db.execSQL(
            "INSERT INTO ArbitraryStorage_new (" +
                "accountIdentifier, \"key\", object, value) " +
                "SELECT " +
                "accountIdentifier, \"key\", object, value " +
                "FROM ArbitraryStorage"
        )
        // Remove the old table
        db.execSQL("DROP TABLE User")
        db.execSQL("DROP TABLE ArbitraryStorage")

        // Change the table name to the correct one
        db.execSQL("ALTER TABLE User_new RENAME TO User")
        db.execSQL("ALTER TABLE ArbitraryStorage_new RENAME TO ArbitraryStorage")
    }

    fun migrateToDualPrimaryKeyArbitraryStorage(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE ArbitraryStorage_dualPK (" +
                "accountIdentifier INTEGER NOT NULL, " +
                "\"key\" TEXT  NOT NULL, " +
                "object TEXT, " +
                "value TEXT, " +
                "PRIMARY KEY(accountIdentifier, \"key\")" +
                ")"
        )
        // Copy the data
        db.execSQL(
            "INSERT INTO ArbitraryStorage_dualPK (" +
                "accountIdentifier, \"key\", object, value) " +
                "SELECT " +
                "accountIdentifier, \"key\", object, value " +
                "FROM ArbitraryStorage"
        )
        // Remove the old table
        db.execSQL("DROP TABLE ArbitraryStorage")

        // Change the table name to the correct one
        db.execSQL("ALTER TABLE ArbitraryStorage_dualPK RENAME TO ArbitraryStorage")
    }

    fun migrateToOfflineSupport(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS Conversations (" +
                "`internalId` TEXT NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`token` TEXT NOT NULL, " +
                "`displayName` TEXT NOT NULL, " +
                "`actorId` TEXT NOT NULL, " +
                "`actorType` TEXT NOT NULL, " +
                "`avatarVersion` TEXT NOT NULL, " +
                "`callFlag` INTEGER NOT NULL, " +
                "`callRecording` INTEGER NOT NULL, " +
                "`callStartTime` INTEGER NOT NULL, " +
                "`canDeleteConversation` INTEGER NOT NULL, " +
                "`canLeaveConversation` INTEGER NOT NULL, " +
                "`canStartCall` INTEGER NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`hasCall` INTEGER NOT NULL, " +
                "`hasPassword` INTEGER NOT NULL, " +
                "`isCustomAvatar` INTEGER NOT NULL, " +
                "`isFavorite` INTEGER NOT NULL, " +
                "`lastActivity` INTEGER NOT NULL, " +
                "`lastCommonReadMessage` INTEGER NOT NULL, " +
                "`lastMessage` TEXT, " +
                "`lastPing` INTEGER NOT NULL, " +
                "`lastReadMessage` INTEGER NOT NULL, " +
                "`lobbyState` TEXT NOT NULL, " +
                "`lobbyTimer` INTEGER NOT NULL, " +
                "`messageExpiration` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`notificationCalls` INTEGER NOT NULL, " +
                "`notificationLevel` TEXT NOT NULL, " +
                "`objectType` TEXT NOT NULL, " +
                "`participantType` TEXT NOT NULL, " +
                "`permissions` INTEGER NOT NULL, " +
                "`readOnly` TEXT NOT NULL, " +
                "`recordingConsent` INTEGER NOT NULL, " +
                "`remoteServer` TEXT, " +
                "`remoteToken` TEXT, " +
                "`sessionId` TEXT NOT NULL, " +
                "`status` TEXT, " +
                "`statusClearAt` INTEGER, " +
                "`statusIcon` TEXT, " +
                "`statusMessage` TEXT, " +
                "`type` TEXT NOT NULL, " +
                "`unreadMention` INTEGER NOT NULL, " +
                "`unreadMentionDirect` INTEGER NOT NULL, " +
                "`unreadMessages` INTEGER NOT NULL, " +
                "PRIMARY KEY(`internalId`), " +
                "FOREIGN KEY(`accountId`) REFERENCES `User`(`id`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE " +
                ")"
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_Conversations_accountId` ON `Conversations` (`accountId`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ChatMessages (" +
                "`internalId` TEXT NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`token` TEXT NOT NULL, " +
                "`id` INTEGER NOT NULL, " +
                "`internalConversationId` TEXT NOT NULL, " +
                "`actorDisplayName` TEXT NOT NULL, " +
                "`message` TEXT NOT NULL, " +
                "`actorId` TEXT NOT NULL, " +
                "`actorType` TEXT NOT NULL, " +
                "`deleted` INTEGER NOT NULL, " +
                "`expirationTimestamp` INTEGER NOT NULL, " +
                "`isReplyable` INTEGER NOT NULL, " +
                "`lastEditActorDisplayName` TEXT, " +
                "`lastEditActorId` TEXT, " +
                "`lastEditActorType` TEXT, " +
                "`lastEditTimestamp` INTEGER, " +
                "`markdown` INTEGER, " +
                "`messageParameters` TEXT, " +
                "`messageType` TEXT NOT NULL, " +
                "`parent` INTEGER, " +
                "`reactions` TEXT, " +
                "`reactionsSelf` TEXT, " +
                "`systemMessage` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "PRIMARY KEY(`internalId`), " +
                "FOREIGN KEY(`internalConversationId`) REFERENCES `Conversations`(`internalId`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE " +
                ")"
        )

        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_ChatMessages_internalId` " +
                "ON `ChatMessages` (`internalId`)"
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ChatMessages_internalConversationId` " +
                "ON `ChatMessages` (`internalConversationId`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS ChatBlocks (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`internalConversationId` TEXT NOT NULL, " +
                "`accountId` INTEGER, `token` TEXT, " +
                "`oldestMessageId` INTEGER NOT NULL, " +
                "`newestMessageId` INTEGER NOT NULL, " +
                "`hasHistory` INTEGER NOT NULL, " +
                "FOREIGN KEY(`internalConversationId`) REFERENCES `Conversations`(`internalId`) " +
                "ON UPDATE CASCADE ON DELETE CASCADE " +
                ")"
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ChatBlocks_internalConversationId` " +
                "ON `ChatBlocks` (`internalConversationId`)"
        )
    }

    fun addArchiveConversations(db: SupportSQLiteDatabase) {
        try {
            db.execSQL(
                "ALTER TABLE Conversations " +
                    "ADD `hasArchived` INTEGER;"
            )
        } catch (e: SQLException) {
            Log.i("Migrations", "hasArchived already exists")
        }
    }
}
