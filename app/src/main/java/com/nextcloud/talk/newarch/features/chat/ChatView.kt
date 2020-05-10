/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.features.chat

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextUtils
import android.text.TextWatcher
import android.view.*
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.lifecycle.observe
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.ImageLoader
import coil.api.load
import coil.api.loadAny
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.archlifecycle.ControllerLifecycleOwner
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MagicCallActivity
import com.nextcloud.talk.callbacks.MentionAutocompleteCallback
import com.nextcloud.talk.components.filebrowser.controllers.BrowserController
import com.nextcloud.talk.controllers.ConversationInfoController
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.mention.Mention
import com.nextcloud.talk.newarch.features.chat.interfaces.ImageLoaderInterface
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.local.models.getMaxMessageLength
import com.nextcloud.talk.newarch.local.models.toUserEntity
import com.nextcloud.talk.newarch.mvvm.BaseView
import com.nextcloud.talk.newarch.mvvm.ext.initRecyclerView
import com.nextcloud.talk.newarch.utils.swipe.ChatMessageSwipeCallback
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.newarch.utils.NetworkComponents
import com.nextcloud.talk.newarch.utils.swipe.ChatMessageSwipeInterface
import com.nextcloud.talk.presenters.MentionAutocompletePresenter
import com.nextcloud.talk.utils.*
import com.nextcloud.talk.utils.AccountUtils.canWeOpenFilesApp
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ACCOUNT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BROWSER_TYPE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_PASSWORD
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ROOM_ID
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.text.Spans
import com.otaliastudios.autocomplete.Autocomplete
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import com.stfalcon.chatkit.utils.DateFormatter
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import com.vanniktech.emoji.EmojiPopup
import kotlinx.android.synthetic.main.controller_chat.view.*
import kotlinx.android.synthetic.main.item_message_quote.view.*
import kotlinx.android.synthetic.main.lobby_view.view.*
import kotlinx.android.synthetic.main.view_message_input.view.*
import org.koin.android.ext.android.inject
import org.parceler.Parcels
import java.util.*

class ChatView(private val bundle: Bundle) : BaseView(), ImageLoaderInterface {
    override val scopeProvider: LifecycleScopeProvider<*> = ControllerScopeProvider.from(this)
    override val lifecycleOwner = ControllerLifecycleOwner(this)

    private lateinit var viewModel: ChatViewModel
    val factory: ChatViewModelFactory by inject()
    private val networkComponents: NetworkComponents by inject()

    private var initialAdapterFillFinished = false
    private var popupBubbleScrollPosition = 0
    var conversationInfoMenuItem: MenuItem? = null
    var conversationVoiceCallMenuItem: MenuItem? = null
    var conversationVideoMenuItem: MenuItem? = null

    private lateinit var mentionAutocomplete: Autocomplete<*>

    private var shouldShowLobby: Boolean = false
    private var isReadOnlyConversation: Boolean = false

    private var emojiPopup: EmojiPopup? = null
    private lateinit var messagesAdapter: Adapter
    private val toolbarOnClickListener: View.OnClickListener = View.OnClickListener {
        showConversationInfoScreen()
    }

    private lateinit var user: User
    private lateinit var conversationToken: String
    private var conversationPassword: String? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        setHasOptionsMenu(true)
        actionBar?.show()
        viewModel = viewModelProvider(factory).get(ChatViewModel::class.java)
        val view = super.onCreateView(inflater, container)

        messagesAdapter = Adapter.builder(this)
                .addSource(ChatViewLiveDataSource(viewModel.messagesLiveData))
                .addSource(ChatDateHeaderSource(activity as Context, ChatElementTypes.DATE_HEADER.ordinal))
                .addPresenter(Presenter.forLoadingIndicator(activity as Context, R.layout.loading_state))
                .addPresenter(ChatPresenter(activity as Context, ::onElementClick, ::onElementLongClick, this))
                .into(view.messagesRecyclerView)

        messagesAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                val layoutManager = view.messagesRecyclerView.layoutManager as LinearLayoutManager

