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
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
interface UsersDao {
    // get active user. ORDER BY/LIMIT make this deterministic if more than one row is ever
    // marked current=1 (e.g. a duplicate-account row left over from a past bug), instead of
    // relying on whatever order an unordered full-table scan happens to return.
    @Query("SELECT * FROM User where current = 1 ORDER BY id DESC LIMIT 1")
    suspend fun getActiveUser(): UserEntity?

    // get active user
    @Query("SELECT * FROM User where current = 1 ORDER BY id DESC LIMIT 1")
    fun getActiveUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM User where current = 1 ORDER BY id DESC LIMIT 1")
    fun getActiveUserSynchronously(): UserEntity?

    @Delete
    suspend fun deleteUser(user: UserEntity): Int

    @Update
    suspend fun updateUser(user: UserEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUsers(vararg users: UserEntity): List<Long>

    // get all users not scheduled for deletion
    @Query("SELECT * FROM User where scheduledForDeletion != 1")
    suspend fun getUsers(): List<UserEntity>

    @Query("SELECT * FROM User where id = :id")
    suspend fun getUserWithId(id: Long): UserEntity?

    @Query("SELECT * FROM User where id = :id AND scheduledForDeletion != 1")
    suspend fun getUserWithIdNotScheduledForDeletion(id: Long): UserEntity?

    @Query("SELECT * FROM User where userId = :userId")
    suspend fun getUserWithUserId(userId: String): UserEntity?

    @Query("SELECT * FROM User where scheduledForDeletion = 1")
    suspend fun getUsersScheduledForDeletion(): List<UserEntity>

    @Query("SELECT * FROM User where scheduledForDeletion = 0")
    suspend fun getUsersNotScheduledForDeletion(): List<UserEntity>

    @Query("SELECT * FROM User WHERE username = :username AND baseUrl = :server")
    suspend fun getUserWithUsernameAndServer(username: String, server: String): UserEntity?

    @Query(
        "UPDATE User SET current = CASE " +
            "WHEN id == :id THEN 1 " +
            "WHEN id != :id THEN 0 " +
            "END"
    )
    suspend fun setUserAsActiveWithId(id: Long): Int

    @Query("Update User SET pushConfigurationState = :state WHERE id == :id")
    suspend fun updatePushState(id: Long, state: PushConfigurationState): Int

    companion object {
        const val TAG = "UsersDao"
    }
}
