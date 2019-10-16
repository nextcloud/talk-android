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
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.requery.Persistable
import io.requery.android.sqlcipher.SqlCipherDatabaseSource
import io.requery.reactivex.ReactiveEntityStore
import io.requery.reactivex.ReactiveSupport
import io.requery.sql.EntityDataStore
import net.orange_box.storebox.StoreBox
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val StorageModule = module {
  single { createPreferences(androidContext()) }
  single { createSqlCipherDatabaseSource(androidContext()) }
  single { createDataStore(get()) }
  single { createUserUtils(get()) }
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

fun createUserUtils(dataStore : ReactiveEntityStore<Persistable>) : UserUtils {
  return UserUtils(dataStore)
}