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

package com.nextcloud.talk.openconversations.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.openconversations.data.OpenConversation
import com.nextcloud.talk.openconversations.data.OpenConversationsModel
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
    open class FetchConversationsSuccessState(val conversations: List<OpenConversation>) : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(FetchConversationsStartState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    fun fetchConversations() {
        _viewState.value = FetchConversationsStartState
        repository.fetchConversations()
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(FetchConversationsObserver())
    }

    inner class FetchConversationsObserver : Observer<OpenConversationsModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(model: OpenConversationsModel) {
            if (model.conversations.isEmpty()) {
                _viewState.value = FetchConversationsEmptyState
            } else {
                _viewState.value = FetchConversationsSuccessState(model.conversations)
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
