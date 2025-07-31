/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import autodagger.AutoInjector
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.databinding.ItemCustomIncomingLocationMessageBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.ChatMessageUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.UriUtils
import com.nextcloud.talk.utils.message.MessageUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class IncomingLocationMessageViewHolder(incomingView: View, payload: Any) :
    MessageHolders.IncomingTextMessageViewHolder<ChatMessage>(incomingView, payload) {
    private val binding: ItemCustomIncomingLocationMessageBinding =
        ItemCustomIncomingLocationMessageBinding.bind(itemView)

    var locationLon: String? = ""
    var locationLat: String? = ""
    var locationName: String? = ""
    var locationGeoLink: String? = ""

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var messageUtils: MessageUtils

    @Inject
    lateinit var dateUtils: DateUtils

    lateinit var commonMessageInterface: CommonMessageInterface

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        sharedApplication!!.componentApplication.inject(this)

        setAvatarAndAuthorOnMessageItem(message)

        colorizeMessageBubble(message)

        itemView.isSelected = false

        val textSize = context.resources!!.getDimension(R.dimen.chat_text_size)
        binding.messageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
        binding.messageText.text = message.text

        binding.messageTime.text = dateUtils.getLocalTimeStringFromTimestamp(message.timestamp)

        // parent message handling
        setParentMessageDataOnMessageItem(message)

        // geo-location
        setLocationDataOnMessageItem(message)

        Reaction().showReactions(
            message,
            ::clickOnReaction,
            ::longClickOnReaction,
            binding.reactions,
            binding.messageText.context,
            false,
            viewThemeUtils
        )
    }

    private fun longClickOnReaction(chatMessage: ChatMessage) {
        commonMessageInterface.onLongClickReactions(chatMessage)
    }

    private fun clickOnReaction(chatMessage: ChatMessage, emoji: String) {
        commonMessageInterface.onClickReaction(chatMessage, emoji)
    }

    private fun setAvatarAndAuthorOnMessageItem(message: ChatMessage) {
        val actorName = message.actorDisplayName
        if (!actorName.isNullOrBlank()) {
            binding.messageAuthor.visibility = View.VISIBLE
            binding.messageAuthor.text = actorName
            binding.messageUserAvatar.setOnClickListener {
                (payload as? MessagePayload)?.profileBottomSheet?.showFor(message, itemView.context)
            }
        } else {
            binding.messageAuthor.setText(R.string.nc_nick_guest)
        }

        if (!message.isGrouped && !message.isOneToOneConversation && !message.isFormerOneToOneConversation) {
            ChatMessageUtils().setAvatarOnMessage(binding.messageUserAvatar, message, viewThemeUtils)
        } else {
            if (message.isOneToOneConversation || message.isFormerOneToOneConversation) {
                binding.messageUserAvatar.visibility = View.GONE
            } else {
                binding.messageUserAvatar.visibility = View.INVISIBLE
            }
            binding.messageAuthor.visibility = View.GONE
        }
    }

    private fun colorizeMessageBubble(message: ChatMessage) {
        viewThemeUtils.talk.themeIncomingMessageBubble(bubble, message.isGrouped, message.isDeleted)
    }

    @Suppress("Detekt.TooGenericExceptionCaught", "Detekt.LongMethod")
    private fun setParentMessageDataOnMessageItem(message: ChatMessage) {
        if (message.parentMessageId != null && !message.isDeleted) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val chatActivity = commonMessageInterface as ChatActivity
                    val urlForChatting = ApiUtils.getUrlForChat(
                        chatActivity.chatApiVersion,
                        chatActivity.conversationUser?.baseUrl,
                        chatActivity.roomToken
                    )

                    val parentChatMessage = withContext(Dispatchers.IO) {
                        chatActivity.chatViewModel.getMessageById(
                            urlForChatting,
                            chatActivity.currentConversation!!,
                            message.parentMessageId!!
                        ).first()
                    }
                    parentChatMessage.activeUser = message.activeUser
                    parentChatMessage.imageUrl?.let {
                        binding.messageQuote.quotedMessageImage.visibility = View.VISIBLE
                        binding.messageQuote.quotedMessageImage.load(it) {
                            addHeader(
                                "Authorization",
                                ApiUtils.getCredentials(message.activeUser!!.username, message.activeUser!!.token)!!
                            )
                        }
                    } ?: run {
                        binding.messageQuote.quotedMessageImage.visibility = View.GONE
                    }
                    binding.messageQuote.quotedMessageAuthor.text = parentChatMessage.actorDisplayName
                        ?: context.getText(R.string.nc_nick_guest)
                    binding.messageQuote.quotedMessage.text = messageUtils
                        .enrichChatReplyMessageText(
                            binding.messageQuote.quotedMessage.context,
                            parentChatMessage,
                            true,
                            viewThemeUtils
                        )

                    binding.messageQuote.quotedMessageAuthor
                        .setTextColor(context.resources.getColor(R.color.textColorMaxContrast, null))

                    viewThemeUtils.talk.themeParentMessage(
                        parentChatMessage,
                        message,
                        binding.messageQuote.quoteColoredView
                    )

                    binding.messageQuote.quotedChatMessageView.visibility =
                        if (!message.isDeleted &&
                            message.parentMessageId != null &&
                            message.parentMessageId != chatActivity.conversationThreadId
                        ) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                } catch (e: Exception) {
                    Log.d(TAG, "Error when processing parent message in view holder", e)
                }
            }
        } else {
            binding.messageQuote.quotedChatMessageView.visibility = View.GONE
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setLocationDataOnMessageItem(message: ChatMessage) {
        if (message.messageParameters != null && message.messageParameters!!.size > 0) {
            for (key in message.messageParameters!!.keys) {
                val individualHashMap: Map<String?, String?> = message.messageParameters!![key]!!
                if (individualHashMap["type"] == "geo-location") {
                    locationLon = individualHashMap["longitude"]
                    locationLat = individualHashMap["latitude"]
                    locationName = individualHashMap["name"]
                    locationGeoLink = individualHashMap["id"]
                }
            }
        }

        binding.webview.settings.javaScriptEnabled = true

        binding.webview.webViewClient = object : WebViewClient() {
            @Deprecated("Use shouldOverrideUrlLoading(WebView view, WebResourceRequest request)")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                if (url != null && UriUtils.hasHttpProtocolPrefixed(url)) {
                    view?.context?.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    true
                } else {
                    false
                }
        }

        val urlStringBuffer = StringBuffer("file:///android_asset/leafletMapMessagePreview.html")
        urlStringBuffer.append(
            "?mapProviderUrl=" + URLEncoder.encode(context.getString(R.string.osm_tile_server_url))
        )
        urlStringBuffer.append(
            "&mapProviderAttribution=" + URLEncoder.encode(context.getString(R.string.osm_tile_server_attributation))
        )
        urlStringBuffer.append("&locationLat=" + URLEncoder.encode(locationLat))
        urlStringBuffer.append("&locationLon=" + URLEncoder.encode(locationLon))
        urlStringBuffer.append("&locationName=" + URLEncoder.encode(locationName))
        urlStringBuffer.append("&locationGeoLink=" + URLEncoder.encode(locationGeoLink))

        binding.webview.loadUrl(urlStringBuffer.toString())

        binding.webview.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_UP -> openGeoLink()
                }

                return v?.onTouchEvent(event) ?: true
            }
        })
    }

    private fun openGeoLink() {
        if (!locationGeoLink.isNullOrEmpty()) {
            val geoLinkWithMarker = addMarkerToGeoLink(locationGeoLink!!)
            val browserIntent = Intent(Intent.ACTION_VIEW, geoLinkWithMarker.toUri())
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(browserIntent)
        } else {
            Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
            Log.e(TAG, "locationGeoLink was null or empty")
        }
    }

    private fun addMarkerToGeoLink(locationGeoLink: String): String = locationGeoLink.replace("geo:", "geo:0,0?q=")

    fun assignCommonMessageInterface(commonMessageInterface: CommonMessageInterface) {
        this.commonMessageInterface = commonMessageInterface
    }

    companion object {
        private const val TAG = "LocInMessageView"
    }
}
