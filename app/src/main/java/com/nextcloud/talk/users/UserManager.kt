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
import com.nextcloud.talk.data.user.model.UserNgEntity
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import java.lang.Boolean.FALSE
import java.lang.Boolean.TRUE

@Suppress("TooManyFunctions")
class UserManager internal constructor(private val userRepository: UsersRepository) : CurrentUserProviderNew {
    val users: Observable<List<UserNgEntity>>
        get() = userRepository.getUsers()

    val usersScheduledForDeletion: Observable<List<UserNgEntity>>
        get() = userRepository.getUsersScheduledForDeletion()

    private fun setAnyUserAndSetAsActive(): Observable<UserNgEntity?> {
        val results = userRepository.getUsersNotScheduledForDeletion()

        var result: UserNgEntity? = null

        results.subscribe(object : Observer<List<UserNgEntity>> {
            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onNext(users: List<UserNgEntity>) {
                if (users.isNotEmpty()) {
                    val user = users[0]
                    user.current = true
                    userRepository.updateUser(user)
                    result = user
                }
            }

            override fun onError(e: Throwable) {
                // unused atm
            }

            override fun onComplete() {
                // unused atm
            }
        })

        return Observable.just(result)
    }

    override val currentUser: UserNgEntity?
        get() {
            return userRepository.getActiveUserSynchronously()
        }

    fun deleteUser(internalId: Long) {
        userRepository.deleteUserWithId(internalId)
    }

    fun deleteUserWithId(internalId: Long) {
        userRepository.deleteUserWithId(internalId)
    }

    fun getUserById(userId: String): Observable<UserNgEntity?> {
        return userRepository.getUserWithUserId(userId)
    }

    fun getUserWithId(id: Long): Observable<UserNgEntity?> {
        return userRepository.getUserWithId(id)
    }

    fun disableAllUsersWithoutId(userId: Long) {
        val results = userRepository.getUsersWithoutUserId(userId)

        results.subscribe(object : Observer<List<UserNgEntity>> {
            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onNext(users: List<UserNgEntity>) {
                if (users.isNotEmpty()) {
                    for (entity in users) {
                        entity.current = false
                        userRepository.updateUser(entity)
                    }
                }
            }

            override fun onError(e: Throwable) {
                // unused atm
            }

            override fun onComplete() {
                // unused atm
            }
        })
    }

    fun checkIfUserIsScheduledForDeletion(username: String, server: String): Observable<Boolean> {
        val results = userRepository.getUserWithUsernameAndServer(username, server)
        var result = FALSE

        results.subscribe(object : Observer<UserNgEntity?> {
            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onNext(user: UserNgEntity) {
                result = user.scheduledForDeletion
            }

            override fun onError(e: Throwable) {
                // unused atm
            }

            override fun onComplete() {
                // unused atm
            }
        })

        return Observable.just(result)
    }

    fun getUserWithInternalId(id: Long): Observable<UserNgEntity?> {
        return userRepository.getUserWithIdNotScheduledForDeletion(id)
    }

    fun getIfUserWithUsernameAndServer(username: String, server: String): Observable<Boolean> {
        val results = userRepository.getUserWithUsernameAndServer(username, server)
        var result = FALSE

        results.subscribe(object : Observer<UserNgEntity?> {
            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onNext(users: UserNgEntity) {
                result = TRUE
            }

            override fun onError(e: Throwable) {
                // unused atm
            }

            override fun onComplete() {
                // unused atm
            }
        })

        return Observable.just(result)
    }

    suspend fun scheduleUserForDeletionWithId(id: Long): Observable<Boolean> {
        val results = userRepository.getUserWithId(id)
        var result = FALSE

        results.subscribe(object : Observer<UserNgEntity?> {
            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onNext(user: UserNgEntity) {
                user.scheduledForDeletion = true
                user.current = false
                userRepository.updateUser(user)
            }

            override fun onError(e: Throwable) {
                // unused atm
            }

            override fun onComplete() {
                // unused atm
            }
        })

        results.subscribe(object : Observer<UserNgEntity?> {
            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onNext(user: UserNgEntity) {
                user.scheduledForDeletion = true
                user.current = false
                userRepository.updateUser(user)
            }

            override fun onError(e: Throwable) {
                // unused atm
            }

            override fun onComplete() {
                // unused atm
            }
        })

        setAnyUserAndSetAsActive().subscribe(object : Observer<UserNgEntity?> {
            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onNext(user: UserNgEntity) {
                result = TRUE
            }

            override fun onError(e: Throwable) {
                // unused atm
            }

            override fun onComplete() {
                // unused atm
            }
        })

        return Observable.just(result)
    }

    fun createOrUpdateUser(
        username: String?,
        userAttributes: UserAttributes,
    ): Observable<UserNgEntity?> {
        var user: UserNgEntity? = null

        if (userAttributes.id == null && username != null && userAttributes.serverUrl != null) {
            userRepository.getUserWithUsernameAndServer(username, userAttributes.serverUrl)
                .subscribe(object : Observer<UserNgEntity?> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(userEntity: UserNgEntity) {
                        user = userEntity
                    }

                    override fun onError(e: Throwable) {
                        // unused atm
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        } else if (userAttributes.id != null) {
            userRepository.getUserWithId(userAttributes.id)
                .subscribe(object : Observer<UserNgEntity?> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(userEntity: UserNgEntity) {
                        user = userEntity
                    }

                    override fun onError(e: Throwable) {
                        // unused atm
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }

        if (user == null) {
            user = createUser(
                username,
                userAttributes
            )
        } else {
            updateUserData(
                user!!,
                userAttributes
            )
        }

        userRepository.insertUser(user!!)
        return userRepository.getUserWithIdLiveData(user!!.id)
    }

    fun getUserWithUsernameAndServer(username: String, server: String): Observable<UserNgEntity?> {
        return userRepository.getUserWithUsernameAndServer(username, server)
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
