/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfo.viewmodel

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.participants.TalkBan
import com.nextcloud.talk.repositories.conversations.ConversationsRepository
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import javax.inject.Inject

class ConversationInfoViewModel @Inject constructor(
    private val chatNetworkDataSource: ChatNetworkDataSource,
    private val conversationsRepository: ConversationsRepository
) : ViewModel() {

    object LifeCycleObserver : DefaultLifecycleObserver {
        enum class LifeCycleFlag {
            PAUSED,
            RESUMED
        }

        lateinit var currentLifeCycleFlag: LifeCycleFlag
        val disposableSet = mutableSetOf<Disposable>()

        override fun onResume(owner: LifecycleOwner) {
            super.onResume(owner)
            currentLifeCycleFlag = LifeCycleFlag.RESUMED
        }

        override fun onPause(owner: LifecycleOwner) {
            super.onPause(owner)
            currentLifeCycleFlag = LifeCycleFlag.PAUSED
            disposableSet.forEach { disposable -> disposable.dispose() }
            disposableSet.clear()
        }
    }

    sealed interface ViewState

    class ListBansSuccessState(val talkBans: List<TalkBan>) : ViewState
    object ListBansErrorState : ViewState

    private val _getTalkBanState: MutableLiveData<ViewState> = MutableLiveData()
    val getTalkBanState: LiveData<ViewState>
        get() = _getTalkBanState

    class BanActorSuccessState(val talkBan: TalkBan) : ViewState
    object BanActorErrorState : ViewState

    private val _getBanActorState: MutableLiveData<ViewState> = MutableLiveData()
    val getBanActorState: LiveData<ViewState>
        get() = _getBanActorState

    object UnBanActorSuccessState : ViewState
    object UnBanActorErrorState : ViewState

    private val _getUnBanActorState: MutableLiveData<ViewState> = MutableLiveData()
    val getUnBanActorState: LiveData<ViewState>
        get() = _getUnBanActorState

    object GetRoomStartState : ViewState
    object GetRoomErrorState : ViewState
    open class GetRoomSuccessState(val conversationModel: ConversationModel) : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(GetRoomStartState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    object GetCapabilitiesStartState : ViewState
    object GetCapabilitiesErrorState : ViewState
    open class GetCapabilitiesSuccessState(val spreedCapabilities: SpreedCapability) : ViewState

    private val _allowGuestsViewState = MutableLiveData<AllowGuestsUIState>(AllowGuestsUIState.None)
    val allowGuestsViewState: LiveData<AllowGuestsUIState>
        get() = _allowGuestsViewState

    private val _passwordViewState = MutableLiveData<PasswordUiState>(PasswordUiState.None)
    val passwordViewState: LiveData<PasswordUiState>
        get() = _passwordViewState

    private val _getCapabilitiesViewState: MutableLiveData<ViewState> = MutableLiveData(GetCapabilitiesStartState)
    val getCapabilitiesViewState: LiveData<ViewState>
        get() = _getCapabilitiesViewState

    private val _clearChatHistoryViewState: MutableLiveData<ClearChatHistoryViewState> =
        MutableLiveData(ClearChatHistoryViewState.None)
    val clearChatHistoryViewState: LiveData<ClearChatHistoryViewState>
        get() = _clearChatHistoryViewState

    private val _getConversationReadOnlyState: MutableLiveData<SetConversationReadOnlyViewState> =
        MutableLiveData(SetConversationReadOnlyViewState.None)
    val getConversationReadOnlyState: LiveData<SetConversationReadOnlyViewState>
        get() = _getConversationReadOnlyState

    fun getRoom(user: User, token: String) {
        _viewState.value = GetRoomStartState
        chatNetworkDataSource.getRoom(user, token)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(GetRoomObserver())
    }

    fun getCapabilities(user: User, token: String, conversationModel: ConversationModel) {
        _getCapabilitiesViewState.value = GetCapabilitiesStartState

        if (conversationModel.remoteServer.isNullOrEmpty()) {
            _getCapabilitiesViewState.value = GetCapabilitiesSuccessState(user.capabilities!!.spreedCapability!!)
        } else {
            chatNetworkDataSource.getCapabilities(user, token)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(object : Observer<SpreedCapability> {
                    override fun onSubscribe(d: Disposable) {
                        LifeCycleObserver.disposableSet.add(d)
                    }

                    override fun onNext(spreedCapabilities: SpreedCapability) {
                        _getCapabilitiesViewState.value = GetCapabilitiesSuccessState(spreedCapabilities)
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Error when fetching spreed capabilities", e)
                        _getCapabilitiesViewState.value = GetCapabilitiesErrorState
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun listBans(user: User, token: String) {
        val url = ApiUtils.getUrlForBans(user.baseUrl!!, token)
        viewModelScope.launch {
            try {
                val listBans = conversationsRepository.listBans(user.getCredentials(), url)
                _getTalkBanState.value = ListBansSuccessState(listBans)
            } catch (exception: Exception) {
                _getTalkBanState.value = ListBansErrorState
                Log.e(TAG, "Error while getting list of banned participants", exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun banActor(user: User, token: String, actorType: String, actorId: String, internalNote: String) {
        val url = ApiUtils.getUrlForBans(user.baseUrl!!, token)
        viewModelScope.launch {
            try {
                val talkBan = conversationsRepository.banActor(
                    user.getCredentials(),
                    url,
                    actorType,
                    actorId,
                    internalNote
                )
                _getBanActorState.value = BanActorSuccessState(talkBan)
            } catch (exception: Exception) {
                _getBanActorState.value = BanActorErrorState
                Log.e(TAG, "Error banning a participant", exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun setConversationReadOnly(roomToken: String, state: Int) {
        viewModelScope.launch {
            try {
                conversationsRepository.setConversationReadOnly(roomToken, state)
                _getConversationReadOnlyState.value = SetConversationReadOnlyViewState.Success
            } catch (exception: Exception) {
                _getConversationReadOnlyState.value = SetConversationReadOnlyViewState.Error(exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun unbanActor(user: User, token: String, banId: Int) {
        val url = ApiUtils.getUrlForUnban(user.baseUrl!!, token, banId)
        viewModelScope.launch {
            try {
                conversationsRepository.unbanActor(user.getCredentials(), url)
                _getUnBanActorState.value = UnBanActorSuccessState
            } catch (exception: Exception) {
                _getUnBanActorState.value = UnBanActorErrorState
                Log.e(TAG, "Error while unbanning a participant", exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun allowGuests(token: String, allow: Boolean) {
        viewModelScope.launch {
            try {
                conversationsRepository.allowGuests(token, allow)
                _allowGuestsViewState.value = AllowGuestsUIState.Success(allow)
            } catch (exception: Exception) {
                _allowGuestsViewState.value = AllowGuestsUIState.Error(exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    @SuppressLint("SuspiciousIndentation")
    fun setPassword(password: String, token: String) {
        viewModelScope.launch {
            try {
                conversationsRepository.setPassword(password, token)
                _passwordViewState.value = PasswordUiState.Success
            } catch (exception: Exception) {
                _passwordViewState.value = PasswordUiState.Error(exception)
            }
        }
    }

    suspend fun archiveConversation(user: User, token: String) {
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val url = ApiUtils.getUrlForArchive(apiVersion, user.baseUrl, token)
        conversationsRepository.archiveConversation(user.getCredentials(), url)
    }

    suspend fun unarchiveConversation(user: User, token: String) {
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val url = ApiUtils.getUrlForArchive(apiVersion, user.baseUrl, token)
        conversationsRepository.unarchiveConversation(user.getCredentials(), url)
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun clearChatHistory(apiVersion: Int, roomToken: String) {
        viewModelScope.launch {
            try {
                conversationsRepository.clearChatHistory(apiVersion, roomToken)
                _clearChatHistoryViewState.value = ClearChatHistoryViewState.Success
            } catch (exception: Exception) {
                _clearChatHistoryViewState.value = ClearChatHistoryViewState.Error(exception)
            }
        }
    }

    inner class GetRoomObserver : Observer<ConversationModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(conversationModel: ConversationModel) {
            _viewState.value = GetRoomSuccessState(conversationModel)
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when fetching room")
            _viewState.value = GetRoomErrorState
        }

        override fun onComplete() {
            // unused atm
        }
    }

    companion object {
        private val TAG = ConversationInfoViewModel::class.simpleName
    }

    sealed class ClearChatHistoryViewState {
        data object None : ClearChatHistoryViewState()
        data object Success : ClearChatHistoryViewState()
        data class Error(val exception: Exception) : ClearChatHistoryViewState()
    }

    sealed class SetConversationReadOnlyViewState {
        data object None : SetConversationReadOnlyViewState()
        data object Success : SetConversationReadOnlyViewState()
        data class Error(val exception: Exception) : SetConversationReadOnlyViewState()
    }

    sealed class AllowGuestsUIState {
        data object None : AllowGuestsUIState()
        data class Success(val allow: Boolean) : AllowGuestsUIState()
        data class Error(val exception: Exception) : AllowGuestsUIState()
    }

    sealed class PasswordUiState {
        data object None : PasswordUiState()
        data object Success : PasswordUiState()
        data class Error(val exception: Exception) : PasswordUiState()
    }
}
