/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Christian Reiner <foss@christian-reiner.info>
 * SPDX-FileCopyrightText: 2024 Parneet Singh <gurayaparneet@gmail.com>
 * SPDX-FileCopyrightText: 2024 Giacomo Pacini <giacomo@paciosoft.com>
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2021-2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021-2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.emoji2.text.EmojiCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.android.common.ui.color.ColorUtil
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.activities.TakePhotoActivity
import com.nextcloud.talk.adapters.messages.CallStartedMessageInterface
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.data.model.FileParameters
import com.nextcloud.talk.chat.ui.ChatEmptyState
import com.nextcloud.talk.chat.ui.ChatEmptyStateType
import com.nextcloud.talk.chat.ui.ChatToolbar
import com.nextcloud.talk.chat.ui.ChatToolbarCallbacks
import com.nextcloud.talk.chat.ui.ChatToolbarState
import com.nextcloud.talk.chat.ui.MessageActionsBottomSheet
import com.nextcloud.talk.chat.ui.ProfileModalBottomSheet
import com.nextcloud.talk.chat.ui.ShowReactionsModalBottomSheet
import com.nextcloud.talk.chat.ui.TempMessageActionsBottomSheet
import com.nextcloud.talk.chat.ui.TypingIndicatorBanner
import com.nextcloud.talk.chat.ui.buildMessageActionsState
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.chat.viewmodels.MessageInputViewModel
import com.nextcloud.talk.conversationinfo.ConversationInfoActivity
import com.nextcloud.talk.conversationinfo.viewmodel.ConversationInfoViewModel
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.dagger.modules.ViewModelFactoryWithParams
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityChatBinding
import com.nextcloud.talk.events.UserMentionClickEvent
import com.nextcloud.talk.events.WebSocketCommunicationEvent
import com.nextcloud.talk.jobs.DeleteConversationWorker
import com.nextcloud.talk.jobs.DownloadFileToCacheWorker
import com.nextcloud.talk.jobs.ShareOperationWorker
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import com.nextcloud.talk.location.LocationPickerActivity
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.ConversationModel.Companion.checkIfVoiceRoom
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall
import com.nextcloud.talk.models.json.threads.ThreadInfo
import com.nextcloud.talk.polls.ui.PollCreateDialogFragment
import com.nextcloud.talk.polls.ui.PollMainDialogFragment
import com.nextcloud.talk.remotefilebrowser.activities.RemoteFileBrowserActivity
import com.nextcloud.talk.shareditems.activities.SharedItemsActivity
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.signaling.SignalingMessageSender
import com.nextcloud.talk.threadsoverview.ThreadsOverviewActivity
import com.nextcloud.talk.translate.ui.TranslateActivity
import com.nextcloud.talk.ui.ConversationDeleteNoticeView
import com.nextcloud.talk.ui.ConversationDeleteNoticeViewData
import com.nextcloud.talk.ui.OutOfOfficeView
import com.nextcloud.talk.ui.OutOfOfficeViewData
import com.nextcloud.talk.ui.PinnedMessageView
import com.nextcloud.talk.ui.PlaybackSpeed
import com.nextcloud.talk.ui.UpcomingEventView
import com.nextcloud.talk.ui.chat.ChatMessageCallbacks
import com.nextcloud.talk.ui.chat.ChatView
import com.nextcloud.talk.ui.chat.ChatViewCallbacks
import com.nextcloud.talk.ui.chat.ChatViewState
import com.nextcloud.talk.ui.dialog.DateTimeCompose
import com.nextcloud.talk.ui.dialog.FileAttachmentPreviewFragment
import com.nextcloud.talk.ui.dialog.GetPinnedOptionsDialog
import com.nextcloud.talk.ui.dialog.SaveToStorageDialogFragment
import com.nextcloud.talk.ui.theme.LocalMessageUtils
import com.nextcloud.talk.ui.theme.LocalOpenGraphFetcher
import com.nextcloud.talk.ui.theme.LocalViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.AudioUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.CapabilitiesUtil.hasSpreedFeatureCapability
import com.nextcloud.talk.utils.CapabilitiesUtil.retentionOfEventRooms
import com.nextcloud.talk.utils.CapabilitiesUtil.retentionOfInstantMeetingRoom
import com.nextcloud.talk.utils.CapabilitiesUtil.retentionOfSIPRoom
import com.nextcloud.talk.utils.ContactUtils
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.FileViewerUtils
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.VibrationUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_NAME
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_PATHS
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_BREAKOUT_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_MODERATOR
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_OPENED_VIA_NOTIFICATION
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_RECORDING_STATE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_START_CALL_AFTER_ROOM_SWITCH
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SWITCH_TO_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_THREAD_ID
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.rx.DisposableSet
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder
import com.nextcloud.talk.webrtc.Globals
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper
import com.nextcloud.talk.webrtc.WebSocketInstance
import com.otaliastudios.autocomplete.Autocomplete
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.math.abs

