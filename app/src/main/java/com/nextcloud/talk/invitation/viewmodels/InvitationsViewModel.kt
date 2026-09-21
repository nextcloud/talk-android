/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.invitation.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.invitation.data.ActionEnum
import com.nextcloud.talk.invitation.data.Invitation
import com.nextcloud.talk.invitation.data.InvitationActionModel
import com.nextcloud.talk.invitation.data.InvitationsModel
import com.nextcloud.talk.invitation.data.InvitationsRepository
import com.nextcloud.talk.logger.Logger
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class InvitationsViewModel @Inject constructor(
    private val repository: InvitationsRepository,
    private val logger: Logger
) : ViewModel() {

    sealed interface ViewState

    object FetchInvitationsStartState : ViewState
    object FetchInvitationsEmptyState : ViewState
    object FetchInvitationsErrorState : ViewState
    open class FetchInvitationsSuccessState(val invitations: List<Invitation>) : ViewState

    private val _fetchInvitationsViewState: MutableLiveData<ViewState> = MutableLiveData(FetchInvitationsStartState)
    val fetchInvitationsViewState: LiveData<ViewState>
        get() = _fetchInvitationsViewState

    object GetInvitationsStartState : ViewState
    object GetInvitationsEmptyState : ViewState
    open class GetInvitationsErrorState(val error: Exception) : ViewState
    open class GetInvitationsSuccessState(val invitations: List<Invitation>) : ViewState

    /**
     * Keyed by [User.id], since [getInvitations] is called once per saved account (the account
     * switcher checks every account for pending invitations) and each account's result must be
     * attributed back to that account rather than overwriting a single shared value.
     */
    private val _invitationsStateByUser = MutableStateFlow<Map<Long, ViewState>>(emptyMap())
    val invitationsStateByUser: StateFlow<Map<Long, ViewState>> = _invitationsStateByUser

    object InvitationActionStartState : ViewState
    object InvitationActionErrorState : ViewState

    private val _invitationActionViewState: MutableLiveData<ViewState> = MutableLiveData(InvitationActionStartState)

    open class InvitationActionSuccessState(val action: ActionEnum, val invitation: Invitation) : ViewState

    val invitationActionViewState: LiveData<ViewState>
        get() = _invitationActionViewState

    fun fetchInvitations(user: User) {
        _fetchInvitationsViewState.value = FetchInvitationsStartState
        repository.fetchInvitations(user)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(FetchInvitationsObserver())
    }

    @Suppress("TooGenericExceptionCaught")
    fun getInvitations(user: User) {
        val userId = user.id ?: return
        viewModelScope.launch {
            val state = try {
                val invitationsModel = repository.getInvitations(user)
                if (invitationsModel.invitations.isEmpty()) {
                    GetInvitationsEmptyState
                } else {
                    GetInvitationsSuccessState(invitationsModel.invitations)
                }
            } catch (e: Exception) {
                logger.e(TAG, "Failed to get invitations", e)
                GetInvitationsErrorState(e)
            }
            _invitationsStateByUser.update { it + (userId to state) }
        }
    }

    fun acceptInvitation(user: User, invitation: Invitation) {
        repository.acceptInvitation(user, invitation)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(InvitationActionObserver())
    }

    fun rejectInvitation(user: User, invitation: Invitation) {
        repository.rejectInvitation(user, invitation)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(InvitationActionObserver())
    }

    inner class FetchInvitationsObserver : Observer<InvitationsModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(model: InvitationsModel) {
            if (model.invitations.isEmpty()) {
                _fetchInvitationsViewState.value = FetchInvitationsEmptyState
            } else {
                _fetchInvitationsViewState.value = FetchInvitationsSuccessState(model.invitations)
            }
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when fetching invitations")
            _fetchInvitationsViewState.value = FetchInvitationsErrorState
        }

        override fun onComplete() {
            // unused atm
        }
    }

    inner class InvitationActionObserver : Observer<InvitationActionModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(model: InvitationActionModel) {
            if (model.statusCode == HTTP_OK) {
                _invitationActionViewState.value = InvitationActionSuccessState(model.action, model.invitation)
            } else {
                _invitationActionViewState.value = InvitationActionErrorState
            }
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when handling invitation")
            _invitationActionViewState.value = InvitationActionErrorState
        }

        override fun onComplete() {
            // unused atm
        }
    }

    companion object {
        private val TAG = InvitationsViewModel::class.java.simpleName
        private const val OPEN_PENDING_INVITATION = "0"
        private const val HTTP_OK = 200
    }
}
