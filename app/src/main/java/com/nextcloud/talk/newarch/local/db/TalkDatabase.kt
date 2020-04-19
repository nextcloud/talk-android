/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.nextcloud.talk.newarch.local.converters.*
import com.nextcloud.talk.newarch.local.dao.ConversationsDao
import com.nextcloud.talk.newarch.local.dao.MessagesDao
import com.nextcloud.talk.newarch.local.dao.UsersDao
import com.nextcloud.talk.newarch.local.models.ConversationEntity
import com.nextcloud.talk.newarch.local.models.MessageEntity
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import org.parceler.converter.HashMapParcelConverter

@Database(
        entities = [ConversationEntity::class, MessageEntity::class, UserNgEntity::class],
        version = 1,
        exportSchema = true
)
@TypeConverters(
        ChatMessageConverter::class, LobbyStateConverter::class,
        ConversationReadOnlyStateConverter::class, NotificationLevelConverter::class,
        ConversationTypeConverter::class, ParticipantTypeConverter::class,
        PushConfigurationConverter::class, CapabilitiesConverter::class,
        SignalingSettingsConverter::class,
        UserStatusConverter::class, SystemMessageTypeConverter::class, ParticipantMapConverter::class,
        HashMapHashMapConverter::class, ChatMessageStatusConverter::class
)

abstract class TalkDatabase : RoomDatabase() {

    abstract fun conversationsDao(): ConversationsDao
    abstract fun messagesDao(): MessagesDao
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