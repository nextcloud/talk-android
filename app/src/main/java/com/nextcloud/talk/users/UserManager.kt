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
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Maybe
import io.reactivex.Single
import java.lang.Boolean.TRUE

@Suppress("TooManyFunctions")
class UserManager internal constructor(private val userRepository: UsersRepository) : CurrentUserProviderNew {
    val users: Single<List<User>>
        get() = userRepository.getUsers()

    val usersScheduledForDeletion: Single<List<User>>
        get() = userRepository.getUsersScheduledForDeletion()

    private fun setAnyUserAndSetAsActive(): Single<User> {
        val results = userRepository.getUsersNotScheduledForDeletion()

        return results.map { users ->
            users
                .firstOrNull()
                ?.apply {
                    current = true
                }.also { user ->
                    userRepository.updateUser(user!!)
                }
        }
    }

    override val currentUser: Maybe<User>
        get() {
            return userRepository.getActiveUser()
        }

    fun deleteUser(internalId: Long) {
        userRepository.deleteUserWithId(internalId)
    }

    fun deleteUserWithId(internalId: Long) {
        userRepository.deleteUserWithId(internalId)
    }

    fun getUserById(userId: String): Maybe<User> {
        return userRepository.getUserWithUserId(userId)
    }

    fun getUserWithId(id: Long): Maybe<User> {
        return userRepository.getUserWithId(id)
    }

    fun disableAllUsersWithoutId(userId: Long): Single<Int> {
        val results = userRepository.getUsersWithoutUserId(userId)

        return results.map { users ->
            var count = 0
            if (users.isNotEmpty()) {
                for (entity in users) {
                    entity.current = false
                    userRepository.updateUser(entity)
                    count++
                }
            }
            count
        }
    }

    fun checkIfUserIsScheduledForDeletion(username: String, server: String): Maybe<Boolean> {
        return userRepository.getUserWithUsernameAndServer(username, server).map { it.scheduledForDeletion }
    }

    fun getUserWithInternalId(id: Long): Maybe<User> {
        return userRepository.getUserWithIdNotScheduledForDeletion(id)
    }

    fun getIfUserWithUsernameAndServer(username: String, server: String): Maybe<Boolean> {
        return userRepository.getUserWithUsernameAndServer(username, server).map { TRUE }
    }

    fun scheduleUserForDeletionWithId(id: Long): Single<Boolean> {
        return userRepository.getUserWithId(id).map { user ->
            user.scheduledForDeletion = true
            user.current = false
            userRepository.updateUser(user)
        }
            .toSingle()
            .flatMap {
                setAnyUserAndSetAsActive()
            }.map { TRUE }
    }

    fun updateExternalSignalingServer(id: Long, externalSignalingServer: ExternalSignalingServer): Single<Int> {
        return userRepository.getUserWithId(id).map { user ->
            user.externalSignalingServer = externalSignalingServer
            userRepository.updateUser(user)
        }.toSingle()
    }

    @Deprecated("Only available for migration, use updateExternalSignalingServer or create new methods")
    fun createOrUpdateUser(
        username: String?,
        userAttributes: UserAttributes,
    ): Maybe<User> {

        val userMaybe: Maybe<User> = if (userAttributes.id != null) {
            userRepository.getUserWithId(userAttributes.id)
        } else if (username != null && userAttributes.serverUrl != null) {
            userRepository.getUserWithUsernameAndServer(username, userAttributes.serverUrl)
        } else {
            Maybe.empty()
        }

        return userMaybe
            .map { user: User? ->
                when (user) {
                    null -> createUser(
                        username,
                        userAttributes
                    )
                    else -> {
                        updateUserData(
                            user,
                            userAttributes
                        )
                        user
                    }
                }
            }
            .switchIfEmpty(Maybe.just(createUser(username, userAttributes)))
            .map { user ->
                userRepository.insertUser(user)
            }
            .flatMap { id ->
                userRepository.getUserWithId(id)
            }
    }

    fun getUserWithUsernameAndServer(username: String, server: String): Maybe<User> {
        return userRepository.getUserWithUsernameAndServer(username, server)
    }

    private fun updateUserData(user: User, userAttributes: UserAttributes) {
        user.userId = userAttributes.userId
        user.token = userAttributes.token
        user.displayName = userAttributes.displayName
        if (userAttributes.pushConfigurationState != null) {
            user.pushConfigurationState = LoganSquare
                .parse(userAttributes.pushConfigurationState, PushConfigurationState::class.java)
        }
        if (userAttributes.capabilities != null) {
            user.capabilities = LoganSquare
                .parse(userAttributes.capabilities, Capabilities::class.java)
        }
        user.clientCertificate = userAttributes.certificateAlias
        if (userAttributes.externalSignalingServer != null) {
            user.externalSignalingServer = LoganSquare
                .parse(userAttributes.externalSignalingServer, ExternalSignalingServer::class.java)
        }
        user.current = userAttributes.currentUser == true
    }

    private fun createUser(username: String?, userAttributes: UserAttributes): User {
        val user = User()
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

    companion object {
        const val TAG = "UserManager"
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
