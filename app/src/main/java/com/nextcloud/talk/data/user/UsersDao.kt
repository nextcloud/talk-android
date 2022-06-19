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

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nextcloud.talk.data.user.model.UserNgEntity
import java.lang.Boolean.FALSE
import java.lang.Boolean.TRUE

@Dao
abstract class UsersDao {
    // get active user
    @Query("SELECT * FROM User where current = 1")
    abstract fun getActiveUser(): UserNgEntity?

    @Query("SELECT * FROM User WHERE current = 1")
    abstract fun getActiveUserLiveData(): LiveData<UserNgEntity?>

    @Query("SELECT * FROM User ORDER BY current DESC")
    abstract fun getUsersLiveData(): LiveData<List<UserNgEntity>>

    @Query("SELECT * FROM User WHERE current != 1 ORDER BY current DESC")
    abstract fun getUsersLiveDataWithoutActive(): LiveData<List<UserNgEntity>>

    @Query("DELETE FROM User WHERE id = :id")
    abstract suspend fun deleteUserWithId(id: Long)

    @Update
    abstract suspend fun updateUser(user: UserNgEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveUser(user: UserNgEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveUsers(vararg users: UserNgEntity): List<Long>

    // get all users not scheduled for deletion
    @Query("SELECT * FROM User where current != 0")
    abstract fun getUsers(): List<UserNgEntity>

    @Query("SELECT * FROM User where id = :id")
    abstract fun getUserWithId(id: Long): UserNgEntity?

    @Query("SELECT * FROM User where id = :id")
    abstract fun getUserWithIdLiveData(id: Long): LiveData<UserNgEntity?>

    @Query("SELECT * FROM User where id = :id AND scheduledForDeletion != 1")
    abstract fun getUserWithIdNotScheduledForDeletion(id: Long): UserNgEntity?

    @Query("SELECT * FROM User where userId = :userId")
    abstract fun getUserWithUserId(userId: String): UserNgEntity?

    @Query("SELECT * FROM User where userId != :userId")
    abstract fun getUsersWithoutUserId(userId: Long): List<UserNgEntity>

    @Query("SELECT * FROM User where current = 0")
    abstract fun getUsersScheduledForDeletion(): List<UserNgEntity>

    @Query("SELECT * FROM User where scheduledForDeletion = 0")
    abstract fun getUsersNotScheduledForDeletion(): List<UserNgEntity>

    @Query("SELECT * FROM User WHERE username = :username AND baseUrl = :server")
    abstract suspend fun getUserWithUsernameAndServer(username: String, server: String): UserNgEntity?

    @Transaction
    open suspend fun setUserAsActiveWithId(id: Long) : Boolean {
        val users = getUsers()
        for (user in users) {
            // removed from clause: && UserStatus.ACTIVE == user.status
            if (user.id != id) {
                user.current = TRUE
                updateUser(user)
            } // removed from clause: && UserStatus.ACTIVE != user.status
            else if (user.id == id) {
                user.current = TRUE
                updateUser(user)
            }
        }

        return true
    }

    @Transaction
    open suspend fun markUserForDeletion(id: Long): Boolean {
        val users = getUsers()
        for (user in users) {
            if (user.id == id) {
                // TODO currently we only have a boolean, no intermediate states
                user.current = FALSE
                updateUser(user)
                break
            }
        }

        return setAnyUserAsActive()
    }
    
    @Transaction
    open suspend fun setAnyUserAsActive(): Boolean {
        val users = getUsers()
        for (user in users) {
            user.current = TRUE
            updateUser(user)
            return true
        }

        return false
    }
}
