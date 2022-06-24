/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.polls.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.polls.model.Poll
import com.nextcloud.talk.polls.repositories.PollRepository
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class PollVoteViewModel @Inject constructor(private val repository: PollRepository) : ViewModel() {

    sealed interface ViewState
    object InitialState : ViewState
    open class PollVoteSuccessState(val poll: Poll) : ViewState
    open class PollVoteFailedState() : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(InitialState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    private var disposable: Disposable? = null

    private var _votedOptions: List<Int> = emptyList()
    val votedOptions: List<Int>
        get() = _votedOptions

    private var _selectedOptions: List<Int> = emptyList()
    val selectedOptions: List<Int>
        get() = _selectedOptions

    fun initVotedOptions(selectedOptions: List<Int>) {
        _votedOptions = selectedOptions
        _selectedOptions = selectedOptions
    }

    fun selectOption(option: Int, isRadioBox: Boolean) {
        if (isRadioBox) {
            _selectedOptions = listOf(option)
        } else {
            _selectedOptions = _selectedOptions.plus(option)
        }
    }

    fun deSelectOption(option: Int) {
        _selectedOptions = _selectedOptions.minus(option)
    }

    fun vote(roomToken: String, pollId: String) {
        if (_selectedOptions.isNotEmpty()) {
            repository.vote(roomToken, pollId, _selectedOptions)
                ?.doOnSubscribe { disposable = it }
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(PollObserver())
        }
    }

    override fun onCleared() {
        super.onCleared()
        disposable?.dispose()
    }

    inner class PollObserver : Observer<Poll> {

        lateinit var poll: Poll

        override fun onSubscribe(d: Disposable) = Unit

        override fun onNext(response: Poll) {
            poll = response
        }

        override fun onError(e: Throwable) {
            Log.d(TAG, "An error occurred: $e")
            _viewState.value = PollVoteFailedState()
        }

        override fun onComplete() {
            _viewState.value = PollVoteSuccessState(poll)
        }
    }

    companion object {
        private val TAG = PollVoteViewModel::class.java.simpleName
    }
}
