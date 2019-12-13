/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.newarch.di.module

import android.content.Context
import com.nextcloud.talk.R.string
import com.nextcloud.talk.models.database.Models
import com.nextcloud.talk.newarch.data.repository.offline.ConversationsRepositoryImpl
import com.nextcloud.talk.newarch.data.repository.offline.MessagesRepositoryImpl
import com.nextcloud.talk.newarch.data.repository.offline.UsersRepositoryImpl
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.MessagesRepository
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.dao.ConversationsDao
import com.nextcloud.talk.newarch.local.dao.MessagesDao
import com.nextcloud.talk.newarch.local.dao.UsersDao
import com.nextcloud.talk.newarch.local.db.TalkDatabase
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageUtils
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.requery.Persistable
import io.requery.android.sqlcipher.SqlCipherDatabaseSource
import io.requery.reactivex.ReactiveEntityStore
import io.requery.reactivex.ReactiveSupport
import io.requery.sql.EntityDataStore
import net.orange_box.storebox.StoreBox
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val StorageModule = module {
  single { createPreferences(androidContext()) }
  single { createSqlCipherDatabaseSource(androidContext()) }
  single { createDataStore(get()) }
  single { createConversationsRepository(get()) }
  single { createMessagesRepository(get()) }
  single { createUsersRepository(get()) }
  single { createArbitraryStorageUtils(get()) }
  single { createUserUtils(get()) }
  single { TalkDatabase.getInstance(androidApplication()) }
  single { get<TalkDatabase>().conversationsDao() }
  single { get<TalkDatabase>().messagesDao() }
  single { get<TalkDatabase>().usersDao() }

}

fun createConversationsRepository(conversationsDao: ConversationsDao): ConversationsRepository {
  return ConversationsRepositoryImpl(conversationsDao)
}

fun createMessagesRepository(messagesDao: MessagesDao): MessagesRepository {
  return MessagesRepositoryImpl(messagesDao)
}

fun createUsersRepository(usersDao: UsersDao): UsersRepository {
  return UsersRepositoryImpl(usersDao)
}

fun createPreferences(context: Context): AppPreferences {
  return StoreBox.create<AppPreferences>(context, AppPreferences::class.java)
}

fun createSqlCipherDatabaseSource(context: Context): SqlCipherDatabaseSource {
  return SqlCipherDatabaseSource(context, Models.DEFAULT,
      context.resources.getString(string.nc_app_name).toLowerCase()
          .replace(" ", "_").trim { it <= ' ' } + ".sqlite",
      context.getString(string.nc_talk_database_encryption_key), 6)
}

fun createDataStore(sqlCipherDatabaseSource: SqlCipherDatabaseSource): ReactiveEntityStore<Persistable> {
  val configuration = sqlCipherDatabaseSource.configuration
  return ReactiveSupport.toReactiveStore(EntityDataStore(configuration))
}

fun createArbitraryStorageUtils(dataStore: ReactiveEntityStore<Persistable>): ArbitraryStorageUtils {
  return ArbitraryStorageUtils(dataStore)
}

fun createUserUtils(dataStore: ReactiveEntityStore<Persistable>): UserUtils {
  return UserUtils(dataStore)
}