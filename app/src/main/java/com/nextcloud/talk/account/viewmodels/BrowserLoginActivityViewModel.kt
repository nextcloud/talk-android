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
import com.nextcloud.talk.account.data.model.LoginCompletion
import com.nextcloud.talk.account.data.model.LoginResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

class BrowserLoginActivityViewModel @Inject constructor(val repository: LoginRepository) : ViewModel() {

    companion object {
        private val TAG = BrowserLoginActivityViewModel::class.java.simpleName
        private const val LOGIN_TIMEOUT_MS = 20 * 60 * 1000L
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
        data object PostLoginTooManyLoginAttempts : PostLoginViewState()
        data class PostLoginContinue(val data: Bundle) : PostLoginViewState()
        data object PostLoginDifferentAccount : PostLoginViewState()
        data object PostLoginRemovalPending : PostLoginViewState()
    }

    private val _postLoginState = MutableStateFlow<PostLoginViewState>(PostLoginViewState.None)
    val postLoginState: StateFlow<PostLoginViewState> = _postLoginState

    fun onBrowserOpened() {
        _initialLoginRequestState.value = InitialLoginViewState.None
    }

    private var savedResponse: LoginResponse? = null
    private var pollingStarted = false
    private var initialLoginJob: Job? = null
    private var loginJob: Job? = null

    // The view model survives a recreation of the activity, which starts the login again. A started login is
    // continued instead of starting a second one, whose session the browser would not authorize. After process
    // death the view model is new, so the login is started again.
    private var isLoginStarted = false

    private fun startLoginOnce(): Boolean {
        if (isLoginStarted) return false
        isLoginStarted = true
        return true
    }

    fun startWebBrowserLogin(baseUrl: String, reAuth: Boolean = false, accountToReauthorize: Long? = null) {
        if (!startLoginOnce()) return
        initialLoginJob = viewModelScope.launch {
            val response = repository.startLoginFlow(baseUrl, reAuth, accountToReauthorize)
            savedResponse = response

            if (response == null) {
                _initialLoginRequestState.value = InitialLoginViewState.InitialLoginRequestError
                return@launch
            }

            _initialLoginRequestState.value =
                InitialLoginViewState.InitialLoginRequestSuccess(response.loginUrl)
            handleWebBrowserLogin()
        }
    }

    fun handleWebBrowserLogin() {
        if (pollingStarted && _postLoginState.value != PostLoginViewState.PostLoginError) return
        savedResponse?.let { response ->
            pollingStarted = true
            _postLoginState.value = PostLoginViewState.None
            loginJob = viewModelScope.launch {
                val completion = withTimeoutOrNull(LOGIN_TIMEOUT_MS) { repository.pollLogin(response) }
                _postLoginState.value = postLoginStateFor(completion)
            }
        }
    }

    fun loginWithQR(dataString: String, reAuth: Boolean = false, accountToReauthorize: Long? = null) {
        if (!startLoginOnce()) return
        loginJob = viewModelScope.launch {
            _postLoginState.value =
                postLoginStateFor(repository.startLoginFlowFromQR(dataString, reAuth, accountToReauthorize))
        }
    }

    fun loginWithOTPQR(dataString: String, reAuth: Boolean = false, accountToReauthorize: Long? = null) {
        if (!startLoginOnce()) return
        loginJob = viewModelScope.launch {
            _postLoginState.value =
                postLoginStateFor(repository.startOTPLoginFlow(dataString, reAuth, accountToReauthorize))
        }
    }

    private suspend fun postLoginStateFor(loginCompletion: LoginCompletion?): PostLoginViewState =
        when {
            loginCompletion == null -> PostLoginViewState.PostLoginError

            loginCompletion.status == LoginRepository.HTTP_TOO_MANY_REQUESTS ->
                PostLoginViewState.PostLoginTooManyLoginAttempts

            else -> postLoginStateFor(repository.parseAndLogin(loginCompletion))
        }

    private fun postLoginStateFor(result: LoginRepository.LoginResult): PostLoginViewState =
        when (result) {
            is LoginRepository.LoginResult.NewAccount -> PostLoginViewState.PostLoginContinue(result.bundle)
            LoginRepository.LoginResult.ExistingAccount -> PostLoginViewState.PostLoginRestartApp
            LoginRepository.LoginResult.DifferentAccount -> PostLoginViewState.PostLoginDifferentAccount
            LoginRepository.LoginResult.RemovalPending -> PostLoginViewState.PostLoginRemovalPending
        }

    fun cancelLogin() {
        initialLoginJob?.cancel()
        loginJob?.cancel()
        repository.cancelLoginFlow()
        savedResponse = null
    }
}
