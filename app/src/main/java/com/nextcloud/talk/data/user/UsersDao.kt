/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.data.user

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nextcloud.talk.data.user.model.UserEntity
import io.reactivex.Maybe
import io.reactivex.Single
import java.lang.Boolean.FALSE
import java.lang.Boolean.TRUE

@Dao
@Suppress("TooManyFunctions")
abstract class UsersDao {
    // get active user
    @Query("SELECT * FROM User where current = 1")
    abstract fun getActiveUser(): Maybe<UserEntity>

    @Query("SELECT * FROM User where current = 1")
    abstract fun getActiveUserSynchronously(): UserEntity?

    @Query("DELETE FROM User WHERE id = :id")
    abstract fun deleteUserWithId(id: Long)

    @Update
    abstract fun updateUser(user: UserEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveUser(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveUsers(vararg users: UserEntity): List<Long>

    // get all users not scheduled for deletion
    @Query("SELECT * FROM User where scheduledForDeletion != 1")
    abstract fun getUsers(): Single<List<UserEntity>>

    @Query("SELECT * FROM User where id = :id")
    abstract fun getUserWithId(id: Long): Maybe<UserEntity>

    @Query("SELECT * FROM User where id = :id AND scheduledForDeletion != 1")
    abstract fun getUserWithIdNotScheduledForDeletion(id: Long): Maybe<UserEntity>

    @Query("SELECT * FROM User where userId = :userId")
    abstract fun getUserWithUserId(userId: String): Maybe<UserEntity>

    @Query("SELECT * FROM User where userId != :userId")
    abstract fun getUsersWithoutUserId(userId: Long): Single<List<UserEntity>>

    @Query("SELECT * FROM User where scheduledForDeletion = 1")
    abstract fun getUsersScheduledForDeletion(): Single<List<UserEntity>>

    @Query("SELECT * FROM User where scheduledForDeletion = 0")
    abstract fun getUsersNotScheduledForDeletion(): Single<List<UserEntity>>

    @Query("SELECT * FROM User WHERE username = :username AND baseUrl = :server")
    abstract fun getUserWithUsernameAndServer(username: String, server: String): Maybe<UserEntity>

    @Transaction
    open fun setUserAsActiveWithId(id: Long): Single<Boolean> {
        return getUsers()
            .map { users ->
                users.forEach { user ->
                    user.current = user.id == id
                    updateUser(user)
                }
                true
            }
            .onErrorReturn { e ->
                Log.e(TAG, "Error setting user active", e)
                false
            }
    }

    @Transaction
    open suspend fun markUserForDeletion(id: Long): Boolean {
        getUserWithId(id).blockingGet()?.let { user ->
            user.current = FALSE
            updateUser(user)
        }

        return setAnyUserAsActive()
    }

    @Transaction
    open suspend fun setAnyUserAsActive(): Boolean {
        val users = getUsers().blockingGet()

        val result = users.firstOrNull()?.let { user ->
            user.current = TRUE
            updateUser(user)
            TRUE
        } ?: FALSE

        return result
    }

    companion object {
        const val TAG = "UsersDao"
    }
}
