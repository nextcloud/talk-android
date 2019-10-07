/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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


import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.text.Editable
import android.text.InputFilter
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.emoji.text.EmojiCompat
import androidx.emoji.widget.EmojiEditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import autodagger.AutoInjector
import butterknife.BindView
import butterknife.OnClick
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.facebook.common.executors.UiThreadImmediateExecutorService
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MagicCallActivity
import com.nextcloud.talk.adapters.messages.*
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.callbacks.MentionAutocompleteCallback
import com.nextcloud.talk.components.filebrowser.controllers.BrowserController
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.events.UserMentionClickEvent
import com.nextcloud.talk.events.WebSocketCommunicationEvent
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.conversations.RoomsOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.mention.Mention
import com.nextcloud.talk.presenters.MentionAutocompletePresenter
import com.nextcloud.talk.utils.*
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder
import com.nextcloud.talk.utils.text.Spans
import com.nextcloud.talk.webrtc.MagicWebSocketInstance
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper
import com.otaliastudios.autocomplete.Autocomplete
import com.stfalcon.chatkit.commons.ImageLoader
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.messages.MessageHolders
import com.stfalcon.chatkit.messages.MessageInput
import com.stfalcon.chatkit.messages.MessagesList
import com.stfalcon.chatkit.messages.MessagesListAdapter
import com.stfalcon.chatkit.utils.DateFormatter
import com.uber.autodispose.AutoDispose
import com.vanniktech.emoji.EmojiPopup
import com.webianks.library.PopupBubble
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.parceler.Parcels
import retrofit2.HttpException
import retrofit2.Response
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ChatController(args: Bundle) : BaseController(args), MessagesListAdapter
.OnLoadMoreListener, MessagesListAdapter.Formatter<Date>, MessagesListAdapter
.OnMessageLongClickListener<IMessage>, MessageHolders.ContentChecker<IMessage> {

    @Inject
    @JvmField
    var ncApi: NcApi? = null
    @Inject
    @JvmField
    var userUtils: UserUtils? = null
    @Inject
    @JvmField
    var appPreferences: AppPreferences? = null
    @Inject
    @JvmField
    var context: Context? = null
    @Inject
    @JvmField
    var eventBus: EventBus? = null
    @BindView(R.id.messagesListView)
    @JvmField
    var messagesListView: MessagesList? = null
    @BindView(R.id.messageInputView)
    @JvmField
    var messageInputView: MessageInput? = null
    @BindView(R.id.messageInput)
    @JvmField
    var messageInput: EmojiEditText? = null
    @BindView(R.id.popupBubbleView)
    @JvmField
    var popupBubble: PopupBubble? = null
    @BindView(R.id.progressBar)
    @JvmField
    var loadingProgressBar: ProgressBar? = null
    @BindView(R.id.smileyButton)
    @JvmField
    var smileyButton: ImageButton? = null
    @BindView(R.id.lobby_view)
    @JvmField
    var lobbyView: RelativeLayout? = null
    @BindView(R.id.lobby_text_view)
    @JvmField
    var conversationLobbyText: TextView? = null
    var roomToken: String? = null
    val conversationUser: UserEntity?
    val roomPassword: String
    var credentials: String? = null
    var currentConversation: Conversation? = null
    var inConversation = false
    var historyRead = false
    var globalLastKnownFutureMessageId = -1
    var globalLastKnownPastMessageId = -1
    var adapter: MessagesListAdapter<ChatMessage>? = null
    var mentionAutocomplete: Autocomplete<*>? = null
    var layoutManager: LinearLayoutManager? = null
    var lookingIntoFuture = false
    var newMessagesCount = 0
    var startCallFromNotification: Boolean? = null
    val roomId: String
    val voiceOnly: Boolean
    var isFirstMessagesProcessing = true
    var isLeavingForConversation: Boolean = false
    var isLinkPreviewAllowed: Boolean = false
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

    init {
        setHasOptionsMenu(true)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        this.conversationUser = args.getParcelable(BundleKeys.KEY_USER_ENTITY)
        this.roomId = args.getString(BundleKeys.KEY_ROOM_ID, "")
        this.roomToken = args.getString(BundleKeys.KEY_ROOM_TOKEN, "")

        if (args.containsKey(BundleKeys.KEY_ACTIVE_CONVERSATION)) {
            this.currentConversation = Parcels.unwrap<Conversation>(args.getParcelable<Parcelable>(BundleKeys.KEY_ACTIVE_CONVERSATION))
        }

        this.roomPassword = args.getString(BundleKeys.KEY_CONVERSATION_PASSWORD, "")

        if (conversationUser?.userId == "?") {
            credentials = null
        } else {
            credentials = ApiUtils.getCredentials(conversationUser.username, conversationUser.token)
        }

        if (args.containsKey(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
            this.startCallFromNotification = args.getBoolean(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)
        }

        this.voiceOnly = args.getBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, false)
    }

    private fun getRoomInfo() {
        val shouldRepeat = conversationUser?.hasSpreedFeatureCapability("webinary-lobby") ?: false
        if (shouldRepeat) {
            checkingLobbyStatus = true
        }


        if (conversationUser != null) {
            ncApi?.getRoom(credentials, ApiUtils.getRoom(conversationUser.baseUrl, roomToken))?.subscribeOn(Schedulers.io())
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.`as`(AutoDispose.autoDisposable(scopeProvider))
                    ?.subscribe(object : Observer<RoomOverall> {
                        override fun onSubscribe(d: Disposable) {
                        }

                        override fun onNext(roomOverall: RoomOverall) {
                            currentConversation = roomOverall.ocs.data
                            loadAvatarForStatusBar()

                            setTitle()
                            setupMentionAutocomplete()
                            checkReadOnlyState()
                            checkLobbyState()

                            if (!inConversation) {
                                joinRoomWithPassword()
                            }

                        }

                        override fun onError(e: Throwable) {

                        }

                        override fun onComplete() {
                            if (shouldRepeat) {
                                if (lobbyTimerHandler == null) {
                                    lobbyTimerHandler = Handler()
                                }

                                lobbyTimerHandler?.postDelayed({ getRoomInfo() }, 5000)
                            }
                        }
                    })
        }
    }

    private fun handleFromNotification() {
        ncApi?.getRooms(credentials, ApiUtils.getUrlForGetRooms(conversationUser?.baseUrl))
                ?.subscribeOn(Schedulers.io())?.observeOn(AndroidSchedulers.mainThread())
                ?.`as`(AutoDispose.autoDisposable(scopeProvider))
                ?.subscribe(object : Observer<RoomsOverall> {
                    override fun onSubscribe(d: Disposable) {
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

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.controller_chat, container, false)
    }

    private fun loadAvatarForStatusBar() {
        if (currentConversation != null && currentConversation?.type != null &&
                currentConversation?.type == Conversation.ConversationType
                        .ROOM_TYPE_ONE_TO_ONE_CALL && activity != null && conversationVoiceCallMenuItem != null) {
            val avatarSize = DisplayUtils.convertDpToPixel(conversationVoiceCallMenuItem?.icon!!
                    .intrinsicWidth.toFloat(), activity).toInt()

            val imageRequest = DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithNameAndPixels(conversationUser?.baseUrl,
                    currentConversation?.name, avatarSize / 2), null)

            val imagePipeline = Fresco.getImagePipeline()
            val dataSource = imagePipeline.fetchDecodedImage(imageRequest, null)

            dataSource.subscribe(object : BaseBitmapDataSubscriber() {
                override fun onNewResultImpl(bitmap: Bitmap?) {
                    if (actionBar != null && bitmap != null && resources != null) {
                        val roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(resources!!, bitmap)
                        roundedBitmapDrawable.isCircular = true
                        roundedBitmapDrawable.setAntiAlias(true)
                        actionBar?.setIcon(roundedBitmapDrawable)
                    }
                }

                override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>) {}
            }, UiThreadImmediateExecutorService.getInstance())
        }
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)

        actionBar?.show()
        var adapterWasNull = false

        if (adapter == null) {
            loadingProgressBar?.visibility = View.VISIBLE

            adapterWasNull = true

            val messageHolders = MessageHolders()
            messageHolders.setIncomingTextConfig(MagicIncomingTextMessageViewHolder::class.java, R.layout.item_custom_incoming_text_message)
            messageHolders.setOutcomingTextConfig(MagicOutcomingTextMessageViewHolder::class.java, R.layout.item_custom_outcoming_text_message)

            messageHolders.setIncomingImageConfig(MagicPreviewMessageViewHolder::class.java, R.layout.item_custom_incoming_preview_message)
            messageHolders.setOutcomingImageConfig(MagicPreviewMessageViewHolder::class.java, R.layout.item_custom_outcoming_preview_message)

            messageHolders.registerContentType(CONTENT_TYPE_SYSTEM_MESSAGE, MagicSystemMessageViewHolder::class.java,
                    R.layout.item_system_message, MagicSystemMessageViewHolder::class.java, R.layout.item_system_message,
                    this)

            messageHolders.registerContentType(CONTENT_TYPE_UNREAD_NOTICE_MESSAGE,
                    MagicUnreadNoticeMessageViewHolder::class.java, R.layout.item_date_header,
                    MagicUnreadNoticeMessageViewHolder::class.java, R.layout.item_date_header, this)

            adapter = MessagesListAdapter(conversationUser?.userId, messageHolders, ImageLoader { imageView, url, payload ->
                val draweeController = Fresco.newDraweeControllerBuilder()
                        .setImageRequest(DisplayUtils.getImageRequestForUrl(url, conversationUser))
                        .setControllerListener(DisplayUtils.getImageControllerListener(imageView))
                        .setOldController(imageView.controller)
                        .setAutoPlayAnimations(true)
                        .build()
                imageView.controller = draweeController
            })
        } else {
            messagesListView?.visibility = View.VISIBLE
        }

        messagesListView?.setAdapter(adapter)
        adapter?.setLoadMoreListener(this)
        adapter?.setDateHeadersFormatter { format(it) }
        adapter?.setOnMessageLongClickListener { onMessageLongClick(it) }

        layoutManager = messagesListView?.layoutManager as LinearLayoutManager?

        popupBubble?.setRecyclerView(messagesListView)

        popupBubble?.setPopupBubbleListener { context ->
            if (newMessagesCount != 0) {
                val scrollPosition: Int
                if (newMessagesCount - 1 < 0) {
                    scrollPosition = 0
                } else {
                    scrollPosition = newMessagesCount - 1
                }
                Handler().postDelayed({ messagesListView?.smoothScrollToPosition(scrollPosition) }, 200)
            }
        }

        messagesListView?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)

                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    if (newMessagesCount != 0 && layoutManager != null) {
                        if (layoutManager!!.findFirstCompletelyVisibleItemPosition() <
                                newMessagesCount) {
                            newMessagesCount = 0

                            if (popupBubble != null && popupBubble!!.isShown) {
                                popupBubble?.hide()
                            }
                        }
                    }
                }
            }
        })


        val filters = arrayOfNulls<InputFilter>(1)
        val lengthFilter = conversationUser?.messageMaxLength ?: 1000


        filters[0] = InputFilter.LengthFilter(lengthFilter)
        messageInput?.filters = filters

        messageInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (s.length >= lengthFilter) {
                    messageInput?.error = String.format(Objects.requireNonNull<Resources>
                    (resources).getString(R.string.nc_limit_hit), Integer.toString(lengthFilter))
                } else {
                    messageInput?.error = null
                }

                val editable = messageInput?.editableText
                if (editable != null && messageInput != null) {
                    val mentionSpans = editable.getSpans(0, messageInput!!.length(),
                            Spans.MentionChipSpan::class.java)
                    var mentionSpan: Spans.MentionChipSpan
                    for (i in mentionSpans.indices) {
                        mentionSpan = mentionSpans[i]
                        if (start >= editable.getSpanStart(mentionSpan) && start < editable.getSpanEnd(mentionSpan)) {
                            if (editable.subSequence(editable.getSpanStart(mentionSpan),
                                            editable.getSpanEnd(mentionSpan)).toString().trim { it <= ' ' } != mentionSpan.label) {
                                editable.removeSpan(mentionSpan)
                            }
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable) {

            }
        })

        messageInputView?.setAttachmentsListener {
            showBrowserScreen(BrowserController
                    .BrowserType.DAV_BROWSER)
        }

        messageInputView?.button?.setOnClickListener { v -> submitMessage() }

        messageInputView?.button?.contentDescription = resources?.getString(R.string
                .nc_description_send_message_button)

        if (currentConversation != null && currentConversation?.roomId != null) {
            loadAvatarForStatusBar()
            checkLobbyState()
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
    }


    private fun checkReadOnlyState() {
        if (currentConversation != null) {
            if (currentConversation?.shouldShowLobby(conversationUser)?: false || currentConversation?.conversationReadOnlyState != null && currentConversation?.conversationReadOnlyState == Conversation.ConversationReadOnlyState.CONVERSATION_READ_ONLY) {

                conversationVoiceCallMenuItem?.icon?.alpha = 99
                conversationVideoMenuItem?.icon?.alpha = 99
                messageInputView?.visibility = View.GONE

            } else {
                if (conversationVoiceCallMenuItem != null) {
                    conversationVoiceCallMenuItem?.icon?.alpha = 255
                }

                if (conversationVideoMenuItem != null) {
                    conversationVideoMenuItem?.icon?.alpha = 255
                }

                if (currentConversation != null && currentConversation!!.shouldShowLobby
                        (conversationUser)) {
                    messageInputView?.visibility = View.GONE
                } else {
                    messageInputView?.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun checkLobbyState() {
        if (currentConversation != null && currentConversation?.isLobbyViewApplicable(conversationUser) ?: false) {

            if (!checkingLobbyStatus) {
                getRoomInfo()
            }

            if (currentConversation?.shouldShowLobby(conversationUser) ?: false) {
                lobbyView?.visibility = View.VISIBLE
                messagesListView?.visibility = View.GONE
                messageInputView?.visibility = View.GONE
                loadingProgressBar?.visibility = View.GONE

                if (currentConversation?.lobbyTimer != null && currentConversation?.lobbyTimer !=
                        0L) {
                    conversationLobbyText?.text = String.format(resources!!.getString(R.string.nc_lobby_waiting_with_date), DateUtils.getLocalDateStringFromTimestampForLobby(currentConversation?.lobbyTimer
                            ?: 0))
                } else {
                    conversationLobbyText?.setText(R.string.nc_lobby_waiting)
                }
            } else {
                lobbyView?.visibility = View.GONE
                messagesListView?.visibility = View.VISIBLE
                messageInput?.visibility = View.VISIBLE
            }
        } else {
            lobbyView?.visibility = View.GONE
            messagesListView?.visibility = View.VISIBLE
            messageInput?.visibility = View.VISIBLE
        }
    }

    private fun showBrowserScreen(browserType: BrowserController.BrowserType) {
        val bundle = Bundle()
        bundle.putParcelable(BundleKeys.KEY_BROWSER_TYPE, Parcels.wrap<BrowserController.BrowserType>(browserType))
        bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, Parcels.wrap<UserEntity>(conversationUser))
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken)
        router.pushController(RouterTransaction.with(BrowserController(bundle))
                .pushChangeHandler(VerticalChangeHandler())
                .popChangeHandler(VerticalChangeHandler()))
    }

    private fun showConversationInfoScreen() {
        val bundle = Bundle()
        bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, conversationUser)
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken)
        router.pushController(RouterTransaction.with(ConversationInfoController(bundle))
                .pushChangeHandler(HorizontalChangeHandler())
                .popChangeHandler(HorizontalChangeHandler()))
    }

    private fun setupMentionAutocomplete() {
        val elevation = 6f
        val backgroundDrawable = ColorDrawable(resources!!.getColor(R.color.bg_default))
        val presenter = MentionAutocompletePresenter(applicationContext, roomToken)
        val callback = MentionAutocompleteCallback(activity,
                conversationUser, messageInput)

        if (mentionAutocomplete == null && messageInput != null) {
            mentionAutocomplete = Autocomplete.on<Mention>(messageInput)
                    .with(elevation)
                    .with(backgroundDrawable)
                    .with(MagicCharPolicy('@'))
                    .with(presenter)
                    .with(callback)
                    .build()
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        eventBus?.register(this)

        if (conversationUser?.userId != "?" && conversationUser?.hasSpreedFeatureCapability("mention-flag") ?: false && activity != null) {
            activity?.findViewById<View>(R.id.toolbar)?.setOnClickListener { v ->
                showConversationInfoScreen()
            }
        }

        isLeavingForConversation = false
        ApplicationWideCurrentRoomHolder.getInstance().currentRoomId = roomId
        ApplicationWideCurrentRoomHolder.getInstance().currentRoomToken = roomId
        ApplicationWideCurrentRoomHolder.getInstance().isInCall = false
        ApplicationWideCurrentRoomHolder.getInstance().userInRoom = conversationUser

        isLinkPreviewAllowed = appPreferences?.areLinkPreviewsAllowed ?: false

        emojiPopup = messageInput?.let {
            EmojiPopup.Builder.fromRootView(view).setOnEmojiPopupShownListener {
            if (resources != null) {
                smileyButton?.setColorFilter(resources!!.getColor(R.color.colorPrimary), PorterDuff.Mode.SRC_IN)
            }
        }.setOnEmojiPopupDismissListener {
            smileyButton?.setColorFilter(resources!!.getColor(R.color.emoji_icons),
                    PorterDuff.Mode.SRC_IN)
        }.setOnEmojiClickListener { emoji, imageView -> messageInput?.editableText?.append(" ") }.build(it)
        }

        if (activity != null) {
            KeyboardUtils(activity, getView(), false)
        }

        cancelNotificationsForCurrentConversation()

        if (inConversation) {
            if (wasDetached && conversationUser?.hasSpreedFeatureCapability("no-ping") ?: false) {
                currentConversation?.sessionId = "0"
                wasDetached = false
                joinRoomWithPassword()
            }
        }
    }

    private fun cancelNotificationsForCurrentConversation() {
        if (conversationUser != null) {
            if (!conversationUser.hasSpreedFeatureCapability("no-ping") && !TextUtils.isEmpty(roomId)) {
                NotificationUtils.cancelExistingNotificationsForRoom(applicationContext,
                        conversationUser, roomId)
            } else if (!TextUtils.isEmpty(roomToken)) {
                NotificationUtils.cancelExistingNotificationsForRoom(applicationContext,
                        conversationUser, roomToken!!)
            }
        }
    }

    override fun onDetach(view: View) {
        super.onDetach(view)
        ApplicationWideCurrentRoomHolder.getInstance().clear()
        eventBus?.unregister(this)

        if (activity != null) {
            activity?.findViewById<View>(R.id.toolbar)?.setOnClickListener(null)
        }

        if (conversationUser != null && conversationUser?.hasSpreedFeatureCapability("no-ping")
                && activity != null && !activity?.isChangingConfigurations!! && !isLeavingForConversation) {
            wasDetached = true
            leaveRoom()
        }

        if (mentionAutocomplete != null && mentionAutocomplete!!.isPopupShowing) {
            mentionAutocomplete?.dismissPopup()
        }
    }

    override fun getTitle(): String? {
        if (currentConversation != null && currentConversation?.displayName != null) {
            return EmojiCompat.get().process(currentConversation!!.displayName).toString()
        } else {
            return ""
        }
    }

    public override fun onDestroy() {
        super.onDestroy()

        if (activity != null) {
            activity?.findViewById<View>(R.id.toolbar)?.setOnClickListener(null)
        }

        if (actionBar != null) {
            actionBar?.setIcon(null)
        }

        adapter = null
        inConversation = false
    }

    private fun startPing() {
        if (conversationUser != null && !conversationUser.hasSpreedFeatureCapability("no-ping")) {
            ncApi?.pingCall(credentials, ApiUtils.getUrlForCallPing(conversationUser.baseUrl,
                    roomToken))
                    ?.subscribeOn(Schedulers.io())
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.repeatWhen { observable -> observable.delay(5000, TimeUnit.MILLISECONDS) }
                    ?.takeWhile { observable -> inConversation }
                    ?.retry(3) { observable -> inConversation }
                    ?.`as`(AutoDispose.autoDisposable(scopeProvider))
                    ?.subscribe(object : Observer<GenericOverall> {
                        override fun onSubscribe(d: Disposable) {
                        }

                        override fun onNext(genericOverall: GenericOverall) {

                        }

                        override fun onError(e: Throwable) {}

                        override fun onComplete() {}
                    })
        }
    }

    @OnClick(R.id.smileyButton)
    internal fun onSmileyClick() {
        emojiPopup?.toggle()
    }

    private fun joinRoomWithPassword() {

        if (currentConversation == null || TextUtils.isEmpty(currentConversation?.sessionId) ||
                currentConversation?.sessionId == "0") {
            ncApi?.joinRoom(credentials,
                    ApiUtils.getUrlForSettingMyselfAsActiveParticipant(conversationUser?.baseUrl, roomToken), roomPassword)
                    ?.subscribeOn(Schedulers.io())
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.retry(3)
                    ?.`as`(AutoDispose.autoDisposable(scopeProvider))
                    ?.subscribe(object : Observer<RoomOverall> {
                        override fun onSubscribe(d: Disposable) {
                        }

                        override fun onNext(roomOverall: RoomOverall) {
                            inConversation = true
                            currentConversation?.sessionId = roomOverall.ocs.data.sessionId

                            ApplicationWideCurrentRoomHolder.getInstance().session =
                                    currentConversation?.sessionId
                            startPing()

                            setupWebsocket()
                            checkLobbyState()

                            if (isFirstMessagesProcessing) {
                                pullChatMessages(0)
                            } else {
                                pullChatMessages(1)
                            }

                            if (magicWebSocketInstance != null) {
                                magicWebSocketInstance?.joinRoomWithRoomTokenAndSession(roomToken, currentConversation?.sessionId)
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
                magicWebSocketInstance?.joinRoomWithRoomTokenAndSession(roomToken,
                        currentConversation?.sessionId)
            }
            startPing()
            if (isFirstMessagesProcessing) {
                pullChatMessages(0)
            } else {
                pullChatMessages(1)
            }
        }
    }

    private fun leaveRoom() {
        ncApi?.leaveRoom(credentials,
                ApiUtils.getUrlForSettingMyselfAsActiveParticipant(conversationUser?.baseUrl,
                        roomToken))
                ?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.`as`(AutoDispose.autoDisposable(scopeProvider))
                ?.subscribe(object : Observer<GenericOverall> {
                    override fun onSubscribe(d: Disposable) {
                    }

                    override fun onNext(genericOverall: GenericOverall) {
                        checkingLobbyStatus = false

                        if (lobbyTimerHandler != null) {
                            lobbyTimerHandler?.removeCallbacksAndMessages(null)
                        }

                        if (magicWebSocketInstance != null && currentConversation != null) {
                            magicWebSocketInstance?.joinRoomWithRoomTokenAndSession("",
                                    currentConversation?.sessionId)
                        }

                        if (!isDestroyed && !isBeingDestroyed && !wasDetached) {
                            router.popCurrentController()
                        }
                    }

                    override fun onError(e: Throwable) {}

                    override fun onComplete() {
                    }
                })
    }

    private fun setSenderId() {
        try {
            val senderId = adapter?.javaClass?.getDeclaredField("senderId")
            senderId?.isAccessible = true
            senderId?.set(adapter, conversationUser?.userId)
        } catch (e: NoSuchFieldException) {
            Log.w(TAG, "Failed to set sender id")
        } catch (e: IllegalAccessException) {
            Log.w(TAG, "Failed to access and set field")
        }

    }

    private fun submitMessage() {
        if (messageInput != null) {
            val editable = messageInput!!.editableText
            val mentionSpans = editable.getSpans(0, editable.length,
                    Spans.MentionChipSpan::class.java)
            var mentionSpan: Spans.MentionChipSpan
            for (i in mentionSpans.indices) {
                mentionSpan = mentionSpans[i]
                var mentionId = mentionSpan.id
                if (mentionId.contains(" ") || mentionId.startsWith("guest/")) {
                    mentionId = "\"" + mentionId + "\""
                }
                editable.replace(editable.getSpanStart(mentionSpan), editable.getSpanEnd(mentionSpan), "@$mentionId")
            }

            messageInput?.setText("")
            sendMessage(editable)
        }
    }

    private fun sendMessage(message: CharSequence) {

        if (conversationUser != null) {
            ncApi?.sendChatMessage(credentials, ApiUtils.getUrlForChat(conversationUser?.baseUrl,
                    roomToken),
                    message, conversationUser.displayName)
                    ?.subscribeOn(Schedulers.io())
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.`as`(AutoDispose.autoDisposable(scopeProvider))
                    ?.subscribe(object : Observer<GenericOverall> {
                        override fun onSubscribe(d: Disposable) {

                        }

                        override fun onNext(genericOverall: GenericOverall) {
                            myFirstMessage = message

                            if (popupBubble?.isShown ?: false) {
                                popupBubble?.hide()
                            }

                            messagesListView?.smoothScrollToPosition(0)
                        }

                        override fun onError(e: Throwable) {
                            if (e is HttpException) {
                                val code = e.code()
                                if (Integer.toString(code).startsWith("2")) {
                                    myFirstMessage = message

                                    if (popupBubble?.isShown ?: false) {
                                        popupBubble?.hide()
                                    }

                                    messagesListView?.smoothScrollToPosition(0)
                                }
                            }
                        }

                        override fun onComplete() {

                        }
                    })
        }
    }

    private fun setupWebsocket() {
        if (conversationUser != null) {
            if (WebSocketConnectionHelper.getMagicWebSocketInstanceForUserId(conversationUser.id) != null) {
                magicWebSocketInstance = WebSocketConnectionHelper.getMagicWebSocketInstanceForUserId(conversationUser.id)
            } else {
                magicWebSocketInstance = null
            }
        }
    }

    private fun pullChatMessages(lookIntoFuture: Int) {
        if (!inConversation) {
            return
        }

        if (currentConversation != null && currentConversation!!.shouldShowLobby(conversationUser)) {
            return
        }

        val fieldMap = HashMap<String, Int>()
        fieldMap["includeLastKnown"] = 0

        var timeout = 30
        if (!lookingIntoFuture) {
            timeout = 0;
        }

        fieldMap["timeout"] = timeout

        if (lookIntoFuture > 0) {
            lookingIntoFuture = true
        } else if (isFirstMessagesProcessing) {
            if (currentConversation != null) {
                globalLastKnownFutureMessageId = currentConversation!!.lastReadMessage
                globalLastKnownPastMessageId = currentConversation!!.lastReadMessage
                fieldMap["includeLastKnown"] = 1
            }
        }

        fieldMap["lookIntoFuture"] = lookIntoFuture
        fieldMap["limit"] = 100
        fieldMap["setReadMarker"] = 1

        val lastKnown: Int
        if (lookIntoFuture > 0) {
            lastKnown = globalLastKnownFutureMessageId
        } else {
            lastKnown = globalLastKnownPastMessageId
        }

        fieldMap["lastKnownMessageId"] = lastKnown

        if (!wasDetached) {
            if (lookIntoFuture > 0) {
                val finalTimeout = timeout
                ncApi?.pullChatMessages(credentials, ApiUtils.getUrlForChat(conversationUser?.baseUrl, roomToken), fieldMap)
                        ?.subscribeOn(Schedulers.io())
                        ?.observeOn(AndroidSchedulers.mainThread())
                        ?.takeWhile { observable -> inConversation && !wasDetached }
                        ?.`as`(AutoDispose.autoDisposable(scopeProvider))
                        ?.subscribe(object : Observer<Response<*>> {
                            override fun onSubscribe(d: Disposable) {
                            }

                            override fun onNext(response: Response<*>) {
                                if (response.code() == 304) {
                                    pullChatMessages(1)
                                } else {
                                    processMessages(response, true, finalTimeout)
                                }
                            }

                            override fun onError(e: Throwable) {

                            }

                            override fun onComplete() {

                            }
                        })

            } else {
                ncApi?.pullChatMessages(credentials,
                        ApiUtils.getUrlForChat(conversationUser?.baseUrl, roomToken), fieldMap)
                        ?.subscribeOn(Schedulers.io())
                        ?.observeOn(AndroidSchedulers.mainThread())
                        ?.retry(3) { observable -> inConversation && !wasDetached }
                        ?.takeWhile { observable -> inConversation && !wasDetached }
                        ?.`as`(AutoDispose.autoDisposable(scopeProvider))
                        ?.subscribe(object : Observer<Response<*>> {
                            override fun onSubscribe(d: Disposable) {
                            }

                            override fun onNext(response: Response<*>) {
                                processMessages(response, false, 0)
                            }

                            override fun onError(e: Throwable) {

                            }

                            override fun onComplete() {

                            }
                        })
            }
        }
    }

    private fun processMessages(response: Response<*>, isFromTheFuture: Boolean, timeout: Int) {
        val xChatLastGivenHeader: String? = response.headers().get("X-Chat-Last-Given")
        if (response.headers().size() > 0 && !TextUtils.isEmpty(xChatLastGivenHeader)) {

            val header = Integer.parseInt(xChatLastGivenHeader!!)
            if (header > 0) {
                if (isFromTheFuture) {
                    globalLastKnownFutureMessageId = header
                } else {
                    globalLastKnownPastMessageId = header
                }
            }
        }

        if (response.code() == 200) {

            val chatOverall = response.body() as ChatOverall?
            val chatMessageList = chatOverall?.ocs!!.data

            val wasFirstMessageProcessing = isFirstMessagesProcessing

            if (isFirstMessagesProcessing) {
                cancelNotificationsForCurrentConversation()

                isFirstMessagesProcessing = false
                loadingProgressBar?.visibility = View.GONE

                messagesListView?.visibility = View.VISIBLE

            }

            var countGroupedMessages = 0
            if (!isFromTheFuture) {

                for (i in chatMessageList.indices) {
                    if (chatMessageList.size > i + 1) {
                        if (TextUtils.isEmpty(chatMessageList[i].systemMessage) &&
                                TextUtils.isEmpty(chatMessageList[i + 1].systemMessage) &&
                                chatMessageList[i + 1].actorId == chatMessageList[i].actorId &&
                                countGroupedMessages < 4 && DateFormatter.isSameDay(chatMessageList[i].createdAt,
                                        chatMessageList[i + 1].createdAt)) {
                            chatMessageList[i].isGrouped = true;
                            countGroupedMessages++
                        } else {
                            countGroupedMessages = 0
                        }
                    }

                    val chatMessage = chatMessageList[i]
                    chatMessage.isOneToOneConversation = currentConversation?.type == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
                    chatMessage.isLinkPreviewAllowed = isLinkPreviewAllowed
                    chatMessage.activeUser = conversationUser

                }

                if (wasFirstMessageProcessing && chatMessageList.size > 0) {
                    globalLastKnownFutureMessageId = chatMessageList[0].jsonMessageId
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

                val isThereANewNotice = shouldAddNewMessagesNotice || adapter?.getMessagePositionByIdInReverse("-1") != -1

                for (i in chatMessageList.indices) {
                    chatMessage = chatMessageList[i]

                    chatMessage.activeUser = conversationUser
                    chatMessage.isLinkPreviewAllowed = isLinkPreviewAllowed

                    // if credentials are empty, we're acting as a guest
                    if (TextUtils.isEmpty(credentials) && myFirstMessage != null && !TextUtils.isEmpty(myFirstMessage?.toString())) {
                        if (chatMessage.actorType == "guests") {
                            conversationUser?.userId = chatMessage.actorId
                            setSenderId()
                        }
                    }

                    val shouldScroll = !isThereANewNotice && !shouldAddNewMessagesNotice && layoutManager?.findFirstVisibleItemPosition() == 0 || adapter != null && adapter?.itemCount == 0

                    if (!shouldAddNewMessagesNotice && !shouldScroll && popupBubble != null) {
                        if (!popupBubble!!.isShown) {
                            newMessagesCount = 1
                            popupBubble?.show()
                        } else if (popupBubble!!.isShown) {
                            newMessagesCount++
                        }
                    } else {
                        newMessagesCount = 0
                    }

                    if (adapter != null) {
                        chatMessage.isGrouped = (adapter!!.isPreviousSameAuthor(chatMessage
                        .actorId, -1) && adapter!!.getSameAuthorLastMessagesCount(chatMessage.actorId) % 5 > 0)
                        chatMessage.isOneToOneConversation = (currentConversation?.type == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL)
                        adapter?.addToStart(chatMessage, shouldScroll)
                    }

                }

                if (shouldAddNewMessagesNotice && adapter != null && messagesListView != null) {
                    layoutManager?.scrollToPositionWithOffset(adapter!!.getMessagePositionByIdInReverse("-1"), messagesListView!!.height / 2)
                }

            }

            if (inConversation) {
                pullChatMessages(1)
            }
        } else if (response.code() == 304 && !isFromTheFuture) {
            if (isFirstMessagesProcessing) {
                cancelNotificationsForCurrentConversation()

                isFirstMessagesProcessing = false
                loadingProgressBar?.visibility = View.GONE
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
            if (it.hasSpreedFeatureCapability("read-only-rooms")) {
                checkReadOnlyState()
            }
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                router.popCurrentController()
                return true
            }
            R.id.conversation_video_call -> {
                if (conversationVideoMenuItem?.icon?.alpha == 255) {
                    startACall(false)
                    return true
                }
                return false
            }
            R.id.conversation_voice_call -> {
                if (conversationVoiceCallMenuItem?.icon?.alpha == 255) {
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

    private fun startACall(isVoiceOnlyCall: Boolean) {
        isLeavingForConversation = true
        if (!isVoiceOnlyCall) {
            val videoCallIntent = getIntentForCall(false)
            if (videoCallIntent != null) {
                startActivity(videoCallIntent)
            }
        } else {
            val voiceCallIntent = getIntentForCall(true)
            if (voiceCallIntent != null) {
                startActivity(voiceCallIntent)
            }
        }
    }

    private fun getIntentForCall(isVoiceOnlyCall: Boolean): Intent? {
        if (currentConversation != null) {
            val bundle = Bundle()
            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken)
            bundle.putString(BundleKeys.KEY_ROOM_ID, roomId)
            bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, conversationUser)
            bundle.putString(BundleKeys.KEY_CONVERSATION_PASSWORD, roomPassword)
            bundle.putString(BundleKeys.KEY_MODIFIED_BASE_URL, conversationUser?.baseUrl)

            if (isVoiceOnlyCall) {
                bundle.putBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, true)
            }

            if (activity != null) {
                val callIntent = Intent(activity, MagicCallActivity::class.java)
                callIntent.putExtras(bundle)

                return callIntent
            } else {
                return null
            }
        } else {
            return null
        }
    }

    override fun onMessageLongClick(message: IMessage) {
        if (activity != null) {
            val clipboardManager = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clipData = android.content.ClipData.newPlainText(
                    resources?.getString(R.string.nc_app_name), message.text)
            clipboardManager.primaryClip = clipData
        }
    }

    override fun hasContentFor(message: IMessage, type: Byte): Boolean {
        when (type) {
            CONTENT_TYPE_SYSTEM_MESSAGE -> return !TextUtils.isEmpty(message.systemMessage)
            CONTENT_TYPE_UNREAD_NOTICE_MESSAGE -> return message.id == "-1"
        }

        return false
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onMessageEvent(webSocketCommunicationEvent: WebSocketCommunicationEvent) {
        /*
        switch (webSocketCommunicationEvent.getType()) {
            case "refreshChat":

                if (webSocketCommunicationEvent.getHashMap().get(BundleKeys.KEY_INTERNAL_USER_ID).equals(Long.toString(conversationUser.getId()))) {
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
        if (currentConversation?.type != Conversation.ConversationType
                        .ROOM_TYPE_ONE_TO_ONE_CALL || currentConversation?.name !=
                userMentionClickEvent.userId) {
            val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(conversationUser?.baseUrl, "1",
                    userMentionClickEvent.userId, null)

            ncApi?.createRoom(credentials,
                    retrofitBucket.url, retrofitBucket.queryMap)
                    ?.subscribeOn(Schedulers.io())
                    ?.observeOn(AndroidSchedulers.mainThread())
                    ?.`as`(AutoDispose.autoDisposable(scopeProvider))
                    ?.subscribe(object : Observer<RoomOverall> {
                        override fun onSubscribe(d: Disposable) {

                        }

                        override fun onNext(roomOverall: RoomOverall) {
                            val conversationIntent = Intent(activity, MagicCallActivity::class.java)
                            val bundle = Bundle()
                            bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, conversationUser)
                            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomOverall.ocs.data.token)
                            bundle.putString(BundleKeys.KEY_ROOM_ID, roomOverall.ocs.data.roomId)

                            if (conversationUser != null) {
                            if (conversationUser.hasSpreedFeatureCapability("chat-v2")) {
                                bundle.putParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION,
                                        Parcels.wrap(roomOverall.ocs.data))
                                conversationIntent.putExtras(bundle)

                                ConductorRemapping.remapChatController(router, conversationUser.id,
                                        roomOverall.ocs.data.token, bundle, false)
                            }

                            } else {
                                conversationIntent.putExtras(bundle)
                                startActivity(conversationIntent)
                                Handler().postDelayed({
                                    if (!isDestroyed && !isBeingDestroyed) {
                                        router.popCurrentController()
                                    }
                                }, 100)
                            }
                        }

                        override fun onError(e: Throwable) {

                        }

                        override fun onComplete() {}
                    })
        }
    }

    companion object {
        private val TAG = "ChatController"
        private val CONTENT_TYPE_SYSTEM_MESSAGE: Byte = 1
        private val CONTENT_TYPE_UNREAD_NOTICE_MESSAGE: Byte = 2
    }
}
