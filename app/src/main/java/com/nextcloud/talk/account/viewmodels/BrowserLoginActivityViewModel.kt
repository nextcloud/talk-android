/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.viewmodels

import androidx.lifecycle.ViewModel
import com.nextcloud.talk.models.LoginData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

//  the point of the view model is to maintain the state of the view (Activity or Activity+XML)
//  thus all the business logic like parsing loginData should be in the repository, only returned to
//  the viewModel and then to the activity to reflect changes to the app state -> app UI

//  TODO test for proper state changes upon an otherwise working repository and UI layer, making sure the view model
//      properly models the view's state
class BrowserLoginActivityViewModel @Inject constructor(
    // TODO set up provide login repository and connect the dots
): ViewModel() {


    companion object {
        private val TAG = BrowserLoginActivityViewModel::class.java.simpleName
    }

    sealed class InitialLoginViewState {
        data object None : InitialLoginViewState()
        data class InitialLoginRequestSuccess(val loginUrl: String): InitialLoginViewState()
        data class InitialLoginRequestError(val exception: Exception): InitialLoginViewState()
    }

    private val _initialLoginRequestState = MutableStateFlow<InitialLoginViewState>(InitialLoginViewState.None)
    val initialLoginRequestState: Flow<InitialLoginViewState>
        get() = _initialLoginRequestState

    sealed class PostLoginViewState {
        data object None: PostLoginViewState()
        data object PostLoginRestart: PostLoginViewState()
        data object PostLoginAccountRemovalAndRestart: PostLoginViewState()
        data class PostLoginError(val e: Exception): PostLoginViewState()
        data class PostLoginUserExists(val data: LoginData): PostLoginViewState()
        data class PostLoginAccountVerification(val data: LoginData): PostLoginViewState()
    }

    private val _postLoginState = MutableStateFlow<PostLoginViewState>(PostLoginViewState.None)
    val postLoginState: Flow<PostLoginViewState>
        get() = _postLoginState

    // TODO start pool login and notify UI on result(s). Don't make viewmodel lifecycle aware, breaks MVVM
}
