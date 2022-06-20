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
import kotlinx.coroutines.flow.distinctUntilChanged

@Suppress("TooManyFunctions")
class UsersRepositoryImpl(private val usersDao: UsersDao) : UsersRepository {
    override fun getActiveUserLiveData(): Flow<UserNgEntity?> {
        return usersDao.getActiveUserLiveData().distinctUntilChanged()
    }

    override fun getActiveUser(): Flow<UserNgEntity?> {
        return usersDao.getActiveUser()
    }

    override fun getUsers(): Flow<List<UserNgEntity>> {
        return usersDao.getUsers()
    }

    override fun getUserWithId(id: Long): Flow<UserNgEntity?> {
        return usersDao.getUserWithId(id)
    }

    override fun getUserWithIdLiveData(id: Long): Flow<UserNgEntity?> {
        return usersDao.getUserWithIdLiveData(id).distinctUntilChanged()
    }

    override fun getUserWithIdNotScheduledForDeletion(id: Long): Flow<UserNgEntity?> {
        return usersDao.getUserWithIdNotScheduledForDeletion(id)
    }

    override fun getUserWithUserId(userId: String): Flow<UserNgEntity?> {
        return usersDao.getUserWithUserId(userId)
    }

    override fun getUsersWithoutUserId(userId: Long): Flow<List<UserNgEntity>> {
        return usersDao.getUsersWithoutUserId(userId)
    }

    override fun getUsersLiveData(): Flow<List<UserNgEntity>> {
        return usersDao.getUsersLiveData().distinctUntilChanged()
    }

    override fun getUsersLiveDataWithoutActive(): Flow<List<UserNgEntity>> {
        return usersDao.getUsersLiveDataWithoutActive().distinctUntilChanged()
    }

    override fun getUsersScheduledForDeletion(): Flow<List<UserNgEntity>> {
        return usersDao.getUsersScheduledForDeletion()
    }

    override fun getUsersNotScheduledForDeletion(): Flow<List<UserNgEntity>> {
        return usersDao.getUsersNotScheduledForDeletion()
    }

    override fun getUserWithUsernameAndServer(username: String, server: String): Flow<UserNgEntity?> {
        return usersDao.getUserWithUsernameAndServer(username, server)
    }

    override suspend fun updateUser(user: UserNgEntity): Int {
        return usersDao.updateUser(user)
    }

    override suspend fun insertUser(user: UserNgEntity): Long {
        return usersDao.saveUser(user)
    }

    override suspend fun setUserAsActiveWithId(id: Long): Flow<Boolean> {
        return usersDao.setUserAsActiveWithId(id)
    }

    override suspend fun deleteUserWithId(id: Long) {
        usersDao.deleteUserWithId(id)
    }

    override suspend fun setAnyUserAsActive(): Flow<Boolean> {
        return usersDao.setAnyUserAsActive()
    }

    override suspend fun markUserForDeletion(id: Long): Flow<Boolean> {
        return usersDao.markUserForDeletion(id)
    }
}
