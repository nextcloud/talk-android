/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.messagesearch

import android.annotation.SuppressLint
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

/**
 * Install PlantUML plugin to render this state diagram
 * @startuml
 * hide empty description
 * [*] --> InitialState
 * InitialState --> LoadingState
 * LoadingState --> EmptyState
 * LoadingState --> LoadedState
 * LoadingState --> LoadingState
 * LoadedState --> LoadingState
 * EmptyState --> LoadingState
 * LoadingState --> ErrorState
 * ErrorState --> LoadingState
 * @enduml
 */
class MessageSearchViewModel @Inject constructor(private val unifiedSearchRepository: UnifiedSearchRepository) :
    ViewModel() {

    sealed class ViewState
    object InitialState : ViewState()
    object LoadingState : ViewState()
    object EmptyState : ViewState()
    object ErrorState : ViewState()
    class LoadedState(val results: List<SearchMessageEntry>, val hasMore: Boolean) : ViewState()
    class FinishedState(val selectedMessageId: String, val selectedThreadId: String?) : ViewState()

    private lateinit var messageSearchHelper: MessageSearchHelper

    private val _state: MutableLiveData<ViewState> = MutableLiveData(InitialState)
    val state: LiveData<ViewState>
        get() = _state

    fun initialize(roomToken: String) {
        messageSearchHelper = MessageSearchHelper(unifiedSearchRepository, roomToken)
    }

    @SuppressLint("CheckResult") // handled by helper
    fun onQueryTextChange(newText: String) {
        if (newText.length >= MIN_CHARS_FOR_SEARCH) {
            _state.value = LoadingState
            messageSearchHelper.cancelSearch()
            messageSearchHelper.startMessageSearch(newText)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onReceiveResults, this::onError)
        }
    }

    @SuppressLint("CheckResult") // handled by helper
    fun loadMore() {
        _state.value = LoadingState
        messageSearchHelper.cancelSearch()
        messageSearchHelper.loadMore()
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(this::onReceiveResults)
    }

    private fun onReceiveResults(results: MessageSearchHelper.MessageSearchResults) {
        if (results.messages.isEmpty()) {
            _state.value = EmptyState
        } else {
            _state.value = LoadedState(results.messages, results.hasMore)
        }
    }

    private fun onError(throwable: Throwable) {
        Log.e(TAG, "onError:", throwable)
        messageSearchHelper.cancelSearch()
        _state.value = ErrorState
    }

    fun refresh(query: String?) {
        query?.let { onQueryTextChange(it) }
    }

    fun selectMessage(messageEntry: SearchMessageEntry) {
        _state.value = FinishedState(messageEntry.messageId!!, messageEntry.threadId)
    }

    companion object {
        private val TAG = MessageSearchViewModel::class.simpleName
        private const val MIN_CHARS_FOR_SEARCH = 2
    }
}
