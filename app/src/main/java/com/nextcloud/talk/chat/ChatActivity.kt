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
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
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
import androidx.appcompat.view.ContextThemeWrapper
import androidx.cardview.widget.CardView
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.text.bold
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
import coil.imageLoader
import coil.load
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.android.common.ui.color.ColorUtil
import com.nextcloud.android.common.ui.theme.utils.ColorRole
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
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.chat.viewmodels.MessageInputViewModel
import com.nextcloud.talk.conversationinfo.ConversationInfoActivity
import com.nextcloud.talk.conversationinfo.viewmodel.ConversationInfoViewModel
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.dagger.modules.ViewModelFactoryWithParams
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
import com.nextcloud.talk.messagesearch.MessageSearchActivity
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.domain.ConversationModel
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
import com.nextcloud.talk.ui.PinnedMessageView
import com.nextcloud.talk.ui.PlaybackSpeed
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.chat.ChatView
import com.nextcloud.talk.ui.chat.ChatMessageCallbacks
import com.nextcloud.talk.ui.chat.ChatViewCallbacks
import com.nextcloud.talk.ui.chat.ChatViewState
import com.nextcloud.talk.ui.dialog.DateTimeCompose
import com.nextcloud.talk.ui.dialog.FileAttachmentPreviewFragment
import com.nextcloud.talk.ui.dialog.GetPinnedOptionsDialog
import com.nextcloud.talk.ui.dialog.MessageActionsDialog
import com.nextcloud.talk.ui.dialog.SaveToStorageDialogFragment
import com.nextcloud.talk.chat.ui.ShowReactionsModalBottomSheet
import com.nextcloud.talk.ui.dialog.TempMessageActionsDialog
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import kotlin.math.roundToInt

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

    private var chatMenu: Menu? = null

    private var scheduledMessagesMenuItem: MenuItem? = null
    private var hasScheduledMessages: Boolean = false

    private var overflowMenuHostView: ComposeView? = null
    private var isThreadMenuExpanded by mutableStateOf(false)

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

    private val startMessageSearchForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            executeIfResultOk(it) { intent ->
                runBlocking {
                    val messageId = intent?.getStringExtra(MessageSearchActivity.RESULT_KEY_MESSAGE_ID)
                    val threadId = intent?.getStringExtra(MessageSearchActivity.RESULT_KEY_THREAD_ID)
                    messageId?.let {
                        // Message search jump handling is pending for this path.
                    }
                }
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

    private var conversationVoiceCallMenuItem: MenuItem? = null
    private var conversationVideoMenuItem: MenuItem? = null
    private var eventConversationMenuItem: MenuItem? = null

    var webSocketInstance: WebSocketInstance? = null
    var signalingMessageSender: SignalingMessageSender? = null
    var externalSignalingServer: ExternalSignalingServer? = null

    var getRoomInfoTimerHandler: Handler? = null

    private val filesToUpload: MutableList<String> = ArrayList()
    lateinit var sharedText: String

    lateinit var participantPermissions: ParticipantPermissions

    private var videoURI: Uri? = null

    private lateinit var pickMultipleMedia: ActivityResultLauncher<PickVisualMediaRequest>

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!openedViaNotification && isChatThread()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            } else {
                val intent = Intent(this@ChatActivity, ConversationsListActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private lateinit var messageInputFragment: MessageInputFragment

    val typingParticipants = HashMap<String, TypingParticipant>()

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

            Log.d(
                TAG,
                "received message in ChatActivity. This is the chat message received via HPB. It would be " +
                    "nicer to receive it in the ViewModel or Repository directly. " +
                    "Otherwise it needs to be passed into it from here..."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)

        binding.offline.root.visibility = View.GONE

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

    private fun setPinnedMessageContent() {
        binding.pinnedMessageComposeView.setContent {
            MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(this@ChatActivity)) {
                val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
                val pinnedMessage = uiState.pinnedMessage
                binding.pinnedMessageContainer.visibility = if (pinnedMessage != null) View.VISIBLE else View.GONE
                if (pinnedMessage != null) {
                    PinnedMessageView(
                        message = pinnedMessage,
                        viewThemeUtils = viewThemeUtils,
                        currentConversation = uiState.conversation,
                        scrollToMessageWithIdWithOffset = { messageId ->
                            scrollToMessageById(messageId.toLong())
                        },
                        hidePinnedMessage = ::hidePinnedMessage,
                        unPinMessage = ::unPinMessage
                    )
                }
            }
        }
    }

    private var chatListState: LazyListState? = null

    private fun scrollToMessageById(messageId: Long) {
        val items = chatViewModel.uiState.value.items
        val targetIndex = items.indexOfFirst { item ->
            (item as? ChatViewModel.ChatItem.MessageItem)?.uiMessage?.id == messageId.toInt()
        }
        if (targetIndex >= 0) {
            lifecycleScope.launch {
                chatListState?.scrollToItem(targetIndex)
            }
        }
    }

    private fun setChatListContent() {
        binding.messagesListViewCompose.setContent {
            MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(this@ChatActivity)) {
                val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
                currentConversation = uiState.conversation

                binding.messagesListViewCompose.visibility = View.VISIBLE

                val listState = rememberLazyListState()
                SideEffect { chatListState = listState }

                CompositionLocalProvider(
                    LocalViewThemeUtils provides viewThemeUtils,
                    LocalMessageUtils provides messageUtils,
                    LocalOpenGraphFetcher provides { url -> chatViewModel.fetchOpenGraph(url) }
                ) {
                    val isOneToOneConversation = uiState.isOneToOneConversation
                    Log.d(TAG, "isOneToOneConversation=" + isOneToOneConversation)

                    ChatView(
                        state = ChatViewState(
                            chatItems = uiState.items,
                            isOneToOneConversation = isOneToOneConversation,
                            conversationThreadId = conversationThreadId,
                            hasChatPermission = this::participantPermissions.isInitialized &&
                                participantPermissions.hasChatPermission()
                        ),
                        callbacks = ChatViewCallbacks(
                            onLoadMore = { loadMoreMessagesCompose() },
                            advanceLocalLastReadMessageIfNeeded = { advanceLocalLastReadMessageIfNeeded(it) },
                            updateRemoteLastReadMessageIfNeeded = { updateRemoteLastReadMessageIfNeeded() },
                            onLoadQuotedMessageClick = { messageId -> onLoadQuotedMessage(messageId) },
                            messageCallbacks = ChatMessageCallbacks(
                                onLongClick = { openMessageActionsDialog(it) },
                                onSwipeReply = { handleSwipeToReply(it) },
                                onFileClick = { downloadAndOpenFile(it) },
                                onPollClick = { pollId, pollName -> openPollDialog(pollId, pollName) },
                                onVoicePlayPauseClick = { onVoicePlayPauseClickCompose(it) },
                                onVoiceSeek = { _, progress -> chatViewModel.seekToMediaPlayer(progress) },
                                onVoiceSpeedClick = { onVoiceSpeedClickCompose(it) },
                                onReactionClick = { messageId, emoji -> handleReactionClick(messageId, emoji) },
                                onReactionLongClick = { messageId -> openReactionsDialog(messageId) },
                                onOpenThreadClick = { messageId -> openThread(messageId.toLong()) },
                                onSystemMessageExpandClick = { messageId ->
                                    chatViewModel.toggleSystemMessageCollapse(messageId)
                                }
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
                            hasReactPermission = participantPermissions.hasReactPermission(),
                            ncApiCoroutines = ncApiCoroutines,
                            onDeleteReaction = { emoji -> chatViewModel.deleteReaction(roomToken, msg, emoji) },
                            onDismiss = { chatViewModel.dismissReactionsSheet() }
                        )
                    }
                }
            }
        }
    }

    private fun onLoadQuotedMessage(messageId: Int) {
        // Loading and displaying surrounding messages for quotes is pending; replace flow from latestChatBlock with
        //  other flow
        Log.d(TAG, "TODO: Load quoted message with id: $messageId")
        Toast.makeText(this, R.string.quoted_message_too_old, Toast.LENGTH_LONG).show()
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

            val file = File(context.cacheDir, filename)
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

    fun downloadAndOpenFile(messageId: Int) {
        lifecycleScope.launch {
            val chatMessage = chatViewModel.getMessageById(messageId.toLong()).first()
            FileViewerUtils(this@ChatActivity, conversationUser).openFile(chatMessage)
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
        Log.d(TAG, "initObservers Called")

        chatViewModel.getCapabilitiesViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.GetCapabilitiesUpdateState -> {
                    if (currentConversation != null) {
                        spreedCapabilities = state.spreedCapabilities
                        chatApiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))
                        participantPermissions = ParticipantPermissions(spreedCapabilities, currentConversation!!)

                        invalidateOptionsMenu()
                        isEventConversation()
                        checkShowCallButtons()
                        checkLobbyState()
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
                    participantPermissions = ParticipantPermissions(spreedCapabilities, state.conversationModel!!)

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

                    if (conversationUser?.userId != "?" &&
                        hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.MENTION_FLAG) &&
                        !isChatThread()
                    ) {
                        binding.chatToolbar.setOnClickListener { _ -> showConversationInfoScreen() }
                    }
                    refreshScheduledMessages()

                    loadAvatarForStatusBar()
                    setActionBarTitle()
                    isEventConversation()
                    checkShowCallButtons()
                    checkLobbyState()
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

        chatViewModel.leaveRoomViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.LeaveRoomSuccessState -> {
                    logConversationInfos("leaveRoom#onNext")

                    checkingLobbyStatus = false

                    if (getRoomInfoTimerHandler != null) {
                        getRoomInfoTimerHandler?.removeCallbacksAndMessages(null)
                    }

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
                    invalidateOptionsMenu()
                }

                is ChatViewModel.ScheduledMessagesErrorState -> {
                    hasScheduledMessages = false
                    messageInputFragment.updateScheduledMessagesAvailability(false)
                    invalidateOptionsMenu()
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
                    binding.conversationDeleteNotice.visibility = View.GONE
                    Snackbar.make(
                        binding.root,
                        context.getString(R.string.nc_room_retention),
                        Snackbar.LENGTH_LONG
                    ).show()

                    chatMenu?.removeItem(R.id.conversation_event)
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
                    binding.outOfOfficeContainer.visibility = View.VISIBLE

                    val backgroundColor = colorUtil.getNullSafeColorWithFallbackRes(
                        conversationUser!!.capabilities!!.themingCapability!!.color,
                        R.color.colorPrimary
                    )

                    binding.outOfOfficeContainer.findViewById<View>(
                        R.id.verticalLine
                    ).setBackgroundColor(backgroundColor)
                    val setAlpha = ColorUtils.setAlphaComponent(backgroundColor, OUT_OF_OFFICE_ALPHA)
                    binding.outOfOfficeContainer.setCardBackgroundColor(setAlpha)

                    val startDateTimestamp: Long = uiState.userAbsence.startDate.toLong()
                    val endDateTimestamp: Long = uiState.userAbsence.endDate.toLong()

                    val startDate = Date(startDateTimestamp * ONE_SECOND_IN_MILLIS)
                    val endDate = Date(endDateTimestamp * ONE_SECOND_IN_MILLIS)

                    if (dateUtils.isSameDate(startDate, endDate)) {
                        binding.outOfOfficeContainer.findViewById<TextView>(R.id.userAbsenceShortMessage).text =
                            String.format(
                                context.resources.getString(R.string.user_absence_for_one_day),
                                currentConversation?.displayName
                            )
                        binding.outOfOfficeContainer.findViewById<TextView>(R.id.userAbsencePeriod).visibility =
                            View.GONE
                    } else {
                        val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        val startDateString = dateFormatter.format(startDate)
                        val endDateString = dateFormatter.format(endDate)
                        binding.outOfOfficeContainer.findViewById<TextView>(R.id.userAbsenceShortMessage).text =
                            String.format(
                                context.resources.getString(R.string.user_absence),
                                currentConversation?.displayName
                            )

                        binding.outOfOfficeContainer.findViewById<TextView>(R.id.userAbsencePeriod).text =
                            "$startDateString - $endDateString"
                    }

                    if (uiState.userAbsence.replacementUserDisplayName != null) {
                        val imageUri = ApiUtils.getUrlForAvatar(
                            conversationUser?.baseUrl,
                            uiState.userAbsence.replacementUserId,
                            false,
                            darkMode = DisplayUtils.isDarkModeOn(context)
                        ).toUri()
                        binding.outOfOfficeContainer.findViewById<ImageView>(R.id.replacement_user_avatar)
                            .load(imageUri) {
                                transformations(CircleCropTransformation())
                                placeholder(R.drawable.account_circle_96dp)
                                error(R.drawable.account_circle_96dp)
                                crossfade(true)
                            }
                        binding.outOfOfficeContainer.findViewById<TextView>(R.id.replacement_user_name).text =
                            uiState.userAbsence.replacementUserDisplayName
                    } else {
                        binding.outOfOfficeContainer.findViewById<LinearLayout>(R.id.userAbsenceReplacement)
                            .visibility = View.GONE
                    }
                    binding.outOfOfficeContainer.findViewById<TextView>(R.id.userAbsenceLongMessage).text =
                        uiState.userAbsence.message
                    binding.outOfOfficeContainer.findViewById<CardView>(R.id.avatar_chip).setOnClickListener {
                        joinOneToOneConversation(uiState.userAbsence.replacementUserId!!)
                    }
                }
            }
        }

        chatViewModel.upcomingEventViewState.observe(this) { uiState ->
            when (uiState) {
                is ChatViewModel.UpcomingEventUIState.Success -> {
                    val hiddenEventKey = "${uiState.event.uri}${uiState.event.start}${uiState.event.summary}"
                    if (hiddenEventKey == chatViewModel.hiddenUpcomingEvent) {
                        binding.upcomingEventCard.visibility = View.GONE
                    } else {
                        binding.upcomingEventCard.visibility = View.VISIBLE
                        viewThemeUtils.material.themeCardView(binding.upcomingEventCard)

                        binding.upcomingEventContainer.upcomingEventSummary.text = uiState.event.summary

                        uiState.event.start?.let { start ->
                            val startDateTime = Instant.ofEpochSecond(start).atZone(ZoneId.systemDefault())
                            val currentTime = ZonedDateTime.now(ZoneId.systemDefault())
                            binding.upcomingEventContainer.upcomingEventTime.text =
                                DateUtils(context).getStringForMeetingStartDateTime(startDateTime, currentTime)
                        }

                        binding.upcomingEventContainer.upcomingEventDismiss.setOnClickListener {
                            binding.upcomingEventCard.visibility = View.GONE
                            chatViewModel.saveHiddenUpcomingEvent(hiddenEventKey)
                            Snackbar.make(
                                binding.root,
                                R.string.nc_upcoming_event_dismissed,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                }

                is ChatViewModel.UpcomingEventUIState.Error -> {
                    Log.e(TAG, "Error fetching upcoming events", uiState.exception)
                }

                ChatViewModel.UpcomingEventUIState.None -> {
                    binding.upcomingEventCard.visibility = View.GONE
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
                        invalidateOptionsMenu()
                    }
                }
            }
        }
    }

    private fun removeUnreadMessagesMarker() {
        chatViewModel.setUnreadMessagesMarker(false)
    }

    fun showConversationDeletionWarning(retentionPeriod: Int) {
        binding.conversationDeleteNotice.visibility = View.VISIBLE
        binding.conversationDeleteNotice.apply {
            isClickable = false
            isFocusable = false
            bringToFront()
        }
        val deleteNoticeText = binding.conversationDeleteNotice.findViewById<TextView>(R.id.deletion_message)
        viewThemeUtils.material.themeCardView(binding.conversationDeleteNotice)

        deleteNoticeText.text = resources.getQuantityString(
            R.plurals.nc_conversation_auto_delete_info,
            retentionPeriod,
            retentionPeriod
        )
        viewThemeUtils.material.colorMaterialButtonPrimaryTonal(
            binding.conversationDeleteNotice
                .findViewById<MaterialButton>(R.id.keep_button)
        )

        if (ConversationUtils.isParticipantOwnerOrModerator(currentConversation!!)) {
            binding.conversationDeleteNotice.findViewById<MaterialButton>(R.id.delete_now_button).visibility =
                View.VISIBLE
            binding.conversationDeleteNotice.findViewById<MaterialButton>(R.id.keep_button).visibility = View.VISIBLE
        } else {
            binding.conversationDeleteNotice.findViewById<MaterialButton>(R.id.delete_now_button).visibility =
                View.GONE
            binding.conversationDeleteNotice.findViewById<MaterialButton>(R.id.keep_button).visibility = View.GONE
        }
        binding.conversationDeleteNotice.findViewById<MaterialButton>(R.id.delete_now_button).setOnClickListener {
            deleteConversationDialog(it.context)
        }

        binding.conversationDeleteNotice.findViewById<MaterialButton>(R.id.keep_button).setOnClickListener {
            chatViewModel.unbindRoom(credentials!!, conversationUser?.baseUrl!!, currentConversation?.token!!)
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

        actionBar?.show()

        binding.let { viewThemeUtils.material.themeFAB(it.voiceRecordingLock) }

        loadAvatarForStatusBar()
        setActionBarTitle()
        viewThemeUtils.material.colorToolbarOverflowIcon(binding.chatToolbar)
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.chatToolbar)
        binding.chatToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(resources!!.getColor(R.color.transparent, null).toDrawable())
        setActionBarTitle()
        viewThemeUtils.material.themeToolbar(binding.chatToolbar)
    }

    private fun setUpWaveform(message: ChatMessage, thenPlay: Boolean = true, backgroundPlayAllowed: Boolean = false) {
        val filename = message.fileParameters.name
        val file = File(context.cacheDir, filename!!)
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

    @Suppress("MagicNumber", "LongMethod")
    private fun updateTypingIndicator() {
        fun ellipsize(text: String): String = DisplayUtils.ellipsize(text, TYPING_INDICATOR_MAX_NAME_LENGTH)

        val participantNames = ArrayList<String>()

        for (typingParticipant in typingParticipants.values) {
            participantNames.add(typingParticipant.name)
        }

        val typingString: SpannableStringBuilder
        when (typingParticipants.size) {
            0 -> typingString = SpannableStringBuilder().append(binding.typingIndicator.text)

            // person1 is typing
            1 -> typingString = SpannableStringBuilder()
                .bold { append(ellipsize(participantNames[0])) }
                .append(WHITESPACE + context.resources?.getString(R.string.typing_is_typing))

            // person1 and person2 are typing
            2 -> typingString = SpannableStringBuilder()
                .bold { append(ellipsize(participantNames[0])) }
                .append(WHITESPACE + context.resources?.getString(R.string.nc_common_and) + WHITESPACE)
                .bold { append(ellipsize(participantNames[1])) }
                .append(WHITESPACE + context.resources?.getString(R.string.typing_are_typing))

            // person1, person2 and person3 are typing
            3 -> typingString = SpannableStringBuilder()
                .bold { append(ellipsize(participantNames[0])) }
                .append(COMMA)
                .bold { append(ellipsize(participantNames[1])) }
                .append(WHITESPACE + context.resources?.getString(R.string.nc_common_and) + WHITESPACE)
                .bold { append(ellipsize(participantNames[2])) }
                .append(WHITESPACE + context.resources?.getString(R.string.typing_are_typing))

            // person1, person2, person3 and 1 other is typing
            4 -> typingString = SpannableStringBuilder()
                .bold { append(participantNames[0]) }
                .append(COMMA)
                .bold { append(participantNames[1]) }
                .append(COMMA)
                .bold { append(participantNames[2]) }
                .append(WHITESPACE + context.resources?.getString(R.string.typing_1_other))

            // person1, person2, person3 and x others are typing
            else -> {
                val moreTypersAmount = typingParticipants.size - 3
                val othersTyping = context.resources?.getString(R.string.typing_x_others)?.let {
                    String.format(it, moreTypersAmount)
                }
                typingString = SpannableStringBuilder()
                    .bold { append(participantNames[0]) }
                    .append(COMMA)
                    .bold { append(participantNames[1]) }
                    .append(COMMA)
                    .bold { append(participantNames[2]) }
                    .append(othersTyping)
            }
        }

        runOnUiThread {
            binding.typingIndicator.text = typingString

            val typingIndicatorPositionY = if (participantNames.size > 0) {
                TYPING_INDICATOR_POSITION_VISIBLE
            } else {
                TYPING_INDICATOR_POSITION_HIDDEN
            }

            binding.typingIndicatorWrapper.animate()
                .translationY(DisplayUtils.convertDpToPixel(typingIndicatorPositionY, context))
                .setInterpolator(AccelerateDecelerateInterpolator())
                .duration = TYPING_INDICATOR_ANIMATION_DURATION
        }
    }

    private fun isTypingStatusEnabled(): Boolean =
        webSocketInstance != null &&
            !CapabilitiesUtil.isTypingStatusPrivate(conversationUser!!)

    private fun loadAvatarForStatusBar() {
        if (currentConversation == null) {
            return
        }

        if (isOneToOneConversation()) {
            val url = ApiUtils.getUrlForAvatar(
                conversationUser!!.baseUrl!!,
                currentConversation!!.name,
                true,
                darkMode = DisplayUtils.isDarkModeOn(supportActionBar?.themedContext!!)
            )

            val target = object : Target {

                private fun setIcon(drawable: Drawable?) {
                    supportActionBar?.let {
                        val avatarSize = (it.height / TOOLBAR_AVATAR_RATIO).roundToInt()
                        val size = DisplayUtils.convertDpToPixel(STATUS_SIZE_IN_DP, context)
                        if (drawable != null && avatarSize > 0) {
                            val bitmap = drawable.toBitmap(avatarSize, avatarSize)
                            val status = StatusDrawable(
                                currentConversation!!.status,
                                null,
                                size,
                                0,
                                binding.chatToolbar.context
                            )
                            viewThemeUtils.talk.themeStatusDrawable(context, status)
                            binding.chatToolbar.findViewById<ImageView>(R.id.chat_toolbar_avatar)
                                .setImageDrawable(bitmap.toDrawable(resources))
                            binding.chatToolbar.findViewById<ImageView>(R.id.chat_toolbar_status)
                                .setImageDrawable(status)
                            binding.chatToolbar.findViewById<ImageView>(R.id.chat_toolbar_status).contentDescription =
                                currentConversation?.status
                            binding.chatToolbar.findViewById<FrameLayout>(R.id.chat_toolbar_avatar_container)
                                .visibility = View.VISIBLE
                        } else {
                            Log.d(TAG, "loadAvatarForStatusBar avatarSize <= 0")
                        }
                    }
                }

                override fun onStart(placeholder: Drawable?) {
                    this.setIcon(placeholder)
                }

                override fun onSuccess(result: Drawable) {
                    this.setIcon(result)
                }
            }

            val credentials = ApiUtils.getCredentials(conversationUser!!.username, conversationUser!!.token)
            if (credentials != null) {
                context.imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .addHeader("Authorization", credentials)
                        .transformations(CircleCropTransformation())
                        .crossfade(true)
                        .target(target)
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build()
                )
            }
        } else {
            binding.chatToolbar.findViewById<FrameLayout>(R.id.chat_toolbar_avatar_container).visibility = View.GONE
        }
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

    private fun showCallButtonMenu(isVoiceOnlyCall: Boolean) {
        val anchor: View? = if (isVoiceOnlyCall) {
            findViewById(R.id.conversation_voice_call)
        } else {
            findViewById(R.id.conversation_video_call)
        }

        if (anchor != null) {
            val popupMenu = PopupMenu(
                ContextThemeWrapper(this, R.style.CallButtonMenu),
                anchor,
                Gravity.END
            )
            popupMenu.inflate(R.menu.chat_call_menu)

            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.call_without_notification -> startACall(isVoiceOnlyCall, true)
                }
                true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                popupMenu.setForceShowIcon(true)
            }
            popupMenu.show()
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
        if (isReadOnlyConversation() ||
            shouldShowLobby() ||
            ConversationUtils.isNoteToSelfConversation(currentConversation)
        ) {
            disableCallButtons()
        } else {
            enableCallButtons()
        }
    }

    private fun checkShowMessageInputView() {
        if (isReadOnlyConversation() ||
            shouldShowLobby() ||
            !participantPermissions.hasChatPermission()
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
                !participantPermissions.canIgnoreLobby()
        }
        return false
    }

    private fun disableCallButtons() {
        if (CapabilitiesUtil.isAbleToCall(spreedCapabilities)) {
            if (conversationVoiceCallMenuItem != null && conversationVideoMenuItem != null) {
                conversationVoiceCallMenuItem?.icon?.alpha = SEMI_TRANSPARENT_INT
                conversationVideoMenuItem?.icon?.alpha = SEMI_TRANSPARENT_INT
                conversationVoiceCallMenuItem?.isEnabled = false
                conversationVideoMenuItem?.isEnabled = false
            } else {
                Log.e(TAG, "call buttons were null when trying to disable them")
            }
        }
    }

    private fun enableCallButtons() {
        if (CapabilitiesUtil.isAbleToCall(spreedCapabilities)) {
            if (conversationVoiceCallMenuItem != null && conversationVideoMenuItem != null) {
                conversationVoiceCallMenuItem?.icon?.alpha = FULLY_OPAQUE_INT
                conversationVideoMenuItem?.icon?.alpha = FULLY_OPAQUE_INT
                conversationVoiceCallMenuItem?.isEnabled = true
                conversationVideoMenuItem?.isEnabled = true
            } else {
                Log.e(TAG, "call buttons were null when trying to enable them")
            }
        }
    }

    private fun isEventConversation() {
        if (currentConversation?.objectType == ConversationEnums.ObjectType.EVENT) {
            if (eventConversationMenuItem != null) {
                eventConversationMenuItem?.icon?.alpha = FULLY_OPAQUE_INT
                eventConversationMenuItem?.isEnabled = true
            }
        } else {
            eventConversationMenuItem?.isEnabled = false
        }
    }

    private fun isReadOnlyConversation(): Boolean =
        currentConversation?.conversationReadOnlyState != null &&
            currentConversation?.conversationReadOnlyState ==
            ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_ONLY

    private fun checkLobbyState() {
        if (currentConversation != null &&
            ConversationUtils.isLobbyViewApplicable(currentConversation!!, spreedCapabilities) &&
            shouldShowLobby()
        ) {
            showLobbyView()
        } else {
            binding.lobby.lobbyView.visibility = View.GONE
            // binding.messagesListView.visibility = View.VISIBLE
            checkShowMessageInputView()
        }
    }

    private fun showLobbyView() {
        binding.lobby.lobbyView.visibility = View.VISIBLE
        // binding.messagesListView.visibility = View.GONE
        binding.fragmentContainerActivityChat.visibility = View.GONE

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
        binding.lobby.lobbyTextView.text = sb.toString()
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
            val file = File(context.cacheDir, fileName)
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

        checkingLobbyStatus = false

        if (getRoomInfoTimerHandler != null) {
            getRoomInfoTimerHandler?.removeCallbacksAndMessages(null)
        }

        if (conversationUser != null && isActivityNotChangingConfigurations() && isNotInCall()) {
            ApplicationWideCurrentRoomHolder.getInstance().clear()
            if (validSessionId()) {
                leaveRoom(null)
            } else {
                Log.d(TAG, "not leaving room (validSessionId is false)")
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

    private fun setActionBarTitle() {
        val title = binding.chatToolbar.findViewById<TextView>(R.id.chat_toolbar_title)
        viewThemeUtils.platform.colorTextView(title, ColorRole.ON_SURFACE)

        title.text =
            if (isChatThread()) {
                conversationThreadInfo?.thread?.title
            } else if (currentConversation?.displayName != null) {
                try {
                    EmojiCompat.get().process(currentConversation?.displayName as CharSequence).toString()
                } catch (e: java.lang.IllegalStateException) {
                    Log.e(TAG, "setActionBarTitle failed $e")
                    currentConversation?.displayName
                }
            } else {
                ""
            }

        if (isChatThread()) {
            val replyAmount = conversationThreadInfo?.thread?.numReplies ?: 0
            val repliesAmountTitle = resources.getQuantityString(
                R.plurals.thread_replies,
                replyAmount,
                replyAmount
            )

            statusMessageViewContents(repliesAmountTitle)
        } else if (currentConversation?.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
            var statusMessage = ""
            if (currentConversation?.statusIcon != null) {
                statusMessage += currentConversation?.statusIcon
            }
            if (currentConversation?.statusMessage != null) {
                statusMessage += currentConversation?.statusMessage
            }
            statusMessageViewContents(statusMessage)
        } else {
            if (currentConversation?.type == ConversationEnums.ConversationType.ROOM_GROUP_CALL ||
                currentConversation?.type == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL
            ) {
                var descriptionMessage = ""
                descriptionMessage += currentConversation?.description
                statusMessageViewContents(descriptionMessage)
            }
        }
    }

    private fun statusMessageViewContents(statusMessageContent: String) {
        val statusMessageView = binding.chatToolbar.findViewById<TextView>(R.id.chat_toolbar_status_message)
        if (statusMessageContent.isNotEmpty()) {
            viewThemeUtils.platform.colorTextView(statusMessageView, ColorRole.ON_SURFACE)
            statusMessageView.text = statusMessageContent
            statusMessageView.visibility = View.VISIBLE
        } else {
            statusMessageView.visibility = View.GONE
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        logConversationInfos("onDestroy")

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

    private fun loadMoreMessagesCompose() {
        chatViewModel.loadMoreMessagesCompose()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_conversation, menu)
        chatMenu = menu

        scheduledMessagesMenuItem = menu.findItem(R.id.conversation_scheduled_messages)

        if (currentConversation?.objectType == ConversationEnums.ObjectType.EVENT) {
            eventConversationMenuItem = menu.findItem(R.id.conversation_event)
        } else {
            menu.removeItem(R.id.conversation_event)
        }

        if (conversationUser?.userId == "?") {
            menu.removeItem(R.id.conversation_info)
        } else {
            loadAvatarForStatusBar()
            setActionBarTitle()
        }

        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)

        if (this::spreedCapabilities.isInitialized) {
            if (hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.READ_ONLY_ROOMS)) {
                checkShowCallButtons()
            }

            scheduledMessagesMenuItem?.isVisible = networkMonitor.isOnline.value &&
                hasScheduledMessages &&
                !ConversationUtils.isNoteToSelfConversation(currentConversation)

            val searchItem = menu.findItem(R.id.conversation_search)
            searchItem.isVisible =
                hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.UNIFIED_SEARCH) &&
                currentConversation!!.remoteServer.isNullOrEmpty() &&
                !isChatThread()

            val sharedItemsItem = menu.findItem(R.id.shared_items)
            sharedItemsItem.isVisible = !isChatThread()

            val conversationFileItem = menu.findItem(R.id.conversation_go_to_file)
            conversationFileItem.isVisible = currentConversation?.objectType == ConversationEnums.ObjectType.FILE

            val conversationInfoItem = menu.findItem(R.id.conversation_info)
            conversationInfoItem.isVisible = !isChatThread()

            val showThreadsItem = menu.findItem(R.id.show_threads)
            showThreadsItem.isVisible = !isChatThread() &&
                hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.THREADS)

            if (CapabilitiesUtil.isAbleToCall(spreedCapabilities) &&
                !isChatThread() &&
                !ConversationUtils.isNoteToSelfConversation(currentConversation)
            ) {
                conversationVoiceCallMenuItem = menu.findItem(R.id.conversation_voice_call)
                conversationVideoMenuItem = menu.findItem(R.id.conversation_video_call)

                this.lifecycleScope.launch {
                    networkMonitor.isOnline.onEach { isOnline ->
                        conversationVoiceCallMenuItem?.isVisible = isOnline
                        searchItem?.isVisible = isOnline
                        conversationVideoMenuItem?.isVisible = isOnline
                    }.collect()
                }

                if (hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.SILENT_CALL)) {
                    Handler().post {
                        findViewById<View?>(R.id.conversation_voice_call)?.setOnLongClickListener {
                            showCallButtonMenu(true)
                            true
                        }
                    }

                    Handler().post {
                        findViewById<View?>(R.id.conversation_video_call)?.setOnLongClickListener {
                            showCallButtonMenu(false)
                            true
                        }
                    }
                }
            } else {
                menu.removeItem(R.id.conversation_video_call)
                menu.removeItem(R.id.conversation_voice_call)
            }

            handleThreadNotificationIcon(menu.findItem(R.id.thread_notifications))
        }
        return true
    }

    private fun handleThreadNotificationIcon(threadNotificationItem: MenuItem) {
        threadNotificationItem.isVisible = isChatThread() &&
            hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.THREADS)

        val threadNotificationIcon = when (conversationThreadInfo?.attendee?.notificationLevel) {
            NOTIFICATION_LEVEL_ALWAYS -> R.drawable.outline_notifications_active_24
            NOTIFICATION_LEVEL_NEVER -> R.drawable.ic_baseline_notifications_off_24
            else -> R.drawable.baseline_notifications_24
        }
        threadNotificationItem.icon = ContextCompat.getDrawable(context, threadNotificationIcon)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.conversation_video_call -> {
                startACall(false, false)
                true
            }

            R.id.conversation_voice_call -> {
                startACall(true, false)
                true
            }

            R.id.conversation_go_to_file -> {
                launchFileShareLink()
                true
            }

            R.id.conversation_info -> {
                showConversationInfoScreen()
                true
            }

            R.id.shared_items -> {
                showSharedItems()
                true
            }

            R.id.conversation_search -> {
                startMessageSearch()
                true
            }

            R.id.conversation_scheduled_messages -> {
                openScheduledMessages()
                true
            }

            R.id.conversation_event -> {
                val anchorView = findViewById<View>(R.id.conversation_event)
                showConversationEventMenu(anchorView)
                true
            }

            R.id.show_threads -> {
                openThreadsOverview()
                true
            }

            R.id.thread_notifications -> {
                showThreadNotificationMenu()
                true
            }

            else -> super.onOptionsItemSelected(item)
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

    @Suppress("Detekt.LongMethod")
    private fun showThreadNotificationMenu() {
        fun setThreadNotificationLevel(level: Int) {
            val threadNotificationUrl = ApiUtils.getUrlForThreadNotificationLevel(
                version = 1,
                baseUrl = conversationUser!!.baseUrl,
                token = roomToken,
                threadId = conversationThreadId!!.toInt()
            )
            chatViewModel.setThreadNotificationLevel(credentials!!, threadNotificationUrl, level)
        }

        if (overflowMenuHostView == null) {
            val threadNotificationsAnchor: View? = findViewById(R.id.thread_notifications)

            val colorScheme = viewThemeUtils.getColorScheme(this)

            overflowMenuHostView = ComposeView(this).apply {
                setContent {
                    MaterialTheme(
                        colorScheme = colorScheme
                    ) {
                        val items = listOf(
                            MenuItemData(
                                title = context.resources.getString(R.string.notifications_default),
                                subtitle = context.resources.getString(
                                    R.string.notifications_default_description
                                ),
                                icon = R.drawable.baseline_notifications_24,
                                onClick = {
                                    setThreadNotificationLevel(0)
                                }
                            ),
                            MenuItemData(
                                title = context.resources.getString(R.string.notification_all_messages),
                                subtitle = null,
                                icon = R.drawable.outline_notifications_active_24,
                                onClick = {
                                    setThreadNotificationLevel(NOTIFICATION_LEVEL_ALWAYS)
                                }
                            ),
                            MenuItemData(
                                title = context.resources.getString(R.string.notification_mention_only),
                                subtitle = null,
                                icon = R.drawable.baseline_notifications_24,
                                onClick = {
                                    setThreadNotificationLevel(NOTIFICATION_LEVEL_MENTION_AND_CALLS)
                                }
                            ),
                            MenuItemData(
                                title = context.resources.getString(R.string.notification_off),
                                subtitle = null,
                                icon = R.drawable.ic_baseline_notifications_off_24,
                                onClick = {
                                    setThreadNotificationLevel(NOTIFICATION_LEVEL_NEVER)
                                }
                            )
                        )

                        OverflowMenu(
                            anchor = threadNotificationsAnchor,
                            expanded = isThreadMenuExpanded,
                            items = items,
                            onDismiss = { isThreadMenuExpanded = false }
                        )
                    }
                }
            }

            addContentView(
                overflowMenuHostView,
                CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.MATCH_PARENT,
                    CoordinatorLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        isThreadMenuExpanded = true
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
        val intent = Intent(this, MessageSearchActivity::class.java)
        intent.putExtra(KEY_CONVERSATION_NAME, currentConversation?.displayName)
        intent.putExtra(KEY_ROOM_TOKEN, roomToken)
        startMessageSearchForResult.launch(intent)
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
                participantPermissions.canPublishAudio()
            )
            bundle.putBoolean(BundleKeys.KEY_ROOM_ONE_TO_ONE, isOneToOneConversation())
            bundle.putBoolean(
                BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_VIDEO,
                participantPermissions.canPublishVideo()
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
        if (!participantPermissions.hasReactPermission()) {
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
        if (message.isTemporary) {
            TempMessageActionsDialog(
                this,
                message
            ).show()
        } else if (hasVisibleItems(message) &&
            !isSystemMessage(message)
        ) {
            MessageActionsDialog(
                this,
                message,
                conversationUser,
                currentConversation,
                isShowMessageDeletionButton(message),
                participantPermissions.hasChatPermission(),
                participantPermissions.hasReactPermission(),
                spreedCapabilities
            ).show()
        }
    }

    private fun isSystemMessage(message: ChatMessage): Boolean =
        ChatMessage.MessageType.SYSTEM_MESSAGE == message.getCalculateMessageType()

    fun deleteMessage(message: ChatMessage) {
        if (!participantPermissions.hasChatPermission()) {
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
        startActivity(intent)
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

    fun markAsUnread(chatMessage: ChatMessage?) {
        if (chatMessage!!.previousMessageId > NO_PREVIOUS_MESSAGE_ID) {
            // previousMessageId is taken to mark chat as unread even when "chat-unread" capability is not available
            // It should be checked if "chat-unread" capability is available and then use
            // https://nextcloud-talk.readthedocs.io/en/latest/chat/#mark-chat-as-unread
            chatViewModel.setChatReadMessage(
                credentials!!,
                ApiUtils.getUrlForChatReadMarker(
                    ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(ApiUtils.API_V1)),
                    conversationUser?.baseUrl!!,
                    roomToken
                ),
                chatMessage.previousMessageId
            )
        }
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
        val filename = message.fileParameters.name
        path = applicationContext.cacheDir.absolutePath + "/" + filename
        val shareUri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID,
            File(path)
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
        val filename = message.fileParameters.name
        path = applicationContext.cacheDir.absolutePath + "/" + filename
        val file = File(context.cacheDir, filename!!)
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
        val filename = message.fileParameters.name
        path = applicationContext.cacheDir.absolutePath + "/" + filename
        val file = File(context.cacheDir, filename!!)
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
                val data: HashMap<String, String>?
                var metaData = ""
                var objectId = ""
                if (message.hasFileAttachment) {
                    val filename = message.fileParameters.name
                    path = applicationContext.cacheDir.absolutePath + "/" + filename
                    shareUri = FileProvider.getUriForFile(
                        context,
                        BuildConfig.APPLICATION_ID,
                        File(path)
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
            message.previousMessageId > NO_PREVIOUS_MESSAGE_ID &&
            // mark as unread
            ChatMessage.MessageType.SYSTEM_MESSAGE != message.getCalculateMessageType() &&
            BuildConfig.DEBUG

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
            !participantPermissions.hasChatPermission() -> false
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
        /*
        switch (webSocketCommunicationEvent.getType()) {
            case "refreshChat":

                if (
                webSocketCommunicationEvent
                .getHashMap().get(BundleKeys.KEY_INTERNAL_USER_ID)
                .equals(Long.toString(conversationUser.getId()))
                ) {
                    if (roomToken.equals(webSocketCommunicationEvent.getHashMap().get(BundleKeys.KEY_ROOM_TOKEN))) {
                        pullChatMessages(2);
                    }
                }
                break;
            default:
        }*/
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(userMentionClickEvent: UserMentionClickEvent) {
        if (currentConversation?.type != ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ||
            currentConversation?.name != userMentionClickEvent.userId
        ) {
            var apiVersion = 1
            // FIXME Fix API checking with guests?
            if (conversationUser != null) {
                apiVersion = ApiUtils.getConversationApiVersion(conversationUser!!, intArrayOf(ApiUtils.API_V4, 1))
            }

            val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
                version = apiVersion,
                baseUrl = conversationUser?.baseUrl!!,
                roomType = "1",
                invite = userMentionClickEvent.userId
            )

            chatViewModel.createRoom(
                credentials!!,
                retrofitBucket.url!!,
                retrofitBucket.queryMap!!
            )
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
                        val outputDir = context.cacheDir
                        val dateFormat = SimpleDateFormat(FILE_DATE_PATTERN, Locale.ROOT)
                        val date = dateFormat.format(Date())
                        val videoName = String.format(
                            context.resources.getString(R.string.nc_video_filename),
                            date
                        )
                        File("$outputDir/$videoName$VIDEO_SUFFIX")
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
        private const val FULLY_OPAQUE_INT: Int = 255
        private const val SEMI_TRANSPARENT_INT: Int = 99
        private const val VOICE_MESSAGE_SEEKBAR_BASE = 1000
        private const val NO_PREVIOUS_MESSAGE_ID: Int = -1
        private const val TOOLBAR_AVATAR_RATIO = 1.5
        private const val STATUS_SIZE_IN_DP = 9f
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
        private const val WHITESPACE = " "
        private const val COMMA = ", "
        private const val TYPING_INDICATOR_ANIMATION_DURATION = 200L
        private const val TYPING_INDICATOR_MAX_NAME_LENGTH = 14
        private const val TYPING_INDICATOR_POSITION_VISIBLE = -18f
        private const val TYPING_INDICATOR_POSITION_HIDDEN = -1f
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
        const val VOICE_MESSAGE_CONTINUOUS_BEFORE = -5
        const val VOICE_MESSAGE_CONTINUOUS_AFTER = 5
        const val VOICE_MESSAGE_PLAY_ADD_THRESHOLD = 0.1
        const val VOICE_MESSAGE_MARK_PLAYED_FACTOR = 20
        const val OUT_OF_OFFICE_ALPHA = 76
        const val ZERO_INDEX = 0
        const val ONE_INDEX = 1
        const val MAX_AMOUNT_MEDIA_FILE_PICKER = 10
    }
}