@Suppress("TooManyFunctions", "LargeClass", "LongMethod")
@AutoInjector(NextcloudTalkApplication::class)
class ChatActivity :
    BaseActivity(),
    CallStartedMessageInterface {

    var active = false

    private lateinit var binding: ActivityChatBinding

    @Inject
    lateinit var ncApi: NcApi

    @Inject
    lateinit var ncApiCoroutines: NcApiCoroutines

    @Inject
    lateinit var permissionUtil: PlatformPermissionUtil

    @Inject
    lateinit var dateUtils: DateUtils

    @Inject
    lateinit var colorUtil: ColorUtil

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var chatViewModelFactory: ChatViewModel.ChatViewModelFactory

    val chatViewModel: ChatViewModel by viewModels {
        ViewModelFactoryWithParams(ChatViewModel::class.java) {
            chatViewModelFactory.build(
                roomToken,
                conversationThreadId
            )
        }
    }

    lateinit var conversationInfoViewModel: ConversationInfoViewModel
    val messageInputViewModel: MessageInputViewModel by viewModels()

    private var hasScheduledMessages: Boolean = false

    private var chatToolbarState by mutableStateOf(ChatToolbarState())
    private var typingParticipantNames by mutableStateOf<List<String>>(emptyList())
    private val chatEmptyStateType = mutableStateOf<ChatEmptyStateType?>(null)
    private val upcomingEventUiState =
        mutableStateOf<ChatViewModel.UpcomingEventUIState>(ChatViewModel.UpcomingEventUIState.None)
    private val overflowContainerHeightPx = mutableIntStateOf(0)

    private val startSelectContactForResult = registerForActivityResult(
        ActivityResultContracts
            .StartActivityForResult()
    ) {
        executeIfResultOk(it) { intent ->
            onSelectContactResult(intent)
        }
    }

    private val startChooseFileIntentForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        executeIfResultOk(it) { intent ->
            onChooseFileResult(intent)
        }
    }

    private val startRemoteFileBrowsingForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        executeIfResultOk(it) { intent ->
            onRemoteFileBrowsingResult(intent)
        }
    }

    private val startPickCameraIntentForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        executeIfResultOk(it) { intent ->
            onPickCameraResult(intent)
        }
    }

    override val view: View
        get() = binding.root

    val disposables = DisposableSet()

    var sessionIdAfterRoomJoined: String? = null

    val roomToken: String by lazy {
        intent.getStringExtra(KEY_ROOM_TOKEN)
            ?: error("roomToken missing")
    }

    val conversationThreadId: Long? by lazy {
        if (intent.hasExtra(KEY_THREAD_ID)) {
            intent.getLongExtra(KEY_THREAD_ID, 0L)
        } else {
            null
        }
    }

    var openedViaNotification: Boolean = false
    var conversationThreadInfo: ThreadInfo? = null
    lateinit var conversationUser: User
    lateinit var spreedCapabilities: SpreedCapability
    var chatApiVersion: Int = 1
    private var roomPassword: String = ""
    var credentials: String? = null
    var currentConversation: ConversationModel? = null
    private var lastMessageClickTime = 0L
    private var lastMessageId = 0
    var mentionAutocomplete: Autocomplete<*>? = null
    var layoutManager: LinearLayoutManager? = null
    var pullChatMessagesPending = false
    var startCallFromNotification: Boolean = false
    var startCallFromRoomSwitch: Boolean = false

    var voiceOnly: Boolean = true
    var focusInput: Boolean = false
    private lateinit var path: String

    var myFirstMessage: CharSequence? = null
    var checkingLobbyStatus: Boolean = false
    private var isLeavingRoom: Boolean = false

    private var lastHandledHighlightNonce: Long? = null
    private var pendingHighlightedMessageId: Long? = null
    private var lastNoMoreResultsToastTime: Long = 0L
    private var pendingHighlightRetryJob: Job? = null
    private var centerSelectedMessageJob: Job? = null
    private var chatListComposeScope: CoroutineScope? = null
    private var pendingScrollToNewestMessage: Boolean = false

    var webSocketInstance: WebSocketInstance? = null
    var signalingMessageSender: SignalingMessageSender? = null
    var externalSignalingServer: ExternalSignalingServer? = null

    var getRoomInfoTimerHandler: Handler? = null

    private val filesToUpload: MutableList<String> = ArrayList()
    lateinit var sharedText: String

    private val _participantPermissionsFlow = MutableStateFlow<ParticipantPermissions?>(null)
    val participantPermissionsFlow: StateFlow<ParticipantPermissions?> = _participantPermissionsFlow.asStateFlow()

    private var videoURI: Uri? = null
    private var pendingTargetMessageId: Long? = null
    private var pendingTargetThreadId: Long? = null
    private var pendingTargetSearchQuery: String? = null

    private lateinit var pickMultipleMedia: ActivityResultLauncher<PickVisualMediaRequest>

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (chatViewModel.chatMode.value == ChatViewModel.ChatMode.SEARCH_MODE) {
                chatViewModel.exitSearchMode()
                chatToolbarState = chatToolbarState.copy(isSearchMode = false, searchQuery = "")
                return
            }

            if (!openedViaNotification && isChatThread()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            } else {
                val intent = Intent(this@ChatActivity, ConversationsListActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(intent)
                finish()
            }
        }
    }

    private lateinit var messageInputFragment: MessageInputFragment

    val typingParticipants = HashMap<String, TypingParticipant>()

    var callStarted = false

    private val leaveRoomObserver = androidx.lifecycle.Observer<ChatViewModel.ViewState> { state ->
        when (state) {
            is ChatViewModel.LeaveRoomSuccessState -> {
                logConversationInfos("leaveRoom#onNext")

                isLeavingRoom = false

                checkingLobbyStatus = false

                if (getRoomInfoTimerHandler != null) {
                    getRoomInfoTimerHandler?.removeCallbacksAndMessages(null)
                }

                ApplicationWideCurrentRoomHolder.getInstance().clear()

                if (webSocketInstance != null && currentConversation != null) {
                    webSocketInstance?.joinRoomWithRoomTokenAndSession(
                        "",
                        sessionIdAfterRoomJoined
                    )
                }

                sessionIdAfterRoomJoined = "0"

                if (state.funToCallWhenLeaveSuccessful != null) {
                    Log.d(TAG, "a callback action was set and is now executed because room was left successfully")
                    state.funToCallWhenLeaveSuccessful.invoke()
                }
            }

            else -> {}
        }
    }

    private val localParticipantMessageListener = SignalingMessageReceiver.LocalParticipantMessageListener { token ->
        if (CallActivity.active) {
            Log.d(TAG, "CallActivity is running. Ignore to switch chat in ChatActivity...")
        } else {
            switchToRoom(
                token = token,
                startCallAfterRoomSwitch = false,
                isVoiceOnlyCall = false
            )
        }
    }

    private val conversationMessageListener = object : SignalingMessageReceiver.ConversationMessageListener {
        override fun onStartTyping(userId: String?, session: String?) {
            val userIdOrGuestSession = userId ?: session

            if (isTypingStatusEnabled() && conversationUser.userId != userIdOrGuestSession) {
                var displayName = webSocketInstance?.getDisplayNameForSession(session)

                if (displayName != null && !typingParticipants.contains(userIdOrGuestSession)) {
                    if (displayName == "") {
                        displayName = context.resources?.getString(R.string.nc_guest)!!
                    }

                    runOnUiThread {
                        val typingParticipant = TypingParticipant(userIdOrGuestSession!!, displayName) {
                            typingParticipants.remove(userIdOrGuestSession)
                            updateTypingIndicator()
                        }

                        typingParticipants[userIdOrGuestSession] = typingParticipant
                        updateTypingIndicator()
                    }
                } else if (typingParticipants.contains(userIdOrGuestSession)) {
                    typingParticipants[userIdOrGuestSession]?.restartTimer()
                }
            }
        }

        override fun onStopTyping(userId: String?, session: String?) {
            val userIdOrGuestSession = userId ?: session

            if (isTypingStatusEnabled() && conversationUser.userId != userId) {
                typingParticipants[userIdOrGuestSession]?.cancelTimer()
                typingParticipants.remove(userIdOrGuestSession)
                updateTypingIndicator()
            }
        }

        override fun onChatMessagesReceived(chatMessages: List<ChatMessageJson>) {
            chatViewModel.onSignalingChatMessageReceived(chatMessages)
            Log.d(TAG, "received signaling message in ChatActivity")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupChatToolbarView()
        setupChatEmptyStateView()
        setupTypingIndicatorView()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.chatContainer) { view, insets ->
                val systemBarInsets = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
                )
                val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

                val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                val bottomPadding = if (isKeyboardVisible) imeInsets.bottom else systemBarInsets.bottom

                view.setPadding(
                    systemBarInsets.left,
                    systemBarInsets.top,
                    systemBarInsets.right,
                    bottomPadding
                )
                WindowInsetsCompat.CONSUMED
            }
        } else {
            colorizeStatusBar()
            colorizeNavigationBar()
        }

        conversationInfoViewModel = ViewModelProvider(this, viewModelFactory)[ConversationInfoViewModel::class.java]

        setChatListContent()

        setPinnedMessageContent()

        setUpcomingEventContent()

        binding.chatOverflowContainer.viewTreeObserver.addOnGlobalLayoutListener {
            overflowContainerHeightPx.intValue = binding.chatOverflowContainer.height
        }

        lifecycleScope.launch {
            currentUserProvider.getCurrentUser()
                .onSuccess { user ->
                    conversationUser = user
                    handleIntent(intent)
                    val urlForChatting = ApiUtils.getUrlForChat(chatApiVersion, conversationUser?.baseUrl, roomToken)
                    val credentials = ApiUtils.getCredentials(conversationUser!!.username, conversationUser!!.token)

                    chatViewModel.initData(
                        user,
                        credentials!!,
                        urlForChatting,
                        conversationThreadId
                    )

                    conversationThreadId?.let {
                        val threadUrl = ApiUtils.getUrlForThread(
                            version = 1,
                            baseUrl = conversationUser!!.baseUrl,
                            token = roomToken,
                            threadId = it.toInt()
                        )
                        chatViewModel.getThread(credentials, threadUrl)
                    }

                    messageInputFragment = getMessageInputFragment()
                    messageInputViewModel.setData(chatViewModel.getChatRepository())

                    initObservers()

                    pendingTargetMessageId?.let { messageId ->
                        lifecycleScope.launch {
                            chatViewModel.openMessageFromGlobalSearch(
                                messageId = messageId,
                                threadId = pendingTargetThreadId,
                                searchQuery = pendingTargetSearchQuery
                            )
                        }
                        pendingTargetMessageId = null
                        pendingTargetThreadId = null
                        pendingTargetSearchQuery = null
                    }

                    pickMultipleMedia = registerForActivityResult(
                        ActivityResultContracts.PickMultipleVisualMedia(MAX_AMOUNT_MEDIA_FILE_PICKER)
                    ) { uris ->
                        if (uris.isNotEmpty()) {
                            onChooseFileResult(uris)
                        }
                    }
                }
                .onFailure {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private fun setUpcomingEventContent() {
        chatViewModel.upcomingEventViewState.observe(this) { state ->
            if (state is ChatViewModel.UpcomingEventUIState.Error) {
                Log.e(TAG, "Error fetching upcoming events", state.exception)
            }
            upcomingEventUiState.value = state
        }
        binding.upcomingEventComposeView.setContent {
            MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(this@ChatActivity)) {
                val uiState = upcomingEventUiState.value
                val successState = uiState as? ChatViewModel.UpcomingEventUIState.Success
                val event = successState?.event
                val hiddenEventKey = event?.let { "${it.uri}${it.start}${it.summary}" }
                if (event != null && hiddenEventKey != chatViewModel.hiddenUpcomingEvent) {
                    UpcomingEventView(
                        event = event,
                        viewThemeUtils = viewThemeUtils,
                        onDismiss = {
                            upcomingEventUiState.value = ChatViewModel.UpcomingEventUIState.None
                            chatViewModel.saveHiddenUpcomingEvent(hiddenEventKey!!)
                            Snackbar.make(binding.root, R.string.nc_upcoming_event_dismissed, Snackbar.LENGTH_LONG)
                                .show()
                        }
                    )
                }
            }
        }
    }

    private fun setPinnedMessageContent() {
        binding.pinnedMessageComposeView.setContent {
            MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(this@ChatActivity)) {
                CompositionLocalProvider(LocalViewThemeUtils provides viewThemeUtils) {
                    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
                    val pinnedMessage = uiState.pinnedMessage
                    binding.pinnedMessageContainer.visibility = if (pinnedMessage != null) View.VISIBLE else View.GONE
                    if (pinnedMessage != null) {
                        PinnedMessageView(
                            message = pinnedMessage,
                            user = conversationUser,
                            viewThemeUtils = viewThemeUtils,
                            currentConversation = uiState.conversation,
                            scrollToMessageWithIdWithOffset = { messageId ->
                                chatViewModel.jumpToQuotedMessage(messageId.toLong())
                            },
                            hidePinnedMessage = ::hidePinnedMessage,
                            unPinMessage = ::unPinMessage
                        )
                    }
                }
            }
        }
    }

    private var chatListState: LazyListState? = null

    fun requestScrollToNewestMessage() {
        val listState = chatListState
        val composeScope = chatListComposeScope

        if (listState != null && composeScope != null) {
            pendingScrollToNewestMessage = false
            chatViewModel.switchToDefaultMode()
            composeScope.launch {
                listState.scrollToItem(0)
            }
        } else {
            pendingScrollToNewestMessage = true
        }
    }

    private fun scrollToMessageById(messageId: Long, logMiss: Boolean = true): Boolean {
        val items = chatViewModel.uiState.value.items
        val targetIndex = items.indexOfFirst { item ->
            (item as? ChatViewModel.ChatItem.MessageItem)?.uiMessage?.id == messageId.toInt()
        }
        val listState = chatListState
        val composeScope = chatListComposeScope
        val isReadyToScroll = targetIndex >= 0 && listState != null && composeScope != null

        if (isReadyToScroll) {
            logSearchNavigation("scroll hit messageId=$messageId index=$targetIndex")
            centerSelectedMessageJob?.cancel()
            val readyListState = requireNotNull(listState)
            val readyComposeScope = requireNotNull(composeScope)
            centerSelectedMessageJob = readyComposeScope.launch {
                stabilizeHighlightedMessagePosition(
                    listState = readyListState,
                    messageId = messageId,
                    initialIndex = targetIndex
                )
            }
        } else if (targetIndex >= 0 && listState != null) {
            if (logMiss) {
                logSearchNavigation("scroll miss messageId=$messageId reason=composeScope-null")
            }
        } else if (targetIndex >= 0) {
            if (logMiss) {
                logSearchNavigation("scroll miss messageId=$messageId reason=listState-null")
            }
        } else if (logMiss) {
            logSearchNavigation("scroll miss messageId=$messageId items=${items.size}")
        }

        return isReadyToScroll
    }

    private suspend fun centerItemInViewportIfVisible(listState: LazyListState, index: Int) {
        val layoutInfo = listState.layoutInfo
        val target = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
        val itemCenter = target.offset + (target.size / 2f)
        val distanceToCenter = itemCenter - viewportCenter

        if (abs(distanceToCenter) > SEARCH_CENTER_TOLERANCE_PX) {
            listState.scrollBy(distanceToCenter)
        }
    }

    private suspend fun stabilizeHighlightedMessagePosition(
        listState: LazyListState,
        messageId: Long,
        initialIndex: Int
    ) {
        var targetIndex = initialIndex
        repeat(SEARCH_CENTER_STABILIZE_ATTEMPTS) { attempt ->
            if (chatViewModel.uiState.value.highlightedMessageId != messageId.toInt()) {
                return
            }

            if (targetIndex < 0) {
                return
            }

            val isVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
            if (!isVisible) {
                listState.scrollToItem(index = targetIndex)
            }
            centerItemInViewportIfVisible(listState, targetIndex)

            if (attempt < SEARCH_CENTER_STABILIZE_ATTEMPTS - 1) {
                delay(SEARCH_CENTER_STABILIZE_DELAY_MS)
                targetIndex = chatViewModel.uiState.value.items.indexOfFirst { item ->
                    (item as? ChatViewModel.ChatItem.MessageItem)?.uiMessage?.id == messageId.toInt()
                }
            }
        }
    }

    private fun schedulePendingHighlightRetry(messageId: Long) {
        pendingHighlightRetryJob?.cancel()
        pendingHighlightRetryJob = lifecycleScope.launch {
            repeat(SEARCH_PENDING_SCROLL_RETRY_MAX) { attempt ->
                if (pendingHighlightedMessageId != messageId) {
                    return@launch
                }

                if (scrollToMessageById(messageId, logMiss = false)) {
                    logSearchNavigation("pending retry resolved messageId=$messageId attempt=${attempt + 1}")
                    pendingHighlightedMessageId = null
                    return@launch
                }

                delay(SEARCH_PENDING_SCROLL_RETRY_DELAY_MS)
            }

            if (pendingHighlightedMessageId == messageId) {
                logSearchNavigation("pending retry timeout messageId=$messageId")
            }
        }
    }

    private fun setChatListContent() {
        binding.messagesListViewCompose.setContent {
            MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(this@ChatActivity)) {
                val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
                val chatMode by chatViewModel.chatMode.collectAsStateWithLifecycle()
                val participantPermissions by participantPermissionsFlow.collectAsStateWithLifecycle()
                currentConversation = uiState.conversation

                LaunchedEffect(uiState.isInLobby, uiState.conversation?.lobbyTimer, uiState.conversation?.description) {
                    if (uiState.isInLobby) {
                        binding.messagesListViewCompose.visibility = View.GONE
                        showLobbyView()
                    } else {
                        binding.messagesListViewCompose.visibility = View.VISIBLE
                        binding.chatEmptyStateComposeView.visibility = View.GONE
                        binding.typingIndicatorComposeView.visibility = View.VISIBLE
                        chatEmptyStateType.value = null
                        if (participantPermissions != null) {
                            checkShowMessageInputView()
                        }
                    }
                }

                val listState = rememberLazyListState()
                val composeScope = rememberCoroutineScope()
                SideEffect {
                    chatListState = listState
                    chatListComposeScope = composeScope
                    if (pendingScrollToNewestMessage) {
                        requestScrollToNewestMessage()
                    }
                }

                SideEffect { chatListState = listState }

                CompositionLocalProvider(
                    LocalViewThemeUtils provides viewThemeUtils,
                    LocalMessageUtils provides messageUtils,
                    LocalOpenGraphFetcher provides { url -> chatViewModel.fetchOpenGraph(url) }
                ) {
                    val currentlyPlayingId by chatViewModel.currentlyPlayedMessageId.collectAsState(null)

                    val isOneToOneConversation = uiState.isOneToOneConversation
                    Log.d(TAG, "isOneToOneConversation=" + isOneToOneConversation)

                    // list of the file ids of messages being downloaded
                    val downloadingFileState = remember { mutableStateOf(listOf<String>()) }

                    // openWhenDownloaded is a derived boolean state of the visible chat message list on the condition
                    // that if any of the messages that are present contain a fileId that is within downloadingFileState
                    val openWhenDownloadState = remember { mutableStateOf(false) }

                    val visibleIds = listState.visibleItemsWithThreshold()
                    LaunchedEffect(visibleIds, downloadingFileState.value) {
                        openWhenDownloadState.value = (downloadingFileState.value.intersect(visibleIds).isNotEmpty())
                    }

                    val overflowHeightDp = with(LocalDensity.current) {
                        overflowContainerHeightPx.intValue.toDp()
                    }
                    ChatView(
                        state = ChatViewState(
                            chatItems = uiState.items,
                            isOneToOneConversation = isOneToOneConversation,
                            currentlyPlayingVoiceMessageId = currentlyPlayingId,
                            conversationThreadId = conversationThreadId,
                            chatMode = chatMode,
                            highlightedMessageId = uiState.highlightedMessageId,
                            highlightedSearchTerm = uiState.highlightedSearchTerm,
                            hasChatPermission = participantPermissions?.hasChatPermission() == true,
                            downloadingFileState = downloadingFileState.value,
                            stickyHeaderTopOffset = overflowHeightDp
                        ),
                        callbacks = ChatViewCallbacks(
                            onLoadMore = { messageId, direction -> loadMoreMessages(messageId, direction) },
                            onJumpToBottom = { chatViewModel.switchToDefaultMode() },
                            advanceLocalLastReadMessageIfNeeded = { advanceLocalLastReadMessageIfNeeded(it) },
                            updateRemoteLastReadMessageIfNeeded = { updateRemoteLastReadMessageIfNeeded() },
                            onLoadQuotedMessageClick = { messageId -> onLoadQuotedMessage(messageId) },
                            messageCallbacks = ChatMessageCallbacks(
                                onLongClick = { openMessageActionsDialog(it) },
                                onSwipeReply = { handleSwipeToReply(it) },
                                onFileClick = { downloadAndOpenFile(it, openWhenDownloadState, downloadingFileState) },
                                onPollClick = { pollId, pollName -> openPollDialog(pollId, pollName) },
                                onVoicePlayPauseClick = { onVoicePlayPauseClickCompose(it) },
                                onVoiceSeek = { _, progress -> chatViewModel.seekToMediaPlayer(progress) },
                                onVoiceSpeedClick = { onVoiceSpeedClickCompose(it) },
                                onReactionClick = { messageId, emoji -> handleReactionClick(messageId, emoji) },
                                onReactionLongClick = { messageId -> openReactionsDialog(messageId) },
                                onOpenThreadClick = { messageId -> openThread(messageId.toLong()) },
                                onSystemMessageExpandClick = { messageId ->
                                    chatViewModel.toggleSystemMessageCollapse(messageId)
                                },
                                onAvatarClick = { messageId -> chatViewModel.showProfileSheet(messageId.toLong()) }
                            )
                        ),
                        listState = listState
                    )
                }

                val reactionsSheetMessageId by chatViewModel.reactionsSheetMessageId.collectAsStateWithLifecycle()
                val reactionsSheetMessage by produceState<ChatMessage?>(null, reactionsSheetMessageId) {
                    value = reactionsSheetMessageId?.let { id -> chatViewModel.getMessageById(id).first() }
                }
                reactionsSheetMessage?.let { msg ->
                    conversationUser?.let { user ->
                        ShowReactionsModalBottomSheet(
                            chatMessage = msg,
                            user = user,
                            roomToken = roomToken,
                            hasReactPermission = participantPermissions?.hasReactPermission() == true,
                            ncApiCoroutines = ncApiCoroutines,
                            onDeleteReaction = { emoji -> chatViewModel.deleteReaction(roomToken, msg, emoji) },
                            onDismiss = { chatViewModel.dismissReactionsSheet() }
                        )
                    }
                }

                val profileSheetMessageId by chatViewModel.profileSheetMessageId.collectAsStateWithLifecycle()
                val profileSheetMessage by produceState<ChatMessage?>(null, profileSheetMessageId) {
                    value = profileSheetMessageId?.let { id -> chatViewModel.getMessageById(id).first() }
                }
                profileSheetMessage
                    ?.takeIf { it.actorType.equals("users") }
                    ?.let { msg ->
                        val actorId = msg.actorId ?: return@let
                        conversationUser?.let { user ->
                            ProfileModalBottomSheet(
                                actorId = actorId,
                                user = user,
                                ncApiCoroutines = ncApiCoroutines,
                                onTalkTo = { actorId -> startDirectChat(actorId) },
                                onDismiss = { chatViewModel.dismissProfileSheet() }
                            )
                        }
                    }

                val messageActionsMessageId by chatViewModel.messageActionsMessageId.collectAsStateWithLifecycle()
                val messageActionsMessage by produceState<ChatMessage?>(null, messageActionsMessageId) {
                    value = messageActionsMessageId?.let { id -> chatViewModel.getMessageById(id).first() }
                }
                val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle()
                messageActionsMessage?.let { msg ->
                    if (msg.isTemporary) {
                        val sendingFailed = msg.sendStatus == SendStatus.FAILED
                        TempMessageActionsBottomSheet(
                            showResend = sendingFailed && isOnline,
                            showEdit = sendingFailed || !isOnline,
                            showDelete = sendingFailed || !isOnline,
                            onResend = {
                                chatViewModel.resendMessage(
                                    conversationUser!!.getCredentials(),
                                    ApiUtils.getUrlForChat(chatApiVersion, conversationUser!!.baseUrl!!, roomToken),
                                    msg
                                )
                            },
                            onEdit = { messageInputViewModel.edit(msg) },
                            onDelete = { chatViewModel.deleteTempMessage(msg) },
                            onCopy = { copyMessage(msg) },
                            onDismiss = { chatViewModel.dismissMessageActions() }
                        )
                    } else {
                        conversationUser?.let { user ->
                            MessageActionsBottomSheet(
                                actionsState = buildMessageActionsState(
                                    message = msg,
                                    user = user,
                                    conversation = currentConversation,
                                    hasChatPermission = participantPermissions?.hasChatPermission() == true,
                                    hasReactPermission = participantPermissions?.hasReactPermission() == true,
                                    spreedCapabilities = spreedCapabilities,
                                    isOnline = isOnline,
                                    dateUtils = dateUtils,
                                    conversationThreadId = conversationThreadId
                                ),
                                onEmojiClick = { emoji ->
                                    if (msg.reactionsSelf?.contains(emoji) == true) {
                                        chatViewModel.deleteReaction(roomToken, msg, emoji)
                                    } else {
                                        chatViewModel.addReaction(roomToken, msg, emoji)
                                    }
                                },
                                onReply = {
                                    if (msg.isThread && conversationThreadId == null) {
                                        openThread(msg)
                                    } else {
                                        messageInputViewModel.reply(msg)
                                    }
                                },
                                onReplyPrivately = { replyPrivately(msg) },
                                onOpenThread = { msg.threadId?.let { openThread(it) } },
                                onForward = { forwardMessage(msg) },
                                onEdit = { messageInputViewModel.edit(msg) },
                                onCopy = { copyMessage(msg) },
                                onMarkAsUnread = { markAsUnread(msg) },
                                onRemind = { remindMeLater(msg) },
                                onPin = { pinMessage(msg) },
                                onUnpin = { unPinMessage(msg) },
                                onTranslate = { translateMessage(msg) },
                                onShareToNote = { shareToNotes(msg) },
                                onShare = {
                                    if (msg.getCalculateMessageType() ==
                                        ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE
                                    ) {
                                        checkIfSharable(msg)
                                    } else {
                                        msg.message?.let { shareMessageText(it) }
                                    }
                                },
                                onSave = { checkIfSaveable(msg) },
                                onOpenInFiles = { openInFilesApp(msg) },
                                onDelete = { deleteMessage(msg) },
                                onDismiss = { chatViewModel.dismissMessageActions() }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LazyListState.visibleItemsWithThreshold(): List<String> =
        remember(this) {
            derivedStateOf {
                val visibleItemsInfo = layoutInfo.visibleItemsInfo
                if (layoutInfo.totalItemsCount == 0) {
                    emptyList()
                } else {
                    visibleItemsInfo.mapNotNull { it.key as? String }
                }
            }
        }.value.mapNotNull { key ->
            val messageItem = chatViewModel.uiState.collectAsState().value.items.firstOrNull { it.stableKey() == key }
            val message = messageItem?.messageOrNull()
            var result: String? = null
            message?.let {
                if (message.messageParameters.isNotEmpty()) {
                    runCatching {
                        message.messageParameters as HashMap<String?, HashMap<String?, String?>>?
                        val fileParameters = FileParameters(message.messageParameters)
                        result = fileParameters.id
                    }.onFailure { e ->
                        when (e) {
                            is ClassCastException -> {} // weird
                            else -> Log.e(TAG, "Error in LazyListState.visibleItemsWithThreshold $e")
                        }
                    }
                }
            }

            result
        }

    private fun onLoadQuotedMessage(messageId: Int) {
        chatViewModel.jumpToQuotedMessage(messageId.toLong())
    }

    private fun onVoicePlayPauseClickCompose(messageId: Int) {
        lifecycleScope.launch {
            val isCurrentlyPlaying = chatViewModel.uiState.value.items
                .mapNotNull { (it as? ChatViewModel.ChatItem.MessageItem)?.uiMessage }
                .firstOrNull { it.id == messageId }
                ?.content
                ?.let { it as? MessageTypeContent.Voice }
                ?.isPlaying ?: false

            val message = chatViewModel.getMessageById(messageId.toLong()).first()
            val filename = message.fileParameters.name
            if (filename.isEmpty()) {
                return@launch
            }

            val file = FileUtils.resolveSharedAttachmentFile(context.cacheDir, filename)
            if (file == null) {
                return@launch
            }
            if (file.exists()) {
                if (isCurrentlyPlaying) {
                    chatViewModel.pauseMediaPlayer(true)
                    chatViewModel.pauseVoiceMessageUiState(messageId)
                } else {
                    val uiSpeed = chatViewModel.uiState.value.items
                        .mapNotNull { (it as? ChatViewModel.ChatItem.MessageItem)?.uiMessage }
                        .firstOrNull { it.id == messageId }
                        ?.content
                        ?.let { it as? MessageTypeContent.Voice }
                        ?.playbackSpeed ?: PlaybackSpeed.NORMAL
                    chatViewModel.setPlayBack(uiSpeed)

                    val retrieved = appPreferences.getWaveFormFromFile(filename)
                    if (retrieved.isEmpty()) {
                        setUpWaveform(message)
                    } else {
                        if (message.voiceMessageFloatArray == null || message.voiceMessageFloatArray!!.isEmpty()) {
                            message.voiceMessageFloatArray = retrieved.toFloatArray()
                            chatViewModel.syncVoiceMessageUiState(message)
                        }
                        startPlayback(file, message)
                    }
                }
            } else {
                downloadFileToCache(message, true) {
                    setUpWaveform(message)
                }
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun startDirectChat(actorId: String) {
        lifecycleScope.launch {
            try {
                val user = conversationUser ?: return@launch
                val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, 1))
                val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
                    version = apiVersion,
                    baseUrl = user.baseUrl!!,
                    roomType = ROOM_TYPE_ONE_TO_ONE,
                    invite = actorId
                )
                val roomOverall = ncApiCoroutines.createRoom(
                    ApiUtils.getCredentials(user.username, user.token),
                    retrofitBucket.url,
                    retrofitBucket.queryMap
                )
                val bundle = Bundle()
                bundle.putString(KEY_ROOM_TOKEN, roomOverall.ocs!!.data!!.token)
                val chatIntent = Intent(this@ChatActivity, ChatActivity::class.java)
                chatIntent.putExtras(bundle)
                chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(chatIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start direct chat with $actorId", e)
                Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun onVoiceSpeedClickCompose(messageId: Int) {
        val currentSpeed = chatViewModel.uiState.value.items
            .mapNotNull { (it as? ChatViewModel.ChatItem.MessageItem)?.uiMessage }
            .firstOrNull { it.id == messageId }
            ?.content
            ?.let { it as? MessageTypeContent.Voice }
            ?.playbackSpeed ?: PlaybackSpeed.NORMAL
        val nextSpeed = currentSpeed.next()
        chatViewModel.setPlayBack(nextSpeed)
        appPreferences.savePreferredPlayback(conversationUser!!.userId, nextSpeed)
        chatViewModel.setVoiceMessageSpeed(messageId, nextSpeed)
    }

    fun downloadAndOpenFile(
        messageId: Int,
        openWhenDownloadState: MutableState<Boolean>,
        downloadState: MutableState<List<String>>
    ) {
        lifecycleScope.launch {
            val chatMessage = chatViewModel.getMessageById(messageId.toLong()).first()
            FileViewerUtils(this@ChatActivity, conversationUser).openFile(
                chatMessage,
                openWhenDownloadState,
                downloadState
            )
        }
    }

    fun openPollDialog(pollId: String, pollName: String) {
        val isOwnerOrModerator = ConversationUtils.isParticipantOwnerOrModerator(currentConversation!!)
        PollMainDialogFragment
            .newInstance(conversationUser!!, roomToken, isOwnerOrModerator, pollId, pollName)
            .show(supportFragmentManager, "PollMainDialogFragment")
    }

    private fun handleReactionClick(messageId: Int, emoji: String) {
        lifecycleScope.launch {
            val chatMessage = chatViewModel.getMessageById(messageId.toLong()).first()
            onClickReaction(chatMessage, emoji)
        }
    }

    private fun openReactionsDialog(messageId: Int) {
        chatViewModel.showReactionsSheet(messageId.toLong())
    }

    private fun getMessageInputFragment(): MessageInputFragment {
        val internalId = conversationUser!!.id.toString() + "@" + roomToken
        return MessageInputFragment().apply {
            arguments = Bundle().apply {
                putString(CONVERSATION_INTERNAL_ID, internalId)
                putString(BundleKeys.KEY_SHARED_TEXT, sharedText)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val extras: Bundle? = intent.extras

        val requestedRoomSwitch = extras?.getBoolean(KEY_SWITCH_TO_ROOM, false) == true

        if (requestedRoomSwitch) {
            val newRoomToken = extras?.getString(KEY_ROOM_TOKEN).orEmpty()
            val startCallAfterRoomSwitch = extras?.getBoolean(KEY_START_CALL_AFTER_ROOM_SWITCH, false) == true
            val isVoiceOnlyCall = extras?.getBoolean(KEY_CALL_VOICE_ONLY, false) == true

            if (newRoomToken != roomToken) {
                switchToRoom(newRoomToken, startCallAfterRoomSwitch, isVoiceOnlyCall)
            }
        } else {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent) {
        val extras: Bundle? = intent.extras

        openedViaNotification = extras?.getBoolean(KEY_OPENED_VIA_NOTIFICATION) ?: false

        sharedText = extras?.getString(BundleKeys.KEY_SHARED_TEXT).orEmpty()

        Log.d(TAG, "   roomToken = $roomToken")
        if (roomToken.isEmpty()) {
            Log.d(TAG, "   roomToken was null or empty!")
        }

        roomPassword = extras?.getString(BundleKeys.KEY_CONVERSATION_PASSWORD).orEmpty()

        credentials = if (conversationUser?.userId == "?") {
            null
        } else {
            ApiUtils.getCredentials(conversationUser!!.username, conversationUser!!.token)
        }

        startCallFromNotification = extras?.getBoolean(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL, false) == true
        startCallFromRoomSwitch = extras?.getBoolean(KEY_START_CALL_AFTER_ROOM_SWITCH, false) == true

        voiceOnly = extras?.getBoolean(KEY_CALL_VOICE_ONLY, false) == true

        focusInput = extras?.getBoolean(BundleKeys.KEY_FOCUS_INPUT) == true

        pendingTargetMessageId = extras?.getString(BundleKeys.KEY_MESSAGE_ID)?.toLongOrNull()?.takeIf { it > 0L }
            ?: extras?.getLong(BundleKeys.KEY_MESSAGE_ID)?.takeIf { it > 0L }
        pendingTargetThreadId = extras?.getString(BundleKeys.KEY_THREAD_ID)?.toLongOrNull()?.takeIf { it > 0L }
            ?: extras?.getLong(BundleKeys.KEY_THREAD_ID)?.takeIf { it > 0L }
        pendingTargetSearchQuery = extras?.getString(BundleKeys.KEY_SEARCH_QUERY)
    }

    override fun onStart() {
        super.onStart()
        active = true
        this.lifecycle.addObserver(AudioUtils)
        this.lifecycle.addObserver(chatViewModel)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        chatViewModel.handleOrientationChange()
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        active = false
        this.lifecycle.removeObserver(AudioUtils)
        this.lifecycle.removeObserver(chatViewModel)
    }

    @OptIn(FlowPreview::class)
    @SuppressLint("SetTextI18n", "ResourceAsColor")
    @Suppress("LongMethod")
    private fun initObservers() {
        data class SearchObserverState(
            val selectedIndex: Int,
            val resultsCount: Int,
            val hasError: Boolean,
            val isLoading: Boolean
        )

        Log.d(TAG, "initObservers Called")

        lifecycleScope.launch {
            chatViewModel.chatMode.collectLatest { mode ->
                val inSearchMode = mode == ChatViewModel.ChatMode.SEARCH_MODE
                updateToolbarForSearchMode(inSearchMode)
                updateSearchLoadingIndicator(
                    isLoading = inSearchMode && chatViewModel.searchUiState.value.isLoading
                )
            }
        }

        lifecycleScope.launch {
            chatViewModel.searchUiState
                .map {
                    SearchObserverState(
                        selectedIndex = it.selectedIndex,
                        resultsCount = it.results.size,
                        hasError = it.error,
                        isLoading = it.isLoading
                    )
                }
                .distinctUntilChanged()
                .collectLatest { state ->
                    val inSearchMode = chatViewModel.chatMode.value == ChatViewModel.ChatMode.SEARCH_MODE
                    updateSearchLoadingIndicator(
                        isLoading = inSearchMode && state.isLoading
                    )
                    if (inSearchMode && state.hasError) {
                        Toast.makeText(this@ChatActivity, R.string.nc_common_error_sorry, Toast.LENGTH_SHORT).show()
                    }
                }
        }

        lifecycleScope.launch {
            chatViewModel.isLoadingFlow.collectLatest { isLoading ->
                updateSearchLoadingIndicator(isLoading)
            }
        }

        lifecycleScope.launch {
            chatViewModel.noMoreSearchResults.collect {
                val inSearchMode = chatViewModel.chatMode.value == ChatViewModel.ChatMode.SEARCH_MODE
                val now = System.currentTimeMillis()
                if (inSearchMode && now - lastNoMoreResultsToastTime >= NO_MORE_RESULTS_TOAST_THROTTLE_MS) {
                    lastNoMoreResultsToastTime = now
                    Toast.makeText(
                        this@ChatActivity,
                        R.string.message_search_no_more_results,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        lifecycleScope.launch {
            chatViewModel.uiState.collectLatest { state ->
                val nonce = state.highlightTriggerNonce
                val messageId = state.highlightedMessageId
                if (nonce != null && messageId != null && nonce != lastHandledHighlightNonce) {
                    lastHandledHighlightNonce = nonce
                    val wasScrolled = scrollToMessageById(messageId.toLong())
                    pendingHighlightedMessageId = if (wasScrolled) null else messageId.toLong()
                    if (!wasScrolled) {
                        schedulePendingHighlightRetry(messageId.toLong())
                    }
                    logSearchNavigation(
                        "highlight nonce=$nonce messageId=$messageId wasScrolled=$wasScrolled " +
                            "pending=$pendingHighlightedMessageId"
                    )
                }

                pendingHighlightedMessageId?.let { pendingMessageId ->
                    if (scrollToMessageById(pendingMessageId)) {
                        logSearchNavigation("pending resolved messageId=$pendingMessageId")
                        pendingHighlightedMessageId = null
                        pendingHighlightRetryJob?.cancel()
                    }
                }
            }
        }

        chatViewModel.getCapabilitiesViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.GetCapabilitiesUpdateState -> {
                    if (currentConversation != null) {
                        spreedCapabilities = state.spreedCapabilities
                        chatApiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))
                        _participantPermissionsFlow.value =
                            ParticipantPermissions(spreedCapabilities, currentConversation!!)

                        updateToolbarState()
                        updateRoomTimerHandler()
                    } else {
                        Log.w(
                            TAG,
                            "currentConversation was null in observer ChatViewModel.GetCapabilitiesUpdateState"
                        )
                    }
                }

                is ChatViewModel.GetCapabilitiesInitialLoadState -> {
                    spreedCapabilities = state.spreedCapabilities
                    currentConversation = state.conversationModel
                    chatApiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))
                    _participantPermissionsFlow.value =
                        ParticipantPermissions(spreedCapabilities, state.conversationModel!!)

                    supportFragmentManager.commit {
                        setReorderingAllowed(true) // optimizes out redundant replace operations
                        replace(R.id.fragment_container_activity_chat, messageInputFragment)
                        runOnCommit {
                            if (focusInput) {
                                messageInputFragment.binding.fragmentMessageInputView.requestFocus()
                            }
                        }
                    }

                    joinRoomWithPassword()

                    refreshScheduledMessages()

                    updateToolbarState()
                    if (state.conversationModel.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
                        state.conversationModel.status == "dnd"
                    ) {
                        conversationUser.let { user ->
                            val credentials = ApiUtils.getCredentials(user.username, user.token)
                            chatViewModel.outOfOfficeStatusOfUser(
                                credentials!!,
                                user.baseUrl!!,
                                state.conversationModel!!.name
                            )
                        }
                    }

                    conversationUser?.let { user ->
                        val credentials = ApiUtils.getCredentials(user.username, user.token)
                        chatViewModel.fetchUpcomingEvent(
                            credentials!!,
                            user.baseUrl!!,
                            roomToken
                        )
                    }

                    if (state.conversationModel.objectType == ConversationEnums.ObjectType.EVENT &&
                        hasSpreedFeatureCapability(
                            conversationUser?.capabilities!!.spreedCapability!!,
                            SpreedFeatures.UNBIND_CONVERSATION
                        )
                    ) {
                        val eventEndTimeStamp =
                            state.conversationModel?.objectId
                                ?.split("#")
                                ?.getOrNull(1)
                                ?.toLongOrNull()
                        val currentTimeStamp = (System.currentTimeMillis() / ONE_SECOND_IN_MILLIS).toLong()
                        val retentionPeriod = retentionOfEventRooms(spreedCapabilities)
                        val isPastEvent = eventEndTimeStamp?.let { it < currentTimeStamp }
                        if (isPastEvent == true && retentionPeriod != 0) {
                            showConversationDeletionWarning(retentionPeriod)
                        }
                    }

                    if (state.conversationModel.objectType == ConversationEnums.ObjectType.PHONE_TEMPORARY &&
                        hasSpreedFeatureCapability(
                            conversationUser?.capabilities!!.spreedCapability!!,
                            SpreedFeatures.UNBIND_CONVERSATION
                        )
                    ) {
                        val retentionPeriod = retentionOfSIPRoom(spreedCapabilities)
                        val systemMessage = currentConversation?.lastMessage?.systemMessageType
                        if (retentionPeriod != 0 &&
                            (
                                systemMessage == ChatMessage.SystemMessageType.CALL_ENDED ||
                                    systemMessage == ChatMessage.SystemMessageType.CALL_ENDED_EVERYONE
                                )
                        ) {
                            showConversationDeletionWarning(retentionPeriod)
                        }
                    }

                    if (state.conversationModel.objectType == ConversationEnums.ObjectType.INSTANT_MEETING &&
                        hasSpreedFeatureCapability(
                            conversationUser?.capabilities!!.spreedCapability!!,
                            SpreedFeatures.UNBIND_CONVERSATION
                        )
                    ) {
                        val retentionPeriod = retentionOfInstantMeetingRoom(spreedCapabilities)
                        val systemMessage = state.conversationModel.lastMessage?.systemMessageType
                        if (retentionPeriod != 0 &&
                            (
                                systemMessage == ChatMessage.SystemMessageType.CALL_ENDED ||
                                    systemMessage == ChatMessage.SystemMessageType.CALL_ENDED_EVERYONE
                                )
                        ) {
                            showConversationDeletionWarning(retentionPeriod)
                        }
                    }

                    updateRoomTimerHandler(MILLIS_250)
                }

                is ChatViewModel.GetCapabilitiesErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }

        chatViewModel.joinRoomViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.JoinRoomSuccessState -> {
                    currentConversation = state.conversationModel

                    sessionIdAfterRoomJoined = currentConversation!!.sessionId
                    ApplicationWideCurrentRoomHolder.getInstance().session = currentConversation!!.sessionId
                    ApplicationWideCurrentRoomHolder.getInstance().currentRoomToken = currentConversation!!.token
                    ApplicationWideCurrentRoomHolder.getInstance().userInRoom = conversationUser

                    logConversationInfos("joinRoomWithPassword#onNext")

                    setupWebsocket()

                    if (currentConversation.checkIfVoiceRoom()) {
                        startACall(false, true)
                    }

                    if (startCallFromNotification) {
                        startCallFromNotification = false
                        startACall(voiceOnly, false)
                    }

                    if (startCallFromRoomSwitch) {
                        startCallFromRoomSwitch = false
                        startACall(voiceOnly, true)
                    }
                }

                is ChatViewModel.JoinRoomErrorState -> {}

                else -> {}
            }
        }

        chatViewModel.leaveRoomViewState.observeForever(leaveRoomObserver)

        messageInputViewModel.sendChatMessageViewState.observe(this) { state ->
            when (state) {
                is MessageInputViewModel.SendChatMessageSuccessState -> {
                    myFirstMessage = state.message
                    removeUnreadMessagesMarker()
                }

                is MessageInputViewModel.SendChatMessageErrorState -> {}

                else -> {}
            }
        }

        messageInputViewModel.scheduleChatMessageViewState.observe(this) { state ->
            when (state) {
                is MessageInputViewModel.ScheduleChatMessageSuccessState -> {
                    val scheduledAt = state.scheduledAt
                    val scheduledTimeText = dateUtils.getLocalDateTimeStringFromTimestamp(
                        scheduledAt * DateConstants.SECOND_DIVIDER
                    )
                    messageInputFragment.onScheduledMessageSent()
                    Snackbar.make(
                        binding.root,
                        getString(R.string.nc_message_scheduled_at, scheduledTimeText),
                        Snackbar.LENGTH_LONG
                    ).show()
                    refreshScheduledMessages()
                }

                is MessageInputViewModel.ScheduleChatMessageErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }

        chatViewModel.scheduledMessagesViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.ScheduledMessagesSuccessState -> {
                    hasScheduledMessages = state.messages.isNotEmpty()
                    messageInputFragment.updateScheduledMessagesAvailability(hasScheduledMessages)
                    updateToolbarState()
                }

                is ChatViewModel.ScheduledMessagesErrorState -> {
                    hasScheduledMessages = false
                    messageInputFragment.updateScheduledMessagesAvailability(false)
                    updateToolbarState()
                }

                else -> {}
            }
        }

        chatViewModel.deleteChatMessageViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.DeleteChatMessageSuccessState -> {
                    if (state.msg.ocs!!.meta!!.statusCode == HttpURLConnection.HTTP_ACCEPTED) {
                        Snackbar.make(
                            binding.root,
                            R.string.nc_delete_message_leaked_to_matterbridge,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }

                is ChatViewModel.DeleteChatMessageErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }

        chatViewModel.createRoomViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.CreateRoomSuccessState -> {
                    val bundle = Bundle()
                    bundle.putString(KEY_ROOM_TOKEN, state.roomOverall.ocs!!.data!!.token)

                    leaveRoom {
                        val chatIntent = Intent(context, ChatActivity::class.java)
                        chatIntent.putExtras(bundle)
                        chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(chatIntent)
                    }
                }

                is ChatViewModel.CreateRoomErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }

        this.lifecycleScope.launch {
            chatViewModel.mediaPlayerSeekbarObserver.onEach { msg ->
            }.collect()
        }

        messageInputViewModel.editMessageViewState.observe(this) { state ->
            when (state) {
                is MessageInputViewModel.EditMessageSuccessState -> {
                    when (state.messageEdited.ocs?.meta?.statusCode) {
                        HTTP_BAD_REQUEST -> {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.edit_error_24_hours_old_message),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }

                        HTTP_FORBIDDEN -> {
                            Snackbar.make(
                                binding.root,
                                getString(R.string.conversation_is_read_only),
                                Snackbar.LENGTH_LONG
                            ).show()
                        }

                        HTTP_NOT_FOUND -> {
                            Snackbar.make(
                                binding.root,
                                "Conversation not found",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                is MessageInputViewModel.EditMessageErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }

        chatViewModel.getVoiceRecordingLocked.observe(this) { showContiniousVoiceRecording ->
            if (showContiniousVoiceRecording) {
                binding.voiceRecordingLock.visibility = View.GONE
                supportFragmentManager.commit {
                    setReorderingAllowed(true) // apparently used for optimizations
                    replace(R.id.fragment_container_activity_chat, MessageInputVoiceRecordingFragment())
                }
            } else {
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace(R.id.fragment_container_activity_chat, getMessageInputFragment())
                }
            }
        }

        chatViewModel.getVoiceRecordingInProgress.observe(this) { voiceRecordingInProgress ->
            VibrationUtils.vibrateShort(context)
            if (voiceRecordingInProgress) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            binding.voiceRecordingLock.visibility = if (
                voiceRecordingInProgress &&
                chatViewModel.getVoiceRecordingLocked.value != true
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        chatViewModel.recordTouchObserver.observe(this) { y ->
            binding.voiceRecordingLock.y -= y
        }

        chatViewModel.unbindRoomResult.observe(this) { uiState ->
            when (uiState) {
                is ChatViewModel.UnbindRoomUiState.Success -> {
                    binding.conversationDeleteNoticeComposeView.visibility = View.GONE
                    Snackbar.make(
                        binding.root,
                        context.getString(R.string.nc_room_retention),
                        Snackbar.LENGTH_LONG
                    ).show()

                    chatToolbarState = chatToolbarState.copy(showEventMenu = false)
                }

                is ChatViewModel.UnbindRoomUiState.Error -> {
                    Snackbar.make(
                        binding.root,
                        context.getString(R.string.nc_common_error_sorry),
                        Snackbar.LENGTH_LONG
                    ).show()
                }

                else -> {}
            }
        }

        chatViewModel.outOfOfficeViewState.observe(this) { uiState ->
            when (uiState) {
                is ChatViewModel.OutOfOfficeUIState.Error -> {
                    Log.e(TAG, "Error fetching/ no user absence data", uiState.exception)
                }

                ChatViewModel.OutOfOfficeUIState.None -> {
                }

                is ChatViewModel.OutOfOfficeUIState.Success -> {
                    binding.outOfOfficeComposeView.apply {
                        visibility = View.VISIBLE
                        setContent {
                            MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(this@ChatActivity)) {
                                OutOfOfficeView(
                                    data = OutOfOfficeViewData(
                                        userAbsence = uiState.userAbsence,
                                        displayName = currentConversation?.displayName.orEmpty(),
                                        baseUrl = conversationUser?.baseUrl
                                    ),
                                    viewThemeUtils = viewThemeUtils,
                                    onReplacementClick = {
                                        joinOneToOneConversation(uiState.userAbsence.replacementUserId!!)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        this.lifecycleScope.launch {
            chatViewModel.threadRetrieveState.collect { uiState ->
                when (uiState) {
                    ChatViewModel.ThreadRetrieveUiState.None -> {
                    }

                    is ChatViewModel.ThreadRetrieveUiState.Error -> {
                        Log.e(TAG, "Error when retrieving thread", uiState.exception)
                        Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                    }

                    is ChatViewModel.ThreadRetrieveUiState.Success -> {
                        conversationThreadInfo = uiState.thread
                        updateToolbarState()
                    }
                }
            }
        }
    }

    private fun removeUnreadMessagesMarker() {
        chatViewModel.setUnreadMessagesMarker(false)
    }

    fun showConversationDeletionWarning(retentionPeriod: Int) {
        binding.conversationDeleteNoticeComposeView.apply {
            visibility = View.VISIBLE
            bringToFront()
            setContent {
                MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(this@ChatActivity)) {
                    ConversationDeleteNoticeView(
                        data = ConversationDeleteNoticeViewData(
                            retentionDays = retentionPeriod,
                            isModeratorOrOwner = ConversationUtils.isParticipantOwnerOrModerator(currentConversation!!)
                        ),
                        viewThemeUtils = viewThemeUtils,
                        onDeleteNow = { deleteConversationDialog(context) },
                        onKeep = {
                            chatViewModel.unbindRoom(
                                credentials!!,
                                conversationUser?.baseUrl!!,
                                currentConversation?.token!!
                            )
                        },
                        onDismiss = {
                            binding.conversationDeleteNoticeComposeView.visibility = View.GONE
                        }
                    )
                }
            }
        }
    }

    fun deleteConversationDialog(context: Context) {
        val dialogBuilder = MaterialAlertDialogBuilder(context)
            .setIcon(
                viewThemeUtils.dialog
                    .colorMaterialAlertDialogIcon(context, R.drawable.ic_delete_black_24dp)
            )
            .setTitle(R.string.nc_delete_call)
            .setMessage(R.string.nc_delete_conversation_more)
            .setPositiveButton(R.string.nc_delete) { _, _ ->
                currentConversation?.let { conversation ->
                    deleteConversation(conversation)
                }
            }
            .setNegativeButton(R.string.nc_cancel) { _, _ ->
            }

        viewThemeUtils.dialog
            .colorMaterialAlertDialogBackground(context, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        )
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    override fun onResume() {
        super.onResume()

        logConversationInfos("onResume")

        pullChatMessagesPending = false

        webSocketInstance?.getSignalingMessageReceiver()?.addListener(localParticipantMessageListener)
        webSocketInstance?.getSignalingMessageReceiver()?.addListener(conversationMessageListener)

        cancelNotificationsForCurrentConversation()

        chatViewModel.getRoom(roomToken)

        binding.let { viewThemeUtils.material.themeFAB(it.voiceRecordingLock) }

        updateToolbarState()
    }

    private fun setupChatToolbarView() {
        binding.chatToolbarComposeView.setContent {
            MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(this@ChatActivity)) {
                CompositionLocalProvider(LocalViewThemeUtils provides viewThemeUtils) {
                    ChatToolbar(
                        state = chatToolbarState,
                        callbacks = ChatToolbarCallbacks(
                            onNavigateUp = { onBackPressedDispatcher.onBackPressed() },
                            onTitleClick = { showConversationInfoScreen() },
                            onVoiceCall = { startACall(true, false) },
                            onSilentVoiceCall = { startACall(true, true) },
                            onVideoCall = { startACall(false, false) },
                            onSilentVideoCall = { startACall(false, true) },
                            onSearchOpen = { startMessageSearch() },
                            onSearchClose = {
                                chatViewModel.exitSearchMode()
                                chatToolbarState = chatToolbarState.copy(isSearchMode = false, searchQuery = "")
                            },
                            onSearchQueryChange = { query ->
                                chatToolbarState = chatToolbarState.copy(searchQuery = query)
                                chatViewModel.onSearchQueryChanged(query)
                            },
                            onSearchSubmit = {
                                chatViewModel.jumpToSearchSelection()
                            },
                            onSearchPrevious = { chatViewModel.selectNextSearchResult() },
                            onSearchNext = { chatViewModel.selectPreviousSearchResult() },
                            onThreadNotificationLevelChange = { level -> setThreadNotificationLevel(level) },
                            onEventMenu = { showConversationEventMenu(binding.chatToolbarComposeView) }
                        )
                    )
                }
            }
        }
    }

    private fun setUpWaveform(message: ChatMessage, thenPlay: Boolean = true, backgroundPlayAllowed: Boolean = false) {
        val filename = message.fileParameters.name
        val file = FileUtils.resolveSharedAttachmentFile(context.cacheDir, filename)
        if (file == null) {
            return
        }
        if (file.exists() && message.voiceMessageFloatArray == null) {
            message.isDownloadingVoiceMessage = true
            chatViewModel.syncVoiceMessageUiState(message)
            CoroutineScope(Dispatchers.Default).launch {
                val r = AudioUtils.audioFileToFloatArray(file)
                appPreferences.saveWaveFormForFile(filename, r.toTypedArray())
                message.voiceMessageFloatArray = r
                withContext(Dispatchers.Main) {
                    message.isDownloadingVoiceMessage = false
                    chatViewModel.syncVoiceMessageUiState(message)
                    startPlayback(file, message)
                }
            }
        } else {
            startPlayback(file, message)
        }
    }

    private fun startPlayback(file: File, message: ChatMessage) {
        chatViewModel.clearMediaPlayerQueue()
        chatViewModel.queueInMediaPlayer(file.canonicalPath, message)
        chatViewModel.startCyclingMediaPlayer()
        message.isPlayingVoiceMessage = true
        chatViewModel.syncVoiceMessageUiState(message)
    }

    private fun updateTypingIndicator() {
        val names = typingParticipants.values.map { it.name }
        runOnUiThread { typingParticipantNames = names }
    }

    private fun isTypingStatusEnabled(): Boolean =
        webSocketInstance != null &&
            !CapabilitiesUtil.isTypingStatusPrivate(conversationUser!!)

    fun updateToolbarState() {
        val conversation = currentConversation
        val user = conversationUser
        val isOneToOne = isOneToOneConversation()
        val capabilitiesReady = ::spreedCapabilities.isInitialized

        chatToolbarState = chatToolbarState.copy(
            title = buildToolbarTitle(conversation),
            subtitle = buildToolbarSubtitle(conversation),
            avatarUrl = buildAvatarUrl(user, conversation, isOneToOne),
            credentials = user?.let { ApiUtils.getCredentials(it.username, it.token) },
            userStatus = if (isOneToOne) conversation?.status else null,
            showVoiceCall = isCallsEnabled(capabilitiesReady, conversation),
            showVideoCall = isCallsEnabled(capabilitiesReady, conversation),
            showSearch = isSearchAvailable(capabilitiesReady, conversation),
            titleClickable = user?.userId != "?" && !chatToolbarState.isSearchMode,
            overflowItems = buildOverflowItems(),
            threadNotificationIcon = buildThreadNotificationIcon(capabilitiesReady),
            showEventMenu = conversation?.objectType == ConversationEnums.ObjectType.EVENT,
            supportsSilentCall = capabilitiesReady &&
                hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.SILENT_CALL) &&
                !isChatThread()
        )
    }

    private fun buildToolbarTitle(conversation: ConversationModel?): String =
        when {
            isChatThread() -> conversationThreadInfo?.thread?.title.orEmpty()
            conversation?.displayName != null -> {
                try {
                    EmojiCompat.get().process(conversation.displayName!! as CharSequence).toString()
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "buildToolbarTitle EmojiCompat processing failed", e)
                    conversation.displayName!!
                }
            }
            else -> ""
        }

    private fun buildAvatarUrl(user: User?, conversation: ConversationModel?, isOneToOne: Boolean): String? =
        if (user != null && conversation != null && isOneToOne) {
            ApiUtils.getUrlForAvatar(user.baseUrl!!, conversation.name, true, DisplayUtils.isDarkModeOn(this))
        } else {
            null
        }

    private fun isCallsEnabled(capabilitiesReady: Boolean, conversation: ConversationModel?): Boolean =
        capabilitiesReady &&
            CapabilitiesUtil.isAbleToCall(spreedCapabilities) &&
            !isChatThread() &&
            !ConversationUtils.isNoteToSelfConversation(conversation) &&
            !isReadOnlyConversation() &&
            !shouldShowLobby()

    private fun isSearchAvailable(capabilitiesReady: Boolean, conversation: ConversationModel?): Boolean =
        capabilitiesReady &&
            networkMonitor.isOnline.value &&
            hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.UNIFIED_SEARCH) &&
            conversation?.remoteServer.isNullOrEmpty() == true &&
            !isChatThread()

    private fun buildThreadNotificationIcon(capabilitiesReady: Boolean): Int? {
        if (!isChatThread() ||
            !capabilitiesReady ||
            !hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.THREADS)
        ) {
            return null
        }
        return when (conversationThreadInfo?.attendee?.notificationLevel) {
            NOTIFICATION_LEVEL_ALWAYS -> R.drawable.outline_notifications_active_24
            NOTIFICATION_LEVEL_NEVER -> R.drawable.ic_baseline_notifications_off_24
            else -> R.drawable.baseline_notifications_24
        }
    }

    private fun buildToolbarSubtitle(conversation: ConversationModel?): String =
        when {
            isChatThread() -> {
                val count = conversationThreadInfo?.thread?.numReplies ?: 0
                resources.getQuantityString(R.plurals.thread_replies, count, count)
            }
            conversation?.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL -> {
                val icon = conversation.statusIcon.orEmpty()
                val msg = conversation.statusMessage.orEmpty()
                "$icon$msg"
            }
            conversation?.type == ConversationEnums.ConversationType.ROOM_GROUP_CALL ||
                conversation?.type == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL ->
                conversation.description.orEmpty()
            else -> ""
        }

    private fun buildOverflowItems(): List<MenuItemData> {
        val items = mutableListOf<MenuItemData>()
        val isThread = isChatThread()
        val capabilitiesReady = ::spreedCapabilities.isInitialized

        if (conversationUser?.userId != "?" && !isThread) {
            items += MenuItemData(
                title = getString(R.string.nc_conversation_menu_conversation_info),
                onClick = { showConversationInfoScreen() }
            )
        }
        if (!isThread) {
            items += MenuItemData(
                title = getString(R.string.nc_shared_items),
                onClick = { showSharedItems() }
            )
            if (capabilitiesReady && hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.THREADS)) {
                items += MenuItemData(
                    title = getString(R.string.recent_threads),
                    onClick = { openThreadsOverview() }
                )
            }
            if (networkMonitor.isOnline.value && hasScheduledMessages) {
                items += MenuItemData(
                    title = getString(R.string.nc_scheduled_messages),
                    onClick = { openScheduledMessages() }
                )
            }
        }
        if (currentConversation?.objectType == ConversationEnums.ObjectType.FILE) {
            items += MenuItemData(
                title = getString(R.string.nc_conversation_menu_conversation_go_to_file),
                onClick = { launchFileShareLink() }
            )
        }
        return items
    }

    fun isOneToOneConversation() =
        currentConversation != null &&
            currentConversation?.type != null &&
            currentConversation?.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL

    private fun updateRoomTimerHandler(delay: Long = -1) {
        val delayForRecursiveCall = if (shouldShowLobby()) {
            GET_ROOM_INFO_DELAY_LOBBY
        } else {
            GET_ROOM_INFO_DELAY_NORMAL
        }

        if (getRoomInfoTimerHandler == null) {
            getRoomInfoTimerHandler = Handler()
        }
        getRoomInfoTimerHandler?.postDelayed(
            {
                chatViewModel.getRoom(roomToken)
            },
            if (delay > 0) delay else delayForRecursiveCall
        )
    }

    private fun switchToRoom(token: String, startCallAfterRoomSwitch: Boolean, isVoiceOnlyCall: Boolean) {
        if (conversationUser != null) {
            runOnUiThread {
                val toastInfo = if (currentConversation?.objectType == ConversationEnums.ObjectType.ROOM) {
                    context.resources.getString(R.string.switch_to_main_room)
                } else {
                    context.resources.getString(R.string.switch_to_breakout_room)
                }
                // do not replace with snackbar, as it would disappear with the activity switch
                Toast.makeText(
                    context,
                    toastInfo,
                    Toast.LENGTH_LONG
                ).show()
            }

            val bundle = Bundle()
            bundle.putString(KEY_ROOM_TOKEN, token)

            if (startCallAfterRoomSwitch) {
                bundle.putBoolean(KEY_START_CALL_AFTER_ROOM_SWITCH, true)
                bundle.putBoolean(KEY_CALL_VOICE_ONLY, isVoiceOnlyCall)
            }

            leaveRoom {
                val chatIntent = Intent(context, ChatActivity::class.java)
                chatIntent.putExtras(bundle)
                chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(chatIntent)
            }
        }
    }

    // override fun updateMediaPlayerProgressBySlider(message: ChatMessage, progress: Int) {
    //     chatViewModel.seekToMediaPlayer(progress)
    // }
    //
    // override fun registerMessageToObservePlaybackSpeedPreferences(
    //     userId: String,
    //     listener: (speed: PlaybackSpeed) -> Unit
    // ) {
    //     CoroutineScope(Dispatchers.Default).launch {
    //         chatViewModel.voiceMessagePlayBackUIFlow.onEach { speed ->
    //             withContext(Dispatchers.Main) {
    //                 listener(speed)
    //             }
    //         }.collect()
    //     }
    // }

    @SuppressLint("LongLogTag")
    private fun downloadFileToCache(
        message: ChatMessage,
        openWhenDownloaded: Boolean,
        funToCallWhenDownloadSuccessful: (() -> Unit)
    ) {
        message.isDownloadingVoiceMessage = true
        chatViewModel.syncVoiceMessageUiState(message)
        message.openWhenDownloaded = openWhenDownloaded

        val baseUrl = conversationUser.baseUrl
        val userId = conversationUser.userId
        val attachmentFolder = CapabilitiesUtil.getAttachmentFolder(
            conversationUser.capabilities!!
                .spreedCapability!!
        )
        val fileName = message.fileParameters.name
        var fileSize = message.fileParameters.size

        val fileId = message.fileParameters.id
        val path = message.fileParameters.path

        // check if download worker is already running
        val workers = WorkManager.getInstance(
            context
        ).getWorkInfosByTag(fileId!!)
        try {
            for (workInfo in workers.get()) {
                if (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED) {
                    Log.d(TAG, "Download worker for $fileId is already running or scheduled")
                    return
                }
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        }

        val data: Data = Data.Builder()
            .putString(DownloadFileToCacheWorker.KEY_BASE_URL, baseUrl)
            .putString(DownloadFileToCacheWorker.KEY_USER_ID, userId)
            .putString(DownloadFileToCacheWorker.KEY_ATTACHMENT_FOLDER, attachmentFolder)
            .putString(DownloadFileToCacheWorker.KEY_FILE_NAME, fileName)
            .putString(DownloadFileToCacheWorker.KEY_FILE_PATH, path)
            .putLong(DownloadFileToCacheWorker.KEY_FILE_SIZE, fileSize!!)
            .build()

        val downloadWorker: OneTimeWorkRequest = OneTimeWorkRequest.Builder(DownloadFileToCacheWorker::class.java)
            .setInputData(data)
            .addTag(fileId)
            .build()

        WorkManager.getInstance().enqueue(downloadWorker)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(downloadWorker.id)
            .observeForever { workInfo: WorkInfo? ->
                if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
                    funToCallWhenDownloadSuccessful()
                }
            }
    }

    fun isRecordAudioPermissionGranted(): Boolean =
        PermissionChecker.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PERMISSION_GRANTED

    fun requestRecordAudioPermissions() {
        requestPermissions(
            arrayOf(
                Manifest.permission.RECORD_AUDIO
            ),
            REQUEST_RECORD_AUDIO_PERMISSION
        )
    }

    private fun requestCameraPermissions() {
        requestPermissions(
            arrayOf(
                Manifest.permission.CAMERA
            ),
            REQUEST_CAMERA_PERMISSION
        )
    }

    private fun requestReadContacts() {
        requestPermissions(
            arrayOf(
                Manifest.permission.READ_CONTACTS
            ),
            REQUEST_READ_CONTACT_PERMISSION
        )
    }

    private fun requestReadFilesPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO
                ),
                REQUEST_SHARE_FILE_PERMISSION
            )
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ),
                REQUEST_SHARE_FILE_PERMISSION
            )
        }
    }

    private fun checkShowCallButtons() {
        updateToolbarState()
    }

    private fun checkShowMessageInputView() {
        if (isReadOnlyConversation() ||
            participantPermissionsFlow.value?.hasChatPermission() == false
        ) {
            binding.fragmentContainerActivityChat.visibility = View.GONE
        } else {
            binding.fragmentContainerActivityChat.visibility = View.VISIBLE
        }
    }

    private fun shouldShowLobby(): Boolean {
        if (currentConversation != null) {
            return hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.WEBINARY_LOBBY) &&
                currentConversation?.lobbyState == ConversationEnums.LobbyState.LOBBY_STATE_MODERATORS_ONLY &&
                !ConversationUtils.canModerate(currentConversation!!, spreedCapabilities) &&
                participantPermissionsFlow.value?.canIgnoreLobby() != true
        }
        return false
    }

    private fun isEventConversation() {
        updateToolbarState()
    }

    private fun isReadOnlyConversation(): Boolean =
        currentConversation?.conversationReadOnlyState != null &&
            currentConversation?.conversationReadOnlyState ==
            ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_ONLY

    private fun setupTypingIndicatorView() {
        binding.typingIndicatorComposeView.setContent {
            MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(this@ChatActivity)) {
                TypingIndicatorBanner(names = typingParticipantNames)
            }
        }
    }

    private fun setupChatEmptyStateView() {
        binding.chatEmptyStateComposeView.setContent {
            val type by chatEmptyStateType
            type?.let { ChatEmptyState(it) }
        }
    }

    private fun showLobbyView() {
        binding.chatEmptyStateComposeView.visibility = View.VISIBLE
        binding.fragmentContainerActivityChat.visibility = View.GONE
        binding.typingIndicatorComposeView.visibility = View.GONE

        val sb = StringBuilder()
        sb.append(resources!!.getText(R.string.nc_lobby_waiting))
            .append("\n\n")

        if (currentConversation?.lobbyTimer != null &&
            currentConversation?.lobbyTimer !=
            0L
        ) {
            val timestampMS = (currentConversation?.lobbyTimer ?: 0) * DateConstants.SECOND_DIVIDER
            val stringWithStartDate = String.format(
                resources!!.getString(R.string.nc_lobby_start_date),
                dateUtils.getLocalDateTimeStringFromTimestamp(timestampMS)
            )
            val relativeTime = dateUtils.relativeStartTimeForLobby(timestampMS, resources!!)

            sb.append("$stringWithStartDate - $relativeTime")
                .append("\n\n")
        }

        sb.append(currentConversation!!.description)
        chatEmptyStateType.value = ChatEmptyStateType.Lobby(sb.toString())
    }

    private fun onRemoteFileBrowsingResult(intent: Intent?) {
        val pathList = intent?.getStringArrayListExtra(RemoteFileBrowserActivity.EXTRA_SELECTED_PATHS)
        if (pathList?.size!! >= 1) {
            pathList
                .chunked(CHUNK_SIZE)
                .forEach { paths ->
                    val data = Data.Builder()
                        .putLong(KEY_INTERNAL_USER_ID, conversationUser!!.id!!)
                        .putString(KEY_ROOM_TOKEN, roomToken)
                        .putStringArray(KEY_FILE_PATHS, paths.toTypedArray())
                        .build()
                    val worker = OneTimeWorkRequest.Builder(ShareOperationWorker::class.java)
                        .setInputData(data)
                        .build()
                    WorkManager.getInstance().enqueue(worker)
                }
        }
    }

    private fun onChooseFileResult(intent: Intent?) {
        try {
            checkNotNull(intent)
            val fileUris = mutableListOf<Uri>()
            intent.clipData?.let {
                for (index in 0 until it.itemCount) {
                    fileUris.add(it.getItemAt(index).uri)
                }
            } ?: run {
                checkNotNull(intent.data)
                intent.data.let {
                    fileUris.add(intent.data!!)
                }
            }
            onChooseFileResult(fileUris)
        } catch (e: IllegalStateException) {
            context.resources?.getString(R.string.nc_upload_failed)?.let {
                Snackbar.make(
                    binding.root,
                    it,
                    Snackbar.LENGTH_LONG
                ).show()
            }
            Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
        }
    }

    private fun onChooseFileResult(filesToUpload: List<Uri>) {
        try {
            require(filesToUpload.isNotEmpty())

            val filenamesWithLineBreaks = StringBuilder("\n")

            for (file in filesToUpload) {
                val filename = FileUtils.getFileName(file, context)
                filenamesWithLineBreaks.append(filename).append("\n")
            }

            val newFragment = FileAttachmentPreviewFragment.newInstance(
                filenamesWithLineBreaks.toString(),
                filesToUpload.map { it.toString() }.toMutableList()
            )
            newFragment.setListener { files, caption ->
                uploadFiles(files, caption)
            }
            newFragment.show(supportFragmentManager, FileAttachmentPreviewFragment.TAG)
        } catch (e: IllegalStateException) {
            context.resources?.getString(R.string.nc_upload_failed)?.let {
                Snackbar.make(
                    binding.root,
                    it,
                    Snackbar.LENGTH_LONG
                ).show()
            }
            Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
        } catch (e: IllegalArgumentException) {
            context.resources?.getString(R.string.nc_upload_failed)?.let {
                Snackbar.make(
                    binding.root,
                    it,
                    Snackbar.LENGTH_LONG
                ).show()
            }
            Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
        }
    }

    private fun onSelectContactResult(intent: Intent?) {
        val contactUri = intent?.data ?: return
        val cursor: Cursor? = contentResolver!!.query(contactUri, null, null, null, null)

        if (cursor != null && cursor.moveToFirst()) {
            val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
            val fileName = ContactUtils.getDisplayNameFromDeviceContact(context, id) + ".vcf"
            val file = FileUtils.resolveSharedAttachmentFile(context.cacheDir, fileName)
            if (file == null) {
                Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                cursor.close()
                return
            }
            writeContactToVcfFile(cursor, file)

            val shareUri = FileProvider.getUriForFile(
                this,
                BuildConfig.APPLICATION_ID,
                File(file.absolutePath)
            )
            uploadFile(
                fileUri = shareUri.toString(),
                isVoiceMessage = false,
                caption = "",
                roomToken = roomToken,
                replyToMessageId = getReplyToMessageId(),
                displayName = currentConversation?.displayName ?: ""
            )
        }
        cursor?.close()
    }

    fun getReplyToMessageId(): Int {
        var replyMessageId = messageInputViewModel.getReplyChatMessage.value?.jsonMessageId
        if (replyMessageId == null || replyMessageId == 0) {
            replyMessageId = conversationThreadInfo?.thread?.id ?: 0
        }
        return replyMessageId
    }

    @Throws(IllegalStateException::class)
    private fun onPickCameraResult(intent: Intent?) {
        try {
            filesToUpload.clear()

            if (intent != null && intent.data != null) {
                run {
                    intent.data.let {
                        filesToUpload.add(intent.data.toString())
                    }
                }
                require(filesToUpload.isNotEmpty())
            } else if (videoURI != null) {
                filesToUpload.add(videoURI.toString())
                videoURI = null
            } else {
                error("Failed to get data from intent and uri")
            }

            if (permissionUtil.isFilesPermissionGranted()) {
                val filenamesWithLineBreaks = StringBuilder("\n")

                for (file in filesToUpload) {
                    val filename = FileUtils.getFileName(file.toUri(), context)
                    filenamesWithLineBreaks.append(filename).append("\n")
                }

                val newFragment = FileAttachmentPreviewFragment.newInstance(
                    filenamesWithLineBreaks.toString(),
                    filesToUpload
                )
                newFragment.setListener { files, caption -> uploadFiles(files, caption) }
                newFragment.show(supportFragmentManager, FileAttachmentPreviewFragment.TAG)
            } else {
                UploadAndShareFilesWorker.requestStoragePermission(this)
            }
        } catch (e: IllegalStateException) {
            Snackbar.make(
                binding.root,
                R.string.nc_upload_failed,
                Snackbar.LENGTH_LONG
            )
                .show()
            Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
        } catch (e: IllegalArgumentException) {
            context.resources?.getString(R.string.nc_upload_failed)?.let {
                Snackbar.make(
                    binding.root,
                    it,
                    Snackbar.LENGTH_LONG
                )
                    .show()
            }
            Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
        }
    }

    private fun executeIfResultOk(result: ActivityResult, onResult: (intent: Intent?) -> Unit) {
        if (result.resultCode == RESULT_OK) {
            onResult(result.data)
        } else {
            Log.e(TAG, "resultCode for received intent was != ok")
        }
    }

    private fun writeContactToVcfFile(cursor: Cursor, file: File) {
        val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)

        val fd: AssetFileDescriptor = contentResolver!!.openAssetFileDescriptor(uri, "r")!!
        fd.use {
            val fis = fd.createInputStream()

            file.createNewFile()
            fis.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == UploadAndShareFilesWorker.REQUEST_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "upload starting after permissions were granted")
                if (filesToUpload.isNotEmpty()) {
                    uploadFiles(filesToUpload)
                }
            } else {
                Snackbar
                    .make(binding.root, context.getString(R.string.read_storage_no_permission), Snackbar.LENGTH_LONG)
                    .show()
            }
        } else if (requestCode == REQUEST_SHARE_FILE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showLocalFilePicker()
            } else {
                Snackbar.make(
                    binding.root,
                    context.getString(R.string.nc_file_storage_permission),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        } else if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // do nothing. user will tap on the microphone again if he wants to record audio..
            } else {
                Snackbar.make(
                    binding.root,
                    context.getString(R.string.nc_voice_message_missing_audio_permission),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        } else if (requestCode == REQUEST_READ_CONTACT_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                startSelectContactForResult.launch(intent)
            } else {
                Snackbar.make(
                    binding.root,
                    context.getString(R.string.nc_share_contact_permission),
                    Snackbar.LENGTH_LONG
                ).show()
            }
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Snackbar
                    .make(binding.root, context.getString(R.string.camera_permission_granted), Snackbar.LENGTH_LONG)
                    .show()
            } else {
                Snackbar
                    .make(binding.root, context.getString(R.string.take_photo_permission), Snackbar.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun uploadFiles(files: MutableList<String>, caption: String = "") {
        for (i in 0 until files.size) {
            if (i == files.size - 1) {
                uploadFile(
                    fileUri = files[i],
                    isVoiceMessage = false,
                    caption = caption,
                    roomToken = roomToken,
                    replyToMessageId = getReplyToMessageId(),
                    displayName = currentConversation?.displayName!!
                )
            } else {
                uploadFile(
                    fileUri = files[i],
                    isVoiceMessage = false,
                    caption = "",
                    roomToken = roomToken,
                    replyToMessageId = getReplyToMessageId(),
                    displayName = currentConversation?.displayName!!
                )
            }
        }
    }

    fun showGalleryPicker() {
        pickMultipleMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
    }

    private fun showLocalFilePicker() {
        val action = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startChooseFileIntentForResult.launch(
            Intent.createChooser(
                action,
                context.resources?.getString(
                    R.string.nc_upload_choose_local_files
                )
            )
        )
    }

    fun sendSelectLocalFileIntent() {
        if (!permissionUtil.isFilesPermissionGranted()) {
            requestReadFilesPermissions()
        } else {
            showLocalFilePicker()
        }
    }

    fun sendChooseContactIntent() {
        requestReadContacts()
    }

    fun showBrowserScreen() {
        val sharingFileBrowserIntent = Intent(this, RemoteFileBrowserActivity::class.java)
        startRemoteFileBrowsingForResult.launch(sharingFileBrowserIntent)
    }

    fun showShareLocationScreen() {
        Log.d(TAG, "showShareLocationScreen")

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!isGpsEnabled) {
            showLocationServicesDisabledDialog()
        } else if (!permissionUtil.isLocationPermissionGranted()) {
            showLocationPermissionDeniedDialog()
        }

        if (permissionUtil.isLocationPermissionGranted() && isGpsEnabled) {
            val intent = Intent(this, LocationPickerActivity::class.java)
            intent.putExtra(KEY_ROOM_TOKEN, roomToken)
            intent.putExtra(BundleKeys.KEY_CHAT_API_VERSION, chatApiVersion)
            startActivity(intent)
        }
    }

    private fun showLocationServicesDisabledDialog() {
        val title = resources.getString(R.string.location_services_disabled)
        val explanation = resources.getString(R.string.location_services_disabled_msg)
        val positive = resources.getString(R.string.nc_permissions_settings)
        val cancel = resources.getString(R.string.nc_cancel)
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(explanation)
            .setPositiveButton(positive) { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton(cancel, null)

        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        )
    }

    private fun showLocationPermissionDeniedDialog() {
        val title = resources.getString(R.string.location_permission_denied)
        val explanation = resources.getString(R.string.location_permission_denied_msg)
        val positive = resources.getString(R.string.nc_permissions_settings)
        val cancel = resources.getString(R.string.nc_cancel)
        val dialogBuilder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(explanation)
            .setPositiveButton(positive) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton(cancel, null)

        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(this, dialogBuilder)
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        )
    }

    private fun showConversationInfoScreen() {
        val bundle = Bundle()

        bundle.putString(KEY_ROOM_TOKEN, roomToken)
        bundle.putBoolean(BundleKeys.KEY_ROOM_ONE_TO_ONE, isOneToOneConversation())

        val upcomingEvent =
            (chatViewModel.upcomingEventViewState.value as? ChatViewModel.UpcomingEventUIState.Success)?.event
        if (upcomingEvent != null) {
            bundle.putParcelable(BundleKeys.KEY_UPCOMING_EVENT, upcomingEvent)
        }

        val intent = Intent(this, ConversationInfoActivity::class.java)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    private fun validSessionId(): Boolean =
        currentConversation != null &&
            sessionIdAfterRoomJoined?.isNotEmpty() == true &&
            sessionIdAfterRoomJoined != "0"

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun cancelNotificationsForCurrentConversation() {
        if (conversationUser != null) {
            if (!TextUtils.isEmpty(roomToken)) {
                try {
                    NotificationUtils.cancelExistingNotificationsForRoom(
                        applicationContext,
                        conversationUser!!,
                        roomToken
                    )
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Cancel notifications for current conversation results with an error.", e)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        logConversationInfos("onPause")

        eventBus.unregister(this)

        webSocketInstance?.getSignalingMessageReceiver()?.removeListener(localParticipantMessageListener)
        webSocketInstance?.getSignalingMessageReceiver()?.removeListener(conversationMessageListener)

        findViewById<View>(R.id.toolbar)?.setOnClickListener(null)

        if (getRoomInfoTimerHandler != null) {
            getRoomInfoTimerHandler?.removeCallbacksAndMessages(null)
        }

        if (conversationUser != null && isActivityNotChangingConfigurations() && isNotInCall()) {
            if (isLeavingRoom) {
                Log.d(TAG, "not leaving room (leave already in progress)")
            } else if (validSessionId()) {
                leaveRoom(null)
            } else {
                Log.d(TAG, "not leaving room (validSessionId is false)")
                ApplicationWideCurrentRoomHolder.getInstance().clear()
            }
        } else {
            Log.d(TAG, "not leaving room...")
        }

        if (mentionAutocomplete != null && mentionAutocomplete!!.isPopupShowing) {
            mentionAutocomplete?.dismissPopup()
        }

        // Updating remote last-read in onPause has a race condition with conversation loading.
        // for conversation list. It may or may not include info about the sent last read message...
        // -> save this field offline in conversation. when getting new conversations, do not overwrite
        // lastReadMessage if offline has higher value
        updateRemoteLastReadMessageIfNeeded()
    }

    private fun advanceLocalLastReadMessageIfNeeded(messageId: Int) {
        chatViewModel.advanceLocalLastReadMessageIfNeeded(messageId)
    }

    private fun updateRemoteLastReadMessageIfNeeded() {
        if (this::spreedCapabilities.isInitialized) {
            spreedCapabilities?.let {
                val url = ApiUtils.getUrlForChatReadMarker(
                    ApiUtils.getChatApiVersion(it, intArrayOf(ApiUtils.API_V1)),
                    conversationUser.baseUrl!!,
                    roomToken
                )

                chatViewModel.updateRemoteLastReadMessageIfNeeded(
                    credentials = credentials!!,
                    url = url
                )
            }
        }
    }

    private fun isActivityNotChangingConfigurations(): Boolean = !isChangingConfigurations

    private fun isNotInCall(): Boolean =
        !ApplicationWideCurrentRoomHolder.getInstance().isInCall &&
            !ApplicationWideCurrentRoomHolder.getInstance().isDialing

    private fun updateToolbarForSearchMode(isSearchMode: Boolean) {
        chatToolbarState = chatToolbarState.copy(
            isSearchMode = isSearchMode,
            titleClickable = !isSearchMode && conversationUser?.userId != "?"
        )
    }

    public override fun onDestroy() {
        super.onDestroy()
        logConversationInfos("onDestroy")

        chatViewModel.leaveRoomViewState.removeObserver(leaveRoomObserver)

        findViewById<View>(R.id.toolbar)?.setOnClickListener(null)

        if (actionBar != null) {
            actionBar?.setIcon(null)
        }

        disposables.dispose()
    }

    private fun joinRoomWithPassword() {
        // if ApplicationWideCurrentRoomHolder contains a session (because a call is active), then keep the sessionId
        if (ApplicationWideCurrentRoomHolder.getInstance().currentRoomToken ==
            currentConversation!!.token
        ) {
            sessionIdAfterRoomJoined = ApplicationWideCurrentRoomHolder.getInstance().session

            ApplicationWideCurrentRoomHolder.getInstance().currentRoomToken = roomToken
            ApplicationWideCurrentRoomHolder.getInstance().userInRoom = conversationUser
        }

        if (!validSessionId()) {
            Log.d(TAG, "sessionID was not valid -> joinRoom")
            val startNanoTime = System.nanoTime()
            Log.d(TAG, "joinRoomWithPassword - joinRoom - calling: $startNanoTime")

            chatViewModel.joinRoom(conversationUser!!, roomToken, roomPassword)
        } else {
            Log.d(TAG, "sessionID was valid -> skip joinRoom")

            setupWebsocket()
        }
    }

    fun leaveRoom(funToCallWhenLeaveSuccessful: (() -> Unit)?) {
        logConversationInfos("leaveRoom")
        isLeavingRoom = true

        var apiVersion = 1
        // FIXME Fix API checking with guests?
        if (conversationUser != null) {
            apiVersion = ApiUtils.getConversationApiVersion(conversationUser!!, intArrayOf(ApiUtils.API_V4, 1))
        }

        val startNanoTime = System.nanoTime()
        Log.d(TAG, "leaveRoom - leaveRoom - calling: $startNanoTime")
        chatViewModel.leaveRoom(
            credentials!!,
            ApiUtils.getUrlForParticipantsActive(
                apiVersion,
                conversationUser?.baseUrl!!,
                roomToken
            ),
            funToCallWhenLeaveSuccessful
        )
    }

    private fun setupWebsocket() {
        if (currentConversation == null || conversationUser == null) {
            Log.e(TAG, "setupWebsocket: currentConversation or conversationUser is null")
            return
        }

        if (currentConversation!!.remoteServer?.isNotEmpty() == true) {
            val apiVersion = ApiUtils.getSignalingApiVersion(conversationUser!!, intArrayOf(ApiUtils.API_V3, 2, 1))
            ncApi.getSignalingSettings(
                credentials,
                ApiUtils.getUrlForSignalingSettings(apiVersion, conversationUser!!.baseUrl, roomToken)
            )
                .subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(object : Observer<SignalingSettingsOverall> {
                    override fun onSubscribe(d: Disposable) {
                        disposables.add(d)
                    }

                    override fun onNext(signalingSettingsOverall: SignalingSettingsOverall) {
                        if (signalingSettingsOverall.ocs!!.settings!!.externalSignalingServer == null ||
                            signalingSettingsOverall.ocs!!.settings!!.externalSignalingServer?.isEmpty() == true
                        ) {
                            return
                        }

                        externalSignalingServer = ExternalSignalingServer()
                        externalSignalingServer!!.externalSignalingServer = signalingSettingsOverall.ocs!!.settings!!
                            .externalSignalingServer
                        externalSignalingServer!!.externalSignalingTicket = signalingSettingsOverall.ocs!!.settings!!
                            .externalSignalingTicket
                        externalSignalingServer!!.federation = signalingSettingsOverall.ocs!!.settings!!.federation

                        webSocketInstance = WebSocketConnectionHelper.getExternalSignalingInstanceForServer(
                            externalSignalingServer!!.externalSignalingServer,
                            conversationUser,
                            externalSignalingServer!!.externalSignalingTicket,
                            TextUtils.isEmpty(credentials)
                        )

                        if (webSocketInstance != null) {
                            webSocketInstance?.joinRoomWithRoomTokenAndSession(
                                roomToken,
                                sessionIdAfterRoomJoined,
                                externalSignalingServer?.federation
                            )
                        }

                        signalingMessageSender = webSocketInstance?.signalingMessageSender
                        webSocketInstance?.getSignalingMessageReceiver()?.addListener(localParticipantMessageListener)
                        webSocketInstance?.getSignalingMessageReceiver()?.addListener(conversationMessageListener)
                    }

                    override fun onError(e: Throwable) {
                        Log.e(TAG, e.message, e)
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        } else {
            webSocketInstance = WebSocketConnectionHelper.getWebSocketInstanceForUser(conversationUser!!)

            if (webSocketInstance != null) {
                webSocketInstance?.joinRoomWithRoomTokenAndSession(
                    roomToken,
                    sessionIdAfterRoomJoined,
                    null
                )

                signalingMessageSender = webSocketInstance?.signalingMessageSender
                webSocketInstance?.getSignalingMessageReceiver()?.addListener(localParticipantMessageListener)
                webSocketInstance?.getSignalingMessageReceiver()?.addListener(conversationMessageListener)
            } else {
                Log.d(TAG, "webSocketInstance not set up. This is only expected when not using the HPB")
            }
        }
    }

    // this is triggered too often when scrolling. Must be made sure it's triggered only once.
    private fun loadMoreMessages(messageId: Int, direction: ChatViewModel.LoadMoreDirection) {
        chatViewModel.loadMoreMessages(messageId, direction)
    }

    private fun launchFileShareLink() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = (conversationUser.baseUrl + "/f/" + currentConversation?.objectId).toUri()
        }
        startActivity(intent)
    }

    private fun openScheduledMessages() {
        val intent = Intent(this, ScheduledMessagesActivity::class.java).apply {
            putExtra(ScheduledMessagesActivity.ROOM_TOKEN, roomToken)
            putExtra(ScheduledMessagesActivity.CONVERSATION_NAME, currentConversation?.displayName.orEmpty())
            if (conversationThreadId != null && conversationThreadId!! > 0) {
                putExtra(ScheduledMessagesActivity.THREAD_ID, conversationThreadId)
                putExtra(ScheduledMessagesActivity.THREAD_TITLE, conversationThreadInfo?.thread?.title.orEmpty())
            }
        }
        startActivity(intent)
    }

    fun showScheduleMessageDialog(
        message: String,
        sendWithoutNotification: Boolean,
        replyToMessageId: Int,
        threadTitle: String?
    ) {
        val shouldDismiss = mutableStateOf(false)
        binding.genericComposeView.setContent {
            ScheduleMessageCompose(
                initialMessage = message,
                viewThemeUtils = viewThemeUtils,
                onDismiss = { shouldDismiss.value = true },
                onSchedule = { scheduledAt, sendWithoutNotification ->
                    val sendAt = scheduledAt.toInt()
                    messageInputViewModel.scheduleChatMessage(
                        credentials = conversationUser!!.getCredentials(),
                        url = ApiUtils.getUrlForScheduledMessages(
                            conversationUser!!.baseUrl!!,
                            roomToken
                        ),
                        message = message,
                        replyTo = replyToMessageId,
                        sendWithoutNotification = sendWithoutNotification,
                        threadTitle = threadTitle,
                        threadId = conversationThreadId,
                        sendAt = sendAt
                    )
                },
                defaultSendWithoutNotification = sendWithoutNotification
            ).GetScheduleDialog(shouldDismiss, this@ChatActivity)
        }
    }

    fun showScheduledMessagesFromInput() {
        openScheduledMessages()
    }

    private fun refreshScheduledMessages() {
        if (!this::spreedCapabilities.isInitialized) {
            return
        }
        val scheduledMessagesUrl = if (isChatThread()) {
            ApiUtils.getUrlForScheduledMessages(
                conversationUser.baseUrl!!,
                roomToken
            ) + "?threadId=${conversationThreadId ?: 0L}"
        } else {
            ApiUtils.getUrlForScheduledMessages(
                conversationUser.baseUrl!!,
                roomToken
            )
        }
        chatViewModel.loadScheduledMessages(
            conversationUser.getCredentials(),
            scheduledMessagesUrl
        )
    }

    private fun setThreadNotificationLevel(level: Int) {
        val threadNotificationUrl = ApiUtils.getUrlForThreadNotificationLevel(
            version = 1,
            baseUrl = conversationUser!!.baseUrl,
            token = roomToken,
            threadId = conversationThreadId!!.toInt()
        )
        chatViewModel.setThreadNotificationLevel(credentials!!, threadNotificationUrl, level)
    }

    @SuppressLint("InflateParams")
    private fun showConversationEventMenu(anchorView: View) {
        val popupView = layoutInflater.inflate(R.layout.item_event_schedule, null)

        val subtitleTextView = popupView.findViewById<TextView>(R.id.meetingTime)

        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true
        popupWindow.showAsDropDown(anchorView, 0, -anchorView.height)

        val meetingStatus = showEventSchedule()
        subtitleTextView.text = meetingStatus

        deleteEventConversation(meetingStatus, popupWindow, popupView)
        archiveEventConversation(meetingStatus, popupWindow, popupView)
    }

    private fun deleteEventConversation(meetingStatus: String, popupWindow: PopupWindow, popupView: View) {
        val deleteConversation = popupView.findViewById<TextView>(R.id.delete_conversation)
        if (meetingStatus == context.resources.getString(R.string.nc_meeting_ended) &&
            currentConversation?.canDeleteConversation == true
        ) {
            deleteConversation.visibility = View.VISIBLE

            deleteConversation.setOnClickListener {
                deleteConversationDialog(it.context)
                popupWindow.dismiss()
            }
        } else {
            deleteConversation.visibility = View.GONE
        }
    }

    private fun archiveEventConversation(meetingStatus: String, popupWindow: PopupWindow, popupView: View) {
        val archiveConversation = popupView.findViewById<TextView>(R.id.archive_conversation)
        val unarchiveConversation = popupView.findViewById<TextView>(R.id.unarchive_conversation)
        if (meetingStatus == context.resources.getString(R.string.nc_meeting_ended) &&
            (
                Participant.ParticipantType.MODERATOR == currentConversation?.participantType ||
                    Participant.ParticipantType.OWNER == currentConversation?.participantType
                )
        ) {
            if (currentConversation?.hasArchived == false) {
                unarchiveConversation.visibility = View.GONE
                archiveConversation.visibility = View.VISIBLE
                archiveConversation.setOnClickListener {
                    this.lifecycleScope.launch {
                        conversationInfoViewModel.archiveConversation(conversationUser!!, currentConversation?.token!!)
                        Snackbar.make(
                            binding.root,
                            String.format(
                                context.resources.getString(R.string.archived_conversation),
                                currentConversation?.displayName
                            ),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    popupWindow.dismiss()
                }
            } else {
                unarchiveConversation.visibility = View.VISIBLE
                archiveConversation.visibility = View.GONE
                unarchiveConversation.setOnClickListener {
                    this.lifecycleScope.launch {
                        conversationInfoViewModel.unarchiveConversation(
                            conversationUser!!,
                            currentConversation?.token!!
                        )
                        Snackbar.make(
                            binding.root,
                            String.format(
                                context.resources.getString(R.string.unarchived_conversation),
                                currentConversation?.displayName
                            ),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    popupWindow.dismiss()
                }
            }
        } else {
            archiveConversation.visibility = View.GONE
            unarchiveConversation.visibility = View.GONE
        }
    }

    private fun deleteConversation(conversation: ConversationModel) {
        val data = Data.Builder()
        data.putLong(
            KEY_INTERNAL_USER_ID,
            conversationUser?.id!!
        )
        data.putString(KEY_ROOM_TOKEN, conversation.token)

        val deleteConversationWorker =
            OneTimeWorkRequest.Builder(DeleteConversationWorker::class.java).setInputData(data.build()).build()
        WorkManager.getInstance().enqueue(deleteConversationWorker)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(deleteConversationWorker.id)
            .observeForever { workInfo: WorkInfo? ->
                if (workInfo != null) {
                    when (workInfo.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            val successMessage = String.format(
                                context.resources.getString(R.string.deleted_conversation),
                                conversation.displayName
                            )
                            Snackbar.make(binding.root, successMessage, Snackbar.LENGTH_LONG).show()
                            finish()
                        }

                        WorkInfo.State.FAILED -> {
                            val errorMessage = context.resources.getString(R.string.nc_common_error_sorry)
                            Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
                        }

                        else -> {
                        }
                    }
                }
            }
    }

    private fun showEventSchedule(): String {
        val meetingTimeStamp = currentConversation?.objectId ?: ""
        val status = getMeetingSchedule(meetingTimeStamp)
        return status
    }

    private fun getMeetingSchedule(meetingTimeStamp: String): String {
        val timestamps = meetingTimeStamp.split("#")
        if (timestamps.size != 2) return context.resources.getString(R.string.nc_invalid_time)

        val startEpoch = timestamps[ZERO_INDEX].toLong()
        val endEpoch = timestamps[ONE_INDEX].toLong()

        val startDateTime = Instant.ofEpochSecond(startEpoch).atZone(ZoneId.systemDefault())
        val endDateTime = Instant.ofEpochSecond(endEpoch).atZone(ZoneId.systemDefault())
        val currentTime = ZonedDateTime.now(ZoneId.systemDefault())

        return when {
            currentTime.isBefore(startDateTime) -> {
                DateUtils(context).getStringForMeetingStartDateTime(startDateTime, currentTime)
            }

            currentTime.isAfter(endDateTime) -> context.resources.getString(R.string.nc_meeting_ended)
            else -> context.resources.getString(R.string.nc_ongoing_meeting)
        }
    }

    private fun showSharedItems() {
        val intent = Intent(this, SharedItemsActivity::class.java)
        intent.putExtra(KEY_CONVERSATION_NAME, currentConversation?.displayName)
        intent.putExtra(KEY_ROOM_TOKEN, roomToken)
        intent.putExtra(
            SharedItemsActivity.KEY_USER_IS_OWNER_OR_MODERATOR,
            ConversationUtils.isParticipantOwnerOrModerator(currentConversation!!)
        )
        intent.putExtra(
            SharedItemsActivity.KEY_IS_ONE_2_ONE,
            currentConversation?.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        )
        startActivity(intent)
    }

    private fun startMessageSearch() {
        chatViewModel.enterSearchMode()
        chatToolbarState = chatToolbarState.copy(
            isSearchMode = true,
            searchQuery = chatViewModel.searchUiState.value.query
        )
    }

    private fun updateSearchLoadingIndicator(isLoading: Boolean) {
        chatToolbarState = chatToolbarState.copy(isLoading = isLoading)
    }

    private fun logSearchNavigation(message: String) {
        if (!BuildConfig.DEBUG) {
            return
        }
        Log.d(TAG, "search-nav: $message")
    }

    private fun startACall(isVoiceOnlyCall: Boolean, callWithoutNotification: Boolean) {
        currentConversation?.let {
            if (conversationUser != null) {
                val pp = ParticipantPermissions(spreedCapabilities, it)
                if (!pp.canStartCall() && currentConversation?.hasCall == false) {
                    Snackbar.make(binding.root, R.string.startCallForbidden, Snackbar.LENGTH_LONG).show()
                } else {
                    ApplicationWideCurrentRoomHolder.getInstance().isDialing = true
                    val callIntent = getIntentForCall(isVoiceOnlyCall, callWithoutNotification)
                    if (callIntent != null) {
                        startActivity(callIntent)
                    }
                }
            }
        }
    }

    private fun getIntentForCall(isVoiceOnlyCall: Boolean, callWithoutNotification: Boolean): Intent? {
        currentConversation?.let {
            val bundle = Bundle()
            bundle.putString(KEY_ROOM_TOKEN, roomToken)
            bundle.putString(BundleKeys.KEY_CONVERSATION_PASSWORD, roomPassword)
            bundle.putString(BundleKeys.KEY_MODIFIED_BASE_URL, conversationUser?.baseUrl!!)
            bundle.putString(KEY_CONVERSATION_NAME, it.displayName)
            bundle.putInt(KEY_RECORDING_STATE, it.callRecording)
            bundle.putBoolean(KEY_IS_MODERATOR, ConversationUtils.isParticipantOwnerOrModerator(it))
            bundle.putBoolean(
                BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO,
                participantPermissionsFlow.value?.canPublishAudio() == true
            )
            bundle.putBoolean(BundleKeys.KEY_ROOM_ONE_TO_ONE, isOneToOneConversation())
            bundle.putBoolean(
                BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO,
                participantPermissionsFlow.value?.canPublishVideo() == true
            )

            if (isVoiceOnlyCall) {
                bundle.putBoolean(KEY_CALL_VOICE_ONLY, true)
            }
            if (callWithoutNotification) {
                bundle.putBoolean(BundleKeys.KEY_CALL_WITHOUT_NOTIFICATION, true)
            }

            if (it.objectType == ConversationEnums.ObjectType.ROOM) {
                bundle.putBoolean(KEY_IS_BREAKOUT_ROOM, true)
            }

            val callIntent = Intent(this, CallActivity::class.java)
            callIntent.putExtras(bundle)
            return callIntent
        } ?: run {
            return null
        }
    }

    fun onClickReaction(chatMessage: ChatMessage, emoji: String) {
        if (participantPermissionsFlow.value?.hasReactPermission() != true) {
            Snackbar.make(binding.root, R.string.reaction_forbidden, Snackbar.LENGTH_LONG).show()
            return
        }
        VibrationUtils.vibrateShort(context)
        if (chatMessage.reactionsSelf?.contains(emoji) == true) {
            chatViewModel.deleteReaction(roomToken, chatMessage, emoji)
        } else {
            chatViewModel.addReaction(roomToken, chatMessage, emoji)
        }
    }

    fun openThread(chatMessage: ChatMessage) {
        openThread(chatMessage.jsonMessageId.toLong())
    }

    fun onMessageClick(message: ChatMessage) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastMessageClickTime < ViewConfiguration.getDoubleTapTimeout() &&
            message.jsonMessageId?.equals(lastMessageId) == true
        ) {
            openMessageActionsDialog(message)
            lastMessageClickTime = 0L
            lastMessageId = 0
        } else {
            lastMessageClickTime = now
            lastMessageId = message.jsonMessageId
        }
    }

    // override fun onPreviewMessageLongClick(chatMessage: ChatMessage) {
    //     onOpenMessageActionsDialog(chatMessage)
    // }

    // just a temporary helper class to get ChatMessage by id. Should be improved after migrationto Compose
    private fun openMessageActionsDialog(messageId: Int) {
        this.lifecycleScope.launch {
            val chatMessage = chatViewModel.getMessageById(messageId.toLong()).first()
            openMessageActionsDialog(chatMessage)
        }
    }

    private fun handleSwipeToReply(messageId: Int) {
        lifecycleScope.launch {
            val chatMessage = chatViewModel.getMessageById(messageId.toLong()).first()
            if (chatMessage.isThread && conversationThreadId == null) {
                openThread(chatMessage)
            } else {
                messageInputViewModel.reply(chatMessage)
            }
        }
    }

    private fun openMessageActionsDialog(message: ChatMessage) {
        if (message.isTemporary || (hasVisibleItems(message) && !isSystemMessage(message))) {
            chatViewModel.showMessageActions(message.jsonMessageId.toLong())
        }
    }

    private fun isSystemMessage(message: ChatMessage): Boolean =
        ChatMessage.MessageType.SYSTEM_MESSAGE == message.getCalculateMessageType()

    fun deleteMessage(message: ChatMessage) {
        if (participantPermissionsFlow.value?.hasChatPermission() != true) {
            Log.w(
                TAG,
                "Deletion of message is skipped because of restrictions by permissions. " +
                    "This method should not have been called!"
            )
            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
        } else {
            var apiVersion = 1
            // FIXME Fix API checking with guests?
            if (conversationUser != null) {
                apiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))
            }

            chatViewModel.deleteChatMessages(
                credentials!!,
                ApiUtils.getUrlForChatMessage(
                    apiVersion,
                    conversationUser.baseUrl!!,
                    roomToken,
                    message.jsonMessageId.toString()
                ),
                message.jsonMessageId
            )
        }
    }

    fun replyPrivately(message: ChatMessage?) {
        val apiVersion =
            ApiUtils.getConversationApiVersion(conversationUser!!, intArrayOf(ApiUtils.API_V4, 1))
        val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            version = apiVersion,
            baseUrl = conversationUser?.baseUrl!!,
            roomType = "1",
            invite = message?.actorId
        )
        chatViewModel.createRoom(
            credentials!!,
            retrofitBucket.url!!,
            retrofitBucket.queryMap!!
        )
    }

    fun forwardMessage(message: ChatMessage?) {
        val bundle = Bundle()
        bundle.putBoolean(BundleKeys.KEY_FORWARD_MSG_FLAG, true)
        bundle.putString(BundleKeys.KEY_FORWARD_MSG_TEXT, message?.message)
        bundle.putString(BundleKeys.KEY_FORWARD_HIDE_SOURCE_ROOM, roomToken)

        val intent = Intent(this, ConversationsListActivity::class.java)
        intent.putExtras(bundle)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    fun remindMeLater(message: ChatMessage?) {
        Log.d(TAG, "remindMeLater called")

        val chatApiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(ApiUtils.API_V1, 1))

        val bundle = bundleOf()
        bundle.putString(KEY_ROOM_TOKEN, roomToken)
        bundle.putInt(BundleKeys.KEY_MESSAGE_ID, message!!.jsonMessageId)
        bundle.putInt(BundleKeys.KEY_CHAT_API_VERSION, chatApiVersion)

        binding.genericComposeView.apply {
            val shouldDismiss = mutableStateOf(false)
            setContent {
                DateTimeCompose(
                    bundle,
                    chatViewModel
                ).GetDateTimeDialog(shouldDismiss, this@ChatActivity)
            }
        }
    }

    fun hidePinnedMessage(message: ChatMessage) {
        val url = ApiUtils.getUrlForChatMessageHiding(
            version = chatApiVersion,
            baseUrl = conversationUser.baseUrl,
            token = roomToken,
            messageId = message.jsonMessageId.toString()
        )
        chatViewModel.hidePinnedMessage(credentials!!, url)
    }

    fun pinMessage(message: ChatMessage) {
        val url = ApiUtils.getUrlForChatMessagePinning(
            version = chatApiVersion,
            baseUrl = conversationUser?.baseUrl,
            token = roomToken,
            messageId = message.jsonMessageId.toString()
        )
        binding.genericComposeView.apply {
            val shouldDismiss = mutableStateOf(false)
            setContent {
                GetPinnedOptionsDialog(shouldDismiss, context, viewThemeUtils) { zonedDateTime ->
                    zonedDateTime?.let {
                        chatViewModel.pinMessage(credentials!!, url, pinUntil = zonedDateTime.toEpochSecond().toInt())
                    } ?: chatViewModel.pinMessage(credentials!!, url)

                    shouldDismiss.value = true
                }
            }
        }
    }

    fun unPinMessage(message: ChatMessage) {
        val url = ApiUtils.getUrlForChatMessagePinning(
            version = chatApiVersion,
            baseUrl = conversationUser.baseUrl,
            token = roomToken,
            messageId = message.jsonMessageId.toString()
        )
        chatViewModel.unPinMessage(credentials!!, url)
    }

    private fun markAsRead(messageId: Int) {
        chatViewModel.setChatReadMessage(
            credentials!!,
            ApiUtils.getUrlForChatReadMarker(
                ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(ApiUtils.API_V1)),
                conversationUser?.baseUrl!!,
                roomToken
            ),
            messageId
        )
    }

    fun markAsUnread(chatMessage: ChatMessage) {
        val items = chatViewModel.uiState.value.items
        val selectedIndex = items.indexOfFirst {
            (it as? ChatViewModel.ChatItem.MessageItem)?.uiMessage?.id == chatMessage.jsonMessageId
        }
        val lastReadMessage = if (selectedIndex in 0 until items.size - 1) {
            (selectedIndex + 1 until items.size)
                .firstNotNullOfOrNull { (items[it] as? ChatViewModel.ChatItem.MessageItem)?.uiMessage?.id }
                ?: 0
        } else {
            0
        }
        chatViewModel.setChatReadMessage(
            credentials!!,
            ApiUtils.getUrlForChatReadMarker(
                ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(ApiUtils.API_V1)),
                conversationUser.baseUrl!!,
                roomToken
            ),
            lastReadMessage
        )
    }

    fun copyMessage(message: ChatMessage?) {
        val clipboardManager =
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(
            resources?.getString(R.string.nc_app_product_name),
            message?.getRichText()
        )
        clipboardManager.setPrimaryClip(clipData)
    }

    fun translateMessage(message: ChatMessage?) {
        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_TRANSLATE_MESSAGE, message?.getRichText())

        val intent = Intent(this, TranslateActivity::class.java)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    fun share(message: ChatMessage) {
        val sharedFile = FileUtils.resolveSharedAttachmentFile(applicationContext.cacheDir, message.fileParameters.name)
        if (sharedFile == null) {
            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
            return
        }
        path = sharedFile.absolutePath
        val shareUri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID,
            sharedFile
        )

        val shareIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, shareUri)
            type = Mimetype.IMAGE_PREFIX_GENERIC
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, resources.getText(R.string.send_to)))
    }

    fun checkIfSharable(message: ChatMessage) {
        val file = FileUtils.resolveSharedAttachmentFile(context.cacheDir, message.fileParameters.name)
        if (file == null) {
            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
            return
        }
        path = file.absolutePath
        if (file.exists()) {
            share(message)
        } else {
            downloadFileToCache(message, false) {
                share(message)
            }
        }
    }

    private fun showSaveToStorageWarning(message: ChatMessage) {
        val saveFragment: DialogFragment = SaveToStorageDialogFragment.newInstance(
            message.fileParameters.name
        )
        saveFragment.show(
            supportFragmentManager,
            SaveToStorageDialogFragment.TAG
        )
    }

    fun checkIfSaveable(message: ChatMessage) {
        val file = FileUtils.resolveSharedAttachmentFile(context.cacheDir, message.fileParameters.name)
        if (file == null) {
            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
            return
        }
        path = file.absolutePath
        if (file.exists()) {
            showSaveToStorageWarning(message)
        } else {
            downloadFileToCache(message, false) {
                showSaveToStorageWarning(message)
            }
        }
    }

    fun shareToNotes(message: ChatMessage) {
        val apiVersion = ApiUtils.getConversationApiVersion(
            conversationUser!!,
            intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, 1)
        )

        this.lifecycleScope.launch {
            val noteToSelfConversation = chatViewModel.checkForNoteToSelf(
                ApiUtils.getCredentials(conversationUser!!.username, conversationUser!!.token)!!,
                ApiUtils.getUrlForNoteToSelf(
                    apiVersion,
                    conversationUser!!.baseUrl
                )
            )

            if (noteToSelfConversation != null) {
                var shareUri: Uri? = null
                var metaData = ""
                var objectId = ""
                if (message.hasFileAttachment) {
                    val file = FileUtils.resolveSharedAttachmentFile(context.cacheDir, message.fileParameters.name)
                    if (file == null) {
                        Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                        return@launch
                    }
                    path = file.absolutePath
                    shareUri = FileProvider.getUriForFile(
                        context,
                        BuildConfig.APPLICATION_ID,
                        file
                    )

                    grantUriPermission(
                        applicationContext.packageName,
                        shareUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } else if (message.hasGeoLocation) {
                    objectId = message.geoLocationParameters.id!!
                    val name = message.geoLocationParameters.name
                    val lat = message.geoLocationParameters.latitude
                    val lon = message.geoLocationParameters.longitude
                    metaData =
                        "{\"type\":\"geo-location\",\"id\":\"geo:$lat,$lon\",\"latitude\":\"$lat\"," +
                        "\"longitude\":\"$lon\",\"name\":\"$name\"}"
                }

                shareToNotes(shareUri, noteToSelfConversation.token, message, objectId, metaData)
            } else {
                Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun shareToNotes(
        shareUri: Uri?,
        roomToken: String,
        message: ChatMessage,
        objectId: String,
        metaData: String
    ) {
        val type = message.getCalculateMessageType()
        when (type) {
            ChatMessage.MessageType.VOICE_MESSAGE -> {
                uploadFile(
                    shareUri.toString(),
                    true,
                    roomToken = roomToken,
                    caption = "",
                    replyToMessageId = getReplyToMessageId(),
                    displayName = currentConversation?.displayName ?: ""
                )
                showSnackBar(roomToken)
            }

            ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE -> {
                val caption = if (message.message != "{file}") message.message else ""
                if (null != shareUri) {
                    try {
                        context.contentResolver.openInputStream(shareUri)?.close()
                        uploadFile(
                            fileUri = shareUri.toString(),
                            isVoiceMessage = false,
                            caption = caption!!,
                            roomToken = roomToken,
                            replyToMessageId = getReplyToMessageId(),
                            displayName = currentConversation?.displayName ?: ""
                        )
                        showSnackBar(roomToken)
                    } catch (e: Exception) {
                        Log.w(TAG, "File corresponding to the uri does not exist $shareUri", e)
                        downloadFileToCache(message, false) {
                            uploadFile(
                                fileUri = shareUri.toString(),
                                isVoiceMessage = false,
                                caption = caption!!,
                                roomToken = roomToken,
                                replyToMessageId = getReplyToMessageId(),
                                displayName = currentConversation?.displayName ?: ""
                            )
                            showSnackBar(roomToken)
                        }
                    }
                }
            }

            ChatMessage.MessageType.SINGLE_NC_GEOLOCATION_MESSAGE -> {
                val apiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))
                chatViewModel.shareLocationToNotes(
                    credentials!!,
                    ApiUtils.getUrlToSendLocation(apiVersion, conversationUser!!.baseUrl!!, roomToken),
                    "geo-location",
                    objectId,
                    metaData
                )
                showSnackBar(roomToken)
            }

            ChatMessage.MessageType.REGULAR_TEXT_MESSAGE -> {
                val apiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))
                chatViewModel.shareToNotes(
                    credentials!!,
                    ApiUtils.getUrlForChat(apiVersion, conversationUser!!.baseUrl!!, roomToken),
                    message.message!!,
                    conversationUser!!.displayName!!
                )
                showSnackBar(roomToken)
            }

            else -> {}
        }
    }

    fun showSnackBar(roomToken: String) {
        val snackBar = Snackbar.make(binding.root, R.string.nc_message_sent, Snackbar.LENGTH_LONG)
        snackBar.view.setOnClickListener {
            openNoteToSelfConversation(roomToken)
        }
        snackBar.show()
    }

    fun openNoteToSelfConversation(noteToSelfRoomToken: String) {
        val bundle = Bundle()
        bundle.putString(KEY_ROOM_TOKEN, noteToSelfRoomToken)
        val chatIntent = Intent(context, ChatActivity::class.java)
        chatIntent.putExtras(bundle)
        chatIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(chatIntent)
    }

    fun openInFilesApp(message: ChatMessage) {
        val keyID = message.fileParameters.id
        val link = message.fileParameters.link
        val fileViewerUtils = FileViewerUtils(this, message.activeUser!!)
        fileViewerUtils.openFileInFilesApp(link!!, keyID!!)
    }

    private fun hasVisibleItems(message: ChatMessage): Boolean =
        !message.isDeleted ||
            // copy message
            message.replyable ||
            // reply to
            message.replyable &&
            // reply privately
            conversationUser?.userId?.isNotEmpty() == true &&
            conversationUser!!.userId != "?" &&
            message.actorType.equals("users") &&
            message.actorId != currentConversation?.actorId &&
            currentConversation?.type != ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ||
            isShowMessageDeletionButton(message) ||
            // delete
            ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == message.getCalculateMessageType() ||
            // forward
            ChatMessage.MessageType.SYSTEM_MESSAGE != message.getCalculateMessageType()

    private fun isShowMessageDeletionButton(message: ChatMessage): Boolean {
        val isUserAllowedByPrivileges = userAllowedByPrivilages(message)

        val isOlderThanSixHours = message
            .createdAt
            .before(Date(System.currentTimeMillis() - AGE_THRESHOLD_FOR_DELETE_MESSAGE))
        val hasDeleteMessagesUnlimitedCapability = hasSpreedFeatureCapability(
            spreedCapabilities,
            SpreedFeatures.DELETE_MESSAGES_UNLIMITED
        )

        return when {
            !isUserAllowedByPrivileges -> false
            !hasDeleteMessagesUnlimitedCapability && isOlderThanSixHours -> false
            message.systemMessageType != ChatMessage.SystemMessageType.DUMMY -> false
            message.isDeleted -> false
            !hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.DELETE_MESSAGES) -> false
            participantPermissionsFlow.value?.hasChatPermission() != true -> false
            hasDeleteMessagesUnlimitedCapability -> true
            else -> true
        }
    }

    fun userAllowedByPrivilages(message: ChatMessage): Boolean {
        if (conversationUser == null) return false

        val isUserAllowedByPrivileges = if (message.actorId == conversationUser!!.userId) {
            true
        } else {
            ConversationUtils.canModerate(currentConversation!!, spreedCapabilities)
        }
        return isUserAllowedByPrivileges
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(webSocketCommunicationEvent: WebSocketCommunicationEvent) {
        when (webSocketCommunicationEvent.type) {
            "roomUpdated" -> {
                // With HPB, the signaling server sends a "room" message when room properties change
                // (e.g. lobby state) while already in the room. Refresh room details so the DB is
                // updated and observeLobbyState() can react to the new lobby state.
                if (webSocketCommunicationEvent.hashMap?.get(Globals.ROOM_TOKEN) == roomToken) {
                    chatViewModel.getRoom(roomToken)
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(userMentionClickEvent: UserMentionClickEvent) {
        if (currentConversation?.type != ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ||
            currentConversation?.name != userMentionClickEvent.userId
        ) {
            joinOneToOneConversation(userMentionClickEvent.userId)
        }
    }

    fun sendPictureFromCamIntent() {
        if (!permissionUtil.isCameraPermissionGranted()) {
            requestCameraPermissions()
        } else {
            startPickCameraIntentForResult.launch(TakePhotoActivity.createIntent(context))
        }
    }

    fun sendVideoFromCamIntent() {
        if (!permissionUtil.isCameraPermissionGranted()) {
            requestCameraPermissions()
        } else {
            Intent(MediaStore.ACTION_VIDEO_CAPTURE).also { takeVideoIntent ->
                takeVideoIntent.resolveActivity(packageManager)?.also {
                    val videoFile: File? = try {
                        val outputDir = FileUtils.getSharedAttachmentsDirectory(context.cacheDir)
                            ?: throw IOException("Could not create shared attachments directory")
                        val dateFormat = SimpleDateFormat(FILE_DATE_PATTERN, Locale.ROOT)
                        val date = dateFormat.format(Date())
                        val videoName = String.format(
                            context.resources.getString(R.string.nc_video_filename),
                            date
                        )
                        File(outputDir, "$videoName$VIDEO_SUFFIX")
                    } catch (e: IOException) {
                        Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                        Log.e(TAG, "error while creating video file", e)
                        null
                    }

                    videoFile?.also {
                        videoURI = FileProvider.getUriForFile(context, context.packageName, it)
                        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoURI)
                        startPickCameraIntentForResult.launch(takeVideoIntent)
                    }
                }
            }
        }
    }

    fun createPoll() {
        val pollVoteDialog = PollCreateDialogFragment.newInstance(
            roomToken
        )
        pollVoteDialog.show(supportFragmentManager, TAG)
    }

    fun createThread() {
        messageInputViewModel.startThreadCreation()
    }

    private fun isChatThread(): Boolean = conversationThreadId != null && conversationThreadId!! > 0

    fun openThread(messageId: Long) {
        val bundle = Bundle()
        bundle.putString(KEY_ROOM_TOKEN, roomToken)
        bundle.putLong(KEY_THREAD_ID, messageId)
        val chatIntent = Intent(context, ChatActivity::class.java)
        chatIntent.putExtras(bundle)
        startActivity(chatIntent)
    }

    fun openThreadsOverview() {
        val threadsUrl = ApiUtils.getUrlForRecentThreads(
            version = 1,
            baseUrl = conversationUser!!.baseUrl,
            token = roomToken
        )

        val bundle = Bundle()
        bundle.putString(KEY_ROOM_TOKEN, roomToken)
        bundle.putString(ThreadsOverviewActivity.KEY_APPBAR_TITLE, getString(R.string.recent_threads))
        bundle.putString(ThreadsOverviewActivity.KEY_THREADS_SOURCE_URL, threadsUrl)

        val threadsOverviewIntent = Intent(context, ThreadsOverviewActivity::class.java)
        threadsOverviewIntent.putExtras(bundle)
        startActivity(threadsOverviewIntent)
    }

    override fun joinAudioCall() {
        startACall(true, false)
    }

    override fun joinVideoCall() {
        startACall(false, false)
    }

    private fun logConversationInfos(methodName: String) {
        Log.d(TAG, " |-----------------------------------------------")
        Log.d(TAG, " | method: $methodName")
        Log.d(TAG, " | ChatActivity: " + System.identityHashCode(this).toString())
        Log.d(TAG, " | roomToken: $roomToken")
        Log.d(TAG, " | currentConversation?.displayName: ${currentConversation?.displayName}")
        Log.d(TAG, " | sessionIdAfterRoomJoined: $sessionIdAfterRoomJoined")
        Log.d(TAG, " |-----------------------------------------------")
    }

    fun shareMessageText(message: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, getString(R.string.share))
        startActivity(shareIntent)
    }

    fun joinOneToOneConversation(userId: String) {
        val apiVersion =
            ApiUtils.getConversationApiVersion(conversationUser!!, intArrayOf(ApiUtils.API_V4, 1))
        val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            version = apiVersion,
            baseUrl = conversationUser?.baseUrl!!,
            roomType = ROOM_TYPE_ONE_TO_ONE,
            source = ACTOR_TYPE,
            invite = userId
        )
        chatViewModel.createRoom(
            credentials!!,
            retrofitBucket.url!!,
            retrofitBucket.queryMap!!
        )
    }

    fun uploadFile(
        fileUri: String,
        isVoiceMessage: Boolean,
        caption: String = "",
        roomToken: String = "",
        replyToMessageId: Int? = null,
        displayName: String
    ) {
        chatViewModel.uploadFile(
            fileUri,
            isVoiceMessage,
            caption,
            roomToken,
            replyToMessageId,
            displayName
        )
        cancelReply()
    }

    fun cancelReply() {
        messageInputViewModel.reply(null)
        chatViewModel.messageDraft.quotedMessageText = null
        chatViewModel.messageDraft.quotedDisplayName = null
        chatViewModel.messageDraft.quotedImageUrl = null
        chatViewModel.messageDraft.quotedJsonId = null
    }

    fun cancelCreateThread() {
        chatViewModel.clearThreadTitle()
    }

    companion object {
        val TAG = ChatActivity::class.simpleName
        private const val CONTENT_TYPE_CALL_STARTED: Byte = 1
        private const val CONTENT_TYPE_SYSTEM_MESSAGE: Byte = 2
        private const val CONTENT_TYPE_UNREAD_NOTICE_MESSAGE: Byte = 3
        private const val CONTENT_TYPE_LOCATION: Byte = 4
        private const val CONTENT_TYPE_VOICE_MESSAGE: Byte = 5
        private const val CONTENT_TYPE_POLL: Byte = 6
        private const val CONTENT_TYPE_LINK_PREVIEW: Byte = 7
        private const val CONTENT_TYPE_DECK_CARD: Byte = 8
        private const val UNREAD_MESSAGES_MARKER_ID = -1
        private const val GET_ROOM_INFO_DELAY_NORMAL: Long = 30000
        private const val GET_ROOM_INFO_DELAY_LOBBY: Long = 5000
        private const val MILLIS_250 = 250L
        private const val AGE_THRESHOLD_FOR_DELETE_MESSAGE: Int = 21600000 // (6 hours in millis = 6 * 3600 * 1000)
        private const val REQUEST_SHARE_FILE_PERMISSION: Int = 221
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 222
        private const val REQUEST_READ_CONTACT_PERMISSION = 234
        private const val REQUEST_CAMERA_PERMISSION = 223
        private const val FILE_DATE_PATTERN = "yyyy-MM-dd HH-mm-ss"
        private const val VIDEO_SUFFIX = ".mp4"
        private const val VOICE_MESSAGE_SEEKBAR_BASE = 1000
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val MESSAGE_PULL_LIMIT = 100
        private const val INVITE_LENGTH = 6
        private const val ACTOR_LENGTH = 6
        private const val CHUNK_SIZE: Int = 10
        private const val NOTIFICATION_LEVEL_ALWAYS = 1
        private const val NOTIFICATION_LEVEL_MENTION_AND_CALLS = 2
        private const val NOTIFICATION_LEVEL_NEVER = 3
        private const val ONE_SECOND_IN_MILLIS = 1000
        private const val MILLISEC_15: Long = 15
        private const val CURRENT_AUDIO_MESSAGE_KEY = "CURRENT_AUDIO_MESSAGE"
        private const val CURRENT_AUDIO_POSITION_KEY = "CURRENT_AUDIO_POSITION"
        private const val CURRENT_AUDIO_WAS_PLAYING_KEY = "CURRENT_AUDIO_PLAYING"
        private const val RESUME_AUDIO_TAG = "RESUME_AUDIO_TAG"
        private const val FIVE_MINUTES_IN_SECONDS: Long = 300
        private const val ROOM_TYPE_ONE_TO_ONE = "1"
        private const val ACTOR_TYPE = "users"
        const val CONVERSATION_INTERNAL_ID = "CONVERSATION_INTERNAL_ID"
        const val NO_OFFLINE_MESSAGES_FOUND = "NO_OFFLINE_MESSAGES_FOUND"
        private const val NO_MORE_RESULTS_TOAST_THROTTLE_MS: Long = 2000
        const val VOICE_MESSAGE_CONTINUOUS_BEFORE = -5
        const val VOICE_MESSAGE_CONTINUOUS_AFTER = 5
        const val VOICE_MESSAGE_PLAY_ADD_THRESHOLD = 0.1
        const val VOICE_MESSAGE_MARK_PLAYED_FACTOR = 20
        const val OUT_OF_OFFICE_ALPHA = 76
        const val ZERO_INDEX = 0
        const val ONE_INDEX = 1
        const val MAX_AMOUNT_MEDIA_FILE_PICKER = 10
        private const val SEARCH_PENDING_SCROLL_RETRY_MAX = 20
        private const val SEARCH_PENDING_SCROLL_RETRY_DELAY_MS = 250L
        private const val SEARCH_CENTER_TOLERANCE_PX = 2f
        private const val SEARCH_CENTER_STABILIZE_ATTEMPTS = 8
        private const val SEARCH_CENTER_STABILIZE_DELAY_MS = 200L
    }
}