                if (layoutManager.findLastVisibleItemPosition() == positionStart - 1) {
                    view.messagesRecyclerView.post {
                        view.messagesRecyclerView.smoothScrollToPosition(positionStart + 1)
                    }
                } else {
                    if (initialAdapterFillFinished) {
                        var minus = itemCount
                        for (i in positionStart..positionStart + itemCount) {
                            val item = messagesAdapter.elementAt(i)
                            if (item != null) {
                                if (item.element.data is ChatElement) {
                                    val data = item.element.data as ChatElement
                                    if (data.elementType == ChatElementTypes.CHAT_MESSAGE) {
                                        val chatElement = item.element.data as ChatElement
                                        val chatMessage = chatElement.data as ChatMessage
                                        if (chatMessage.actorId == viewModel.user.userId) {
                                            minus -= 1
                                        }
                                    }
                                }
                            }
                        }

                        if (popupBubbleScrollPosition == 0 && minus != 0) {
                            popupBubbleScrollPosition = messagesAdapter.itemCount - minus
                            view.popupBubbleView.activate()
                        } else if (popupBubbleScrollPosition > 0) {
                            popupBubbleScrollPosition -= itemCount
                        }
                    } else {
                        initialAdapterFillFinished = true
                    }
                }
            }
        })

        val layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
        layoutManager.stackFromEnd = true
        view.messagesRecyclerView.initRecyclerView(layoutManager, messagesAdapter, true)
        view.messagesRecyclerView.preserveFocusAfterLayout = true


        emojiPopup = view.messageInput.let {
            EmojiPopup.Builder.fromRootView(view).setOnEmojiPopupShownListener {
                if (resources != null) {
                    view.smileyButton?.setColorFilter(resources!!.getColor(R.color.colorPrimary), PorterDuff.Mode.SRC_IN)
                }
            }.setOnEmojiPopupDismissListener {
                view.smileyButton.setColorFilter(resources!!.getColor(R.color.emoji_icons),
                        PorterDuff.Mode.SRC_IN)
            }.setOnEmojiClickListener { emoji, imageView -> it.editableText?.append(" ") }.build(it)
        }

        view.smileyButton.setOnClickListener {
            emojiPopup?.toggle()
        }

        viewModel.apply {
            conversation.observe(this@ChatView) { conversation ->
                setTitle()

                shouldShowLobby = conversation!!.shouldShowLobby(user)
                isReadOnlyConversation = conversation.conversationReadOnlyState == Conversation.ConversationReadOnlyState.CONVERSATION_READ_ONLY

                activity?.invalidateOptionsMenu()
                setupMentionAutocomplete()

                if (shouldShowLobby) {
                    view.messagesRecyclerView?.visibility = View.GONE
                    view.messageInputView?.visibility = View.GONE
                    view.separator?.visibility = View.GONE
                    view.lobbyView?.visibility = View.VISIBLE
                    val timer = conversation.lobbyTimer
                    val unit = if (timer != null && timer != 0L) {
                        view.lobbyTextView?.text = String.format(
                                resources!!.getString(R.string.nc_lobby_waiting_with_date),
                                DateUtils.getLocalDateStringFromTimestampForLobby(
                                        conversation.lobbyTimer!!
                                ))
                    } else {
                        view.lobbyTextView?.setText(R.string.nc_lobby_waiting)
                    }
                } else {
                    view.messagesRecyclerView?.visibility = View.VISIBLE
                    view.lobbyView?.visibility = View.GONE

                    if (isReadOnlyConversation) {
                        view.messageInputView?.visibility = View.GONE
                        view.separator?.visibility = View.GONE
                    } else {
                        view.messageInputView?.visibility = View.VISIBLE
                        view.separator?.visibility = View.VISIBLE
                    }
                }
            }
        }

        view.cancelReplyButton.setOnClickListener { hideReplyView() }

        val controller = ChatMessageSwipeCallback(context, messagesAdapter, object : ChatMessageSwipeInterface {
            override fun onSwipePerformed(position: Int) {
                val element = messagesAdapter.elementAt(position)
                if (element != null) {
                    val adapterChatElement = element.element as Element<ChatElement>
                    if (adapterChatElement.data is ChatElement) {
                        val chatElement = adapterChatElement.data as ChatElement
                        showReplyView(chatElement.data as ChatMessage)
                    }
                }
            }
        })

        val itemTouchHelper = ItemTouchHelper(controller)
        itemTouchHelper.attachToRecyclerView(view.messagesRecyclerView)

        return view
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                router.popController(this)
                return true
            }
            R.id.conversation_video_call -> {
                startACall(false)
                return true
            }
            R.id.conversation_voice_call -> {
                startACall(true)
                return true
            }
            R.id.conversation_info -> {
                showConversationInfoScreen()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun showConversationInfoScreen() {
        viewModel.conversation.value?.let { conversation ->
            val bundle = Bundle()
            bundle.putParcelable(KEY_USER_ENTITY, viewModel.user.toUserEntity())
            bundle.putString(KEY_CONVERSATION_TOKEN, conversation.token)
            router.pushController(RouterTransaction.with(ConversationInfoController(bundle))
                    .pushChangeHandler(HorizontalChangeHandler())
                    .popChangeHandler(HorizontalChangeHandler()))
        }
    }

    private fun onElementClick(page: Page, holder: Presenter.Holder, element: Element<ChatElement>, payload: Map<String, String>) {
        if (element.type == ChatElementTypes.CHAT_MESSAGE.ordinal) {
            element.data?.let { chatElement ->
                var chatMessage = chatElement.data as ChatMessage
                if (payload.containsKey("parentMessage")) {
                    chatMessage = chatMessage.parentMessage!!
                }
                val currentUser = viewModel.user
                if (chatMessage.messageType == ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE) {
                    val accountString = currentUser.username + "@" + currentUser.baseUrl
                            .replace("https://", "")
                            .replace("http://", "")
                    if (canWeOpenFilesApp(context, accountString)) {
                        val filesAppIntent = Intent(Intent.ACTION_VIEW, null)
                        val componentName = ComponentName(
                                context.getString(R.string.nc_import_accounts_from),
                                "com.owncloud.android.ui.activity.FileDisplayActivity"
                        )
                        filesAppIntent.component = componentName
                        filesAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        filesAppIntent.setPackage(
                                context.getString(R.string.nc_import_accounts_from)
                        )
                        filesAppIntent.putExtra(
                                KEY_ACCOUNT, accountString
                        )
                        filesAppIntent.putExtra(
                                KEY_FILE_ID,
                                chatMessage.selectedIndividualHashMap!!["id"]
                        )
                        context.startActivity(filesAppIntent)
                    } else {
                        val browserIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(chatMessage.selectedIndividualHashMap!!["link"])
                        )
                        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(browserIntent)
                    }
                } else if (chatMessage.messageType == ChatMessage.MessageType.SINGLE_LINK_GIPHY_MESSAGE) {
                    val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://giphy.com")
                    )
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(browserIntent)
                } else if (chatMessage.messageType == ChatMessage.MessageType.SINGLE_LINK_TENOR_MESSAGE) {
                    val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://tenor.com")
                    )
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(browserIntent)
                } else if (chatMessage.messageType == ChatMessage.MessageType.SINGLE_LINK_IMAGE_MESSAGE) {
                    val browserIntent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(chatMessage.imageUrl)
                    )
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(browserIntent)
                }
            }

        }
    }

    private fun hideReplyView() {
        view?.messageInputView?.let {
            with (it) {
                quotedMessageLayout.tag = null
                quotedMessageLayout.isVisible = false
                attachmentButton.isVisible = true
                attachmentButtonSpace.isVisible = true
            }
        }
    }

    private fun showReplyView(chatMessage: ChatMessage) {
        view?.let {
            with(it.messageInputView) {
                attachmentButton.isVisible = false
                attachmentButtonSpace.isVisible = false
                cancelReplyButton.isVisible = true
                quotedChatText.maxLines = 2
                quotedChatText.ellipsize = TextUtils.TruncateAt.END
                quotedChatText.text = chatMessage.text
                quotedAuthor.text = if (chatMessage.user.name.isNotEmpty()) chatMessage.user.name else resources.getText(R.string.nc_guest)
                quotedMessageTime.text = DateFormatter.format(chatMessage.createdAt, DateFormatter.Template.TIME)
                loadImage(quotedUserAvatar, chatMessage.user.avatar)

                chatMessage.imageUrl?.let { previewImageUrl ->
                    if (previewImageUrl == "no-preview") {
                        if (chatMessage.selectedIndividualHashMap?.containsKey("mimetype") == true) {
                            quotedPreviewImage.isVisible = true
                            networkComponents.getImageLoader(viewModel.user).loadAny(context, DrawableUtils.getDrawableResourceIdForMimeType(chatMessage.selectedIndividualHashMap!!["mimetype"])) {
                                target(quotedPreviewImage) }
                        } else {
                            quotedPreviewImage.isVisible = false
                        }
                    } else {
                        quotedPreviewImage.isVisible = true
                        val mutableMap = mutableMapOf<String, String>()
                        if (chatMessage.selectedIndividualHashMap?.containsKey("mimetype") == true) {
                            mutableMap["mimetype"] = chatMessage.selectedIndividualHashMap!!["mimetype"]!!
                        }

                        loadImage(quotedPreviewImage, previewImageUrl, mutableMap)
                    }
                } ?: run {
                    quotedPreviewImage.isVisible = false
                }
                quotedMessageLayout.tag = chatMessage.jsonMessageId
                quotedMessageLayout.isVisible = true

            }
        }
    }

    private fun onElementLongClick(page: Page, holder: Presenter.Holder, element: Element<ChatElement>, payload: Map<String, String>) {
        if (element.type == ChatElementTypes.CHAT_MESSAGE.ordinal) {
            element.data?.let { chatElement ->
                var chatMessage = chatElement.data as ChatMessage
                if (payload.containsKey("parentMessage")) {
                    chatMessage = chatMessage.parentMessage!!
                }


                PopupMenu(this.context, holder.itemView, Gravity.START).apply {
                    setOnMenuItemClickListener { item ->
                        when (item?.itemId) {

                            R.id.action_copy_message -> {
                                val clipboardManager =
                                        activity?.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clipData = ClipData.newPlainText(resources?.getString(R.string.nc_app_name), chatMessage.text)
                                clipboardManager.setPrimaryClip(clipData)
                                true
                            }
                            R.id.action_reply_to_message -> {
                                showReplyView(chatMessage)
                                true
                            }
                            else -> false
                        }
                    }
                    inflate(R.menu.chat_message_menu)
                    menu.findItem(R.id.action_reply_to_message).isVisible = chatMessage.replyable
                    show()
                }
            }
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        viewModel.view = this
        user = bundle.getParcelable(BundleKeys.KEY_USER)!!
        conversationToken = bundle.getString(BundleKeys.KEY_CONVERSATION_TOKEN)!!
        conversationPassword = bundle.getString(KEY_CONVERSATION_PASSWORD)
        viewModel.user = user
        viewModel.conversationPassword = conversationPassword
        setupViews()
        toolbar?.setOnClickListener(toolbarOnClickListener)
        viewModel.joinConversation(user, conversationToken, conversationPassword)
    }

    override fun onDetach(view: View) {
        super.onDetach(view)
        viewModel.view = null
        viewModel.leaveConversation()
        toolbar?.setOnClickListener(null)
    }

    override fun onCreateOptionsMenu(
            menu: Menu,
            inflater: MenuInflater
    ) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_conversation, menu)
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
            with(view.popupBubbleView) {
                setRecyclerView(view.messagesRecyclerView)
                setPopupBubbleListener {
                    view.messagesRecyclerView.post {

                        view.messagesRecyclerView.smoothScrollToPosition(popupBubbleScrollPosition)
                        popupBubbleScrollPosition = 0
                        deactivate()

                    }
                }
            }

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
                if (!::mentionAutocomplete.isInitialized) {

                    val elevation = 6f
                    val backgroundDrawable = ColorDrawable(resources!!.getColor(R.color.bg_default))
                    val presenter = MentionAutocompletePresenter(activity as Context, conversation.token)
                    val callback = MentionAutocompleteCallback(
                            activity,
                            viewModel.user, view.messageInput
                    )

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
            val replyMessageId= view?.messageInputView?.quotedMessageLayout?.tag as Long?
            view?.messageInput?.setText("")
            viewModel.sendMessage(it, replyMessageId)
            if (replyMessageId != null) {
                hideReplyView()
            }
        }
    }

    private fun startACall(isVoiceOnlyCall: Boolean) {
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
        viewModel.conversation.value?.let {
            val bundle = Bundle()
            bundle.putString(KEY_CONVERSATION_TOKEN, it.token)
            bundle.putString(KEY_ROOM_ID, it.conversationId)
            bundle.putParcelable(KEY_USER_ENTITY, viewModel.user.toUserEntity())
            bundle.putString(KEY_CONVERSATION_PASSWORD, viewModel.conversationPassword)

            if (isVoiceOnlyCall) {
                bundle.putBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, true)
            }

            activity?.let {
                val callIntent = Intent(activity, MagicCallActivity::class.java)
                callIntent.putExtras(bundle)
                return callIntent
            }
        }

        return null
    }

    private fun showBrowserScreen(browserType: BrowserController.BrowserType) {
        viewModel.conversation.value?.let {
            val bundle = Bundle()
            bundle.putParcelable(KEY_BROWSER_TYPE, Parcels.wrap(browserType)
            )
            bundle.putParcelable(KEY_USER_ENTITY, viewModel.user)
            bundle.putString(KEY_CONVERSATION_TOKEN, it.token)
            router.pushController(
                    RouterTransaction.with(BrowserController(bundle))
                            .pushChangeHandler(VerticalChangeHandler())
                            .popChangeHandler(VerticalChangeHandler())
            )

        }
    }

    override fun getLayoutId(): Int {
        return R.layout.controller_chat
    }

    override fun getTitle(): String? {
        return viewModel.conversation.value?.displayName
    }

    override fun getImageLoader(): ImageLoader {
        return networkComponents.getImageLoader(viewModel.user)
    }

    override fun loadImage(imageView: ImageView, url: String, payload: MutableMap<String, String>?) {
        val imageLoader = networkComponents.getImageLoader(viewModel.user)

        imageLoader.load(activity as Context, url) {
            if (url.contains("/avatar/")) {
                transformations(CircleCropTransformation())
            } else {
                payload?.let {
                    if (payload.containsKey("mimetype")) {
                        placeholder(DrawableUtils.getDrawableResourceIdForMimeType(payload["mimetype"])
                        )
                    }
                }
            }

            target(imageView)
            val needsAuthBasedOnUrl = url.contains("index.php/core/preview?fileId=") || url.contains("index.php/avatar/")
            if (url.startsWith(viewModel.user.baseUrl) && needsAuthBasedOnUrl) {
                addHeader("Authorization", viewModel.user.getCredentials())
            }
        }
    }
}