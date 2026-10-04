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
import com.nextcloud.talk.account.data.PendingBrowserLoginStore
import com.nextcloud.talk.account.data.model.LoginResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        data object PostLoginDifferentAccount : PostLoginViewState()
    }

    private val _postLoginState = MutableStateFlow<PostLoginViewState>(PostLoginViewState.None)
    val postLoginState: StateFlow<PostLoginViewState> = _postLoginState

    private val _waitingForBrowserState = MutableStateFlow(false)
    val waitingForBrowserState = _waitingForBrowserState.asStateFlow()

    fun setWaitingForBrowser(value: Boolean) {
        _waitingForBrowserState.value = value
    }

    private var savedResponse: LoginResponse? = null

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
        viewModelScope.launch {
            val response = repository.startLoginFlow(baseUrl, reAuth, accountToReauthorize)
            savedResponse = response

            if (response == null) {
                _initialLoginRequestState.value = InitialLoginViewState.InitialLoginRequestError
                return@launch
            }

            // Survives this view model, see PendingBrowserLoginStore.
            PendingBrowserLoginStore.save(response, reAuth, accountToReauthorize)

            _initialLoginRequestState.value =
                InitialLoginViewState.InitialLoginRequestSuccess(response.loginUrl)
        }
    }

    /**
     * Continues the browser login that a destroyed [com.nextcloud.talk.account.BrowserLoginActivity] left unfinished:
     * no new login request and no new browser, only the polling of the saved response.
     */
    fun resumeWebBrowserLogin() {
        if (!startLoginOnce()) return
        val pending = PendingBrowserLoginStore.active()
        if (pending == null) {
            _postLoginState.value = PostLoginViewState.PostLoginError
            return
        }
        savedResponse = pending.response
        repository.resumeLoginFlow(pending.reAuth, pending.accountToReauthorize)
        handleWebBrowserLogin()
    }

    fun handleWebBrowserLogin() {
        savedResponse?.let { response ->
            viewModelScope.launch {
                val loginCompletionResponse = repository.pollLogin(response)
                // Only reached when the poll ended. If this view model is cleared first, the login stays pending.
                PendingBrowserLoginStore.clear()

                if (loginCompletionResponse == null) {
                    _postLoginState.value = PostLoginViewState.PostLoginError
                    return@launch
                }

                _postLoginState.value = postLoginStateFor(repository.parseAndLogin(loginCompletionResponse))
            }
        }
    }

    fun loginWithQR(dataString: String, reAuth: Boolean = false, accountToReauthorize: Long? = null) {
        if (!startLoginOnce()) return
        viewModelScope.launch {
            val loginCompletionResponse = repository.startLoginFlowFromQR(dataString, reAuth, accountToReauthorize)
            if (loginCompletionResponse == null) {
                _postLoginState.value = PostLoginViewState.PostLoginError
                return@launch
            }

            _postLoginState.value = postLoginStateFor(repository.parseAndLogin(loginCompletionResponse))
        }
    }

    fun loginWithOTPQR(dataString: String, reAuth: Boolean = false, accountToReauthorize: Long? = null) {
        if (!startLoginOnce()) return
        viewModelScope.launch {
            val loginCompletionResponse = repository.startOTPLoginFlow(dataString, reAuth, accountToReauthorize)
            if (loginCompletionResponse == null) {
                _postLoginState.value = PostLoginViewState.PostLoginError
                return@launch
            }

            _postLoginState.value = postLoginStateFor(repository.parseAndLogin(loginCompletionResponse))
        }
    }

    private fun postLoginStateFor(result: LoginRepository.LoginResult): PostLoginViewState =
        when (result) {
            is LoginRepository.LoginResult.NewAccount -> PostLoginViewState.PostLoginContinue(result.bundle)
            LoginRepository.LoginResult.ExistingAccount -> PostLoginViewState.PostLoginRestartApp
            LoginRepository.LoginResult.DifferentAccount -> PostLoginViewState.PostLoginDifferentAccount
        }

    fun cancelLogin() {
        PendingBrowserLoginStore.clear()
        repository.cancelLoginFlow()
    }
}
