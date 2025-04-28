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
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.ContactsContract
import android.provider.MediaStore
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.cardview.widget.CardView
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.core.text.bold
import androidx.emoji2.text.EmojiCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.android.common.ui.color.ColorUtil
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.activities.TakePhotoActivity
import com.nextcloud.talk.adapters.messages.CallStartedMessageInterface
import com.nextcloud.talk.adapters.messages.CommonMessageInterface
import com.nextcloud.talk.adapters.messages.IncomingDeckCardViewHolder
import com.nextcloud.talk.adapters.messages.IncomingLinkPreviewMessageViewHolder
import com.nextcloud.talk.adapters.messages.IncomingLocationMessageViewHolder
import com.nextcloud.talk.adapters.messages.IncomingPollMessageViewHolder
import com.nextcloud.talk.adapters.messages.IncomingPreviewMessageViewHolder
import com.nextcloud.talk.adapters.messages.IncomingTextMessageViewHolder
import com.nextcloud.talk.adapters.messages.IncomingVoiceMessageViewHolder
import com.nextcloud.talk.adapters.messages.MessagePayload
import com.nextcloud.talk.adapters.messages.OutcomingDeckCardViewHolder
import com.nextcloud.talk.adapters.messages.OutcomingLinkPreviewMessageViewHolder
import com.nextcloud.talk.adapters.messages.OutcomingLocationMessageViewHolder
import com.nextcloud.talk.adapters.messages.OutcomingPollMessageViewHolder
import com.nextcloud.talk.adapters.messages.OutcomingPreviewMessageViewHolder
import com.nextcloud.talk.adapters.messages.OutcomingTextMessageViewHolder
import com.nextcloud.talk.adapters.messages.OutcomingVoiceMessageViewHolder
import com.nextcloud.talk.adapters.messages.PreviewMessageInterface
import com.nextcloud.talk.adapters.messages.PreviewMessageViewHolder
import com.nextcloud.talk.adapters.messages.SystemMessageInterface
import com.nextcloud.talk.adapters.messages.SystemMessageViewHolder
import com.nextcloud.talk.adapters.messages.TalkMessagesListAdapter
import com.nextcloud.talk.adapters.messages.UnreadNoticeMessageViewHolder
import com.nextcloud.talk.adapters.messages.VoiceMessageInterface
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.chat.viewmodels.MessageInputViewModel
import com.nextcloud.talk.conversationinfo.ConversationInfoActivity
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.ActivityChatBinding
import com.nextcloud.talk.events.UserMentionClickEvent
import com.nextcloud.talk.events.WebSocketCommunicationEvent
import com.nextcloud.talk.extensions.loadAvatarOrImagePreview
import com.nextcloud.talk.jobs.DownloadFileToCacheWorker
import com.nextcloud.talk.jobs.ShareOperationWorker
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import com.nextcloud.talk.location.LocationPickerActivity
import com.nextcloud.talk.messagesearch.MessageSearchActivity
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall
import com.nextcloud.talk.polls.ui.PollCreateDialogFragment
import com.nextcloud.talk.remotefilebrowser.activities.RemoteFileBrowserActivity
import com.nextcloud.talk.shareditems.activities.SharedItemsActivity
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.signaling.SignalingMessageSender
import com.nextcloud.talk.translate.ui.TranslateActivity
import com.nextcloud.talk.ui.PlaybackSpeed
import com.nextcloud.talk.ui.PlaybackSpeedControl
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet
import com.nextcloud.talk.ui.dialog.DateTimeCompose
import com.nextcloud.talk.ui.dialog.FileAttachmentPreviewFragment
import com.nextcloud.talk.ui.dialog.MessageActionsDialog
import com.nextcloud.talk.ui.dialog.SaveToStorageDialogFragment
import com.nextcloud.talk.ui.dialog.ShowReactionsDialog
import com.nextcloud.talk.ui.dialog.TempMessageActionsDialog
import com.nextcloud.talk.ui.recyclerview.MessageSwipeActions
import com.nextcloud.talk.ui.recyclerview.MessageSwipeCallback
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.AudioUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
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
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_RECORDING_STATE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_START_CALL_AFTER_ROOM_SWITCH
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SWITCH_TO_ROOM
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.rx.DisposableSet
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper
import com.nextcloud.talk.webrtc.WebSocketInstance
import com.otaliastudios.autocomplete.Autocomplete
import com.stfalcon.chatkit.commons.ImageLoader
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.messages.MessageHolders
import com.stfalcon.chatkit.messages.MessageHolders.ContentChecker
import com.stfalcon.chatkit.messages.MessagesListAdapter
import com.stfalcon.chatkit.utils.DateFormatter
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.collections.set
import kotlin.math.roundToInt
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

