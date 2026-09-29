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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Suppress("TooManyFunctions")
class UserManager internal constructor(private val userRepository: UsersRepository) {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getUsers(): List<User> = userRepository.getUsers()

    suspend fun getUsersScheduledForDeletion(): List<User> = userRepository.getUsersScheduledForDeletion()

    suspend fun getUsersNotScheduledForDeletion(): List<User> = userRepository.getUsersNotScheduledForDeletion()

    /**
     * The default account, i.e. the last active user, or - if none is active - any user not scheduled for deletion,
     * which is then set as active.
     *
     * Only for entry points that have no account context (app launch, share-to, deep links without account).
     * Screens, workers and receivers must use the user they were started for, see [getUserWithId] and [userFlow].
     */
    suspend fun getDefaultUser(): User? = userRepository.getActiveUser() ?: getAnyUserAndSetAsActive()

    /**
     * Emits the user with the given internal id whenever its row changes, or null if it does not exist (anymore).
     */
    fun userFlow(id: Long): Flow<User?> = userRepository.getUserWithIdFlow(id).distinctUntilChanged()

    /**
     * Ensures that at most one user is marked as active. If several are, the one with the highest id is kept,
     * matching the user returned by [getDefaultUser].
     */
    suspend fun repairMultipleActiveUsers() {
        if (userRepository.repairMultipleActiveUsers() > 0) {
            Log.w(TAG, "Multiple active users found, kept the one with the highest id as active")
        }
    }

    /**
     * Backed by [activeUserStateFlow] rather than [UsersRepository.getActiveUserFlow] directly, so that
     * [setUserAsActive] can push the newly-active user out synchronously the moment it succeeds, instead of
     * consumers having to wait for Room's invalidation-tracker round trip to notice the DB write and re-query.
     * That round trip is asynchronous and was racing against code (e.g. AccountVerificationActivity.
     * proceedWithLogin()) that both changes the active user and immediately acts as if every observer already
     * knows about it - e.g. launching a screen for the new user before its avatar/data had actually updated.
     * Room's own [UsersRepository.getActiveUserFlow] is still relied on underneath to seed this and to catch
     * any change to the `current` flag that doesn't go through [setUserAsActive].
     *
     * Screens must not use this but the account they were started for, see [userFlow].
     */
    val defaultUserFlow: StateFlow<User?>
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

    // The following updates only write the given fields, so they cannot reset fields that changed since the user
    // was read, like the default account flag. Use them instead of saving a whole user that was read earlier.

    suspend fun updateExternalSignalingServer(id: Long, externalSignalingServer: ExternalSignalingServer): Int {
        val updated = userRepository.updateExternalSignalingServer(id, externalSignalingServer)
        if (updated == 0) throw NoSuchElementException()
        return updated
    }

    suspend fun updateCapabilities(id: Long, capabilities: CapabilitiesDto?, serverVersion: ServerVersionDto?): Int =
        userRepository.updateCapabilities(id, capabilities, serverVersion)

    suspend fun updateDisplayName(id: Long, displayName: String?): Int =
        userRepository.updateDisplayName(id, displayName)

    suspend fun updateClientCertificate(id: Long, clientCertificate: String?): Int =
        userRepository.updateClientCertificate(id, clientCertificate)

    suspend fun updateCredentials(id: Long, token: String?, clientCertificate: String?): Int =
        userRepository.updateCredentials(id, token, clientCertificate)

    suspend fun updateOrCreateUser(user: User): Int =
        when (user.id) {
            null -> userRepository.insertUser(user).toInt()
            else -> userRepository.updateUser(user)
        }

    suspend fun setUserAsActive(user: User): Boolean {
        Log.d(TAG, "setUserAsActive:" + user.id!!)
        val success = userRepository.setUserAsActiveWithId(user.id!!)
        if (success) {
            activeUserStateFlow.value = userRepository.getUserWithId(user.id!!) ?: user
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
