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
import io.reactivex.Observable

@Suppress("TooManyFunctions")
class UsersRepositoryImpl(private val usersDao: UsersDao) : UsersRepository {
    override fun getActiveUserLiveData(): Observable<UserNgEntity?> {
        return usersDao.getActiveUserLiveData()
    }

    override fun getActiveUser(): Observable<UserNgEntity?> {
        return usersDao.getActiveUser()
    }

    override fun getActiveUserSynchronously(): UserNgEntity? {
        return usersDao.getActiveUserSynchronously()
    }

    override fun getUsers(): Observable<List<UserNgEntity>> {
        return usersDao.getUsers()
    }

    override fun getUserWithId(id: Long): Observable<UserNgEntity?> {
        return usersDao.getUserWithId(id)
    }

    override fun getUserWithIdLiveData(id: Long): Observable<UserNgEntity?> {
        return usersDao.getUserWithIdLiveData(id).distinctUntilChanged()
    }

    override fun getUserWithIdNotScheduledForDeletion(id: Long): Observable<UserNgEntity?> {
        return usersDao.getUserWithIdNotScheduledForDeletion(id)
    }

    override fun getUserWithUserId(userId: String): Observable<UserNgEntity?> {
        return usersDao.getUserWithUserId(userId)
    }

    override fun getUsersWithoutUserId(userId: Long): Observable<List<UserNgEntity>> {
        return usersDao.getUsersWithoutUserId(userId)
    }

    override fun getUsersLiveData(): Observable<List<UserNgEntity>> {
        return usersDao.getUsersLiveData().distinctUntilChanged()
    }

    override fun getUsersLiveDataWithoutActive(): Observable<List<UserNgEntity>> {
        return usersDao.getUsersLiveDataWithoutActive().distinctUntilChanged()
    }

    override fun getUsersScheduledForDeletion(): Observable<List<UserNgEntity>> {
        return usersDao.getUsersScheduledForDeletion()
    }

    override fun getUsersNotScheduledForDeletion(): Observable<List<UserNgEntity>> {
        return usersDao.getUsersNotScheduledForDeletion()
    }

    override fun getUserWithUsernameAndServer(username: String, server: String): Observable<UserNgEntity?> {
        return usersDao.getUserWithUsernameAndServer(username, server)
    }

    override fun updateUser(user: UserNgEntity): Int {
        return usersDao.updateUser(user)
    }

    override fun insertUser(user: UserNgEntity): Long {
        return usersDao.saveUser(user)
    }

    override suspend fun setUserAsActiveWithId(id: Long): Boolean {
        return usersDao.setUserAsActiveWithId(id)
    }

    override fun deleteUserWithId(id: Long) {
        usersDao.deleteUserWithId(id)
    }

    override suspend fun setAnyUserAsActive(): Boolean {
        return usersDao.setAnyUserAsActive()
    }

    override suspend fun markUserForDeletion(id: Long): Boolean {
        return usersDao.markUserForDeletion(id)
    }
}
