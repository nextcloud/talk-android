/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus juliuslinus1@gmail.com
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.nextcloud.talk.chat.data.io.AudioFocusRequestManager
import com.nextcloud.talk.chat.data.io.AudioRecorderManager
import com.nextcloud.talk.chat.data.io.MediaPlayerManager
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.commons.models.IMessage
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.lang.Thread.sleep
import javax.inject.Inject

class MessageInputViewModel @Inject constructor(
    private val chatNetworkDataSource: ChatNetworkDataSource,
    private val audioRecorderManager: AudioRecorderManager,
    private val mediaPlayerManager: MediaPlayerManager,
    private val audioFocusRequestManager: AudioFocusRequestManager,
    private val dataStore: AppPreferences
) : ViewModel(), DefaultLifecycleObserver {
    enum class LifeCycleFlag {
        PAUSED,
        RESUMED,
        STOPPED
    }
    lateinit var currentLifeCycleFlag: LifeCycleFlag
    val disposableSet = mutableSetOf<Disposable>()

    data class QueuedMessage(
        val message: CharSequence? = null,
        val displayName: String? = null,
        val replyTo: Int? = null,
        val sendWithoutNotification: Boolean? = null
    )

    private var isQueueing: Boolean = false
    private var messageQueue: MutableList<QueuedMessage> = mutableListOf()

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        currentLifeCycleFlag = LifeCycleFlag.RESUMED
        audioRecorderManager.handleOnResume()
        mediaPlayerManager.handleOnResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        currentLifeCycleFlag = LifeCycleFlag.PAUSED
        disposableSet.forEach { disposable -> disposable.dispose() }
        disposableSet.clear()
        audioRecorderManager.handleOnPause()
        mediaPlayerManager.handleOnPause()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        currentLifeCycleFlag = LifeCycleFlag.STOPPED
        audioRecorderManager.handleOnStop()
        mediaPlayerManager.handleOnStop()
    }

    val getAudioFocusChange: LiveData<AudioFocusRequestManager.ManagerState>
        get() = audioFocusRequestManager.getManagerState

    private val _getRecordingTime: MutableLiveData<Long> = MutableLiveData(0L)
    val getRecordingTime: LiveData<Long>
        get() = _getRecordingTime

    val micInputAudioObserver: LiveData<Pair<Float, Float>>
        get() = audioRecorderManager.getAudioValues

    val mediaPlayerSeekbarObserver: LiveData<Int>
        get() = mediaPlayerManager.mediaPlayerSeekBarPosition

    private val _getEditChatMessage: MutableLiveData<IMessage?> = MutableLiveData()
    val getEditChatMessage: LiveData<IMessage?>
        get() = _getEditChatMessage

    private val _getReplyChatMessage: MutableLiveData<IMessage?> = MutableLiveData()
    val getReplyChatMessage: LiveData<IMessage?>
        get() = _getReplyChatMessage

    sealed interface ViewState
    object SendChatMessageStartState : ViewState
    class SendChatMessageSuccessState(val message: CharSequence) : ViewState
    class SendChatMessageErrorState(val e: Throwable, val message: CharSequence) : ViewState
    private val _sendChatMessageViewState: MutableLiveData<ViewState> = MutableLiveData(SendChatMessageStartState)
    val sendChatMessageViewState: LiveData<ViewState>
        get() = _sendChatMessageViewState
    object EditMessageStartState : ViewState
    object EditMessageErrorState : ViewState
    class EditMessageSuccessState(val messageEdited: ChatOverallSingleMessage) : ViewState

    private val _editMessageViewState: MutableLiveData<ViewState> = MutableLiveData()
    val editMessageViewState: LiveData<ViewState>
        get() = _editMessageViewState

    private val _isVoicePreviewPlaying: MutableLiveData<Boolean> = MutableLiveData(false)
    val isVoicePreviewPlaying: LiveData<Boolean>
        get() = _isVoicePreviewPlaying

    private val _messageQueueSizeFlow = MutableStateFlow(messageQueue.size)
    val messageQueueSizeFlow: LiveData<Int>
        get() = _messageQueueSizeFlow.asLiveData()

    private val _messageQueueFlow: MutableLiveData<List<String>> = MutableLiveData()
    val messageQueueFlow: LiveData<List<String>>
        get() = _messageQueueFlow

    @Suppress("LongParameterList")
    fun sendChatMessage(
        roomToken: String,
        credentials: String,
        url: String,
        message: CharSequence,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean
    ) {
        if (isQueueing) {
            messageQueue.add(QueuedMessage(message, displayName, replyTo, sendWithoutNotification))
            dataStore.saveMessageQueue(roomToken, messageQueue)
            _messageQueueSizeFlow.update { messageQueue.size }
            _messageQueueFlow.postValue(listOf(message.toString()))
            return
        }

        chatNetworkDataSource.sendChatMessage(
            credentials,
            url,
            message,
            displayName,
            replyTo,
            sendWithoutNotification
        ).subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    _sendChatMessageViewState.value = SendChatMessageErrorState(e, message)
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(t: GenericOverall) {
                    _sendChatMessageViewState.value = SendChatMessageSuccessState(message)
                }
            })
    }

    fun editChatMessage(credentials: String, url: String, text: String) {
        chatNetworkDataSource.editChatMessage(credentials, url, text)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ChatOverallSingleMessage> {
                override fun onSubscribe(d: Disposable) {
                    disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "failed to edit message", e)
                    _editMessageViewState.value = EditMessageErrorState
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(messageEdited: ChatOverallSingleMessage) {
                    _editMessageViewState.value = EditMessageSuccessState(messageEdited)
                }
            })
    }

    fun reply(message: IMessage?) {
        _getReplyChatMessage.postValue(message)
    }

    fun edit(message: IMessage?) {
        _getEditChatMessage.postValue(message)
    }

    fun startMicInput(context: Context) {
        audioFocusRequestManager.audioFocusRequest(true) {
            audioRecorderManager.start(context)
        }
    }

    fun stopMicInput() {
        audioFocusRequestManager.audioFocusRequest(false) {
            audioRecorderManager.stop()
        }
    }

    fun startMediaPlayer(path: String) {
        audioFocusRequestManager.audioFocusRequest(true) {
            mediaPlayerManager.start(path)
            _isVoicePreviewPlaying.postValue(true)
        }
    }

    fun pauseMediaPlayer() {
        audioFocusRequestManager.audioFocusRequest(false) {
            mediaPlayerManager.pause()
            _isVoicePreviewPlaying.postValue(false)
        }
    }

    fun stopMediaPlayer() {
        audioFocusRequestManager.audioFocusRequest(false) {
            mediaPlayerManager.stop()
            _isVoicePreviewPlaying.postValue(false)
        }
    }

    fun seekMediaPlayerTo(progress: Int) {
        mediaPlayerManager.seekTo(progress)
    }

    fun setRecordingTime(time: Long) {
        _getRecordingTime.postValue(time)
    }

    fun sendAndEmptyMessageQueue(roomToken: String, credentials: String, url: String) {
        if (isQueueing) return
        messageQueue.clear()

        val queue = dataStore.getMessageQueue(roomToken)
        dataStore.saveMessageQueue(roomToken, null) // empties the queue
        while (queue.size > 0) {
            val msg = queue.removeFirst()
            sleep(DELAY_BETWEEN_QUEUED_MESSAGES)
            sendChatMessage(
                roomToken,
                credentials,
                url,
                msg.message!!,
                msg.displayName!!,
                msg.replyTo!!,
                msg.sendWithoutNotification!!
            )
        }
        _messageQueueSizeFlow.tryEmit(0)
    }

    fun getTempMessagesFromMessageQueue(roomToken: String) {
        val queue = dataStore.getMessageQueue(roomToken)
        val list = mutableListOf<String>()
        for (msg in queue) {
            Log.d("Julius", "Msg: ${msg.message}")
            list.add(msg.message.toString())
        }
        _messageQueueFlow.postValue(list)
    }

    fun switchToMessageQueue(shouldQueue: Boolean) {
        isQueueing = shouldQueue
    }

    fun restoreMessageQueue(roomToken: String) {
        messageQueue = dataStore.getMessageQueue(roomToken)
        _messageQueueSizeFlow.tryEmit(messageQueue.size)
    }

    companion object {
        private val TAG = MessageInputViewModel::class.java.simpleName
        private const val DELAY_BETWEEN_QUEUED_MESSAGES: Long = 100
    }
}
