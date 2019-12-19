package com.nextcloud.talk.newarch.features.chat

import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextUtils
import android.text.TextWatcher
import android.view.*
import android.widget.AbsListView
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.api.load
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.messages.*
import com.nextcloud.talk.callbacks.MentionAutocompleteCallback
import com.nextcloud.talk.components.filebrowser.controllers.BrowserController
import com.nextcloud.talk.controllers.ChatController
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.mention.Mention
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseView
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.local.models.getMaxMessageLength
import com.nextcloud.talk.newarch.mvvm.ext.initRecyclerView
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.presenters.MentionAutocompletePresenter
import com.nextcloud.talk.utils.*
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_PASSWORD
import com.nextcloud.talk.utils.text.Spans
import com.otaliastudios.autocomplete.Autocomplete
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.messages.MessageHolders
import com.stfalcon.chatkit.messages.MessagesListAdapter
import com.stfalcon.chatkit.utils.DateFormatter
import kotlinx.android.synthetic.main.controller_chat.view.*
import kotlinx.android.synthetic.main.controller_conversations_rv.view.*
import kotlinx.android.synthetic.main.lobby_view.view.*
import kotlinx.android.synthetic.main.view_message_input.view.*
import org.koin.android.ext.android.inject
import org.parceler.Parcels
import java.util.*
import coil.ImageLoader as CoilImageLoader
import com.stfalcon.chatkit.commons.ImageLoader as ChatKitImageLoader

