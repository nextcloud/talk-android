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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.*
import android.widget.ImageView
import androidx.lifecycle.observe
import androidx.recyclerview.widget.LinearLayoutManager
import coil.ImageLoader
import coil.api.load
import coil.target.Target
import coil.transform.CircleCropTransformation
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.archlifecycle.ControllerLifecycleOwner
import com.bluelinelabs.conductor.autodispose.ControllerScopeProvider
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.nextcloud.talk.R
import com.nextcloud.talk.callbacks.MentionAutocompleteCallback
import com.nextcloud.talk.components.filebrowser.controllers.BrowserController
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.mention.Mention
import com.nextcloud.talk.newarch.features.chat.interfaces.ImageLoaderInterface
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.local.models.getMaxMessageLength
import com.nextcloud.talk.newarch.mvvm.BaseView
import com.nextcloud.talk.newarch.mvvm.ext.initRecyclerView
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.newarch.utils.NetworkComponents
import com.nextcloud.talk.presenters.MentionAutocompletePresenter
import com.nextcloud.talk.utils.*
import com.nextcloud.talk.utils.AccountUtils.canWeOpenFilesApp
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ACCOUNT
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_CONVERSATION_PASSWORD
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_FILE_ID
import com.nextcloud.talk.utils.text.Spans
import com.otaliastudios.autocomplete.Autocomplete
import com.otaliastudios.elements.Adapter
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import com.otaliastudios.elements.pagers.PageSizePager
import com.stfalcon.chatkit.messages.MessagesListAdapter
import com.uber.autodispose.lifecycle.LifecycleScopeProvider
import kotlinx.android.synthetic.main.controller_chat.view.*
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

    var conversationInfoMenuItem: MenuItem? = null
    var conversationVoiceCallMenuItem: MenuItem? = null
    var conversationVideoMenuItem: MenuItem? = null

    private lateinit var recyclerViewAdapter: MessagesListAdapter<ChatMessage>
    private lateinit var mentionAutocomplete: Autocomplete<*>

    private var shouldShowLobby: Boolean = false
    private var isReadOnlyConversation: Boolean = false

    private lateinit var messagesAdapter: Adapter

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        setHasOptionsMenu(true)
        actionBar?.show()
        viewModel = viewModelProvider(factory).get(ChatViewModel::class.java)
        val view = super.onCreateView(inflater, container)

        viewModel.init(bundle.getParcelable(BundleKeys.KEY_USER)!!, bundle.getString(BundleKeys.KEY_CONVERSATION_TOKEN)!!, bundle.getString(KEY_CONVERSATION_PASSWORD))

        messagesAdapter = Adapter.builder(this)
                .setPager(PageSizePager(80))
                //.addSource(ChatViewSource(itemsPerPage = 10))
                .addSource(ChatDateHeaderSource(activity as Context, ChatElementTypes.DATE_HEADER.ordinal))
                .addPresenter(Presenter.forLoadingIndicator(activity as Context, R.layout.loading_state))
                .addPresenter(ChatPresenter(activity as Context, ::onElementClick, ::onElementLongClick, this))
                .setAutoScrollMode(Adapter.AUTOSCROLL_POSITION_0, true)
                .into(view.messagesRecyclerView)

        viewModel.apply {
            conversation.observe(this@ChatView) { conversation ->
                setTitle()

                if (Conversation.ConversationType.ONE_TO_ONE_CONVERSATION == conversation?.type) {
                    loadAvatar()
                } else {
                    actionBar?.setIcon(null)
                }

                shouldShowLobby = conversation!!.shouldShowLobby(user)
                isReadOnlyConversation = conversation.conversationReadOnlyState == Conversation.ConversationReadOnlyState.CONVERSATION_READ_ONLY

                activity?.invalidateOptionsMenu()

                if (shouldShowLobby) {
                    view.messagesListView?.visibility = View.GONE
                    view.messageInputView?.visibility = View.GONE
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
                    view.messagesListView?.visibility = View.GONE
                    view.lobbyView?.visibility = View.GONE

                    if (isReadOnlyConversation) {
                        view.messageInputView?.visibility = View.GONE
                    } else {
                        view.messageInputView?.visibility = View.VISIBLE
                    }
                }
            }
        }
        return view
    }

    private fun onElementClick(page: Page, holder: Presenter.Holder, element: Element<ChatElement>) {
        if (element.type == ChatElementTypes.INCOMING_PREVIEW_MESSAGE.ordinal || element.type == ChatElementTypes.OUTGOING_PREVIEW_MESSAGE.ordinal) {
            element.data?.let { chatElement ->
                val chatMessage = chatElement.data as ChatMessage
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

    private fun onElementLongClick(page: Page, holder: Presenter.Holder, element: Element<ChatElement>) {

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
            view.messagesRecyclerView.initRecyclerView(
                    LinearLayoutManager(view.context), recyclerViewAdapter, false
            )

            view.popupBubbleView.setRecyclerView(view.messagesListView)

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
                    BundleKeys.KEY_BROWSER_TYPE, Parcels.wrap(browserType)
            )
            bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, viewModel.user)
            bundle.putString(BundleKeys.KEY_CONVERSATION_TOKEN, it.token)
            router.pushController(
                    RouterTransaction.with(BrowserController(bundle))
                            .pushChangeHandler(VerticalChangeHandler())
                            .popChangeHandler(VerticalChangeHandler())
            )

        }
    }

    private fun loadAvatar() {
        val imageLoader = networkComponents.getImageLoader(viewModel.user)
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