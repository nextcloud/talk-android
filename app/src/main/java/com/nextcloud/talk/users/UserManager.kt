/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <infoi@andy-scherzinger.de>
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
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.ServerVersion
import com.nextcloud.talk.models.json.push.PushConfigurationState
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.rx2.rxMaybe
import kotlinx.coroutines.rx2.rxSingle

@Suppress("TooManyFunctions")
class UserManager internal constructor(private val userRepository: UsersRepository) {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Deprecated("Use suspend fun getUsers() instead")
    val users: Single<List<User>>
        get() = rxSingle { getUsers() }

    suspend fun getUsers(): List<User> = userRepository.getUsers()

    @Deprecated("Use suspend fun getUsersScheduledForDeletion() instead")
    val usersScheduledForDeletion: Single<List<User>>
        get() = rxSingle { getUsersScheduledForDeletion() }

    suspend fun getUsersScheduledForDeletion(): List<User> = userRepository.getUsersScheduledForDeletion()

    /**
     * @deprecated coroutine-native code should use [com.nextcloud.talk.utils.database.user.CurrentUserProvider]
     * instead.
     */
    @Deprecated("Use CurrentUserProvider.getCurrentUser() instead")
    val currentUser: Maybe<User>
        get() = rxMaybe { getCurrentUserOrAny() }

    private suspend fun getCurrentUserOrAny(): User? = userRepository.getActiveUser() ?: getAnyUserAndSetAsActive()

    /**
     * Backed by [activeUserSubject] rather than [UsersRepository.getActiveUserFlow] directly, so that
     * [setUserAsActive] can push the newly-active user out synchronously the moment it succeeds, instead of
     * consumers having to wait for Room's invalidation-tracker round trip to notice the DB write and re-query.
     * That round trip is asynchronous and was racing against code (e.g. AccountVerificationActivity.
     * proceedWithLogin()) that both changes the active user and immediately acts as if every observer already
     * knows about it - e.g. launching a screen for the new user before its avatar/data had actually updated.
     * Room's own [UsersRepository.getActiveUserFlow] is still relied on underneath to seed this and to catch
     * any change to the `current` flag that doesn't go through [setUserAsActive].
     *
     * RxJava-based for CurrentUserProviderOld, the still-used but deprecated consumer. Coroutine-based code
     * should prefer [currentUserFlow] instead, which is updated at the exact same point and needs no RxJava
     * bridging on the consuming side.
     */
    val currentUserObservable: Observable<User>
        get() = activeUserSubject

    /**
     * Coroutine-native counterpart to [currentUserObservable] - see its doc for why this exists. Both are
     * updated synchronously, at the same point in [setUserAsActive], from Room's same underlying query.
     */
    val currentUserFlow: StateFlow<User?>
        get() = activeUserStateFlow

    private val activeUserSubject: Subject<User> by lazy {
        val subject = BehaviorSubject.create<User>().toSerialized()
        managerScope.launch {
            userRepository.getActiveUserFlow().filterNotNull().collect(subject::onNext)
        }
        subject
    }

    private val activeUserStateFlow: MutableStateFlow<User?> by lazy {
        val flow = MutableStateFlow<User?>(null)
        managerScope.launch {
            userRepository.getActiveUserFlow().collect { flow.value = it }
        }
        flow
    }

    @Deprecated("Use suspend fun deleteUser(internalId: Long) instead")
    fun deleteUser(internalId: Long): Int = runBlocking { deleteUserSuspend(internalId) }

    suspend fun deleteUserSuspend(internalId: Long): Int {
        val user = userRepository.getUserWithId(internalId) ?: return 0
        return userRepository.deleteUser(user)
    }

    @Deprecated("Use suspend fun getUserWithId(id: Long) instead")
    fun getUserWithId(id: Long): Maybe<User> = rxMaybe { getUserWithIdSuspend(id) }

    suspend fun getUserWithIdSuspend(id: Long): User? = userRepository.getUserWithId(id)

    @Deprecated("Use suspend fun checkIfUserIsScheduledForDeletion(username, server) instead")
    fun checkIfUserIsScheduledForDeletion(username: String, server: String): Single<Boolean> =
        rxSingle { checkIfUserIsScheduledForDeletionSuspend(username, server) }

    suspend fun checkIfUserIsScheduledForDeletionSuspend(username: String, server: String): Boolean =
        userRepository.getUserWithUsernameAndServer(username, server)?.scheduledForDeletion ?: false

    @Deprecated("Use suspend fun getUserWithInternalId(id: Long) instead")
    fun getUserWithInternalId(id: Long): Maybe<User> = rxMaybe { getUserWithInternalIdSuspend(id) }

    suspend fun getUserWithInternalIdSuspend(id: Long): User? = userRepository.getUserWithIdNotScheduledForDeletion(id)

    @Deprecated("Use suspend fun checkIfUserExists(username, server) instead")
    fun checkIfUserExists(username: String, server: String): Single<Boolean> =
        rxSingle { checkIfUserExistsSuspend(username, server) }

    suspend fun checkIfUserExistsSuspend(username: String, server: String): Boolean =
        userRepository.getUserWithUsernameAndServer(username, server) != null

    /**
     * Don't ask
     *
     * @return `true` if the user was updated **AND** there is another user to set as active, `false` otherwise
     */
    @Deprecated("Use suspend fun scheduleUserForDeletionWithId(id: Long) instead")
    fun scheduleUserForDeletionWithId(id: Long): Single<Boolean> = rxSingle { scheduleUserForDeletionWithIdSuspend(id) }

