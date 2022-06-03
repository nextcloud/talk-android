package com.nextcloud.talk.polls.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.polls.model.PollModel
import com.nextcloud.talk.polls.repositories.DialogPollRepository
import javax.inject.Inject

class PollViewModel @Inject constructor(private val repository: DialogPollRepository) : ViewModel() {

    private lateinit var repositoryParameters: DialogPollRepository.Parameters

    sealed interface ViewState
    object InitialState : ViewState
    open class PollOpenState(val poll: PollModel) : ViewState
    open class PollClosedState(val poll: PollModel) : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(InitialState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    fun initialize(userEntity: UserEntity, roomToken: String, pollId: Int) {
        repositoryParameters = DialogPollRepository.Parameters(
            userEntity.userId,
            userEntity.token,
            userEntity.baseUrl,
            roomToken,
            pollId
        )
        // loadAvailableTypes()
    }
}