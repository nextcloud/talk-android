/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.viewmodels

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.account.data.LoginRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class BrowserLoginActivityViewModel @Inject constructor(val repository: LoginRepository) : ViewModel() {

    companion object {
        private val TAG = BrowserLoginActivityViewModel::class.java.simpleName
    }

    sealed class InitialLoginViewState {
        data object None : InitialLoginViewState()
        data class InitialLoginRequestSuccess(val loginUrl: String) : InitialLoginViewState()
        data object InitialLoginRequestError : InitialLoginViewState()
    }

    private val _initialLoginRequestState = MutableStateFlow<InitialLoginViewState>(InitialLoginViewState.None)
    val initialLoginRequestState: StateFlow<InitialLoginViewState> = _initialLoginRequestState

    sealed class PostLoginViewState {
        data object None : PostLoginViewState()
        data object PostLoginRestartApp : PostLoginViewState()
        data object PostLoginError : PostLoginViewState()
        data class PostLoginContinue(val data: Bundle) : PostLoginViewState()
    }

    private val _postLoginState = MutableStateFlow<PostLoginViewState>(PostLoginViewState.None)
    val postLoginState: StateFlow<PostLoginViewState> = _postLoginState

    fun loginNormally(baseUrl: String, reAuth: Boolean = false) {
        viewModelScope.launch {
            val response = repository.startLoginFlow(baseUrl, reAuth)

            if (response == null) {
                _initialLoginRequestState.value = InitialLoginViewState.InitialLoginRequestError
                return@launch
            }

            _initialLoginRequestState.value =
                InitialLoginViewState.InitialLoginRequestSuccess(response.loginUrl)

            val loginCompletionResponse = repository.pollLogin(response)

            if (loginCompletionResponse == null) {
                _postLoginState.value = PostLoginViewState.PostLoginError
                return@launch
            }

            val bundle = repository.parseAndLogin(loginCompletionResponse)
            if (bundle == null) {
                _postLoginState.value = PostLoginViewState.PostLoginRestartApp
                return@launch
            }

            _postLoginState.value = PostLoginViewState.PostLoginContinue(bundle)
        }
    }

    fun loginWithQR(dataString: String, reAuth: Boolean = false) {
        viewModelScope.launch {
            val loginCompletionResponse = repository.startLoginFlowFromQR(dataString, reAuth)
            if (loginCompletionResponse == null) {
                _postLoginState.value = PostLoginViewState.PostLoginError
                return@launch
            }

            val bundle = repository.parseAndLogin(loginCompletionResponse)
            if (bundle == null) {
                _postLoginState.value = PostLoginViewState.PostLoginRestartApp
                return@launch
            }

            _postLoginState.value = PostLoginViewState.PostLoginContinue(bundle)
        }
    }

    fun cancelLogin() = repository.cancelLoginFlow()
}
