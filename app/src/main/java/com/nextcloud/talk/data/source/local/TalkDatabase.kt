/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2020 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
import com.nextcloud.talk.data.source.local.converters.ServerVersionConverter
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
import androidx.room.AutoMigration

@Database(
    entities = [UserEntity::class, ArbitraryStorageEntity::class],
    version = 10,
    autoMigrations = [
        AutoMigration(from = 9, to = 10)
    ],
    exportSchema = true
)
@TypeConverters(
    PushConfigurationConverter::class,
    CapabilitiesConverter::class,
    ServerVersionConverter::class,
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
        private var instance: TalkDatabase? = null

        @JvmStatic
        fun getInstance(context: Context, appPreferences: AppPreferences): TalkDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context, appPreferences).also { instance = it }
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
                .addMigrations(Migrations.MIGRATION_6_8, Migrations.MIGRATION_7_8, Migrations.MIGRATION_8_9)
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
