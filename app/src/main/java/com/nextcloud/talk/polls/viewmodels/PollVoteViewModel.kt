/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
    open class PollVoteSuccessState : ViewState
    open class PollVoteHiddenSuccessState : ViewState
    open class PollVoteFailedState : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(InitialState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    private val _submitButtonEnabled: MutableLiveData<Boolean> = MutableLiveData()
    val submitButtonEnabled: LiveData<Boolean>
        get() = _submitButtonEnabled

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
        _selectedOptions = if (isRadioBox) {
            listOf(option)
        } else {
            _selectedOptions.plus(option)
        }
    }

    fun deSelectOption(option: Int) {
        _selectedOptions = _selectedOptions.minus(option)
    }

    fun vote(roomToken: String, pollId: String) {
        if (_selectedOptions.isNotEmpty()) {
            _submitButtonEnabled.value = false

            repository.vote(roomToken, pollId, _selectedOptions)
                .doOnSubscribe { disposable = it }
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(PollObserver())
        }
    }

    override fun onCleared() {
        super.onCleared()
        disposable?.dispose()
    }

    fun updateSubmitButton() {
        val areSelectedOptionsDifferentToVotedOptions = !(
            votedOptions.containsAll(selectedOptions) &&
                selectedOptions.containsAll(votedOptions)
            )

        _submitButtonEnabled.value = areSelectedOptionsDifferentToVotedOptions && selectedOptions.isNotEmpty()
    }

    inner class PollObserver : Observer<Poll> {

        lateinit var poll: Poll

        override fun onSubscribe(d: Disposable) = Unit

        override fun onNext(response: Poll) {
            poll = response
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "An error occurred: $e")
            _viewState.value = PollVoteFailedState()
        }

        override fun onComplete() {
            if (poll.resultMode == 1) {
                _viewState.value = PollVoteHiddenSuccessState()
            } else {
                _viewState.value = PollVoteSuccessState()
            }
        }
    }

    companion object {
        private val TAG = PollVoteViewModel::class.java.simpleName
    }
}
