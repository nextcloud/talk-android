/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.users

import android.text.TextUtils
import android.util.Log
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.CapabilitiesDto
import com.nextcloud.talk.models.json.capabilities.ServerVersionDto
import com.nextcloud.talk.models.json.push.PushConfigurationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
class UserManager internal constructor(private val userRepository: UsersRepository) {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getUsers(): List<User> = userRepository.getUsers()

    suspend fun getUsersScheduledForDeletion(): List<User> = userRepository.getUsersScheduledForDeletion()

    /**
     * The active user, or - if none is active - any user not scheduled for deletion, which is then set as active.
     */
    suspend fun getCurrentUser(): User? = userRepository.getActiveUser() ?: getAnyUserAndSetAsActive()

    /**
     * Backed by [activeUserStateFlow] rather than [UsersRepository.getActiveUserFlow] directly, so that
     * [setUserAsActive] can push the newly-active user out synchronously the moment it succeeds, instead of
     * consumers having to wait for Room's invalidation-tracker round trip to notice the DB write and re-query.
     * That round trip is asynchronous and was racing against code (e.g. AccountVerificationActivity.
     * proceedWithLogin()) that both changes the active user and immediately acts as if every observer already
     * knows about it - e.g. launching a screen for the new user before its avatar/data had actually updated.
     * Room's own [UsersRepository.getActiveUserFlow] is still relied on underneath to seed this and to catch
     * any change to the `current` flag that doesn't go through [setUserAsActive].
     */
    val currentUserFlow: StateFlow<User?>
        get() = activeUserStateFlow

    private val activeUserStateFlow: MutableStateFlow<User?> by lazy {
        val flow = MutableStateFlow<User?>(null)
        managerScope.launch {
            userRepository.getActiveUserFlow().collect { flow.value = it }
        }
        flow
    }

    suspend fun deleteUser(internalId: Long): Int {
        val user = userRepository.getUserWithId(internalId) ?: return 0
        return userRepository.deleteUser(user)
    }

    suspend fun getUserWithId(id: Long): User? = userRepository.getUserWithId(id)

    suspend fun checkIfUserIsScheduledForDeletion(username: String, server: String): Boolean =
        userRepository.getUserWithUsernameAndServer(username, server)?.scheduledForDeletion ?: false

    suspend fun getUserWithInternalId(id: Long): User? = userRepository.getUserWithIdNotScheduledForDeletion(id)

    suspend fun checkIfUserExists(username: String, server: String): Boolean =
        userRepository.getUserWithUsernameAndServer(username, server) != null

    /**
     * Don't ask
     *
     * @return `true` if the user was updated **AND** there is another user to set as active, `false` otherwise
     */
    suspend fun scheduleUserForDeletionWithId(id: Long): Boolean {
        val user = userRepository.getUserWithId(id) ?: return false
        user.scheduledForDeletion = true
        user.current = false
        userRepository.updateUser(user)
        return getAnyUserAndSetAsActive() != null
    }

    private suspend fun getAnyUserAndSetAsActive(): User? {
        val results = userRepository.getUsersNotScheduledForDeletion()
        if (results.isEmpty()) {
            return null
        }
        val user = results.first()
        return if (setUserAsActive(user)) {
            userRepository.getActiveUser()
        } else {
            null
        }
    }

    suspend fun updateExternalSignalingServer(id: Long, externalSignalingServer: ExternalSignalingServer): Int {
        val user = userRepository.getUserWithId(id) ?: throw NoSuchElementException()
        user.externalSignalingServer = externalSignalingServer
        return userRepository.updateUser(user)
    }

    suspend fun updateOrCreateUser(user: User): Int =
        when (user.id) {
            null -> userRepository.insertUser(user).toInt()
            else -> userRepository.updateUser(user)
        }

    suspend fun saveUser(user: User): Int = userRepository.updateUser(user)

    suspend fun setUserAsActive(user: User): Boolean {
        Log.d(TAG, "setUserAsActive:" + user.id!!)
        val success = userRepository.setUserAsActiveWithId(user.id!!)
        if (success) {
            activeUserStateFlow.value = user
        }
        return success
    }

    suspend fun storeProfile(username: String?, userAttributes: UserAttributes): User? {
        val existingUser = findUser(userAttributes)
        val user = if (existingUser != null) {
            existingUser.apply {
                token = userAttributes.token
                baseUrl = userAttributes.serverUrl
                current = userAttributes.currentUser
                userId = userAttributes.userId
                token = userAttributes.token
                displayName = userAttributes.displayName
                clientCertificate = userAttributes.certificateAlias
                updateUserData(this, userAttributes)
            }
        } else {
            createUser(username, userAttributes)
        }
        val id = userRepository.insertUser(user)
        return userRepository.getUserWithId(id)
    }

    private suspend fun findUser(userAttributes: UserAttributes): User? =
        if (userAttributes.id != null) {
            userRepository.getUserWithId(userAttributes.id)
        } else {
            null
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
                .parse(userAttributes.capabilities, CapabilitiesDto::class.java)
        }
        if (userAttributes.serverVersion != null) {
            user.serverVersion = LoganSquare
                .parse(userAttributes.serverVersion, ServerVersionDto::class.java)
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
            user.capabilities = LoganSquare.parse(userAttributes.capabilities, CapabilitiesDto::class.java)
        }
        if (!TextUtils.isEmpty(userAttributes.serverVersion)) {
            user.serverVersion = LoganSquare.parse(userAttributes.serverVersion, ServerVersionDto::class.java)
        }
        if (!TextUtils.isEmpty(userAttributes.certificateAlias)) {
            user.clientCertificate = userAttributes.certificateAlias
        }
        if (!TextUtils.isEmpty(userAttributes.externalSignalingServer)) {
            user.externalSignalingServer = LoganSquare
                .parse(userAttributes.externalSignalingServer, ExternalSignalingServer::class.java)
        }
        user.current = userAttributes.currentUser == true
        return user
    }

    suspend fun updatePushState(id: Long, state: PushConfigurationState): Int =
        userRepository.updatePushState(id, state)

    companion object {
        const val TAG = "UserManager"
        private const val NO_ACTIVE_USER_ID = -1L
    }

    data class UserAttributes(
        val id: Long?,
        val serverUrl: String?,
        val currentUser: Boolean,
        val userId: String?,
        val token: String?,
        val displayName: String?,
        val pushConfigurationState: String?,
        val capabilities: String?,
        val serverVersion: String?,
        val certificateAlias: String?,
        val externalSignalingServer: String?
    )
}
