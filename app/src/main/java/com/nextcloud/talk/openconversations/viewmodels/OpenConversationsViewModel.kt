/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.openconversations.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.openconversations.data.OpenConversationsRepository
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class OpenConversationsViewModel @Inject constructor(private val repository: OpenConversationsRepository) :
    ViewModel() {

    sealed interface ViewState

    object FetchConversationsStartState : ViewState
    object FetchConversationsEmptyState : ViewState
    object FetchConversationsErrorState : ViewState
    open class FetchConversationsSuccessState(val conversations: List<Conversation>) : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(FetchConversationsStartState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    private val _searchTerm: MutableLiveData<String> = MutableLiveData("")
    val searchTerm: LiveData<String>
        get() = _searchTerm

    fun fetchConversations() {
        _viewState.value = FetchConversationsStartState
        repository.fetchConversations(_searchTerm.value ?: "")
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(FetchConversationsObserver())
    }

    fun updateSearchTerm(newTerm: String) {
        _searchTerm.value = newTerm
    }

    inner class FetchConversationsObserver : Observer<List<Conversation>> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(conversations: List<Conversation>) {
            if (conversations.isEmpty()) {
                _viewState.value = FetchConversationsEmptyState
            } else {
                _viewState.value = FetchConversationsSuccessState(conversations)
            }
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when fetching open conversations")
            _viewState.value = FetchConversationsErrorState
        }

        override fun onComplete() {
            // unused atm
        }
    }

    companion object {
        private val TAG = OpenConversationsViewModel::class.simpleName
    }
}
