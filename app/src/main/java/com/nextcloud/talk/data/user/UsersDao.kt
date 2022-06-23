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
import com.nextcloud.talk.data.user.model.UserNgEntity
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.disposables.Disposable
import java.lang.Boolean.FALSE
import java.lang.Boolean.TRUE

@Dao
@Suppress("TooManyFunctions")
abstract class UsersDao {
    // get active user
    @Query("SELECT * FROM User where current = 1")
    abstract fun getActiveUser(): Single<UserNgEntity?>

    @Query("SELECT * FROM User where current = 1")
    abstract fun getActiveUserSynchronously(): UserNgEntity?

    @Query("SELECT * FROM User WHERE current = 1")
    abstract fun getActiveUserLiveData(): Single<UserNgEntity?>

    @Query("DELETE FROM User WHERE id = :id")
    abstract fun deleteUserWithId(id: Long)

    @Update
    abstract fun updateUser(user: UserNgEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveUser(user: UserNgEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun saveUsers(vararg users: UserNgEntity): List<Long>

    // get all users not scheduled for deletion
    @Query("SELECT * FROM User where current != 0")
    abstract fun getUsers(): Single<List<UserNgEntity>>

    @Query("SELECT * FROM User where id = :id")
    abstract fun getUserWithId(id: Long): Single<UserNgEntity?>

    @Query("SELECT * FROM User where id = :id AND scheduledForDeletion != 1")
    abstract fun getUserWithIdNotScheduledForDeletion(id: Long): Single<UserNgEntity?>

    @Query("SELECT * FROM User where userId = :userId")
    abstract fun getUserWithUserId(userId: String): Single<UserNgEntity?>

    @Query("SELECT * FROM User where userId != :userId")
    abstract fun getUsersWithoutUserId(userId: Long): Single<List<UserNgEntity>>

    @Query("SELECT * FROM User where current = 0")
    abstract fun getUsersScheduledForDeletion(): Single<List<UserNgEntity>>

    @Query("SELECT * FROM User where scheduledForDeletion = 0")
    abstract fun getUsersNotScheduledForDeletion(): Single<List<UserNgEntity>>

    @Query("SELECT * FROM User WHERE username = :username AND baseUrl = :server")
    abstract fun getUserWithUsernameAndServer(username: String, server: String): Single<UserNgEntity?>

    @Transaction
    open suspend fun setUserAsActiveWithId(id: Long): Boolean {
        val users = getUsers()
        var result = TRUE

        users.subscribe(object : SingleObserver<List<UserNgEntity>> {
            override fun onSuccess(users: List<UserNgEntity>) {
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
            }

            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onError(e: Throwable) {
                Log.e(TAG, "Error setting user active", e)
                result = FALSE
            }
        })

        return result
    }

    @Transaction
    open suspend fun markUserForDeletion(id: Long): Boolean {
        val users = getUsers().blockingGet()

        for (user in users) {
            if (user.id == id) {
                user.current = FALSE
                updateUser(user)
            }
        }

        return setAnyUserAsActive()
    }

    @Transaction
    open suspend fun setAnyUserAsActive(): Boolean {
        val users = getUsers().blockingGet()
        var result = FALSE

        for (user in users) {
            user.current = TRUE
            updateUser(user)
            result = TRUE
            break
        }

        return result
    }

    companion object {
        const val TAG = "UsersDao"
    }
}
