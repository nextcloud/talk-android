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
import com.nextcloud.talk.data.user.model.UserCapabilitiesUpdate
import com.nextcloud.talk.data.user.model.UserClientCertificateUpdate
import com.nextcloud.talk.data.user.model.UserCredentialsUpdate
import com.nextcloud.talk.data.user.model.UserDisplayNameUpdate
import com.nextcloud.talk.data.user.model.UserEntity
import com.nextcloud.talk.data.user.model.UserExternalSignalingServerUpdate
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

    @Query("SELECT * FROM User where id = :id")
    fun getUserWithIdFlow(id: Long): Flow<UserEntity?>

    // If several users are marked as active, keeps only the one with the highest id, matching getActiveUser().
    // A single statement, so it cannot interleave with setUserAsActiveWithId(). SQLite evaluates the uncorrelated
    // subqueries once, before any row is changed.
    @Query(
        "UPDATE User SET current = CASE WHEN id = (SELECT MAX(id) FROM User WHERE current = 1) THEN 1 ELSE 0 END " +
            "WHERE (SELECT COUNT(*) FROM User WHERE current = 1) > 1"
    )
    suspend fun repairMultipleActiveUsers(): Int

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

    // The following updates only write the columns of the given partial entity. Writing back a whole user row that
    // was read earlier (e.g. before a network request) would reset columns that changed in the meantime, like
    // "current".

    @Update(entity = UserEntity::class)
    suspend fun updateCapabilities(update: UserCapabilitiesUpdate): Int

    @Update(entity = UserEntity::class)
    suspend fun updateExternalSignalingServer(update: UserExternalSignalingServerUpdate): Int

    @Update(entity = UserEntity::class)
    suspend fun updateDisplayName(update: UserDisplayNameUpdate): Int

    @Update(entity = UserEntity::class)
    suspend fun updateClientCertificate(update: UserClientCertificateUpdate): Int

    @Update(entity = UserEntity::class)
    suspend fun updateCredentials(update: UserCredentialsUpdate): Int

    companion object {
        const val TAG = "UsersDao"
    }
}
