/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Christian Reiner <foss@christian-reiner.info>
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.viewmodels

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.io.AudioFocusRequestManager
import com.nextcloud.talk.chat.data.io.MediaPlayerManager
import com.nextcloud.talk.chat.data.io.MediaRecorderManager
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.toIntOrZero
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import com.nextcloud.talk.models.MessageDraft
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.ReactionAddedModel
import com.nextcloud.talk.models.domain.ReactionDeletedModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.opengraph.Reference
import com.nextcloud.talk.models.json.reminder.Reminder
import com.nextcloud.talk.models.json.threads.ThreadInfo
import com.nextcloud.talk.models.json.userAbsence.UserAbsenceData
import com.nextcloud.talk.repositories.reactions.ReactionsRepository
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.ui.PlaybackSpeed
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.preferences.AppPreferences
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongParameterList")
class ChatViewModel @Inject constructor(
    // should be removed here. Use it via RetrofitChatNetwork
    private val appPreferences: AppPreferences,
    private val chatNetworkDataSource: ChatNetworkDataSource,
    private val chatRepository: ChatMessageRepository,
    private val threadsRepository: ThreadsRepository,
    private val conversationRepository: OfflineConversationsRepository,
    private val reactionsRepository: ReactionsRepository,
    private val mediaRecorderManager: MediaRecorderManager,
    private val audioFocusRequestManager: AudioFocusRequestManager,
    private val userProvider: CurrentUserProviderNew
) : ViewModel(),
    DefaultLifecycleObserver {

    enum class LifeCycleFlag {
        PAUSED,
        RESUMED,
        STOPPED
    }

    private val mediaPlayerManager: MediaPlayerManager = MediaPlayerManager.sharedInstance(appPreferences)
    lateinit var currentLifeCycleFlag: LifeCycleFlag
    val disposableSet = mutableSetOf<Disposable>()
    var mediaPlayerDuration = mediaPlayerManager.mediaPlayerDuration
    val mediaPlayerPosition = mediaPlayerManager.mediaPlayerPosition
    var chatRoomToken: String = ""
    var messageDraft: MessageDraft = MessageDraft()
    lateinit var participantPermissions: ParticipantPermissions

    fun getChatRepository(): ChatMessageRepository = chatRepository

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        currentLifeCycleFlag = LifeCycleFlag.RESUMED
        mediaRecorderManager.handleOnResume()
        chatRepository.handleOnResume()
        mediaPlayerManager.handleOnResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        currentLifeCycleFlag = LifeCycleFlag.PAUSED
        disposableSet.forEach { disposable -> disposable.dispose() }
        disposableSet.clear()
        mediaRecorderManager.handleOnPause()
        chatRepository.handleOnPause()
        mediaPlayerManager.handleOnPause()

        saveMessageDraft()
    }

    private fun saveMessageDraft() {
        CoroutineScope(Dispatchers.IO).launch {
            val model = conversationRepository.getLocallyStoredConversation(chatRoomToken)
            model?.let {
                it.messageDraft = messageDraft
                conversationRepository.updateConversation(it)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        currentLifeCycleFlag = LifeCycleFlag.STOPPED
        mediaRecorderManager.handleOnStop()
        chatRepository.handleOnStop()
        mediaPlayerManager.handleOnStop()
    }

    val backgroundPlayUIFlow = mediaPlayerManager.backgroundPlayUIFlow

    val mediaPlayerSeekbarObserver: Flow<ChatMessage>
        get() = mediaPlayerManager.mediaPlayerSeekBarPositionMsg

    val managerStateFlow: Flow<MediaPlayerManager.MediaPlayerManagerState>
        get() = mediaPlayerManager.managerState

    val voiceMessagePlayBackUIFlow: Flow<PlaybackSpeed>
        get() = _voiceMessagePlayBackUIFlow
    private val _voiceMessagePlayBackUIFlow: MutableSharedFlow<PlaybackSpeed> = MutableSharedFlow()

    val getAudioFocusChange: LiveData<AudioFocusRequestManager.ManagerState>
        get() = audioFocusRequestManager.getManagerState

    private val _recordTouchObserver: MutableLiveData<Float> = MutableLiveData()
    val recordTouchObserver: LiveData<Float>
        get() = _recordTouchObserver

    private val _getVoiceRecordingInProgress: MutableLiveData<Boolean> = MutableLiveData()
    val getVoiceRecordingInProgress: LiveData<Boolean>
        get() = _getVoiceRecordingInProgress

    private val _getVoiceRecordingLocked: MutableLiveData<Boolean> = MutableLiveData()
    val getVoiceRecordingLocked: LiveData<Boolean>
        get() = _getVoiceRecordingLocked

    private val _outOfOfficeViewState = MutableLiveData<OutOfOfficeUIState>(OutOfOfficeUIState.None)
    val outOfOfficeViewState: LiveData<OutOfOfficeUIState>
        get() = _outOfOfficeViewState

    private val _unbindRoomResult = MutableLiveData<UnbindRoomUiState>(UnbindRoomUiState.None)
    val unbindRoomResult: LiveData<UnbindRoomUiState>
        get() = _unbindRoomResult

    private val _voiceMessagePlaybackSpeedPreferences: MutableLiveData<Map<String, PlaybackSpeed>> = MutableLiveData()
    val voiceMessagePlaybackSpeedPreferences: LiveData<Map<String, PlaybackSpeed>>
        get() = _voiceMessagePlaybackSpeedPreferences

    private val _getContextChatMessages: MutableLiveData<List<ChatMessageJson>> = MutableLiveData()
    val getContextChatMessages: LiveData<List<ChatMessageJson>>
        get() = _getContextChatMessages

    private val _threadCreationState = MutableStateFlow<ThreadCreationUiState>(ThreadCreationUiState.None)
    val threadCreationState: StateFlow<ThreadCreationUiState> = _threadCreationState

    private val _threadRetrieveState = MutableStateFlow<ThreadRetrieveUiState>(ThreadRetrieveUiState.None)
    val threadRetrieveState: StateFlow<ThreadRetrieveUiState> = _threadRetrieveState

    val getOpenGraph: LiveData<Reference>
        get() = _getOpenGraph
    private val _getOpenGraph: MutableLiveData<Reference> = MutableLiveData()

    val getMessageFlow = chatRepository.messageFlow
        .onEach {
            _chatMessageViewState.value = if (_chatMessageViewState.value == ChatMessageInitialState) {
                ChatMessageStartState
            } else {
                ChatMessageUpdateState
            }
        }.catch {
            _chatMessageViewState.value = ChatMessageErrorState
        }

    val getRemoveMessageFlow = chatRepository.removeMessageFlow

    val getUpdateMessageFlow = chatRepository.updateMessageFlow

    val getLastCommonReadFlow = chatRepository.lastCommonReadFlow

    val getLastReadMessageFlow = chatRepository.lastReadMessageFlow

    val getConversationFlow = conversationRepository.conversationFlow
        .onEach {
            _getRoomViewState.value = GetRoomSuccessState
        }.catch {
            _getRoomViewState.value = GetRoomErrorState
        }

    val getGeneralUIFlow = chatRepository.generalUIFlow

    sealed interface ViewState

    object GetReminderStartState : ViewState
    open class GetReminderExistState(val reminder: Reminder) : ViewState
    object GetReminderStateSet : ViewState

    private val _getReminderExistState: MutableLiveData<ViewState> = MutableLiveData(GetReminderStartState)

    val getReminderExistState: LiveData<ViewState>
        get() = _getReminderExistState

    object GetRoomStartState : ViewState
    object GetRoomErrorState : ViewState
    object GetRoomSuccessState : ViewState

    private val _getRoomViewState: MutableLiveData<ViewState> = MutableLiveData(GetRoomStartState)
    val getRoomViewState: LiveData<ViewState>
        get() = _getRoomViewState

    object GetCapabilitiesStartState : ViewState
    object GetCapabilitiesErrorState : ViewState
    open class GetCapabilitiesInitialLoadState(val spreedCapabilities: SpreedCapability) : ViewState
    open class GetCapabilitiesUpdateState(val spreedCapabilities: SpreedCapability) : ViewState

    private val _getCapabilitiesViewState: MutableLiveData<ViewState> = MutableLiveData(GetCapabilitiesStartState)
    val getCapabilitiesViewState: LiveData<ViewState>
        get() = _getCapabilitiesViewState

    object JoinRoomStartState : ViewState
    object JoinRoomErrorState : ViewState
    open class JoinRoomSuccessState(val conversationModel: ConversationModel) : ViewState

    private val _joinRoomViewState: MutableLiveData<ViewState> = MutableLiveData(JoinRoomStartState)
    val joinRoomViewState: LiveData<ViewState>
        get() = _joinRoomViewState

    object LeaveRoomStartState : ViewState
    class LeaveRoomSuccessState(val funToCallWhenLeaveSuccessful: (() -> Unit)?) : ViewState

    private val _leaveRoomViewState: MutableLiveData<ViewState> = MutableLiveData(LeaveRoomStartState)
    val leaveRoomViewState: LiveData<ViewState>
        get() = _leaveRoomViewState

    object ChatMessageInitialState : ViewState
    object ChatMessageStartState : ViewState
    object ChatMessageUpdateState : ViewState
    object ChatMessageErrorState : ViewState

    private val _chatMessageViewState: MutableLiveData<ViewState> = MutableLiveData(ChatMessageInitialState)
    val chatMessageViewState: LiveData<ViewState>
        get() = _chatMessageViewState

    object DeleteChatMessageStartState : ViewState
    class DeleteChatMessageSuccessState(val msg: ChatOverallSingleMessage) : ViewState
    object DeleteChatMessageErrorState : ViewState

    private val _deleteChatMessageViewState: MutableLiveData<ViewState> = MutableLiveData(DeleteChatMessageStartState)
    val deleteChatMessageViewState: LiveData<ViewState>
        get() = _deleteChatMessageViewState

    object CreateRoomStartState : ViewState
    object CreateRoomErrorState : ViewState
    class CreateRoomSuccessState(val roomOverall: RoomOverall) : ViewState

    private val _createRoomViewState: MutableLiveData<ViewState> = MutableLiveData(CreateRoomStartState)
    val createRoomViewState: LiveData<ViewState>
        get() = _createRoomViewState

    object ReactionAddedStartState : ViewState
    class ReactionAddedSuccessState(val reactionAddedModel: ReactionAddedModel) : ViewState

    private val _reactionAddedViewState: MutableLiveData<ViewState> = MutableLiveData(ReactionAddedStartState)
    val reactionAddedViewState: LiveData<ViewState>
        get() = _reactionAddedViewState

    object ReactionDeletedStartState : ViewState
    class ReactionDeletedSuccessState(val reactionDeletedModel: ReactionDeletedModel) : ViewState

    private val _reactionDeletedViewState: MutableLiveData<ViewState> = MutableLiveData(ReactionDeletedStartState)
    val reactionDeletedViewState: LiveData<ViewState>
        get() = _reactionDeletedViewState

    fun initData(credentials: String, urlForChatting: String, roomToken: String, threadId: Long?) {
        chatRepository.initData(credentials, urlForChatting, roomToken, threadId)
        chatRoomToken = roomToken
    }

    fun updateConversation(currentConversation: ConversationModel) {
        chatRepository.updateConversation(currentConversation)
    }

    fun getRoom(token: String) {
        _getRoomViewState.value = GetRoomStartState
        conversationRepository.getRoom(token)
    }

    fun getCapabilities(user: User, token: String, conversationModel: ConversationModel) {
        Log.d(TAG, "Remote server ${conversationModel.remoteServer}")
        if (conversationModel.remoteServer.isNullOrEmpty()) {
            if (_getCapabilitiesViewState.value == GetCapabilitiesStartState) {
                _getCapabilitiesViewState.value = GetCapabilitiesInitialLoadState(
                    user.capabilities!!.spreedCapability!!
                )
            } else {
                _getCapabilitiesViewState.value = GetCapabilitiesUpdateState(user.capabilities!!.spreedCapability!!)
            }
            participantPermissions = ParticipantPermissions(
                user.capabilities!!.spreedCapability!!,
                conversationModel
            )
        } else {
            chatNetworkDataSource.getCapabilities(user, token)
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(object : Observer<SpreedCapability> {
                    override fun onSubscribe(d: Disposable) {
                        disposableSet.add(d)
                    }

                    override fun onNext(spreedCapabilities: SpreedCapability) {
                        if (_getCapabilitiesViewState.value == GetCapabilitiesStartState) {
                            _getCapabilitiesViewState.value = GetCapabilitiesInitialLoadState(spreedCapabilities)
                        } else {
                            _getCapabilitiesViewState.value = GetCapabilitiesUpdateState(spreedCapabilities)
                        }
                        participantPermissions = ParticipantPermissions(
                            spreedCapabilities,
                            conversationModel
                        )
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, "Error when fetching spreed capabilities", e)
                        _getCapabilitiesViewState.value = GetCapabilitiesErrorState
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    fun joinRoom(user: User, token: String, roomPassword: String) {
        _joinRoomViewState.value = JoinRoomStartState
        chatNetworkDataSource.joinRoom(user, token, roomPassword)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.retry(JOIN_ROOM_RETRY_COUNT)
            ?.subscribe(JoinRoomObserver())
    }

    fun setReminder(user: User, roomToken: String, messageId: String, timestamp: Int, chatApiVersion: Int) {
        chatNetworkDataSource.setReminder(user, roomToken, messageId, timestamp, chatApiVersion)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(SetReminderObserver())
    }

    fun getReminder(user: User, roomToken: String, messageId: String, chatApiVersion: Int) {
        chatNetworkDataSource.getReminder(user, roomToken, messageId, chatApiVersion)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(GetReminderObserver())
    }

    fun overrideReminderState() {
        _getReminderExistState.value = GetReminderStateSet
    }

    fun deleteReminder(user: User, roomToken: String, messageId: String, chatApiVersion: Int) {
        chatNetworkDataSource.deleteReminder(user, roomToken, messageId, chatApiVersion)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposableSet.add(d)
                }

                override fun onNext(genericOverall: GenericOverall) {
                    _getReminderExistState.value = GetReminderStartState
                }

                override fun onError(e: Throwable) {
                    Log.d(TAG, "Error when deleting reminder", e)
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    fun leaveRoom(credentials: String, url: String, funToCallWhenLeaveSuccessful: (() -> Unit)?) {
        val startNanoTime = System.nanoTime()
        chatNetworkDataSource.leaveRoom(credentials, url)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "leaveRoom - leaveRoom - ERROR", e)
                }

                override fun onComplete() {
                    Log.d(TAG, "leaveRoom - leaveRoom - completed: $startNanoTime")
                }

                override fun onNext(t: GenericOverall) {
                    _leaveRoomViewState.value = LeaveRoomSuccessState(funToCallWhenLeaveSuccessful)
                    _getCapabilitiesViewState.value = GetCapabilitiesStartState
                    _getRoomViewState.value = GetRoomStartState
                }
            })
    }

    fun createRoom(credentials: String, url: String, queryMap: Map<String, String>) {
        chatNetworkDataSource.createRoom(credentials, url, queryMap)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<RoomOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    _createRoomViewState.value = CreateRoomErrorState
                    Log.e(TAG, e.message, e)
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(t: RoomOverall) {
                    _createRoomViewState.value = CreateRoomSuccessState(t)
                }
            })
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun createThread(credentials: String, url: String) {
        viewModelScope.launch {
            try {
                val thread = chatNetworkDataSource.createThread(credentials, url)
                _threadCreationState.value = ThreadCreationUiState.Success(thread.ocs?.data)
            } catch (exception: Exception) {
                _threadCreationState.value = ThreadCreationUiState.Error(exception)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun getThread(credentials: String, url: String) {
        viewModelScope.launch {
            try {
                val thread = threadsRepository.getThread(credentials, url)
                _threadRetrieveState.value = ThreadRetrieveUiState.Success(thread.ocs?.data)
            } catch (exception: Exception) {
                _threadRetrieveState.value = ThreadRetrieveUiState.Error(exception)
            }
        }
    }

    fun loadMessages(withCredentials: String, withUrl: String) {
        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_CHAT_URL, withUrl)
        bundle.putString(BundleKeys.KEY_CREDENTIALS, withCredentials)
        chatRepository.initScopeAndLoadInitialMessages(
            withNetworkParams = bundle
        )
    }

    fun loadMoreMessages(
        beforeMessageId: Long,
        roomToken: String,
        withMessageLimit: Int,
        withCredentials: String,
        withUrl: String
    ) {
        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_CHAT_URL, withUrl)
        bundle.putString(BundleKeys.KEY_CREDENTIALS, withCredentials)
        chatRepository.loadMoreMessages(
            beforeMessageId,
            roomToken,
            withMessageLimit,
            withNetworkParams = bundle
        )
    }

    // fun initMessagePolling(withCredentials: String, withUrl: String, roomToken: String) {
    //     val bundle = Bundle()
    //     bundle.putString(BundleKeys.KEY_CHAT_URL, withUrl)
    //     bundle.putString(BundleKeys.KEY_CREDENTIALS, withCredentials)
    //     chatRepository.initMessagePolling(roomToken, withNetworkParams = bundle)
    // }

    fun deleteChatMessages(credentials: String, url: String, messageId: String) {
        chatNetworkDataSource.deleteChatMessage(credentials, url)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ChatOverallSingleMessage> {
                override fun onSubscribe(d: Disposable) {
                    disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.e(
                        TAG,
                        "Something went wrong when trying to delete message with id " +
                            messageId,
                        e
                    )
                    _deleteChatMessageViewState.value = DeleteChatMessageErrorState
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(t: ChatOverallSingleMessage) {
                    _deleteChatMessageViewState.value = DeleteChatMessageSuccessState(t)
                }
            })
    }

    fun setChatReadMarker(credentials: String, url: String, previousMessageId: Int) {
        chatNetworkDataSource.setChatReadMarker(credentials, url, previousMessageId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, e.message, e)
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(t: GenericOverall) {
                    // unused atm
                }
            })
    }

    fun shareToNotes(credentials: String, url: String, message: String, displayName: String) {
        chatNetworkDataSource.shareToNotes(credentials, url, message, displayName)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ChatOverallSingleMessage> {
                override fun onSubscribe(d: Disposable) {
                    disposableSet.add(d)
                }

                override fun onNext(genericOverall: ChatOverallSingleMessage) {
                    // unused atm
                }

                override fun onError(e: Throwable) {
                    Log.d(TAG, "Error when sharing to notes $e")
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    suspend fun checkForNoteToSelf(credentials: String, baseUrl: String): ConversationModel? {
        val response = chatNetworkDataSource.checkForNoteToSelf(credentials, baseUrl)
        if (response.ocs?.meta?.statusCode == HTTP_CODE_OK) {
            val noteToSelfConversation = ConversationModel.mapToConversationModel(
                response.ocs?.data!!,
                userProvider.currentUser.blockingGet()
            )
            return noteToSelfConversation
        } else {
            return null
        }
    }

    fun shareLocationToNotes(credentials: String, url: String, objectType: String, objectId: String, metadata: String) {
        chatNetworkDataSource.shareLocationToNotes(credentials, url, objectType, objectId, metadata)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposableSet.add(d)
                }

                override fun onNext(genericOverall: GenericOverall) {
                    // unused atm
                }

                override fun onError(e: Throwable) {
                    Log.e(TAG, "Error when sharing location to notes $e")
                }

                override fun onComplete() {
                    // unused atm
                }
            })
    }

    fun deleteReaction(roomToken: String, chatMessage: ChatMessage, emoji: String) {
        reactionsRepository.deleteReaction(roomToken, chatMessage, emoji)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ReactionDeletedModel> {
                override fun onSubscribe(d: Disposable) {
                    disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.d(TAG, "$e")
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(reactionDeletedModel: ReactionDeletedModel) {
                    if (reactionDeletedModel.success) {
                        _reactionDeletedViewState.value = ReactionDeletedSuccessState(reactionDeletedModel)
                    }
                }
            })
    }

    fun addReaction(roomToken: String, chatMessage: ChatMessage, emoji: String) {
        reactionsRepository.addReaction(roomToken, chatMessage, emoji)
            .subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<ReactionAddedModel> {
                override fun onSubscribe(d: Disposable) {
                    disposableSet.add(d)
                }

                override fun onError(e: Throwable) {
                    Log.d(TAG, "$e")
                }

                override fun onComplete() {
                    // unused atm
                }

                override fun onNext(reactionAddedModel: ReactionAddedModel) {
                    if (reactionAddedModel.success) {
                        _reactionAddedViewState.value = ReactionAddedSuccessState(reactionAddedModel)
                    }
                }
            })
    }

    fun startAudioRecording(context: Context, currentConversation: ConversationModel) {
        audioFocusRequestManager.audioFocusRequest(true) {
            Log.d(TAG, "Recording Started")
            mediaRecorderManager.start(context, currentConversation)
            _getVoiceRecordingInProgress.postValue(true)
        }
    }

    fun stopAudioRecording() {
        audioFocusRequestManager.audioFocusRequest(false) {
            mediaRecorderManager.stop()
            _getVoiceRecordingInProgress.postValue(false)
            Log.d(TAG, "Recording stopped")
        }
    }

    fun stopAndSendAudioRecording(roomToken: String = "", replyToMessageId: Int? = null, displayName: String) {
        stopAudioRecording()

        if (mediaRecorderManager.mediaRecorderState != MediaRecorderManager.MediaRecorderState.ERROR) {
            val uri = Uri.fromFile(File(mediaRecorderManager.currentVoiceRecordFile))
            Log.d(TAG, "File uploaded")
            uploadFile(
                fileUri = uri.toString(),
                isVoiceMessage = true,
                caption = "",
                roomToken = roomToken,
                replyToMessageId = replyToMessageId,
                displayName = displayName
            )
        }
    }

    fun stopAndDiscardAudioRecording() {
        stopAudioRecording()
        Log.d(TAG, "File discarded")
        val cachedFile = File(mediaRecorderManager.currentVoiceRecordFile)
        cachedFile.delete()
    }

    fun getCurrentVoiceRecordFile(): String = mediaRecorderManager.currentVoiceRecordFile

    fun uploadFile(
        fileUri: String,
        isVoiceMessage: Boolean,
        caption: String = "",
        roomToken: String = "",
        replyToMessageId: Int? = null,
        displayName: String
    ) {
        val metaDataMap = mutableMapOf<String, Any>()
        var room = ""

        if (!participantPermissions.hasChatPermission()) {
            Log.w(TAG, "uploading file(s) is forbidden because of missing attendee permissions")
            return
        }

        if (replyToMessageId != 0) {
            metaDataMap["replyTo"] = replyToMessageId.toString()
        }

        if (isVoiceMessage) {
            metaDataMap["messageType"] = "voice-message"
        }

        if (caption != "") {
            metaDataMap["caption"] = caption
        }

        val metaData = Gson().toJson(metaDataMap)

        room = if (roomToken == "") chatRoomToken else roomToken

        try {
            require(fileUri.isNotEmpty())
            UploadAndShareFilesWorker.upload(
                fileUri,
                room,
                displayName,
                metaData
            )
        } catch (e: IllegalArgumentException) {
            Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
        }
    }

    fun postToRecordTouchObserver(float: Float) {
        _recordTouchObserver.postValue(float)
    }

    fun setVoiceRecordingLocked(boolean: Boolean) {
        _getVoiceRecordingLocked.postValue(boolean)
    }

    // Made this so that the MediaPlayer in ChatActivity can be focused. Eventually the player logic should be moved
    // to the MediaPlayerManager class, so the audio focus logic can be handled in ChatViewModel, as it's done in
    // the MessageInputViewModel
    fun audioRequest(request: Boolean, callback: () -> Unit) {
        audioFocusRequestManager.audioFocusRequest(request, callback)
    }

    fun handleOrientationChange() {
        _getCapabilitiesViewState.value = GetCapabilitiesStartState
    }

    fun getMessageById(url: String, conversationModel: ConversationModel, messageId: Long): Flow<ChatMessage> =
        flow {
            val bundle = Bundle()
            bundle.putString(BundleKeys.KEY_CHAT_URL, url)
            bundle.putString(
                BundleKeys.KEY_CREDENTIALS,
                userProvider.currentUser.blockingGet().getCredentials()
            )
            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversationModel.token)

            val message = chatRepository.getMessage(messageId, bundle)
            emit(message.first())
        }

    fun setPlayBack(speed: PlaybackSpeed) {
        mediaPlayerManager.setPlayBackSpeed(speed)
        CoroutineScope(Dispatchers.Default).launch {
            _voiceMessagePlayBackUIFlow.emit(speed)
        }
    }

    fun startMediaPlayer(path: String) {
        audioRequest(true) {
            mediaPlayerManager.start(path)
        }
    }

    fun startCyclingMediaPlayer() = audioRequest(true, mediaPlayerManager::startCycling)

    fun pauseMediaPlayer(notifyUI: Boolean) {
        audioRequest(false) {
            mediaPlayerManager.pause(notifyUI)
        }
    }

    fun seekToMediaPlayer(progress: Int) = mediaPlayerManager.seekTo(progress)

    fun stopMediaPlayer() = audioRequest(false, mediaPlayerManager::stop)

    fun queueInMediaPlayer(path: String, msg: ChatMessage) = mediaPlayerManager.addToPlayList(path, msg)

    fun clearMediaPlayerQueue() = mediaPlayerManager.clearPlayList()

    inner class JoinRoomObserver : Observer<ConversationModel> {
        override fun onSubscribe(d: Disposable) {
            disposableSet.add(d)
        }

        override fun onNext(conversationModel: ConversationModel) {
            _joinRoomViewState.value = JoinRoomSuccessState(conversationModel)
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when joining room")
            _joinRoomViewState.value = JoinRoomErrorState
        }

        override fun onComplete() {
            // unused atm
        }
    }

    inner class SetReminderObserver : Observer<Reminder> {
        override fun onSubscribe(d: Disposable) {
            disposableSet.add(d)
        }

        override fun onNext(reminder: Reminder) {
            Log.d(TAG, "reminder set successfully")
        }

        override fun onError(e: Throwable) {
            Log.e(TAG, "Error when sending reminder, $e")
        }

        override fun onComplete() {
            // unused atm
        }
    }

    inner class GetReminderObserver : Observer<Reminder> {
        override fun onSubscribe(d: Disposable) {
            disposableSet.add(d)
        }

        override fun onNext(reminder: Reminder) {
            _getReminderExistState.value = GetReminderExistState(reminder)
        }

        override fun onError(e: Throwable) {
            Log.d(TAG, "Error when getting reminder $e")
            _getReminderExistState.value = GetReminderStartState
        }

        override fun onComplete() {
            // unused atm
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun outOfOfficeStatusOfUser(credentials: String, baseUrl: String, userId: String) {
        viewModelScope.launch {
            try {
                val response = chatNetworkDataSource.getOutOfOfficeStatusForUser(credentials, baseUrl, userId)
                _outOfOfficeViewState.value = OutOfOfficeUIState.Success(response.ocs?.data!!)
            } catch (exception: Exception) {
                _outOfOfficeViewState.value = OutOfOfficeUIState.Error(exception)
            }
        }
    }

    fun deleteTempMessage(chatMessage: ChatMessage) {
        viewModelScope.launch {
            chatRepository.deleteTempMessage(chatMessage)
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun unbindRoom(credentials: String, baseUrl: String, roomToken: String) {
        viewModelScope.launch {
            try {
                val response = chatNetworkDataSource.unbindRoom(credentials, baseUrl, roomToken)
                _unbindRoomResult.value = UnbindRoomUiState.Success(response.ocs?.meta?.statusCode!!)
            } catch (exception: Exception) {
                _unbindRoomResult.value = UnbindRoomUiState.Error(exception.message.toString())
            }
        }
    }

    fun resendMessage(credentials: String, urlForChat: String, message: ChatMessage) {
        viewModelScope.launch {
            chatRepository.resendChatMessage(
                credentials,
                urlForChat,
                message.message.orEmpty(),
                message.actorDisplayName.orEmpty(),
                message.parentMessageId?.toIntOrZero() ?: 0,
                false,
                message.referenceId.orEmpty()
            ).collect { result ->
                if (result.isSuccess) {
                    Log.d(TAG, "resend successful")
                } else {
                    Log.e(TAG, "resend failed")
                }
            }
        }
    }

    fun getContextForChatMessages(credentials: String, baseUrl: String, token: String, messageId: String, limit: Int) {
        viewModelScope.launch {
            val messages = chatNetworkDataSource.getContextForChatMessage(
                credentials,
                baseUrl,
                token,
                messageId,
                limit
            )

            _getContextChatMessages.value = messages
        }
    }

    fun getOpenGraph(credentials: String, baseUrl: String, urlToPreview: String) {
        viewModelScope.launch {
            _getOpenGraph.value = chatNetworkDataSource.getOpenGraph(credentials, baseUrl, urlToPreview)
        }
    }

    suspend fun updateMessageDraft() {
        val model = conversationRepository.getLocallyStoredConversation(chatRoomToken)
        model?.messageDraft?.let {
            messageDraft = it
        }
    }

    fun clearThreadTitle() {
        messageDraft.threadTitle = ""
        saveMessageDraft()
    }

    companion object {
        private val TAG = ChatViewModel::class.simpleName
        const val JOIN_ROOM_RETRY_COUNT: Long = 3
        const val HTTP_CODE_OK: Int = 200
    }

    sealed class OutOfOfficeUIState {
        data object None : OutOfOfficeUIState()
        data class Success(val userAbsence: UserAbsenceData) : OutOfOfficeUIState()
        data class Error(val exception: Exception) : OutOfOfficeUIState()
    }

    sealed class UnbindRoomUiState {
        data object None : UnbindRoomUiState()
        data class Success(val statusCode: Int) : UnbindRoomUiState()
        data class Error(val message: String) : UnbindRoomUiState()
    }

    sealed class ThreadCreationUiState {
        data object None : ThreadCreationUiState()
        data class Success(val thread: ThreadInfo?) : ThreadCreationUiState()
        data class Error(val exception: Exception) : ThreadCreationUiState()
    }

    sealed class ThreadRetrieveUiState {
        data object None : ThreadRetrieveUiState()
        data class Success(val thread: ThreadInfo?) : ThreadRetrieveUiState()
        data class Error(val exception: Exception) : ThreadRetrieveUiState()
    }
}
