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
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.io.AudioFocusRequestManager
import com.nextcloud.talk.chat.data.io.AudioRecorderManager
import com.nextcloud.talk.chat.data.io.MediaPlayerManager
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.utils.message.SendMessageUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.commons.models.IMessage
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.Thread.sleep
import javax.inject.Inject

class MessageInputViewModel @Inject constructor(
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

    lateinit var chatRepository: ChatMessageRepository
    lateinit var currentLifeCycleFlag: LifeCycleFlag
    val disposableSet = mutableSetOf<Disposable>()

    fun setData(chatMessageRepository: ChatMessageRepository){
        chatRepository = chatMessageRepository
    }

    data class QueuedMessage(
        val id: Int,
        var message: CharSequence? = null,
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
    class SendChatMessageErrorState(val message: CharSequence) : ViewState
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

    private val _messageQueueFlow: MutableLiveData<List<QueuedMessage>> = MutableLiveData()
    val messageQueueFlow: LiveData<List<QueuedMessage>>
        get() = _messageQueueFlow

    private val _callStartedFlow: MutableLiveData<Pair<ChatMessage, Boolean>> = MutableLiveData()
    val callStartedFlow: LiveData<Pair<ChatMessage, Boolean>>
        get() = _callStartedFlow

    @Suppress("LongParameterList")
    fun sendChatMessage(
        internalId: String,
        credentials: String,
        url: String,
        message: CharSequence,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean
    ) {
        val referenceId = SendMessageUtils().generateReferenceId()
        Log.d(TAG, "Random SHA-256 Hash: $referenceId")

        viewModelScope.launch {
            chatRepository.addTemporaryMessage(
                message,
                displayName,
                replyTo,
                referenceId
            ).collect { result ->
                if (result.isSuccess) {
                    Log.d(TAG, "temp message ref id: " + (result.getOrNull()?.referenceId ?: "none"))

                    _sendChatMessageViewState.value = SendChatMessageSuccessState(message)
                } else {
                    _sendChatMessageViewState.value = SendChatMessageErrorState(message)
                }
            }
        }

        if (isQueueing) {
            val tempID = System.currentTimeMillis().toInt()
            val qMsg = QueuedMessage(tempID, message, displayName, replyTo, sendWithoutNotification)
            messageQueue = dataStore.getMessageQueue(internalId)
            messageQueue.add(qMsg)
            dataStore.saveMessageQueue(internalId, messageQueue)
            _messageQueueSizeFlow.update { messageQueue.size }
            _messageQueueFlow.postValue(listOf(qMsg))
            return
        }

        viewModelScope.launch {
            chatRepository.sendChatMessage(
                credentials,
                url,
                message,
                displayName,
                replyTo,
                sendWithoutNotification,
                referenceId
            ).collect { result ->
                if (result.isSuccess) {
                    Log.d(TAG, "received ref id: " + (result.getOrNull()?.referenceId ?: "none"))

                    _sendChatMessageViewState.value = SendChatMessageSuccessState(message)
                } else {
                    _sendChatMessageViewState.value = SendChatMessageErrorState(message)
                }
            }
        }
    }

    fun editChatMessage(credentials: String, url: String, text: String) {
        viewModelScope.launch {
            chatRepository.editChatMessage(
                credentials,
                url,
                text
            ).collect { result ->
                if (result.isSuccess) {
                    _editMessageViewState.value = EditMessageSuccessState(result.getOrNull()!!)
                } else {
                    _editMessageViewState.value = EditMessageErrorState
                }
            }
        }
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

    fun sendAndEmptyMessageQueue(internalId: String, credentials: String, url: String) {
        if (isQueueing) return
        messageQueue.clear()

        val queue = dataStore.getMessageQueue(internalId)
        dataStore.saveMessageQueue(internalId, null) // empties the queue
        while (queue.size > 0) {
            val msg = queue.removeAt(0)
            sendChatMessage(
                internalId,
                credentials,
                url,
                msg.message!!,
                msg.displayName!!,
                msg.replyTo!!,
                msg.sendWithoutNotification!!
            )
            sleep(DELAY_BETWEEN_QUEUED_MESSAGES)
        }
        _messageQueueSizeFlow.tryEmit(0)
    }

    fun getTempMessagesFromMessageQueue(internalId: String) {
        val queue = dataStore.getMessageQueue(internalId)
        val list = mutableListOf<QueuedMessage>()
        for (msg in queue) {
            list.add(msg)
        }
        _messageQueueFlow.postValue(list)
    }

    fun switchToMessageQueue(shouldQueue: Boolean) {
        isQueueing = shouldQueue
    }

    fun restoreMessageQueue(internalId: String) {
        messageQueue = dataStore.getMessageQueue(internalId)
        _messageQueueSizeFlow.tryEmit(messageQueue.size)
    }

    fun removeFromQueue(internalId: String, id: Int) {
        val queue = dataStore.getMessageQueue(internalId)
        for (qMsg in queue) {
            if (qMsg.id == id) {
                queue.remove(qMsg)
                break
            }
        }
        dataStore.saveMessageQueue(internalId, queue)
        _messageQueueSizeFlow.tryEmit(queue.size)
    }

    fun editQueuedMessage(internalId: String, id: Int, newMessage: String) {
        val queue = dataStore.getMessageQueue(internalId)
        for (qMsg in queue) {
            if (qMsg.id == id) {
                qMsg.message = newMessage
                break
            }
        }
        dataStore.saveMessageQueue(internalId, queue)
    }

    fun showCallStartedIndicator(recent: ChatMessage, show: Boolean) {
        _callStartedFlow.postValue(Pair(recent, show))
    }

    companion object {
        private val TAG = MessageInputViewModel::class.java.simpleName
        private const val DELAY_BETWEEN_QUEUED_MESSAGES: Long = 1000
    }
}
