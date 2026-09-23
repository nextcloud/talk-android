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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Suppress("TooManyFunctions")
class UserManager internal constructor(private val userRepository: UsersRepository) {
    val users: Single<List<User>>
        get() = userRepository.getUsers()

    val usersScheduledForDeletion: Single<List<User>>
        get() = userRepository.getUsersScheduledForDeletion()

    val currentUser: Maybe<User>
        get() {
            return userRepository.getActiveUser()
                .switchIfEmpty(Maybe.defer { getAnyUserAndSetAsActive() })
        }

    /**
     * Backed by [activeUserSubject] rather than [UsersRepository.getActiveUserObservable] directly, so that
     * [setUserAsActive] can push the newly-active user out synchronously the moment it succeeds, instead of
     * consumers having to wait for Room's invalidation-tracker round trip to notice the DB write and re-query.
     * That round trip is asynchronous and was racing against code (e.g. AccountVerificationActivity.
     * proceedWithLogin()) that both changes the active user and immediately acts as if every observer already
     * knows about it - e.g. launching a screen for the new user before its avatar/data had actually updated.
     * Room's own observable is still relied on underneath to seed this and to catch any change to the `current`
     * flag that doesn't go through [setUserAsActive].
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
        userRepository.getActiveUserObservable().subscribe(subject::onNext) { }
        subject
    }

    private val activeUserStateFlow: MutableStateFlow<User?> by lazy {
        val flow = MutableStateFlow<User?>(null)
        userRepository.getActiveUserObservable().subscribe({ flow.value = it }) { }
        flow
    }

    fun deleteUser(internalId: Long): Int =
        userRepository.deleteUser(userRepository.getUserWithId(internalId).blockingGet())

    fun getUserWithId(id: Long): Maybe<User> = userRepository.getUserWithId(id)

    fun checkIfUserIsScheduledForDeletion(username: String, server: String): Single<Boolean> =
        userRepository
            .getUserWithUsernameAndServer(username, server)
            .map { it.scheduledForDeletion }
            .switchIfEmpty(Single.just(false))

    fun getUserWithInternalId(id: Long): Maybe<User> = userRepository.getUserWithIdNotScheduledForDeletion(id)

    fun checkIfUserExists(username: String, server: String): Single<Boolean> =
        userRepository
            .getUserWithUsernameAndServer(username, server)
            .map { true }
            .switchIfEmpty(Single.just(false))

    /**
     * Don't ask
     *
     * @return `true` if the user was updated **AND** there is another user to set as active, `false` otherwise
     */
    fun scheduleUserForDeletionWithId(id: Long): Single<Boolean> =
        userRepository.getUserWithId(id)
            .map { user ->
                user.scheduledForDeletion = true
                user.current = false
                userRepository.updateUser(user)
            }
            .flatMap { getAnyUserAndSetAsActive() }
            .map { true }
            .switchIfEmpty(Single.just(false))

    private fun getAnyUserAndSetAsActive(): Maybe<User> {
        val results = userRepository.getUsersNotScheduledForDeletion()

        return results
            .flatMapMaybe {
                if (it.isNotEmpty()) {
                    val user = it.first()
                    if (setUserAsActive(user).blockingGet()) {
                        userRepository.getActiveUser()
                    } else {
                        Maybe.empty()
                    }
                } else {
                    Maybe.empty()
                }
            }
    }

    fun updateExternalSignalingServer(id: Long, externalSignalingServer: ExternalSignalingServer): Single<Int> =
        userRepository.getUserWithId(id).map { user ->
            user.externalSignalingServer = externalSignalingServer
            userRepository.updateUser(user)
        }.toSingle()

    fun updateOrCreateUser(user: User): Single<Int> =
        Single.fromCallable {
            when (user.id) {
                null -> userRepository.insertUser(user).toInt()
                else -> userRepository.updateUser(user)
            }
        }

    fun saveUser(user: User): Single<Int> =
        Single.fromCallable {
            userRepository.updateUser(user)
        }

    fun setUserAsActive(user: User): Single<Boolean> {
        Log.d(TAG, "setUserAsActive:" + user.id!!)
        return userRepository.setUserAsActiveWithId(user.id!!)
            .doOnSuccess { success ->
                if (success) {
                    activeUserSubject.onNext(user)
                    activeUserStateFlow.value = user
                }
            }
    }

    fun storeProfile(username: String?, userAttributes: UserAttributes): Maybe<User> =
        findUser(userAttributes)
            .map { user: User? ->
                when (user) {
                    null -> createUser(
                        username,
                        userAttributes
                    )
                    else -> {
                        user.token = userAttributes.token
                        user.baseUrl = userAttributes.serverUrl
                        user.current = userAttributes.currentUser
                        user.userId = userAttributes.userId
                        user.token = userAttributes.token
                        user.displayName = userAttributes.displayName
                        user.clientCertificate = userAttributes.certificateAlias

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

    private fun findUser(userAttributes: UserAttributes): Maybe<User> =
        if (userAttributes.id != null) {
            userRepository.getUserWithId(userAttributes.id)
        } else {
            Maybe.empty()
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

    fun updatePushState(id: Long, state: PushConfigurationState): Single<Int> =
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
