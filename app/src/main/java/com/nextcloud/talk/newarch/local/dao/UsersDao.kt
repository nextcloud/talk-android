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

package com.nextcloud.talk.newarch.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nextcloud.talk.newarch.local.models.UserNgEntity

@Dao
abstract class UsersDao {
  @Query("DELETE FROM users WHERE id = :userId")
  abstract fun deleteUserForId(userId: Long)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract fun saveUser(user: UserNgEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  abstract suspend fun saveUsers(vararg users: UserNgEntity)

  // get all users not scheduled for deletion
  @Query("SELECT * FROM users where status != 2")
  abstract fun getUsers(): List<UserNgEntity>

  @Query("SELECT * FROM users where status = 2")
  abstract fun getUsersScheduledForDeletion(): List<UserNgEntity>

}