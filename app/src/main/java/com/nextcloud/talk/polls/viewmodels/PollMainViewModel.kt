/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.polls.model.Poll
import com.nextcloud.talk.polls.repositories.PollRepository
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class PollMainViewModel @Inject constructor(private val repository: PollRepository) : ViewModel() {

    @Inject
    lateinit var userManager: UserManager

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

    lateinit var user: User
    lateinit var roomToken: String
    private var isOwnerOrModerator: Boolean = false
    lateinit var pollId: String
    lateinit var pollTitle: String

    private var editVotes: Boolean = false

    sealed interface ViewState
    object InitialState : ViewState
    object DismissDialogState : ViewState
    object LoadingState : ViewState

    open class PollVoteState(
        val poll: Poll,
        val showVotersAmount: Boolean,
        val showEndPollButton: Boolean,
        val showDismissEditButton: Boolean
    ) : ViewState

    open class PollResultState(
        val poll: Poll,
        val showVotersAmount: Boolean,
        val showEndPollButton: Boolean,
        val showEditButton: Boolean
    ) : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(InitialState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    private var disposable: Disposable? = null

    fun setData(user: User, roomToken: String, isOwnerOrModerator: Boolean, pollId: String, pollTitle: String) {
        this.user = user
        this.roomToken = roomToken
        this.isOwnerOrModerator = isOwnerOrModerator
        this.pollId = pollId
        this.pollTitle = pollTitle

        loadPoll()
    }

    fun voted() {
        loadPoll()
    }

    fun editVotes() {
        editVotes = true
        loadPoll()
    }

    fun dismissEditVotes() {
        loadPoll()
    }

    private fun loadPoll() {
        _viewState.value = LoadingState
        repository.getPoll(roomToken, pollId)
            .doOnSubscribe { disposable = it }
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(PollObserver())
    }

    fun endPoll() {
        _viewState.value = LoadingState
        repository.closePoll(roomToken, pollId)
            .doOnSubscribe { disposable = it }
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
            val showEndPollButton = showEndPollButton(poll)
            val showVotersAmount = showVotersAmount(poll)

            if (votedForOpenHiddenPoll(poll)) {
                _viewState.value = PollVoteState(poll, showVotersAmount, showEndPollButton, false)
            } else if (editVotes && poll.status == Poll.STATUS_OPEN) {
                _viewState.value = PollVoteState(poll, false, showEndPollButton, true)
                editVotes = false
            } else if (poll.status == Poll.STATUS_CLOSED || poll.votedSelf?.isNotEmpty() == true) {
                val showEditButton = poll.status == Poll.STATUS_OPEN && poll.resultMode == Poll.RESULT_MODE_PUBLIC
                _viewState.value = PollResultState(poll, showVotersAmount, showEndPollButton, showEditButton)
            } else if (poll.votedSelf.isNullOrEmpty()) {
                _viewState.value = PollVoteState(poll, showVotersAmount, showEndPollButton, false)
            } else {
                Log.w(TAG, "unknown poll state")
            }
        }
    }

    private fun showEndPollButton(poll: Poll): Boolean =
        !editVotes && poll.status == Poll.STATUS_OPEN && (isPollCreatedByCurrentUser(poll) || isOwnerOrModerator)

    private fun showVotersAmount(poll: Poll): Boolean =
        votedForPublicPoll(poll) ||
            poll.status == Poll.STATUS_CLOSED ||
            isOwnerOrModerator ||
            isPollCreatedByCurrentUser(poll)

    private fun votedForOpenHiddenPoll(poll: Poll): Boolean =
        poll.status == Poll.STATUS_OPEN &&
            poll.resultMode == Poll.RESULT_MODE_HIDDEN &&
            poll.votedSelf?.isNotEmpty() == true

    private fun votedForPublicPoll(poll: Poll): Boolean =
        poll.resultMode == Poll.RESULT_MODE_PUBLIC &&
            poll.votedSelf?.isNotEmpty() == true

    private fun isPollCreatedByCurrentUser(poll: Poll): Boolean =
        currentUserProvider.currentUser.blockingGet().userId == poll.actorId

    fun dismissDialog() {
        _viewState.value = DismissDialogState
    }

    companion object {
        private val TAG = PollMainViewModel::class.java.simpleName
    }
}
