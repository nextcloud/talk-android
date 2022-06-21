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

    sealed interface ViewState
    open class PollCreationState(val enableAddOptionButton: Boolean, val enableCreatePollButton: Boolean) : ViewState
    object PollCreatedState : ViewState
    object PollCreationFailedState : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(PollCreationState(true, false))
    val viewState: LiveData<ViewState>
        get() = _viewState

    private var _options: MutableLiveData<ArrayList<PollCreateOptionItem>> =
        MutableLiveData<ArrayList<PollCreateOptionItem>>()
    val options: LiveData<ArrayList<PollCreateOptionItem>>
        get() = _options

    private var _question: MutableLiveData<String> = MutableLiveData<String>()
    val question: LiveData<String>
        get() = _question

    private var _privatePoll: MutableLiveData<Boolean> = MutableLiveData<Boolean>()
    val privatePoll: LiveData<Boolean>
        get() = _privatePoll

    private var _multipleAnswer: MutableLiveData<Boolean> = MutableLiveData<Boolean>()
    val multipleAnswer: LiveData<Boolean>
        get() = _multipleAnswer

    private var disposable: Disposable? = null

    fun initialize(roomToken: String) {
        this.roomToken = roomToken
        updateCreationState()
    }

    override fun onCleared() {
        super.onCleared()
        disposable?.dispose()
    }

    fun addOption() {
        val item = PollCreateOptionItem("")
        val currentOptions: ArrayList<PollCreateOptionItem> = _options.value ?: ArrayList()
        currentOptions.add(item)
        _options.value = currentOptions
        updateCreationState()
    }

    fun removeOption(item: PollCreateOptionItem) {
        val currentOptions: ArrayList<PollCreateOptionItem> = _options.value ?: ArrayList()
        currentOptions.remove(item)
        _options.value = currentOptions
        updateCreationState()
    }

    fun createPoll() {
        var maxVotes = 1
        if (multipleAnswer.value == true) {
            maxVotes = 0
        }

        var resultMode = 0
        if (privatePoll.value == true) {
            resultMode = 1
        }

        if (_question.value?.isNotEmpty() == true && _options.value?.isNotEmpty() == true) {
            repository.createPoll(
                roomToken, _question.value!!, _options.value!!.map { it.pollOption }, resultMode,
                maxVotes
            )
                ?.doOnSubscribe { disposable = it }
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(PollObserver())
        }
    }

    fun setQuestion(question: String) {
        _question.value = question
        updateCreationState()
    }

    fun setPrivatePoll(checked: Boolean) {
        _privatePoll.value = checked
    }

    fun setMultipleAnswer(checked: Boolean) {
        _multipleAnswer.value = checked
    }

    fun optionsItemTextChanged() {
        updateCreationState()
    }

    private fun updateCreationState() {
        _viewState.value = PollCreationState(enableAddOptionButton(), enableCreatePollButton())
    }

    private fun enableCreatePollButton(): Boolean {
        return _question.value?.isNotEmpty() == true && atLeastTwoOptionsAreFilled()
    }

    private fun atLeastTwoOptionsAreFilled(): Boolean {
        if (_options.value != null) {
            var filledOptions = 0
            _options.value?.forEach {
                if (it.pollOption.isNotEmpty()) {
                    filledOptions++
                }
                if (filledOptions >= 2) {
                    return true
                }
            }
        }
        return false
    }

    private fun enableAddOptionButton(): Boolean {
        if (_options.value != null && _options.value?.size != 0) {
            _options.value?.forEach {
                if (it.pollOption.isBlank()) {
                    return false
                }
            }
        }
        return true
    }

    inner class PollObserver : Observer<Poll> {

        lateinit var poll: Poll

        override fun onSubscribe(d: Disposable) = Unit

        override fun onNext(response: Poll) {
            poll = response
        }

        override fun onError(e: Throwable) {
            _viewState.value = PollCreationFailedState
        }

        override fun onComplete() {
            _viewState.value = PollCreatedState
        }
    }

    companion object {
        private val TAG = PollCreateViewModel::class.java.simpleName
    }
}
