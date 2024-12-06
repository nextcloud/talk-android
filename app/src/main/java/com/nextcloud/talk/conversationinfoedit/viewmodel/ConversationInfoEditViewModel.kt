/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfoedit.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationinfoedit.data.ConversationInfoEditRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

class ConversationInfoEditViewModel @Inject constructor(
    private val repository: ChatNetworkDataSource,
    private val conversationInfoEditRepository: ConversationInfoEditRepository
) : ViewModel() {

    sealed interface ViewState

    object GetRoomStartState : ViewState
    object GetRoomErrorState : ViewState
    open class GetRoomSuccessState(val conversationModel: ConversationModel) : ViewState

    object UploadAvatarErrorState : ViewState
    open class UploadAvatarSuccessState(val conversationModel: ConversationModel) : ViewState

    object DeleteAvatarErrorState : ViewState
    open class DeleteAvatarSuccessState(val conversationModel: ConversationModel) : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(GetRoomStartState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    private val _renameRoomUiState = MutableLiveData<RenameRoomUiState>(RenameRoomUiState.None)
    val renameRoomUiState: LiveData<RenameRoomUiState>
        get() = _renameRoomUiState

    private val _setConversationDescriptionUiState =
        MutableLiveData<SetConversationDescriptionUiState>(SetConversationDescriptionUiState.None)
    val setConversationDescriptionUiState: LiveData<SetConversationDescriptionUiState>
        get() = _setConversationDescriptionUiState

    fun getRoom(user: User, token: String) {
        _viewState.value = GetRoomStartState
        repository.getRoom(user, token)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(GetRoomObserver())
    }

    fun uploadConversationAvatar(user: User, file: File, roomToken: String) {
        conversationInfoEditRepository.uploadConversationAvatar(user, file, roomToken)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(UploadConversationAvatarObserver())
    }

    fun deleteConversationAvatar(user: User, roomToken: String) {
        conversationInfoEditRepository.deleteConversationAvatar(user, roomToken)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(DeleteConversationAvatarObserver())
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun renameRoom(roomToken: String, newRoomName: String) {
        viewModelScope.launch {
            try {
                conversationInfoEditRepository.renameConversation(roomToken, newRoomName)
                _renameRoomUiState.value = RenameRoomUiState.Success
            } catch (exception: Exception) {
                _renameRoomUiState.value = RenameRoomUiState.Error(exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun setConversationDescription(roomToken: String, conversationDescription: String?) {
        viewModelScope.launch {
            try {
                conversationInfoEditRepository.setConversationDescription(
                    roomToken,
                    conversationDescription
                )

                _setConversationDescriptionUiState.value = SetConversationDescriptionUiState.Success
            } catch (exception: Exception) {
                _setConversationDescriptionUiState.value = SetConversationDescriptionUiState.Error(exception)
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

    inner class UploadConversationAvatarObserver : Observer<ConversationModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(conversationModel: ConversationModel) {
            _viewState.value = UploadAvatarSuccessState(conversationModel)
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when uploading avatar")
            _viewState.value = UploadAvatarErrorState
        }

        override fun onComplete() {
            // unused atm
        }
    }

    inner class DeleteConversationAvatarObserver : Observer<ConversationModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(conversationModel: ConversationModel) {
            _viewState.value = DeleteAvatarSuccessState(conversationModel)
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when deleting avatar")
            _viewState.value = DeleteAvatarErrorState
        }

        override fun onComplete() {
            // unused atm
        }
    }

    companion object {
        private val TAG = ConversationInfoEditViewModel::class.simpleName
    }

    sealed class RenameRoomUiState {
        data object None : RenameRoomUiState()
        data object Success : RenameRoomUiState()
        data class Error(val exception: Exception) : RenameRoomUiState()
    }

    sealed class SetConversationDescriptionUiState {
        data object None : SetConversationDescriptionUiState()
        data object Success : SetConversationDescriptionUiState()
        data class Error(val exception: Exception) : SetConversationDescriptionUiState()
    }
}
