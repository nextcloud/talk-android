/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2020 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.user

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.models.json.push.PushConfigurationState
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

    @Query(
        "UPDATE User SET current = CASE " +
            "WHEN id == :id THEN 1 " +
            "WHEN id != :id THEN 0 " +
            "END"
    )
    abstract fun setUserAsActiveWithId(id: Long): Int

    @Query("Update User SET pushConfigurationState = :state WHERE id == :id")
    abstract fun updatePushState(id: Long, state: PushConfigurationState): Single<Int>

    companion object {
        const val TAG = "UsersDao"
    }
}
