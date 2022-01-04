/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * @author Tim Krüger
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.controllers

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES.O
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.ContactsContract
import android.text.Editable
import android.text.InputFilter
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.widget.AbsListView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.widget.doAfterTextChanged
import androidx.emoji.text.EmojiCompat
import androidx.emoji.widget.EmojiTextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import coil.load
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.facebook.common.executors.UiThreadImmediateExecutorService
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.google.android.flexbox.FlexboxLayout
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.activities.TakePhotoActivity
import com.nextcloud.talk.adapters.messages.IncomingLocationMessageViewHolder
import com.nextcloud.talk.adapters.messages.IncomingPreviewMessageViewHolder
import com.nextcloud.talk.adapters.messages.IncomingVoiceMessageViewHolder
import com.nextcloud.talk.adapters.messages.MagicIncomingTextMessageViewHolder
import com.nextcloud.talk.adapters.messages.MagicOutcomingTextMessageViewHolder
import com.nextcloud.talk.adapters.messages.MagicSystemMessageViewHolder
import com.nextcloud.talk.adapters.messages.MagicUnreadNoticeMessageViewHolder
import com.nextcloud.talk.adapters.messages.OutcomingLocationMessageViewHolder
import com.nextcloud.talk.adapters.messages.OutcomingPreviewMessageViewHolder
import com.nextcloud.talk.adapters.messages.OutcomingVoiceMessageViewHolder
import com.nextcloud.talk.adapters.messages.TalkMessagesListAdapter
import com.nextcloud.talk.adapters.messages.VoiceMessageInterface
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.callbacks.MentionAutocompleteCallback
import com.nextcloud.talk.components.filebrowser.controllers.BrowserController
import com.nextcloud.talk.components.filebrowser.controllers.BrowserForSharingController
import com.nextcloud.talk.controllers.base.NewBaseController
import com.nextcloud.talk.controllers.util.viewBinding
import com.nextcloud.talk.databinding.ControllerChatBinding
import com.nextcloud.talk.events.UserMentionClickEvent
import com.nextcloud.talk.events.WebSocketCommunicationEvent
import com.nextcloud.talk.jobs.DownloadFileToCacheWorker
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import com.nextcloud.talk.models.database.CapabilitiesUtil
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.conversations.RoomsOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.mention.Mention
import com.nextcloud.talk.presenters.MentionAutocompletePresenter
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet
import com.nextcloud.talk.ui.dialog.AttachmentDialog
import com.nextcloud.talk.ui.recyclerview.MessageSwipeActions
import com.nextcloud.talk.ui.recyclerview.MessageSwipeCallback
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ConductorRemapping
import com.nextcloud.talk.utils.ConductorRemapping.remapChatController
import com.nextcloud.talk.utils.ContactUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.ImageEmojiEditText
import com.nextcloud.talk.utils.KeyboardUtils
import com.nextcloud.talk.utils.MagicCharPolicy
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.UriUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ACTIVE_CONVERSATION
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder
import com.nextcloud.talk.utils.text.Spans
import com.nextcloud.talk.webrtc.MagicWebSocketInstance
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper
import com.otaliastudios.autocomplete.Autocomplete
import com.stfalcon.chatkit.commons.ImageLoader
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.messages.MessageHolders
import com.stfalcon.chatkit.messages.MessageHolders.ContentChecker
import com.stfalcon.chatkit.messages.MessagesListAdapter
import com.stfalcon.chatkit.utils.DateFormatter
import com.vanniktech.emoji.EmojiPopup
import com.yarolegovich.lovelydialog.LovelyStandardDialog
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.view_message_input.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.parceler.Parcels
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.Objects
import java.util.concurrent.ExecutionException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ChatController(args: Bundle) :
    NewBaseController(
        R.layout.controller_chat,
        args
    ),
    MessagesListAdapter.OnLoadMoreListener,
    MessagesListAdapter.Formatter<Date>,
    MessagesListAdapter.OnMessageViewLongClickListener<IMessage>,
    ContentChecker<ChatMessage>,
    VoiceMessageInterface {

    private val binding: ControllerChatBinding by viewBinding(ControllerChatBinding::bind)

    @Inject
    @JvmField
    var ncApi: NcApi? = null

    @Inject
    @JvmField
    var userUtils: UserUtils? = null

    @Inject
    @JvmField
    var eventBus: EventBus? = null

    val disposableList = ArrayList<Disposable>()

    var roomToken: String? = null
    val conversationUser: UserEntity?
    val roomPassword: String
    var credentials: String? = null
    var currentConversation: Conversation? = null
    var inConversation = false
    var historyRead = false
    var globalLastKnownFutureMessageId = -1
    var globalLastKnownPastMessageId = -1
    var adapter: TalkMessagesListAdapter<ChatMessage>? = null
    var mentionAutocomplete: Autocomplete<*>? = null
    var layoutManager: LinearLayoutManager? = null
    var lookingIntoFuture = false
    var newMessagesCount = 0
    var startCallFromNotification: Boolean? = null
    val roomId: String
    val voiceOnly: Boolean
    var isFirstMessagesProcessing = true
    var wasDetached: Boolean = false
    var emojiPopup: EmojiPopup? = null

    var myFirstMessage: CharSequence? = null
    var checkingLobbyStatus: Boolean = false

    var conversationInfoMenuItem: MenuItem? = null
    var conversationVoiceCallMenuItem: MenuItem? = null
    var conversationVideoMenuItem: MenuItem? = null

    var magicWebSocketInstance: MagicWebSocketInstance? = null

    var lobbyTimerHandler: Handler? = null
    val roomJoined: Boolean = false
    var pastPreconditionFailed = false
    var futurePreconditionFailed = false

    val filesToUpload: MutableList<String> = ArrayList()
    var sharedText: String
    var isVoiceRecordingInProgress: Boolean = false
    var currentVoiceRecordFile: String = ""

    private var recorder: MediaRecorder? = null

    var mediaPlayer: MediaPlayer? = null
    lateinit var mediaPlayerHandler: Handler
    var currentlyPlayedVoiceMessage: ChatMessage? = null

    init {
        Log.d(TAG, "init ChatController")

        setHasOptionsMenu(true)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        this.conversationUser = args.getParcelable(KEY_USER_ENTITY)
        this.roomId = args.getString(KEY_ROOM_ID, "")
        this.roomToken = args.getString(KEY_ROOM_TOKEN, "")
        this.sharedText = args.getString(BundleKeys.KEY_SHARED_TEXT, "")

        Log.d(TAG, "   roomToken = $roomToken")
        if (roomToken.isNullOrEmpty()) {
            Log.d(TAG, "   roomToken was null or empty!")
        }

        if (args.containsKey(KEY_ACTIVE_CONVERSATION)) {
            this.currentConversation = Parcels.unwrap<Conversation>(args.getParcelable(KEY_ACTIVE_CONVERSATION))
        }

        this.roomPassword = args.getString(BundleKeys.KEY_CONVERSATION_PASSWORD, "")

        if (conversationUser?.userId == "?") {
            credentials = null
        } else {
            credentials = ApiUtils.getCredentials(conversationUser!!.username, conversationUser.token)
        }

        if (args.containsKey(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
            this.startCallFromNotification = args.getBoolean(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)
        }

        this.voiceOnly = args.getBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, false)
    }

    private fun getRoomInfo() {
        val shouldRepeat = CapabilitiesUtil.hasSpreedFeatureCapability(conversationUser, "webinary-lobby")
        if (shouldRepeat) {
            checkingLobbyStatus = true
        }

        if (conversationUser != null) {
            val apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.APIv4, 1))

            ncApi?.getRoom(credentials, ApiUtils.getUrlForRoom(apiVersion, conversationUser.baseUrl, roomToken))
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(object : Observer<RoomOverall> {
                    override fun onSubscribe(d: Disposable) {
                        disposableList.add(d)
                    }

                    @Suppress("Detekt.TooGenericExceptionCaught")
                    override fun onNext(roomOverall: RoomOverall) {
                        currentConversation = roomOverall.ocs.data
                        Log.d(
                            TAG,
                            "getRoomInfo. token: " + currentConversation?.getToken() +
                                " sessionId: " + currentConversation?.sessionId
                        )
                        loadAvatarForStatusBar()

                        setTitle()
                        try {
                            setupMentionAutocomplete()
                            checkReadOnlyState()
                            checkLobbyState()

                            if (!inConversation) {
                                joinRoomWithPassword()
                            }
                        } catch (npe: NullPointerException) {
                            // view binding can be null
                            // since this is called asynchrously and UI might have been destroyed in the meantime
                            Log.i(TAG, "UI destroyed - view binding already gone")
                        }
                    }

                    override fun onError(e: Throwable) {
                    }

                    override fun onComplete() {
                        if (shouldRepeat) {
                            if (lobbyTimerHandler == null) {
                                lobbyTimerHandler = Handler()
                            }

                            lobbyTimerHandler?.postDelayed({ getRoomInfo() }, LOBBY_TIMER_DELAY)
                        }
                    }
                })
        }
    }

    private fun handleFromNotification() {
        var apiVersion = 1
        // FIXME Can this be called for guests?
        if (conversationUser != null) {
            apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.APIv4, 1))
        }

        ncApi?.getRooms(credentials, ApiUtils.getUrlForRooms(apiVersion, conversationUser?.baseUrl))
            ?.subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<RoomsOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposableList.add(d)
                }

                override fun onNext(roomsOverall: RoomsOverall) {
                    for (conversation in roomsOverall.ocs.data) {
                        if (roomId == conversation.roomId) {
                            roomToken = conversation.token
                            currentConversation = conversation
                            setTitle()
                            getRoomInfo()
                            break
                        }
                    }
                }

                override fun onError(e: Throwable) {
                }

                override fun onComplete() {
                }
            })
    }

    private fun loadAvatarForStatusBar() {
        if (inOneToOneCall() && activity != null && conversationVoiceCallMenuItem != null) {
            val avatarSize = DisplayUtils.convertDpToPixel(
                conversationVoiceCallMenuItem?.icon!!
                    .intrinsicWidth.toFloat(),
                activity
            ).toInt()

            val imageRequest = DisplayUtils.getImageRequestForUrl(
                ApiUtils.getUrlForAvatarWithNameAndPixels(
                    conversationUser?.baseUrl,
                    currentConversation?.name, avatarSize / 2
                ),
                conversationUser!!
            )

            val imagePipeline = Fresco.getImagePipeline()
            val dataSource = imagePipeline.fetchDecodedImage(imageRequest, null)

            dataSource.subscribe(
                object : BaseBitmapDataSubscriber() {
                    override fun onNewResultImpl(bitmap: Bitmap?) {
                        if (actionBar != null && bitmap != null && resources != null) {
                            val roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(resources!!, bitmap)
                            roundedBitmapDrawable.isCircular = true
                            roundedBitmapDrawable.setAntiAlias(true)
                            actionBar?.setIcon(roundedBitmapDrawable)
                        }
                    }

                    override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>) {}
                },
                UiThreadImmediateExecutorService.getInstance()
            )
        }
    }

    private fun inOneToOneCall() = currentConversation != null && currentConversation?.type != null &&
        currentConversation?.type == Conversation.ConversationType
        .ROOM_TYPE_ONE_TO_ONE_CALL

    override fun onViewBound(view: View) {
        actionBar?.show()
        var adapterWasNull = false

        if (adapter == null) {
            binding.progressBar.visibility = View.VISIBLE

            adapterWasNull = true

            val messageHolders = MessageHolders()
            val profileBottomSheet = ProfileBottomSheet(ncApi!!, conversationUser!!, router)

            messageHolders.setIncomingTextConfig(
                MagicIncomingTextMessageViewHolder::class.java,
                R.layout.item_custom_incoming_text_message,
                profileBottomSheet
            )
            messageHolders.setOutcomingTextConfig(
                MagicOutcomingTextMessageViewHolder::class.java,
                R.layout.item_custom_outcoming_text_message
            )

            messageHolders.setIncomingImageConfig(
                IncomingPreviewMessageViewHolder::class.java,
                R.layout.item_custom_incoming_preview_message,
                profileBottomSheet
            )

            messageHolders.setOutcomingImageConfig(
                OutcomingPreviewMessageViewHolder::class.java,
                R.layout.item_custom_outcoming_preview_message
            )

            messageHolders.registerContentType(
                CONTENT_TYPE_SYSTEM_MESSAGE,
                MagicSystemMessageViewHolder::class.java,
                R.layout.item_system_message,
                MagicSystemMessageViewHolder::class.java,
                R.layout.item_system_message,
                this
            )

            messageHolders.registerContentType(
                CONTENT_TYPE_UNREAD_NOTICE_MESSAGE,
                MagicUnreadNoticeMessageViewHolder::class.java,
                R.layout.item_date_header,
                MagicUnreadNoticeMessageViewHolder::class.java,
                R.layout.item_date_header,
                this
            )

            messageHolders.registerContentType(
                CONTENT_TYPE_LOCATION,
                IncomingLocationMessageViewHolder::class.java,
                profileBottomSheet,
                R.layout.item_custom_incoming_location_message,
                OutcomingLocationMessageViewHolder::class.java,
                null,
                R.layout.item_custom_outcoming_location_message,
                this
            )

            messageHolders.registerContentType(
                CONTENT_TYPE_VOICE_MESSAGE,
                IncomingVoiceMessageViewHolder::class.java,
                profileBottomSheet,
                R.layout.item_custom_incoming_voice_message,
                OutcomingVoiceMessageViewHolder::class.java,
                null,
                R.layout.item_custom_outcoming_voice_message,
                this
            )

            var senderId = ""
            if (!conversationUser?.userId.equals("?")) {
                senderId = "users/" + conversationUser?.userId
            } else {
                senderId = currentConversation?.getActorType() + "/" + currentConversation?.getActorId()
            }

            Log.d(TAG, "Initialize TalkMessagesListAdapter with senderId: " + senderId)

            adapter = TalkMessagesListAdapter(
                senderId,
                messageHolders,
                ImageLoader { imageView, url, payload ->
                    val draweeController = Fresco.newDraweeControllerBuilder()
                        .setImageRequest(DisplayUtils.getImageRequestForUrl(url, conversationUser))
                        .setControllerListener(DisplayUtils.getImageControllerListener(imageView))
                        .setOldController(imageView.controller)
                        .setAutoPlayAnimations(true)
                        .build()
                    imageView.controller = draweeController
                },
                this
            )
        } else {
            binding.messagesListView.visibility = View.VISIBLE
        }

        binding.messagesListView.setAdapter(adapter)
        adapter?.setLoadMoreListener(this)
        adapter?.setDateHeadersFormatter { format(it) }
        adapter?.setOnMessageViewLongClickListener { view, message -> onMessageViewLongClick(view, message) }

        adapter?.registerViewClickListener(
            R.id.playPauseBtn
        ) { view, message ->
            val filename = message.getSelectedIndividualHashMap()["name"]
            val file = File(context!!.cacheDir, filename!!)
            if (file.exists()) {
                if (message.isPlayingVoiceMessage) {
                    pausePlayback(message)
                } else {
                    startPlayback(message)
                }
            } else {
                downloadFileToCache(message)
            }
        }

        if (context != null) {
            val messageSwipeController = MessageSwipeCallback(
                activity!!,
                object : MessageSwipeActions {
                    override fun showReplyUI(position: Int) {
                        val chatMessage = adapter?.items?.get(position)?.item as ChatMessage?
                        replyToMessage(chatMessage, chatMessage?.jsonMessageId)
                    }
                }
            )

            val itemTouchHelper = ItemTouchHelper(messageSwipeController)
            itemTouchHelper.attachToRecyclerView(binding.messagesListView)
        }

        layoutManager = binding.messagesListView.layoutManager as LinearLayoutManager?

        binding.popupBubbleView.setRecyclerView(binding.messagesListView)

        binding.popupBubbleView.setPopupBubbleListener { context ->
            if (newMessagesCount != 0) {
                val scrollPosition: Int
                if (newMessagesCount - 1 < 0) {
                    scrollPosition = 0
                } else {
                    scrollPosition = newMessagesCount - 1
                }
                Handler().postDelayed(
                    {
                        binding.messagesListView.smoothScrollToPosition(scrollPosition)
                    },
                    NEW_MESSAGES_POPUP_BUBBLE_DELAY
                )
            }
        }

        binding.messageInputView.setPadding(0, 0, 0, 0)

        binding.messagesListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    if (newMessagesCount != 0 && layoutManager != null) {
                        if (layoutManager!!.findFirstCompletelyVisibleItemPosition() < newMessagesCount) {
                            newMessagesCount = 0

                            if (binding.popupBubbleView.isShown) {
                                binding.popupBubbleView.hide()
                            }
                        }
                    }
                }
            }
        })

        val filters = arrayOfNulls<InputFilter>(1)
        val lengthFilter = CapabilitiesUtil.getMessageMaxLength(conversationUser) ?: MESSAGE_MAX_LENGTH

        filters[0] = InputFilter.LengthFilter(lengthFilter)
        binding.messageInputView.inputEditText?.filters = filters

        binding.messageInputView.inputEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            @Suppress("Detekt.TooGenericExceptionCaught")
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                try {
                    if (s.length >= lengthFilter) {
                        binding.messageInputView.inputEditText?.error = String.format(
                            Objects.requireNonNull<Resources>(resources).getString(R.string.nc_limit_hit),
                            Integer.toString(lengthFilter)
                        )
                    } else {
                        binding.messageInputView.inputEditText?.error = null
                    }

                    val editable = binding.messageInputView.inputEditText?.editableText
                    if (editable != null && binding.messageInputView.inputEditText != null) {
                        val mentionSpans = editable.getSpans(
                            0, binding.messageInputView.inputEditText!!.length(),
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
                } catch (npe: NullPointerException) {
                    // view binding can be null
                    // since this is called asynchronously and UI might have been destroyed in the meantime
                    Log.i(TAG, "UI destroyed - view binding already gone")
                }
            }

            override fun afterTextChanged(s: Editable) {
            }
        })

        // Image keyboard support
        // See: https://developer.android.com/guide/topics/text/image-keyboard
        (binding.messageInputView.inputEditText as ImageEmojiEditText).onCommitContentListener = {
            uploadFiles(mutableListOf(it.toString()), false)
        }

        showMicrophoneButton(true)

        binding.messageInputView.messageInput.doAfterTextChanged {
            if (binding.messageInputView.messageInput.text.isEmpty()) {
                showMicrophoneButton(true)
            } else {
                showMicrophoneButton(false)
            }
        }

        var sliderInitX = 0F
        var downX = 0f
        var deltaX = 0f

        var voiceRecordStartTime = 0L
        var voiceRecordEndTime = 0L

        binding.messageInputView.recordAudioButton.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                view.performClick()
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!isRecordAudioPermissionGranted()) {
                            requestRecordAudioPermissions()
                            return true
                        }
                        if (!UploadAndShareFilesWorker.isStoragePermissionGranted(context!!)) {
                            UploadAndShareFilesWorker.requestStoragePermission(this@ChatController)
                            return true
                        }

                        voiceRecordStartTime = System.currentTimeMillis()

                        setVoiceRecordFileName()
                        startAudioRecording(currentVoiceRecordFile)
                        downX = event.x
                        showRecordAudioUi(true)
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        Log.d(TAG, "ACTION_CANCEL. same as for UP")
                        if (!isVoiceRecordingInProgress || !isRecordAudioPermissionGranted()) {
                            return true
                        }

                        stopAndDiscardAudioRecording()
                        showRecordAudioUi(false)
                        binding.messageInputView.slideToCancelDescription.x = sliderInitX
                    }
                    MotionEvent.ACTION_UP -> {
                        Log.d(TAG, "ACTION_UP. stop recording??")
                        if (!isVoiceRecordingInProgress || !isRecordAudioPermissionGranted()) {
                            return true
                        }
                        showRecordAudioUi(false)

                        voiceRecordEndTime = System.currentTimeMillis()
                        val voiceRecordDuration = voiceRecordEndTime - voiceRecordStartTime
                        if (voiceRecordDuration < MINIMUM_VOICE_RECORD_DURATION) {
                            Log.d(TAG, "voiceRecordDuration: " + voiceRecordDuration)
                            Toast.makeText(
                                context,
                                context!!.getString(R.string.nc_voice_message_hold_to_record_info),
                                Toast.LENGTH_SHORT
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

                        if (!isVoiceRecordingInProgress || !isRecordAudioPermissionGranted()) {
                            return true
                        }

                        showRecordAudioUi(true)

                        if (sliderInitX == 0.0F) {
                            sliderInitX = binding.messageInputView.slideToCancelDescription.x
                        }

                        val movedX: Float = event.x
                        deltaX = movedX - downX

                        // only allow slide to left
                        if (binding.messageInputView.slideToCancelDescription.x > sliderInitX) {
                            binding.messageInputView.slideToCancelDescription.x = sliderInitX
                        }

                        if (binding.messageInputView.slideToCancelDescription.x < VOICE_RECORD_CANCEL_SLIDER_X) {
                            Log.d(TAG, "stopping recording because slider was moved to left")
                            stopAndDiscardAudioRecording()
                            showRecordAudioUi(false)
                            binding.messageInputView.slideToCancelDescription.x = sliderInitX
                            return true
                        } else {
                            binding.messageInputView.slideToCancelDescription.x = binding.messageInputView
                                .slideToCancelDescription.x + deltaX
                            downX = movedX
                        }
                    }
                }

                return v?.onTouchEvent(event) ?: true
            }
        })

        binding.messageInputView.inputEditText?.setText(sharedText)
        binding.messageInputView.setAttachmentsListener {
            activity?.let { AttachmentDialog(it, this).show() }
        }

        binding.messageInputView.button.setOnClickListener { v -> submitMessage() }

        binding.messageInputView.button.contentDescription = resources?.getString(
            R.string
                .nc_description_send_message_button
        )

        if (currentConversation != null && currentConversation?.roomId != null) {
            loadAvatarForStatusBar()
            setTitle()
        }

        if (adapterWasNull) {
            // we're starting
            if (TextUtils.isEmpty(roomToken)) {
                handleFromNotification()
            } else {
                getRoomInfo()
            }
        }
        super.onViewBound(view)
    }

    private fun startPlayback(message: ChatMessage) {

        if (!this.isAttached) {
            // don't begin to play voice message if screen is not visible anymore.
            // this situation might happen if file is downloading but user already left the chatview.
            // If user returns to chatview, the old chatview instance is not attached anymore
            // and he has to click the play button again (which is considered to be okay)
            return
        }

        initMediaPlayer(message)

        if (!mediaPlayer!!.isPlaying) {
            mediaPlayer!!.start()
        }

        mediaPlayerHandler = Handler()
        activity?.runOnUiThread(object : Runnable {
            override fun run() {
                if (mediaPlayer != null) {
                    val currentPosition: Int = mediaPlayer!!.currentPosition / VOICE_MESSAGE_SEEKBAR_BASE
                    message.voiceMessagePlayedSeconds = currentPosition
                    adapter?.update(message)
                }
                mediaPlayerHandler.postDelayed(this, SECOND)
            }
        })

        message.isDownloadingVoiceMessage = false
        message.isPlayingVoiceMessage = true
        adapter?.update(message)
    }

    private fun pausePlayback(message: ChatMessage) {
        if (mediaPlayer!!.isPlaying) {
            mediaPlayer!!.pause()
        }

        message.isPlayingVoiceMessage = false
        adapter?.update(message)
    }

    private fun initMediaPlayer(message: ChatMessage) {
        if (message != currentlyPlayedVoiceMessage) {
            currentlyPlayedVoiceMessage?.let { stopMediaPlayer(it) }
        }

        if (mediaPlayer == null) {
            val fileName = message.getSelectedIndividualHashMap()["name"]
            val absolutePath = context!!.cacheDir.absolutePath + "/" + fileName
            mediaPlayer = MediaPlayer().apply {
                setDataSource(absolutePath)
                prepare()
            }
            currentlyPlayedVoiceMessage = message
            message.voiceMessageDuration = mediaPlayer!!.duration / VOICE_MESSAGE_SEEKBAR_BASE

            mediaPlayer!!.setOnCompletionListener {
                stopMediaPlayer(message)
            }
        } else {
            Log.e(TAG, "mediaPlayer was not null. This should not happen!")
        }
    }

    private fun stopMediaPlayer(message: ChatMessage) {
        message.isPlayingVoiceMessage = false
        message.resetVoiceMessage = true
        adapter?.update(message)

        currentlyPlayedVoiceMessage = null

        mediaPlayerHandler.removeCallbacksAndMessages(null)

        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun updateMediaPlayerProgressBySlider(messageWithSlidedProgress: ChatMessage, progress: Int) {
        if (mediaPlayer != null) {
            if (messageWithSlidedProgress == currentlyPlayedVoiceMessage) {
                mediaPlayer!!.seekTo(progress * VOICE_MESSAGE_SEEKBAR_BASE)
            }
        }
    }

    @SuppressLint("LongLogTag")
    private fun downloadFileToCache(message: ChatMessage) {
        message.isDownloadingVoiceMessage = true
        adapter?.update(message)

        val baseUrl = message.activeUser.baseUrl
        val userId = message.activeUser.userId
        val attachmentFolder = CapabilitiesUtil.getAttachmentFolder(message.activeUser)
        val fileName = message.getSelectedIndividualHashMap()["name"]
        var size = message.getSelectedIndividualHashMap()["size"]
        if (size == null) {
            size = "-1"
        }
        val fileSize = Integer.valueOf(size)
        val fileId = message.getSelectedIndividualHashMap()["id"]
        val path = message.getSelectedIndividualHashMap()["path"]

        // check if download worker is already running
        val workers = WorkManager.getInstance(
            context!!
        ).getWorkInfosByTag(fileId!!)
        try {
            for (workInfo in workers.get()) {
                if (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED) {
                    Log.d(TAG, "Download worker for " + fileId + " is already running or scheduled")
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
            .putInt(DownloadFileToCacheWorker.KEY_FILE_SIZE, fileSize)
            .build()

        val downloadWorker: OneTimeWorkRequest = OneTimeWorkRequest.Builder(DownloadFileToCacheWorker::class.java)
            .setInputData(data)
            .addTag(fileId)
            .build()

        WorkManager.getInstance().enqueue(downloadWorker)

        WorkManager.getInstance(context!!).getWorkInfoByIdLiveData(downloadWorker.id)
            .observeForever { workInfo: WorkInfo ->
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    startPlayback(message)
                }
            }
    }

    @SuppressLint("SimpleDateFormat")
    private fun setVoiceRecordFileName() {
        val pattern = "yyyy-MM-dd HH-mm-ss"
        val simpleDateFormat = SimpleDateFormat(pattern)
        val date: String = simpleDateFormat.format(Date())

        val fileNameWithoutSuffix = String.format(
            context!!.resources.getString(R.string.nc_voice_message_filename),
            date, currentConversation!!.displayName
        )
        val fileName = fileNameWithoutSuffix + VOICE_MESSAGE_FILE_SUFFIX

        currentVoiceRecordFile = "${context!!.cacheDir.absolutePath}/$fileName"
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
        } else {
            binding.messageInputView.microphoneEnabledInfo.visibility = View.GONE
            binding.messageInputView.microphoneEnabledInfoBackground.visibility = View.GONE
            binding.messageInputView.audioRecordDuration.visibility = View.GONE
            binding.messageInputView.slideToCancelDescription.visibility = View.GONE
            binding.messageInputView.attachmentButton.visibility = View.VISIBLE
            binding.messageInputView.smileyButton.visibility = View.VISIBLE
            binding.messageInputView.messageInput.visibility = View.VISIBLE
            binding.messageInputView.messageInput.hint =
                context?.resources?.getString(R.string.nc_hint_enter_a_message)
        }
    }

    private fun isRecordAudioPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PermissionChecker.checkSelfPermission(
                context!!,
                Manifest.permission.RECORD_AUDIO
            ) == PermissionChecker.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PermissionChecker.checkSelfPermission(
                context!!,
                Manifest.permission.CAMERA
            ) == PermissionChecker.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun startAudioRecording(file: String) {
        binding.messageInputView.audioRecordDuration.base = SystemClock.elapsedRealtime()
        binding.messageInputView.audioRecordDuration.start()

        val animation: Animation = AlphaAnimation(1.0f, 0.0f)
        animation.duration = 750
        animation.interpolator = LinearInterpolator()
        animation.repeatCount = Animation.INFINITE
        animation.repeatMode = Animation.REVERSE
        binding.messageInputView.microphoneEnabledInfo.startAnimation(animation)

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFile(file)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            try {
                prepare()
            } catch (e: IOException) {
                Log.e(TAG, "prepare for audio recording failed")
            }

            try {
                start()
                isVoiceRecordingInProgress = true
            } catch (e: IllegalStateException) {
                Log.e(TAG, "start for audio recording failed")
            }

            vibrate()
        }
    }

    private fun stopAndSendAudioRecording() {
        stopAudioRecording()
        val uri = Uri.fromFile(File(currentVoiceRecordFile))
        uploadFiles(mutableListOf(uri.toString()), true)
    }

    private fun stopAndDiscardAudioRecording() {
        stopAudioRecording()

        val cachedFile = File(currentVoiceRecordFile)
        cachedFile.delete()
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private fun stopAudioRecording() {
        binding.messageInputView.audioRecordDuration.stop()
        binding.messageInputView.microphoneEnabledInfo.clearAnimation()

        if (isVoiceRecordingInProgress) {
            recorder?.apply {
                try {
                    stop()
                    release()
                    isVoiceRecordingInProgress = false
                    Log.d(TAG, "stopped recorder. isVoiceRecordingInProgress = false")
                } catch (e: RuntimeException) {
                    Log.w(TAG, "error while stopping recorder!")
                }

                vibrate()
            }
            recorder = null
        } else {
            Log.e(TAG, "tried to stop audio recorder but it was not recording")
        }
    }

    fun vibrate() {
        val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= O) {
            vibrator.vibrate(VibrationEffect.createOneShot(SHORT_VIBRATE, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(SHORT_VIBRATE)
        }
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

    private fun checkReadOnlyState() {
        if (currentConversation != null && isAlive()) {
            if (currentConversation?.shouldShowLobby(conversationUser) ?: false ||
                currentConversation?.conversationReadOnlyState != null &&
                currentConversation?.conversationReadOnlyState ==
                Conversation.ConversationReadOnlyState.CONVERSATION_READ_ONLY
            ) {

                conversationVoiceCallMenuItem?.icon?.alpha = SEMI_TRANSPARENT_INT
                conversationVideoMenuItem?.icon?.alpha = SEMI_TRANSPARENT_INT
                binding.messageInputView.visibility = View.GONE
            } else {
                if (conversationVoiceCallMenuItem != null) {
                    conversationVoiceCallMenuItem?.icon?.alpha = FULLY_OPAQUE_INT
                }

                if (conversationVideoMenuItem != null) {
                    conversationVideoMenuItem?.icon?.alpha = FULLY_OPAQUE_INT
                }

                if (currentConversation != null && currentConversation!!.shouldShowLobby(conversationUser)
                ) {
                    binding.messageInputView.visibility = View.GONE
                } else {
                    binding.messageInputView.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun checkLobbyState() {
        if (currentConversation != null &&
            currentConversation?.isLobbyViewApplicable(conversationUser) ?: false &&
            isAlive()
        ) {

            if (!checkingLobbyStatus) {
                getRoomInfo()
            }

            if (currentConversation?.shouldShowLobby(conversationUser) ?: false) {
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
                    val timestamp = currentConversation?.lobbyTimer ?: 0
                    val stringWithStartDate = String.format(
                        resources!!.getString(R.string.nc_lobby_start_date),
                        DateUtils.getLocalDateStringFromTimestampForLobby(timestamp)
                    )
                    val relativeTime = DateUtils.relativeStartTimeForLobby(timestamp, resources!!)

                    sb.append("$stringWithStartDate - $relativeTime")
                        .append("\n\n")
                }

                sb.append(currentConversation!!.description)
                binding.lobby.lobbyTextView.text = sb.toString()
            } else {
                binding.lobby.lobbyView.visibility = View.GONE
                binding.messagesListView.visibility = View.VISIBLE
                binding.messageInputView.inputEditText?.visibility = View.VISIBLE
                if (isFirstMessagesProcessing && pastPreconditionFailed) {
                    pastPreconditionFailed = false
                    pullChatMessages(0)
                } else if (futurePreconditionFailed) {
                    futurePreconditionFailed = false
                    pullChatMessages(1)
                }
            }
        } else {
            binding.lobby.lobbyView.visibility = View.GONE
            binding.messagesListView.visibility = View.VISIBLE
            binding.messageInputView.inputEditText?.visibility = View.VISIBLE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (resultCode != RESULT_OK) {
            Log.e(TAG, "resultCode for received intent was != ok")
            return
        }

        if (requestCode == REQUEST_CODE_CHOOSE_FILE) {
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

                val filenamesWithLinebreaks = StringBuilder("\n")

                for (file in filesToUpload) {
                    val filename = UriUtils.getFileName(Uri.parse(file), context)
                    filenamesWithLinebreaks.append(filename).append("\n")
                }

                val confirmationQuestion = when (filesToUpload.size) {
                    1 -> context?.resources?.getString(R.string.nc_upload_confirm_send_single)?.let {
                        String.format(it, title)
                    }
                    else -> context?.resources?.getString(R.string.nc_upload_confirm_send_multiple)?.let {
                        String.format(it, title)
                    }
                }

                LovelyStandardDialog(activity)
                    .setPositiveButtonColorRes(R.color.nc_darkGreen)
                    .setTitle(confirmationQuestion)
                    .setMessage(filenamesWithLinebreaks.toString())
                    .setPositiveButton(R.string.nc_yes) { v ->
                        if (UploadAndShareFilesWorker.isStoragePermissionGranted(context!!)) {
                            uploadFiles(filesToUpload, false)
                        } else {
                            UploadAndShareFilesWorker.requestStoragePermission(this)
                        }
                    }
                    .setNegativeButton(R.string.nc_no) {}
                    .show()
            } catch (e: IllegalStateException) {
                Toast.makeText(context, context?.resources?.getString(R.string.nc_upload_failed), Toast.LENGTH_LONG)
                    .show()
                Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, context?.resources?.getString(R.string.nc_upload_failed), Toast.LENGTH_LONG)
                    .show()
                Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
            }
        } else if (requestCode == REQUEST_CODE_SELECT_CONTACT) {
            val contactUri = intent?.data ?: return
            val cursor: Cursor? = activity?.contentResolver!!.query(contactUri, null, null, null, null)

            if (cursor != null && cursor.moveToFirst()) {
                val id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                val fileName = ContactUtils.getDisplayNameFromDeviceContact(context!!, id) + ".vcf"
                val file = File(context?.cacheDir, fileName)
                writeContactToVcfFile(cursor, file)

                val shareUri = FileProvider.getUriForFile(
                    activity!!,
                    BuildConfig.APPLICATION_ID,
                    File(file.absolutePath)
                )
                uploadFiles(mutableListOf(shareUri.toString()), false)
            }
            cursor?.close()
        } else if (requestCode == REQUEST_CODE_PICK_CAMERA) {
            if (resultCode == RESULT_OK) {
                try {
                    checkNotNull(intent)
                    filesToUpload.clear()
                    run {
                        checkNotNull(intent.data)
                        intent.data.let {
                            filesToUpload.add(intent.data.toString())
                        }
                    }
                    require(filesToUpload.isNotEmpty())

                    if (UploadAndShareFilesWorker.isStoragePermissionGranted(context!!)) {
                        uploadFiles(filesToUpload, false)
                    } else {
                        UploadAndShareFilesWorker.requestStoragePermission(this)
                    }
                } catch (e: IllegalStateException) {
                    Toast.makeText(context, context?.resources?.getString(R.string.nc_upload_failed), Toast.LENGTH_LONG)
                        .show()
                    Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(context, context?.resources?.getString(R.string.nc_upload_failed), Toast.LENGTH_LONG)
                        .show()
                    Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
                }
            }
        }
    }

    private fun writeContactToVcfFile(cursor: Cursor, file: File) {
        val lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY))
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)

        val fd: AssetFileDescriptor = activity?.contentResolver!!.openAssetFileDescriptor(uri, "r")!!
        val fis = fd.createInputStream()

        file.createNewFile()
        fis.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == UploadAndShareFilesWorker.REQUEST_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(ConversationsListController.TAG, "upload starting after permissions were granted")
                if (filesToUpload.isNotEmpty()) {
                    uploadFiles(filesToUpload, false)
                }
            } else {
                Toast
                    .makeText(context, context?.getString(R.string.read_storage_no_permission), Toast.LENGTH_LONG)
                    .show()
            }
        } else if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // do nothing. user will tap on the microphone again if he wants to record audio..
            } else {
                Toast.makeText(
                    context,
                    context!!.getString(R.string.nc_voice_message_missing_audio_permission),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (requestCode == REQUEST_READ_CONTACT_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                startActivityForResult(intent, REQUEST_CODE_SELECT_CONTACT)
            } else {
                Toast.makeText(
                    context,
                    context!!.getString(R.string.nc_share_contact_permission),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "launch cam activity since permission for cam has been granted")
                startActivityForResult(TakePhotoActivity.createIntent(context!!), REQUEST_CODE_PICK_CAMERA)
            } else {
                Toast
                    .makeText(context, context?.getString(R.string.take_photo_permission), Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun uploadFiles(files: MutableList<String>, isVoiceMessage: Boolean) {
        var metaData = ""
        if (isVoiceMessage) {
            metaData = VOICE_MESSAGE_META_DATA
        }

        try {
            require(files.isNotEmpty())
            val data: Data = Data.Builder()
                .putStringArray(UploadAndShareFilesWorker.DEVICE_SOURCEFILES, files.toTypedArray())
                .putString(
                    UploadAndShareFilesWorker.NC_TARGETPATH,
                    CapabilitiesUtil.getAttachmentFolder(conversationUser)
                )
                .putString(UploadAndShareFilesWorker.ROOM_TOKEN, roomToken)
                .putString(UploadAndShareFilesWorker.META_DATA, metaData)
                .build()
            val uploadWorker: OneTimeWorkRequest = OneTimeWorkRequest.Builder(UploadAndShareFilesWorker::class.java)
                .setInputData(data)
                .build()
            WorkManager.getInstance().enqueue(uploadWorker)

            if (!isVoiceMessage) {
                Toast.makeText(
                    context, context?.getString(R.string.nc_upload_in_progess),
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, context?.resources?.getString(R.string.nc_upload_failed), Toast.LENGTH_LONG).show()
            Log.e(javaClass.simpleName, "Something went wrong when trying to upload file", e)
        }
    }

    fun sendSelectLocalFileIntent() {
        val action = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(
            Intent.createChooser(
                action,
                context?.resources?.getString(
                    R.string.nc_upload_choose_local_files
                )
            ),
            REQUEST_CODE_CHOOSE_FILE
        )
    }

    fun sendChooseContactIntent() {
        requestReadContacts()
    }

    fun showBrowserScreen(browserType: BrowserController.BrowserType) {
        val bundle = Bundle()
        bundle.putParcelable(BundleKeys.KEY_BROWSER_TYPE, Parcels.wrap<BrowserController.BrowserType>(browserType))
        bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, Parcels.wrap<UserEntity>(conversationUser))
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken)
        router.pushController(
            RouterTransaction.with(BrowserForSharingController(bundle))
                .pushChangeHandler(VerticalChangeHandler())
                .popChangeHandler(VerticalChangeHandler())
        )
    }

    fun showShareLocationScreen() {
        Log.d(TAG, "showShareLocationScreen")

        val bundle = Bundle()
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken)
        router.pushController(
            RouterTransaction.with(LocationPickerController(bundle))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    private fun showConversationInfoScreen() {
        val bundle = Bundle()
        bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, conversationUser)
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken)
        bundle.putBoolean(BundleKeys.KEY_ROOM_ONE_TO_ONE, inOneToOneCall())
        router.pushController(
            RouterTransaction.with(ConversationInfoController(bundle))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler())
        )
    }

    private fun setupMentionAutocomplete() {
        if (isAlive()) {
            val elevation = 6f
            resources?.let {
                val backgroundDrawable = ColorDrawable(it.getColor(R.color.bg_default))
                val presenter = MentionAutocompletePresenter(activity, roomToken)
                val callback = MentionAutocompleteCallback(
                    activity,
                    conversationUser,
                    binding.messageInputView.inputEditText
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
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        Log.d(TAG, "onAttach")
        eventBus?.register(this)

        if (conversationUser?.userId != "?" &&
            CapabilitiesUtil.hasSpreedFeatureCapability(conversationUser, "mention-flag") ?: false &&
            activity != null
        ) {
            activity?.findViewById<View>(R.id.toolbar)?.setOnClickListener { v -> showConversationInfoScreen() }
        }

        ApplicationWideCurrentRoomHolder.getInstance().currentRoomId = roomId
        ApplicationWideCurrentRoomHolder.getInstance().currentRoomToken = roomToken
        ApplicationWideCurrentRoomHolder.getInstance().userInRoom = conversationUser

        val smileyButton = binding.messageInputView.findViewById<ImageButton>(R.id.smileyButton)

        emojiPopup = binding.messageInputView.inputEditText?.let {
            EmojiPopup.Builder.fromRootView(view).setOnEmojiPopupShownListener {
                if (resources != null) {
                    smileyButton?.setImageDrawable(
                        ContextCompat.getDrawable(context!!, R.drawable.ic_baseline_keyboard_24)
                    )
                }
            }.setOnEmojiPopupDismissListener {
                smileyButton?.setImageDrawable(
                    ContextCompat.getDrawable(context!!, R.drawable.ic_insert_emoticon_black_24dp)
                )
            }.setOnEmojiClickListener { emoji,
                imageView ->
                binding.messageInputView.inputEditText?.editableText?.append(" ")
            }.build(it)
        }

        smileyButton?.setOnClickListener {
            emojiPopup?.toggle()
        }

        binding.messageInputView.findViewById<ImageButton>(R.id.cancelReplyButton)?.setOnClickListener {
            cancelReply()
        }

        if (activity != null) {
            KeyboardUtils(activity, getView(), false)
        }

        cancelNotificationsForCurrentConversation()

        if (inConversation) {
            if (wasDetached) {
                currentConversation?.sessionId = "0"
                wasDetached = false
                Log.d(TAG, "execute joinRoomWithPassword in onAttach")
                joinRoomWithPassword()
            }
        }
    }

    private fun cancelReply() {
        binding.messageInputView.findViewById<RelativeLayout>(R.id.quotedChatMessageView)?.visibility = View.GONE
        binding.messageInputView.findViewById<ImageButton>(R.id.attachmentButton)?.visibility = View.VISIBLE
    }

    private fun cancelNotificationsForCurrentConversation() {
        if (conversationUser != null) {
            if (!TextUtils.isEmpty(roomToken)) {
                NotificationUtils.cancelExistingNotificationsForRoom(
                    applicationContext,
                    conversationUser,
                    roomToken!!
                )
            }
        }
    }

    override fun onDetach(view: View) {
        super.onDetach(view)
        Log.d(TAG, "onDetach")
        eventBus?.unregister(this)

        if (activity != null) {
            activity?.findViewById<View>(R.id.toolbar)?.setOnClickListener(null)
        }

        if (conversationUser != null &&
            activity != null &&
            !activity?.isChangingConfigurations!! &&
            !ApplicationWideCurrentRoomHolder.getInstance().isInCall &&
            !ApplicationWideCurrentRoomHolder.getInstance().isDialing
        ) {
            ApplicationWideCurrentRoomHolder.getInstance().clear()
            wasDetached = true
            leaveRoom()
        }

        if (mentionAutocomplete != null && mentionAutocomplete!!.isPopupShowing) {
            mentionAutocomplete?.dismissPopup()
        }
    }

    override val title: String
        get() =
            if (currentConversation?.displayName != null) {
                try {
                    " " + EmojiCompat.get().process(currentConversation?.displayName as CharSequence).toString()
                } catch (e: IllegalStateException) {
                    " " + currentConversation?.displayName
                }
            } else {
                ""
            }

    public override fun onDestroy() {
        super.onDestroy()

        if (activity != null) {
            activity?.findViewById<View>(R.id.toolbar)?.setOnClickListener(null)
        }

        if (actionBar != null) {
            actionBar?.setIcon(null)
        }

        currentlyPlayedVoiceMessage?.let { stopMediaPlayer(it) }

        adapter = null
        inConversation = false
    }

    private fun dispose() {
        for (disposable in disposableList) {
            if (!disposable.isDisposed()) {
                disposable.dispose()
            }
        }
    }

    private fun joinRoomWithPassword() {

        if (currentConversation == null ||
            TextUtils.isEmpty(currentConversation?.sessionId) ||
            currentConversation?.sessionId == "0"
        ) {
            var apiVersion = 1
            // FIXME Fix API checking with guests?
            if (conversationUser != null) {
                apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.APIv4, 1))
            }

            ncApi?.joinRoom(
                credentials,
                ApiUtils.getUrlForParticipantsActive(apiVersion, conversationUser?.baseUrl, roomToken),
                roomPassword
            )
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.retry(3)
                ?.subscribe(object : Observer<RoomOverall> {
                    override fun onSubscribe(d: Disposable) {
                        disposableList.add(d)
                    }

                    @Suppress("Detekt.TooGenericExceptionCaught")
                    override fun onNext(roomOverall: RoomOverall) {
                        inConversation = true
                        currentConversation?.sessionId = roomOverall.ocs.data.sessionId

                        ApplicationWideCurrentRoomHolder.getInstance().session =
                            currentConversation?.sessionId

                        setupWebsocket()

                        try {
                            checkLobbyState()
                        } catch (npe: NullPointerException) {
                            // view binding can be null
                            // since this is called asynchrously and UI might have been destroyed in the meantime
                            Log.i(TAG, "UI destroyed - view binding already gone")
                        }

                        if (isFirstMessagesProcessing) {
                            pullChatMessages(0)
                        } else {
                            pullChatMessages(1, 0)
                        }

                        if (magicWebSocketInstance != null) {
                            magicWebSocketInstance?.joinRoomWithRoomTokenAndSession(
                                roomToken,
                                currentConversation?.sessionId
                            )
                        }
                        if (startCallFromNotification != null && startCallFromNotification ?: false) {
                            startCallFromNotification = false
                            startACall(voiceOnly)
                        }
                    }

                    override fun onError(e: Throwable) {
                    }

                    override fun onComplete() {
                    }
                })
        } else {
            inConversation = true
            ApplicationWideCurrentRoomHolder.getInstance().session = currentConversation?.sessionId
            if (magicWebSocketInstance != null) {
                magicWebSocketInstance?.joinRoomWithRoomTokenAndSession(
                    roomToken,
                    currentConversation?.sessionId
                )
            }
            if (isFirstMessagesProcessing) {
                pullChatMessages(0)
            } else {
                pullChatMessages(1)
            }
        }
    }

    private fun leaveRoom() {
        Log.d(TAG, "leaveRoom")
        var apiVersion = 1
        // FIXME Fix API checking with guests?
        if (conversationUser != null) {
            apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.APIv4, 1))
        }

        ncApi?.leaveRoom(
            credentials,
            ApiUtils.getUrlForParticipantsActive(
                apiVersion,
                conversationUser?.baseUrl,
                roomToken
            )
        )
            ?.subscribeOn(Schedulers.io())
            ?.observeOn(AndroidSchedulers.mainThread())
            ?.subscribe(object : Observer<GenericOverall> {
                override fun onSubscribe(d: Disposable) {
                    disposableList.add(d)
                }

                override fun onNext(genericOverall: GenericOverall) {
                    checkingLobbyStatus = false

                    if (lobbyTimerHandler != null) {
                        lobbyTimerHandler?.removeCallbacksAndMessages(null)
                    }

                    if (magicWebSocketInstance != null && currentConversation != null) {
                        magicWebSocketInstance?.joinRoomWithRoomTokenAndSession(
                            "",
                            currentConversation?.sessionId
                        )
                    } else {
                        Log.e(TAG, "magicWebSocketInstance or currentConversation were null! Failed to leave the room!")
                    }

                    if (!isDestroyed && !isBeingDestroyed && !wasDetached) {
                        router.popCurrentController()
                    }
                }

                override fun onError(e: Throwable) {}

                override fun onComplete() {
                    dispose()
                }
            })
    }

    private fun submitMessage() {
        if (binding.messageInputView.inputEditText != null) {
            val editable = binding.messageInputView.inputEditText!!.editableText
            val mentionSpans = editable.getSpans(
                0, editable.length,
                Spans.MentionChipSpan::class.java
            )
            var mentionSpan: Spans.MentionChipSpan
            for (i in mentionSpans.indices) {
                mentionSpan = mentionSpans[i]
                var mentionId = mentionSpan.id
                if (mentionId.contains(" ") || mentionId.startsWith("guest/")) {
                    mentionId = "\"" + mentionId + "\""
                }
                editable.replace(editable.getSpanStart(mentionSpan), editable.getSpanEnd(mentionSpan), "@$mentionId")
            }

            binding.messageInputView.inputEditText?.setText("")
            val replyMessageId: Int? = view?.findViewById<RelativeLayout>(R.id.quotedChatMessageView)?.tag as Int?
            sendMessage(
                editable,
                if (
                    view
                        ?.findViewById<RelativeLayout>(R.id.quotedChatMessageView)
                        ?.visibility == View.VISIBLE
                ) replyMessageId else null
            )
            cancelReply()
        }
    }

    private fun sendMessage(message: CharSequence, replyTo: Int?) {

        if (conversationUser != null) {
            val apiVersion = ApiUtils.getChatApiVersion(conversationUser, intArrayOf(1))

            ncApi!!.sendChatMessage(
                credentials,
                ApiUtils.getUrlForChat(apiVersion, conversationUser.baseUrl, roomToken),
                message,
                conversationUser.displayName,
                replyTo
            )
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(object : Observer<GenericOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    @Suppress("Detekt.TooGenericExceptionCaught")
                    override fun onNext(genericOverall: GenericOverall) {
                        myFirstMessage = message

                        try {
                            if (binding.popupBubbleView.isShown == true) {
                                binding.popupBubbleView.hide()
                            }

                            binding.messagesListView.smoothScrollToPosition(0)
                        } catch (npe: NullPointerException) {
                            // view binding can be null
                            // since this is called asynchrously and UI might have been destroyed in the meantime
                            Log.i(TAG, "UI destroyed - view binding already gone")
                        }
                    }

                    override fun onError(e: Throwable) {
                        if (e is HttpException) {
                            val code = e.code()
                            if (Integer.toString(code).startsWith("2")) {
                                myFirstMessage = message

                                if (binding.popupBubbleView.isShown == true) {
                                    binding.popupBubbleView.hide()
                                }

                                binding.messagesListView.smoothScrollToPosition(0)
                            }
                        }
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
        showMicrophoneButton(true)
    }

    private fun setupWebsocket() {
        if (conversationUser != null) {
            if (WebSocketConnectionHelper.getMagicWebSocketInstanceForUserId(conversationUser.id) != null) {
                magicWebSocketInstance =
                    WebSocketConnectionHelper.getMagicWebSocketInstanceForUserId(conversationUser.id)
            } else {
                Log.d(TAG, "magicWebSocketInstance became null")
                magicWebSocketInstance = null
            }
        }
    }

    fun pullChatMessages(lookIntoFuture: Int, setReadMarker: Int = 1, xChatLastCommonRead: Int? = null) {
        if (!inConversation) {
            return
        }

        if (currentConversation != null && currentConversation!!.shouldShowLobby(conversationUser)) {
            // return
        }

        val fieldMap = HashMap<String, Int>()
        fieldMap["includeLastKnown"] = 0

        if (lookIntoFuture > 0) {
            lookingIntoFuture = true
        } else if (isFirstMessagesProcessing) {
            if (currentConversation != null) {
                globalLastKnownFutureMessageId = currentConversation!!.lastReadMessage
                globalLastKnownPastMessageId = currentConversation!!.lastReadMessage
                fieldMap["includeLastKnown"] = 1
            }
        }

        val timeout = if (lookingIntoFuture) {
            30
        } else {
            0
        }

        fieldMap["timeout"] = timeout

        fieldMap["lookIntoFuture"] = lookIntoFuture
        fieldMap["limit"] = 100
        fieldMap["setReadMarker"] = setReadMarker

        val lastKnown: Int
        if (lookIntoFuture > 0) {
            lastKnown = globalLastKnownFutureMessageId
        } else {
            lastKnown = globalLastKnownPastMessageId
        }

        fieldMap["lastKnownMessageId"] = lastKnown
        xChatLastCommonRead?.let {
            fieldMap["lastCommonReadId"] = it
        }

        if (!wasDetached) {
            var apiVersion = 1
            // FIXME this is a best guess, guests would need to get the capabilities themselves
            if (conversationUser != null) {
                apiVersion = ApiUtils.getChatApiVersion(conversationUser, intArrayOf(1))
            }

            if (lookIntoFuture > 0) {
                val finalTimeout = timeout
                ncApi?.pullChatMessages(
                    credentials,
                    ApiUtils.getUrlForChat(apiVersion, conversationUser?.baseUrl, roomToken), fieldMap
                )
                    ?.subscribeOn(Schedulers.io())
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.takeWhile { observable -> inConversation && !wasDetached }
                    ?.subscribe(object : Observer<Response<*>> {
                        override fun onSubscribe(d: Disposable) {
                            disposableList.add(d)
                        }

                        @Suppress("Detekt.TooGenericExceptionCaught")
                        override fun onNext(response: Response<*>) {
                            try {
                                if (response.code() == 304) {
                                    pullChatMessages(1, setReadMarker, xChatLastCommonRead)
                                } else if (response.code() == 412) {
                                    futurePreconditionFailed = true
                                } else {
                                    processMessages(response, true, finalTimeout)
                                }
                            } catch (npe: NullPointerException) {
                                // view binding can be null
                                // since this is called asynchrously and UI might have been destroyed in the meantime
                                Log.i(TAG, "UI destroyed - view binding already gone")
                            }
                        }

                        override fun onError(e: Throwable) {
                            Log.e(TAG, "failed to pull chat messages", e)
                        }

                        override fun onComplete() {
                            // unused atm
                        }
                    })
            } else {
                ncApi?.pullChatMessages(
                    credentials,
                    ApiUtils.getUrlForChat(apiVersion, conversationUser?.baseUrl, roomToken), fieldMap
                )
                    ?.subscribeOn(Schedulers.io())
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.takeWhile { observable -> inConversation && !wasDetached }
                    ?.subscribe(object : Observer<Response<*>> {
                        override fun onSubscribe(d: Disposable) {
                            disposableList.add(d)
                        }

                        @Suppress("Detekt.TooGenericExceptionCaught")
                        override fun onNext(response: Response<*>) {
                            try {
                                if (response.code() == 412) {
                                    pastPreconditionFailed = true
                                } else {
                                    processMessages(response, false, 0)
                                }
                            } catch (npe: NullPointerException) {
                                // view binding can be null
                                // since this is called asynchrously and UI might have been destroyed in the meantime
                                Log.i(TAG, "UI destroyed - view binding already gone")
                            }
                        }

                        override fun onError(e: Throwable) {
                            // unused atm
                        }

                        override fun onComplete() {
                            // unused atm
                        }
                    })
            }
        }
    }

    private fun processMessages(response: Response<*>, isFromTheFuture: Boolean, timeout: Int) {
        val xChatLastGivenHeader: String? = response.headers().get("X-Chat-Last-Given")
        val xChatLastCommonRead = response.headers().get("X-Chat-Last-Common-Read")?.let {
            Integer.parseInt(it)
        }
        if (response.headers().size > 0 && !TextUtils.isEmpty(xChatLastGivenHeader)) {

            val header = Integer.parseInt(xChatLastGivenHeader!!)
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

        if (response.code() == HTTP_CODE_OK) {

            val chatOverall = response.body() as ChatOverall?
            val chatMessageList = setDeletionFlagsAndRemoveInfomessages(chatOverall?.ocs!!.data)

            if (chatMessageList.isNotEmpty() &&
                ChatMessage.SystemMessageType.CLEARED_CHAT == chatMessageList[0].systemMessageType
            ) {
                adapter?.clear()
                adapter?.notifyDataSetChanged()
            }

            if (isFirstMessagesProcessing) {
                cancelNotificationsForCurrentConversation()

                isFirstMessagesProcessing = false
                binding.progressBar.visibility = View.GONE

                binding.messagesListView.visibility = View.VISIBLE
            }

            var countGroupedMessages = 0
            if (!isFromTheFuture) {

                for (i in chatMessageList.indices) {
                    if (chatMessageList.size > i + 1) {
                        if (TextUtils.isEmpty(chatMessageList[i].systemMessage) &&
                            TextUtils.isEmpty(chatMessageList[i + 1].systemMessage) &&
                            chatMessageList[i + 1].actorId == chatMessageList[i].actorId &&
                            countGroupedMessages < 4 &&
                            DateFormatter.isSameDay(chatMessageList[i].createdAt, chatMessageList[i + 1].createdAt)
                        ) {
                            chatMessageList[i].isGrouped = true
                            countGroupedMessages++
                        } else {
                            countGroupedMessages = 0
                        }
                    }

                    val chatMessage = chatMessageList[i]
                    chatMessage.isOneToOneConversation =
                        currentConversation?.type == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
                    chatMessage.activeUser = conversationUser
                }

                if (adapter != null) {
                    adapter?.addToEnd(chatMessageList, false)
                }
            } else {

                var chatMessage: ChatMessage

                val shouldAddNewMessagesNotice = timeout == 0 && adapter?.itemCount ?: 0 > 0 && chatMessageList.size > 0

                if (shouldAddNewMessagesNotice) {
                    val unreadChatMessage = ChatMessage()
                    unreadChatMessage.jsonMessageId = -1
                    unreadChatMessage.actorId = "-1"
                    unreadChatMessage.timestamp = chatMessageList[0].timestamp
                    unreadChatMessage.message = context?.getString(R.string.nc_new_messages)
                    adapter?.addToStart(unreadChatMessage, false)
                }

                val isThereANewNotice =
                    shouldAddNewMessagesNotice || adapter?.getMessagePositionByIdInReverse("-1") != -1

                for (i in chatMessageList.indices) {
                    chatMessage = chatMessageList[i]

                    chatMessage.activeUser = conversationUser

                    val shouldScroll =
                        !isThereANewNotice &&
                            !shouldAddNewMessagesNotice &&
                            layoutManager?.findFirstVisibleItemPosition() == 0 ||
                            adapter != null &&
                            adapter?.itemCount == 0

                    if (!shouldAddNewMessagesNotice && !shouldScroll) {
                        if (!binding.popupBubbleView.isShown) {
                            newMessagesCount = 1
                            binding.popupBubbleView.show()
                        } else if (binding.popupBubbleView.isShown == true) {
                            newMessagesCount++
                        }
                    } else {
                        newMessagesCount = 0
                    }

                    if (adapter != null) {
                        chatMessage.isGrouped = (
                            adapter!!.isPreviousSameAuthor(
                                chatMessage.actorId,
                                -1
                            ) && adapter!!.getSameAuthorLastMessagesCount(chatMessage.actorId) % 5 > 0
                            )
                        chatMessage.isOneToOneConversation =
                            (currentConversation?.type == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL)
                        adapter?.addToStart(chatMessage, shouldScroll)
                    }
                }

                if (shouldAddNewMessagesNotice && adapter != null) {
                    layoutManager?.scrollToPositionWithOffset(
                        adapter!!.getMessagePositionByIdInReverse("-1"),
                        binding.messagesListView.height / 2
                    )
                }
            }

            // update read status of all messages
            for (message in adapter!!.items) {
                xChatLastCommonRead?.let {
                    if (message.item is ChatMessage) {
                        val chatMessage = message.item as ChatMessage

                        if (chatMessage.jsonMessageId <= it) {
                            chatMessage.readStatus = ReadStatus.READ
                        } else {
                            chatMessage.readStatus = ReadStatus.SENT
                        }
                    }
                }
            }
            adapter?.notifyDataSetChanged()

            if (inConversation) {
                pullChatMessages(1, 1, xChatLastCommonRead)
            }
        } else if (response.code() == 304 && !isFromTheFuture) {
            if (isFirstMessagesProcessing) {
                cancelNotificationsForCurrentConversation()

                isFirstMessagesProcessing = false
                binding.progressBar.visibility = View.GONE
            }

            historyRead = true

            if (!lookingIntoFuture && inConversation) {
                pullChatMessages(1)
            }
        }
    }

    override fun onLoadMore(page: Int, totalItemsCount: Int) {
        if (!historyRead && inConversation) {
            pullChatMessages(0)
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

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_conversation, menu)
        if (conversationUser?.userId == "?") {
            menu.removeItem(R.id.conversation_info)
            conversationVoiceCallMenuItem = menu.findItem(R.id.conversation_voice_call)
            conversationVideoMenuItem = menu.findItem(R.id.conversation_video_call)
        } else {
            conversationInfoMenuItem = menu.findItem(R.id.conversation_info)
            conversationVoiceCallMenuItem = menu.findItem(R.id.conversation_voice_call)
            conversationVideoMenuItem = menu.findItem(R.id.conversation_video_call)

            loadAvatarForStatusBar()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        conversationUser?.let {
            if (CapabilitiesUtil.hasSpreedFeatureCapability(it, "read-only-rooms")) {
                checkReadOnlyState()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                (activity as MainActivity).resetConversationsList()
                return true
            }
            R.id.conversation_video_call -> {
                if (conversationVideoMenuItem?.icon?.alpha == FULLY_OPAQUE_INT) {
                    startACall(false)
                    return true
                }
                return false
            }
            R.id.conversation_voice_call -> {
                if (conversationVoiceCallMenuItem?.icon?.alpha == FULLY_OPAQUE_INT) {
                    startACall(true)
                    return true
                }
                return false
            }
            R.id.conversation_info -> {
                showConversationInfoScreen()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun setDeletionFlagsAndRemoveInfomessages(chatMessageList: List<ChatMessage>): List<ChatMessage> {
        val chatMessageMap = chatMessageList.map { it.id to it }.toMap().toMutableMap()
        val chatMessageIterator = chatMessageMap.iterator()
        while (chatMessageIterator.hasNext()) {
            val currentMessage = chatMessageIterator.next()
            if (isInfoMessageAboutDeletion(currentMessage)) {
                if (!chatMessageMap.containsKey(currentMessage.value.parentMessage.id)) {
                    // if chatMessageMap doesnt't contain message to delete (this happens when lookingIntoFuture),
                    // the message to delete has to be modified directly inside the adapter
                    setMessageAsDeleted(currentMessage.value.parentMessage)
                } else {
                    chatMessageMap[currentMessage.value.parentMessage.id]!!.isDeleted = true
                }
                chatMessageIterator.remove()
            }
        }
        return chatMessageMap.values.toList()
    }

    private fun isInfoMessageAboutDeletion(currentMessage: MutableMap.MutableEntry<String, ChatMessage>): Boolean {
        return currentMessage.value.parentMessage != null && currentMessage.value.systemMessageType == ChatMessage
            .SystemMessageType.MESSAGE_DELETED
    }

    private fun startACall(isVoiceOnlyCall: Boolean) {
        ApplicationWideCurrentRoomHolder.getInstance().isDialing = true
        val callIntent = getIntentForCall(isVoiceOnlyCall)
        if (callIntent != null) {
            startActivity(callIntent)
        }
    }

    private fun getIntentForCall(isVoiceOnlyCall: Boolean): Intent? {
        currentConversation?.let {
            val bundle = Bundle()
            bundle.putString(KEY_ROOM_TOKEN, roomToken)
            bundle.putString(KEY_ROOM_ID, roomId)
            bundle.putParcelable(KEY_USER_ENTITY, conversationUser)
            bundle.putString(BundleKeys.KEY_CONVERSATION_PASSWORD, roomPassword)
            bundle.putString(BundleKeys.KEY_MODIFIED_BASE_URL, conversationUser?.baseUrl)
            bundle.putString(BundleKeys.KEY_CONVERSATION_NAME, it.displayName)

            if (isVoiceOnlyCall) {
                bundle.putBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, true)
            }

            return if (activity != null) {
                val callIntent = Intent(activity, CallActivity::class.java)
                callIntent.putExtras(bundle)
                callIntent
            } else {
                null
            }
        } ?: run {
            return null
        }
    }

    override fun onMessageViewLongClick(view: View?, message: IMessage?) {
        PopupMenu(
            ContextThemeWrapper(view?.context, R.style.appActionBarPopupMenu),
            view,
            if (
                message?.user?.id == currentConversation?.actorType + "/" + currentConversation?.actorId
            ) Gravity.END else Gravity.START
        ).apply {
            setOnMenuItemClickListener { item ->
                when (item?.itemId) {

                    R.id.action_copy_message -> {
                        val clipboardManager =
                            activity?.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clipData = ClipData.newPlainText(
                            resources?.getString(R.string.nc_app_product_name),
                            message?.text
                        )
                        clipboardManager.setPrimaryClip(clipData)
                        true
                    }
                    R.id.action_forward_message -> {
                        val bundle = Bundle()
                        bundle.putBoolean(BundleKeys.KEY_FORWARD_MSG_FLAG, true)
                        bundle.putString(BundleKeys.KEY_FORWARD_MSG_TEXT, message?.text)
                        bundle.putString(BundleKeys.KEY_FORWARD_HIDE_SOURCE_ROOM, roomId)
                        router.pushController(
                            RouterTransaction.with(ConversationsListController(bundle))
                                .pushChangeHandler(HorizontalChangeHandler())
                                .popChangeHandler(HorizontalChangeHandler())
                        )
                        true
                    }
                    R.id.action_reply_to_message -> {
                        val chatMessage = message as ChatMessage?
                        replyToMessage(chatMessage, message?.jsonMessageId)
                        true
                    }
                    R.id.action_reply_privately -> {
                        val apiVersion =
                            ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.APIv4, 1))
                        val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
                            apiVersion,
                            conversationUser?.baseUrl,
                            "1",
                            null,
                            message?.user?.id?.substring(6),
                            null
                        )
                        ncApi!!.createRoom(
                            credentials,
                            retrofitBucket.getUrl(), retrofitBucket.getQueryMap()
                        )
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(object : Observer<RoomOverall> {
                                override fun onSubscribe(d: Disposable) {
                                    // unused atm
                                }

                                override fun onNext(roomOverall: RoomOverall) {
                                    val bundle = Bundle()
                                    bundle.putParcelable(KEY_USER_ENTITY, conversationUser)
                                    bundle.putString(KEY_ROOM_TOKEN, roomOverall.getOcs().getData().getToken())
                                    bundle.putString(KEY_ROOM_ID, roomOverall.getOcs().getData().getRoomId())

                                    // FIXME once APIv2+ is used only, the createRoom already returns all the data
                                    ncApi!!.getRoom(
                                        credentials,
                                        ApiUtils.getUrlForRoom(
                                            apiVersion, conversationUser?.baseUrl,
                                            roomOverall.getOcs().getData().getToken()
                                        )
                                    )
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(object : Observer<RoomOverall> {
                                            override fun onSubscribe(d: Disposable) {
                                                // unused atm
                                            }

                                            override fun onNext(roomOverall: RoomOverall) {
                                                bundle.putParcelable(
                                                    KEY_ACTIVE_CONVERSATION,
                                                    Parcels.wrap(roomOverall.getOcs().getData())
                                                )
                                                remapChatController(
                                                    router, conversationUser!!.id,
                                                    roomOverall.getOcs().getData().getToken(), bundle, true
                                                )
                                            }

                                            override fun onError(e: Throwable) {
                                                Log.e(TAG, e.message, e)
                                            }

                                            override fun onComplete() {
                                                // unused atm
                                            }
                                        })
                                }

                                override fun onError(e: Throwable) {
                                    Log.e(TAG, e.message, e)
                                }

                                override fun onComplete() {
                                    // unused atm
                                }
                            })
                        true
                    }
                    R.id.action_delete_message -> {
                        var apiVersion = 1
                        // FIXME Fix API checking with guests?
                        if (conversationUser != null) {
                            apiVersion = ApiUtils.getChatApiVersion(conversationUser, intArrayOf(1))
                        }

                        ncApi?.deleteChatMessage(
                            credentials,
                            ApiUtils.getUrlForChatMessage(
                                apiVersion,
                                conversationUser?.baseUrl,
                                roomToken,
                                message?.id
                            )
                        )?.subscribeOn(Schedulers.io())
                            ?.observeOn(AndroidSchedulers.mainThread())
                            ?.subscribe(object : Observer<ChatOverallSingleMessage> {
                                override fun onSubscribe(d: Disposable) {
                                    // unused atm
                                }

                                override fun onNext(t: ChatOverallSingleMessage) {
                                    if (t.ocs.meta.statusCode == HttpURLConnection.HTTP_ACCEPTED) {
                                        Toast.makeText(
                                            context, R.string.nc_delete_message_leaked_to_matterbridge,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }

                                override fun onError(e: Throwable) {
                                    Log.e(
                                        TAG,
                                        "Something went wrong when trying to delete message with id " +
                                            message?.id,
                                        e
                                    )
                                    Toast.makeText(context, R.string.nc_common_error_sorry, Toast.LENGTH_LONG).show()
                                }

                                override fun onComplete() {
                                    // unused atm
                                }
                            })
                        true
                    }
                    else -> false
                }
            }
            inflate(R.menu.chat_message_menu)
            menu.findItem(R.id.action_copy_message).isVisible = !(message as ChatMessage).isDeleted
            menu.findItem(R.id.action_reply_to_message).isVisible = message.replyable
            menu.findItem(R.id.action_reply_privately).isVisible = message.replyable &&
                conversationUser?.userId?.isNotEmpty() == true && conversationUser.userId != "?" &&
                message.user.id.startsWith("users/") &&
                message.user.id.substring(6) != currentConversation?.actorId &&
                currentConversation?.type != Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
            menu.findItem(R.id.action_delete_message).isVisible = isShowMessageDeletionButton(message)
            menu.findItem(R.id.action_forward_message).isVisible =
                ChatMessage.MessageType.REGULAR_TEXT_MESSAGE == message.getMessageType()
            if (menu.hasVisibleItems()) {
                if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    setForceShowIcon(true)
                }
                show()
            }
        }
    }

    private fun replyToMessage(chatMessage: ChatMessage?, jsonMessageId: Int?) {
        chatMessage?.let {
            binding.messageInputView.findViewById<ImageButton>(R.id.attachmentButton)?.visibility =
                View.GONE
            binding.messageInputView.findViewById<ImageButton>(R.id.cancelReplyButton)?.visibility =
                View.VISIBLE

            val quotedMessage = binding
                .messageInputView
                .findViewById<EmojiTextView>(R.id.quotedMessage)

            quotedMessage?.maxLines = 2
            quotedMessage?.ellipsize = TextUtils.TruncateAt.END
            quotedMessage?.text = it.text
            binding.messageInputView.findViewById<EmojiTextView>(R.id.quotedMessageAuthor)?.text =
                it.actorDisplayName ?: context!!.getText(R.string.nc_nick_guest)

            conversationUser?.let { currentUser ->
                val quotedMessageImage = binding
                    .messageInputView
                    .findViewById<ImageView>(R.id.quotedMessageImage)
                chatMessage.imageUrl?.let { previewImageUrl ->
                    quotedMessageImage?.visibility = View.VISIBLE

                    val px = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        96f,
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
                    binding
                        .messageInputView
                        .findViewById<ImageView>(R.id.quotedMessageImage)
                        ?.visibility = View.GONE
                }
            }

            val quotedChatMessageView = binding
                .messageInputView
                .findViewById<RelativeLayout>(R.id.quotedChatMessageView)
            quotedChatMessageView?.tag = jsonMessageId
            quotedChatMessageView?.visibility = View.VISIBLE
        }
    }

    private fun showMicrophoneButton(show: Boolean) {
        if (show && CapabilitiesUtil.hasSpreedFeatureCapability(conversationUser, "voice-message-sharing")) {
            binding.messageInputView.messageSendButton.visibility = View.GONE
            binding.messageInputView.recordAudioButton.visibility = View.VISIBLE
        } else {
            binding.messageInputView.messageSendButton.visibility = View.VISIBLE
            binding.messageInputView.recordAudioButton.visibility = View.GONE
        }
    }

    private fun setMessageAsDeleted(message: IMessage?) {
        val messageTemp = message as ChatMessage
        messageTemp.isDeleted = true

        messageTemp.isOneToOneConversation =
            currentConversation?.type == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        messageTemp.activeUser = conversationUser

        adapter?.update(messageTemp)
    }

    private fun isShowMessageDeletionButton(message: ChatMessage): Boolean {
        if (conversationUser == null) return false

        if (message.systemMessageType != ChatMessage.SystemMessageType.DUMMY) return false

        if (message.isDeleted) return false

        if (message.hasFileAttachment()) return false

        if (OBJECT_MESSAGE.equals(message.message)) return false

        val isOlderThanSixHours = message
            .createdAt
            ?.before(Date(System.currentTimeMillis() - AGE_THREHOLD_FOR_DELETE_MESSAGE)) == true
        if (isOlderThanSixHours) return false

        val isUserAllowedByPrivileges = if (message.actorId == conversationUser.userId) {
            true
        } else {
            currentConversation!!.canModerate(conversationUser)
        }
        if (!isUserAllowedByPrivileges) return false

        if (!CapabilitiesUtil.hasSpreedFeatureCapability(conversationUser, "delete-messages")) return false

        return true
    }

    override fun hasContentFor(message: ChatMessage, type: Byte): Boolean {
        return when (type) {
            CONTENT_TYPE_LOCATION -> message.hasGeoLocation()
            CONTENT_TYPE_VOICE_MESSAGE -> message.isVoiceMessage()
            CONTENT_TYPE_SYSTEM_MESSAGE -> !TextUtils.isEmpty(message.systemMessage)
            CONTENT_TYPE_UNREAD_NOTICE_MESSAGE -> message.id == "-1"
            else -> false
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
        if (currentConversation?.type != Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL ||
            currentConversation?.name != userMentionClickEvent.userId
        ) {

            var apiVersion = 1
            // FIXME Fix API checking with guests?
            if (conversationUser != null) {
                apiVersion = ApiUtils.getConversationApiVersion(conversationUser, intArrayOf(ApiUtils.APIv4, 1))
            }

            val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
                apiVersion,
                conversationUser?.baseUrl,
                "1",
                null,
                userMentionClickEvent.userId,
                null
            )

            ncApi?.createRoom(
                credentials,
                retrofitBucket.url, retrofitBucket.queryMap
            )
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.subscribe(object : Observer<RoomOverall> {
                    override fun onSubscribe(d: Disposable) {
                        // unused atm
                    }

                    override fun onNext(roomOverall: RoomOverall) {
                        val conversationIntent = Intent(activity, CallActivity::class.java)
                        val bundle = Bundle()
                        bundle.putParcelable(KEY_USER_ENTITY, conversationUser)
                        bundle.putString(KEY_ROOM_TOKEN, roomOverall.ocs.data.token)
                        bundle.putString(KEY_ROOM_ID, roomOverall.ocs.data.roomId)

                        if (conversationUser != null) {
                            bundle.putParcelable(
                                KEY_ACTIVE_CONVERSATION,
                                Parcels.wrap(roomOverall.ocs.data)
                            )
                            conversationIntent.putExtras(bundle)

                            ConductorRemapping.remapChatController(
                                router, conversationUser.id,
                                roomOverall.ocs.data.token, bundle, false
                            )
                        } else {
                            conversationIntent.putExtras(bundle)
                            startActivity(conversationIntent)
                            Handler().postDelayed(
                                {
                                    if (!isDestroyed && !isBeingDestroyed) {
                                        router.popCurrentController()
                                    }
                                },
                                POP_CURRENT_CONTROLLER_DELAY
                            )
                        }
                    }

                    override fun onError(e: Throwable) {
                        // unused atm
                    }

                    override fun onComplete() {
                        // unused atm
                    }
                })
        }
    }

    fun sendPictureFromCamIntent() {
        if (!isCameraPermissionGranted()) {
            requestCameraPermissions()
        } else {
            startActivityForResult(TakePhotoActivity.createIntent(context!!), REQUEST_CODE_PICK_CAMERA)
        }
    }

    companion object {
        private const val TAG = "ChatController"
        private const val CONTENT_TYPE_SYSTEM_MESSAGE: Byte = 1
        private const val CONTENT_TYPE_UNREAD_NOTICE_MESSAGE: Byte = 2
        private const val CONTENT_TYPE_LOCATION: Byte = 3
        private const val CONTENT_TYPE_VOICE_MESSAGE: Byte = 4
        private const val NEW_MESSAGES_POPUP_BUBBLE_DELAY: Long = 200
        private const val POP_CURRENT_CONTROLLER_DELAY: Long = 100
        private const val LOBBY_TIMER_DELAY: Long = 5000
        private const val HTTP_CODE_OK: Int = 200
        private const val MESSAGE_MAX_LENGTH: Int = 1000
        private const val AGE_THREHOLD_FOR_DELETE_MESSAGE: Int = 21600000 // (6 hours in millis = 6 * 3600 * 1000)
        private const val REQUEST_CODE_CHOOSE_FILE: Int = 555
        private const val REQUEST_CODE_SELECT_CONTACT: Int = 666
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 222
        private const val REQUEST_READ_CONTACT_PERMISSION = 234
        private const val REQUEST_CAMERA_PERMISSION = 223
        private const val REQUEST_CODE_PICK_CAMERA: Int = 333
        private const val OBJECT_MESSAGE: String = "{object}"
        private const val MINIMUM_VOICE_RECORD_DURATION: Int = 1000
        private const val VOICE_RECORD_CANCEL_SLIDER_X: Int = -50
        private const val VOICE_MESSAGE_META_DATA = "{\"messageType\":\"voice-message\"}"
        private const val VOICE_MESSAGE_FILE_SUFFIX = ".mp3"
        private const val SHORT_VIBRATE: Long = 20
        private const val FULLY_OPAQUE_INT: Int = 255
        private const val SEMI_TRANSPARENT_INT: Int = 99
        private const val VOICE_MESSAGE_SEEKBAR_BASE: Int = 1000
        private const val SECOND: Long = 1000
    }
}
