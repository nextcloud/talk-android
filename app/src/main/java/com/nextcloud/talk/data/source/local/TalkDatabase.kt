/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023-2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2020 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.source.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nextcloud.talk.R
import com.nextcloud.talk.data.database.dao.ChatBlocksDao
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.data.database.dao.ConversationsDao
import com.nextcloud.talk.data.database.model.ChatBlockEntity
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.data.source.local.Migrations.AutoMigration16To17
import com.nextcloud.talk.data.source.local.converters.ArrayListConverter
import com.nextcloud.talk.data.source.local.converters.CapabilitiesConverter
import com.nextcloud.talk.data.source.local.converters.ExternalSignalingServerConverter
import com.nextcloud.talk.data.source.local.converters.HashMapHashMapConverter
import com.nextcloud.talk.data.source.local.converters.LinkedHashMapConverter
import com.nextcloud.talk.data.source.local.converters.PushConfigurationConverter
import com.nextcloud.talk.data.source.local.converters.SendStatusConverter
import com.nextcloud.talk.data.source.local.converters.ServerVersionConverter
import com.nextcloud.talk.data.source.local.converters.SignalingSettingsConverter
import com.nextcloud.talk.data.storage.ArbitraryStoragesDao
import com.nextcloud.talk.data.storage.model.ArbitraryStorageEntity
import com.nextcloud.talk.data.user.UsersDao
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.models.MessageDraftConverter
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.util.Locale

@Database(
    entities = [
        UserEntity::class,
        ArbitraryStorageEntity::class,
        ConversationEntity::class,
        ChatMessageEntity::class,
        ChatBlockEntity::class
    ],
    version = 20,
    autoMigrations = [
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 16, to = 17, spec = AutoMigration16To17::class),
        AutoMigration(from = 19, to = 20)
    ],
    exportSchema = true
)
@TypeConverters(
    PushConfigurationConverter::class,
    CapabilitiesConverter::class,
    ServerVersionConverter::class,
    ExternalSignalingServerConverter::class,
    SignalingSettingsConverter::class,
    HashMapHashMapConverter::class,
    LinkedHashMapConverter::class,
    ArrayListConverter::class,
    SendStatusConverter::class,
    MessageDraftConverter::class
)
@Suppress("MagicNumber")
abstract class TalkDatabase : RoomDatabase() {
    abstract fun usersDao(): UsersDao
    abstract fun conversationsDao(): ConversationsDao
    abstract fun chatMessagesDao(): ChatMessagesDao
    abstract fun chatBlocksDao(): ChatBlocksDao
    abstract fun arbitraryStoragesDao(): ArbitraryStoragesDao

    companion object {
        const val TAG = "TalkDatabase"
        const val SQL_CIPHER_LIBRARY = "sqlcipher"

        @Volatile
        private var instance: TalkDatabase? = null

        @JvmStatic
        fun getInstance(context: Context): TalkDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        // If editing the migrations, please add a test case in MigrationsTest under androidTest/data
        val MIGRATIONS = arrayOf(
            Migrations.MIGRATION_6_8,
            Migrations.MIGRATION_7_8,
            Migrations.MIGRATION_8_9,
            Migrations.MIGRATION_10_11,
            Migrations.MIGRATION_11_12,
            Migrations.MIGRATION_12_13,
            Migrations.MIGRATION_13_14,
            Migrations.MIGRATION_14_15,
            Migrations.MIGRATION_15_16,
            Migrations.MIGRATION_17_19
        )

        @Suppress("SpreadOperator")
        private fun build(context: Context): TalkDatabase {
            val passCharArray = context.getString(R.string.nc_talk_database_encryption_key).toCharArray()
            val passphrase: ByteArray = getBytesFromChars(passCharArray)
            val factory = SupportOpenHelperFactory(passphrase)

            val dbName = context
                .resources
                .getString(R.string.nc_app_product_name)
                .lowercase(Locale.getDefault())
                .replace(" ", "_")
                .trim() +
                ".sqlite"

            System.loadLibrary(SQL_CIPHER_LIBRARY)

            return Room
                .databaseBuilder(context.applicationContext, TalkDatabase::class.java, dbName)
                // comment out openHelperFactory to view the database entries in Android Studio for debugging
                .openHelperFactory(factory)
                .fallbackToDestructiveMigrationFrom(true, 18)
                .addMigrations(*MIGRATIONS) // * converts migrations to vararg
                .allowMainThreadQueries()
                .addCallback(
                    object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA defer_foreign_keys = 1")
                        }
                    }
                )
                .build()
        }

        private fun getBytesFromChars(chars: CharArray): ByteArray = String(chars).toByteArray(Charsets.UTF_8)
    }
}
