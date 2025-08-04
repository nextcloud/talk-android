/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.viewmodels

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.account.data.LoginRepository
import com.nextcloud.talk.account.data.LoginRepository.Companion.PARSE_LOGIN
import com.nextcloud.talk.account.data.LoginRepository.Companion.START_LOGIN_FLOW
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

//  the point of the view model is to maintain the state of the view (Activity or Activity+XML)
//  thus all the business logic like parsing loginData should be in the repository, only returned to
//  the viewModel and then to the activity to reflect changes to the app state -> app UI

//  TODO test for proper state changes upon an otherwise working repository and UI layer, making sure the view model
//      properly models the view's state
class BrowserLoginActivityViewModel @Inject constructor(val repository: LoginRepository): ViewModel() {

    companion object {
        private val TAG = BrowserLoginActivityViewModel::class.java.simpleName
    }

    sealed class InitialLoginViewState {
        data object None : InitialLoginViewState()
        data class InitialLoginRequestSuccess(val loginUrl: String): InitialLoginViewState()
        data object InitialLoginRequestError: InitialLoginViewState()
    }

    private val _initialLoginRequestState = MutableStateFlow<InitialLoginViewState>(InitialLoginViewState.None)
    val initialLoginRequestState: Flow<InitialLoginViewState>
        get() = _initialLoginRequestState

    sealed class PostLoginViewState {
        data object None: PostLoginViewState()
        data object PostLoginRestartApp: PostLoginViewState()
        data object PostLoginError: PostLoginViewState()
        data class PostLoginContinue(val data: Bundle): PostLoginViewState()
    }

    private val _postLoginState = MutableStateFlow<PostLoginViewState>(PostLoginViewState.None)
    val postLoginState: Flow<PostLoginViewState>
        get() = _postLoginState

    /**
     * Login Repository exit points
     */
    val errorFlow: Flow<Pair<String, String>> get() = repository.errorFlow.onEach { pair ->
        val tag = pair.first

        when (tag) {
            START_LOGIN_FLOW -> _initialLoginRequestState.value = InitialLoginViewState.InitialLoginRequestError
            PARSE_LOGIN -> _postLoginState.value = PostLoginViewState.PostLoginError

            else -> {}
        }
    }

    val launchWebFlow: Flow<String> get() = repository.launchWebFlow.onEach { url ->
        _initialLoginRequestState.value = InitialLoginViewState.InitialLoginRequestSuccess(url)
    }

    val restartAppFlow: Flow<Boolean> get() = repository.restartAppFlow.onEach {
        _postLoginState.value = PostLoginViewState.PostLoginRestartApp
    }

    val continueLoginFlow: Flow<Bundle> get() = repository.continueLoginFlow.onEach { bundle ->
        _postLoginState.value = PostLoginViewState.PostLoginContinue(bundle)
    }

    init {
        viewModelScope.launch { errorFlow.collect() }

        viewModelScope.launch { launchWebFlow.collect() }

        viewModelScope.launch { restartAppFlow.collect() }

        viewModelScope.launch { continueLoginFlow.collect() }
    }

    /**
     * Login Repository Entry points
     */

    fun loginNormally(baseUrl: String, reAuth: Boolean = false) = repository.startLoginFlow(baseUrl, reAuth)

    fun loginWithQR(dataString: String, reAuth: Boolean = false) = repository.startLoginFlowFromQR(dataString, reAuth)



}
