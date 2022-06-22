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
interface UsersRepository {
    fun getActiveUserLiveData(): Observable<UserNgEntity?>
    fun getActiveUser(): Observable<UserNgEntity?>
    fun getActiveUserSynchronously(): UserNgEntity?
    fun getUsers(): Observable<List<UserNgEntity>>
    fun getUserWithId(id: Long): Observable<UserNgEntity?>
    fun getUserWithIdLiveData(id: Long): Observable<UserNgEntity?>
    fun getUserWithIdNotScheduledForDeletion(id: Long): Observable<UserNgEntity?>
    fun getUserWithUserId(userId: String): Observable<UserNgEntity?>
    fun getUsersWithoutUserId(userId: Long): Observable<List<UserNgEntity>>
    fun getUsersLiveData(): Observable<List<UserNgEntity>>
    fun getUsersLiveDataWithoutActive(): Observable<List<UserNgEntity>>
    fun getUsersScheduledForDeletion(): Observable<List<UserNgEntity>>
    fun getUsersNotScheduledForDeletion(): Observable<List<UserNgEntity>>
    fun getUserWithUsernameAndServer(username: String, server: String): Observable<UserNgEntity?>
    fun updateUser(user: UserNgEntity): Int
    fun insertUser(user: UserNgEntity): Long
    suspend fun setUserAsActiveWithId(id: Long): Boolean
    fun deleteUserWithId(id: Long)
    suspend fun setAnyUserAsActive(): Boolean
    suspend fun markUserForDeletion(id: Long): Boolean
}
