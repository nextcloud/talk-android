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
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.ServerVersionDto
import com.nextcloud.talk.models.json.push.PushConfigurationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Suppress("TooManyFunctions")
class UsersRepositoryImpl(private val usersDao: UsersDao) : UsersRepository {

    override suspend fun getActiveUser(): User? = UserMapper.toModel(usersDao.getActiveUser())

    override fun getActiveUserFlow(): Flow<User?> =
        usersDao.getActiveUserFlow().map {
            UserMapper.toModel(it)
        }

    override suspend fun getUsers(): List<User> = UserMapper.toModel(usersDao.getUsers())

    override suspend fun getUserWithId(id: Long): User? = UserMapper.toModel(usersDao.getUserWithId(id))

    override fun getUserWithIdFlow(id: Long): Flow<User?> =
        usersDao.getUserWithIdFlow(id).map {
            UserMapper.toModel(it)
        }

    override suspend fun repairMultipleActiveUsers(): Int = usersDao.repairMultipleActiveUsers()

    override suspend fun getUserWithIdNotScheduledForDeletion(id: Long): User? =
        UserMapper.toModel(usersDao.getUserWithIdNotScheduledForDeletion(id))

    override suspend fun getUserWithUserId(userId: String): User? =
        UserMapper.toModel(usersDao.getUserWithUserId(userId))

    override suspend fun getUsersScheduledForDeletion(): List<User> =
        UserMapper.toModel(usersDao.getUsersScheduledForDeletion())

    override suspend fun getUsersNotScheduledForDeletion(): List<User> =
        UserMapper.toModel(usersDao.getUsersNotScheduledForDeletion())

    override suspend fun getUserWithUsernameAndServer(username: String, server: String): User? =
        UserMapper.toModel(usersDao.getUserWithUsernameAndServer(username, server))

    override suspend fun updateUser(user: User): Int = usersDao.updateUser(UserMapper.toEntity(user))

    override suspend fun insertUser(user: User): Long = usersDao.saveUser(UserMapper.toEntity(user))

    override suspend fun setUserAsActiveWithId(id: Long): Boolean {
        val amountUpdated = usersDao.setUserAsActiveWithId(id)
        Log.d(TAG, "setUserAsActiveWithId. amountUpdated: $amountUpdated")
        return amountUpdated > 0
    }

    override suspend fun deleteUser(user: User): Int = usersDao.deleteUser(UserMapper.toEntity(user))

    override suspend fun updatePushState(id: Long, state: PushConfigurationState): Int =
        usersDao.updatePushState(id, state)

    override suspend fun updateCapabilities(
        id: Long,
        capabilities: CapabilitiesDto?,
        serverVersion: ServerVersionDto?
    ): Int = usersDao.updateCapabilities(id, capabilities, serverVersion)

    override suspend fun updateExternalSignalingServer(
        id: Long,
        externalSignalingServer: ExternalSignalingServer?
    ): Int = usersDao.updateExternalSignalingServer(id, externalSignalingServer)

    override suspend fun updateDisplayName(id: Long, displayName: String?): Int =
        usersDao.updateDisplayName(id, displayName)

    override suspend fun updateClientCertificate(id: Long, clientCertificate: String?): Int =
        usersDao.updateClientCertificate(id, clientCertificate)

    override suspend fun updateCredentials(id: Long, token: String?, clientCertificate: String?): Int =
        usersDao.updateCredentials(id, token, clientCertificate)

    companion object {
        private val TAG = UsersRepositoryImpl::class.simpleName
    }
}
