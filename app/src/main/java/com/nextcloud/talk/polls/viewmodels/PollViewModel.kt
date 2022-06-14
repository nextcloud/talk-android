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
class PollViewModel @Inject constructor(private val repository: PollRepository) : ViewModel() {

    private lateinit var roomToken: String
    private lateinit var pollId: String

    private var editPoll: Boolean = false

    sealed interface ViewState
    object InitialState : ViewState
    open class PollUnvotedState(val poll: Poll) : ViewState
    open class PollVotedState(val poll: Poll) : ViewState

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
        loadPoll() // TODO load other view
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
        }

        override fun onComplete() {
            if (editPoll) {
                _viewState.value = PollUnvotedState(poll)
                editPoll = false
            } else if (poll.votedSelf.isNullOrEmpty()) {
                _viewState.value = PollUnvotedState(poll)
            } else {
                _viewState.value = PollVotedState(poll)
            }
        }
    }

    companion object {
        private val TAG = PollViewModel::class.java.simpleName
    }
}
