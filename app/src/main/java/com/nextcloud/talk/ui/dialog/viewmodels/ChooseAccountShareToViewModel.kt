/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class ChooseAccountShareToViewModel @Inject constructor(private val userManager: UserManager) : ViewModel() {

    val currentUser: User? = userManager.currentUser.blockingGet()

    sealed interface ViewState

    object LoadUsersStartState : ViewState
    open class LoadUsersSuccessState(val users: List<User>) : ViewState
    object SwitchUserSuccessState : ViewState
    object SwitchUserErrorState : ViewState

    private val _viewState: MutableStateFlow<ViewState> = MutableStateFlow(LoadUsersStartState)
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private val disposables = CompositeDisposable()

    fun loadUsers() {
        _viewState.value = LoadUsersStartState
        disposables.add(
            userManager.users
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { users -> _viewState.value = LoadUsersSuccessState(users.filter { !it.current }) },
                    { e ->
                        Log.e(TAG, "Error loading users", e)
                        _viewState.value = LoadUsersSuccessState(emptyList())
                    }
                )
        )
    }

    fun switchToUser(user: User) {
        disposables.add(
            userManager.setUserAsActive(user)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { success -> _viewState.value = if (success) SwitchUserSuccessState else SwitchUserErrorState },
                    { e ->
                        Log.e(TAG, "Error switching user", e)
                        _viewState.value = SwitchUserErrorState
                    }
                )
        )
    }

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }

    companion object {
        private val TAG = ChooseAccountShareToViewModel::class.simpleName
    }
}
