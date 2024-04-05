/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
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

    fun createConversation(roomName: String, conversationType: Conversation.ConversationType?) {
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
