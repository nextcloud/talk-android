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

@Suppress("TooManyFunctions")
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
            val user = results[0]
            user.current = true
            userRepository.updateUser(user)
            return user
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
        username: String?,
        userAttributes: UserAttributes,
    ): LiveData<UserNgEntity?> {
        var user = if (userAttributes.id == null && username != null && userAttributes.serverUrl != null) {
            userRepository.getUserWithUsernameAndServer(username, userAttributes.serverUrl)
        } else if (userAttributes.id != null) {
            userRepository.getUserWithId(userAttributes.id)
        } else {
            null
        }

        if (user == null) {
            user = createUser(
                username,
                userAttributes
            )
        } else {
            updateUserData(
                user,
                userAttributes
            )
        }
        userRepository.insertUser(user)
        return userRepository.getUserWithIdLiveData(user.id)
    }

    private fun updateUserData(user: UserNgEntity, userAttributes: UserAttributes) {
        updateUserIdIfNeeded(userAttributes, user)
        updateTokenIfNeeded(userAttributes, user)
        updateDisplayNameIfNeeded(userAttributes, user)
        updatePushConfigurationStateIfNeeded(userAttributes, user)
        updateCapabilitiesIfNeeded(userAttributes, user)
        updateCertificateAliasIfNeeded(userAttributes, user)
        updateExternalSignalingServerIfNeeded(userAttributes, user)
        updateCurrentUserStatusIfNeeded(userAttributes, user)
    }

    private fun updateCurrentUserStatusIfNeeded(userAttributes: UserAttributes, user: UserNgEntity) {
        if (userAttributes.currentUser != null) {
            user.current = userAttributes.currentUser
        }
    }

    private fun updateExternalSignalingServerIfNeeded(userAttributes: UserAttributes, user: UserNgEntity) {
        if (userAttributes.externalSignalingServer != null) {
            val newExternalSignalingServer = LoganSquare
                .parse(userAttributes.externalSignalingServer, ExternalSignalingServer::class.java)
            if (newExternalSignalingServer != user.externalSignalingServer) {
                user.externalSignalingServer = newExternalSignalingServer
            }
        }
    }

    private fun updateCertificateAliasIfNeeded(userAttributes: UserAttributes, user: UserNgEntity) {
        if (userAttributes.certificateAlias != null && userAttributes.certificateAlias != user.clientCertificate) {
            user.clientCertificate = userAttributes.certificateAlias
        }
    }

    private fun updateCapabilitiesIfNeeded(userAttributes: UserAttributes, user: UserNgEntity) {
        if (userAttributes.capabilities != null) {
            val newCapabilities = LoganSquare.parse(userAttributes.capabilities, Capabilities::class.java)
            if (newCapabilities != user.capabilities) {
                user.capabilities = newCapabilities
            }
        }
    }

    private fun updatePushConfigurationStateIfNeeded(userAttributes: UserAttributes, user: UserNgEntity) {
        if (userAttributes.pushConfigurationState != null) {
            val newPushConfigurationState = LoganSquare
                .parse(userAttributes.pushConfigurationState, PushConfigurationState::class.java)
            if (newPushConfigurationState != user.pushConfigurationState) {
                user.pushConfigurationState = newPushConfigurationState
            }
        }
    }

    private fun updateDisplayNameIfNeeded(userAttributes: UserAttributes, user: UserNgEntity) {
        if (validDisplayName(userAttributes.displayName, user)) {
            user.displayName = userAttributes.displayName
        }
    }

    private fun updateTokenIfNeeded(userAttributes: UserAttributes, user: UserNgEntity) {
        if (userAttributes.token != null && userAttributes.token != user.token) {
            user.token = userAttributes.token
        }
    }

    private fun updateUserIdIfNeeded(userAttributes: UserAttributes, user: UserNgEntity) {
        if (userAttributes.userId != null && (user.userId == null || user.userId != userAttributes.userId)) {
            user.userId = userAttributes.userId
        }
    }

    private fun createUser(username: String?, userAttributes: UserAttributes): UserNgEntity {
        val user = UserNgEntity()
        user.baseUrl = userAttributes.serverUrl
        user.username = username
        user.token = userAttributes.token
        if (!TextUtils.isEmpty(userAttributes.displayName)) {
            user.displayName = userAttributes.displayName
        }
        if (userAttributes.pushConfigurationState != null) {
            user.pushConfigurationState = LoganSquare
                .parse(userAttributes.pushConfigurationState, PushConfigurationState::class.java)
        }
        if (!TextUtils.isEmpty(userAttributes.userId)) {
            user.userId = userAttributes.userId
        }
        if (!TextUtils.isEmpty(userAttributes.capabilities)) {
            user.capabilities = LoganSquare.parse(userAttributes.capabilities, Capabilities::class.java)
        }
        if (!TextUtils.isEmpty(userAttributes.certificateAlias)) {
            user.clientCertificate = userAttributes.certificateAlias
        }
        if (!TextUtils.isEmpty(userAttributes.externalSignalingServer)) {
            user.externalSignalingServer = LoganSquare
                .parse(userAttributes.externalSignalingServer, ExternalSignalingServer::class.java)
        }
        user.current = true
        return user
    }

    private fun validDisplayName(displayName: String?, user: UserNgEntity): Boolean {
        return if (displayName == null) {
            false
        } else {
            user.displayName == null || user.displayName != null && displayName != user.displayName
        }
    }

    data class UserAttributes(
        val id: Long?,
        val serverUrl: String?,
        val currentUser: Boolean?,
        val userId: String?,
        val token: String?,
        val displayName: String?,
        val pushConfigurationState: String?,
        val capabilities: String?,
        val certificateAlias: String?,
        val externalSignalingServer: String?
    )
}
