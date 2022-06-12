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
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nextcloud.talk.data.source.local.converters.CapabilitiesConverter
import com.nextcloud.talk.data.source.local.converters.HashMapHashMapConverter
import com.nextcloud.talk.data.source.local.converters.PushConfigurationConverter
import com.nextcloud.talk.data.source.local.converters.SignalingSettingsConverter
import com.nextcloud.talk.data.user.UsersDao
import com.nextcloud.talk.data.user.model.UserNgEntity

@Database(
    entities = [UserNgEntity::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(
    PushConfigurationConverter::class,
    CapabilitiesConverter::class,
    SignalingSettingsConverter::class,
    HashMapHashMapConverter::class
)

abstract class TalkDatabase : RoomDatabase() {

    abstract fun usersDao(): UsersDao

    companion object {
        private const val DB_NAME = "talk.db"

        @Volatile
        private var INSTANCE: TalkDatabase? = null

        fun getInstance(context: Context): TalkDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context).also { INSTANCE = it }
            }

        private fun build(context: Context) =
            Room.databaseBuilder(context.applicationContext, TalkDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA defer_foreign_keys = 1")
                    }
                })
                .build()
    }
}
