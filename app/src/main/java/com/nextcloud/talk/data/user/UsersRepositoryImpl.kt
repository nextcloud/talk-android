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

import com.nextcloud.talk.data.user.model.User
import io.reactivex.Maybe
import io.reactivex.Single

@Suppress("TooManyFunctions")
class UsersRepositoryImpl(private val usersDao: UsersDao) : UsersRepository {

    override fun getActiveUser(): Maybe<User> {
        return usersDao.getActiveUser().map { UserMapper.toModel(it) }
    }

    override fun getUsers(): Single<List<User>> {
        return usersDao.getUsers().map { UserMapper.toModel(it) }
    }

    override fun getUserWithId(id: Long): Maybe<User> {
        return usersDao.getUserWithId(id).map { UserMapper.toModel(it) }
    }

    override fun getUserWithIdNotScheduledForDeletion(id: Long): Maybe<User> {
        return usersDao.getUserWithIdNotScheduledForDeletion(id).map { UserMapper.toModel(it) }
    }

    override fun getUserWithUserId(userId: String): Maybe<User> {
        return usersDao.getUserWithUserId(userId).map { UserMapper.toModel(it) }
    }

    override fun getUsersWithoutUserId(userId: Long): Single<List<User>> {
        return usersDao.getUsersWithoutUserId(userId).map { UserMapper.toModel(it) }
    }

    override fun getUsersScheduledForDeletion(): Single<List<User>> {
        return usersDao.getUsersScheduledForDeletion().map { UserMapper.toModel(it) }
    }

    override fun getUsersNotScheduledForDeletion(): Single<List<User>> {
        return usersDao.getUsersNotScheduledForDeletion().map { UserMapper.toModel(it) }
    }

    override fun getUserWithUsernameAndServer(username: String, server: String): Maybe<User> {
        return usersDao.getUserWithUsernameAndServer(username, server).map { UserMapper.toModel(it) }
    }

    override fun updateUser(user: User): Int {
        return usersDao.updateUser(UserMapper.toEntity(user))
    }

    override fun insertUser(user: User): Long {
        return usersDao.saveUser(UserMapper.toEntity(user))
    }

    override fun setUserAsActiveWithId(id: Long): Single<Boolean> {
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
