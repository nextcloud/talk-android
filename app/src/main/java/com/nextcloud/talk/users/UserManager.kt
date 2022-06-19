/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.users

import android.text.TextUtils
import androidx.lifecycle.LiveData
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.model.UserNgEntity
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew

class UserManager internal constructor(private val userRepository: UsersRepository) : CurrentUserProviderNew {
    fun anyUserExists(): Boolean {
        return userRepository.getUsers().isNotEmpty()
    }

    fun hasMultipleUsers(): Boolean {
        return userRepository.getUsers().size > 1
    }

    val users: List<UserNgEntity>
        get() = userRepository.getUsers()

    val usersScheduledForDeletion: List<UserNgEntity>
        get() = userRepository.getUsersScheduledForDeletion()

    suspend fun setAnyUserAndSetAsActive(): UserNgEntity? {
        val results = userRepository.getUsersNotScheduledForDeletion()
        if (results.isNotEmpty()) {
            val UserNgEntity = results[0]
            UserNgEntity.current = true
            userRepository.updateUser(UserNgEntity)
            return UserNgEntity
        }
        return null
    }

    override val currentUser: UserNgEntity?
        get() {
            return userRepository.getActiveUser()
        }

    suspend fun deleteUser(internalId: Long) {
        userRepository.deleteUserWithId(internalId)
    }

    suspend fun deleteUserWithId(internalId: Long) {
        userRepository.deleteUserWithId(internalId)
    }

    fun getUserById(userId: String): UserNgEntity? {
        return userRepository.getUserWithUserId(userId)
    }

    fun getUserWithId(id: Long): UserNgEntity? {
        return userRepository.getUserWithId(id)
    }

    suspend fun disableAllUsersWithoutId(userId: Long) {
        val results = userRepository.getUsersWithoutUserId(userId)
        if (results.isNotEmpty()) {
            for (entity in results) {
                entity.current = false
                userRepository.updateUser(entity)
            }
        }
    }

    suspend fun checkIfUserIsScheduledForDeletion(username: String, server: String): Boolean {
        val results = userRepository.getUserWithUsernameAndServer(username, server)
        return results?.scheduledForDeletion ?: false
    }

    fun getUserWithInternalId(id: Long): UserNgEntity? {
        return userRepository.getUserWithIdNotScheduledForDeletion(id)
    }

    suspend fun getIfUserWithUsernameAndServer(username: String, server: String): Boolean {
        return userRepository.getUserWithUsernameAndServer(username, server) != null
    }

    suspend fun scheduleUserForDeletionWithId(id: Long): Boolean {
        val result = userRepository.getUserWithId(id)

        if (result != null) {
            result.scheduledForDeletion = true
            result.current = false
            userRepository.updateUser(result)
        }

        return setAnyUserAndSetAsActive() != null
    }

    suspend fun createOrUpdateUser(
        username: String?, token: String?,
        serverUrl: String?,
        displayName: String?,
        pushConfigurationState: String?,
        currentUser: Boolean?,
        userId: String?,
        internalId: Long?,
        capabilities: String?,
        certificateAlias: String?,
        externalSignalingServer: String?
    ): LiveData<UserNgEntity?> {
        var user = if (internalId == null && username != null && serverUrl != null) {
            userRepository.getUserWithUsernameAndServer(username, serverUrl)
        } else if (internalId != null) {
            userRepository.getUserWithId(internalId)
        } else {
            null
        }

        if (user == null) {
            user = UserNgEntity()
            user.baseUrl = serverUrl
            user.username = username
            user.token = token
            if (!TextUtils.isEmpty(displayName)) {
                user.displayName = displayName
            }
            if (pushConfigurationState != null) {
                user.pushConfigurationState = LoganSquare
                    .parse(pushConfigurationState, PushConfigurationState::class.java)
            }
            if (!TextUtils.isEmpty(userId)) {
                user.userId = userId
            }
            if (!TextUtils.isEmpty(capabilities)) {
                user.capabilities = LoganSquare.parse(capabilities, Capabilities::class.java)
            }
            if (!TextUtils.isEmpty(certificateAlias)) {
                user.clientCertificate = certificateAlias
            }
            if (!TextUtils.isEmpty(externalSignalingServer)) {
                user.externalSignalingServer = LoganSquare
                    .parse(externalSignalingServer, ExternalSignalingServer::class.java)
            }
            user.current = true
        } else {
            if (userId != null && (user.userId == null || user.userId != userId)) {
                user.userId = userId
            }
            if (token != null && token != user.token) {
                user.token = token
            }
            if (displayName != null && user.displayName == null || displayName != null && (user.displayName
                    != null) && displayName != user.displayName
            ) {
                user.displayName = displayName
            }
            if (pushConfigurationState != null) {
                val newPushConfigurationState = LoganSquare
                    .parse(pushConfigurationState, PushConfigurationState::class.java)
                if (newPushConfigurationState != user.pushConfigurationState) {
                    user.pushConfigurationState = newPushConfigurationState
                }
            }
            if (capabilities != null) {
                val newCapabilities = LoganSquare.parse(capabilities, Capabilities::class.java)
                if (newCapabilities != user.capabilities) {
                    user.capabilities = newCapabilities
                }
            }
            if (certificateAlias != null && certificateAlias != user.clientCertificate) {
                user.clientCertificate = certificateAlias
            }
            if (externalSignalingServer != null) {
                val newExternalSignalingServer = LoganSquare
                    .parse(externalSignalingServer, ExternalSignalingServer::class.java)
                if (newExternalSignalingServer != user.externalSignalingServer) {
                    user.externalSignalingServer = newExternalSignalingServer
                }
            }
            if (currentUser != null) {
                user.current = currentUser
            }
        }
        userRepository.insertUser(user)
        return userRepository.getUserWithIdLiveData(user.id)
    }
}
