/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.invitation.data.InvitationsModel
import com.nextcloud.talk.invitation.data.InvitationsRepository
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.openconversations.data.OpenConversationsRepository
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.UserIdUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class ConversationsListViewModel @Inject constructor(
    private val repository: OfflineConversationsRepository,
    private val threadsRepository: ThreadsRepository,
    private val currentUserProvider: CurrentUserProviderNew,
    private val openConversationsRepository: OpenConversationsRepository,
    var userManager: UserManager
) : ViewModel() {

    @Inject
    lateinit var invitationsRepository: InvitationsRepository

    @Inject
    lateinit var arbitraryStorageManager: ArbitraryStorageManager

    private val _currentUser = currentUserProvider.currentUser.blockingGet()
    val currentUser: User = _currentUser
    val credentials = ApiUtils.getCredentials(_currentUser.username, _currentUser.token) ?: ""

    sealed interface ViewState

    sealed class ThreadsExistUiState {
        data object None : ThreadsExistUiState()
        data class Success(val threadsExistence: Boolean?) : ThreadsExistUiState()
        data class Error(val exception: Exception) : ThreadsExistUiState()
    }

    private val _threadsExistState = MutableStateFlow<ThreadsExistUiState>(ThreadsExistUiState.None)
    val threadsExistState: StateFlow<ThreadsExistUiState> = _threadsExistState

    sealed class OpenConversationsUiState {
        data object None : OpenConversationsUiState()
        data class Success(val conversations: List<Conversation>) : OpenConversationsUiState()
        data class Error(val exception: Throwable) : OpenConversationsUiState()
    }

    private val _openConversationsState = MutableStateFlow<OpenConversationsUiState>(OpenConversationsUiState.None)
    val openConversationsState: StateFlow<OpenConversationsUiState> = _openConversationsState

    object GetRoomsStartState : ViewState
    object GetRoomsErrorState : ViewState
    open class GetRoomsSuccessState(val listIsNotEmpty: Boolean) : ViewState

    private val _getRoomsViewState: MutableLiveData<ViewState> = MutableLiveData(GetRoomsStartState)
    val getRoomsViewState: LiveData<ViewState>
        get() = _getRoomsViewState

    val getRoomsFlow = repository.roomListFlow
        .onEach { list ->
            _getRoomsViewState.value = GetRoomsSuccessState(list.isNotEmpty())
        }.catch {
            _getRoomsViewState.value = GetRoomsErrorState
        }

    object GetFederationInvitationsStartState : ViewState
    object GetFederationInvitationsErrorState : ViewState

    open class GetFederationInvitationsSuccessState(val showInvitationsHint: Boolean) : ViewState

    private val _getFederationInvitationsViewState: MutableLiveData<ViewState> =
        MutableLiveData(GetFederationInvitationsStartState)
    val getFederationInvitationsViewState: LiveData<ViewState>
        get() = _getFederationInvitationsViewState

    object ShowBadgeStartState : ViewState
    object ShowBadgeErrorState : ViewState
    open class ShowBadgeSuccessState(val showBadge: Boolean) : ViewState

    private val _showBadgeViewState: MutableLiveData<ViewState> = MutableLiveData(ShowBadgeStartState)
    val showBadgeViewState: LiveData<ViewState>
        get() = _showBadgeViewState

    fun getFederationInvitations() {
        _getFederationInvitationsViewState.value = GetFederationInvitationsStartState
        _showBadgeViewState.value = ShowBadgeStartState

        userManager.users.blockingGet()?.forEach {
            invitationsRepository.fetchInvitations(it)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(FederatedInvitationsObserver())
        }
    }

    fun getRooms() {
        val startNanoTime = System.nanoTime()
        Log.d(TAG, "fetchData - getRooms - calling: $startNanoTime")
        repository.getRooms()
    }

    fun checkIfThreadsExist() {
        val limitForFollowedThreadsExistenceCheck = 1
        val accountId = UserIdUtils.getIdForUser(currentUserProvider.currentUser.blockingGet())

        fun isLastCheckTooOld(lastCheckDate: Long): Boolean {
            val currentTimeMillis = System.currentTimeMillis()
            val differenceMillis = currentTimeMillis - lastCheckDate
            val checkIntervalInMillies = TimeUnit.HOURS.toMillis(2)
            return differenceMillis > checkIntervalInMillies
        }

        fun checkIfFollowedThreadsExist() {
            val threadsUrl = ApiUtils.getUrlForSubscribedThreads(
                version = 1,
                baseUrl = currentUser.baseUrl
            )

            viewModelScope.launch {
                try {
                    val threads =
                        threadsRepository.getThreads(credentials, threadsUrl, limitForFollowedThreadsExistenceCheck)
                    val followedThreadsExistNew = threads.ocs?.data?.isNotEmpty()
                    _threadsExistState.value = ThreadsExistUiState.Success(followedThreadsExistNew)
                    val followedThreadsExistLastCheckNew = System.currentTimeMillis()
                    arbitraryStorageManager.storeStorageSetting(
                        accountId,
                        FOLLOWED_THREADS_EXIST_LAST_CHECK,
                        followedThreadsExistLastCheckNew.toString(),
                        ""
                    )
                    arbitraryStorageManager.storeStorageSetting(
                        accountId,
                        FOLLOWED_THREADS_EXIST,
                        followedThreadsExistNew.toString(),
                        ""
                    )
                } catch (exception: Exception) {
                    _threadsExistState.value = ThreadsExistUiState.Error(exception)
                }
            }
        }

        if (!hasSpreedFeatureCapability(currentUser.capabilities?.spreedCapability, SpreedFeatures.THREADS)) {
            _threadsExistState.value = ThreadsExistUiState.Success(false)
            return
        }

        val followedThreadsExistOld = arbitraryStorageManager.getStorageSetting(
            accountId,
            FOLLOWED_THREADS_EXIST,
            ""
        ).blockingGet()?.value?.toBoolean() ?: false

        val followedThreadsExistLastCheckOld = arbitraryStorageManager.getStorageSetting(
            accountId,
            FOLLOWED_THREADS_EXIST_LAST_CHECK,
            ""
        ).blockingGet()?.value?.toLong()

        if (followedThreadsExistOld) {
            Log.d(TAG, "followed threads exist for this user. No need to check again.")
            _threadsExistState.value = ThreadsExistUiState.Success(true)
        } else {
            if (followedThreadsExistLastCheckOld == null || isLastCheckTooOld(followedThreadsExistLastCheckOld)) {
                Log.d(TAG, "check if followed threads exist never happened or is too old. Checking now...")
                checkIfFollowedThreadsExist()
            } else {
                _threadsExistState.value = ThreadsExistUiState.Success(false)
                Log.d(TAG, "already checked in the last 2 hours if followed threads exist. Skip check.")
            }
        }
    }

    fun fetchOpenConversations() {
        _openConversationsState.value = OpenConversationsUiState.None

        if (!hasSpreedFeatureCapability(currentUser.capabilities?.spreedCapability, SpreedFeatures.LISTABLE_ROOMS)) {
            return
        }

        viewModelScope.launch {
            openConversationsRepository.fetchConversations("")
                .onSuccess { conversations ->
                    if (conversations.isEmpty()) {
                        _openConversationsState.value = OpenConversationsUiState.None
                    } else {
                        _openConversationsState.value = OpenConversationsUiState.Success(conversations)
                    }
                }
                .onFailure { exception ->
                    Log.e(TAG, "Failed to fetch conversations", exception)
                    _openConversationsState.value = OpenConversationsUiState.Error(exception)
                }
        }
    }

    inner class FederatedInvitationsObserver : Observer<InvitationsModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(invitationsModel: InvitationsModel) {
            val currentUser = currentUserProvider.currentUser.blockingGet()

            if (invitationsModel.user.userId?.equals(currentUser.userId) == true &&
                invitationsModel.user.baseUrl?.equals(currentUser.baseUrl) == true
            ) {
                if (invitationsModel.invitations.isNotEmpty()) {
                    _getFederationInvitationsViewState.value = GetFederationInvitationsSuccessState(true)
                } else {
                    _getFederationInvitationsViewState.value = GetFederationInvitationsSuccessState(false)
                }
            } else {
                if (invitationsModel.invitations.isNotEmpty()) {
                    _showBadgeViewState.value = ShowBadgeSuccessState(true)
                }
            }
        }

        override fun onError(e: Throwable) {
            _getFederationInvitationsViewState.value = GetFederationInvitationsErrorState
            Log.e(TAG, "Failed to fetch pending invitations", e)
        }

        override fun onComplete() {
            // unused atm
        }
    }

    companion object {
        private val TAG = ConversationsListViewModel::class.simpleName
        const val FOLLOWED_THREADS_EXIST_LAST_CHECK = "FOLLOWED_THREADS_EXIST_LAST_CHECK"
        const val FOLLOWED_THREADS_EXIST = "FOLLOWED_THREADS_EXIST"
    }
}
