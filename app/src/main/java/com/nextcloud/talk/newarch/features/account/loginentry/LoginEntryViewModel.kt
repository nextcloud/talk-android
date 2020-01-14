package com.nextcloud.talk.newarch.features.account.loginentry

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.models.LoginData
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.GetCapabilitiesUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetProfileUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetSignalingSettingsUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.preferences.AppPreferences
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf
import java.net.URLDecoder

class LoginEntryViewModel constructor(
        application: Application,
        private val getProfileUseCase: GetProfileUseCase,
        private val getCapabilitiesUseCase: GetCapabilitiesUseCase,
        private val getSignalingSettingsUseCase: GetSignalingSettingsUseCase,
        private val appPreferences: AppPreferences,
        private val usersRepository: UsersRepository) :
        BaseViewModel<LoginEntryView>(application) {
    val state: MutableLiveData<LoginEntryStateWrapper> = MutableLiveData(LoginEntryStateWrapper(LoginEntryState.PENDING_CHECK, null))

    private val user = UserNgEntity(-1, "-1", "", "")

    fun parseData(prefix: String, separator: String, data: String?) {
        viewModelScope.launch {
            if (data?.startsWith(prefix) == false) {
                state.postValue(LoginEntryStateWrapper(LoginEntryState.FAILED, LoginEntryStateClarification.INVALID_PARSED_DATA))
                return@launch
            }

            data as String

            val loginData = LoginData()
            // format is xxx://login/server:xxx&user:xxx&password:xxx
            val dataWithoutPrefix = data.substring(prefix.length)
            val values = dataWithoutPrefix.split("&").toTypedArray()
            if (values.size != 3) {
                state.postValue(LoginEntryStateWrapper(LoginEntryState.FAILED, LoginEntryStateClarification.INVALID_PARSED_DATA))
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
                        state.postValue(LoginEntryStateWrapper(LoginEntryState.FAILED, LoginEntryStateClarification.INVALID_PARSED_DATA))
                        return@launch
                    }
                }
            }

            if (!loginData.serverUrl.isNullOrEmpty() && !loginData.username.isNullOrEmpty() && !loginData.token.isNullOrEmpty()) {
                storeCredentialsOrVerify(loginData)
            } else {
                state.postValue(LoginEntryStateWrapper(LoginEntryState.FAILED, LoginEntryStateClarification.INVALID_PARSED_DATA))
                return@launch
            }


        }
    }

    private suspend fun storeCredentialsOrVerify(loginData: LoginData) {
        // username and server url will be null here for sure because we do a check earlier in the process
        val user = usersRepository.getUserWithUsernameAndServer(loginData.username!!, loginData.serverUrl!!)
        if (user != null) {
            user.token = loginData.token
            usersRepository.updateUser(user)
            state.postValue(LoginEntryStateWrapper(LoginEntryState.OK, LoginEntryStateClarification.ACCOUNT_UPDATED))
        } else {
            getProfile(loginData)
        }
    }

    private fun getProfile(loginData: LoginData) {
        user.username = loginData.username!!
        user.baseUrl = loginData.serverUrl!!
        getProfileUseCase.invoke(viewModelScope, parametersOf(user), object : UseCaseResponse<UserProfileOverall> {
            override suspend fun onSuccess(result: UserProfileOverall) {
                result.ocs.data.userId?.let { userId ->
                    user.displayName = result.ocs.data.displayName
                    user.userId = userId
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
        getCapabilitiesUseCase.invoke(viewModelScope, parametersOf(user.baseUrl), object : UseCaseResponse<CapabilitiesOverall> {
            override suspend fun onSuccess(result: CapabilitiesOverall) {
                user.capabilities = result.ocs.data.capabilities
                getSignalingSettings()
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                state.postValue(LoginEntryStateWrapper(LoginEntryState.FAILED, LoginEntryStateClarification.CAPABILITIES_FETCH_FAILED))
            }
        })
    }

    private fun getSignalingSettings() {
        getSignalingSettingsUseCase.invoke(viewModelScope, parametersOf(user), object : UseCaseResponse<SignalingSettingsOverall> {
            override suspend fun onSuccess(result: SignalingSettingsOverall) {
                user.signalingSettings = result.ocs.signalingSettings
                registerForPush()
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                state.postValue(LoginEntryStateWrapper(LoginEntryState.FAILED, LoginEntryStateClarification.SIGNALING_SETTINGS_FETCH_FAILED))
            }
        })

    }

    private fun registerForPush() {
        val token = appPreferences.pushToken
        if (!token.isNullOrBlank()) {
            registerForPushWithServer(token)
        } else {
            state.postValue(LoginEntryStateWrapper(LoginEntryState.OK, LoginEntryStateClarification.PUSH_REGISTRATION_MISSING_TOKEN))
        }
    }

    private fun registerForPushWithServer(token: String) {

    }

    private fun registerForPushWithProxy() {

    }
}