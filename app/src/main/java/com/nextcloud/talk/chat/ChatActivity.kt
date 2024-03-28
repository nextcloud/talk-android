/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * @author Tim Krüger
 * @author Ezhil Shanmugham
 * Copyright (C) 2021-2022 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021-2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * Copyright (C) 2024 Giacomo Pacini <giacomo@paciosoft.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.chat

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.content.res.Resources
import android.database.Cursor
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.SystemClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.RelativeLayout.BELOW
import android.widget.RelativeLayout.LayoutParams
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import androidx.core.graphics.drawable.toBitmap
import androidx.core.text.bold
import androidx.core.widget.doAfterTextChanged
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.widget.EmojiTextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
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
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.activities.TakePhotoActivity
import com.nextcloud.talk.adapters.messages.CallStartedMessageInterface
import com.nextcloud.talk.adapters.messages.CallStartedViewHolder
import com.nextcloud.talk.adapters.messages.CommonMessageInterface
import com.nextcloud.talk.adapters.messages.IncomingLinkPreviewMessageViewHolder
import com.nextcloud.talk.adapters.messages.IncomingLocationMessageViewHolder
import com.nextcloud.talk.adapters.messages.IncomingPollMessageViewHolder
import com.nextcloud.talk.adapters.messages.IncomingPreviewMessageViewHolder
import com.nextcloud.talk.adapters.messages.IncomingTextMessageViewHolder
import com.nextcloud.talk.adapters.messages.IncomingVoiceMessageViewHolder
import com.nextcloud.talk.adapters.messages.MessagePayload
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
import com.nextcloud.talk.callbacks.MentionAutocompleteCallback
import com.nextcloud.talk.chat.viewmodels.ChatViewModel
import com.nextcloud.talk.conversationinfo.ConversationInfoActivity
import com.nextcloud.talk.conversationlist.ConversationsListActivity
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
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.ConversationReadOnlyState
import com.nextcloud.talk.models.domain.ConversationType
import com.nextcloud.talk.models.domain.LobbyState
import com.nextcloud.talk.models.domain.ObjectType
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.models.json.mention.Mention
import com.nextcloud.talk.models.json.signaling.NCSignalingMessage
import com.nextcloud.talk.polls.ui.PollCreateDialogFragment
import com.nextcloud.talk.presenters.MentionAutocompletePresenter
import com.nextcloud.talk.remotefilebrowser.activities.RemoteFileBrowserActivity
import com.nextcloud.talk.shareditems.activities.SharedItemsActivity
import com.nextcloud.talk.signaling.SignalingMessageReceiver
import com.nextcloud.talk.signaling.SignalingMessageSender
import com.nextcloud.talk.translate.ui.TranslateActivity
import com.nextcloud.talk.ui.MicInputCloud
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet
import com.nextcloud.talk.ui.dialog.AttachmentDialog
import com.nextcloud.talk.ui.dialog.DateTimePickerFragment
import com.nextcloud.talk.ui.dialog.FileAttachmentPreviewFragment
import com.nextcloud.talk.ui.dialog.MessageActionsDialog
import com.nextcloud.talk.ui.dialog.SaveToStorageDialogFragment
import com.nextcloud.talk.ui.dialog.ShowReactionsDialog
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
import com.nextcloud.talk.utils.ImageEmojiEditText
import com.nextcloud.talk.utils.MagicCharPolicy
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.ParticipantPermissions
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.UriUtils
import com.nextcloud.talk.utils.VibrationUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CALL_VOICE_ONLY
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_NAME
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_PATHS
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INTERNAL_USER_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_BREAKOUT_ROOM
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_IS_MODERATOR
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_RECORDING_STATE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_START_CALL_AFTER_ROOM_SWITCH
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_SWITCH_TO_ROOM
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.rx.DisposableSet
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder
import com.nextcloud.talk.utils.text.Spans
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper
import com.nextcloud.talk.webrtc.WebSocketInstance
import com.otaliastudios.autocomplete.Autocomplete
import com.stfalcon.chatkit.commons.ImageLoader
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.messages.MessageHolders
import com.stfalcon.chatkit.messages.MessageHolders.ContentChecker
import com.stfalcon.chatkit.messages.MessagesListAdapter
import com.stfalcon.chatkit.utils.DateFormatter
import com.vanniktech.emoji.EmojiPopup
import io.reactivex.subjects.BehaviorSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlin.collections.set
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt

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
    lateinit var currentUserProvider: CurrentUserProviderNew

    @Inject
    lateinit var permissionUtil: PlatformPermissionUtil

    @Inject
    lateinit var dateUtils: DateUtils

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    lateinit var chatViewModel: ChatViewModel

    private lateinit var editMessage: ChatMessage

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
    private var globalLastKnownFutureMessageId = -1
    private var globalLastKnownPastMessageId = -1
    var adapter: TalkMessagesListAdapter<ChatMessage>? = null
    private var mentionAutocomplete: Autocomplete<*>? = null
    var layoutManager: LinearLayoutManager? = null
    var pullChatMessagesPending = false
    var newMessagesCount = 0
    var startCallFromNotification: Boolean = false
    var startCallFromRoomSwitch: Boolean = false
    lateinit var roomId: String
    var voiceOnly: Boolean = true
    var isFirstMessagesProcessing = true
    private var emojiPopup: EmojiPopup? = null
    private lateinit var path: String

    var myFirstMessage: CharSequence? = null
    var checkingLobbyStatus: Boolean = false

    private var conversationInfoMenuItem: MenuItem? = null
    private var conversationVoiceCallMenuItem: MenuItem? = null
    private var conversationVideoMenuItem: MenuItem? = null
    private var conversationSharedItemsItem: MenuItem? = null

    private var webSocketInstance: WebSocketInstance? = null
    private var signalingMessageSender: SignalingMessageSender? = null

    var getRoomInfoTimerHandler: Handler? = null
    var pastPreconditionFailed = false
    var futurePreconditionFailed = false

    private val filesToUpload: MutableList<String> = ArrayList()
    private lateinit var sharedText: String
    var currentVoiceRecordFile: String = ""
    var isVoiceRecordingLocked: Boolean = false
    private var isVoicePreviewPlaying: Boolean = false

    private var recorder: MediaRecorder? = null

    private enum class MediaRecorderState {
        INITIAL,
        INITIALIZED,
        CONFIGURED,
        PREPARED,
        RECORDING,
        RELEASED,
        ERROR
    }

    private val editableBehaviorSubject = BehaviorSubject.createDefault(false)
    private val editedTextBehaviorSubject = BehaviorSubject.createDefault("")

    private var mediaRecorderState: MediaRecorderState = MediaRecorderState.INITIAL

    private var voicePreviewMediaPlayer: MediaPlayer? = null
    private var voicePreviewObjectAnimator: ObjectAnimator? = null

    var mediaPlayer: MediaPlayer? = null
    lateinit var mediaPlayerHandler: Handler

    private var currentlyPlayedVoiceMessage: ChatMessage? = null

    private lateinit var micInputAudioRecorder: AudioRecord
    private var micInputAudioRecordThread: Thread? = null
    private var isMicInputAudioThreadRunning: Boolean = false
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var voiceRecordDuration = 0L
    private var voiceRecordPauseTime = 0L

    // messy workaround for a mediaPlayer bug, don't delete
    private var lastRecordMediaPosition: Int = 0
    private var lastRecordedSeeked: Boolean = false

    private val audioFocusChangeListener = getAudioFocusChangeListener()

    private val noisyAudioStreamReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            chatViewModel.isPausedDueToBecomingNoisy = true
            if (isVoicePreviewPlaying) {
                pausePreviewVoicePlaying()
            }
            if (currentlyPlayedVoiceMessage != null) {
                pausePlayback(currentlyPlayedVoiceMessage!!)
            }
        }
    }

    private lateinit var participantPermissions: ParticipantPermissions

    private var videoURI: Uri? = null

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (currentlyPlayedVoiceMessage != null) {
                stopMediaPlayer(currentlyPlayedVoiceMessage!!)
            }
            val intent = Intent(this@ChatActivity, ConversationsListActivity::class.java)
            intent.putExtras(Bundle())
            startActivity(intent)
        }
    }

    var typingTimer: CountDownTimer? = null
    var typedWhileTypingTimerIsRunning: Boolean = false
    val typingParticipants = HashMap<String, TypingParticipant>()

    var callStarted = false
    private var voiceMessageToRestoreId = ""
    private var voiceMessageToRestoreAudioPosition = 0
    private var voiceMessageToRestoreWasPlaying = false

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

        binding.progressBar.visibility = View.VISIBLE

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        initObservers()

        if (savedInstanceState != null) {
            // Restore value of members from saved state
            var voiceMessageId = savedInstanceState.getString(CURRENT_AUDIO_MESSAGE_KEY, "")
            var voiceMessagePosition = savedInstanceState.getInt(CURRENT_AUDIO_POSITION_KEY, 0)
            var wasAudioPLaying = savedInstanceState.getBoolean(CURRENT_AUDIO_WAS_PLAYING_KEY, false)
            if (!voiceMessageId.equals("")) {
                Log.d(RESUME_AUDIO_TAG, "restored voice messageID: " + voiceMessageId)
                Log.d(RESUME_AUDIO_TAG, "audio position: " + voiceMessagePosition)
                Log.d(RESUME_AUDIO_TAG, "audio was playing: " + wasAudioPLaying.toString())
                voiceMessageToRestoreId = voiceMessageId
                voiceMessageToRestoreAudioPosition = voiceMessagePosition
                voiceMessageToRestoreWasPlaying = wasAudioPLaying
            } else {
                Log.d(RESUME_AUDIO_TAG, "stored voice message id is empty, not resuming audio playing")
                voiceMessageToRestoreId = ""
                voiceMessageToRestoreAudioPosition = 0
                voiceMessageToRestoreWasPlaying = false
            }
        } else {
            voiceMessageToRestoreId = ""
            voiceMessageToRestoreAudioPosition = 0
            voiceMessageToRestoreWasPlaying = false
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

        roomId = extras?.getString(KEY_ROOM_ID).orEmpty()
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
        context.getSharedPreferences(localClassName, MODE_PRIVATE).apply {
            val text = getString(roomToken, "")
            val cursor = getInt(roomToken + CURSOR_KEY, 0)
            binding.messageInputView.messageInput.setText(text)
            binding.messageInputView.messageInput.setSelection(cursor)
        }
        this.lifecycle.addObserver(AudioUtils)
        this.lifecycle.addObserver(ChatViewModel.LifeCycleObserver)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (currentlyPlayedVoiceMessage != null) {
            outState.putString(CURRENT_AUDIO_MESSAGE_KEY, currentlyPlayedVoiceMessage!!.getId())
            outState.putInt(CURRENT_AUDIO_POSITION_KEY, currentlyPlayedVoiceMessage!!.voiceMessagePlayedSeconds)
            outState.putBoolean(CURRENT_AUDIO_WAS_PLAYING_KEY, currentlyPlayedVoiceMessage!!.isPlayingVoiceMessage)
            Log.d(RESUME_AUDIO_TAG, "Stored current audio message ID: " + currentlyPlayedVoiceMessage!!.getId())
            Log.d(
                RESUME_AUDIO_TAG,
                "Audio Position: " + currentlyPlayedVoiceMessage!!.voiceMessagePlayedSeconds
                    .toString() + " | isPLaying: " + currentlyPlayedVoiceMessage!!.isPlayingVoiceMessage
            )
        }
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        active = false
        stopPreviewVoicePlaying()
        if (isMicInputAudioThreadRunning) {
            stopMicInputRecordingAnimation()
        }
        if (mediaRecorderState == MediaRecorderState.RECORDING) {
            stopAudioRecording()
        }
        val text = binding.messageInputView.messageInput.text.toString()
        val cursor = binding.messageInputView.messageInput.selectionStart
        val previous = context.getSharedPreferences(localClassName, MODE_PRIVATE).getString(roomToken, "null")
        if (text != previous) {
            context.getSharedPreferences(localClassName, MODE_PRIVATE).edit().apply {
                putString(roomToken, text)
                putInt(roomToken + CURSOR_KEY, cursor)
                apply()
            }
        }
        this.lifecycle.removeObserver(AudioUtils)
        this.lifecycle.removeObserver(ChatViewModel.LifeCycleObserver)
    }

    @SuppressLint("NotifyDataSetChanged")
    @Suppress("LongMethod")
    private fun initObservers() {
        Log.d(TAG, "initObservers Called")
        chatViewModel.getRoomViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.GetRoomSuccessState -> {
                    currentConversation = state.conversationModel
                    logConversationInfos("GetRoomSuccessState")
                    chatViewModel.getCapabilities(conversationUser!!, roomToken, currentConversation!!)
                }

                is ChatViewModel.GetRoomErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }

        chatViewModel.getCapabilitiesViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.GetCapabilitiesSuccessState -> {
                    spreedCapabilities = state.spreedCapabilities
                    chatApiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))

                    invalidateOptionsMenu()
                    initMessageInputView()

                    if (conversationUser?.userId != "?" &&
                        CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.MENTION_FLAG)
                    ) {
                        binding.chatToolbar.setOnClickListener { v -> showConversationInfoScreen() }
                    }

                    if (adapter == null) {
                        initAdapter()
                        binding.messagesListView.setAdapter(adapter)
                    }

                    layoutManager = binding.messagesListView.layoutManager as LinearLayoutManager?

                    loadAvatarForStatusBar()
                    setActionBarTitle()
                    participantPermissions = ParticipantPermissions(spreedCapabilities, currentConversation!!)

                    setupSwipeToReply()
                    setupMentionAutocomplete()
                    checkShowCallButtons()
                    checkShowMessageInputView()
                    checkLobbyState()

                    if (!validSessionId()) {
                        joinRoomWithPassword()
                    } else {
                        Log.d(TAG, "already inConversation. joinRoomWithPassword is skipped")
                    }

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
                            chatViewModel.getRoom(conversationUser!!, roomToken)
                        },
                        delayForRecursiveCall
                    )

                    chatViewModel.refreshChatParams(
                        setupFieldsForPullChatMessages(
                            false,
                            0,
                            false
                        )
                    )
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
                    ApplicationWideCurrentRoomHolder.getInstance().currentRoomId = currentConversation!!.roomId
                    ApplicationWideCurrentRoomHolder.getInstance().currentRoomToken = currentConversation!!.token
                    ApplicationWideCurrentRoomHolder.getInstance().userInRoom = conversationUser

                    logConversationInfos("joinRoomWithPassword#onNext")

                    if (webSocketInstance != null) {
                        webSocketInstance?.joinRoomWithRoomTokenAndSession(
                            roomToken,
                            sessionIdAfterRoomJoined
                        )
                    }
                    if (startCallFromNotification != null && startCallFromNotification) {
                        startCallFromNotification = false
                        startACall(voiceOnly, false)
                    }

                    if (startCallFromRoomSwitch) {
                        startCallFromRoomSwitch = false
                        startACall(voiceOnly, true)
                    }
                }

                is ChatViewModel.JoinRoomErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }

        chatViewModel.leaveRoomViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.LeaveRoomSuccessState -> {
                    logConversationInfos("leaveRoom#onNext")

                    sendStopTypingMessage()

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

        chatViewModel.sendChatMessageViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.SendChatMessageSuccessState -> {
                    myFirstMessage = state.message

                    if (binding.popupBubbleView.isShown == true) {
                        binding.popupBubbleView.hide()
                    }
                    binding.messagesListView.smoothScrollToPosition(0)
                }

                is ChatViewModel.SendChatMessageErrorState -> {
                    if (state.e is HttpException) {
                        val code = state.e.code()
                        if (code.toString().startsWith("2")) {
                            myFirstMessage = state.message

                            if (binding.popupBubbleView.isShown == true) {
                                binding.popupBubbleView.hide()
                            }

                            binding.messagesListView.smoothScrollToPosition(0)
                        }
                    }
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

                    chatViewModel.refreshChatParams(
                        setupFieldsForPullChatMessages(
                            true,
                            globalLastKnownFutureMessageId,
                            true
                        )
                    )
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
                    bundle.putString(KEY_ROOM_ID, state.roomOverall.ocs!!.data!!.roomId)

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

        chatViewModel.getFieldMapForChat.observe(this) { fieldMap ->
            if (fieldMap.isNotEmpty()) {
                chatViewModel.pullChatMessages(
                    credentials!!,
                    ApiUtils.getUrlForChat(chatApiVersion, conversationUser?.baseUrl, roomToken)
                )
            }
        }

        chatViewModel.pullChatMessageViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.PullChatMessageSuccessState -> {
                    Log.d(TAG, "PullChatMessageSuccess: Code: ${state.response.code()}")
                    when (state.response.code()) {
                        HTTP_CODE_OK -> {
                            Log.d(TAG, "lookIntoFuture: ${state.lookIntoFuture}")
                            val chatOverall = state.response.body() as ChatOverall?
                            var chatMessageList = chatOverall?.ocs!!.data!!

                            processHeaderChatLastGiven(state.response, state.lookIntoFuture)

                            chatMessageList = handleSystemMessages(chatMessageList)

                            if (chatMessageList.size == 0) {
                                chatViewModel.refreshChatParams(
                                    setupFieldsForPullChatMessages(
                                        true,
                                        globalLastKnownFutureMessageId,
                                        true
                                    )
                                )
                                return@observe
                            }

                            determinePreviousMessageIds(chatMessageList)

                            handleExpandableSystemMessages(chatMessageList)

                            if (chatMessageList.isNotEmpty() &&
                                ChatMessage.SystemMessageType.CLEARED_CHAT == chatMessageList[0].systemMessageType
                            ) {
                                adapter?.clear()
                                adapter?.notifyDataSetChanged()
                            }

                            var lastAdapterId = getLastAdapterId()

                            if (
                                state.lookIntoFuture &&
                                lastAdapterId != 0 &&
                                chatMessageList[0].jsonMessageId > lastAdapterId
                            ) {
                                processMessagesFromTheFuture(chatMessageList)
                            } else if (!state.lookIntoFuture) {
                                processMessagesNotFromTheFuture(chatMessageList)
                                collapseSystemMessages()
                            }

                            val newXChatLastCommonRead = state.response.headers()["X-Chat-Last-Common-Read"]?.let {
                                Integer.parseInt(it)
                            }

                            updateReadStatusOfAllMessages(newXChatLastCommonRead)

                            processCallStartedMessages(chatMessageList)

                            adapter?.notifyDataSetChanged()

                            chatViewModel.refreshChatParams(
                                setupFieldsForPullChatMessages(
                                    true,
                                    newXChatLastCommonRead,
                                    true
                                )
                            )
                        }

                        HTTP_CODE_NOT_MODIFIED -> {
                            chatViewModel.refreshChatParams(
                                setupFieldsForPullChatMessages(
                                    true,
                                    globalLastKnownFutureMessageId,
                                    true
                                ),
                                true
                            )
                        }

                        HTTP_CODE_PRECONDITION_FAILED -> {
                            chatViewModel.refreshChatParams(
                                setupFieldsForPullChatMessages(
                                    true,
                                    globalLastKnownFutureMessageId,
                                    true
                                ),
                                true
                            )
                        }

                        else -> {}
                    }

                    processExpiredMessages()
                    if (isFirstMessagesProcessing) {
                        cancelNotificationsForCurrentConversation()
                        isFirstMessagesProcessing = false
                        binding.progressBar.visibility = View.GONE
                        binding.messagesListView.visibility = View.VISIBLE

                        collapseSystemMessages()
                    }
                }

                is ChatViewModel.PullChatMessageCompleteState -> {
                    Log.d(TAG, "PullChatMessageCompleted")
                }

                is ChatViewModel.PullChatMessageErrorState -> {
                    Log.d(TAG, "PullChatMessageError")
                }

                else -> {}
            }
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

        chatViewModel.editMessageViewState.observe(this) { state ->
            when (state) {
                is ChatViewModel.EditMessageSuccessState -> {
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
                    clearEditUI()
                }

                is ChatViewModel.EditMessageErrorState -> {
                    Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
                }

                else -> {}
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    override fun onResume() {
        super.onResume()

        logConversationInfos("onResume")

        pullChatMessagesPending = false

        setupWebsocket()
        webSocketInstance?.getSignalingMessageReceiver()?.addListener(localParticipantMessageListener)
        webSocketInstance?.getSignalingMessageReceiver()?.addListener(conversationMessageListener)

        initSmileyKeyboardToggler()

        themeMessageInputView()

        cancelNotificationsForCurrentConversation()

        chatViewModel.getRoom(conversationUser!!, roomToken)

        actionBar?.show()

        setupSwipeToReply()

        binding.popupBubbleView.setRecyclerView(binding.messagesListView)

        binding.popupBubbleView.setPopupBubbleListener { context ->
            if (newMessagesCount != 0) {
                val scrollPosition = if (newMessagesCount - 1 < 0) {
                    0
                } else {
                    newMessagesCount - 1
                }
                Handler().postDelayed(
                    {
                        binding.messagesListView.smoothScrollToPosition(scrollPosition)
                    },
                    NEW_MESSAGES_POPUP_BUBBLE_DELAY
                )
            }
        }

        binding.scrollDownButton.setOnClickListener {
            binding.messagesListView.scrollToPosition(0)
            it.visibility = View.GONE
        }

        binding.let { viewThemeUtils.material.colorMaterialButtonPrimaryTonal(it.scrollDownButton) }

        binding.let { viewThemeUtils.material.themeFAB(it.voiceRecordingLock) }

        binding.let { viewThemeUtils.material.colorMaterialButtonPrimaryFilled(it.popupBubbleView) }

        binding.messageInputView.setPadding(0, 0, 0, 0)

        binding.messagesListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    if (layoutManager!!.findFirstCompletelyVisibleItemPosition() > 0) {
                        binding.scrollDownButton.visibility = View.VISIBLE
                    } else {
                        binding.scrollDownButton.visibility = View.GONE
                    }

                    if (newMessagesCount != 0 && layoutManager != null) {
                        if (layoutManager!!.findFirstCompletelyVisibleItemPosition() < newMessagesCount) {
                            newMessagesCount = 0

                            if (binding.popupBubbleView.isShown == true) {
                                binding.popupBubbleView.hide()
                            }
                        }
                    }
                }
            }
        })

        loadAvatarForStatusBar()
        setActionBarTitle()
        viewThemeUtils.material.colorToolbarOverflowIcon(binding.chatToolbar)
    }

    private fun initMessageInputView() {
        val filters = arrayOfNulls<InputFilter>(1)
        val lengthFilter = CapabilitiesUtil.getMessageMaxLength(spreedCapabilities)

        binding.editView.editMessageView.visibility = View.GONE

        if (editableBehaviorSubject.value!!) {
            val editableText = Editable.Factory.getInstance().newEditable(editMessage.message)
            binding.messageInputView.inputEditText.text = editableText
            binding.messageInputView.inputEditText.setSelection(editableText.length)
            binding.editView.editMessage.setText(editMessage.message)
        }

        filters[0] = InputFilter.LengthFilter(lengthFilter)
        binding.messageInputView.inputEditText?.filters = filters

        binding.messageInputView.inputEditText?.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                // unused atm
            }

            @Suppress("Detekt.TooGenericExceptionCaught")
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                updateOwnTypingStatus(s)

                if (s.length >= lengthFilter) {
                    binding.messageInputView.inputEditText?.error = String.format(
                        Objects.requireNonNull<Resources>(resources).getString(R.string.nc_limit_hit),
                        lengthFilter.toString()
                    )
                } else {
                    binding.messageInputView.inputEditText?.error = null
                }

                val editable = binding.messageInputView.inputEditText?.editableText
                editedTextBehaviorSubject.onNext(editable.toString().trim())

                if (editable != null && binding.messageInputView.inputEditText != null) {
                    val mentionSpans = editable.getSpans(
                        0,
                        binding.messageInputView.inputEditText!!.length(),
                        Spans.MentionChipSpan::class.java
                    )
                    var mentionSpan: Spans.MentionChipSpan
                    for (i in mentionSpans.indices) {
                        mentionSpan = mentionSpans[i]
                        if (start >= editable.getSpanStart(mentionSpan) &&
                            start < editable.getSpanEnd(mentionSpan)
                        ) {
                            if (editable.subSequence(
                                    editable.getSpanStart(mentionSpan),
                                    editable.getSpanEnd(mentionSpan)
                                ).toString().trim { it <= ' ' } != mentionSpan.label
                            ) {
                                editable.removeSpan(mentionSpan)
                            }
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable) {
                // unused atm
            }
        })

        // Image keyboard support
        // See: https://developer.android.com/guide/topics/text/image-keyboard

        (binding.messageInputView.inputEditText as ImageEmojiEditText).onCommitContentListener = {
            uploadFile(it.toString(), false)
        }
        initVoiceRecordButton()
        if (editableBehaviorSubject.value!!) {
            setEditUI()
        }

        if (sharedText.isNotEmpty()) {
            binding.messageInputView.inputEditText?.setText(sharedText)
        }

        binding.messageInputView.setAttachmentsListener {
            AttachmentDialog(this, this).show()
        }

        binding.messageInputView.button?.setOnClickListener {
            submitMessage(false)
        }

        binding.messageInputView.editMessageButton.setOnClickListener {
            if (editMessage.message == editedTextBehaviorSubject.value!!) {
                clearEditUI()
                return@setOnClickListener
            }
            editMessageAPI(editMessage, editedMessageText = editedTextBehaviorSubject.value!!)
        }
        binding.editView.clearEdit.setOnClickListener {
            clearEditUI()
        }

        if (CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.SILENT_SEND)) {
            binding.messageInputView.button?.setOnLongClickListener {
                showSendButtonMenu()
                true
            }
        }

        binding.messageInputView.button?.contentDescription =
            resources?.getString(R.string.nc_description_send_message_button)
    }

    private fun editMessageAPI(message: ChatMessage, editedMessageText: String) {
        var apiVersion = 1
        // FIXME Fix API checking with guests?
        if (conversationUser != null) {
            apiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))
        }

        chatViewModel.editChatMessage(
            credentials!!,
            ApiUtils.getUrlForChatMessage(
                apiVersion,
                conversationUser?.baseUrl!!,
                roomToken,
                message.id
            ),
            editedMessageText
        )
    }

    private fun getLastAdapterId(): Int {
        var lastId = 0
        if (adapter?.items?.size != 0) {
            val item = adapter?.items?.get(0)?.item
            if (item != null) {
                lastId = (item as ChatMessage).jsonMessageId
            } else {
                lastId = 0
            }
        }
        return lastId
    }

    private fun setEditUI() {
        binding.messageInputView.messageSendButton.visibility = View.GONE
        binding.messageInputView.recordAudioButton.visibility = View.GONE
        binding.messageInputView.editMessageButton.visibility = View.VISIBLE
        binding.editView.editMessageView.visibility = View.VISIBLE
        binding.messageInputView.attachmentButton.visibility = View.GONE
    }

    private fun clearEditUI() {
        binding.messageInputView.editMessageButton.visibility = View.GONE
        editableBehaviorSubject.onNext(false)
        binding.messageInputView.inputEditText.setText("")
        binding.editView.editMessageView.visibility = View.GONE
        binding.messageInputView.attachmentButton.visibility = View.VISIBLE
    }

    private fun themeMessageInputView() {
        binding.messageInputView.button?.let { viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY) }

        binding.messageInputView.findViewById<ImageButton>(R.id.cancelReplyButton)?.setOnClickListener {
            cancelReply()
        }

        binding.messageInputView.findViewById<ImageButton>(R.id.cancelReplyButton)?.let {
            viewThemeUtils.platform
                .themeImageButton(it)
        }

        binding.messageInputView.findViewById<MaterialButton>(R.id.playPauseBtn)?.let {
            viewThemeUtils.material.colorMaterialButtonText(it)
        }

        binding.messageInputView.findViewById<SeekBar>(R.id.seekbar)?.let {
            viewThemeUtils.platform.themeHorizontalSeekBar(it)
        }

        binding.messageInputView.findViewById<ImageView>(R.id.deleteVoiceRecording)?.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }
        binding.messageInputView.findViewById<ImageView>(R.id.sendVoiceRecording)?.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }

        binding.messageInputView.findViewById<ImageView>(R.id.microphoneEnabledInfo)?.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }

        binding.messageInputView.findViewById<LinearLayout>(R.id.voice_preview_container)?.let {
            viewThemeUtils.talk.themeOutgoingMessageBubble(it, true, false)
        }

        binding.messageInputView.findViewById<MicInputCloud>(R.id.micInputCloud)?.let {
            viewThemeUtils.talk.themeMicInputCloud(it)
        }
        binding.messageInputView.findViewById<ImageView>(R.id.editMessageButton)?.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }
        binding.editView.clearEdit.let {
            viewThemeUtils.platform.colorImageView(it, ColorRole.PRIMARY)
        }
    }

    private fun setupActionBar() {
        setSupportActionBar(binding.chatToolbar)
        binding.chatToolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setIcon(ColorDrawable(resources!!.getColor(R.color.transparent, null)))
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
                    pausePlayback(message)
                } else {
                    val retrieved = appPreferences.getWaveFormFromFile(filename)
                    if (retrieved.isEmpty()) {
                        setUpWaveform(message)
                    } else {
                        startPlayback(message)
                    }
                }
            } else {
                Log.d(TAG, "Downloaded to cache")
                downloadFileToCache(message, true) {
                    setUpWaveform(message)
                }
            }
        }
    }

    private fun setUpWaveform(message: ChatMessage, thenPlay: Boolean = true) {
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
                    startPlayback(message, thenPlay)
                }
            }
        } else {
            startPlayback(message, thenPlay)
        }
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
            CONTENT_TYPE_CALL_STARTED,
            CallStartedViewHolder::class.java,
            payload,
            R.layout.call_started_message,
            CallStartedViewHolder::class.java,
            payload,
            R.layout.call_started_message,
            this
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
        return messageHolders
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initVoiceRecordButton() {
        if (!isVoiceRecordingLocked) {
            if (!editableBehaviorSubject.value!!) {
                if (binding.messageInputView.messageInput.text!!.isNotEmpty()) {
                    showMicrophoneButton(false)
                } else {
                    showMicrophoneButton(true)
                }
            }
        } else if (mediaRecorderState == MediaRecorderState.RECORDING) {
            binding.messageInputView.playPauseBtn.visibility = View.GONE
            binding.messageInputView.seekBar.visibility = View.GONE
        } else {
            showVoiceRecordingLockedInterface(true)
            showPreviewVoiceRecording(true)
            stopMicInputRecordingAnimation()
            binding.messageInputView.micInputCloud.setState(MicInputCloud.ViewState.PAUSED_STATE)
        }

        isVoicePreviewPlaying = false
        binding.messageInputView.messageInput.doAfterTextChanged {
            if (!editableBehaviorSubject.value!!) {
                if (binding.messageInputView.messageInput.text?.isEmpty() == true) {
                    showMicrophoneButton(true)
                } else {
                    showMicrophoneButton(false)
                }
            }
        }

        var sliderInitX = 0F
        var downX = 0f
        var originY = 0f
        var deltaX = 0f
        var deltaY = 0f

        var voiceRecordStartTime = 0L
        var voiceRecordEndTime = 0L

        // this is so that the seekbar is no longer draggable
        binding.messageInputView.seekBar.setOnTouchListener(OnTouchListener { _, _ -> true })

        binding.messageInputView.micInputCloud.setOnClickListener {
            if (mediaRecorderState == MediaRecorderState.RECORDING) {
                audioFocusRequest(false) {
                    recorder?.stop()
                }
                mediaRecorderState = MediaRecorderState.INITIAL
                stopMicInputRecordingAnimation()
                showPreviewVoiceRecording(true)
            } else {
                stopPreviewVoicePlaying()
                initMediaRecorder(currentVoiceRecordFile)
                startMicInputRecordingAnimation()
                showPreviewVoiceRecording(false)
            }
        }

        binding.messageInputView.deleteVoiceRecording.setOnClickListener {
            stopAndDiscardAudioRecording()
            endVoiceRecordingUI()
            stopMicInputRecordingAnimation()
            binding.messageInputView.slideToCancelDescription.x = sliderInitX
        }

        binding.messageInputView.sendVoiceRecording.setOnClickListener {
            stopAndSendAudioRecording()
            endVoiceRecordingUI()
            stopMicInputRecordingAnimation()
            binding.messageInputView.slideToCancelDescription.x = sliderInitX
        }

        binding.messageInputView.playPauseBtn.setOnClickListener {
            Log.d(TAG, "is voice preview playing $isVoicePreviewPlaying")
            if (isVoicePreviewPlaying) {
                Log.d(TAG, "Paused")
                pausePreviewVoicePlaying()
                binding.messageInputView.playPauseBtn.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable
                        .ic_baseline_play_arrow_voice_message_24
                )
                isVoicePreviewPlaying = false
            } else {
                Log.d(TAG, "Started")
                startPreviewVoicePlaying()
                binding.messageInputView.playPauseBtn.icon = ContextCompat.getDrawable(
                    context,
                    R.drawable
                        .ic_baseline_pause_voice_message_24
                )
                isVoicePreviewPlaying = true
            }
        }

        binding.messageInputView.recordAudioButton.setOnTouchListener(object : OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                v?.performClick() // ?????????
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!isRecordAudioPermissionGranted()) {
                            requestRecordAudioPermissions()
                            return true
                        }
                        if (!permissionUtil.isFilesPermissionGranted()) {
                            UploadAndShareFilesWorker.requestStoragePermission(this@ChatActivity)
                            return true
                        }

                        voiceRecordStartTime = System.currentTimeMillis()

                        setVoiceRecordFileName()
                        startAudioRecording(currentVoiceRecordFile)
                        downX = event.x
                        originY = event.y
                        showRecordAudioUi(true)
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        Log.d(TAG, "ACTION_CANCEL. same as for UP")
                        if (mediaRecorderState != MediaRecorderState.RECORDING || !isRecordAudioPermissionGranted()) {
                            return true
                        }

                        stopAndDiscardAudioRecording()
                        endVoiceRecordingUI()
                        binding.messageInputView.slideToCancelDescription.x = sliderInitX
                    }

                    MotionEvent.ACTION_UP -> {
                        Log.d(TAG, "ACTION_UP. stop recording??")
                        if (mediaRecorderState != MediaRecorderState.RECORDING ||
                            !isRecordAudioPermissionGranted() ||
                            isVoiceRecordingLocked
                        ) {
                            return true
                        }
                        showRecordAudioUi(false)

                        voiceRecordEndTime = System.currentTimeMillis()
                        voiceRecordDuration = voiceRecordEndTime - voiceRecordStartTime
                        if (voiceRecordDuration < MINIMUM_VOICE_RECORD_DURATION) {
                            Log.d(TAG, "voiceRecordDuration: $voiceRecordDuration")
                            Snackbar.make(
                                binding.root,
                                context.getString(R.string.nc_voice_message_hold_to_record_info),
                                Snackbar.LENGTH_SHORT
                            ).show()
                            stopAndDiscardAudioRecording()
                            return true
                        } else {
                            voiceRecordStartTime = 0L
                            voiceRecordEndTime = 0L
                            stopAndSendAudioRecording()
                        }

                        binding.messageInputView.slideToCancelDescription.x = sliderInitX
                    }

                    MotionEvent.ACTION_MOVE -> {
                        Log.d(TAG, "ACTION_MOVE.")

                        if (mediaRecorderState != MediaRecorderState.RECORDING || !isRecordAudioPermissionGranted()) {
                            return true
                        }

                        showRecordAudioUi(true)

                        val movedX: Float = event.x
                        val movedY: Float = event.y
                        deltaX = movedX - downX
                        deltaY = movedY - originY

                        binding.voiceRecordingLock.translationY.let {
                            if (it < VOICE_RECORD_LOCK_BUTTON_Y) {
                                Log.d(TAG, "Voice Recording Locked")
                                isVoiceRecordingLocked = true
                                showVoiceRecordingLocked(true)
                                showVoiceRecordingLockedInterface(true)
                                startMicInputRecordingAnimation()
                            } else if (deltaY < 0f) {
                                binding.voiceRecordingLock.translationY = deltaY
                            }
                        }

                        // only allow slide to left
                        binding.messageInputView.slideToCancelDescription.x.let {
                            if (sliderInitX == 0.0F) {
                                sliderInitX = it
                            }

                            if (it > sliderInitX) {
                                binding.messageInputView.slideToCancelDescription.x = sliderInitX
                            }
                        }

                        binding.messageInputView.slideToCancelDescription.x.let {
                            if (it < VOICE_RECORD_CANCEL_SLIDER_X) {
                                Log.d(TAG, "stopping recording because slider was moved to left")
                                stopAndDiscardAudioRecording()
                                endVoiceRecordingUI()
                                binding.messageInputView.slideToCancelDescription.x = sliderInitX
                                return true
                            } else {
                                binding.messageInputView.slideToCancelDescription.x = it + deltaX
                                downX = movedX
                            }
                        }
                    }
                }

                return v?.onTouchEvent(event) ?: true
            }
        })
    }

    private fun showPreviewVoiceRecording(value: Boolean) {
        val micInputCloudLayoutParams: LayoutParams = binding.messageInputView.micInputCloud
            .layoutParams as LayoutParams

        val deleteVoiceRecordingLayoutParams: LayoutParams = binding.messageInputView.deleteVoiceRecording
            .layoutParams as LayoutParams

        val sendVoiceRecordingLayoutParams: LayoutParams = binding.messageInputView.sendVoiceRecording
            .layoutParams as LayoutParams

        if (value) {
            voiceRecordPauseTime = binding.messageInputView.audioRecordDuration.base - SystemClock.elapsedRealtime()
            binding.messageInputView.audioRecordDuration.stop()
            binding.messageInputView.audioRecordDuration.visibility = View.GONE
            binding.messageInputView.playPauseBtn.visibility = View.VISIBLE
            binding.messageInputView.playPauseBtn.icon = ContextCompat.getDrawable(
                context,
                R.drawable.ic_baseline_play_arrow_voice_message_24
            )
            binding.messageInputView.seekBar.visibility = View.VISIBLE
            binding.messageInputView.seekBar.progress = 0
            binding.messageInputView.seekBar.max = 0
            micInputCloudLayoutParams.removeRule(BELOW)
            micInputCloudLayoutParams.addRule(BELOW, R.id.voice_preview_container)
            deleteVoiceRecordingLayoutParams.removeRule(BELOW)
            deleteVoiceRecordingLayoutParams.addRule(BELOW, R.id.voice_preview_container)
            sendVoiceRecordingLayoutParams.removeRule(BELOW)
            sendVoiceRecordingLayoutParams.addRule(BELOW, R.id.voice_preview_container)
        } else {
            binding.messageInputView.audioRecordDuration.base = SystemClock.elapsedRealtime()
            binding.messageInputView.audioRecordDuration.start()
            binding.messageInputView.playPauseBtn.visibility = View.GONE
            binding.messageInputView.seekBar.visibility = View.GONE
            binding.messageInputView.audioRecordDuration.visibility = View.VISIBLE
            micInputCloudLayoutParams.removeRule(BELOW)
            micInputCloudLayoutParams.addRule(BELOW, R.id.audioRecordDuration)
            deleteVoiceRecordingLayoutParams.removeRule(BELOW)
            deleteVoiceRecordingLayoutParams.addRule(BELOW, R.id.audioRecordDuration)
            sendVoiceRecordingLayoutParams.removeRule(BELOW)
            sendVoiceRecordingLayoutParams.addRule(BELOW, R.id.audioRecordDuration)
        }
    }

    private fun initPreviewVoiceRecording() {
        voicePreviewMediaPlayer = MediaPlayer().apply {
            setDataSource(currentVoiceRecordFile)
            prepare()
            setOnPreparedListener {
                binding.messageInputView.seekBar.progress = 0
                binding.messageInputView.seekBar.max = it.duration
                voicePreviewObjectAnimator = ObjectAnimator.ofInt(
                    binding.messageInputView.seekBar,
                    "progress",
                    0,
                    it.duration
                ).apply {
                    duration = it.duration.toLong()
                    interpolator = LinearInterpolator()
                }
                audioFocusRequest(true) {
                    voicePreviewMediaPlayer!!.start()
                    voicePreviewObjectAnimator!!.start()
                    handleBecomingNoisyBroadcast(register = true)
                }
            }

            setOnCompletionListener {
                stopPreviewVoicePlaying()
            }
        }
    }

    private fun startPreviewVoicePlaying() {
        Log.d(TAG, "started preview voice recording")
        if (voicePreviewMediaPlayer == null) {
            initPreviewVoiceRecording()
        } else {
            audioFocusRequest(true) {
                voicePreviewMediaPlayer!!.start()
                voicePreviewObjectAnimator!!.resume()
                handleBecomingNoisyBroadcast(register = true)
            }
        }
    }

    private fun pausePreviewVoicePlaying() {
        Log.d(TAG, "paused preview voice recording")
        audioFocusRequest(false) {
            voicePreviewMediaPlayer!!.pause()
            voicePreviewObjectAnimator!!.pause()
            handleBecomingNoisyBroadcast(register = false)
        }
    }

    private fun stopPreviewVoicePlaying() {
        if (voicePreviewMediaPlayer != null) {
            isVoicePreviewPlaying = false
            binding.messageInputView.playPauseBtn.icon = ContextCompat.getDrawable(context, R.drawable.ic_refresh)
            voicePreviewObjectAnimator!!.end()
            voicePreviewObjectAnimator = null
            binding.messageInputView.seekBar.clearAnimation()
            audioFocusRequest(false) {
                voicePreviewMediaPlayer!!.stop()
                voicePreviewMediaPlayer!!.release()
                voicePreviewMediaPlayer = null
                handleBecomingNoisyBroadcast(register = false)
            }
        }
    }

    private fun endVoiceRecordingUI() {
        stopPreviewVoicePlaying()
        showRecordAudioUi(false)
        binding.voiceRecordingLock.translationY = 0f
        isVoiceRecordingLocked = false
        showVoiceRecordingLocked(false)
        showVoiceRecordingLockedInterface(false)
        stopMicInputRecordingAnimation()
    }

    private fun showVoiceRecordingLocked(value: Boolean) {
        if (value) {
            binding.voiceRecordingLock.setImageDrawable(
                ContextCompat.getDrawable(context, R.drawable.ic_lock_grey600_24px)
            )

            binding.voiceRecordingLock.alpha = 1f
            binding.voiceRecordingLock.animate().alpha(0f).setDuration(VOICE_RECORDING_LOCK_ANIMATION_DURATION.toLong())
                .setInterpolator(AccelerateInterpolator()).start()
        } else {
            binding.voiceRecordingLock.setImageDrawable(
                ContextCompat.getDrawable(context, R.drawable.ic_lock_open_grey600_24dp)
            )
            binding.voiceRecordingLock.alpha = 1f
        }
    }

    private fun showVoiceRecordingLockedInterface(value: Boolean) {
        val audioDurationLayoutParams: LayoutParams = binding.messageInputView.audioRecordDuration
            .layoutParams as LayoutParams

        val micInputCloudLayoutParams: LayoutParams = binding.messageInputView.micInputCloud
            .layoutParams as LayoutParams

        val deleteVoiceRecordingLayoutParams: LayoutParams = binding.messageInputView.deleteVoiceRecording
            .layoutParams as LayoutParams

        val sendVoiceRecordingLayoutParams: LayoutParams = binding.messageInputView.sendVoiceRecording
            .layoutParams as LayoutParams

        val standardQuarterMargin = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            resources.getDimension(R.dimen.standard_quarter_margin),
            resources
                .displayMetrics
        ).toInt()

        binding.messageInputView.button.isEnabled = true
        if (value) {
            binding.messageInputView.slideToCancelDescription.visibility = View.GONE
            binding.messageInputView.deleteVoiceRecording.visibility = View.VISIBLE
            binding.messageInputView.sendVoiceRecording.visibility = View.VISIBLE
            binding.messageInputView.micInputCloud.visibility = View.VISIBLE
            binding.messageInputView.recordAudioButton.visibility = View.GONE
            binding.messageInputView.microphoneEnabledInfo.clearAnimation()
            binding.messageInputView.microphoneEnabledInfo.visibility = View.GONE
            binding.messageInputView.microphoneEnabledInfoBackground.visibility = View.GONE
            binding.messageInputView.recordAudioButton.visibility = View.GONE
            micInputCloudLayoutParams.removeRule(BELOW)
            micInputCloudLayoutParams.addRule(BELOW, R.id.audioRecordDuration)
            deleteVoiceRecordingLayoutParams.removeRule(BELOW)
            deleteVoiceRecordingLayoutParams.addRule(BELOW, R.id.audioRecordDuration)
            sendVoiceRecordingLayoutParams.removeRule(BELOW)
            sendVoiceRecordingLayoutParams.addRule(BELOW, R.id.audioRecordDuration)
            audioDurationLayoutParams.removeRule(RelativeLayout.CENTER_VERTICAL)
            audioDurationLayoutParams.removeRule(RelativeLayout.END_OF)
            audioDurationLayoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, R.bool.value_true)
            audioDurationLayoutParams.setMargins(0, standardQuarterMargin, 0, 0)
        } else {
            binding.messageInputView.deleteVoiceRecording.visibility = View.GONE
            binding.messageInputView.micInputCloud.visibility = View.GONE
            binding.messageInputView.recordAudioButton.visibility = View.VISIBLE
            binding.messageInputView.sendVoiceRecording.visibility = View.GONE
            binding.messageInputView.playPauseBtn.visibility = View.GONE
            binding.messageInputView.seekBar.visibility = View.GONE
            audioDurationLayoutParams.addRule(RelativeLayout.CENTER_VERTICAL, R.bool.value_true)
            audioDurationLayoutParams.addRule(RelativeLayout.END_OF, R.id.microphoneEnabledInfo)
            audioDurationLayoutParams.removeRule(RelativeLayout.CENTER_HORIZONTAL)
            audioDurationLayoutParams.setMargins(0, 0, 0, 0)
        }
    }

    private fun initSmileyKeyboardToggler() {
        val smileyButton = binding.messageInputView.findViewById<ImageButton>(R.id.smileyButton)

        emojiPopup = binding.messageInputView.inputEditText?.let {
            EmojiPopup(
                rootView = binding.root,
                editText = it,
                onEmojiPopupShownListener = {
                    if (resources != null) {
                        smileyButton?.setImageDrawable(
                            ContextCompat.getDrawable(context, R.drawable.ic_baseline_keyboard_24)
                        )
                    }
                },
                onEmojiPopupDismissListener = {
                    smileyButton?.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_insert_emoticon_black_24dp)
                    )
                },
                onEmojiClickListener = {
                    binding.messageInputView.inputEditText?.editableText?.append(" ")
                }
            )
        }

        smileyButton?.setOnClickListener {
            emojiPopup?.toggle()
        }
    }

    @Suppress("MagicNumber", "LongMethod")
    private fun updateTypingIndicator() {
        fun ellipsize(text: String): String {
            return DisplayUtils.ellipsize(text, TYPING_INDICATOR_MAX_NAME_LENGTH)
        }

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

            if (participantNames.size > 0) {
                binding.typingIndicatorWrapper.animate()
                    .translationY(binding.messageInputView.y - DisplayUtils.convertDpToPixel(18f, context))
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .duration = TYPING_INDICATOR_ANIMATION_DURATION
            } else {
                if (binding.typingIndicator.lineCount == 1) {
                    binding.typingIndicatorWrapper.animate()
                        .translationY(binding.messageInputView.y)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .duration = TYPING_INDICATOR_ANIMATION_DURATION
                } else if (binding.typingIndicator.lineCount == 2) {
                    binding.typingIndicatorWrapper.animate()
                        .translationY(binding.messageInputView.y + DisplayUtils.convertDpToPixel(15f, context))
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .duration = TYPING_INDICATOR_ANIMATION_DURATION
                }
            }
        }
    }

    fun updateOwnTypingStatus(typedText: CharSequence) {
        fun sendStartTypingSignalingMessage() {
            for ((sessionId, _) in webSocketInstance?.getUserMap()!!) {
                val ncSignalingMessage = NCSignalingMessage()
                ncSignalingMessage.to = sessionId
                ncSignalingMessage.type = TYPING_STARTED_SIGNALING_MESSAGE_TYPE
                signalingMessageSender!!.send(ncSignalingMessage)
            }
        }

        if (isTypingStatusEnabled()) {
            if (typedText.isEmpty()) {
                sendStopTypingMessage()
            } else if (typingTimer == null) {
                sendStartTypingSignalingMessage()

                typingTimer = object : CountDownTimer(
                    TYPING_DURATION_TO_SEND_NEXT_TYPING_MESSAGE,
                    TYPING_INTERVAL_TO_SEND_NEXT_TYPING_MESSAGE
                ) {
                    override fun onTick(millisUntilFinished: Long) {
                        // unused
                    }

                    override fun onFinish() {
                        if (typedWhileTypingTimerIsRunning) {
                            sendStartTypingSignalingMessage()
                            cancel()
                            start()
                            typedWhileTypingTimerIsRunning = false
                        } else {
                            sendStopTypingMessage()
                        }
                    }
                }.start()
            } else {
                typedWhileTypingTimerIsRunning = true
            }
        }
    }

    private fun sendStopTypingMessage() {
        if (isTypingStatusEnabled()) {
            typingTimer = null
            typedWhileTypingTimerIsRunning = false

            for ((sessionId, _) in webSocketInstance?.getUserMap()!!) {
                val ncSignalingMessage = NCSignalingMessage()
                ncSignalingMessage.to = sessionId
                ncSignalingMessage.type = TYPING_STOPPED_SIGNALING_MESSAGE_TYPE
                signalingMessageSender!!.send(ncSignalingMessage)
            }
        }
    }

    private fun isTypingStatusEnabled(): Boolean {
        return webSocketInstance != null &&
            !CapabilitiesUtil.isTypingStatusPrivate(conversationUser!!)
    }

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
                            replyToMessage(chatMessage)
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
                                .setImageDrawable(BitmapDrawable(resources, bitmap))
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
        currentConversation != null && currentConversation?.type != null &&
            currentConversation?.type == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL

    private fun isGroupConversation() =
        currentConversation != null && currentConversation?.type != null &&
            currentConversation?.type == ConversationType.ROOM_GROUP_CALL

    private fun isPublicConversation() =
        currentConversation != null && currentConversation?.type != null &&
            currentConversation?.type == ConversationType.ROOM_PUBLIC_CALL

    private fun switchToRoom(token: String, startCallAfterRoomSwitch: Boolean, isVoiceOnlyCall: Boolean) {
        if (conversationUser != null) {
            runOnUiThread {
                if (currentConversation?.objectType == ObjectType.ROOM) {
                    Snackbar.make(
                        binding.root,
                        context.resources.getString(R.string.switch_to_main_room),
                        Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    Snackbar.make(
                        binding.root,
                        context.resources.getString(R.string.switch_to_breakout_room),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
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

    private fun showSendButtonMenu() {
        val popupMenu = PopupMenu(
            ContextThemeWrapper(this, R.style.ChatSendButtonMenu),
            binding.messageInputView.button,
            Gravity.END
        )
        popupMenu.inflate(R.menu.chat_send_menu)

        popupMenu.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.send_without_notification -> submitMessage(true)
            }
            true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            popupMenu.setForceShowIcon(true)
        }
        popupMenu.show()
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

    private fun getAudioFocusChangeListener(): AudioManager.OnAudioFocusChangeListener {
        return AudioManager.OnAudioFocusChangeListener { flag ->
            when (flag) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    chatViewModel.isPausedDueToBecomingNoisy = false
                    if (isVoicePreviewPlaying) {
                        stopPreviewVoicePlaying()
                    }
                    if (currentlyPlayedVoiceMessage != null) {
                        stopMediaPlayer(currentlyPlayedVoiceMessage!!)
                    }
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    chatViewModel.isPausedDueToBecomingNoisy = false
                    if (isVoicePreviewPlaying) {
                        pausePreviewVoicePlaying()
                    }
                    if (currentlyPlayedVoiceMessage != null) {
                        pausePlayback(currentlyPlayedVoiceMessage!!)
                    }
                }
            }
        }
    }

    private fun audioFocusRequest(shouldRequestFocus: Boolean, onGranted: () -> Unit) {
        if (chatViewModel.isPausedDueToBecomingNoisy) {
            onGranted()
            return
        }
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val duration = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE

        val isGranted: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(duration)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
            if (shouldRequestFocus) {
                audioManager.requestAudioFocus(focusRequest)
            } else {
                audioManager.abandonAudioFocusRequest(focusRequest)
            }
        } else {
            @Deprecated("This method was deprecated in API level 26.")
            if (shouldRequestFocus) {
                audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, duration)
            } else {
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
        }
        if (isGranted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onGranted()
        }
    }

    private fun handleBecomingNoisyBroadcast(register: Boolean) {
        if (register && !chatViewModel.receiverRegistered) {
            registerReceiver(noisyAudioStreamReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            chatViewModel.receiverRegistered = true
        } else if (!chatViewModel.receiverUnregistered) {
            unregisterReceiver(noisyAudioStreamReceiver)
            chatViewModel.receiverUnregistered = true
            chatViewModel.receiverRegistered = false
        }
    }

    private fun startPlayback(message: ChatMessage, doPlay: Boolean = true) {
        if (!active) {
            // don't begin to play voice message if screen is not visible anymore.
            // this situation might happen if file is downloading but user already left the chatview.
            // If user returns to chatview, the old chatview instance is not attached anymore
            // and he has to click the play button again (which is considered to be okay)
            return
        }

        initMediaPlayer(message)

        mediaPlayer?.let {
            if (!it.isPlaying && doPlay) {
                audioFocusRequest(true) {
                    it.start()
                    handleBecomingNoisyBroadcast(register = true)
                }
            }

            mediaPlayerHandler = Handler()
            runOnUiThread(object : Runnable {
                override fun run() {
                    if (mediaPlayer != null) {
                        if (message.isPlayingVoiceMessage) {
                            val pos = mediaPlayer!!.currentPosition / VOICE_MESSAGE_SEEKBAR_BASE
                            if (pos < (mediaPlayer!!.duration / VOICE_MESSAGE_SEEKBAR_BASE)) {
                                lastRecordMediaPosition = mediaPlayer!!.currentPosition
                                message.voiceMessagePlayedSeconds = pos
                                message.voiceMessageSeekbarProgress = mediaPlayer!!.currentPosition
                                adapter?.update(message)
                            } else {
                                message.resetVoiceMessage = true
                                message.voiceMessagePlayedSeconds = 0
                                message.voiceMessageSeekbarProgress = 0
                                adapter?.update(message)
                                stopMediaPlayer(message)
                            }
                        }
                    }
                    mediaPlayerHandler.postDelayed(this, MILISEC_15)
                }
            })

            message.isDownloadingVoiceMessage = false
            message.isPlayingVoiceMessage = doPlay
            // message.voiceMessagePlayedSeconds = lastRecordMediaPosition / VOICE_MESSAGE_SEEKBAR_BASE
            // message.voiceMessageSeekbarProgress = lastRecordMediaPosition
            // the commented instructions objective was to update audio seekbarprogress
            // in the case in which audio status is paused when the position is resumed
            adapter?.update(message)
        }
    }

    private fun pausePlayback(message: ChatMessage) {
        if (mediaPlayer!!.isPlaying) {
            audioFocusRequest(false) {
                mediaPlayer!!.pause()
                handleBecomingNoisyBroadcast(register = false)
            }
        }

        message.isPlayingVoiceMessage = false
        adapter?.update(message)
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun initMediaPlayer(message: ChatMessage) {
        if (message != currentlyPlayedVoiceMessage) {
            currentlyPlayedVoiceMessage?.let { stopMediaPlayer(it) }
        }

        if (mediaPlayer == null) {
            val fileName = message.selectedIndividualHashMap!!["name"]
            val absolutePath = context.cacheDir.absolutePath + "/" + fileName

            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(absolutePath)
                    prepare()
                    setOnPreparedListener {
                        currentlyPlayedVoiceMessage = message
                        message.voiceMessageDuration = mediaPlayer!!.duration / VOICE_MESSAGE_SEEKBAR_BASE
                        lastRecordedSeeked = false
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        setOnMediaTimeDiscontinuityListener { mp, _ ->
                            if (lastRecordMediaPosition > ONE_SECOND_IN_MILLIS && !lastRecordedSeeked) {
                                mp.seekTo(lastRecordMediaPosition)
                                lastRecordedSeeked = true
                            }
                        }
                        // this ensures that audio can be resumed at a given position
                        this.seekTo(lastRecordMediaPosition)
                    }
                    setOnCompletionListener {
                        stopMediaPlayer(message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "failed to initialize mediaPlayer", e)
                Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun stopMediaPlayer(message: ChatMessage) {
        message.isPlayingVoiceMessage = false
        message.resetVoiceMessage = true
        adapter?.update(message)

        currentlyPlayedVoiceMessage = null
        lastRecordMediaPosition = 0 // this ensures that if audio track is changed, then it is played from the beginning

        mediaPlayerHandler.removeCallbacksAndMessages(null)

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    Log.d(TAG, "media player is stopped")
                    audioFocusRequest(false) {
                        it.stop()
                        handleBecomingNoisyBroadcast(register = false)
                    }
                }
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "mediaPlayer was not initialized", e)
        } finally {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    override fun updateMediaPlayerProgressBySlider(messageWithSlidedProgress: ChatMessage, progress: Int) {
        if (mediaPlayer != null) {
            if (messageWithSlidedProgress == currentlyPlayedVoiceMessage) {
                mediaPlayer!!.seekTo(progress)
            }
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

    private fun isChildOfExpandableSystemMessage(chatMessage: ChatMessage): Boolean {
        return isSystemMessage(chatMessage) &&
            !chatMessage.expandableParent &&
            chatMessage.lastItemOfExpandableGroup != 0
    }

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

    @SuppressLint("SimpleDateFormat")
    private fun setVoiceRecordFileName() {
        val simpleDateFormat = SimpleDateFormat(FILE_DATE_PATTERN)
        val date: String = simpleDateFormat.format(Date())

        val fileNameWithoutSuffix = String.format(
            context.resources.getString(R.string.nc_voice_message_filename),
            date,
            currentConversation!!.displayName
        )
        val fileName = fileNameWithoutSuffix + VOICE_MESSAGE_FILE_SUFFIX

        currentVoiceRecordFile = "${context.cacheDir.absolutePath}/$fileName"
    }

    private fun showRecordAudioUi(show: Boolean) {
        if (show) {
            binding.messageInputView.microphoneEnabledInfo.visibility = View.VISIBLE
            binding.messageInputView.microphoneEnabledInfoBackground.visibility = View.VISIBLE
            binding.messageInputView.audioRecordDuration.visibility = View.VISIBLE
            binding.messageInputView.slideToCancelDescription.visibility = View.VISIBLE
            binding.messageInputView.attachmentButton.visibility = View.GONE
            binding.messageInputView.smileyButton.visibility = View.GONE
            binding.messageInputView.messageInput.visibility = View.GONE
            binding.messageInputView.messageInput.hint = ""
            binding.voiceRecordingLock.visibility = View.VISIBLE
        } else {
            binding.messageInputView.microphoneEnabledInfo.visibility = View.GONE
            binding.messageInputView.microphoneEnabledInfoBackground.visibility = View.GONE
            binding.messageInputView.audioRecordDuration.visibility = View.GONE
            binding.messageInputView.slideToCancelDescription.visibility = View.GONE
            binding.messageInputView.attachmentButton.visibility = View.VISIBLE
            binding.messageInputView.smileyButton.visibility = View.VISIBLE
            binding.messageInputView.messageInput.visibility = View.VISIBLE
            binding.messageInputView.messageInput.hint =
                context.resources?.getString(R.string.nc_hint_enter_a_message)
            binding.voiceRecordingLock.visibility = View.GONE
        }
    }

    private fun startMicInputRecordingAnimation() {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )

        if (micInputAudioRecordThread == null && permissionCheck == PERMISSION_GRANTED) {
            Log.d(TAG, "Mic Animation Started")
            micInputAudioRecorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            isMicInputAudioThreadRunning = true
            micInputAudioRecorder.startRecording()
            initMicInputAudioRecordThread()
            micInputAudioRecordThread!!.start()
            binding.messageInputView.micInputCloud.startAnimators()
        }
    }

    private fun initMicInputAudioRecordThread() {
        micInputAudioRecordThread = Thread(
            Runnable {
                while (isMicInputAudioThreadRunning) {
                    val byteArr = ByteArray(bufferSize / 2)
                    micInputAudioRecorder.read(byteArr, 0, byteArr.size)
                    val d = abs(byteArr[0].toDouble())
                    if (d > AUDIO_VALUE_MAX) {
                        binding.messageInputView.micInputCloud.setRotationSpeed(
                            log10(d).toFloat(),
                            MicInputCloud.MAXIMUM_RADIUS
                        )
                    } else if (d > AUDIO_VALUE_MIN) {
                        binding.messageInputView.micInputCloud.setRotationSpeed(
                            log10(d).toFloat(),
                            MicInputCloud.EXTENDED_RADIUS
                        )
                    } else {
                        binding.messageInputView.micInputCloud.setRotationSpeed(
                            1f,
                            MicInputCloud.DEFAULT_RADIUS
                        )
                    }
                    Thread.sleep(AUDIO_VALUE_SLEEP)
                }
            }
        )
    }

    private fun stopMicInputRecordingAnimation() {
        if (micInputAudioRecordThread != null) {
            Log.d(TAG, "Mic Animation Ended")
            audioFocusRequest(false) {
                micInputAudioRecorder.stop()
                micInputAudioRecorder.release()
            }
            isMicInputAudioThreadRunning = false
            micInputAudioRecordThread = null
        }
    }

    private fun isRecordAudioPermissionGranted(): Boolean {
        return PermissionChecker.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PERMISSION_GRANTED
    }

    private fun startAudioRecording(file: String) {
        binding.messageInputView.audioRecordDuration.base = SystemClock.elapsedRealtime()
        binding.messageInputView.audioRecordDuration.start()

        val animation: Animation = AlphaAnimation(1.0f, 0.0f)
        animation.duration = ANIMATION_DURATION
        animation.interpolator = LinearInterpolator()
        animation.repeatCount = Animation.INFINITE
        animation.repeatMode = Animation.REVERSE
        binding.messageInputView.microphoneEnabledInfo.startAnimation(animation)

        initMediaRecorder(file)
        VibrationUtils.vibrateShort(context)
    }

    private fun initMediaRecorder(file: String) {
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorderState = MediaRecorderState.INITIALIZED

            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorderState = MediaRecorderState.CONFIGURED

            setOutputFile(file)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(VOICE_MESSAGE_SAMPLING_RATE)
            setAudioEncodingBitRate(VOICE_MESSAGE_ENCODING_BIT_RATE)
            setAudioChannels(VOICE_MESSAGE_CHANNELS)

            try {
                prepare()
                mediaRecorderState = MediaRecorderState.PREPARED
            } catch (e: IOException) {
                mediaRecorderState = MediaRecorderState.ERROR
                Log.e(TAG, "prepare for audio recording failed")
            }

            try {
                audioFocusRequest(true) {
                    start()
                }
                mediaRecorderState = MediaRecorderState.RECORDING
                Log.d(TAG, "recording started")
            } catch (e: IllegalStateException) {
                mediaRecorderState = MediaRecorderState.ERROR
                Log.e(TAG, "start for audio recording failed")
            }
        }
    }

    private fun stopAndSendAudioRecording() {
        stopAudioRecording()
        Log.d(TAG, "stopped and sent audio recording")

        if (mediaRecorderState != MediaRecorderState.ERROR) {
            val uri = Uri.fromFile(File(currentVoiceRecordFile))
            uploadFile(uri.toString(), true)
        } else {
            mediaRecorderState = MediaRecorderState.INITIAL
        }
    }

    private fun stopAndDiscardAudioRecording() {
        stopAudioRecording()
        Log.d(TAG, "stopped and discarded audio recording")
        val cachedFile = File(currentVoiceRecordFile)
        cachedFile.delete()

        if (mediaRecorderState == MediaRecorderState.ERROR) {
            mediaRecorderState = MediaRecorderState.INITIAL
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun stopAudioRecording() {
        binding.messageInputView.audioRecordDuration.stop()
        binding.messageInputView.microphoneEnabledInfo.clearAnimation()

        recorder?.apply {
            try {
                if (mediaRecorderState == MediaRecorderState.RECORDING) {
                    audioFocusRequest(false) {
                        stop()
                        reset()
                    }
                    mediaRecorderState = MediaRecorderState.INITIAL
                    Log.d(TAG, "stopped recorder")
                }
                release()
                mediaRecorderState = MediaRecorderState.RELEASED
            } catch (e: Exception) {
                when (e) {
                    is java.lang.IllegalStateException,
                    is java.lang.RuntimeException -> {
                        mediaRecorderState = MediaRecorderState.ERROR
                        Log.e(TAG, "error while stopping recorder! with state $mediaRecorderState $e")
                    }
                }
            }

            VibrationUtils.vibrateShort(context)
        }
        recorder = null
    }

    private fun requestRecordAudioPermissions() {
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
            binding.messageInputView.visibility = View.GONE
        } else {
            binding.messageInputView.visibility = View.VISIBLE
        }
    }

    private fun shouldShowLobby(): Boolean {
        if (currentConversation != null) {
            return CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.WEBINARY_LOBBY) &&
                currentConversation?.lobbyState == LobbyState.LOBBY_STATE_MODERATORS_ONLY &&
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

    private fun isReadOnlyConversation(): Boolean {
        return currentConversation?.conversationReadOnlyState != null &&
            currentConversation?.conversationReadOnlyState ==
            ConversationReadOnlyState.CONVERSATION_READ_ONLY
    }

    private fun checkLobbyState() {
        if (currentConversation != null &&
            ConversationUtils.isLobbyViewApplicable(currentConversation!!, spreedCapabilities)
        ) {
            if (shouldShowLobby()) {
                binding.lobby.lobbyView.visibility = View.VISIBLE
                binding.messagesListView.visibility = View.GONE
                binding.messageInputView.visibility = View.GONE
                binding.progressBar.visibility = View.GONE

                val sb = StringBuilder()
                sb.append(resources!!.getText(R.string.nc_lobby_waiting))
                    .append("\n\n")

                if (currentConversation?.lobbyTimer != null && currentConversation?.lobbyTimer !=
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
            } else {
                binding.lobby.lobbyView.visibility = View.GONE
                binding.messagesListView.visibility = View.VISIBLE
                binding.messageInputView.inputEditText?.visibility = View.VISIBLE
            }
        } else {
            binding.lobby.lobbyView.visibility = View.GONE
            binding.messagesListView.visibility = View.VISIBLE
            if (!isVoiceRecordingLocked) {
                binding.messageInputView.inputEditText?.visibility = View.VISIBLE
            }
        }
    }

    @Throws(IllegalStateException::class)
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (resultCode != RESULT_OK && (requestCode != REQUEST_CODE_MESSAGE_SEARCH)) {
            Log.e(TAG, "resultCode for received intent was != ok")
            return
        }

        when (requestCode) {
            REQUEST_CODE_SELECT_REMOTE_FILES -> {
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

            REQUEST_CODE_CHOOSE_FILE -> {
                try {
                    checkNotNull(intent)
                    filesToUpload.clear()
                    intent.clipData?.let {
                        for (index in 0 until it.itemCount) {
                            filesToUpload.add(it.getItemAt(index).uri.toString())
                        }
                    } ?: run {
                        checkNotNull(intent.data)
                        intent.data.let {
                            filesToUpload.add(intent.data.toString())
                        }
                    }
                    require(filesToUpload.isNotEmpty())

                    val filenamesWithLineBreaks = StringBuilder("\n")

                    for (file in filesToUpload) {
                        val filename = FileUtils.getFileName(Uri.parse(file), context)
                        filenamesWithLineBreaks.append(filename).append("\n")
                    }

                    val newFragment = FileAttachmentPreviewFragment.newInstance(
                        filenamesWithLineBreaks.toString(),
                        filesToUpload
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

            REQUEST_CODE_SELECT_CONTACT -> {
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

            REQUEST_CODE_PICK_CAMERA -> {
                if (resultCode == RESULT_OK) {
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
                                val filename = FileUtils.getFileName(Uri.parse(file), context)
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
            }

            REQUEST_CODE_MESSAGE_SEARCH -> {
                val messageId = intent?.getStringExtra(MessageSearchActivity.RESULT_KEY_MESSAGE_ID)
                messageId?.let { id ->
                    scrollToMessageWithId(id)
                }
            }
        }
    }

    private fun scrollToMessageWithId(messageId: String) {
        val position = adapter?.items?.indexOfFirst {
            it.item is ChatMessage && (it.item as ChatMessage).id == messageId
        }
        if (position != null && position >= 0) {
            binding.messagesListView.scrollToPosition(position)
        } else {
            // TODO show error that we don't have that message?
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

    private fun hasGrantedPermissions(grantResults: IntArray): Boolean {
        return permissionUtil.isFilesPermissionGranted()
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
                startActivityForResult(intent, REQUEST_CODE_SELECT_CONTACT)
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

        try {
            require(fileUri.isNotEmpty())
            UploadAndShareFilesWorker.upload(
                fileUri,
                room,
                currentConversation?.displayName!!,
                metaData
            )
        } catch (e: IllegalArgumentException) {
            context.resources?.getString(R.string.nc_upload_failed)?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG)
                    .show()
            }
            Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
        }
    }

    private fun showLocalFilePicker() {
        val action = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(
            Intent.createChooser(
                action,
                context.resources?.getString(
                    R.string.nc_upload_choose_local_files
                )
            ),
            REQUEST_CODE_CHOOSE_FILE
        )
    }

    override fun startActivity(intent: Intent) {
        val user = currentUserProvider.currentUser.blockingGet()
        if (intent.data != null && TextUtils.equals(intent.action, Intent.ACTION_VIEW)) {
            val uri = intent.data.toString()
            if (uri.startsWith(user.baseUrl!!)) {
                if (UriUtils.isInstanceInternalFileShareUrl(user.baseUrl!!, uri)) {
                    // https://cloud.nextcloud.com/f/41
                    val fileViewerUtils = FileViewerUtils(applicationContext, user)
                    fileViewerUtils.openFileInFilesApp(uri, UriUtils.extractInstanceInternalFileShareFileId(uri))
                } else if (UriUtils.isInstanceInternalFileUrl(user.baseUrl!!, uri)) {
                    // https://cloud.nextcloud.com/apps/files/?dir=/Engineering&fileid=41
                    val fileViewerUtils = FileViewerUtils(applicationContext, user)
                    fileViewerUtils.openFileInFilesApp(uri, UriUtils.extractInstanceInternalFileFileId(uri))
                } else if (UriUtils.isInstanceInternalFileUrlNew(user.baseUrl!!, uri)) {
                    // https://cloud.nextcloud.com/apps/files/?dir=/Engineering&fileid=41
                    val fileViewerUtils = FileViewerUtils(applicationContext, user)
                    fileViewerUtils.openFileInFilesApp(uri, UriUtils.extractInstanceInternalFileFileIdNew(uri))
                } else {
                    super.startActivity(intent)
                }
            } else {
                super.startActivity(intent)
            }
        } else {
            super.startActivity(intent)
        }
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
        startActivityForResult(sharingFileBrowserIntent, REQUEST_CODE_SELECT_REMOTE_FILES)
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

    private fun setupMentionAutocomplete() {
        val elevation = MENTION_AUTO_COMPLETE_ELEVATION
        resources?.let {
            val backgroundDrawable = ColorDrawable(it.getColor(R.color.bg_default, null))
            val presenter = MentionAutocompletePresenter(this, roomToken, chatApiVersion)
            val callback = MentionAutocompleteCallback(
                this,
                conversationUser!!,
                binding.messageInputView.inputEditText,
                viewThemeUtils
            )

            if (mentionAutocomplete == null && binding.messageInputView.inputEditText != null) {
                mentionAutocomplete = Autocomplete.on<Mention>(binding.messageInputView.inputEditText)
                    .with(elevation)
                    .with(backgroundDrawable)
                    .with(MagicCharPolicy('@'))
                    .with(presenter)
                    .with(callback)
                    .build()
            }
        }
    }

    private fun validSessionId(): Boolean {
        return currentConversation != null &&
            sessionIdAfterRoomJoined?.isNotEmpty() == true &&
            sessionIdAfterRoomJoined != "0"
    }

    private fun cancelReply() {
        binding.messageInputView.findViewById<RelativeLayout>(R.id.quotedChatMessageView)?.visibility = View.GONE
        binding.messageInputView.findViewById<ImageButton>(R.id.attachmentButton)?.visibility = View.VISIBLE
    }

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

    private fun isActivityNotChangingConfigurations(): Boolean {
        return !isChangingConfigurations
    }

    private fun isNotInCall(): Boolean {
        return !ApplicationWideCurrentRoomHolder.getInstance().isInCall &&
            !ApplicationWideCurrentRoomHolder.getInstance().isDialing
    }

    private fun setActionBarTitle() {
        val title = binding.chatToolbar.findViewById<TextView>(R.id.chat_toolbar_title)
        viewThemeUtils.platform.colorTextView(title, ColorRole.ON_SURFACE)

        title.text =
            if (currentConversation?.displayName != null) {
                try {
                    EmojiCompat.get().process(currentConversation?.displayName as CharSequence).toString()
                } catch (e: java.lang.IllegalStateException) {
                    currentConversation?.displayName
                    error(e)
                }
            } else {
                ""
            }

        val statusMessageView = binding.chatToolbar.findViewById<TextView>(R.id.chat_toolbar_status_message)
        if (currentConversation?.type == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
            var statusMessage = ""
            if (currentConversation?.statusIcon != null) {
                statusMessage += currentConversation?.statusIcon
            }

            if (currentConversation?.statusMessage != null) {
                statusMessage += currentConversation?.statusMessage
            }

            if (statusMessage.isNotEmpty()) {
                viewThemeUtils.platform.colorTextView(statusMessageView, ColorRole.ON_SURFACE)
                statusMessageView.text = statusMessage
                statusMessageView.visibility = View.VISIBLE
            } else {
                statusMessageView.visibility = View.GONE
            }
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

        currentlyPlayedVoiceMessage?.let { stopMediaPlayer(it) }

        adapter = null
        Log.d(TAG, "inConversation was set to false!")
    }

    private fun joinRoomWithPassword() {
        // if ApplicationWideCurrentRoomHolder contains a session (because a call is active), then keep the sessionId
        if (ApplicationWideCurrentRoomHolder.getInstance().currentRoomId ==
            currentConversation!!.roomId
        ) {
            sessionIdAfterRoomJoined = ApplicationWideCurrentRoomHolder.getInstance().session

            ApplicationWideCurrentRoomHolder.getInstance().currentRoomId = roomId
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

            if (webSocketInstance != null) {
                webSocketInstance?.joinRoomWithRoomTokenAndSession(
                    roomToken,
                    sessionIdAfterRoomJoined
                )
            }
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

    private fun submitMessage(sendWithoutNotification: Boolean) {
        if (binding.messageInputView.inputEditText != null) {
            val editable = binding.messageInputView.inputEditText!!.editableText
            val mentionSpans = editable.getSpans(
                0,
                editable.length,
                Spans.MentionChipSpan::class.java
            )
            var mentionSpan: Spans.MentionChipSpan
            for (i in mentionSpans.indices) {
                mentionSpan = mentionSpans[i]
                var mentionId = mentionSpan.id
                if (mentionId.contains(" ") ||
                    mentionId.contains("@") ||
                    mentionId.startsWith("guest/") ||
                    mentionId.startsWith("group/")
                ) {
                    mentionId = "\"" + mentionId + "\""
                }
                editable.replace(editable.getSpanStart(mentionSpan), editable.getSpanEnd(mentionSpan), "@$mentionId")
            }

            binding.messageInputView.inputEditText?.setText("")
            sendStopTypingMessage()
            val replyMessageId: Int? = findViewById<RelativeLayout>(R.id.quotedChatMessageView)?.tag as Int?
            sendMessage(
                editable,
                if (findViewById<RelativeLayout>(R.id.quotedChatMessageView)?.visibility == View.VISIBLE) {
                    replyMessageId
                } else {
                    null
                },
                sendWithoutNotification
            )
            cancelReply()
        }
    }

    private fun sendMessage(message: CharSequence, replyTo: Int?, sendWithoutNotification: Boolean) {
        if (conversationUser != null) {
            chatViewModel.sendChatMessage(
                credentials!!,
                ApiUtils.getUrlForChat(chatApiVersion, conversationUser!!.baseUrl!!, roomToken),
                message,
                conversationUser!!.displayName ?: "",
                replyTo ?: 0,
                sendWithoutNotification
            )
        }
        showMicrophoneButton(true)
    }

    private fun setupWebsocket() {
        if (conversationUser == null) {
            return
        }
        webSocketInstance = WebSocketConnectionHelper.getWebSocketInstanceForUser(conversationUser!!)

        if (webSocketInstance == null) {
            Log.d(TAG, "webSocketInstance not set up. This should only happen when not using the HPB")
        }

        signalingMessageSender = webSocketInstance?.signalingMessageSender
    }

    private fun processCallStartedMessages(chatMessageList: List<ChatMessage>) {
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
                    mostRecentCallSystemMessage as ChatMessage,
                    chatMessageList
                )
            }
        } catch (e: NoSuchElementException) {
            Log.d(TAG, "No System messages found $e")
        }
    }

    private fun setupFieldsForPullChatMessages(
        lookIntoFuture: Boolean,
        xChatLastCommonRead: Int?,
        setReadMarker: Boolean
    ): HashMap<String, Int> {
        val fieldMap = HashMap<String, Int>()
        fieldMap["includeLastKnown"] = 0

        if (!lookIntoFuture && isFirstMessagesProcessing) {
            if (currentConversation != null) {
                globalLastKnownFutureMessageId = currentConversation!!.lastReadMessage
                globalLastKnownPastMessageId = currentConversation!!.lastReadMessage
                fieldMap["includeLastKnown"] = 1
            }
        }

        val lastKnown = if (lookIntoFuture) {
            globalLastKnownFutureMessageId
        } else {
            globalLastKnownPastMessageId
        }

        fieldMap["lastKnownMessageId"] = lastKnown
        xChatLastCommonRead?.let {
            fieldMap["lastCommonReadId"] = it
        }

        val timeout = if (lookIntoFuture) {
            LOOKING_INTO_FUTURE_TIMEOUT
        } else {
            0
        }

        fieldMap["timeout"] = timeout
        fieldMap["limit"] = MESSAGE_PULL_LIMIT

        if (lookIntoFuture) {
            fieldMap["lookIntoFuture"] = 1
        } else {
            fieldMap["lookIntoFuture"] = 0
        }

        if (setReadMarker) {
            fieldMap["setReadMarker"] = 1
        } else {
            fieldMap["setReadMarker"] = 0
        }
        return fieldMap
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

        if (CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.MESSAGE_EXPIRATION)) {
            deleteExpiredMessages()
        }
    }

    private fun updateReadStatusOfAllMessages(xChatLastCommonRead: Int?) {
        if (adapter != null) {
            for (message in adapter!!.items) {
                xChatLastCommonRead?.let {
                    updateReadStatusOfMessage(message, it)
                }
            }
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

    private fun processMessagesFromTheFuture(chatMessageList: List<ChatMessage>) {
        val shouldAddNewMessagesNotice = layoutManager?.findFirstVisibleItemPosition()!! > 0
        if (shouldAddNewMessagesNotice) {
            val unreadChatMessage = ChatMessage()
            unreadChatMessage.jsonMessageId = -1
            unreadChatMessage.actorId = "-1"
            unreadChatMessage.timestamp = chatMessageList[0].timestamp
            unreadChatMessage.message = context.getString(R.string.nc_new_messages)
            adapter?.addToStart(unreadChatMessage, false)
        }

        addMessagesToAdapter(shouldAddNewMessagesNotice, chatMessageList)
    }

    private fun processMessagesNotFromTheFuture(chatMessageList: List<ChatMessage>) {
        var countGroupedMessages = 0

        for (i in chatMessageList.indices) {
            if (chatMessageList.size > i + 1) {
                if (isSameDayNonSystemMessages(chatMessageList[i], chatMessageList[i + 1]) &&
                    chatMessageList[i + 1].actorId == chatMessageList[i].actorId &&
                    countGroupedMessages < GROUPED_MESSAGES_THRESHOLD
                ) {
                    chatMessageList[i].isGrouped = true
                    countGroupedMessages++
                } else {
                    countGroupedMessages = 0
                }
            }

            val chatMessage = chatMessageList[i]
            chatMessage.isOneToOneConversation =
                currentConversation?.type == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
            chatMessage.isFormerOneToOneConversation =
                (currentConversation?.type == ConversationType.FORMER_ONE_TO_ONE)
            chatMessage.activeUser = conversationUser
            chatMessage.token = roomToken
        }

        if (adapter != null) {
            adapter?.addToEnd(chatMessageList, false)
        }
        scrollToRequestedMessageIfNeeded()
        // FENOM: add here audio resume policy
        resumeAudioPlaybackIfNeeded()
    }

    private fun scrollToFirstUnreadMessage() {
        adapter?.let {
            layoutManager?.scrollToPositionWithOffset(
                it.getMessagePositionByIdInReverse("-1"),
                binding.messagesListView.height / 2
            )
        }
    }

    private fun addMessagesToAdapter(shouldAddNewMessagesNotice: Boolean, chatMessageList: List<ChatMessage>) {
        val isThereANewNotice =
            shouldAddNewMessagesNotice || adapter?.getMessagePositionByIdInReverse("-1") != -1
        for (chatMessage in chatMessageList) {
            chatMessage.activeUser = conversationUser

            val shouldScroll =
                !isThereANewNotice &&
                    !shouldAddNewMessagesNotice &&
                    layoutManager?.findFirstVisibleItemPosition() == 0 ||
                    adapter != null &&
                    adapter?.itemCount == 0

            modifyMessageCount(shouldAddNewMessagesNotice, shouldScroll)

            adapter?.let {
                chatMessage.isGrouped = (
                    it.isPreviousSameAuthor(chatMessage.actorId, -1) &&
                        it.getSameAuthorLastMessagesCount(chatMessage.actorId) %
                        GROUPED_MESSAGES_SAME_AUTHOR_THRESHOLD > 0
                    )
                chatMessage.isOneToOneConversation =
                    (currentConversation?.type == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL)
                chatMessage.isFormerOneToOneConversation =
                    (currentConversation?.type == ConversationType.FORMER_ONE_TO_ONE)
                it.addToStart(chatMessage, shouldScroll)
            }
        }
    }

    private fun modifyMessageCount(shouldAddNewMessagesNotice: Boolean, shouldScroll: Boolean) {
        if (shouldAddNewMessagesNotice) {
            binding.popupBubbleView.isShown.let {
                if (it) {
                    newMessagesCount++
                } else {
                    newMessagesCount = 1
                    binding.scrollDownButton.visibility = View.GONE
                    binding.popupBubbleView.show()
                }
            }
        } else {
            binding.scrollDownButton.visibility = View.GONE
            newMessagesCount = 0
        }
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

    private fun processHeaderChatLastGiven(response: Response<*>, isFromTheFuture: Boolean) {
        val xChatLastGivenHeader: String? = response.headers()["X-Chat-Last-Given"]

        val header = if (response.headers().size > 0 &&
            xChatLastGivenHeader?.isNotEmpty() == true
        ) {
            xChatLastGivenHeader.toInt()
        } else {
            return
        }

        if (header > 0) {
            if (isFromTheFuture) {
                globalLastKnownFutureMessageId = header
            } else {
                if (globalLastKnownFutureMessageId == -1) {
                    globalLastKnownFutureMessageId = header
                }
                globalLastKnownPastMessageId = header
            }
        }
    }

    /**
     * this method must be called after that the adatper has finished loading ChatMessages items
     * it searches by ID the message that was playing,
     * then, if it finds it, it restores audio position
     * and eventually resumes audio playback
     * @author Giacomo Pacini
     */
    private fun resumeAudioPlaybackIfNeeded() {
        if (!voiceMessageToRestoreId.equals("")) {
            Log.d(RESUME_AUDIO_TAG, "begin method to resume audio playback")

            if (adapter != null) {
                Log.d(RESUME_AUDIO_TAG, "adapter is not null, proceeding")
                val voiceMessagePosition = adapter!!.items!!.indexOfFirst {
                    it.item is ChatMessage && (it.item as ChatMessage).id == voiceMessageToRestoreId
                }
                if (voiceMessagePosition >= 0) {
                    val currentItem = adapter?.items?.get(voiceMessagePosition)?.item
                    if (currentItem is ChatMessage && currentItem.id == voiceMessageToRestoreId) {
                        currentlyPlayedVoiceMessage = currentItem
                        lastRecordMediaPosition = voiceMessageToRestoreAudioPosition * 1000
                        Log.d(RESUME_AUDIO_TAG, "trying to resume audio")
                        binding.messagesListView.scrollToPosition(voiceMessagePosition)
                        // WORKAROUND TO FETCH FILE INFO:
                        currentlyPlayedVoiceMessage!!.getImageUrl()
                        // see getImageUrl() source code
                        setUpWaveform(currentlyPlayedVoiceMessage!!, voiceMessageToRestoreWasPlaying)
                        Log.d(RESUME_AUDIO_TAG, "resume audio procedure completed")
                    } else {
                        Log.d(RESUME_AUDIO_TAG, "currentItem retrieved was not chatmessage or its id was not correct")
                    }
                } else {
                    Log.d(
                        RESUME_AUDIO_TAG,
                        "voiceMessagePosition is -1, adapter # of items: " + adapter!!.getItemCount()
                    )
                }
            } else {
                Log.d(RESUME_AUDIO_TAG, "TalkMessagesListAdapater is null")
            }
        } else {
            Log.d(RESUME_AUDIO_TAG, "No voice message to restore")
        }
        voiceMessageToRestoreId = ""
        voiceMessageToRestoreAudioPosition = 0
        voiceMessageToRestoreWasPlaying = false
    }

    private fun scrollToRequestedMessageIfNeeded() {
        intent.getStringExtra(BundleKeys.KEY_MESSAGE_ID)?.let {
            scrollToMessageWithId(it)
        }
    }

    private fun isSameDayNonSystemMessages(messageLeft: ChatMessage, messageRight: ChatMessage): Boolean {
        return TextUtils.isEmpty(messageLeft.systemMessage) &&
            TextUtils.isEmpty(messageRight.systemMessage) &&
            DateFormatter.isSameDay(messageLeft.createdAt, messageRight.createdAt)
    }

    override fun onLoadMore(page: Int, totalItemsCount: Int) {
        if (page > 1) {
            chatViewModel.refreshChatParams(
                setupFieldsForPullChatMessages(
                    false,
                    null,
                    true
                )
            )
        }
    }

    override fun format(date: Date): String {
        return if (DateFormatter.isToday(date)) {
            resources!!.getString(R.string.nc_date_header_today)
        } else if (DateFormatter.isYesterday(date)) {
            resources!!.getString(R.string.nc_date_header_yesterday)
        } else {
            DateFormatter.format(date, DateFormatter.Template.STRING_DAY_MONTH_YEAR)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.menu_conversation, menu)

        binding.messageInputView.context?.let {
            viewThemeUtils.platform.colorToolbarMenuIcon(
                it,
                menu.findItem(R.id.conversation_voice_call)
            )

            viewThemeUtils.platform.colorToolbarMenuIcon(
                it,
                menu.findItem(R.id.conversation_video_call)
            )
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
            if (CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.READ_ONLY_ROOMS)) {
                checkShowCallButtons()
            }

            val searchItem = menu.findItem(R.id.conversation_search)
            searchItem.isVisible = CapabilitiesUtil.isUnifiedSearchAvailable(spreedCapabilities)

            if (currentConversation!!.remoteServer != null ||
                !CapabilitiesUtil.isSharedItemsAvailable(spreedCapabilities)
            ) {
                menu.removeItem(R.id.shared_items)
            }

            if (currentConversation!!.remoteServer != null) {
                menu.removeItem(R.id.conversation_video_call)
                menu.removeItem(R.id.conversation_voice_call)
            } else if (CapabilitiesUtil.isAbleToCall(spreedCapabilities)) {
                conversationVoiceCallMenuItem = menu.findItem(R.id.conversation_voice_call)
                conversationVideoMenuItem = menu.findItem(R.id.conversation_video_call)

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
        startActivityForResult(intent, REQUEST_CODE_MESSAGE_SEARCH)
    }

    private fun handleSystemMessages(chatMessageList: List<ChatMessage>): List<ChatMessage> {
        val chatMessageMap = chatMessageList.map { it.id to it }.toMap().toMutableMap()
        val chatMessageIterator = chatMessageMap.iterator()
        while (chatMessageIterator.hasNext()) {
            val currentMessage = chatMessageIterator.next()

            // setDeletionFlagsAndRemoveInfomessages
            if (isInfoMessageAboutDeletion(currentMessage)) {
                if (!chatMessageMap.containsKey(currentMessage.value.parentMessage!!.id)) {
                    // if chatMessageMap doesn't contain message to delete (this happens when lookingIntoFuture),
                    // the message to delete has to be modified directly inside the adapter
                    setMessageAsDeleted(currentMessage.value.parentMessage)
                } else {
                    chatMessageMap[currentMessage.value.parentMessage!!.id]!!.isDeleted = true
                }
                chatMessageIterator.remove()
            } else if (isReactionsMessage(currentMessage)) {
                // delete reactions system messages
                if (!chatMessageMap.containsKey(currentMessage.value.parentMessage!!.id)) {
                    updateAdapterForReaction(currentMessage.value.parentMessage)
                }

                chatMessageIterator.remove()
            } else if (isPollVotedMessage(currentMessage)) {
                // delete poll system messages
                chatMessageIterator.remove()
            } else if (isEditMessage(currentMessage)) {
                if (!chatMessageMap.containsKey(currentMessage.value.parentMessage!!.id)) {
                    setMessageAsEdited(currentMessage.value.parentMessage)
                }

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

    private fun isInfoMessageAboutDeletion(currentMessage: MutableMap.MutableEntry<String, ChatMessage>): Boolean {
        return currentMessage.value.parentMessage != null && currentMessage.value.systemMessageType == ChatMessage
            .SystemMessageType.MESSAGE_DELETED
    }

    private fun isReactionsMessage(currentMessage: MutableMap.MutableEntry<String, ChatMessage>): Boolean {
        return currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.REACTION ||
            currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.REACTION_DELETED ||
            currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.REACTION_REVOKED
    }

    private fun isEditMessage(currentMessage: MutableMap.MutableEntry<String, ChatMessage>): Boolean {
        return currentMessage.value.parentMessage != null && currentMessage.value.systemMessageType == ChatMessage
            .SystemMessageType.MESSAGE_EDITED
    }

    private fun isPollVotedMessage(currentMessage: MutableMap.MutableEntry<String, ChatMessage>): Boolean {
        return currentMessage.value.systemMessageType == ChatMessage.SystemMessageType.POLL_VOTED
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
            bundle.putString(KEY_ROOM_ID, roomId)
            bundle.putString(BundleKeys.KEY_CONVERSATION_PASSWORD, roomPassword)
            bundle.putString(BundleKeys.KEY_MODIFIED_BASE_URL, conversationUser?.baseUrl!!)
            bundle.putString(KEY_CONVERSATION_NAME, it.displayName)
            bundle.putInt(KEY_RECORDING_STATE, it.callRecording)
            bundle.putBoolean(KEY_IS_MODERATOR, ConversationUtils.isParticipantOwnerOrModerator(it))
            bundle.putBoolean(
                BundleKeys.KEY_PARTICIPANT_PERMISSION_CAN_PUBLISH_AUDIO,
                participantPermissions.canPublishAudio()
            )
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

            if (it.objectType == ObjectType.ROOM) {
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
        if (hasVisibleItems(message) && !isSystemMessage(message)) {
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

    private fun isSystemMessage(message: ChatMessage): Boolean {
        return ChatMessage.MessageType.SYSTEM_MESSAGE == message.getCalculateMessageType()
    }

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
            apiVersion,
            conversationUser?.baseUrl!!,
            "1",
            null,
            message?.user?.id?.substring(INVITE_LENGTH),
            null
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
        bundle.putString(BundleKeys.KEY_FORWARD_HIDE_SOURCE_ROOM, roomId)

        val intent = Intent(this, ConversationsListActivity::class.java)
        intent.putExtras(bundle)
        startActivity(intent)
    }

    fun remindMeLater(message: ChatMessage?) {
        Log.d(TAG, "remindMeLater called")

        val chatApiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(ApiUtils.API_V1, 1))

        val newFragment: DialogFragment = DateTimePickerFragment.newInstance(roomToken, message!!.id, chatApiVersion)
        newFragment.show(supportFragmentManager, DateTimePickerFragment.TAG)
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
        val apiVersion = ApiUtils.getChatApiVersion(spreedCapabilities, intArrayOf(1))
        val type = message.getCalculateMessageType()
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
                        Log.w(TAG, "File corresponding to the uri does not exist " + shareUri.toString())
                        downloadFileToCache(message, false) {
                            uploadFile(shareUri.toString(), false, caption!!, roomToken)
                            Snackbar.make(binding.root, R.string.nc_message_sent, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            ChatMessage.MessageType.SINGLE_NC_GEOLOCATION_MESSAGE -> {
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

    private fun hasVisibleItems(message: ChatMessage): Boolean {
        return !message.isDeleted || // copy message
            message.replyable || // reply to
            message.replyable && // reply privately
            conversationUser?.userId?.isNotEmpty() == true && conversationUser!!.userId != "?" &&
            message.user.id.startsWith("users/") &&
            message.user.id.substring(ACTOR_LENGTH) != currentConversation?.actorId &&
            currentConversation?.type != ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ||
            isShowMessageDeletionButton(message) || // delete
            ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == message.getCalculateMessageType() || // forward
            message.previousMessageId > NO_PREVIOUS_MESSAGE_ID && // mark as unread
            ChatMessage.MessageType.SYSTEM_MESSAGE != message.getCalculateMessageType() &&
            BuildConfig.DEBUG
    }

    fun replyToMessage(message: IMessage?) {
        val chatMessage = message as ChatMessage?
        chatMessage?.let {
            binding.messageInputView.findViewById<ImageButton>(R.id.attachmentButton)?.visibility =
                View.GONE
            binding.messageInputView.findViewById<ImageButton>(R.id.cancelReplyButton)?.visibility =
                View.VISIBLE

            val quotedMessage = binding.messageInputView.findViewById<EmojiTextView>(R.id.quotedMessage)

            quotedMessage?.maxLines = 2
            quotedMessage?.ellipsize = TextUtils.TruncateAt.END
            quotedMessage?.text = it.text
            binding.messageInputView.findViewById<EmojiTextView>(R.id.quotedMessageAuthor)?.text =
                it.actorDisplayName ?: context.getText(R.string.nc_nick_guest)

            conversationUser?.let {
                val quotedMessageImage = binding.messageInputView.findViewById<ImageView>(R.id.quotedMessageImage)
                chatMessage.imageUrl?.let { previewImageUrl ->
                    quotedMessageImage?.visibility = View.VISIBLE

                    val px = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        QUOTED_MESSAGE_IMAGE_MAX_HEIGHT,
                        resources?.displayMetrics
                    )

                    quotedMessageImage?.maxHeight = px.toInt()
                    val layoutParams = quotedMessageImage?.layoutParams as FlexboxLayout.LayoutParams
                    layoutParams.flexGrow = 0f
                    quotedMessageImage.layoutParams = layoutParams
                    quotedMessageImage.load(previewImageUrl) {
                        addHeader("Authorization", credentials!!)
                    }
                } ?: run {
                    binding.messageInputView.findViewById<ImageView>(R.id.quotedMessageImage)?.visibility = View.GONE
                }
            }

            val quotedChatMessageView =
                binding.messageInputView.findViewById<RelativeLayout>(R.id.quotedChatMessageView)
            quotedChatMessageView?.tag = message?.jsonMessageId
            quotedChatMessageView?.visibility = View.VISIBLE
        }
    }

    private fun showMicrophoneButton(show: Boolean) {
        if (show && CapabilitiesUtil.hasSpreedFeatureCapability(
                spreedCapabilities, SpreedFeatures.VOICE_MESSAGE_SHARING
            )
        ) {
            Log.d(TAG, "Microphone shown")
            binding.messageInputView.messageSendButton.visibility = View.GONE
            binding.messageInputView.recordAudioButton.visibility = View.VISIBLE
        } else {
            Log.d(TAG, "Microphone hidden")
            binding.messageInputView.messageSendButton.visibility = View.VISIBLE
            binding.messageInputView.recordAudioButton.visibility = View.GONE
        }
    }

    private fun setMessageAsDeleted(message: IMessage?) {
        val messageTemp = message as ChatMessage
        messageTemp.isDeleted = true

        messageTemp.isOneToOneConversation =
            currentConversation?.type == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        messageTemp.activeUser = conversationUser

        adapter?.update(messageTemp)
    }

    private fun setMessageAsEdited(message: IMessage?) {
        val messageTemp = message as ChatMessage
        messageTemp.lastEditTimestamp = message.lastEditTimestamp

        messageTemp.isOneToOneConversation =
            currentConversation?.type == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        messageTemp.activeUser = conversationUser

        adapter?.update(messageTemp)
    }

    private fun updateAdapterForReaction(message: IMessage?) {
        val messageTemp = message as ChatMessage

        messageTemp.isOneToOneConversation =
            currentConversation?.type == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        messageTemp.activeUser = conversationUser

        adapter?.update(messageTemp)
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

    override fun hasContentFor(message: ChatMessage, type: Byte): Boolean {
        return when (type) {
            CONTENT_TYPE_LOCATION -> message.hasGeoLocation()
            CONTENT_TYPE_VOICE_MESSAGE -> message.isVoiceMessage
            CONTENT_TYPE_POLL -> message.isPoll()
            CONTENT_TYPE_LINK_PREVIEW -> message.isLinkPreview()
            CONTENT_TYPE_SYSTEM_MESSAGE -> !TextUtils.isEmpty(message.systemMessage)
            CONTENT_TYPE_UNREAD_NOTICE_MESSAGE -> message.id == "-1"
            CONTENT_TYPE_CALL_STARTED -> message.id == "-2"

            else -> false
        }
    }

    private fun processMostRecentMessage(recent: ChatMessage, chatMessageList: List<ChatMessage>) {
        when (recent.systemMessageType) {
            ChatMessage.SystemMessageType.CALL_STARTED -> { // add CallStartedMessage with id -2
                if (!callStarted) {
                    val callStartedChatMessage = ChatMessage()
                    callStartedChatMessage.jsonMessageId = CALL_STARTED_ID
                    callStartedChatMessage.actorId = "-2"
                    val name = if (recent.actorDisplayName.isNullOrEmpty()) "Guest" else recent.actorDisplayName
                    callStartedChatMessage.actorDisplayName = name
                    callStartedChatMessage.actorType = recent.actorType
                    callStartedChatMessage.timestamp = chatMessageList[0].timestamp
                    callStartedChatMessage.message = null
                    adapter?.addToStart(callStartedChatMessage, false)
                    callStarted = true
                }
            } // remove CallStartedMessage with id -2
            ChatMessage.SystemMessageType.CALL_ENDED,
            ChatMessage.SystemMessageType.CALL_MISSED,
            ChatMessage.SystemMessageType.CALL_TRIED,
            ChatMessage.SystemMessageType.CALL_ENDED_EVERYONE -> {
                adapter?.deleteById("-2")
                callStarted = false
            } // remove message of id -2
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
        if (currentConversation?.type != ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ||
            currentConversation?.name != userMentionClickEvent.userId
        ) {
            var apiVersion = 1
            // FIXME Fix API checking with guests?
            if (conversationUser != null) {
                apiVersion = ApiUtils.getConversationApiVersion(conversationUser!!, intArrayOf(ApiUtils.API_V4, 1))
            }

            val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
                apiVersion,
                conversationUser?.baseUrl!!,
                "1",
                null,
                userMentionClickEvent.userId,
                null
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
            startActivityForResult(TakePhotoActivity.createIntent(context), REQUEST_CODE_PICK_CAMERA)
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
                        startActivityForResult(takeVideoIntent, REQUEST_CODE_PICK_CAMERA)
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
        for (position in 0 until (adapter!!.items.size)) {
            val currentItem = adapter?.items?.get(position)?.item
            if (currentItem is ChatMessage && currentItem.id == parentMessage.id) {
                layoutManager!!.scrollToPosition(position)
                break
            }
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

    fun editMessage(message: ChatMessage) {
        editableBehaviorSubject.onNext(true)
        editMessage = message
        initMessageInputView()
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
        private const val NEW_MESSAGES_POPUP_BUBBLE_DELAY: Long = 200
        private const val GET_ROOM_INFO_DELAY_NORMAL: Long = 30000
        private const val GET_ROOM_INFO_DELAY_LOBBY: Long = 5000
        private const val HTTP_CODE_OK: Int = 200
        private const val AGE_THRESHOLD_FOR_DELETE_MESSAGE: Int = 21600000 // (6 hours in millis = 6 * 3600 * 1000)
        private const val REQUEST_CODE_CHOOSE_FILE: Int = 555
        private const val REQUEST_CODE_SELECT_CONTACT: Int = 666
        private const val REQUEST_CODE_MESSAGE_SEARCH: Int = 777
        private const val REQUEST_SHARE_FILE_PERMISSION: Int = 221
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 222
        private const val REQUEST_READ_CONTACT_PERMISSION = 234
        private const val REQUEST_CAMERA_PERMISSION = 223
        private const val REQUEST_CODE_PICK_CAMERA: Int = 333
        private const val REQUEST_CODE_SELECT_REMOTE_FILES = 888
        private const val OBJECT_MESSAGE: String = "{object}"
        private const val MINIMUM_VOICE_RECORD_DURATION: Int = 1000
        private const val MINIMUM_VOICE_RECORD_TO_STOP: Int = 200
        private const val VOICE_RECORD_CANCEL_SLIDER_X: Int = -50
        private const val VOICE_RECORD_LOCK_BUTTON_Y: Int = -130
        private const val VOICE_MESSAGE_META_DATA = "{\"messageType\":\"voice-message\"}"
        private const val VOICE_MESSAGE_FILE_SUFFIX = ".mp3"

        // Samplingrate 22050 was chosen because somehow 44100 failed to playback on safari when recorded on android.
        // Please test with firefox, chrome, safari and mobile clients if changing anything regarding the sound.
        private const val VOICE_MESSAGE_SAMPLING_RATE = 22050
        private const val VOICE_MESSAGE_ENCODING_BIT_RATE = 32000
        private const val VOICE_MESSAGE_CHANNELS = 1
        private const val FILE_DATE_PATTERN = "yyyy-MM-dd HH-mm-ss"
        private const val VIDEO_SUFFIX = ".mp4"
        private const val FULLY_OPAQUE_INT: Int = 255
        private const val SEMI_TRANSPARENT_INT: Int = 99
        private const val VOICE_MESSAGE_SEEKBAR_BASE = 1000
        private const val NO_PREVIOUS_MESSAGE_ID: Int = -1
        private const val GROUPED_MESSAGES_THRESHOLD = 4
        private const val GROUPED_MESSAGES_SAME_AUTHOR_THRESHOLD = 5
        private const val TOOLBAR_AVATAR_RATIO = 1.5
        private const val STATUS_SIZE_IN_DP = 9f
        private const val HTTP_CODE_NOT_MODIFIED = 304
        private const val HTTP_CODE_PRECONDITION_FAILED = 412
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val QUOTED_MESSAGE_IMAGE_MAX_HEIGHT = 96f
        private const val MENTION_AUTO_COMPLETE_ELEVATION = 6f
        private const val MESSAGE_PULL_LIMIT = 100
        private const val INVITE_LENGTH = 6
        private const val ACTOR_LENGTH = 6
        private const val ANIMATION_DURATION: Long = 750
        private const val LOOKING_INTO_FUTURE_TIMEOUT = 30
        private const val CHUNK_SIZE: Int = 10
        private const val ONE_SECOND_IN_MILLIS = 1000
        private const val SAMPLE_RATE = 8000
        private const val VOICE_RECORDING_LOCK_ANIMATION_DURATION = 500
        private const val AUDIO_VALUE_MAX = 40
        private const val AUDIO_VALUE_MIN = 20
        private const val AUDIO_VALUE_SLEEP: Long = 50
        private const val WHITESPACE = " "
        private const val COMMA = ", "
        private const val TYPING_INDICATOR_ANIMATION_DURATION = 200L
        private const val TYPING_INDICATOR_MAX_NAME_LENGTH = 14
        private const val TYPING_DURATION_TO_SEND_NEXT_TYPING_MESSAGE = 10000L
        private const val TYPING_INTERVAL_TO_SEND_NEXT_TYPING_MESSAGE = 1000L
        private const val TYPING_STARTED_SIGNALING_MESSAGE_TYPE = "startedTyping"
        private const val TYPING_STOPPED_SIGNALING_MESSAGE_TYPE = "stoppedTyping"
        private const val CALL_STARTED_ID = -2
        private const val MILISEC_15: Long = 15
        private const val LINEBREAK = "\n"
        private const val CURSOR_KEY = "_cursor"
        private const val CURRENT_AUDIO_MESSAGE_KEY = "CURRENT_AUDIO_MESSAGE"
        private const val CURRENT_AUDIO_POSITION_KEY = "CURRENT_AUDIO_POSITION"
        private const val CURRENT_AUDIO_WAS_PLAYING_KEY = "CURRENT_AUDIO_PLAYING"
        private const val RESUME_AUDIO_TAG = "RESUME_AUDIO_TAG"
    }
}
