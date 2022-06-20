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
import com.nextcloud.talk.data.user.model.UserNgEntity
import com.nextcloud.talk.data.user.model.User

@Suppress("TooManyFunctions")
interface UsersRepository {
    fun getActiveUserLiveData(): LiveData<UserNgEntity?>
    fun getActiveUser(): UserNgEntity?
    fun getUsers(): List<UserNgEntity>
    fun getUserWithId(id: Long): UserNgEntity?
    fun getUserWithIdLiveData(id: Long): LiveData<UserNgEntity?>
    fun getUserWithIdNotScheduledForDeletion(id: Long): UserNgEntity?
    fun getUserWithUserId(userId: String): UserNgEntity?
    fun getUsersWithoutUserId(userId: Long): List<UserNgEntity>
    fun getUsersLiveData(): LiveData<List<User>>
    fun getUsersLiveDataWithoutActive(): LiveData<List<User>>
    fun getUsersScheduledForDeletion(): List<UserNgEntity>
    fun getUsersNotScheduledForDeletion(): List<UserNgEntity>
    suspend fun getUserWithUsernameAndServer(username: String, server: String): UserNgEntity?
    suspend fun updateUser(user: UserNgEntity): Int
    suspend fun insertUser(user: UserNgEntity): Long
    suspend fun setUserAsActiveWithId(id: Long): Boolean
    suspend fun deleteUserWithId(id: Long)
    suspend fun setAnyUserAsActive(): Boolean
    suspend fun markUserForDeletion(id: Long): Boolean
}
