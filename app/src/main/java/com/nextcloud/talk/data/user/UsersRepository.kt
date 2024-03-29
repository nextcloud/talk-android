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
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single

@Suppress("TooManyFunctions")
interface UsersRepository {
    fun getActiveUser(): Maybe<User>
    fun getActiveUserObservable(): Observable<User>
    fun getUsers(): Single<List<User>>
    fun getUserWithId(id: Long): Maybe<User>
    fun getUserWithIdNotScheduledForDeletion(id: Long): Maybe<User>
    fun getUserWithUserId(userId: String): Maybe<User>
    fun getUsersScheduledForDeletion(): Single<List<User>>
    fun getUsersNotScheduledForDeletion(): Single<List<User>>
    fun getUserWithUsernameAndServer(username: String, server: String): Maybe<User>
    fun updateUser(user: User): Int
    fun insertUser(user: User): Long
    fun setUserAsActiveWithId(id: Long): Single<Boolean>
    fun deleteUser(user: User): Int
    fun updatePushState(id: Long, state: PushConfigurationState): Single<Int>
}
