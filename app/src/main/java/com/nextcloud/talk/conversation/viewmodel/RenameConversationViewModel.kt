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

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.conversation.repository.ConversationRepository
import com.nextcloud.talk.models.json.generic.GenericOverall
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class RenameConversationViewModel @Inject constructor(private val repository: ConversationRepository) : ViewModel() {

    sealed class ViewState
    object InitialState : ViewState()
    object RenamingState : ViewState()
    object RenamingSuccessState : ViewState()
    object RenamingFailedState : ViewState()

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(
        InitialState
    )
    val viewState: LiveData<ViewState>
        get() = _viewState

    fun renameConversation(roomToken: String, roomNameNew: String) {
        _viewState.value = RenamingState

        repository.renameConversation(
            roomToken,
            roomNameNew
        )
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(RenameConversationObserver())
    }

    inner class RenameConversationObserver : Observer<GenericOverall> {

        lateinit var genericOverall: GenericOverall

        override fun onSubscribe(d: Disposable) = Unit

        override fun onNext(response: GenericOverall) {
            genericOverall = response
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Failed to rename conversation", e)
            _viewState.value = RenamingFailedState
        }

        override fun onComplete() {
            _viewState.value = RenamingSuccessState
        }
    }

    companion object {
        private val TAG = RenameConversationViewModel::class.java.simpleName
    }
}