    suspend fun scheduleUserForDeletionWithIdSuspend(id: Long): Boolean {
        val user = userRepository.getUserWithId(id) ?: return false
        user.scheduledForDeletion = true
        user.current = false
        userRepository.updateUser(user)
        return getAnyUserAndSetAsActive() != null
    }

    /**
     * If there is more than one local User row for the same username+baseUrl (e.g. reusing the
     * same token): keep whichever row [UsersRepository.getActiveUser] actually resolves to if
     * it's one of the duplicates, otherwise a duplicate marked current, otherwise the oldest
     * (lowest id) row, and schedules the rest for deletion so AccountRemovalWorker cleans them up
     * like any other removed account.
     *
     * The active user is resolved first, rather than trusting each row's own `current` flag,
     * because a past(?) bug could leave more than one row marked current=true for the same account.
     * Picking a duplicate to keep by its `current` flag alone could then disagree with whichever
     * row a live session/background sync is still bound to, and deleting that row here would trip
     * a foreign key constraint on any in-flight write still referencing it.
     *
     * @return the number of duplicate rows scheduled for deletion
     */
    @Deprecated("Use suspend fun scheduleDuplicateAccountsForDeletion() instead")
    fun scheduleDuplicateAccountsForDeletion(): Single<Int> = rxSingle { scheduleDuplicateAccountsForDeletionSuspend() }

    suspend fun scheduleDuplicateAccountsForDeletionSuspend(): Int {
        val allUsers = getUsers()
        val activeUserId = userRepository.getActiveUser()?.id ?: NO_ACTIVE_USER_ID

        val duplicateGroups = allUsers
            .filter { !it.username.isNullOrEmpty() && !it.baseUrl.isNullOrEmpty() }
            .groupBy { it.username to it.baseUrl }
            .values
            .filter { it.size > 1 }

        var scheduledCount = 0
        duplicateGroups.forEach { duplicates ->
            val userToKeep = duplicates.firstOrNull { it.id == activeUserId }
                ?: duplicates.firstOrNull { it.current }
                ?: duplicates.minByOrNull { it.id ?: Long.MAX_VALUE }
            duplicates
                .filter { it.id != userToKeep?.id }
                .forEach { duplicate ->
                    duplicate.scheduledForDeletion = true
                    userRepository.updateUser(duplicate)
                    scheduledCount++
                }
        }
        return scheduledCount
    }

    private suspend fun getAnyUserAndSetAsActive(): User? {
        val results = userRepository.getUsersNotScheduledForDeletion()
        if (results.isEmpty()) {
            return null
        }
        val user = results.first()
        return if (setUserAsActiveSuspend(user)) {
            userRepository.getActiveUser()
        } else {
            null
        }
    }

    @Deprecated("Use suspend fun updateExternalSignalingServer(id, externalSignalingServer) instead")
    fun updateExternalSignalingServer(id: Long, externalSignalingServer: ExternalSignalingServer): Single<Int> =
        rxSingle { updateExternalSignalingServerSuspend(id, externalSignalingServer) }

    suspend fun updateExternalSignalingServerSuspend(id: Long, externalSignalingServer: ExternalSignalingServer): Int {
        val user = userRepository.getUserWithId(id) ?: throw NoSuchElementException()
        user.externalSignalingServer = externalSignalingServer
        return userRepository.updateUser(user)
    }

    @Deprecated("Use suspend fun updateOrCreateUser(user) instead")
    fun updateOrCreateUser(user: User): Single<Int> = rxSingle { updateOrCreateUserSuspend(user) }

    suspend fun updateOrCreateUserSuspend(user: User): Int =
        when (user.id) {
            null -> userRepository.insertUser(user).toInt()
            else -> userRepository.updateUser(user)
        }

    @Deprecated("Use suspend fun saveUser(user) instead")
    fun saveUser(user: User): Single<Int> = rxSingle { saveUserSuspend(user) }

    suspend fun saveUserSuspend(user: User): Int = userRepository.updateUser(user)

    @Deprecated("Use suspend fun setUserAsActive(user) instead")
    fun setUserAsActive(user: User): Single<Boolean> = rxSingle { setUserAsActiveSuspend(user) }

    suspend fun setUserAsActiveSuspend(user: User): Boolean {
        Log.d(TAG, "setUserAsActive:" + user.id!!)
        val success = userRepository.setUserAsActiveWithId(user.id!!)
        if (success) {
            activeUserSubject.onNext(user)
            activeUserStateFlow.value = user
        }
        return success
    }

    @Deprecated("Use suspend fun storeProfile(username, userAttributes) instead")
    fun storeProfile(username: String?, userAttributes: UserAttributes): Maybe<User> =
        rxMaybe { storeProfileSuspend(username, userAttributes) }

    suspend fun storeProfileSuspend(username: String?, userAttributes: UserAttributes): User? {
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
                .parse(userAttributes.capabilities, Capabilities::class.java)
        }
        if (userAttributes.serverVersion != null) {
            user.serverVersion = LoganSquare
                .parse(userAttributes.serverVersion, ServerVersion::class.java)
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
        if (!TextUtils.isEmpty(userAttributes.serverVersion)) {
            user.serverVersion = LoganSquare.parse(userAttributes.serverVersion, ServerVersion::class.java)
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

    @Deprecated("Use suspend fun updatePushState(id, state) instead")
    fun updatePushState(id: Long, state: PushConfigurationState): Single<Int> =
        rxSingle { updatePushStateSuspend(id, state) }

    suspend fun updatePushStateSuspend(id: Long, state: PushConfigurationState): Int =
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
