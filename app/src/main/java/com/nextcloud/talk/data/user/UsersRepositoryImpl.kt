/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2020 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.user

import android.util.Log
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.push.PushConfigurationState
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single

@Suppress("TooManyFunctions")
class UsersRepositoryImpl(private val usersDao: UsersDao) : UsersRepository {

    override fun getActiveUser(): Maybe<User> {
        val user = usersDao.getActiveUser()
            .map {
                setUserAsActiveWithId(it.id)
                UserMapper.toModel(it)!!
            }
        return user
    }

    override fun getActiveUserObservable(): Observable<User> {
        return usersDao.getActiveUserObservable().map { UserMapper.toModel(it) }
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
        val amountUpdated = usersDao.setUserAsActiveWithId(id)
        Log.d(TAG, "setUserAsActiveWithId. amountUpdated: $amountUpdated")
        return if (amountUpdated > 0) {
            Single.just(true)
        } else {
            Single.just(false)
        }
    }

    override fun deleteUser(user: User): Int {
        return usersDao.deleteUser(UserMapper.toEntity(user))
    }

    override fun updatePushState(id: Long, state: PushConfigurationState): Single<Int> {
        return usersDao.updatePushState(id, state)
    }

    companion object {
        private val TAG = UsersRepositoryImpl::class.simpleName
    }
}
