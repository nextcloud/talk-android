/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.models.domain.StartCallRecordingModel
import com.nextcloud.talk.models.domain.StopCallRecordingModel
import com.nextcloud.talk.repositories.callrecording.CallRecordingRepository
import com.nextcloud.talk.users.UserManager
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class CallRecordingViewModel @Inject constructor(private val repository: CallRecordingRepository) : ViewModel() {

    @Inject
    lateinit var userManager: UserManager

    lateinit var roomToken: String

    sealed interface ViewState
    open class RecordingStartedState(val hasVideo: Boolean, val showStartedInfo: Boolean) : ViewState

    object RecordingStoppedState : ViewState
    open class RecordingStartingState(val hasVideo: Boolean) : ViewState
    object RecordingStoppingState : ViewState
    object RecordingConfirmStopState : ViewState
    object RecordingErrorState : ViewState

    private val _viewState: MutableLiveData<ViewState> = MutableLiveData(RecordingStoppedState)
    val viewState: LiveData<ViewState>
        get() = _viewState

    private var disposable: Disposable? = null

    fun clickRecordButton() {
        when (viewState.value) {
            is RecordingStartedState -> {
                _viewState.value = RecordingConfirmStopState
            }
            RecordingStoppedState -> {
                startRecording()
            }
            RecordingConfirmStopState -> {
                // confirm dialog to stop recording might have been dismissed without to click an action.
                // just show it again.
                _viewState.value = RecordingConfirmStopState
            }
            is RecordingStartingState -> {
                stopRecording()
            }
            RecordingErrorState -> {
                stopRecording()
            }
            else -> {}
        }
    }

    private fun startRecording() {
        _viewState.value = RecordingStartingState(true)
        repository.startRecording(roomToken)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(CallStartRecordingObserver())
    }

    fun stopRecording() {
        _viewState.value = RecordingStoppingState
        repository.stopRecording(roomToken)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(CallStopRecordingObserver())
    }

    fun dismissStopRecording() {
        _viewState.value = RecordingStartedState(true, false)
    }

    override fun onCleared() {
        super.onCleared()
        disposable?.dispose()
    }

    fun setData(roomToken: String) {
        this.roomToken = roomToken
    }

    // https://nextcloud-talk.readthedocs.io/en/latest/constants/#call-recording-status
    fun setRecordingState(state: Int) {
        when (state) {
            RECORDING_STOPPED_CODE -> _viewState.value = RecordingStoppedState
            RECORDING_STARTED_VIDEO_CODE -> _viewState.value = RecordingStartedState(true, true)
            RECORDING_STARTED_AUDIO_CODE -> _viewState.value = RecordingStartedState(false, true)
            RECORDING_STARTING_VIDEO_CODE -> _viewState.value = RecordingStartingState(true)
            RECORDING_STARTING_AUDIO_CODE -> _viewState.value = RecordingStartingState(false)
            RECORDING_FAILED_CODE -> _viewState.value = RecordingErrorState
            else -> {}
        }
    }

    inner class CallStartRecordingObserver : Observer<StartCallRecordingModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(startCallRecordingModel: StartCallRecordingModel) {
            // unused atm. RecordingStartedState is set via setRecordingState which is triggered by signaling message.
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "failure in CallStartRecordingObserver", e)
            _viewState.value = RecordingErrorState
        }

        override fun onComplete() {
            // dismiss()
        }
    }

    inner class CallStopRecordingObserver : Observer<StopCallRecordingModel> {
        override fun onSubscribe(d: Disposable) {
            // unused atm
        }

        override fun onNext(stopCallRecordingModel: StopCallRecordingModel) {
            if (stopCallRecordingModel.success) {
                _viewState.value = RecordingStoppedState
            }
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "failure in CallStopRecordingObserver", e)
            _viewState.value = RecordingErrorState
        }

        override fun onComplete() {
            // dismiss()
        }
    }

    companion object {
        private val TAG = CallRecordingViewModel::class.java.simpleName
        const val RECORDING_STOPPED_CODE = 0
        const val RECORDING_STARTED_VIDEO_CODE = 1
        const val RECORDING_STARTED_AUDIO_CODE = 2
        const val RECORDING_STARTING_VIDEO_CODE = 3
        const val RECORDING_STARTING_AUDIO_CODE = 4
        const val RECORDING_FAILED_CODE = 5
    }
}
