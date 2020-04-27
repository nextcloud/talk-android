/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.features.account.loginentry

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.models.LoginData
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.models.json.push.PushConfiguration
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.models.json.push.PushConfigurationStateWrapper
import com.nextcloud.talk.models.json.push.PushRegistrationOverall
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.newarch.mvvm.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.*
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.local.models.other.UserStatus
import com.nextcloud.talk.newarch.local.models.toUser
import com.nextcloud.talk.newarch.local.models.toUserEntity
import com.nextcloud.talk.newarch.utils.NetworkComponents
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.parameter.parametersOf
import java.net.URLDecoder

class LoginEntryViewModel(
        application: Application,
        private val networkComponents: NetworkComponents,
        private val appPreferences: AppPreferences,
        private val usersRepository: UsersRepository) :
        BaseViewModel<LoginEntryView>(application) {
    val state: MutableLiveData<LoginEntryStateWrapper> = MutableLiveData(LoginEntryStateWrapper(LoginEntryState.PENDING_CHECK, null))

    private var user: User? = null
    private var updatingUser = false

    fun parseData(prefix: String, separator: String, data: String?) {
        state.postValue(LoginEntryStateWrapper(LoginEntryState.CHECKING, null))
        viewModelScope.launch {
            if (data?.startsWith(prefix) == false) {
                return@launch
            }

            data as String

            val loginData = LoginData()
            // format is xxx://login/server:xxx&user:xxx&password:xxx
            val dataWithoutPrefix = data.substring(prefix.length)
            val values = dataWithoutPrefix.split("&").toTypedArray()
            if (values.size != 3) {
                return@launch
            }

            for (value in values) {
                when {
                    value.startsWith("user$separator") -> {
                        loginData.username = URLDecoder.decode(
                                value.substring("user$separator".length)
                        )
                    }
                    value.startsWith("password$separator") -> {
                        loginData.token = URLDecoder.decode(
                                value.substring("password$separator".length)
                        )
                    }
                    value.startsWith("server$separator") -> {
                        loginData.serverUrl = URLDecoder.decode(
                                value.substring("server$separator".length)
                        )
                    }
                    else -> {
                        // fail
                        return@launch
                    }
                }
            }

            if (!loginData.serverUrl.isNullOrEmpty() && !loginData.username.isNullOrEmpty() && !loginData.token.isNullOrEmpty()) {
                storeCredentialsOrVerify(loginData)
            } else {
                state.postValue(LoginEntryStateWrapper(LoginEntryState.FAILED, LoginEntryStateClarification.INVALID_PARSED_DATA))
            }
        }
    }

    private suspend fun storeCredentialsOrVerify(loginData: LoginData) = withContext(Dispatchers.IO) {
        // username and server url will be null here for sure because we do a check earlier in the process
        val userIfExists = usersRepository.getUserWithUsernameAndServer(loginData.username!!, loginData.serverUrl!!)
        if (userIfExists != null) {
            updatingUser = true
            user = userIfExists.toUser()
            user!!.token = loginData.token
            usersRepository.updateUser(user!!.toUserEntity())
            // complicated - we need to unregister, etc, etc, but not yet
            state.postValue(LoginEntryStateWrapper(LoginEntryState.OK, LoginEntryStateClarification.ACCOUNT_UPDATED))
        } else {
            user = User(null, "", "", "")
            getProfile(loginData)
        }
    }

    private fun getProfile(loginData: LoginData) {
        user!!.id = -1
        user!!.username = loginData.username!!
        user!!.baseUrl = loginData.serverUrl!!
        user!!.token = loginData.token
        val repository = networkComponents.getRepository(false, user!!)
        val getProfileUseCase = GetProfileUseCase(repository, ApiErrorHandler())
        getProfileUseCase.invoke(viewModelScope, parametersOf(user!!.toUserEntity()), object : UseCaseResponse<UserProfileOverall> {
            override suspend fun onSuccess(result: UserProfileOverall) {
                result.ocs.data.userId?.let { userId ->
                    user!!.displayName = result.ocs.data.displayName
                    user!!.userId = userId
                    getCapabilities()
                } ?: run {
                    state.postValue(LoginEntryStateWrapper(LoginEntryState.FAILED, LoginEntryStateClarification.PROFILE_FETCH_FAILED))
                }
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                state.postValue(LoginEntryStateWrapper(LoginEntryState.FAILED, LoginEntryStateClarification.PROFILE_FETCH_FAILED))
            }
        })
    }

    private fun getCapabilities() {
        val repository = networkComponents.getRepository(false, user!!)
        val getCapabilitiesUseCase = GetCapabilitiesUseCase(repository, ApiErrorHandler())
        getCapabilitiesUseCase.invoke(viewModelScope, parametersOf(user!!.baseUrl), object : UseCaseResponse<CapabilitiesOverall> {
            override suspend fun onSuccess(result: CapabilitiesOverall) {
                user!!.capabilities = result.ocs.data.capabilities
                getSignalingSettings()
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                state.postValue(LoginEntryStateWrapper(LoginEntryState.FAILED, LoginEntryStateClarification.CAPABILITIES_FETCH_FAILED))
            }
        })
    }

    private fun getSignalingSettings() {
        val repository = networkComponents.getRepository(false, user!!)
        val getSignalingSettingsUseCase = GetSignalingSettingsUseCase(repository, ApiErrorHandler())
        getSignalingSettingsUseCase.invoke(viewModelScope, parametersOf(user!!.toUserEntity()), object : UseCaseResponse<SignalingSettingsOverall> {
            override suspend fun onSuccess(result: SignalingSettingsOverall) {
                user!!.signalingSettings = result.ocs.signalingSettings
                val pushConfiguration = PushConfiguration()
                val pushConfigurationStateWrapper = PushConfigurationStateWrapper(PushConfigurationState.PENDING, 0)
                pushConfiguration.pushConfigurationStateWrapper = pushConfigurationStateWrapper
                user!!.pushConfiguration = pushConfiguration
                user!!.id = null
                withContext(Dispatchers.IO) {
                    user!!.id = usersRepository.insertUser(user!!.toUserEntity())
                    usersRepository.setUserAsActiveWithId(user!!.id!!)
                    user!!.status = UserStatus.ACTIVE
                    registerForPush()
                }
            }


            override suspend fun onError(errorModel: ErrorModel?) {
                state.postValue(LoginEntryStateWrapper(LoginEntryState.FAILED, LoginEntryStateClarification.SIGNALING_SETTINGS_FETCH_FAILED))
            }
        })
    }

    private suspend fun registerForPush() = withContext(Dispatchers.IO) {
        val token = appPreferences.pushToken
        if (!token.isNullOrBlank()) {
            user!!.pushConfiguration!!.pushToken = token
            usersRepository.updateUser(user!!.toUserEntity())
            registerForPushWithServer(token)
        } else {
            state.postValue(LoginEntryStateWrapper(LoginEntryState.OK, LoginEntryStateClarification.PUSH_REGISTRATION_MISSING_TOKEN))
        }
    }

    private fun registerForPushWithServer(token: String) {
        val options = PushUtils(usersRepository).getMapForPushRegistrationWithServer(context, token)
        val repository = networkComponents.getRepository(false, user!!)
        val registerPushWithServerUseCase = RegisterPushWithServerUseCase(repository, ApiErrorHandler())
        registerPushWithServerUseCase.invoke(viewModelScope, parametersOf(user!!.toUserEntity(), options), object : UseCaseResponse<PushRegistrationOverall> {
            override suspend fun onSuccess(result: PushRegistrationOverall) {
                user!!.pushConfiguration!!.deviceIdentifier = result.ocs.data.deviceIdentifier
                user!!.pushConfiguration!!.deviceIdentifierSignature = result.ocs.data.signature
                user!!.pushConfiguration!!.userPublicKey = result.ocs.data.publicKey
                user!!.pushConfiguration!!.pushConfigurationStateWrapper = PushConfigurationStateWrapper(PushConfigurationState.SERVER_REGISTRATION_DONE, null)
                usersRepository.updateUser(user!!.toUserEntity())
                registerForPushWithProxy()
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                user!!.pushConfiguration!!.pushConfigurationStateWrapper!!.pushConfigurationState = PushConfigurationState.FAILED_WITH_SERVER_REGISTRATION
                user!!.pushConfiguration!!.pushConfigurationStateWrapper!!.reason = errorModel?.code
                usersRepository.updateUser(user!!.toUserEntity())
                state.postValue(LoginEntryStateWrapper(LoginEntryState.OK, LoginEntryStateClarification.PUSH_REGISTRATION_WITH_SERVER_FAILED))
            }
        })
    }

    private suspend fun registerForPushWithProxy() {
        val options = PushUtils(usersRepository).getMapForPushRegistrationWithServer(user!!.toUserEntity())

        if (options != null) {
            val repository = networkComponents.getRepository(false, user!!)
            val registerPushWithProxyUseCase = RegisterPushWithProxyUseCase(repository, ApiErrorHandler())

            registerPushWithProxyUseCase.invoke(viewModelScope, parametersOf(user!!.toUserEntity(), options), object : UseCaseResponse<Any> {
                override suspend fun onSuccess(result: Any) {
                    user!!.pushConfiguration!!.pushConfigurationStateWrapper = PushConfigurationStateWrapper(PushConfigurationState.PROXY_REGISTRATION_DONE, null)
                    withContext(Dispatchers.IO) {
                        usersRepository.updateUser(user!!.toUserEntity())
                        state.postValue(LoginEntryStateWrapper(LoginEntryState.OK, if (!updatingUser) LoginEntryStateClarification.ACCOUNT_CREATED else LoginEntryStateClarification.ACCOUNT_UPDATED))
                    }
                }

                override suspend fun onError(errorModel: ErrorModel?) {
                    user!!.pushConfiguration!!.pushConfigurationStateWrapper!!.pushConfigurationState = PushConfigurationState.FAILED_WITH_PROXY_REGISTRATION
                    user!!.pushConfiguration!!.pushConfigurationStateWrapper!!.reason = errorModel?.code
                    withContext(Dispatchers.IO) {
                        usersRepository.updateUser(user!!.toUserEntity())
                        state.postValue(LoginEntryStateWrapper(LoginEntryState.OK, LoginEntryStateClarification.PUSH_REGISTRATION_WITH_PUSH_PROXY_FAILED))
                    }
                }
            })
        } else {
            user!!.pushConfiguration!!.pushConfigurationStateWrapper!!.pushConfigurationState = PushConfigurationState.FAILED_WITH_PROXY_REGISTRATION
            withContext(Dispatchers.IO) {
                usersRepository.updateUser(user!!.toUserEntity())
                state.postValue(LoginEntryStateWrapper(LoginEntryState.OK, LoginEntryStateClarification.PUSH_REGISTRATION_WITH_PUSH_PROXY_FAILED))
            }
        }
    }
}