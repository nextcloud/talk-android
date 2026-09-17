/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chooseaccount

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.ui.chooseaccount.model.ChooseAccountShareToViewState
import com.nextcloud.talk.ui.chooseaccount.model.LoadUsersStartStateChooseAccountShareTo
import com.nextcloud.talk.ui.chooseaccount.model.LoadUsersSuccessStateChooseAccountShareTo
import com.nextcloud.talk.ui.chooseaccount.model.SwitchUserErrorStateChooseAccountShareTo
import com.nextcloud.talk.ui.chooseaccount.model.SwitchUserSuccessStateChooseAccountShareTo
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.database.user.CurrentUserProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class ChooseAccountShareToViewModel @Inject constructor(
    private val userManager: UserManager,
    currentUserProvider: CurrentUserProvider
) : ViewModel() {

    val currentUser: User? = runBlocking { currentUserProvider.getCurrentUser() }.getOrNull()

    private val _chooseAccountShareToViewState: MutableStateFlow<ChooseAccountShareToViewState> =
        MutableStateFlow(LoadUsersStartStateChooseAccountShareTo)
    val chooseAccountShareToViewState: StateFlow<ChooseAccountShareToViewState> =
        _chooseAccountShareToViewState.asStateFlow()

    @Suppress("TooGenericExceptionCaught")
    fun loadUsers() {
        _chooseAccountShareToViewState.value = LoadUsersStartStateChooseAccountShareTo
        viewModelScope.launch {
            try {
                val users = userManager.getUsers()
                _chooseAccountShareToViewState.value =
                    LoadUsersSuccessStateChooseAccountShareTo(users.filter { !it.current })
            } catch (e: Exception) {
                Log.e(TAG, "Error loading users", e)
                _chooseAccountShareToViewState.value = LoadUsersSuccessStateChooseAccountShareTo(emptyList())
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun switchToUser(user: User) {
        viewModelScope.launch {
            try {
                val success = userManager.setUserAsActiveSuspend(user)
                _chooseAccountShareToViewState.value =
                    if (success) {
                        SwitchUserSuccessStateChooseAccountShareTo
                    } else {
                        SwitchUserErrorStateChooseAccountShareTo
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error switching user", e)
                _chooseAccountShareToViewState.value = SwitchUserErrorStateChooseAccountShareTo
            }
        }
    }

    companion object {
        private val TAG = ChooseAccountShareToViewModel::class.simpleName
    }
}
