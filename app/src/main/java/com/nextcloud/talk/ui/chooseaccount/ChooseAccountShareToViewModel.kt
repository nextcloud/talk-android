/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chooseaccount

import android.util.Log
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.ui.chooseaccount.model.ChooseAccountShareToViewState
import com.nextcloud.talk.ui.chooseaccount.model.LoadUsersStartStateChooseAccountShareTo
import com.nextcloud.talk.ui.chooseaccount.model.LoadUsersSuccessStateChooseAccountShareTo
import com.nextcloud.talk.ui.chooseaccount.model.SwitchUserErrorStateChooseAccountShareTo
import com.nextcloud.talk.ui.chooseaccount.model.SwitchUserSuccessStateChooseAccountShareTo
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

    private val _chooseAccountShareToViewState: MutableStateFlow<ChooseAccountShareToViewState> =
        MutableStateFlow(LoadUsersStartStateChooseAccountShareTo)
    val chooseAccountShareToViewState: StateFlow<ChooseAccountShareToViewState> =
        _chooseAccountShareToViewState.asStateFlow()

    private val disposables = CompositeDisposable()

    fun loadUsers() {
        _chooseAccountShareToViewState.value = LoadUsersStartStateChooseAccountShareTo
        disposables.add(
            userManager.users
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { users ->
                        _chooseAccountShareToViewState.value =
                            LoadUsersSuccessStateChooseAccountShareTo(users.filter { !it.current })
                    },
                    { e ->
                        Log.e(TAG, "Error loading users", e)
                        _chooseAccountShareToViewState.value = LoadUsersSuccessStateChooseAccountShareTo(emptyList())
                    }
                )
        )
    }

    fun switchToUser(user: User) {
        disposables.add(
            userManager.setUserAsActive(user)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe({ success ->
                    _chooseAccountShareToViewState.value =
                        if (success) {
                            SwitchUserSuccessStateChooseAccountShareTo
                        } else {
                            SwitchUserErrorStateChooseAccountShareTo
                        }
                }, { e ->
                    Log.e(TAG, "Error switching user", e)
                    _chooseAccountShareToViewState.value = SwitchUserErrorStateChooseAccountShareTo
                })
        )
    }

    override fun onCleared() {
        disposables.dispose()
        super.onCleared()
    }

    companion object {
        private val TAG = ChooseAccountShareToViewModel::class.simpleName
    }
}
