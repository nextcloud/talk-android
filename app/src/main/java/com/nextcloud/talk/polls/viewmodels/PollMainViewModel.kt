/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.polls.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.polls.model.Poll
import com.nextcloud.talk.polls.repositories.PollRepository
import com.nextcloud.talk.utils.database.user.UserUtils
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

/**
 * @startuml
 * hide empty description
 * [*] --> InitialState
 * InitialState --> PollOpenState
 * note left
 *      Open second viewmodel for child fragment
 * end note
 * InitialState --> PollClosedState
 * @enduml
 */
class PollMainViewModel @Inject constructor(private val repository: PollRepository) : ViewModel() {

    @Inject
    lateinit var userUtils: UserUtils

    private lateinit var roomToken: String
    private lateinit var pollId: String

    private var editPoll: Boolean = false

    sealed interface ViewState
    object InitialState : ViewState
    open class PollVoteState(
        val poll: Poll,
        val showEndPollButton: Boolean
    ) : ViewState

    open class PollVoteHiddenState(
        val poll: Poll,
        val showEndPollButton: Boolean
    ) : ViewState

    open class PollResultState(
        val poll: Poll,
        val showEditButton: Boolean,
        val showEndPollButton: Boolean
    ) : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(InitialState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    private var disposable: Disposable? = null

    fun initialize(roomToken: String, pollId: String) {
        this.roomToken = roomToken
        this.pollId = pollId

        loadPoll()
    }

    fun voted() {
        loadPoll()
    }

    fun edit() {
        editPoll = true
        loadPoll()
    }

    private fun loadPoll() {
        repository.getPoll(roomToken, pollId)
            ?.doOnSubscribe { disposable = it }
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(PollObserver())
    }

    fun closePoll() {
        repository.closePoll(roomToken, pollId)
            ?.doOnSubscribe { disposable = it }
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(PollObserver())
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
            Log.e(TAG, "An error occurred: $e")
        }

        override fun onComplete() {
            val showEndPollButton = poll.status == Poll.STATUS_OPEN && isPollCreatedByCurrentUser(poll)

            if (votedForOpenHiddenPoll(poll)) {
                _viewState.value = PollVoteHiddenState(poll, showEndPollButton)
            } else if (editPoll && poll.status == Poll.STATUS_OPEN) {
                _viewState.value = PollVoteState(poll, showEndPollButton)
                editPoll = false
            } else if (poll.status == Poll.STATUS_CLOSED || poll.votedSelf?.isNotEmpty() == true) {
                setPollResultState(poll)
            } else if (poll.votedSelf.isNullOrEmpty()) {
                _viewState.value = PollVoteState(poll, showEndPollButton)
            } else {
                Log.w(TAG, "unknown poll state")
            }
        }
    }

    private fun setPollResultState(poll: Poll) {
        val showEditButton = poll.status == Poll.STATUS_OPEN && poll.resultMode == Poll.RESULT_MODE_PUBLIC
        val showEndPollButton = poll.status == Poll.STATUS_OPEN && isPollCreatedByCurrentUser(poll)

        _viewState.value = PollResultState(poll, showEditButton, showEndPollButton)
    }

    private fun votedForOpenHiddenPoll(poll: Poll): Boolean {
        return poll.status == Poll.STATUS_OPEN &&
            poll.resultMode == Poll.RESULT_MODE_HIDDEN &&
            poll.votedSelf?.isNotEmpty() == true
    }

    private fun isPollCreatedByCurrentUser(poll: Poll): Boolean {
        return userUtils.currentUser?.userId == poll.actorId
    }

    companion object {
        private val TAG = PollMainViewModel::class.java.simpleName
    }
}
