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
import androidx.lifecycle.viewModelScope
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.io.AudioFocusRequestManager
import com.nextcloud.talk.chat.data.io.AudioRecorderManager
import com.nextcloud.talk.chat.data.io.MediaPlayerManager
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.models.MessageDraft
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.chat.ChatUtils
import com.nextcloud.talk.utils.message.SendMessageUtils
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("Detekt.TooManyFunctions")
@AutoInjector(NextcloudTalkApplication::class)
class MessageInputViewModel :
    ViewModel(),
    DefaultLifecycleObserver {

    data class MessageInputState(
        val messageText: String = "",
        val messageCursor: Int = 0,
        val quotedJsonId: Int? = null,
        val quotedDisplayName: String? = null,
        val quotedMessageText: String? = null,
        val quotedImageUrl: String? = null,
        val threadTitle: String? = null,
        val editMessage: ChatMessage? = null,
        val isThreadCreationInProgress: Boolean = false
    )

    enum class LifeCycleFlag {
        PAUSED,
        RESUMED,
        STOPPED
    }

    init {
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)
    }

    @Inject
    lateinit var audioRecorderManager: AudioRecorderManager

    @Inject
    lateinit var mediaPlayerManager: MediaPlayerManager

    @Inject
    lateinit var audioFocusRequestManager: AudioFocusRequestManager

    lateinit var chatRepository: ChatMessageRepository
    lateinit var chatNetworkDataSource: ChatNetworkDataSource
    lateinit var currentLifeCycleFlag: LifeCycleFlag
    val disposableSet = mutableSetOf<Disposable>()

    fun setData(chatMessageRepository: ChatMessageRepository) {
        chatRepository = chatMessageRepository
    }

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

    val mediaPlayerSeekbarObserver: Flow<Int>
        get() = mediaPlayerManager.mediaPlayerSeekBarPosition

    private val _getEditChatMessage: MutableStateFlow<ChatMessage?> = MutableStateFlow(null)
    val getEditChatMessage: StateFlow<ChatMessage?>
        get() = _getEditChatMessage

    private val _getReplyChatMessage: MutableLiveData<ChatMessage?> = MutableLiveData()
    val getReplyChatMessage: LiveData<ChatMessage?>
        get() = _getReplyChatMessage

    object CreateThreadStartState : ViewState
    class CreateThreadEditState : ViewState

    private val _createThreadViewState: MutableLiveData<ViewState> = MutableLiveData(CreateThreadStartState)
    val createThreadViewState: LiveData<ViewState>
        get() = _createThreadViewState

    sealed interface ViewState

    object SendChatMessageStartState : ViewState
    class SendChatMessageSuccessState(val message: CharSequence) : ViewState
    class SendChatMessageErrorState(val message: CharSequence) : ViewState

    private val _sendChatMessageViewState: MutableLiveData<ViewState> = MutableLiveData(SendChatMessageStartState)
    val sendChatMessageViewState: LiveData<ViewState>
        get() = _sendChatMessageViewState

    object EditMessageErrorState : ViewState
    class EditMessageSuccessState(val messageEdited: ChatOverallSingleMessage) : ViewState

    private val _editMessageViewState: MutableLiveData<ViewState> = MutableLiveData()
    val editMessageViewState: LiveData<ViewState>
        get() = _editMessageViewState

    private val _isVoicePreviewPlaying: MutableLiveData<Boolean> = MutableLiveData(false)
    val isVoicePreviewPlaying: LiveData<Boolean>
        get() = _isVoicePreviewPlaying

    object ScheduleChatMessageStartState : ViewState
    class ScheduleChatMessageSuccessState(val scheduledAt: Long) : ViewState
    object ScheduleChatMessageErrorState : ViewState

    private val _scheduleChatMessageViewState: MutableLiveData<ViewState> =
        MutableLiveData(ScheduleChatMessageStartState)
    val scheduleChatMessageViewState: LiveData<ViewState>
        get() = _scheduleChatMessageViewState

    @Suppress("LongParameterList")
    fun sendChatMessage(
        credentials: String,
        url: String,
        message: String,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        threadTitle: String?
    ) {
        val referenceId = SendMessageUtils().generateReferenceId()
        Log.d(TAG, "Random SHA-256 Hash: $referenceId")

        viewModelScope.launch {
            chatRepository.addTemporaryMessage(
                message,
                displayName,
                replyTo,
                sendWithoutNotification,
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

        viewModelScope.launch {
            chatRepository.sendChatMessage(
                credentials,
                url,
                message,
                displayName,
                replyTo,
                sendWithoutNotification,
                referenceId,
                threadTitle
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

    fun sendUnsentMessages(credentials: String, url: String) {
        viewModelScope.launch {
            chatRepository.sendUnsentChatMessages(
                credentials,
                url
            )
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

    fun editTempChatMessage(message: ChatMessage, editedMessageText: String) {
        viewModelScope.launch {
            chatRepository.editTempChatMessage(
                message,
                editedMessageText
            ).collect {}
        }
    }

    private val _inputState = MutableStateFlow(MessageInputState())
    val inputState: StateFlow<MessageInputState> = _inputState

    fun updateMessageText(text: String) {
        _inputState.update { it.copy(messageText = text) }
    }

    fun updateMessageCursor(cursor: Int) {
        _inputState.update { it.copy(messageCursor = cursor) }
    }

    fun updateThreadTitle(title: String) {
        _inputState.update { it.copy(threadTitle = title) }
    }

    fun initFromDraft(draft: MessageDraft) {
        _inputState.update {
            it.copy(
                messageText = draft.messageText,
                messageCursor = draft.messageCursor,
                quotedJsonId = draft.quotedJsonId,
                quotedDisplayName = draft.quotedDisplayName,
                quotedMessageText = draft.quotedMessageText,
                quotedImageUrl = draft.quotedImageUrl,
                threadTitle = draft.threadTitle,
                isThreadCreationInProgress = false
            )
        }
    }

    fun toDraft(): MessageDraft =
        MessageDraft(
            messageText = _inputState.value.messageText,
            messageCursor = _inputState.value.messageCursor,
            quotedJsonId = _inputState.value.quotedJsonId,
            quotedDisplayName = _inputState.value.quotedDisplayName,
            quotedMessageText = _inputState.value.quotedMessageText,
            quotedImageUrl = _inputState.value.quotedImageUrl,
            threadTitle = _inputState.value.threadTitle
        )

    fun reply(message: ChatMessage?) {
        _inputState.update {
            it.copy(
                quotedJsonId = message?.jsonMessageId,
                quotedDisplayName = message?.actorDisplayName,
                quotedMessageText = message?.getRichText(),
                quotedImageUrl = "" // TODO
            )
        }
        _getReplyChatMessage.postValue(message)
    }

    fun cancelReply() {
        _inputState.update {
            it.copy(
                quotedJsonId = null,
                quotedDisplayName = null,
                quotedMessageText = null,
                quotedImageUrl = null
            )
        }
        _getReplyChatMessage.postValue(null)
    }

    fun edit(message: ChatMessage?) {
        val text = message?.let {
            ChatUtils.getParsedMessage(it.message, it.messageParameters)
        } ?: ""
        _inputState.update {
            it.copy(
                editMessage = message,
                messageText = text,
                messageCursor = text.length
            )
        }
        _getEditChatMessage.value = message
    }

    fun cancelEdit() {
        _inputState.update { it.copy(editMessage = null) }
        _getEditChatMessage.value = null
    }

    fun cancelCreateThread() {
        _inputState.update { it.copy(threadTitle = "") }
        stopThreadCreation()
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
            mediaPlayerManager.pause(false)
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

    fun startThreadCreation() {
        _inputState.update { it.copy(isThreadCreationInProgress = true) }
        _createThreadViewState.postValue(CreateThreadEditState())
    }

    fun stopThreadCreation() {
        _inputState.update { it.copy(isThreadCreationInProgress = false) }
        _createThreadViewState.postValue(CreateThreadStartState)
    }

    @Suppress("LongParameterList")
    fun scheduleChatMessage(
        credentials: String,
        url: String,
        message: String,
        replyTo: Int?,
        sendWithoutNotification: Boolean,
        threadTitle: String?,
        threadId: Long?,
        sendAt: Int?
    ) {
        val referenceId = SendMessageUtils().generateReferenceId()
        Log.d(TAG, "Random SHA-256 Hash: $referenceId")

        viewModelScope.launch {
            chatRepository.sendScheduledChatMessage(
                credentials,
                url,
                message,
                replyTo,
                sendWithoutNotification,
                threadTitle,
                threadId,
                sendAt
            ).collect { result ->
                if (result.isSuccess) {
                    _scheduleChatMessageViewState.value =
                        ScheduleChatMessageSuccessState(sendAt?.toLong() ?: 0L)
                } else {
                    _scheduleChatMessageViewState.value = ScheduleChatMessageErrorState
                }
            }
        }
    }

    companion object {
        private val TAG = MessageInputViewModel::class.java.simpleName
    }
}
