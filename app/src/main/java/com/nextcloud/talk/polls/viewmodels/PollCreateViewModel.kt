package com.nextcloud.talk.polls.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.polls.adapters.PollCreateOptionItem
import com.nextcloud.talk.polls.model.Poll
import com.nextcloud.talk.polls.repositories.PollRepository
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class PollCreateViewModel @Inject constructor(private val repository: PollRepository) : ViewModel() {

    private lateinit var roomToken: String

    // TODO remove testing items
    var defaultOptions: MutableList<PollCreateOptionItem> = mutableListOf<PollCreateOptionItem>().apply {
        for (i in 1..3) {
            val item = PollCreateOptionItem("item " + i)
            this.add(item)
        }
    }

    private var _options: MutableLiveData<ArrayList<PollCreateOptionItem>> =
        MutableLiveData<ArrayList<PollCreateOptionItem>>(defaultOptions)
    val options: LiveData<ArrayList<PollCreateOptionItem>>
        get() = _options

    var question: String = ""

    // lateinit var options: List<String>
    var privatePoll: Boolean = false
    var multipleAnswer: Boolean = false

    sealed interface ViewState
    object InitialState : ViewState
    open class PollCreatingState() : ViewState
    open class PollCreatedState() : ViewState
    open class PollCreationFailedState() : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(InitialState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    private var disposable: Disposable? = null

    fun initialize(roomToken: String) {
        this.roomToken = roomToken
    }

    override fun onCleared() {
        super.onCleared()
        disposable?.dispose()
    }

    private fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }

    fun addOption() {
        val item = PollCreateOptionItem("")
        val currentOptions: ArrayList<PollCreateOptionItem> = _options.value ?: ArrayList()
        currentOptions.add(item)
        _options.value = currentOptions
    }

    fun removeOption(item: PollCreateOptionItem) {
        val currentOptions: ArrayList<PollCreateOptionItem> = _options.value ?: ArrayList()
        currentOptions.remove(item)
        _options.value = currentOptions
    }

    fun createPoll() {
        var maxVotes = 1
        if (multipleAnswer) {
            maxVotes = 0
        }

        var resultMode = 0
        if (privatePoll) {
            resultMode = 1
        }

        val items = _options.value!!
        repository.createPoll(roomToken, question, items.map { it.pollOption }, resultMode, maxVotes)
            ?.doOnSubscribe { disposable = it }
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(PollObserver())
    }

    private fun map(
        list: ArrayList<PollCreateOptionItem>,
    ): List<String> {
        val resultList: ArrayList<String>? = ArrayList()

        list.forEach {
            resultList?.add(it.pollOption)
        }

        return resultList?.toList()!!
    }

    inner class PollObserver : Observer<Poll> {

        lateinit var poll: Poll

        override fun onSubscribe(d: Disposable) = Unit

        override fun onNext(response: Poll) {
            poll = response
        }

        override fun onError(e: Throwable) {
            _viewState.value = PollCreationFailedState()
        }

        override fun onComplete() {
            _viewState.value = PollCreatedState()
        }
    }

    companion object {
        private val TAG = PollCreateViewModel::class.java.simpleName
    }
}
