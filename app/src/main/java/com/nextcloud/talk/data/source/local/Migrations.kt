package com.nextcloud.talk.data.source.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create the new tables
            database.execSQL(
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
            database.execSQL(
                "CREATE TABLE ArbitraryStorage_new (" +
                    "accountIdentifier INTEGER NOT NULL, " +
                    "\"key\" TEXT, " +
                    "object TEXT, " +
                    "value TEXT, " +
                    "PRIMARY KEY(accountIdentifier)" +
                    ")"
            )
            // Copy the data
            database.execSQL(
                "INSERT INTO User_new (" +
                    "id, userId, username, baseUrl, token, displayName, pushConfigurationState, capabilities, " +
                    "clientCertificate, externalSignalingServer, current, scheduledForDeletion) " +
                    "SELECT " +
                    "id, userId, username, baseUrl, token, displayName, pushConfigurationState, capabilities, " +
                    "clientCertificate, externalSignalingServer, current, scheduledForDeletion " +
                    "FROM User"
            )
            database.execSQL(
                "INSERT INTO ArbitraryStorage_new (" +
                    "accountIdentifier, \"key\", object, value) " +
                    "SELECT " +
                    "accountIdentifier, \"key\", object, value " +
                    "FROM ArbitraryStorage"
            )
            // Remove the old table
            database.execSQL("DROP TABLE User")
            database.execSQL("DROP TABLE ArbitraryStorage")

            // Change the table name to the correct one
            database.execSQL("ALTER TABLE User_new RENAME TO User")
            database.execSQL("ALTER TABLE ArbitraryStorage_new RENAME TO ArbitraryStorage")
        }
    }
}