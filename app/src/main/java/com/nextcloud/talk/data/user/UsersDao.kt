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
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nextcloud.talk.data.user.model.UserEntity
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single

@Dao
@Suppress("TooManyFunctions")
abstract class UsersDao {
    // get active user
    @Query("SELECT * FROM User where current = 1")
    abstract fun getActiveUser(): Maybe<UserEntity>

    // get active user
    @Query("SELECT * FROM User where current = 1")
    abstract fun getActiveUserObservable(): Observable<UserEntity>

    @Query("SELECT * FROM User where current = 1")
    abstract fun getActiveUserSynchronously(): UserEntity?

    @Delete
    abstract fun deleteUser(user: UserEntity): Int

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

    @Query("SELECT * FROM User where scheduledForDeletion = 1")
    abstract fun getUsersScheduledForDeletion(): Single<List<UserEntity>>

    @Query("SELECT * FROM User where scheduledForDeletion = 0")
    abstract fun getUsersNotScheduledForDeletion(): Single<List<UserEntity>>

    @Query("SELECT * FROM User WHERE username = :username AND baseUrl = :server")
    abstract fun getUserWithUsernameAndServer(username: String, server: String): Maybe<UserEntity>

    @Transaction
    @Suppress("Detekt.TooGenericExceptionCaught") // blockingGet() only throws RuntimeExceptions per rx docs
    open fun setUserAsActiveWithId(id: Long): Boolean {
        return try {
            getUsers().blockingGet().forEach { user ->
                user.current = user.id == id

                Log.d(TAG, "xxxxxxxxxxxx")
                Log.d(TAG, "setUserAsActiveWithId. user.username: " + user.username)
                Log.d(TAG, "setUserAsActiveWithId. user.id: " + user.id)
                Log.d(TAG, "setUserAsActiveWithId. user.current: " + user.current)
                Log.d(TAG, "xxxxxxxxxxxx")

                updateUser(user)
            }
            true
        } catch (e: RuntimeException) {
            Log.e(TAG, "Error setting user active", e)
            false
        }
    }

    companion object {
        const val TAG = "UsersDao"
    }
}
