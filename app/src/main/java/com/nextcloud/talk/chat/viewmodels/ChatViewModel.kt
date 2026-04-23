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
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager
import com.nextcloud.talk.chat.data.ChatMessageRepository
import com.nextcloud.talk.chat.data.io.AudioFocusRequestManager
import com.nextcloud.talk.chat.data.io.MediaPlayerManager
import com.nextcloud.talk.chat.data.io.MediaRecorderManager
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.data.network.ChatNetworkDataSource
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.chat.ui.model.toUiModel
import com.nextcloud.talk.chat.viewmodels.ChatViewModel.Companion.POST_UPLOAD_FETCH_MAX_ATTEMPTS
import com.nextcloud.talk.chat.viewmodels.ChatViewModel.Companion.POST_UPLOAD_FETCH_RETRY_DELAY_MS
import com.nextcloud.talk.conversationlist.DirectShareHelper
import com.nextcloud.talk.conversationlist.data.OfflineConversationsRepository
import com.nextcloud.talk.conversationlist.data.network.OfflineFirstConversationsRepository
import com.nextcloud.talk.conversationlist.viewmodels.ConversationsListViewModel.Companion.FOLLOWED_THREADS_EXIST
import com.nextcloud.talk.data.database.mappers.toDomainModel
import com.nextcloud.talk.data.database.model.ChatMessageEntity
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.toIntOrZero
import com.nextcloud.talk.jobs.ShareOperationWorker
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import com.nextcloud.talk.messagesearch.MessageSearchHelper
import com.nextcloud.talk.models.MessageDraft
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.ReactionAddedModel
import com.nextcloud.talk.models.domain.ReactionDeletedModel
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.opengraph.OpenGraphObject
import com.nextcloud.talk.models.json.reminder.Reminder
import com.nextcloud.talk.models.json.threads.ThreadInfo
import com.nextcloud.talk.models.json.upcomingEvents.UpcomingEvent
import com.nextcloud.talk.models.json.userAbsence.UserAbsenceData
import com.nextcloud.talk.repositories.reactions.ReactionsRepository
import com.nextcloud.talk.repositories.unifiedsearch.UnifiedSearchRepository
import com.nextcloud.talk.threadsoverview.data.ThreadsRepository
import com.nextcloud.talk.ui.PlaybackSpeed
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.UserIdUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.CurrentUserProvider
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongParameterList")
class ChatViewModel @AssistedInject constructor(
    // should be removed here. Use it via RetrofitChatNetwork
    private val appPreferences: AppPreferences,
    private val chatNetworkDataSource: ChatNetworkDataSource,
    private val chatRepository: ChatMessageRepository,
    private val threadsRepository: ThreadsRepository,
    private val conversationRepository: OfflineConversationsRepository,
    private val reactionsRepository: ReactionsRepository,
    private val unifiedSearchRepository: UnifiedSearchRepository,
    private val mediaRecorderManager: MediaRecorderManager,
    private val audioFocusRequestManager: AudioFocusRequestManager,
    private val currentUserProvider: CurrentUserProvider,
    @Assisted private val chatRoomToken: String,
    @Assisted private val conversationThreadId: Long?
) : ViewModel(),
    DefaultLifecycleObserver {

    @Inject
    lateinit var arbitraryStorageManager: ArbitraryStorageManager

    enum class LifeCycleFlag {
        PAUSED,
        RESUMED,
        STOPPED
    }

    enum class ChatMode {
        // Used to observe new messages. No search features are available in this mode
        DEFAULT_MODE,

        // Used when using the search feature inside the chat. Search controls are shown
        SEARCH_MODE,

        // Might be used when clicking a quoted message or choosing a message from the message search in conversation
        // list (only if messages is not contained in latest chat block). Search controls are not shown
        OLD_CHATBLOCK_MODE
    }

    enum class LoadMoreDirection {
        OLDER,
        NEWER
    }

    data class SearchUiState(
        val query: String = "",
        val results: List<SearchMessageEntry> = emptyList(),
        val selectedIndex: Int = -1,
        val isLoading: Boolean = false,
        val hasMore: Boolean = false,
        val error: Boolean = false
    ) {
        val selectedResult: SearchMessageEntry?
            get() = results.getOrNull(selectedIndex)
    }

    @Deprecated("use currentUserFlow")
    lateinit var currentUser: User

    private var messageSearchHelper: MessageSearchHelper? = null
    private var searchRequestJob: Job? = null
    private val contextAnchorMessageId = MutableStateFlow<Long?>(null)

    private val _chatMode = MutableStateFlow(ChatMode.DEFAULT_MODE)
    val chatMode: StateFlow<ChatMode> = _chatMode

    private val _searchUiState = MutableStateFlow(SearchUiState())
    val searchUiState: StateFlow<SearchUiState> = _searchUiState

    private val _noMoreSearchResults = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val noMoreSearchResults: MutableSharedFlow<Unit> = _noMoreSearchResults

    private var localLastReadMessage: Int = 0

    private var showUnreadMessagesMarker: Boolean = true
    private var isLoadMoreInProgress = false

    private val mediaPlayerManager: MediaPlayerManager = MediaPlayerManager.sharedInstance(appPreferences)
    lateinit var currentLifeCycleFlag: LifeCycleFlag
    val disposableSet = mutableSetOf<Disposable>()
    var mediaPlayerDuration = mediaPlayerManager.mediaPlayerDuration
    val mediaPlayerPosition = mediaPlayerManager.mediaPlayerPosition

    var messageDraft: MessageDraft = MessageDraft()
    var hiddenUpcomingEvent: String? = null
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
        searchRequestJob?.cancel()
        messageSearchHelper?.cancelSearch()
        mediaRecorderManager.handleOnPause()
        chatRepository.handleOnPause()
        mediaPlayerManager.handleOnPause()

        saveMessageDraft()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        currentLifeCycleFlag = LifeCycleFlag.STOPPED
        mediaRecorderManager.handleOnStop()
        chatRepository.handleOnStop()
        mediaPlayerManager.handleOnStop()
    }

    fun onSignalingChatMessageReceived(chatMessages: List<ChatMessageJson>) {
        viewModelScope.launch {
            chatRepository.onSignalingChatMessageReceived(chatMessages)
        }
    }

    fun setUnreadMessagesMarker(shouldShow: Boolean) {
        showUnreadMessagesMarker = shouldShow
    }

    val backgroundPlayUIFlow = mediaPlayerManager.backgroundPlayUIFlow

    val mediaPlayerSeekbarObserver: Flow<ChatMessage>
        get() = mediaPlayerManager.mediaPlayerSeekBarPositionMsg

    // FIXME - map this to string id or some other kinda of id idk
    val currentlyPlayedMessageId: Flow<Int?>
        get() = mediaPlayerManager.currentCycledMessage.map { msg -> msg?.jsonMessageId }

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

    private val _upcomingEventViewState = MutableLiveData<UpcomingEventUIState>(UpcomingEventUIState.None)
    val upcomingEventViewState: LiveData<UpcomingEventUIState>
        get() = _upcomingEventViewState

    private val _unbindRoomResult = MutableLiveData<UnbindRoomUiState>(UnbindRoomUiState.None)
    val unbindRoomResult: LiveData<UnbindRoomUiState>
        get() = _unbindRoomResult

    private val _voiceMessagePlaybackSpeedPreferences: MutableLiveData<Map<String, PlaybackSpeed>> = MutableLiveData()
    val voiceMessagePlaybackSpeedPreferences: LiveData<Map<String, PlaybackSpeed>>
        get() = _voiceMessagePlaybackSpeedPreferences

    private val _threadRetrieveState = MutableStateFlow<ThreadRetrieveUiState>(ThreadRetrieveUiState.None)
    val threadRetrieveState: StateFlow<ThreadRetrieveUiState> = _threadRetrieveState

    private val _reactionsSheetMessageId = MutableStateFlow<Long?>(null)
    val reactionsSheetMessageId: StateFlow<Long?> = _reactionsSheetMessageId

    fun showReactionsSheet(messageId: Long) {
        _reactionsSheetMessageId.value = messageId
    }

    fun dismissReactionsSheet() {
        _reactionsSheetMessageId.value = null
    }

    private val _profileSheetMessageId = MutableStateFlow<Long?>(null)
    val profileSheetMessageId: StateFlow<Long?> = _profileSheetMessageId

    fun showProfileSheet(messageId: Long) {
        _profileSheetMessageId.value = messageId
    }

    fun dismissProfileSheet() {
        _profileSheetMessageId.value = null
    }

    val getLastCommonReadFlow = chatRepository.lastCommonReadFlow

    sealed interface ViewState

    object GetReminderStartState : ViewState
    open class GetReminderExistState(val reminder: Reminder) : ViewState
    object GetReminderStateSet : ViewState

    private val _getReminderExistState: MutableLiveData<ViewState> = MutableLiveData(GetReminderStartState)

    val getReminderExistState: LiveData<ViewState>
        get() = _getReminderExistState

    object GetCapabilitiesStartState : ViewState
    object GetCapabilitiesErrorState : ViewState
    open class GetCapabilitiesInitialLoadState(
        val spreedCapabilities: SpreedCapability,
        val conversationModel: ConversationModel
    ) : ViewState
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

    object ScheduledMessagesIdleState : ViewState
    object ScheduledMessagesLoadingState : ViewState
    data class ScheduledMessagesSuccessState(val messages: List<ChatMessage>) : ViewState
    object ScheduledMessagesErrorState : ViewState

    private val _scheduledMessagesViewState: MutableLiveData<ViewState> = MutableLiveData(ScheduledMessagesIdleState)
    val scheduledMessagesViewState: LiveData<ViewState>
        get() = _scheduledMessagesViewState

    private val _scheduledMessagesCount = MutableLiveData<Int>()
    val scheduledMessagesCount: LiveData<Int> = _scheduledMessagesCount

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

    @Volatile private var firstUnreadMessageId: Int? = null

    @Volatile private var oneOrMoreMessagesWereSent = false

    private val expandedSystemMessageParents = MutableStateFlow<Set<Int>>(emptySet())

    fun toggleSystemMessageCollapse(parentMessageId: Int) {
        expandedSystemMessageParents.update { current ->
            if (parentMessageId in current) current - parentMessageId else current + parentMessageId
        }
    }

    // ------------------------------
    // UI State. This should be the only UI state. Add more val here and update via copy whenever necessary.
    // ------------------------------
    data class ChatUiState(
        val items: List<ChatItem> = emptyList(),
        val isOneToOneConversation: Boolean = false,

        // Adding the whole conversation is just an intermediate solution as it is used in the activity.
        // For the future, only necessary vars from conversation should be in the ui state
        val conversation: ConversationModel? = null,
        val pinnedMessage: ChatMessage? = null,
        val highlightedMessageId: Int? = null,
        val highlightedSearchTerm: String? = null,
        val highlightTriggerNonce: Long? = null
    )

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    // ------------------------------
    // Current user flows
    // ------------------------------
    private val currentUserFlow: StateFlow<User?> =
        currentUserProvider.currentUserFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val nonNullUserFlow = currentUserFlow.filterNotNull()

    private val conversationFlow: Flow<ConversationModel> =
        nonNullUserFlow
            .flatMapLatest { user ->
                val userId = requireNotNull(user.id)
                conversationRepository.observeConversation(userId, chatRoomToken)
            }
            .mapNotNull { result ->
                when (result) {
                    is OfflineFirstConversationsRepository.ConversationResult.Found ->
                        result.conversation

                    OfflineFirstConversationsRepository.ConversationResult.NotFound ->
                        null
                }
            }
            .distinctUntilChangedBy { it.lastReadMessage }
            .onEach {
                println("Conversation changed: lastRead=${it.lastReadMessage}")
            }

    private val conversationAndUserFlow =
        combine(conversationFlow, nonNullUserFlow) { c, u -> c to u }
            .shareIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(CONVERSATION_AND_USER_FLOW_SHARING_TIMEOUT_MS),
                replay = 1
            )

    // ------------------------------
    // Messages
    // ------------------------------
    private fun Flow<List<ChatMessageEntity>>.mapToChatMessages(userId: String): Flow<List<ChatMessage>> =
        map { entities ->
            entities.map { entity ->
                entity.toDomainModel().apply {
                    avatarUrl = getAvatarUrl(this)
                    incoming = actorId != userId
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val messagesFlow: Flow<List<ChatMessage>> =
        conversationAndUserFlow
            .flatMapLatest { (conversation, user) ->
                combine(chatMode, contextAnchorMessageId) { mode, anchorMessageId ->
                    mode to anchorMessageId
                }
                    .flatMapLatest { (mode, anchorMessageId) ->
                        val messagesSource = if (mode == ChatMode.DEFAULT_MODE) {
                            chatRepository.observeLatestMessages(conversation.internalId)
                        } else if (anchorMessageId != null) {
                            chatRepository.observeMessagesForAnchor(
                                internalConversationId = conversation.internalId,
                                anchorMessageId = anchorMessageId
                            )
                        } else {
                            chatRepository.observeLatestMessages(conversation.internalId)
                        }
                        messagesSource
                            .distinctUntilChanged()
                            .mapToChatMessages(user.userId!!)
                    }
            }
            .map { messages ->
                messages.let(::handleSystemMessages)
                    .let(::handleThreadMessages)
            }
    // .distinctUntilChangedBy { it.map { msg -> msg.jsonMessageId } }

    private val trackedParentIds = MutableStateFlow<Set<Long>>(emptySet())

    private val parentMessagesFlow: Flow<Map<Long, ChatMessage>> =
        trackedParentIds
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    chatRepository.observeParentMessages(ids.toList())
                        .map { messages -> messages.associateBy { it.jsonMessageId.toLong() } }
                }
            }

    // ------------------------------
    // Last read message cache
    // ------------------------------
    private var lastReadMessage: Int = 0

    // ------------------------------
    // Initialization
    // ------------------------------
    init {
        observeConversation()
        observeMessages()
        observeMediaPlayerProgressForCompose()
        observePinnedMessage()
        observeRoomRefresh()
        observeIncomingMessages()
    }

    fun enterSearchMode() {
        _chatMode.value = ChatMode.SEARCH_MODE
    }

    fun switchToDefaultMode() {
        resetUnreadMarkerCache()
        contextAnchorMessageId.value = null
        _chatMode.value = ChatMode.DEFAULT_MODE
        _uiState.update { it.copy(highlightedMessageId = null, highlightedSearchTerm = null) }
    }

    fun exitSearchMode() {
        searchRequestJob?.cancel()
        messageSearchHelper?.cancelSearch()
        _searchUiState.value = SearchUiState()
        switchToDefaultMode()
    }

    fun onSearchQueryChanged(query: String) {
        _searchUiState.update { it.copy(query = query, error = false) }

        if (query.length < MIN_CHARS_FOR_SEARCH) {
            searchRequestJob?.cancel()
            messageSearchHelper?.cancelSearch()
            _searchUiState.update {
                it.copy(
                    results = emptyList(),
                    selectedIndex = -1,
                    isLoading = false,
                    hasMore = false,
                    error = false
                )
            }
            return
        }

        val helper = ensureSearchHelper()
        if (helper == null) {
            return
        }
        helper.cancelSearch()
        searchRequestJob?.cancel()
        _searchUiState.update { it.copy(isLoading = true, error = false) }

        searchRequestJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    helper.startMessageSearch(query)
                }
                if (_searchUiState.value.query != query) {
                    return@launch
                }

                val navigableResults = result.messages.filter { it.messageId?.toLongOrNull() != null }
                _searchUiState.update {
                    it.copy(
                        results = navigableResults,
                        selectedIndex = if (navigableResults.isNotEmpty()) 0 else -1,
                        isLoading = false,
                        hasMore = result.hasMore,
                        error = false
                    )
                }
                if (navigableResults.isNotEmpty()) {
                    jumpToSearchSelection()
                }
            } catch (_: CancellationException) {
                // Ignore cancellation; request was superseded by a newer one.
            } catch (@Suppress("Detekt.TooGenericExceptionCaught") throwable: Throwable) {
                _searchUiState.update { state -> state.copy(isLoading = false, error = true) }
            }
        }
    }

    fun selectNextSearchResult() {
        val state = _searchUiState.value
        if (state.results.isEmpty()) return

        if (state.selectedIndex >= state.results.lastIndex) {
            if (state.hasMore) {
                loadMoreSearchResultsAndSelectNext()
            } else {
                _noMoreSearchResults.tryEmit(Unit)
            }
            return
        }

        val nextIndex = (state.selectedIndex + 1).coerceAtMost(state.results.lastIndex)
        _searchUiState.update { it.copy(selectedIndex = nextIndex) }
        jumpToSearchSelection()
    }

    private fun loadMoreSearchResultsAndSelectNext() {
        val helper = ensureSearchHelper()
        val previousSize = _searchUiState.value.results.size

        if (helper == null || _searchUiState.value.isLoading) {
            return
        }
        val queryAtStart = _searchUiState.value.query

        _searchUiState.update { it.copy(isLoading = true, error = false) }

        searchRequestJob?.cancel()
        searchRequestJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    helper.loadMore()
                }
                if (_searchUiState.value.query != queryAtStart) {
                    return@launch
                }

                if (result == null) {
                    _searchUiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                val navigableResults = result.messages.filter { it.messageId?.toLongOrNull() != null }
                val nextIndex = if (navigableResults.size > previousSize) {
                    previousSize
                } else {
                    _searchUiState.value.selectedIndex.coerceAtMost(navigableResults.lastIndex)
                }
                _searchUiState.update {
                    it.copy(
                        results = navigableResults,
                        selectedIndex = nextIndex,
                        isLoading = false,
                        hasMore = result.hasMore,
                        error = false
                    )
                }
                if (navigableResults.isNotEmpty()) {
                    jumpToSearchSelection()
                }
                if (navigableResults.size <= previousSize) {
                    _noMoreSearchResults.tryEmit(Unit)
                }
            } catch (_: CancellationException) {
                // Ignore cancellation; request was superseded.
            } catch (@Suppress("Detekt.TooGenericExceptionCaught") throwable: Throwable) {
                _searchUiState.update { state -> state.copy(isLoading = false, error = true) }
            }
        }
    }

    fun selectPreviousSearchResult() {
        val state = _searchUiState.value
        if (state.results.isEmpty()) return
        val oldIndex = state.selectedIndex
        val previousIndex = (state.selectedIndex - 1).coerceAtLeast(0)
        if (previousIndex == oldIndex) {
            _noMoreSearchResults.tryEmit(Unit)
        }
        _searchUiState.update { it.copy(selectedIndex = previousIndex) }
        jumpToSearchSelection()
    }

    fun jumpToSearchSelection() {
        val result = _searchUiState.value.selectedResult ?: return
        val messageId = result.messageId?.toLongOrNull() ?: return
        val targetThreadId = result.threadId?.toLongOrNull()?.takeIf { result.isThreadReplyResult() }

        viewModelScope.launch {
            val foundLocally = _uiState.value.items.any {
                (it as? ChatItem.MessageItem)?.uiMessage?.id == messageId.toInt()
            }
            if (!foundLocally) {
                loadMessageContextAndSwitchMode(
                    messageId = messageId,
                    targetThreadId = targetThreadId,
                    mode = ChatMode.SEARCH_MODE
                )
            }
            triggerMessageHighlight(messageId.toInt(), _searchUiState.value.query)
        }
    }

    fun jumpToQuotedMessage(messageId: Long) {
        viewModelScope.launch {
            val foundLocally = _uiState.value.items.any {
                (it as? ChatItem.MessageItem)?.uiMessage?.id == messageId.toInt()
            }
            if (!foundLocally) {
                loadMessageContextAndSwitchMode(
                    messageId = messageId,
                    targetThreadId = conversationThreadId,
                    mode = ChatMode.OLD_CHATBLOCK_MODE
                )
            }
            triggerMessageHighlight(messageId.toInt())
        }
    }

    suspend fun openMessageFromGlobalSearch(messageId: Long, threadId: Long?, searchQuery: String?) {
        loadMessageContextAndSwitchMode(
            messageId = messageId,
            targetThreadId = threadId,
            mode = ChatMode.OLD_CHATBLOCK_MODE
        )
        triggerMessageHighlight(messageId.toInt(), searchQuery)
    }

    private suspend fun loadMessageContextAndSwitchMode(messageId: Long, targetThreadId: Long?, mode: ChatMode) {
        val result = chatRepository.loadMessageContext(
            messageId = messageId,
            limit = CONTEXT_MESSAGES_LIMIT,
            threadId = targetThreadId
        )

        result.let {
            resetUnreadMarkerCache()
            contextAnchorMessageId.value = messageId
            _chatMode.value = mode
        }
    }

    private fun ensureSearchHelper(): MessageSearchHelper? {
        val user = currentUserFlow.value
        val helper = messageSearchHelper ?: user?.let {
            MessageSearchHelper(
                unifiedSearchRepository = unifiedSearchRepository,
                currentUser = it,
                fromRoom = chatRoomToken
            )
        }?.also { createdHelper ->
            messageSearchHelper = createdHelper
        }
        return helper
    }

    private fun triggerMessageHighlight(messageId: Int, searchTerm: String? = null) {
        _uiState.update { state ->
            state.copy(
                highlightedMessageId = messageId,
                highlightedSearchTerm = searchTerm?.trim()?.takeIf { it.isNotEmpty() },
                highlightTriggerNonce = System.nanoTime()
            )
        }
    }

    private fun resetUnreadMarkerCache() {
        firstUnreadMessageId = null
    }

    private fun observeMediaPlayerProgressForCompose() {
        mediaPlayerSeekbarObserver
            .onEach { message ->
                syncVoiceMessageUiState(message)
            }
            .launchIn(viewModelScope)
    }

    fun pauseVoiceMessageUiState(messageId: Int) {
        _uiState.update { current ->
            val updatedItems = current.items.map { item ->
                if (item is ChatItem.MessageItem && item.uiMessage.id == messageId) {
                    val voiceContent = item.uiMessage.content as? MessageTypeContent.Voice
                    if (voiceContent != null) {
                        item.copy(uiMessage = item.uiMessage.copy(content = voiceContent.copy(isPlaying = false)))
                    } else {
                        item
                    }
                } else {
                    item
                }
            }
            current.copy(items = updatedItems)
        }
    }

    fun setVoiceMessageSpeed(messageId: Int, speed: PlaybackSpeed) {
        _uiState.update { current ->
            val updatedItems = current.items.map { item ->
                if (item is ChatItem.MessageItem && item.uiMessage.id == messageId) {
                    val voiceContent = item.uiMessage.content as? MessageTypeContent.Voice
                    if (voiceContent != null) {
                        item.copy(uiMessage = item.uiMessage.copy(content = voiceContent.copy(playbackSpeed = speed)))
                    } else {
                        item
                    }
                } else {
                    item
                }
            }
            current.copy(items = updatedItems)
        }
    }

    fun syncVoiceMessageUiState(message: ChatMessage) {
        _uiState.update { current ->
            val updatedItems = current.items.map { item ->
                if (item is ChatItem.MessageItem && item.uiMessage.id == message.jsonMessageId) {
                    val voiceContent = item.uiMessage.content as? MessageTypeContent.Voice
                    if (voiceContent != null) {
                        val updatedVoiceContent = voiceContent.copy(
                            actorId = message.actorId,
                            isPlaying = message.isPlayingVoiceMessage,
                            wasPlayed = message.wasPlayedVoiceMessage,
                            isDownloading = message.isDownloadingVoiceMessage,
                            durationSeconds = message.voiceMessageDuration,
                            playedSeconds = message.voiceMessagePlayedSeconds,
                            seekbarProgress = message.voiceMessageSeekbarProgress,
                            waveform = message.voiceMessageFloatArray?.toList() ?: voiceContent.waveform
                            // playbackSpeed is preserved from existing voiceContent
                        )
                        item.copy(uiMessage = item.uiMessage.copy(content = updatedVoiceContent))
                    } else {
                        item
                    }
                } else {
                    item
                }
            }

            current.copy(items = updatedItems)
        }
    }

    // ------------------------------
    // Observe conversation
    // ------------------------------
    private fun observeConversation() {
        conversationFlow
            .onEach { conversation ->
                lastReadMessage = conversation.lastReadMessage

                _uiState.update { current ->
                    current.copy(
                        conversation = conversation,
                        isOneToOneConversation = !conversation.isOneToOneConversation()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observePinnedMessage() {
        nonNullUserFlow
            .flatMapLatest { user ->
                conversationRepository.observeConversation(requireNotNull(user.id), chatRoomToken)
                    .mapNotNull { result ->
                        (result as? OfflineFirstConversationsRepository.ConversationResult.Found)?.conversation
                    }
                    .distinctUntilChangedBy { it.lastPinnedId to it.hiddenPinnedId }
                    .flatMapLatest { conversation ->
                        val pinnedId = conversation.lastPinnedId
                        if (pinnedId != null && pinnedId != 0L && pinnedId != conversation.hiddenPinnedId) {
                            val bundle = Bundle().apply {
                                putString(
                                    BundleKeys.KEY_CHAT_URL,
                                    ApiUtils.getUrlForChat(1, user.baseUrl, chatRoomToken)
                                )
                                putString(
                                    BundleKeys.KEY_CREDENTIALS,
                                    ApiUtils.getCredentials(user.username, user.token)
                                )
                                putString(BundleKeys.KEY_ROOM_TOKEN, chatRoomToken)
                            }
                            chatRepository.getMessage(pinnedId, bundle)
                                .map { it as ChatMessage? }
                                .catch { emit(null) }
                        } else {
                            flowOf(null)
                        }
                    }
            }
            .onEach { pinnedMessage ->
                _uiState.update { it.copy(pinnedMessage = pinnedMessage) }
            }
            .catch { Log.e(TAG, "Error observing pinned message", it) }
            .launchIn(viewModelScope)
    }

    private fun observeRoomRefresh() {
        chatRepository.roomRefreshFlow
            .debounce(ROOM_REFRESH_DEBOUNCE_MS)
            .onEach { getRoom(chatRoomToken) }
            .launchIn(viewModelScope)
    }

    private fun observeIncomingMessages() {
        chatRepository.incomingMessageFlow
            .onEach {
                val (conversation, user) = conversationAndUserFlow.first()
                val context = NextcloudTalkApplication.sharedApplication!!
                val isOneToOne = conversation.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
                DirectShareHelper.reportIncomingMessage(
                    context,
                    user,
                    conversation.token,
                    conversation.displayName ?: conversation.token,
                    isOneToOne
                )
            }
            .launchIn(viewModelScope)
    }

    private data class CombinedInput(
        val messages: List<ChatMessage>,
        val lastCommonRead: Int,
        val parentMap: Map<Long, ChatMessage>,
        val conversationLastRead: Int,
        val expandedParents: Set<Int> = emptySet()
    )

    private data class ProcessedMessages(val items: List<ChatItem>, val missingParentIds: List<Long>)

    private fun observeMessages() {
        // conversationFlow provides the user's own lastReadMessage for the unread marker.
        // getLastCommonReadFlow provides the "last read by all" value for read-receipt checkmarks.
        // These are two different concepts that must not be conflated.
        combine(
            messagesFlow,
            getLastCommonReadFlow.onStart { emit(0) },
            parentMessagesFlow,
            conversationFlow.map { it.lastReadMessage },
            expandedSystemMessageParents
        ) { messages, lastCommonRead, parentMap, conversationLastRead, expandedParents ->
            CombinedInput(messages, lastCommonRead, parentMap, conversationLastRead, expandedParents)
        }
            .map { (messages, lastCommonRead, parentMap, conversationLastRead, expandedParents) ->
                val messageMap: Map<Long, ChatMessage> = messages.associateBy { it.jsonMessageId.toLong() }
                val combinedMap: Map<Long, ChatMessage> = messageMap + parentMap

                val parentIds: List<Long> = messages.mapNotNull { it.parentMessageId }
                val missingParentIds: List<Long> =
                    parentIds.filterNot { parentId -> combinedMap.containsKey(parentId) }
                        .distinct()

                val user = currentUserFlow.value
                applyMessageGrouping(messages)
                applySystemMessageGrouping(messages)
                val uiMessages = messages.map { message ->
                    val parent: ChatMessage? = combinedMap[message.parentMessageId]
                    message.toUiModel(
                        user = user ?: currentUser,
                        chatMessage = message,
                        lastCommonReadMessageId = lastCommonRead,
                        parentMessage = parent
                    )
                }

                val items = buildChatItems(uiMessages, conversationLastRead, expandedParents)
                ProcessedMessages(items = items, missingParentIds = missingParentIds)
            }
            .flowOn(Dispatchers.Default)
            .onEach { (items, missingParentIds) ->
                if (missingParentIds.isNotEmpty()) {
                    trackedParentIds.update { it + missingParentIds }
                    viewModelScope.launch {
                        val user = currentUserFlow.value ?: return@launch
                        chatRepository.fetchMissingParents(
                            "${user.id}@$chatRoomToken",
                            missingParentIds
                        )
                    }
                }

                _uiState.update { current ->
                    current.copy(items = items)
                }
            }
            .launchIn(viewModelScope)
    }

    // ------------------------------
    // Build chat items (pure)
    // ------------------------------
    private fun buildChatItems(
        uiMessages: List<ChatMessageUi>,
        lastReadMessage: Int,
        expandedParents: Set<Int> = emptySet()
    ): List<ChatItem> {
        var lastDate: LocalDate? = null
        var lastExpandableParentId: Int? = null

        Log.d(TAG, "buildChatItems...................")

        return buildList {
            if (firstUnreadMessageId == null && lastReadMessage > 0) {
                firstUnreadMessageId =
                    uiMessages.firstOrNull {
                        it.id > lastReadMessage
                    }?.id
                Log.d(TAG, "reversedMessages.size = ${uiMessages.size}")
                Log.d(TAG, "firstUnreadMessageId = $firstUnreadMessageId")
                Log.d(TAG, "conversation.lastReadMessage = $lastReadMessage")
            }

            for (uiMessage in uiMessages) {
                if (uiMessage.isExpandableParent) {
                    lastExpandableParentId = uiMessage.id
                }

                if (uiMessage.isHiddenByCollapse && lastExpandableParentId !in expandedParents) {
                    continue
                }

                val date = uiMessage.date

                if (date != lastDate) {
                    add(ChatItem.DateHeaderItem(date))
                    lastDate = date
                }

                if (!oneOrMoreMessagesWereSent && uiMessage.id == firstUnreadMessageId) {
                    add(ChatItem.UnreadMessagesMarkerItem(date))
                }

                val adjustedMessage = if (uiMessage.isExpandableParent) {
                    uiMessage.copy(isExpanded = uiMessage.id in expandedParents)
                } else {
                    uiMessage
                }
                add(ChatItem.MessageItem(adjustedMessage))
            }
        }.asReversed()
    }

    private fun applyMessageGrouping(messages: List<ChatMessage>) {
        messages.forEachIndexed { index, message ->
            message.isGrouped = index > 0 && shouldGroupMessage(message, messages[index - 1])
            message.isGroupedWithNext = index < messages.size - 1 && shouldGroupMessage(messages[index + 1], message)
        }
    }

    private fun applySystemMessageGrouping(messages: List<ChatMessage>) {
        messages.forEach { message ->
            message.expandableParent = false
            message.lastItemOfExpandableGroup = 0
            message.expandableChildrenAmount = 0
            message.hiddenByCollapse = false
        }

        var i = 0
        while (i < messages.size) {
            val msg = messages[i]
            if (!msg.isSystemMessage) {
                i++
                continue
            }
            var runEnd = i
            while (runEnd + 1 < messages.size) {
                val next = messages[runEnd + 1]
                if (next.isSystemMessage &&
                    next.systemMessageType == msg.systemMessageType &&
                    isSameDayMessages(messages[runEnd], next)
                ) {
                    runEnd++
                } else {
                    break
                }
            }
            if (runEnd > i) {
                val lastChildId = messages[runEnd].jsonMessageId
                msg.expandableParent = true
                msg.lastItemOfExpandableGroup = lastChildId
                msg.expandableChildrenAmount = runEnd - i
                for (j in i + 1..runEnd) {
                    messages[j].lastItemOfExpandableGroup = lastChildId
                }
            }
            i = runEnd + 1
        }

        messages.forEach { message ->
            if (isChildOfExpandableGroup(message)) {
                message.hiddenByCollapse = true
            }
        }
    }

    private fun isChildOfExpandableGroup(message: ChatMessage): Boolean =
        message.isSystemMessage && !message.expandableParent && message.lastItemOfExpandableGroup != 0

    private fun isSameDayMessages(message1: ChatMessage, message2: ChatMessage): Boolean {
        val date1 = Instant.ofEpochMilli(message1.timestamp * TIMESTAMP_TO_MILLIS)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val date2 = Instant.ofEpochMilli(message2.timestamp * TIMESTAMP_TO_MILLIS)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        return date1 == date2
    }

    private fun shouldGroupMessage(current: ChatMessage, previous: ChatMessage): Boolean {
        val sameMessageKind = current.isSystemMessage == previous.isSystemMessage
        val notUnclassifiedBot = current.actorType != "bots" || current.actorId == "changelog"
        val sameActor = current.isSystemMessage ||
            (current.actorType == previous.actorType && current.actorId == previous.actorId)
        val currentDate = Instant.ofEpochMilli(current.timestamp * TIMESTAMP_TO_MILLIS)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val previousDate = Instant.ofEpochMilli(previous.timestamp * TIMESTAMP_TO_MILLIS)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        val timeDifference = kotlin.math.abs(current.timestamp - previous.timestamp)
        val neitherEdited = (current.lastEditTimestamp ?: 0L) == 0L || (previous.lastEditTimestamp ?: 0L) == 0L

        return sameMessageKind &&
            notUnclassifiedBot &&
            sameActor &&
            currentDate == previousDate &&
            current.actorId == previous.actorId &&
            timeDifference <= GROUPING_TIME_WINDOW_SECONDS &&
            neitherEdited
    }

    fun onMessageSent() {
        oneOrMoreMessagesWereSent = true
    }

    fun observeConversationAndUserFirstTime() {
        conversationAndUserFlow
            .take(1)
            .onEach { (conversation, user) ->
                val credentials =
                    ApiUtils.getCredentials(user.username, user.token) ?: return@onEach

                val url =
                    ApiUtils.getUrlForChat(1, user.baseUrl, chatRoomToken)

                chatRepository.updateConversation(conversation)

                val isChatRelaySupported = withTimeoutOrNull(WEBSOCKET_CONNECT_TIMEOUT_MS) {
                    awaitChatRelaySupport(user)
                } ?: false

                loadInitialMessages(
                    withCredentials = credentials,
                    withUrl = url,
                    isChatRelaySupported = isChatRelaySupported
                )

                viewModelScope.launch {
                    startMessagePolling(isChatRelaySupported)
                }

                getCapabilities(user, chatRoomToken, conversation)
            }
            .launchIn(viewModelScope)
    }

    fun isChatRelaySupported(user: User): Boolean {
        val websocketInstance = WebSocketConnectionHelper.getWebSocketInstanceForUser(user)
        return websocketInstance?.supportsChatRelay() == true
    }

    private suspend fun awaitChatRelaySupport(user: User): Boolean {
        val wsInstance = WebSocketConnectionHelper.getWebSocketInstanceForUser(user) ?: return false
        while (!wsInstance.isConnected) {
            delay(WEBSOCKET_POLL_INTERVAL_MS)
        }
        return wsInstance.supportsChatRelay()
    }

    fun observeConversationAndUserEveryTime() {
        conversationAndUserFlow
            .onEach { (conversation, user) ->
                chatRepository.updateConversation(conversation)

                getCapabilities(user, chatRoomToken, conversation)

                advanceLocalLastReadMessageIfNeeded(
                    conversation.lastReadMessage
                )
            }
            .launchIn(viewModelScope)
    }

    private fun observeShareCompleted() {
        ShareOperationWorker.shareCompletedFlow
            .filter { it == chatRoomToken }
            .onEach {
                Log.d(TAG, "Share completed for room $chatRoomToken — fetching new messages immediately")
                fetchNewMessagesWithRetry()
            }
            .launchIn(viewModelScope)

        UploadAndShareFilesWorker.uploadCompletedFlow
            .filter { it == chatRoomToken }
            .onEach {
                Log.d(TAG, "Upload completed for room $chatRoomToken — fetching new messages immediately")
                UploadAndShareFilesWorker.clearUploadCompletedReplay()
                fetchNewMessagesWithRetry()
            }
            .launchIn(viewModelScope)
    }

    /**
     * Retries [ChatMessageRepository.fetchNewMessages] up to [POST_UPLOAD_FETCH_MAX_ATTEMPTS] times,
     * waiting [POST_UPLOAD_FETCH_RETRY_DELAY_MS] ms between attempts.  Stops as soon as at least one
     * new message is received so that the happy-path (server responds quickly) has no unnecessary
     * delay, while a slow server still gets a few extra chances before we fall back to the regular
     * insurance-request cycle.
     */
    private suspend fun fetchNewMessagesWithRetry() {
        repeat(POST_UPLOAD_FETCH_MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                Log.d(
                    TAG,
                    "fetchNewMessagesWithRetry: attempt ${attempt + 1}, " +
                        "waiting ${POST_UPLOAD_FETCH_RETRY_DELAY_MS}ms"
                )
                delay(POST_UPLOAD_FETCH_RETRY_DELAY_MS)
            }
            val gotMessages = chatRepository.fetchNewMessages()
            if (gotMessages) {
                Log.d(TAG, "fetchNewMessagesWithRetry: new messages received on attempt ${attempt + 1}")
                return
            }
        }
        Log.d(TAG, "fetchNewMessagesWithRetry: no new messages after $POST_UPLOAD_FETCH_MAX_ATTEMPTS attempts")
    }
    private fun handleSystemMessages(chatMessageList: List<ChatMessage>): List<ChatMessage> {
        fun shouldRemoveMessage(currentMessage: MutableMap.MutableEntry<Int, ChatMessage>): Boolean =
            isInfoMessageAboutDeletion(currentMessage) ||
                isReactionsMessage(currentMessage) ||
                isPollVotedMessage(currentMessage) ||
                isEditMessage(currentMessage) ||
                isThreadCreatedMessage(currentMessage)

        val chatMessageMap = chatMessageList.associateBy { it.jsonMessageId }.toMutableMap()
        val chatMessageIterator = chatMessageMap.iterator()

        while (chatMessageIterator.hasNext()) {
            val currentMessage = chatMessageIterator.next()

            if (shouldRemoveMessage(currentMessage)) {
                chatMessageIterator.remove()
            }
        }
        return chatMessageMap.values.toList()
    }

    private fun isInfoMessageAboutDeletion(currentMessage: MutableMap.MutableEntry<Int, ChatMessage>): Boolean =
        currentMessage.value.parentMessageId != null &&
            currentMessage.value.systemMessageType == ChatMessage
                .SystemMessageType.MESSAGE_DELETED

    private fun isReactionsMessage(currentMessage: MutableMap.MutableEntry<Int, ChatMessage>): Boolean =
        currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.REACTION ||
            currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.REACTION_DELETED ||
            currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.REACTION_REVOKED

    private fun isThreadCreatedMessage(currentMessage: MutableMap.MutableEntry<Int, ChatMessage>): Boolean =
        currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.THREAD_CREATED

    private fun isEditMessage(currentMessage: MutableMap.MutableEntry<Int, ChatMessage>): Boolean =
        currentMessage.value.parentMessageId != null &&
            currentMessage.value.systemMessageType == ChatMessage
                .SystemMessageType.MESSAGE_EDITED

    private fun isPollVotedMessage(currentMessage: MutableMap.MutableEntry<Int, ChatMessage>): Boolean =
        currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.POLL_VOTED

    private fun handleThreadMessages(chatMessageList: List<ChatMessage>): List<ChatMessage> {
        fun isThreadChildMessage(currentMessage: MutableMap.MutableEntry<Int, ChatMessage>): Boolean =
            currentMessage.value.isThread &&
                currentMessage.value.threadId?.toInt() != currentMessage.value.jsonMessageId

        val chatMessageMap = chatMessageList.associateBy { it.jsonMessageId }.toMutableMap()

        if (conversationThreadId == null) {
            val chatMessageIterator = chatMessageMap.iterator()
            while (chatMessageIterator.hasNext()) {
                val currentMessage = chatMessageIterator.next()

                if (isThreadChildMessage(currentMessage)) {
                    chatMessageIterator.remove()
                }
            }
        }

        return chatMessageMap.values.toList()
    }

    // val timeString = DateUtils.getLocalTimeStringFromTimestamp(message.timestamp)

    fun getAvatarUrl(message: ChatMessage): String =
        if (this::currentUser.isInitialized) {
            ApiUtils.getUrlForAvatar(
                currentUser.baseUrl,
                message.actorId,
                false
            )
        } else {
            ""
        }

    fun initData(user: User, credentials: String, urlForChatting: String, threadId: Long?) {
        currentUser = user

        chatRepository.initData(
            user,
            credentials,
            urlForChatting,
            chatRoomToken,
            threadId
        )

        observeConversationAndUserFirstTime()
        observeConversationAndUserEveryTime()
        observeShareCompleted()
    }

    fun ConversationModel?.isOneToOneConversation(): Boolean =
        this?.type ==
            ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL

    @Deprecated("use observeConversation")
    fun getRoom(token: String) {
        // _getRoomViewState.value = GetRoomStartState
        conversationRepository.getRoom(currentUser, token)
    }

    fun loadScheduledMessages(credentials: String, url: String) {
        _scheduledMessagesViewState.value = ScheduledMessagesLoadingState
        viewModelScope.launch {
            chatRepository.getScheduledChatMessages(credentials, url).collect { result ->
                if (result.isSuccess) {
                    _scheduledMessagesViewState.value =
                        ScheduledMessagesSuccessState(result.getOrNull().orEmpty())
                    _scheduledMessagesCount.value = result.getOrNull()?.size ?: 0
                } else {
                    _scheduledMessagesViewState.value = ScheduledMessagesErrorState
                }
            }
        }
    }

    fun getCapabilities(user: User, token: String, conversationModel: ConversationModel) {
        Log.d(TAG, "Remote server ${conversationModel.remoteServer}")
        if (conversationModel.remoteServer.isNullOrEmpty()) {
            if (_getCapabilitiesViewState.value == GetCapabilitiesStartState) {
                _getCapabilitiesViewState.value = GetCapabilitiesInitialLoadState(
                    user.capabilities!!.spreedCapability!!,
                    conversationModel
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
                            _getCapabilitiesViewState.value = GetCapabilitiesInitialLoadState(
                                spreedCapabilities,
                                conversationModel
                            )
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

    @Suppress("Detekt.TooGenericExceptionCaught", "MagicNumber")
    fun setThreadNotificationLevel(credentials: String, url: String, level: Int) {
        fun updateFollowedThreadsIndicator(notificationLevel: Int?) {
            when (notificationLevel) {
                1, 2 -> {
                    val accountId = UserIdUtils.getIdForUser(currentUser)
                    arbitraryStorageManager.storeStorageSetting(
                        accountId,
                        FOLLOWED_THREADS_EXIST,
                        true.toString(),
                        ""
                    )
                }
            }
        }

        viewModelScope.launch {
            try {
                val thread = threadsRepository.setThreadNotificationLevel(credentials, url, level)
                updateFollowedThreadsIndicator(thread.ocs?.data?.attendee?.notificationLevel)
                _threadRetrieveState.value = ThreadRetrieveUiState.Success(thread.ocs?.data)
            } catch (exception: Exception) {
                _threadRetrieveState.value = ThreadRetrieveUiState.Error(exception)
            }
        }
    }

    suspend fun loadInitialMessages(withCredentials: String, withUrl: String, isChatRelaySupported: Boolean) {
        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_CHAT_URL, withUrl)
        bundle.putString(BundleKeys.KEY_CREDENTIALS, withCredentials)
        chatRepository.loadInitialMessages(
            withNetworkParams = bundle,
            isChatRelaySupported = isChatRelaySupported
        )
    }

    suspend fun startMessagePolling(hasHighPerformanceBackend: Boolean) {
        chatRepository.startMessagePolling(hasHighPerformanceBackend)
    }

    fun loadMoreMessages(messageId: Int, direction: LoadMoreDirection) {
        Log.d(TAG, "Compose load more, messageId: $messageId direction: $direction")
        val user = currentUserFlow.value
        val urlForChatting = ApiUtils.getUrlForChat(
            1,
            user?.baseUrl,
            chatRoomToken
        )
        val credentials = ApiUtils.getCredentials(user?.username, user?.token)

        viewModelScope.launch {
            if (isLoadMoreInProgress) {
                return@launch
            }

            isLoadMoreInProgress = true
            val bundle = Bundle()
            bundle.putString(BundleKeys.KEY_CHAT_URL, urlForChatting)
            bundle.putString(BundleKeys.KEY_CREDENTIALS, credentials!!)
            try {
                val directionForRepository = when (direction) {
                    LoadMoreDirection.OLDER -> ChatMessageRepository.LoadMoreDirection.OLDER
                    LoadMoreDirection.NEWER -> ChatMessageRepository.LoadMoreDirection.NEWER
                }

                chatRepository.loadMoreMessages(
                    messageId.toLong(),
                    directionForRepository,
                    uiState.value.conversation!!.token,
                    LOAD_MORE_MESSAGES_LIMIT,
                    withNetworkParams = bundle
                )
            } finally {
                isLoadMoreInProgress = false
            }
        }
    }

    fun deleteChatMessages(credentials: String, url: String, messageId: Int) {
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

    fun advanceLocalLastReadMessageIfNeeded(messageId: Int) {
        if (localLastReadMessage < messageId) {
            localLastReadMessage = messageId
        }
    }

    /**
     * Please use with caution to not spam the server
     */
    fun updateRemoteLastReadMessageIfNeeded(credentials: String, url: String) {
        if (localLastReadMessage > _uiState.value.conversation!!.lastReadMessage) {
            setChatReadMessage(credentials, url, localLastReadMessage)
        }
    }

    /**
     * Please use with caution to not spam the server
     */
    fun setChatReadMessage(credentials: String, url: String, lastReadMessage: Int) {
        chatNetworkDataSource.setChatReadMarker(credentials, url, lastReadMessage)
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
                currentUser
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
        val credentials = ApiUtils.getCredentials(currentUser.username, currentUser.token)
        val url = ApiUtils.getUrlForMessageReaction(
            baseUrl = currentUser.baseUrl!!,
            roomToken = roomToken,
            messageId = chatMessage.jsonMessageId.toString()
        )

        viewModelScope.launch {
            try {
                val model = reactionsRepository.deleteReaction(
                    credentials,
                    currentUser.id!!,
                    url,
                    roomToken,
                    chatMessage,
                    emoji
                )
                if (model.success) {
                    _reactionDeletedViewState.value = ReactionDeletedSuccessState(model)
                }
            } catch (e: IOException) {
                Log.d(TAG, "deleteReaction I/O error: $e")
            } catch (e: HttpException) {
                Log.d(TAG, "deleteReaction HTTP error: $e")
            }
        }
    }

    fun addReaction(roomToken: String, chatMessage: ChatMessage, emoji: String) {
        val credentials = ApiUtils.getCredentials(currentUser.username, currentUser.token)
        val url = ApiUtils.getUrlForMessageReaction(
            baseUrl = currentUser.baseUrl!!,
            roomToken = roomToken,
            messageId = chatMessage.jsonMessageId.toString()
        )

        viewModelScope.launch {
            try {
                val model = reactionsRepository.addReaction(
                    credentials,
                    currentUser.id!!,
                    url,
                    roomToken,
                    chatMessage,
                    emoji
                )
                if (model.success) {
                    _reactionAddedViewState.value = ReactionAddedSuccessState(model)
                }
            } catch (e: IOException) {
                Log.d(TAG, "addReaction I/O error: $e")
            } catch (e: HttpException) {
                Log.d(TAG, "addReaction HTTP error: $e")
            }
        }
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

    // fun getMessageById(url: String, conversationModel: ConversationModel, messageId: Long): Flow<ChatMessage> =
    //     flow {
    //         val bundle = Bundle()
    //         bundle.putString(BundleKeys.KEY_CHAT_URL, url)
    //         bundle.putString(
    //             BundleKeys.KEY_CREDENTIALS,
    //             currentUser.getCredentials()
    //         )
    //         bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversationModel.token)
    //
    //         val message = chatRepository.getMessage(messageId, bundle)
    //         emit(message.first())
    //     }

    @Deprecated("use getMessageById(messageId: Long)")
    fun getMessageById(url: String, conversationModel: ConversationModel, messageId: Long): Flow<ChatMessage> {
        val bundle = Bundle().apply {
            putString(BundleKeys.KEY_CHAT_URL, url)
            putString(BundleKeys.KEY_CREDENTIALS, currentUser.getCredentials())
            putString(BundleKeys.KEY_ROOM_TOKEN, chatRoomToken)
        }

        return chatRepository.getMessage(messageId, bundle)
    }

    fun getMessageById(messageId: Long): Flow<ChatMessage> {
        val urlForChatting = ApiUtils.getUrlForChat(
            1, // Keep API v1 for local message lookup until version wiring is centralized.
            currentUser?.baseUrl,
            chatRoomToken
        )

        val bundle = Bundle().apply {
            putString(BundleKeys.KEY_CHAT_URL, urlForChatting)
            putString(BundleKeys.KEY_CREDENTIALS, currentUser.getCredentials())
            putString(BundleKeys.KEY_ROOM_TOKEN, chatRoomToken)
        }

        return chatRepository.getMessage(messageId, bundle)
    }

    // fun getIndividualMessageFromServer(
    //     credentials: String,
    //     baseUrl: String,
    //     token: String,
    //     messageId: String
    // ): Flow<ChatMessage?> =
    //     flow {
    //         val messages = chatNetworkDataSource.getContextForChatMessage(
    //             credentials = credentials,
    //             baseUrl = baseUrl,
    //             token = token,
    //             messageId = messageId,
    //             limit = 1,
    //             threadId = null
    //         )
    //
    //         if (messages.isNotEmpty()) {
    //             val message = messages[0]
    //             emit(message.toDomainModel())
    //         } else {
    //             emit(null)
    //         }
    //     }.flowOn(Dispatchers.IO)

    suspend fun getNumberOfThreadReplies(threadId: Long): Int = chatRepository.getNumberOfThreadReplies(threadId)

    fun setPlayBack(speed: PlaybackSpeed) {
        mediaPlayerManager.setPlayBackSpeed(speed)
        viewModelScope.launch {
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

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun fetchUpcomingEvent(credentials: String, baseUrl: String, roomToken: String) {
        viewModelScope.launch {
            updateHiddenUpcomingEvent()
            try {
                val response = chatNetworkDataSource.getUpcomingEvents(credentials, baseUrl, roomToken)
                val firstEvent = response.ocs?.data?.events?.firstOrNull()
                if (firstEvent != null) {
                    _upcomingEventViewState.value = UpcomingEventUIState.Success(firstEvent)
                } else {
                    _upcomingEventViewState.value = UpcomingEventUIState.None
                }
            } catch (exception: Exception) {
                _upcomingEventViewState.value = UpcomingEventUIState.Error(exception)
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

    suspend fun fetchOpenGraph(url: String): OpenGraphObject? {
        if (!this::currentUser.isInitialized) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                chatNetworkDataSource.getOpenGraph(
                    currentUser.getCredentials(),
                    currentUser.baseUrl!!,
                    url
                )?.openGraphObject
            }.getOrNull()
        }
    }

    suspend fun updateMessageDraft() {
        val model = conversationRepository.getLocallyStoredConversation(
            currentUser,
            chatRoomToken
        )
        model?.messageDraft?.let {
            messageDraft = it
        }
    }

    fun saveMessageDraft() {
        viewModelScope.launch {
            val model = conversationRepository.getLocallyStoredConversation(
                currentUser,
                chatRoomToken
            )
            model?.let {
                it.messageDraft = messageDraft
                conversationRepository.updateConversation(it)
            }
        }
    }

    suspend fun updateHiddenUpcomingEvent() {
        val model = conversationRepository.getLocallyStoredConversation(
            currentUser,
            chatRoomToken
        )
        model?.hiddenUpcomingEvent?.let {
            hiddenUpcomingEvent = it
        }
    }

    fun saveHiddenUpcomingEvent(value: String) {
        hiddenUpcomingEvent = value
        viewModelScope.launch {
            val model = conversationRepository.getLocallyStoredConversation(
                currentUser,
                chatRoomToken
            )
            model?.let {
                it.hiddenUpcomingEvent = value
                conversationRepository.updateConversation(it)
            }
        }
    }

    fun pinMessage(credentials: String, url: String, pinUntil: Int = 0) {
        viewModelScope.launch {
            chatRepository.pinMessage(credentials, url, pinUntil).collect {
                // UI is updated from room change observer
                getRoom(chatRoomToken)
            }
        }
    }

    fun unPinMessage(credentials: String, url: String) {
        viewModelScope.launch {
            chatRepository.unPinMessage(credentials, url).collect {
                // This updates the room if there are other pinned messages we need to show

                getRoom(chatRoomToken)
            }
        }
    }

    fun hidePinnedMessage(credentials: String, url: String) {
        viewModelScope.launch {
            chatRepository.hidePinnedMessage(credentials, url).collect {
                getRoom(chatRoomToken)
            }
        }
    }

    fun refreshRoom() {
        viewModelScope.launch {
            getRoom(chatRoomToken)
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
        private const val CONVERSATION_AND_USER_FLOW_SHARING_TIMEOUT_MS = 5_000L
        private const val WEBSOCKET_CONNECT_TIMEOUT_MS = 3000L
        private const val WEBSOCKET_POLL_INTERVAL_MS = 50L
        private const val ROOM_REFRESH_DEBOUNCE_MS = 500L
        private const val GROUPING_TIME_WINDOW_SECONDS = 300L
        private const val TIMESTAMP_TO_MILLIS = 1000L
        private const val MIN_CHARS_FOR_SEARCH = 2
        private const val CONTEXT_MESSAGES_LIMIT = 50
        private const val LOAD_MORE_MESSAGES_LIMIT = 100
        private const val POST_UPLOAD_FETCH_MAX_ATTEMPTS = 4
        private const val POST_UPLOAD_FETCH_RETRY_DELAY_MS = 1_500L
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

    sealed class ThreadRetrieveUiState {
        data object None : ThreadRetrieveUiState()
        data class Success(val thread: ThreadInfo?) : ThreadRetrieveUiState()
        data class Error(val exception: Exception) : ThreadRetrieveUiState()
    }

    sealed class UpcomingEventUIState {
        data object None : UpcomingEventUIState()
        data class Success(val event: UpcomingEvent) : UpcomingEventUIState()
        data class Error(val exception: Exception) : UpcomingEventUIState()
    }

    sealed class ChatEvent {
        object Initial : ChatEvent()
        object StartRegularPolling : ChatEvent()
        object Loading : ChatEvent()
        object Ready : ChatEvent()
        data class Error(val throwable: Throwable) : ChatEvent()
    }

    sealed interface ChatItem {
        fun messageOrNull(): ChatMessageUi? = (this as? MessageItem)?.uiMessage
        fun dateOrNull(): LocalDate? = (this as? DateHeaderItem)?.date

        fun stableKey(): Any =
            when (this) {
                is MessageItem -> "msg_${uiMessage.id}"
                is DateHeaderItem -> "header_$date"
                is UnreadMessagesMarkerItem -> "last_read_$date"
                is LoadGapItem -> "load_gap_$anchorMessageId"
            }

        data class MessageItem(val uiMessage: ChatMessageUi) : ChatItem
        data class DateHeaderItem(val date: LocalDate) : ChatItem
        data class UnreadMessagesMarkerItem(val date: LocalDate) : ChatItem
        data class LoadGapItem(val anchorMessageId: Int) : ChatItem
    }

    @AssistedFactory
    interface ChatViewModelFactory {
        fun build(roomToken: String, conversationThreadId: Long?): ChatViewModel
    }
}
