/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2020 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.user

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.push.PushConfigurationState
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface UsersRepository {
    suspend fun getActiveUser(): User?
    fun getActiveUserFlow(): Flow<User?>
    suspend fun getUsers(): List<User>
    suspend fun getUserWithId(id: Long): User?
    suspend fun getUserWithIdNotScheduledForDeletion(id: Long): User?
    suspend fun getUserWithUserId(userId: String): User?
    suspend fun getUsersScheduledForDeletion(): List<User>
    suspend fun getUsersNotScheduledForDeletion(): List<User>
    suspend fun getUserWithUsernameAndServer(username: String, server: String): User?
    suspend fun updateUser(user: User): Int
    suspend fun insertUser(user: User): Long
    suspend fun setUserAsActiveWithId(id: Long): Boolean
    suspend fun deleteUser(user: User): Int
    suspend fun updatePushState(id: Long, state: PushConfigurationState): Int
}
