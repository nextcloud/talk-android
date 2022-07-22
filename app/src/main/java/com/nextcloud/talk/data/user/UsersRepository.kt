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
interface UsersRepository {
    fun getActiveUser(): Maybe<User>
    fun getUsers(): Single<List<User>>
    fun getUserWithId(id: Long): Maybe<User>
    fun getUserWithIdNotScheduledForDeletion(id: Long): Maybe<User>
    fun getUserWithUserId(userId: String): Maybe<User>
    fun getUsersWithoutUserId(id: Long): Single<List<User>>
    fun getUsersScheduledForDeletion(): Single<List<User>>
    fun getUsersNotScheduledForDeletion(): Single<List<User>>
    fun getUserWithUsernameAndServer(username: String, server: String): Maybe<User>
    fun updateUser(user: User): Int
    fun insertUser(user: User): Long
    fun setUserAsActiveWithId(id: Long): Single<Boolean>
    fun deleteUser(user: User): Int
    fun setAnyUserAsActive(): Boolean
    fun markUserForDeletion(id: Long): Boolean
}
