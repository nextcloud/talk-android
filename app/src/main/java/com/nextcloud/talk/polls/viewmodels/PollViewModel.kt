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

    sealed interface ViewState
    object InitialState : ViewState
    open class PollOpenState(val poll: Poll) : ViewState
    open class PollVotedState(val poll: Poll) : ViewState
    open class PollClosedState(val poll: Poll) : ViewState

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
            // TODO check attributes and decide if poll is open/closed/selfvoted...

            when (poll.status) {
                Poll.STATUS_OPEN -> _viewState.value = PollOpenState(poll)
                Poll.STATUS_CLOSED -> _viewState.value = PollClosedState(poll)
            }
        }
    }

    companion object {
        private val TAG = PollViewModel::class.java.simpleName
    }
}