@AutoInjector(NextcloudTalkApplication::class)
class ChatActivity :
    BaseActivity(),
    MessagesListAdapter.OnLoadMoreListener,
    MessagesListAdapter.Formatter<Date>,
    MessagesListAdapter.OnMessageViewLongClickListener<IMessage>,
    ContentChecker<ChatMessage>,
    VoiceMessageInterface,
    CommonMessageInterface,
    PreviewMessageInterface,
    SystemMessageInterface,
    CallStartedMessageInterface {

    var active = false

    private lateinit var binding: ActivityChatBinding

    @Inject
    lateinit var ncApi: NcApi

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

    lateinit var chatViewModel: ChatViewModel
    lateinit var messageInputViewModel: MessageInputViewModel

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
                onMessageSearchResult(intent)
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
    lateinit var roomToken: String
    var conversationUser: User? = null
    lateinit var spreedCapabilities: SpreedCapability
    var chatApiVersion: Int = 1
    private var roomPassword: String = ""
    var credentials: String? = null
    var currentConversation: ConversationModel? = null
    var adapter: TalkMessagesListAdapter<ChatMessage>? = null
    var mentionAutocomplete: Autocomplete<*>? = null
    var layoutManager: LinearLayoutManager? = null
    var pullChatMessagesPending = false
    var startCallFromNotification: Boolean = false
    var startCallFromRoomSwitch: Boolean = false

    var voiceOnly: Boolean = true
    private lateinit var path: String

    var myFirstMessage: CharSequence? = null
    var checkingLobbyStatus: Boolean = false

    private var conversationVoiceCallMenuItem: MenuItem? = null
    private var conversationVideoMenuItem: MenuItem? = null

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
            val intent = Intent(this@ChatActivity, ConversationsListActivity::class.java)
            intent.putExtras(Bundle())
            startActivity(intent)
        }
    }

    private lateinit var messageInputFragment: MessageInputFragment

    val typingParticipants = HashMap<String, TypingParticipant>()

    var callStarted = false

    private val localParticipantMessageListener = object : SignalingMessageReceiver.LocalParticipantMessageListener {
        override fun onSwitchTo(token: String?) {
            if (token != null) {
                if (CallActivity.active) {
                    Log.d(TAG, "CallActivity is running. Ignore to switch chat in ChatActivity...")
                } else {
                    switchToRoom(token, false, false)
                }
            }
        }
    }

    private val conversationMessageListener = object : SignalingMessageReceiver.ConversationMessageListener {
        override fun onStartTyping(userId: String?, session: String?) {
            val userIdOrGuestSession = userId ?: session

            if (isTypingStatusEnabled() && conversationUser?.userId != userIdOrGuestSession) {
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

            if (isTypingStatusEnabled() && conversationUser?.userId != userId) {
                typingParticipants[userIdOrGuestSession]?.cancelTimer()
                typingParticipants.remove(userIdOrGuestSession)
                updateTypingIndicator()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setupActionBar()
        setContentView(binding.root)
        setupSystemColors()

        conversationUser = currentUserProvider.currentUser.blockingGet()
        handleIntent(intent)

        chatViewModel = ViewModelProvider(this, viewModelFactory)[ChatViewModel::class.java]

        val urlForChatting = ApiUtils.getUrlForChat(chatApiVersion, conversationUser?.baseUrl, roomToken)
        val credentials = ApiUtils.getCredentials(conversationUser!!.username, conversationUser!!.token)
        chatViewModel.initData(
            credentials!!,
            urlForChatting,
            roomToken
        )

        messageInputFragment = getMessageInputFragment()
        messageInputViewModel = ViewModelProvider(this, viewModelFactory)[MessageInputViewModel::class.java]
        messageInputViewModel.setData(chatViewModel.getChatRepository())

        binding.progressBar.visibility = View.VISIBLE

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        initObservers()

        pickMultipleMedia = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(5)
        ) { uris ->
            if (uris.isNotEmpty()) {
                onChooseFileResult(uris)
            }
        }
    }

    private fun getMessageInputFragment(): MessageInputFragment {
        val internalId = conversationUser!!.id.toString() + "@" + roomToken
        return MessageInputFragment().apply {
            arguments = Bundle().apply {
                putString(CONVERSATION_INTERNAL_ID, internalId)
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

        roomToken = extras?.getString(KEY_ROOM_TOKEN).orEmpty()

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
        adapter = null
        this.lifecycle.removeObserver(AudioUtils)
        this.lifecycle.removeObserver(chatViewModel)
    }

    @SuppressLint("NotifyDataSetChanged", "SetTextI18n", "ResourceAsColor")
    @Suppress("LongMethod")
    private fun initObservers() {
        Log.d(TAG, "initObservers Called")

        this.lifecycleScope.launch {
            chatViewModel.getConversationFlow
                .onEach { conversationModel ->
                    currentConversation = conversationModel
                    chatViewModel.updateConversation(
                        currentConversation!!
                    )

                    logConversationInfos("GetRoomSuccessState")

                    if (adapter == null) {
                        initAdapter()
                        binding.messagesListView.setAdapter(adapter)
                        layoutManager = binding.messagesListView.layoutManager as LinearLayoutManager?
                    }

                    chatViewModel.getCapabilities(conversationUser!!, roomToken, currentConversation!!)
                }.collect()
        }

        chatViewModel.getRoomViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.GetRoomSuccessState -> {
                    // unused atm
                }

                is ChatViewModel.GetRoomErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }

        chatViewModel.getCapabilitiesViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.GetCapabilitiesUpdateState -> {
                    if (currentConversation != null) {
                        spreedCapabilities = state.spreedCapabilities
                        chatApiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))
                        participantPermissions = ParticipantPermissions(spreedCapabilities, currentConversation!!)

                        invalidateOptionsMenu()
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
                    if (currentConversation != null) {
                        spreedCapabilities = state.spreedCapabilities
                        chatApiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))
                        participantPermissions = ParticipantPermissions(spreedCapabilities, currentConversation!!)

                        supportFragmentManager.commit {
                            setReorderingAllowed(true) // optimizes out redundant replace operations
                            replace(R.id.fragment_container_activity_chat, messageInputFragment)
                        }

                        joinRoomWithPassword()

                        if (conversationUser?.userId != "?" &&
                            CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.MENTION_FLAG)
                        ) {
                            binding.chatToolbar.setOnClickListener { _ -> showConversationInfoScreen() }
                        }

                        loadAvatarForStatusBar()
                        setupSwipeToReply()
                        setActionBarTitle()
                        checkShowCallButtons()
                        checkLobbyState()
                        if (currentConversation?.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
                            currentConversation?.status == "dnd"
                        ) {
                            conversationUser?.let { user ->
                                val credentials = ApiUtils.getCredentials(user.username, user.token)
                                chatViewModel.outOfOfficeStatusOfUser(
                                    credentials!!,
                                    user.baseUrl!!,
                                    currentConversation!!.name
                                )
                            }
                        }

                        updateRoomTimerHandler(MILLIS_250)

                        val urlForChatting =
                            ApiUtils.getUrlForChat(chatApiVersion, conversationUser?.baseUrl, roomToken)

                        chatViewModel.loadMessages(
                            withCredentials = credentials!!,
                            withUrl = urlForChatting
                        )
                    } else {
                        Log.w(
                            TAG,
                            "currentConversation was null in observer ChatViewModel.GetCapabilitiesInitialLoadState"
                        )
                    }
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

                    if (binding.unreadMessagesPopup.isShown) {
                        binding.unreadMessagesPopup.visibility = View.GONE
                    }
                    binding.messagesListView.smoothScrollToPosition(0)
                }

                is MessageInputViewModel.SendChatMessageErrorState -> {
                    binding.messagesListView.smoothScrollToPosition(0)
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

                    val id = state.msg.ocs!!.data!!.parentMessage!!.id.toString()
                    val index = adapter?.getMessagePositionById(id) ?: 0
                    val message = adapter?.items?.get(index)?.item as ChatMessage
                    setMessageAsDeleted(message)
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

        chatViewModel.chatMessageViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.ChatMessageStartState -> {
                    // Handle UI on first load
                    cancelNotificationsForCurrentConversation()
                    binding.progressBar.visibility = View.GONE
                    binding.offline.root.visibility = View.GONE
                    binding.messagesListView.visibility = View.VISIBLE
                    collapseSystemMessages()
                }

                is ChatViewModel.ChatMessageUpdateState -> {
                    binding.progressBar.visibility = View.GONE
                    binding.offline.root.visibility = View.GONE
                    binding.messagesListView.visibility = View.VISIBLE
                }

                is ChatViewModel.ChatMessageErrorState -> {
                    // unused atm
                }

                else -> {}
            }
        }

        this.lifecycleScope.launch {
            chatViewModel.getMessageFlow
                .onEach { triple ->
                    val lookIntoFuture = triple.first
                    val setUnreadMessagesMarker = triple.second
                    var chatMessageList = triple.third

                    chatMessageList = handleSystemMessages(chatMessageList)
                    if (chatMessageList.isEmpty()) {
                        return@onEach
                    }

                    determinePreviousMessageIds(chatMessageList)

                    handleExpandableSystemMessages(chatMessageList)

                    if (ChatMessage.SystemMessageType.CLEARED_CHAT == chatMessageList[0].systemMessageType) {
                        adapter?.clear()
                        adapter?.notifyDataSetChanged()
                    }

                    if (lookIntoFuture) {
                        Log.d(TAG, "chatMessageList.size in getMessageFlow:" + chatMessageList.size)
                        processMessagesFromTheFuture(chatMessageList, setUnreadMessagesMarker)
                    } else {
                        processMessagesNotFromTheFuture(chatMessageList)
                        collapseSystemMessages()
                    }

                    processExpiredMessages()
                    processCallStartedMessages()

                    adapter?.notifyDataSetChanged()
                }
                .collect()
        }

        this.lifecycleScope.launch {
            chatViewModel.getRemoveMessageFlow
                .onEach {
                    removeMessageById(it.id)
                }
                .collect()
        }

        this.lifecycleScope.launch {
            chatViewModel.getUpdateMessageFlow
                .onEach {
                    updateMessageInsideAdapter(it)
                }
                .collect()
        }

        this.lifecycleScope.launch {
            chatViewModel.getLastCommonReadFlow
                .onEach {
                    updateReadStatusOfAllMessages(it)
                    processExpiredMessages()
                }
                .collect()
        }

        this.lifecycleScope.launch {
            chatViewModel.getLastReadMessageFlow
                .onEach { lastRead ->
                    scrollToAndCenterMessageWithId(lastRead.toString())
                }
                .collect()
        }

        this.lifecycleScope.launch {
            chatViewModel.getGeneralUIFlow.onEach { key ->
                when (key) {
                    NO_OFFLINE_MESSAGES_FOUND -> {
                        binding.progressBar.visibility = View.GONE
                        binding.messagesListView.visibility = View.GONE
                        binding.offline.root.visibility = View.VISIBLE
                    }

                    else -> {}
                }
            }.collect()
        }

        this.lifecycleScope.launch {
            chatViewModel.mediaPlayerSeekbarObserver.onEach { msg ->
                adapter?.update(msg)
            }.collect()
        }

        chatViewModel.reactionDeletedViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.ReactionDeletedSuccessState -> {
                    updateUiToDeleteReaction(
                        state.reactionDeletedModel.chatMessage,
                        state.reactionDeletedModel.emoji
                    )
                }

                else -> {}
            }
        }

        chatViewModel.reactionAddedViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.ReactionAddedSuccessState -> {
                    updateUiToAddReaction(
                        state.reactionAddedModel.chatMessage,
                        state.reactionAddedModel.emoji
                    )
                }

                else -> {}
            }
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
                    val newString = state.messageEdited.ocs?.data?.parentMessage?.message ?: "(null)"
                    val id = state.messageEdited.ocs?.data?.parentMessage?.id.toString()
                    val index = adapter?.getMessagePositionById(id) ?: 0
                    val message = adapter?.items?.get(index)?.item as ChatMessage
                    setMessageAsEdited(message, newString)
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
                        var imageUri = ApiUtils.getUrlForAvatar(
                            conversationUser?.baseUrl,
                            uiState.userAbsence
                                .replacementUserId,
                            false
                        ).toUri()
                        if (DisplayUtils.isDarkModeOn(context)) {
                            imageUri = ApiUtils.getUrlForAvatarDarkTheme(
                                conversationUser?.baseUrl,
                                uiState
                                    .userAbsence
                                    .replacementUserId,
                                false
                            ).toUri()
                        }
                        binding.outOfOfficeContainer.findViewById<TextView>(R.id.absenceReplacement).text =
                            context.resources.getString(R.string.user_absence_replacement)
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
    }

    private fun removeUnreadMessagesMarker() {
        removeMessageById(UNREAD_MESSAGES_MARKER_ID.toString())
    }

    // do not use adapter.deleteById() as it seems to contain a bug! Use this method instead!
    @Suppress("MagicNumber")
    private fun removeMessageById(idToDelete: String) {
        val indexToDelete = adapter?.getMessagePositionById(idToDelete)
        if (indexToDelete != null && indexToDelete != UNREAD_MESSAGES_MARKER_ID) {
            // If user sent a message as a first message in todays chat, the temp message will be deleted when
            // messages are retrieved from server, but also the date has to be deleted as it will be added again
            // when the chat messages are added from server. Otherwise date "Today" would be shown twice.
            if (indexToDelete == 0 && (adapter?.items?.get(1))?.item is Date) {
                adapter?.items?.removeAt(0)
                adapter?.items?.removeAt(0)
                adapter?.notifyItemRangeRemoved(indexToDelete, 1)
            } else {
                adapter?.items?.removeAt(indexToDelete)
                adapter?.notifyItemRemoved(indexToDelete)
            }
        }
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

        setupSwipeToReply()

        binding.unreadMessagesPopup.setOnClickListener {
            binding.messagesListView.smoothScrollToPosition(0)
            binding.unreadMessagesPopup.visibility = View.GONE
        }

        binding.scrollDownButton.setOnClickListener {
            binding.messagesListView.scrollToPosition(0)
            it.visibility = View.GONE
        }

        binding.let { viewThemeUtils.material.colorMaterialButtonPrimaryTonal(it.scrollDownButton) }

        binding.let { viewThemeUtils.material.themeFAB(it.voiceRecordingLock) }

        binding.let { viewThemeUtils.material.colorMaterialButtonPrimaryFilled(it.unreadMessagesPopup) }

        binding.messagesListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    if (isScrolledToBottom()) {
                        binding.unreadMessagesPopup.visibility = View.GONE
                        binding.scrollDownButton.visibility = View.GONE
                    } else {
                        if (binding.unreadMessagesPopup.isShown) {
                            binding.scrollDownButton.visibility = View.GONE
                        } else {
                            binding.scrollDownButton.visibility = View.VISIBLE
                        }
                    }
                }
            }
        })

        loadAvatarForStatusBar()
        setActionBarTitle()
        viewThemeUtils.material.colorToolbarOverflowIcon(binding.chatToolbar)
    }

    // private fun getLastAdapterId(): Int {
    //     var lastId = 0
    //     if (adapter?.items?.size != 0) {
    //         val item = adapter?.items?.get(0)?.item
    //         if (item != null) {
    //             lastId = (item as ChatMessage).jsonMessageId
    //         } else {
    //             lastId = 0
    //         }
    //     }
    //     return lastId
    // }

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

    private fun initAdapter() {
        val senderId = if (!conversationUser!!.userId.equals("?")) {
            "users/" + conversationUser!!.userId
        } else {
            currentConversation?.actorType + "/" + currentConversation?.actorId
        }

        Log.d(TAG, "Initialize TalkMessagesListAdapter with senderId: $senderId")

        adapter = TalkMessagesListAdapter(
            senderId,
            initMessageHolders(),
            ImageLoader { imageView, url, placeholder ->
                imageView.loadAvatarOrImagePreview(url!!, conversationUser!!, placeholder as Drawable?)
            },
            this
        )

        adapter?.setLoadMoreListener(this)
        adapter?.setDateHeadersFormatter { format(it) }
        adapter?.setOnMessageViewLongClickListener { view, message -> onMessageViewLongClick(view, message) }

        adapter?.registerViewClickListener(
            R.id.playPauseBtn
        ) { _, message ->
            val filename = message.selectedIndividualHashMap!!["name"]
            val file = File(context.cacheDir, filename!!)
            if (file.exists()) {
                if (message.isPlayingVoiceMessage) {
                    chatViewModel.pauseMediaPlayer(true)
                    message.isPlayingVoiceMessage = false
                    adapter?.update(message)
                } else {
                    val retrieved = appPreferences.getWaveFormFromFile(filename)
                    if (retrieved.isEmpty()) {
                        setUpWaveform(message)
                    } else {
                        startPlayback(file, message)
                    }
                }
            } else {
                Log.d(TAG, "Downloaded to cache")
                downloadFileToCache(message, true) {
                    setUpWaveform(message)
                }
            }
        }

        adapter?.registerViewClickListener(R.id.playbackSpeedControlBtn) { button, message ->
            val nextSpeed = (button as PlaybackSpeedControl).getSpeed().next()
            chatViewModel.setPlayBack(nextSpeed)
            appPreferences.savePreferredPlayback(conversationUser!!.userId, nextSpeed)
        }
    }

    private fun setUpWaveform(message: ChatMessage, thenPlay: Boolean = true, backgroundPlayAllowed: Boolean = false) {
        val filename = message.selectedIndividualHashMap!!["name"]
        val file = File(context.cacheDir, filename!!)
        if (file.exists() && message.voiceMessageFloatArray == null) {
            message.isDownloadingVoiceMessage = true
            adapter?.update(message)
            CoroutineScope(Dispatchers.Default).launch {
                val r = AudioUtils.audioFileToFloatArray(file)
                appPreferences.saveWaveFormForFile(filename, r.toTypedArray())
                message.voiceMessageFloatArray = r
                withContext(Dispatchers.Main) {
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
        adapter?.update(message)

        var pos = adapter?.getMessagePositionById(message.id)!! - 1
        do {
            if (pos < 0) break
            val nextItem = (adapter?.items?.get(pos)?.item) ?: break
            val nextMessage = if (nextItem is ChatMessage) nextItem else break
            if (!nextMessage.isVoiceMessage) break

            downloadFileToCache(nextMessage, false) {
                val newFilename = nextMessage.selectedIndividualHashMap!!["name"]
                val newFile = File(context.cacheDir, newFilename!!)
                chatViewModel.queueInMediaPlayer(newFile.canonicalPath, nextMessage)
            }
            pos--
        } while (true && pos >= 0)
    }

    private fun initMessageHolders(): MessageHolders {
        val messageHolders = MessageHolders()
        val profileBottomSheet = ProfileBottomSheet(ncApi, conversationUser!!, viewThemeUtils)

        val payload = MessagePayload(
            roomToken,
            ConversationUtils.isParticipantOwnerOrModerator(currentConversation!!),
            profileBottomSheet
        )

        messageHolders.setIncomingTextConfig(
            IncomingTextMessageViewHolder::class.java,
            R.layout.item_custom_incoming_text_message,
            payload
        )
        messageHolders.setOutcomingTextConfig(
            OutcomingTextMessageViewHolder::class.java,
            R.layout.item_custom_outcoming_text_message
        )

        messageHolders.setIncomingImageConfig(
            IncomingPreviewMessageViewHolder::class.java,
            R.layout.item_custom_incoming_preview_message,
            payload
        )

        messageHolders.setOutcomingImageConfig(
            OutcomingPreviewMessageViewHolder::class.java,
            R.layout.item_custom_outcoming_preview_message
        )

        messageHolders.registerContentType(
            CONTENT_TYPE_SYSTEM_MESSAGE,
            SystemMessageViewHolder::class.java,
            R.layout.item_system_message,
            SystemMessageViewHolder::class.java,
            R.layout.item_system_message,
            this
        )
        messageHolders.registerContentType(
            CONTENT_TYPE_UNREAD_NOTICE_MESSAGE,
            UnreadNoticeMessageViewHolder::class.java,
            R.layout.item_date_header,
            UnreadNoticeMessageViewHolder::class.java,
            R.layout.item_date_header,
            this
        )

        messageHolders.registerContentType(
            CONTENT_TYPE_LOCATION,
            IncomingLocationMessageViewHolder::class.java,
            payload,
            R.layout.item_custom_incoming_location_message,
            OutcomingLocationMessageViewHolder::class.java,
            null,
            R.layout.item_custom_outcoming_location_message,
            this
        )

        messageHolders.registerContentType(
            CONTENT_TYPE_VOICE_MESSAGE,
            IncomingVoiceMessageViewHolder::class.java,
            payload,
            R.layout.item_custom_incoming_voice_message,
            OutcomingVoiceMessageViewHolder::class.java,
            null,
            R.layout.item_custom_outcoming_voice_message,
            this
        )

        messageHolders.registerContentType(
            CONTENT_TYPE_POLL,
            IncomingPollMessageViewHolder::class.java,
            payload,
            R.layout.item_custom_incoming_poll_message,
            OutcomingPollMessageViewHolder::class.java,
            payload,
            R.layout.item_custom_outcoming_poll_message,
            this
        )

        messageHolders.registerContentType(
            CONTENT_TYPE_LINK_PREVIEW,
            IncomingLinkPreviewMessageViewHolder::class.java,
            payload,
            R.layout.item_custom_incoming_link_preview_message,
            OutcomingLinkPreviewMessageViewHolder::class.java,
            payload,
            R.layout.item_custom_outcoming_link_preview_message,
            this
        )

        messageHolders.registerContentType(
            CONTENT_TYPE_DECK_CARD,
            IncomingDeckCardViewHolder::class.java,
            payload,
            R.layout.item_custom_incoming_deck_card_message,
            OutcomingDeckCardViewHolder::class.java,
            payload,
            R.layout.item_custom_outcoming_deck_card_message,
            this
        )

        return messageHolders
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

    private fun setupSwipeToReply() {
        if (this::participantPermissions.isInitialized &&
            participantPermissions.hasChatPermission() &&
            !isReadOnlyConversation()
        ) {
            val messageSwipeCallback = MessageSwipeCallback(
                this,
                object : MessageSwipeActions {
                    override fun showReplyUI(position: Int) {
                        val chatMessage = adapter?.items?.getOrNull(position)?.item as ChatMessage?
                        if (chatMessage != null) {
                            messageInputViewModel.reply(chatMessage)
                        }
                    }
                }
            )

            val itemTouchHelper = ItemTouchHelper(messageSwipeCallback)
            itemTouchHelper.attachToRecyclerView(binding.messagesListView)
        }
    }

    private fun loadAvatarForStatusBar() {
        if (currentConversation == null) {
            return
        }

        if (isOneToOneConversation()) {
            var url = ApiUtils.getUrlForAvatar(
                conversationUser!!.baseUrl!!,
                currentConversation!!.name,
                true
            )

            if (DisplayUtils.isDarkModeOn(supportActionBar?.themedContext!!)) {
                url = "$url/dark"
            }

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

    private fun isGroupConversation() =
        currentConversation != null &&
            currentConversation?.type != null &&
            currentConversation?.type == ConversationEnums.ConversationType.ROOM_GROUP_CALL

    private fun isPublicConversation() =
        currentConversation != null &&
            currentConversation?.type != null &&
            currentConversation?.type == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL

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

    override fun updateMediaPlayerProgressBySlider(message: ChatMessage, progress: Int) {
        chatViewModel.seekToMediaPlayer(progress)
    }

    override fun registerMessageToObservePlaybackSpeedPreferences(
        userId: String,
        listener: (speed: PlaybackSpeed) -> Unit
    ) {
        CoroutineScope(Dispatchers.Default).launch {
            chatViewModel.voiceMessagePlayBackUIFlow.onEach { speed ->
                withContext(Dispatchers.Main) {
                    listener(speed)
                }
            }.collect()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun collapseSystemMessages() {
        adapter?.items?.forEach {
            if (it.item is ChatMessage) {
                val chatMessage = it.item as ChatMessage
                if (isChildOfExpandableSystemMessage(chatMessage)) {
                    chatMessage.hiddenByCollapse = true
                }
                chatMessage.isExpanded = false
            }
        }

        adapter?.notifyDataSetChanged()
    }

    private fun isChildOfExpandableSystemMessage(chatMessage: ChatMessage): Boolean =
        isSystemMessage(chatMessage) &&
            !chatMessage.expandableParent &&
            chatMessage.lastItemOfExpandableGroup != 0

    @SuppressLint("NotifyDataSetChanged")
    override fun expandSystemMessage(chatMessageToExpand: ChatMessage) {
        adapter?.items?.forEach {
            if (it.item is ChatMessage) {
                val belongsToGroupToExpand =
                    (it.item as ChatMessage).lastItemOfExpandableGroup == chatMessageToExpand.lastItemOfExpandableGroup

                if (belongsToGroupToExpand) {
                    (it.item as ChatMessage).hiddenByCollapse = false
                }
            }
        }

        chatMessageToExpand.isExpanded = true

        adapter?.notifyDataSetChanged()
    }

    @SuppressLint("LongLogTag")
    private fun downloadFileToCache(
        message: ChatMessage,
        openWhenDownloaded: Boolean,
        funToCallWhenDownloadSuccessful: (() -> Unit)
    ) {
        message.isDownloadingVoiceMessage = true
        message.openWhenDownloaded = openWhenDownloaded
        adapter?.update(message)

        val baseUrl = message.activeUser!!.baseUrl
        val userId = message.activeUser!!.userId
        val attachmentFolder = CapabilitiesUtil.getAttachmentFolder(
            message.activeUser!!.capabilities!!
                .spreedCapability!!
        )
        val fileName = message.selectedIndividualHashMap!!["name"]
        var size = message.selectedIndividualHashMap!!["size"]
        if (size == null) {
            size = "-1"
        }
        val fileSize = size.toLong()
        val fileId = message.selectedIndividualHashMap!!["id"]
        val path = message.selectedIndividualHashMap!!["path"]

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
            .putLong(DownloadFileToCacheWorker.KEY_FILE_SIZE, fileSize)
            .build()

        val downloadWorker: OneTimeWorkRequest = OneTimeWorkRequest.Builder(DownloadFileToCacheWorker::class.java)
            .setInputData(data)
            .addTag(fileId)
            .build()

        WorkManager.getInstance().enqueue(downloadWorker)

        WorkManager.getInstance(context).getWorkInfoByIdLiveData(downloadWorker.id)
            .observeForever { workInfo: WorkInfo ->
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
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
            return CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.WEBINARY_LOBBY) &&
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
            binding.messagesListView.visibility = View.VISIBLE
            checkShowMessageInputView()
        }
    }

    private fun showLobbyView() {
        binding.lobby.lobbyView.visibility = View.VISIBLE
        binding.messagesListView.visibility = View.GONE
        binding.fragmentContainerActivityChat.visibility = View.GONE
        binding.progressBar.visibility = View.GONE

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
            uploadFile(shareUri.toString(), false)
        }
        cursor?.close()
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

    private fun onMessageSearchResult(intent: Intent?) {
        val messageId = intent?.getStringExtra(MessageSearchActivity.RESULT_KEY_MESSAGE_ID)
        messageId?.let { id ->
            scrollToAndCenterMessageWithId(id)
        }
    }

    private fun executeIfResultOk(result: ActivityResult, onResult: (intent: Intent?) -> Unit) {
        if (result.resultCode == Activity.RESULT_OK) {
            onResult(result.data)
        } else {
            Log.e(TAG, "resultCode for received intent was != ok")
        }
    }

    private fun scrollToMessageWithId(messageId: String) {
        val position = adapter?.items?.indexOfFirst {
            it.item is ChatMessage && (it.item as ChatMessage).id == messageId
        }
        if (position != null && position >= 0) {
            binding.messagesListView.scrollToPosition(position)
        } else {
            Log.d(TAG, "message $messageId that should be scrolled to was not found (scrollToMessageWithId)")
        }
    }

    private fun scrollToAndCenterMessageWithId(messageId: String) {
        adapter?.let {
            val position = it.getMessagePositionByIdInReverse(messageId)
            if (position != -1) {
                layoutManager?.scrollToPositionWithOffset(
                    position,
                    binding.messagesListView.height / 2
                )
            } else {
                Log.d(
                    TAG,
                    "message $messageId that should be scrolled to was not found " +
                        "(scrollToAndCenterMessageWithId)"
                )
            }
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
                uploadFile(files[i], false, caption)
            } else {
                uploadFile(files[i], false)
            }
        }
    }

    private fun uploadFile(fileUri: String, isVoiceMessage: Boolean, caption: String = "", token: String = "") {
        var metaData = ""
        var room = ""

        if (!participantPermissions.hasChatPermission()) {
            Log.w(TAG, "uploading file(s) is forbidden because of missing attendee permissions")
            return
        }

        if (isVoiceMessage) {
            metaData = VOICE_MESSAGE_META_DATA
        }

        if (caption != "") {
            metaData = "{\"caption\":\"$caption\"}"
        }

        if (token == "") room = roomToken else room = token

        chatViewModel.uploadFile(fileUri, room, currentConversation?.displayName!!, metaData)
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

        val intent = Intent(this, LocationPickerActivity::class.java)
        intent.putExtra(KEY_ROOM_TOKEN, roomToken)
        intent.putExtra(BundleKeys.KEY_CHAT_API_VERSION, chatApiVersion)
        startActivity(intent)
    }

    private fun showConversationInfoScreen() {
        val bundle = Bundle()

        bundle.putString(KEY_ROOM_TOKEN, roomToken)
        bundle.putBoolean(BundleKeys.KEY_ROOM_ONE_TO_ONE, isOneToOneConversation())

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
    }

    private fun isActivityNotChangingConfigurations(): Boolean = !isChangingConfigurations

    private fun isNotInCall(): Boolean =
        !ApplicationWideCurrentRoomHolder.getInstance().isInCall &&
            !ApplicationWideCurrentRoomHolder.getInstance().isDialing

    private fun setActionBarTitle() {
        val title = binding.chatToolbar.findViewById<TextView>(R.id.chat_toolbar_title)
        viewThemeUtils.platform.colorTextView(title, ColorRole.ON_SURFACE)

        title.text =
            if (currentConversation?.displayName != null) {
                try {
                    EmojiCompat.get().process(currentConversation?.displayName as CharSequence).toString()
                } catch (e: java.lang.IllegalStateException) {
                    Log.e(TAG, "setActionBarTitle failed $e")
                    currentConversation?.displayName
                }
            } else {
                ""
            }

        if (currentConversation?.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
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

        adapter = null
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

    private fun processCallStartedMessages() {
        try {
            val mostRecentCallSystemMessage = adapter?.items?.first {
                it.item is ChatMessage &&
                    (it.item as ChatMessage).systemMessageType in
                    listOf(
                        ChatMessage.SystemMessageType.CALL_STARTED,
                        ChatMessage.SystemMessageType.CALL_JOINED,
                        ChatMessage.SystemMessageType.CALL_LEFT,
                        ChatMessage.SystemMessageType.CALL_ENDED,
                        ChatMessage.SystemMessageType.CALL_TRIED,
                        ChatMessage.SystemMessageType.CALL_ENDED_EVERYONE,
                        ChatMessage.SystemMessageType.CALL_MISSED
                    )
            }?.item

            if (mostRecentCallSystemMessage != null) {
                processMostRecentMessage(
                    mostRecentCallSystemMessage as ChatMessage
                )
            }
        } catch (e: NoSuchElementException) {
            Log.d(TAG, "No System messages found $e")
        }
    }

    private fun processExpiredMessages() {
        @SuppressLint("NotifyDataSetChanged")
        fun deleteExpiredMessages() {
            val messagesToDelete: ArrayList<ChatMessage> = ArrayList()
            val systemTime = System.currentTimeMillis() / ONE_SECOND_IN_MILLIS

            if (adapter?.items != null) {
                for (itemWrapper in adapter?.items!!) {
                    if (itemWrapper.item is ChatMessage) {
                        val chatMessage = itemWrapper.item as ChatMessage
                        if (chatMessage.expirationTimestamp != 0 && chatMessage.expirationTimestamp < systemTime) {
                            messagesToDelete.add(chatMessage)
                        }
                    }
                }
                adapter!!.delete(messagesToDelete)
                adapter!!.notifyDataSetChanged()
            }
        }

        if (this::spreedCapabilities.isInitialized) {
            if (CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.MESSAGE_EXPIRATION)) {
                deleteExpiredMessages()
            }
        } else {
            Log.w(TAG, "spreedCapabilities are not initialized in processExpiredMessages()")
        }
    }

    private fun updateReadStatusOfAllMessages(xChatLastCommonRead: Int?) {
        if (adapter != null) {
            for (message in adapter!!.items) {
                xChatLastCommonRead?.let {
                    updateReadStatusOfMessage(message, it)
                }
            }
            adapter!!.notifyDataSetChanged()
        }
    }

    private fun updateReadStatusOfMessage(
        message: MessagesListAdapter<IMessage>.Wrapper<Any>,
        xChatLastCommonRead: Int
    ) {
        if (message.item is ChatMessage) {
            val chatMessage = message.item as ChatMessage
            if (chatMessage.jsonMessageId <= xChatLastCommonRead) {
                chatMessage.readStatus = ReadStatus.READ
            } else {
                chatMessage.readStatus = ReadStatus.SENT
            }
        }
    }

    private fun processMessagesFromTheFuture(chatMessageList: List<ChatMessage>, setUnreadMessagesMarker: Boolean) {
        binding.scrollDownButton.visibility = View.GONE

        val scrollToBottom: Boolean

        if (setUnreadMessagesMarker) {
            scrollToBottom = false
            setUnreadMessageMarker(chatMessageList)
        } else {
            if (isScrolledToBottom()) {
                scrollToBottom = true
            } else {
                scrollToBottom = false
                binding.unreadMessagesPopup.visibility = View.VISIBLE
                // here we have the problem that the chat jumps for every update
            }
        }

        for (chatMessage in chatMessageList) {
            chatMessage.activeUser = conversationUser

            adapter?.let {
                val previousChatMessage = it.items?.getOrNull(1)?.item
                if (previousChatMessage != null && previousChatMessage is ChatMessage) {
                    chatMessage.isGrouped = groupMessages(chatMessage, previousChatMessage)
                }
                chatMessage.isOneToOneConversation =
                    (currentConversation?.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL)
                chatMessage.isFormerOneToOneConversation =
                    (currentConversation?.type == ConversationEnums.ConversationType.FORMER_ONE_TO_ONE)
                Log.d(TAG, "chatMessage to add:" + chatMessage.message)
                it.addToStart(chatMessage, scrollToBottom)
            }
        }

        // workaround to jump back to unread messages marker
        if (setUnreadMessagesMarker) {
            scrollToFirstUnreadMessage()
        }
    }

    private fun isScrolledToBottom(): Boolean {
        val position = layoutManager?.findFirstVisibleItemPosition()
        if (position == -1) {
            Log.w(
                TAG,
                "FirstVisibleItemPosition was -1 but true is returned for isScrolledToBottom(). This can " +
                    "happen when the UI is not yet ready"
            )
            return true
        }

        return layoutManager?.findFirstVisibleItemPosition() == 0
    }

    private fun setUnreadMessageMarker(chatMessageList: List<ChatMessage>) {
        if (chatMessageList.isNotEmpty()) {
            val unreadChatMessage = ChatMessage()
            unreadChatMessage.jsonMessageId = UNREAD_MESSAGES_MARKER_ID
            unreadChatMessage.actorId = "-1"
            unreadChatMessage.timestamp = chatMessageList[0].timestamp
            unreadChatMessage.message = context.getString(R.string.nc_new_messages)
            adapter?.addToStart(unreadChatMessage, false)
        }
    }

    private fun processMessagesNotFromTheFuture(chatMessageList: List<ChatMessage>) {
        for (i in chatMessageList.indices) {
            if (chatMessageList.size > i + 1) {
                chatMessageList[i].isGrouped = groupMessages(chatMessageList[i], chatMessageList[i + 1])
            }

            val chatMessage = chatMessageList[i]
            chatMessage.isOneToOneConversation =
                currentConversation?.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
            chatMessage.isFormerOneToOneConversation =
                (currentConversation?.type == ConversationEnums.ConversationType.FORMER_ONE_TO_ONE)
            chatMessage.activeUser = conversationUser
            chatMessage.token = roomToken
        }

        if (adapter != null) {
            adapter?.addToEnd(chatMessageList, false)
        }
        scrollToRequestedMessageIfNeeded()
    }

    private fun scrollToFirstUnreadMessage() {
        adapter?.let {
            scrollToAndCenterMessageWithId(UNREAD_MESSAGES_MARKER_ID.toString())
        }
    }

    private fun groupMessages(message1: ChatMessage, message2: ChatMessage): Boolean {
        val message1IsSystem = message1.systemMessage.isNotEmpty()
        val message2IsSystem = message2.systemMessage.isNotEmpty()
        if (message1IsSystem != message2IsSystem) {
            return false
        }

        if (message1.actorType == "bots" && message1.actorId != "changelog") {
            return false
        }

        if (!message1IsSystem &&
            (
                (message1.actorType != message2.actorType) ||
                    (message2.actorId != message1.actorId)
                )
        ) {
            return false
        }

        val timeDifference = dateUtils.getTimeDifferenceInSeconds(
            message2.timestamp,
            message1.timestamp
        )
        val isLessThan5Min = timeDifference > FIVE_MINUTES_IN_SECONDS
        return isSameDayMessages(message2, message1) &&
            (message2.actorId == message1.actorId) &&
            (!isLessThan5Min) &&
            (message2.lastEditTimestamp == 0L || message1.lastEditTimestamp == 0L)
    }

    private fun determinePreviousMessageIds(chatMessageList: List<ChatMessage>) {
        var previousMessageId = NO_PREVIOUS_MESSAGE_ID
        for (i in chatMessageList.indices.reversed()) {
            val chatMessage = chatMessageList[i]

            if (previousMessageId > NO_PREVIOUS_MESSAGE_ID) {
                chatMessage.previousMessageId = previousMessageId
            } else {
                adapter?.let {
                    if (!it.isEmpty) {
                        if (it.items[0].item is ChatMessage) {
                            chatMessage.previousMessageId = (it.items[0].item as ChatMessage).jsonMessageId
                        } else if (it.items.size > 1 && it.items[1].item is ChatMessage) {
                            chatMessage.previousMessageId = (it.items[1].item as ChatMessage).jsonMessageId
                        }
                    }
                }
            }

            previousMessageId = chatMessage.jsonMessageId
        }
    }

    private fun getItemFromAdapter(messageId: String): Pair<ChatMessage, Int>? {
        if (adapter != null) {
            val messagePosition = adapter!!.items!!.indexOfFirst {
                it.item is ChatMessage && (it.item as ChatMessage).id == messageId
            }
            if (messagePosition >= 0) {
                val currentItem = adapter?.items?.get(messagePosition)?.item
                if (currentItem is ChatMessage && currentItem.id == messageId) {
                    return Pair(currentItem, messagePosition)
                } else {
                    Log.d(TAG, "currentItem retrieved was not chatmessage or its id was not correct")
                }
            } else {
                Log.d(TAG, "messagePosition is -1, adapter # of items: " + adapter!!.itemCount)
            }
        } else {
            Log.d(TAG, "TalkMessagesListAdapter is null")
        }
        return null
    }

    private fun scrollToRequestedMessageIfNeeded() {
        intent.getStringExtra(BundleKeys.KEY_MESSAGE_ID)?.let {
            scrollToMessageWithId(it)
        }
    }

    private fun isSameDayNonSystemMessages(messageLeft: ChatMessage, messageRight: ChatMessage): Boolean =
        TextUtils.isEmpty(messageLeft.systemMessage) &&
            TextUtils.isEmpty(messageRight.systemMessage) &&
            DateFormatter.isSameDay(messageLeft.createdAt, messageRight.createdAt)

    private fun isSameDayMessages(message1: ChatMessage, message2: ChatMessage): Boolean =
        DateFormatter.isSameDay(message1.createdAt, message2.createdAt)

    override fun onLoadMore(page: Int, totalItemsCount: Int) {
        val messageId = (
            adapter?.items
                ?.lastOrNull { it.item is ChatMessage }
                ?.item as? ChatMessage
            )?.jsonMessageId

        messageId?.let {
            val urlForChatting = ApiUtils.getUrlForChat(chatApiVersion, conversationUser?.baseUrl, roomToken)

            chatViewModel.loadMoreMessages(
                beforeMessageId = it.toLong(),
                withUrl = urlForChatting,
                withCredentials = credentials!!,
                withMessageLimit = MESSAGE_PULL_LIMIT,
                roomToken = currentConversation!!.token
            )
        }
    }

    override fun format(date: Date): String =
        if (DateFormatter.isToday(date)) {
            resources!!.getString(R.string.nc_date_header_today)
        } else if (DateFormatter.isYesterday(date)) {
            resources!!.getString(R.string.nc_date_header_yesterday)
        } else {
            DateFormatter.format(date, DateFormatter.Template.STRING_DAY_MONTH_YEAR)
        }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_conversation, menu)

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
            if (CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.READ_ONLY_ROOMS)) {
                checkShowCallButtons()
            }

            val searchItem = menu.findItem(R.id.conversation_search)

            searchItem.isVisible = CapabilitiesUtil.isUnifiedSearchAvailable(spreedCapabilities) &&
                currentConversation!!.remoteServer.isNullOrEmpty()

            if (currentConversation!!.remoteServer != null ||
                !CapabilitiesUtil.isSharedItemsAvailable(spreedCapabilities)
            ) {
                menu.removeItem(R.id.shared_items)
            }

            if (CapabilitiesUtil.isAbleToCall(spreedCapabilities)) {
                conversationVoiceCallMenuItem = menu.findItem(R.id.conversation_voice_call)
                conversationVideoMenuItem = menu.findItem(R.id.conversation_video_call)

                this.lifecycleScope.launch {
                    networkMonitor.isOnline.onEach { isOnline ->
                        conversationVoiceCallMenuItem?.isVisible = isOnline
                        searchItem?.isVisible = isOnline
                        conversationVideoMenuItem?.isVisible = isOnline
                    }.collect()
                }

                if (CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.SILENT_CALL)) {
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
        }

        return true
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

            else -> super.onOptionsItemSelected(item)
        }

    private fun showSharedItems() {
        val intent = Intent(this, SharedItemsActivity::class.java)
        intent.putExtra(KEY_CONVERSATION_NAME, currentConversation?.displayName)
        intent.putExtra(KEY_ROOM_TOKEN, roomToken)
        intent.putExtra(
            SharedItemsActivity.KEY_USER_IS_OWNER_OR_MODERATOR,
            ConversationUtils.isParticipantOwnerOrModerator(currentConversation!!)
        )
        startActivity(intent)
    }

    private fun startMessageSearch() {
        val intent = Intent(this, MessageSearchActivity::class.java)
        intent.putExtra(KEY_CONVERSATION_NAME, currentConversation?.displayName)
        intent.putExtra(KEY_ROOM_TOKEN, roomToken)
        startMessageSearchForResult.launch(intent)
    }

    private fun handleSystemMessages(chatMessageList: List<ChatMessage>): List<ChatMessage> {
        val chatMessageMap = chatMessageList.map { it.id to it }.toMap().toMutableMap()

        val chatMessageIterator = chatMessageMap.iterator()
        while (chatMessageIterator.hasNext()) {
            val currentMessage = chatMessageIterator.next()

            if (isInfoMessageAboutDeletion(currentMessage) ||
                isReactionsMessage(currentMessage) ||
                isPollVotedMessage(currentMessage) ||
                isEditMessage(currentMessage)
            ) {
                chatMessageIterator.remove()
            }
        }
        return chatMessageMap.values.toList()
    }

    private fun handleExpandableSystemMessages(chatMessageList: List<ChatMessage>): List<ChatMessage> {
        val chatMessageMap = chatMessageList.map { it.id to it }.toMap().toMutableMap()
        val chatMessageIterator = chatMessageMap.iterator()
        while (chatMessageIterator.hasNext()) {
            val currentMessage = chatMessageIterator.next()

            val previousMessage = chatMessageMap[currentMessage.value.previousMessageId.toString()]
            if (isSystemMessage(currentMessage.value) &&
                previousMessage?.systemMessageType == currentMessage.value.systemMessageType
            ) {
                previousMessage?.expandableParent = true
                currentMessage.value.expandableParent = false

                if (currentMessage.value.lastItemOfExpandableGroup == 0) {
                    currentMessage.value.lastItemOfExpandableGroup = currentMessage.value.jsonMessageId
                }

                previousMessage?.lastItemOfExpandableGroup = currentMessage.value.lastItemOfExpandableGroup
                previousMessage?.expandableChildrenAmount = currentMessage.value.expandableChildrenAmount + 1
            }
        }
        return chatMessageMap.values.toList()
    }

    private fun isInfoMessageAboutDeletion(currentMessage: MutableMap.MutableEntry<String, ChatMessage>): Boolean =
        currentMessage.value.parentMessageId != null &&
            currentMessage.value.systemMessageType == ChatMessage
                .SystemMessageType.MESSAGE_DELETED

    private fun isReactionsMessage(currentMessage: MutableMap.MutableEntry<String, ChatMessage>): Boolean =
        currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.REACTION ||
            currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.REACTION_DELETED ||
            currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.REACTION_REVOKED

    private fun isEditMessage(currentMessage: MutableMap.MutableEntry<String, ChatMessage>): Boolean =
        currentMessage.value.parentMessageId != null &&
            currentMessage.value.systemMessageType == ChatMessage
                .SystemMessageType.MESSAGE_EDITED

    private fun isPollVotedMessage(currentMessage: MutableMap.MutableEntry<String, ChatMessage>): Boolean =
        currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.POLL_VOTED

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

    override fun onClickReaction(chatMessage: ChatMessage, emoji: String) {
        VibrationUtils.vibrateShort(context)
        if (chatMessage.reactionsSelf?.contains(emoji) == true) {
            chatViewModel.deleteReaction(roomToken, chatMessage, emoji)
        } else {
            chatViewModel.addReaction(roomToken, chatMessage, emoji)
        }
    }

    override fun onLongClickReactions(chatMessage: ChatMessage) {
        ShowReactionsDialog(
            this,
            roomToken,
            chatMessage,
            conversationUser,
            participantPermissions.hasChatPermission(),
            ncApi
        ).show()
    }

    override fun onOpenMessageActionsDialog(chatMessage: ChatMessage) {
        openMessageActionsDialog(chatMessage)
    }

    override fun onMessageViewLongClick(view: View?, message: IMessage?) {
        openMessageActionsDialog(message)
    }

    override fun onPreviewMessageLongClick(chatMessage: ChatMessage) {
        onOpenMessageActionsDialog(chatMessage)
    }

    private fun openMessageActionsDialog(iMessage: IMessage?) {
        val message = iMessage as ChatMessage

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
                spreedCapabilities
            ).show()
        }
    }

    private fun isSystemMessage(message: ChatMessage): Boolean =
        ChatMessage.MessageType.SYSTEM_MESSAGE == message.getCalculateMessageType()

    fun deleteMessage(message: IMessage) {
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
                    conversationUser?.baseUrl!!,
                    roomToken,
                    message.id!!
                ),
                message.id!!
            )
        }
    }

    fun replyPrivately(message: IMessage?) {
        val apiVersion =
            ApiUtils.getConversationApiVersion(conversationUser!!, intArrayOf(ApiUtils.API_V4, 1))
        val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            version = apiVersion,
            baseUrl = conversationUser?.baseUrl!!,
            roomType = "1",
            invite = message?.user?.id?.substring(INVITE_LENGTH)
        )
        chatViewModel.createRoom(
            credentials!!,
            retrofitBucket.url!!,
            retrofitBucket.queryMap!!
        )
    }

    fun forwardMessage(message: IMessage?) {
        val bundle = Bundle()
        bundle.putBoolean(BundleKeys.KEY_FORWARD_MSG_FLAG, true)
        bundle.putString(BundleKeys.KEY_FORWARD_MSG_TEXT, message?.text)
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
        bundle.putString(BundleKeys.KEY_MESSAGE_ID, message!!.id)
        bundle.putInt(BundleKeys.KEY_CHAT_API_VERSION, chatApiVersion)

        binding.genericComposeView.apply {
            val shouldDismiss = mutableStateOf(false)
            setContent {
                DateTimeCompose(bundle).GetDateTimeDialog(shouldDismiss, this@ChatActivity)
            }
        }
    }

    fun markAsUnread(message: IMessage?) {
        val chatMessage = message as ChatMessage?
        if (chatMessage!!.previousMessageId > NO_PREVIOUS_MESSAGE_ID) {
            chatViewModel.setChatReadMarker(
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

    fun copyMessage(message: IMessage?) {
        val clipboardManager =
            getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(
            resources?.getString(R.string.nc_app_product_name),
            message?.text
        )
        clipboardManager.setPrimaryClip(clipData)
    }

    fun translateMessage(message: IMessage?) {
        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_TRANSLATE_MESSAGE, message?.text)

        val intent = Intent(this, TranslateActivity::class.java)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    fun share(message: ChatMessage) {
        val filename = message.selectedIndividualHashMap!!["name"]
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
        val filename = message.selectedIndividualHashMap!!["name"]
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
            message.selectedIndividualHashMap!!["name"]!!
        )
        saveFragment.show(
            supportFragmentManager,
            SaveToStorageDialogFragment.TAG
        )
    }

    fun checkIfSaveable(message: ChatMessage) {
        val filename = message.selectedIndividualHashMap!!["name"]
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

    fun shareToNotes(message: ChatMessage, roomToken: String) {
        var shareUri: Uri? = null
        val data: HashMap<String?, String?>?
        var metaData: String = ""
        var objectId: String = ""
        if (message.hasFileAttachment()) {
            val filename = message.selectedIndividualHashMap!!["name"]
            path = applicationContext.cacheDir.absolutePath + "/" + filename
            shareUri = FileProvider.getUriForFile(
                this,
                BuildConfig.APPLICATION_ID,
                File(path)
            )

            this.grantUriPermission(
                applicationContext.packageName,
                shareUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } else if (message.hasGeoLocation()) {
            data = message.messageParameters?.get("object")
            objectId = data?.get("id")!!
            val name = data["name"]!!
            val lat = data["latitude"]!!
            val lon = data["longitude"]!!
            metaData =
                "{\"type\":\"geo-location\",\"id\":\"geo:$lat,$lon\",\"latitude\":\"$lat\"," +
                "\"longitude\":\"$lon\",\"name\":\"$name\"}"
        }

        shareToNotes(shareUri, roomToken, message, objectId, metaData)
    }

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
                uploadFile(shareUri.toString(), true, token = roomToken)
                Snackbar.make(binding.root, R.string.nc_message_sent, Snackbar.LENGTH_SHORT).show()
            }

            ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE -> {
                val caption = if (message.message != "{file}") message.message else ""
                if (null != shareUri) {
                    try {
                        context.contentResolver.openInputStream(shareUri)?.close()
                        uploadFile(shareUri.toString(), false, caption!!, roomToken)
                        Snackbar.make(binding.root, R.string.nc_message_sent, Snackbar.LENGTH_SHORT).show()
                    } catch (e: java.lang.Exception) {
                        Log.w(TAG, "File corresponding to the uri does not exist $shareUri")
                        downloadFileToCache(message, false) {
                            uploadFile(shareUri.toString(), false, caption!!, roomToken)
                            Snackbar.make(binding.root, R.string.nc_message_sent, Snackbar.LENGTH_SHORT).show()
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
                Snackbar.make(binding.root, R.string.nc_message_sent, Snackbar.LENGTH_SHORT).show()
            }

            ChatMessage.MessageType.REGULAR_TEXT_MESSAGE -> {
                val apiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))
                chatViewModel.shareToNotes(
                    credentials!!,
                    ApiUtils.getUrlForChat(apiVersion, conversationUser!!.baseUrl!!, roomToken),
                    message.message!!,
                    conversationUser!!.displayName!!
                )
                Snackbar.make(binding.root, R.string.nc_message_sent, Snackbar.LENGTH_SHORT).show()
            }

            else -> {}
        }
    }

    fun openInFilesApp(message: ChatMessage) {
        val keyID = message.selectedIndividualHashMap!![PreviewMessageViewHolder.KEY_ID]
        val link = message.selectedIndividualHashMap!!["link"]
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
            message.user.id.startsWith("users/") &&
            message.user.id.substring(ACTOR_LENGTH) != currentConversation?.actorId &&
            currentConversation?.type != ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ||
            isShowMessageDeletionButton(message) ||
            // delete
            ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == message.getCalculateMessageType() ||
            // forward
            message.previousMessageId > NO_PREVIOUS_MESSAGE_ID &&
            // mark as unread
            ChatMessage.MessageType.SYSTEM_MESSAGE != message.getCalculateMessageType() &&
            BuildConfig.DEBUG

    private fun setMessageAsDeleted(message: IMessage?) {
        val messageTemp = message as ChatMessage
        messageTemp.isDeleted = true
        messageTemp.message = getString(R.string.message_deleted_by_you)

        messageTemp.isOneToOneConversation =
            currentConversation?.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        messageTemp.activeUser = conversationUser

        adapter?.update(messageTemp)
    }

    private fun setMessageAsEdited(message: IMessage?, newString: String) {
        val messageTemp = message as ChatMessage
        messageTemp.lastEditTimestamp = message.lastEditTimestamp
        messageTemp.message = newString

        val index = adapter?.getMessagePositionById(messageTemp.id)!!
        if (index > 0) {
            val adapterMsg = adapter?.items?.get(index)?.item as ChatMessage
            messageTemp.parentMessageId = adapterMsg.parentMessageId
        }

        messageTemp.isOneToOneConversation =
            currentConversation?.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        messageTemp.activeUser = conversationUser

        adapter?.update(messageTemp)
    }

    private fun updateMessageInsideAdapter(message: IMessage?) {
        message?.let {
            val messageTemp = message as ChatMessage

            // TODO is this needed?
            messageTemp.isOneToOneConversation =
                currentConversation?.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
            messageTemp.activeUser = conversationUser

            adapter?.update(messageTemp)
        }
    }

    fun updateUiToAddReaction(message: ChatMessage, emoji: String) {
        if (message.reactions == null) {
            message.reactions = LinkedHashMap()
        }

        if (message.reactionsSelf == null) {
            message.reactionsSelf = ArrayList()
        }

        var amount = message.reactions!![emoji]
        if (amount == null) {
            amount = 0
        }
        message.reactions!![emoji] = amount + 1
        message.reactionsSelf!!.add(emoji)
        adapter?.update(message)
    }

    fun updateUiToDeleteReaction(message: ChatMessage, emoji: String) {
        if (message.reactions == null) {
            message.reactions = LinkedHashMap()
        }

        if (message.reactionsSelf == null) {
            message.reactionsSelf = ArrayList()
        }

        var amount = message.reactions!![emoji]
        if (amount == null) {
            amount = 0
        }
        message.reactions!![emoji] = amount - 1
        if (message.reactions!![emoji]!! <= 0) {
            message.reactions!!.remove(emoji)
        }
        message.reactionsSelf!!.remove(emoji)
        adapter?.update(message)
    }

    private fun isShowMessageDeletionButton(message: ChatMessage): Boolean {
        val isUserAllowedByPrivileges = userAllowedByPrivilages(message)

        val isOlderThanSixHours = message
            .createdAt
            .before(Date(System.currentTimeMillis() - AGE_THRESHOLD_FOR_DELETE_MESSAGE))
        val hasDeleteMessagesUnlimitedCapability = CapabilitiesUtil.hasSpreedFeatureCapability(
            spreedCapabilities,
            SpreedFeatures.DELETE_MESSAGES_UNLIMITED
        )

        return when {
            !isUserAllowedByPrivileges -> false
            !hasDeleteMessagesUnlimitedCapability && isOlderThanSixHours -> false
            message.systemMessageType != ChatMessage.SystemMessageType.DUMMY -> false
            message.isDeleted -> false
            !CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.DELETE_MESSAGES) -> false
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

    override fun hasContentFor(message: ChatMessage, type: Byte): Boolean =
        when (type) {
            CONTENT_TYPE_LOCATION -> message.hasGeoLocation()
            CONTENT_TYPE_VOICE_MESSAGE -> message.isVoiceMessage
            CONTENT_TYPE_POLL -> message.isPoll()
            CONTENT_TYPE_LINK_PREVIEW -> message.isLinkPreview()
            CONTENT_TYPE_SYSTEM_MESSAGE -> !TextUtils.isEmpty(message.systemMessage)
            CONTENT_TYPE_UNREAD_NOTICE_MESSAGE -> message.id == UNREAD_MESSAGES_MARKER_ID.toString()
            CONTENT_TYPE_CALL_STARTED -> message.id == "-2"
            CONTENT_TYPE_DECK_CARD -> message.isDeckCard()

            else -> false
        }

    private fun processMostRecentMessage(recent: ChatMessage) {
        when (recent.systemMessageType) {
            ChatMessage.SystemMessageType.CALL_STARTED -> {
                if (!callStarted) {
                    messageInputViewModel.showCallStartedIndicator(recent, true)
                    callStarted = true
                }
            }

            ChatMessage.SystemMessageType.CALL_ENDED,
            ChatMessage.SystemMessageType.CALL_MISSED,
            ChatMessage.SystemMessageType.CALL_TRIED,
            ChatMessage.SystemMessageType.CALL_ENDED_EVERYONE -> {
                callStarted = false
                messageInputViewModel.showCallStartedIndicator(recent, false)
            }

            else -> {}
        }
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

    fun jumpToQuotedMessage(parentMessage: ChatMessage) {
        var foundMessage = false
        for (position in 0 until (adapter!!.items.size)) {
            val currentItem = adapter?.items?.get(position)?.item
            if (currentItem is ChatMessage && currentItem.id == parentMessage.id) {
                layoutManager!!.scrollToPosition(position)
                foundMessage = true
                break
            }
        }
        if (!foundMessage) {
            Log.d(TAG, "quoted message with id " + parentMessage.id + " was not found in adapter")
            // TODO: show better info
            // TODO: improve handling how this can be avoided. E.g. loading chat until message is reached...
            Snackbar.make(binding.root, "Message was not found", Snackbar.LENGTH_LONG).show()
        }
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
        private const val VOICE_MESSAGE_META_DATA = "{\"messageType\":\"voice-message\"}"
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
    }
}
