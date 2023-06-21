/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.conversationinfoedit.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.chat.data.ChatRepository
import com.nextcloud.talk.conversationinfoedit.data.ConversationInfoEditRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import javax.inject.Inject

class ConversationInfoEditViewModel @Inject constructor(
    private val repository: ChatRepository,
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
}
