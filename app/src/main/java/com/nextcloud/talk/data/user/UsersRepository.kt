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

import com.nextcloud.talk.data.user.model.UserNgEntity
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface UsersRepository {
    fun getActiveUserLiveData(): Flow<UserNgEntity?>
    fun getActiveUser(): Flow<UserNgEntity?>
    fun getUsers(): Flow<List<UserNgEntity>>
    fun getUserWithId(id: Long): Flow<UserNgEntity?>
    fun getUserWithIdLiveData(id: Long): Flow<UserNgEntity?>
    fun getUserWithIdNotScheduledForDeletion(id: Long): Flow<UserNgEntity?>
    fun getUserWithUserId(userId: String): Flow<UserNgEntity?>
    fun getUsersWithoutUserId(userId: Long): Flow<List<UserNgEntity>>
    fun getUsersLiveData(): Flow<List<UserNgEntity>>
    fun getUsersLiveDataWithoutActive(): Flow<List<UserNgEntity>>
    fun getUsersScheduledForDeletion(): Flow<List<UserNgEntity>>
    fun getUsersNotScheduledForDeletion(): Flow<List<UserNgEntity>>
    fun getUserWithUsernameAndServer(username: String, server: String): Flow<UserNgEntity?>
    suspend fun updateUser(user: UserNgEntity): Int
    suspend fun insertUser(user: UserNgEntity): Long
    suspend fun setUserAsActiveWithId(id: Long): Flow<Boolean>
    suspend fun deleteUserWithId(id: Long)
    suspend fun setAnyUserAsActive(): Flow<Boolean>
    suspend fun markUserForDeletion(id: Long): Flow<Boolean>
}
