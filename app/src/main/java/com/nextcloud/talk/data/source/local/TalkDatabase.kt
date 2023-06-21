/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
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

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nextcloud.talk.R
import com.nextcloud.talk.data.source.local.converters.CapabilitiesConverter
import com.nextcloud.talk.data.source.local.converters.ExternalSignalingServerConverter
import com.nextcloud.talk.data.source.local.converters.HashMapHashMapConverter
import com.nextcloud.talk.data.source.local.converters.PushConfigurationConverter
import com.nextcloud.talk.data.source.local.converters.SignalingSettingsConverter
import com.nextcloud.talk.data.storage.ArbitraryStoragesDao
import com.nextcloud.talk.data.storage.model.ArbitraryStorageEntity
import com.nextcloud.talk.data.user.UsersDao
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.utils.preferences.AppPreferences
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteDatabaseHook
import net.sqlcipher.database.SupportFactory
import java.util.Locale

@Database(
    entities = [UserEntity::class, ArbitraryStorageEntity::class],
    version = 8,
    exportSchema = true
)
@TypeConverters(
    PushConfigurationConverter::class,
    CapabilitiesConverter::class,
    ExternalSignalingServerConverter::class,
    SignalingSettingsConverter::class,
    HashMapHashMapConverter::class
)
abstract class TalkDatabase : RoomDatabase() {

    abstract fun usersDao(): UsersDao
    abstract fun arbitraryStoragesDao(): ArbitraryStoragesDao

    companion object {
        const val TAG = "TalkDatabase"

        @Volatile
        private var INSTANCE: TalkDatabase? = null

        @JvmStatic
        fun getInstance(context: Context, appPreferences: AppPreferences): TalkDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, appPreferences).also { INSTANCE = it }
            }

        private fun build(context: Context, appPreferences: AppPreferences): TalkDatabase {
            val passCharArray = context.getString(R.string.nc_talk_database_encryption_key).toCharArray()
            val passphrase: ByteArray = SQLiteDatabase.getBytes(passCharArray)

            val factory = if (appPreferences.isDbRoomMigrated) {
                Log.i(TAG, "No cipher migration needed")
                SupportFactory(passphrase)
            } else {
                Log.i(TAG, "Add cipher migration hook")
                SupportFactory(passphrase, getCipherMigrationHook())
            }

            val dbName = context
                .resources
                .getString(R.string.nc_app_product_name)
                .lowercase(Locale.getDefault())
                .replace(" ", "_")
                .trim { it <= ' ' } +
                ".sqlite"

            return Room
                .databaseBuilder(context.applicationContext, TalkDatabase::class.java, dbName)
                // comment out openHelperFactory to view the database entries in Android Studio for debugging
                .openHelperFactory(factory)
                .addMigrations(Migrations.MIGRATION_6_8, Migrations.MIGRATION_7_8)
                .allowMainThreadQueries()
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA defer_foreign_keys = 1")
                        }
                    }
                )
                .build()
        }

        private fun getCipherMigrationHook(): SQLiteDatabaseHook {
            return object : SQLiteDatabaseHook {
                override fun preKey(database: SQLiteDatabase) {
                    // unused atm
                }

                override fun postKey(database: SQLiteDatabase) {
                    Log.i(TAG, "DB cipher_migrate START")
                    database.rawExecSQL("PRAGMA cipher_migrate;")
                    Log.i(TAG, "DB cipher_migrate END")
                }
            }
        }
    }
}