class ChatView : BaseView(), MessageHolders.ContentChecker<IMessage>, MessagesListAdapter.OnLoadMoreListener, MessagesListAdapter
.OnMessageLongClickListener<IMessage>, MessagesListAdapter.Formatter<Date> {

    lateinit var viewModel: ChatViewModel
    val factory: ChatViewModelFactory by inject()
    val imageLoader: CoilImageLoader by inject()

    var conversationInfoMenuItem: MenuItem? = null
    var conversationVoiceCallMenuItem: MenuItem? = null
    var conversationVideoMenuItem: MenuItem? = null

    private var newMessagesCount = 0

    private lateinit var recyclerViewAdapter: MessagesListAdapter<ChatMessage>
    private lateinit var mentionAutocomplete: Autocomplete<*>

    private var shouldShowLobby: Boolean = false
    private var isReadOnlyConversation: Boolean = false

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        setHasOptionsMenu(true)
        actionBar?.show()
        viewModel = viewModelProvider(factory).get(ChatViewModel::class.java)
        viewModel.init(args.getParcelable(BundleKeys.KEY_USER_ENTITY)!!, args.getString(BundleKeys.KEY_ROOM_TOKEN)!!, args.getString(KEY_CONVERSATION_PASSWORD))

        viewModel.apply {
            conversation.observe(this@ChatView) { conversation ->
                setTitle()
                setupAdapter()

                if (Conversation.ConversationType.ONE_TO_ONE_CONVERSATION == conversation?.type) {
                    loadAvatar()
                } else {
                    actionBar?.setIcon(null)
                }

                shouldShowLobby = conversation!!.shouldShowLobby(user)
                isReadOnlyConversation = conversation.conversationReadOnlyState == Conversation.ConversationReadOnlyState.CONVERSATION_READ_ONLY

                activity?.invalidateOptionsMenu()

                if (shouldShowLobby) {
                    view?.messagesListView?.visibility = View.GONE
                    view?.messageInputView?.visibility = View.GONE
                    view?.lobbyView?.visibility = View.VISIBLE
                    val timer = conversation.lobbyTimer
                    if (timer != null && timer != 0L) {
                        view?.lobbyTextView?.text =  String.format(
                                resources!!.getString(R.string.nc_lobby_waiting_with_date),
                                DateUtils.getLocalDateStringFromTimestampForLobby(
                                        conversation.lobbyTimer!!
                                ))
                    } else {
                        view?.lobbyTextView?.setText(R.string.nc_lobby_waiting)
                    }
                } else {
                    view?.messagesListView?.visibility = View.GONE
                    view?.lobbyView?.visibility = View.GONE

                    if (isReadOnlyConversation) {
                        view?.messageInputView?.visibility = View.GONE
                    } else {
                        view?.messageInputView?.visibility = View.VISIBLE

                    }
                }

            }
        }
        return super.onCreateView(inflater, container)
    }


    override fun onAttach(view: View) {
        super.onAttach(view)
        setupViews()
    }

    override fun onCreateOptionsMenu(
            menu: Menu,
            inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_conversation_plus_filter, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        conversationInfoMenuItem = menu.findItem(R.id.conversation_info)
        conversationVoiceCallMenuItem = menu.findItem(R.id.conversation_voice_call)
        conversationVideoMenuItem = menu.findItem(R.id.conversation_video_call)

        if (shouldShowLobby || isReadOnlyConversation) {
            conversationVoiceCallMenuItem?.isVisible = false
            conversationVideoMenuItem?.isVisible = false
        } else {
            conversationVoiceCallMenuItem?.isVisible = true
            conversationVideoMenuItem?.isVisible = true
        }
    }

    private fun setupViews() {
        view?.let { view ->
            view.recyclerView.initRecyclerView(
                    LinearLayoutManager(view.context), recyclerViewAdapter, false
            )

            recyclerViewAdapter.setLoadMoreListener(this)
            recyclerViewAdapter.setDateHeadersFormatter { format(it) }
            recyclerViewAdapter.setOnMessageLongClickListener { onMessageLongClick(it) }

            view.popupBubbleView.setRecyclerView(view.messagesListView)

            view.popupBubbleView.setPopupBubbleListener { context ->
                if (newMessagesCount != 0) {
                    val scrollPosition: Int
                    if (newMessagesCount - 1 < 0) {
                        scrollPosition = 0
                    } else {
                        scrollPosition = newMessagesCount - 1
                    }
                    view.messagesListView.postDelayed({
                        view.messagesListView.smoothScrollToPosition(scrollPosition)
                    }, 200)
                }
            }

            view.messagesListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(
                        recyclerView: RecyclerView,
                        newState: Int
                ) {
                    super.onScrollStateChanged(recyclerView, newState)

                    if (newState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                        if (newMessagesCount != 0) {
                            val layoutManager: LinearLayoutManager = view.messagesListView.layoutManager as LinearLayoutManager
                            if (layoutManager.findFirstCompletelyVisibleItemPosition() <
                                    newMessagesCount
                            ) {
                                newMessagesCount = 0

                                view.popupBubbleView?.hide()
                            }
                        }
                    }
                }
            })

            val filters = arrayOfNulls<InputFilter>(1)
            val lengthFilter = viewModel.user.getMaxMessageLength()


            filters[0] = InputFilter.LengthFilter(lengthFilter)
            view.messageInput.filters = filters

            view.messageInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                        s: CharSequence,
                        start: Int,
                        count: Int,
                        after: Int
                ) {

                }

                override fun onTextChanged(
                        s: CharSequence,
                        start: Int,
                        before: Int,
                        count: Int
                ) {
                    if (s.length >= lengthFilter) {
                        view.messageInput.error = String.format(
                                Objects.requireNonNull<Resources>
                                (resources).getString(R.string.nc_limit_hit), Integer.toString(lengthFilter)
                        )
                    } else {
                        view.messageInput.error = null
                    }

                    val editable = view.messageInput.editableText
                    if (editable != null) {
                        val mentionSpans = editable.getSpans(
                                0, view.messageInput.length(),
                                Spans.MentionChipSpan::class.java
                        )
                        var mentionSpan: Spans.MentionChipSpan
                        for (i in mentionSpans.indices) {
                            mentionSpan = mentionSpans[i]
                            if (start >= editable.getSpanStart(mentionSpan) && start < editable.getSpanEnd(
                                            mentionSpan
                                    )
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

                }
            })

            view.messageInputView?.setAttachmentsListener {
                showBrowserScreen(
                        BrowserController
                                .BrowserType.DAV_BROWSER
                )
            }

            view.messageInputView?.button?.setOnClickListener { submitMessage() }

            view.messageInputView?.button?.contentDescription = resources?.getString(
                    R.string.nc_description_send_message_button
            )

        }

        setupMentionAutocomplete()
    }

    private fun setupMentionAutocomplete() {
        viewModel.conversation.value?.let { conversation ->
            view?.let { view ->
                val elevation = 6f
                val backgroundDrawable = ColorDrawable(resources!!.getColor(R.color.bg_default))
                val presenter = MentionAutocompletePresenter(context, conversation.token)
                val callback = MentionAutocompleteCallback(
                        activity,
                        viewModel.user, view.messageInput
                )

                if (!::mentionAutocomplete.isInitialized) {
                    mentionAutocomplete = Autocomplete.on<Mention>(view.messageInput)
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

    private fun submitMessage() {
        val editable = view?.messageInput?.editableText
        editable?.let {
            val mentionSpans = it.getSpans(
                    0, it.length,
                    Spans.MentionChipSpan::class.java
            )
            var mentionSpan: Spans.MentionChipSpan
            for (i in mentionSpans.indices) {
                mentionSpan = mentionSpans[i]
                var mentionId = mentionSpan.id
                if (mentionId.contains(" ") || mentionId.startsWith("guest/")) {
                    mentionId = "\"" + mentionId + "\""
                }
                it.replace(
                        it.getSpanStart(mentionSpan), it.getSpanEnd(mentionSpan), "@$mentionId"
                )
            }

            view?.messageInput?.setText("")
            viewModel.sendMessage(it)

        }
    }

    private fun showBrowserScreen(browserType: BrowserController.BrowserType) {
        viewModel.conversation.value?.let {
            val bundle = Bundle()
            bundle.putParcelable(
                    BundleKeys.KEY_BROWSER_TYPE, Parcels.wrap<BrowserController.BrowserType>(browserType)
            )
            bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, Parcels.wrap<UserNgEntity>(viewModel.user))
            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, it.token)
            router.pushController(
                    RouterTransaction.with(BrowserController(bundle))
                            .pushChangeHandler(VerticalChangeHandler())
                            .popChangeHandler(VerticalChangeHandler())
            )

        }
    }

    private fun setupAdapter() {
        val messageHolders = MessageHolders()
        messageHolders.setIncomingTextConfig(
                MagicIncomingTextMessageViewHolder::class.java, R.layout.item_custom_incoming_text_message
        )
        messageHolders.setOutcomingTextConfig(
                MagicOutcomingTextMessageViewHolder::class.java,
                R.layout.item_custom_outcoming_text_message
        )

        messageHolders.setIncomingImageConfig(
                MagicPreviewMessageViewHolder::class.java, R.layout.item_custom_incoming_preview_message
        )
        messageHolders.setOutcomingImageConfig(
                MagicPreviewMessageViewHolder::class.java, R.layout.item_custom_outcoming_preview_message
        )

        messageHolders.registerContentType(
                ChatController.CONTENT_TYPE_SYSTEM_MESSAGE, MagicSystemMessageViewHolder::class.java,
                R.layout.item_system_message, MagicSystemMessageViewHolder::class.java,
                R.layout.item_system_message,
                this
        )

        messageHolders.registerContentType(
                ChatController.CONTENT_TYPE_UNREAD_NOTICE_MESSAGE,
                MagicUnreadNoticeMessageViewHolder::class.java, R.layout.item_date_header,
                MagicUnreadNoticeMessageViewHolder::class.java, R.layout.item_date_header, this
        )

        recyclerViewAdapter = MessagesListAdapter(
                viewModel.user.userId, messageHolders, ChatKitImageLoader { imageView, url, payload ->
            imageView.load(url) {
                if (url!!.contains("/avatar/")) {
                    transformations(CircleCropTransformation())
                } else {
                    if (payload is ImageLoaderPayload) {
                        payload.map?.let {
                            if (payload.map.containsKey("mimetype")) {
                                placeholder(
                                        DrawableUtils.getDrawableResourceIdForMimeType(
                                                payload.map.get("mimetype") as String?
                                        )
                                )
                            }
                        }
                    }
                }

                val needsAuthBasedOnUrl = url.contains("index.php/core/preview?fileId=") || url.contains("index.php/avatar/")
                if (url.startsWith(viewModel.user.baseUrl) && needsAuthBasedOnUrl) {
                    addHeader("Authorization", viewModel.user.getCredentials())
                }
            }
        })

    }

    private fun loadAvatar() {
        val avatarSize = DisplayUtils.convertDpToPixel(
                conversationVoiceCallMenuItem?.icon!!
                        .intrinsicWidth.toFloat(), activity!!
        )
                .toInt()

        avatarSize.let {
            val target = object : Target {
                override fun onSuccess(result: Drawable) {
                    super.onSuccess(result)
                    actionBar?.setIcon(result)
                }
            }

            viewModel.conversation.value?.let {
                val avatarRequest = Images().getRequestForUrl(
                        imageLoader, context, ApiUtils.getUrlForAvatarWithNameAndPixels(
                        viewModel.user.baseUrl,
                        it.name, avatarSize / 2
                ), viewModel.user, target, this,
                        CircleCropTransformation()
                )

                imageLoader.load(avatarRequest)

            }
        }
    }

    override fun getLayoutId(): Int {
        return R.layout.controller_chat
    }

    override fun getTitle(): String? {
        return viewModel.conversation.value?.displayName
    }

    override fun hasContentFor(message: IMessage, type: Byte): Boolean {
        when (type) {
            ChatController.CONTENT_TYPE_SYSTEM_MESSAGE -> return !TextUtils.isEmpty(message.systemMessage)
            ChatController.CONTENT_TYPE_UNREAD_NOTICE_MESSAGE -> return message.id == "-1"
        }

        return false
    }

    override fun format(date: Date): String {
        return when {
            DateFormatter.isToday(date) -> {
                resources!!.getString(R.string.nc_date_header_today)
            }
            DateFormatter.isYesterday(date) -> {
                resources!!.getString(R.string.nc_date_header_yesterday)
            }
            else -> {
                DateFormatter.format(date, DateFormatter.Template.STRING_DAY_MONTH_YEAR)
            }
        }
    }

    override fun onLoadMore(page: Int, totalItemsCount: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onMessageLongClick(message: IMessage?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}