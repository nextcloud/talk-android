/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <infoi@andy-scherzinger.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.data.source.local

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Suppress("MagicNumber")
object Migrations {
    val MIGRATION_6_8 = object : Migration(6, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i("Migrations", "Migrating 6 to 8")
            migrateToRoom(database)
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i("Migrations", "Migrating 7 to 8")
            migrateToRoom(database)
        }
    }

    fun migrateToRoom(database: SupportSQLiteDatabase) {
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
