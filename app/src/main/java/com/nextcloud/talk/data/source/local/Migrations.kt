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
}
