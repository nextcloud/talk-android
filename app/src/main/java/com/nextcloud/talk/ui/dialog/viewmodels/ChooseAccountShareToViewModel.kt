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
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class ChooseAccountShareToViewModel @Inject constructor(private val userManager: UserManager) : ViewModel() {

    val currentUser: User? = userManager.currentUser.blockingGet()

    sealed interface ViewState

    object LoadUsersStartState : ViewState
    open class LoadUsersSuccessState(val users: List<User>) : ViewState
    object SwitchUserSuccessState : ViewState
    object SwitchUserErrorState : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(LoadUsersStartState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    fun loadUsers() {
        _viewState.value = LoadUsersStartState
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
    }

    fun switchToUser(user: User) {
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
    }

    companion object {
        private val TAG = ChooseAccountShareToViewModel::class.simpleName
    }
}
