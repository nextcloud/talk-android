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

package com.nextcloud.talk.conversation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.conversation.repository.ConversationRepository
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class ConversationViewModel @Inject constructor(private val repository: ConversationRepository) : ViewModel() {

    sealed class ViewState
    object InitialState : ViewState()

    object CreatingState : ViewState()
    class CreatingSuccessState(val roomToken: String) : ViewState()
    object CreatingFailedState : ViewState()

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(
        InitialState
    )
    val viewState: LiveData<ViewState>
        get() = _viewState

    private var disposable: Disposable? = null

    override fun onCleared() {
        super.onCleared()
        disposable?.dispose()
    }

    fun createConversation(
        roomName: String,
        conversationType: Conversation.ConversationType?
    ) {
        _viewState.value = CreatingState

        repository.createConversation(
            roomName,
            conversationType
        )
            .doOnSubscribe { disposable = it }
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(CreateConversationObserver())
    }

    inner class CreateConversationObserver : Observer<RoomOverall> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(roomOverall: RoomOverall) {
            val conversation = roomOverall.ocs!!.data
            _viewState.value = CreatingSuccessState(conversation?.token!!)
        }

        override fun onError(e: Throwable) {
            // dispose()
        }

        override fun onComplete() {
            // dispose()
        }
    }

    companion object {
        private val TAG = ConversationViewModel::class.java.simpleName
    }
}
