package com.nextcloud.talk.newarch.features.chat

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import coil.api.loadAny
import coil.api.newLoadBuilder
import com.amulyakhare.textdrawable.TextDrawable
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.newarch.features.chat.interfaces.ImageLoaderInterface
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.DrawableUtils.getDrawableResourceIdForMimeType
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import com.otaliastudios.elements.extensions.HeaderSource
import com.stfalcon.chatkit.utils.DateFormatter
import kotlinx.android.synthetic.main.item_message_quote.view.*
import kotlinx.android.synthetic.main.rv_chat_incoming_preview_item.view.*
import kotlinx.android.synthetic.main.rv_chat_incoming_text_item.view.*
import kotlinx.android.synthetic.main.rv_chat_incoming_text_item.view.messageUserAvatar
import kotlinx.android.synthetic.main.rv_chat_outgoing_preview_item.view.*
import kotlinx.android.synthetic.main.rv_chat_outgoing_text_item.view.*
import kotlinx.android.synthetic.main.rv_chat_system_item.view.*
import kotlinx.android.synthetic.main.rv_date_and_unread_notice_item.view.*
import org.koin.core.KoinComponent

open class ChatPresenter<T : Any>(context: Context, onElementClick: ((Page, Holder, Element<T>) -> Unit)?, private val onElementLongClick: ((Page, Holder, Element<T>) -> Unit)?, private val imageLoader: ImageLoaderInterface) : Presenter<T>(context, onElementClick), KoinComponent {
    override val elementTypes: Collection<Int>
        get() = listOf(ChatElementTypes.INCOMING_TEXT_MESSAGE.ordinal, ChatElementTypes.OUTGOING_TEXT_MESSAGE.ordinal, ChatElementTypes.INCOMING_PREVIEW_MESSAGE.ordinal, ChatElementTypes.OUTGOING_PREVIEW_MESSAGE.ordinal, ChatElementTypes.SYSTEM_MESSAGE.ordinal, ChatElementTypes.UNREAD_MESSAGE_NOTICE.ordinal, ChatElementTypes.DATE_HEADER.ordinal)

    override fun onCreate(parent: ViewGroup, elementType: Int): Holder {
        return when (elementType) {
            ChatElementTypes.INCOMING_TEXT_MESSAGE.ordinal -> {
                Holder(getLayoutInflater().inflate(R.layout.rv_chat_incoming_text_item, parent, false))
            }
            ChatElementTypes.OUTGOING_TEXT_MESSAGE.ordinal -> {
                Holder(getLayoutInflater().inflate(R.layout.rv_chat_outgoing_text_item, parent, false))
            }
            ChatElementTypes.INCOMING_PREVIEW_MESSAGE.ordinal -> {
                Holder(getLayoutInflater().inflate(R.layout.rv_date_and_unread_notice_item, parent, false))
            }
            ChatElementTypes.OUTGOING_PREVIEW_MESSAGE.ordinal -> {
                Holder(getLayoutInflater().inflate(R.layout.rv_date_and_unread_notice_item, parent, false))
            }
            ChatElementTypes.SYSTEM_MESSAGE.ordinal -> {
                Holder(getLayoutInflater().inflate(R.layout.rv_chat_system_item, parent, false))
            }
            else -> {
                Holder(getLayoutInflater().inflate(R.layout.rv_date_and_unread_notice_item, parent, false))
            }
        }
    }

    override fun onBind(page: Page, holder: Holder, element: Element<T>, payloads: List<Any>) {
        super.onBind(page, holder, element, payloads)

        holder.itemView.setOnLongClickListener {
            onElementLongClick?.invoke(page, holder, element)
            true
        }

        var chatElement: ChatElement?
        var chatMessage: ChatMessage? = null

        if (element.data is ChatElement) {
            chatElement = element.data as ChatElement
            chatMessage = chatElement.data as ChatMessage?
        }

        when {
            chatMessage != null -> {
                chatMessage.let {
                    if (element.type == ChatElementTypes.INCOMING_TEXT_MESSAGE.ordinal || element.type == ChatElementTypes.INCOMING_TEXT_MESSAGE.ordinal) {
                        holder.itemView.messageAuthor?.text = it.actorDisplayName
                        holder.itemView.messageUserAvatar?.isVisible = !it.grouped && !it.oneToOneConversation

                        if (element.type == ChatElementTypes.INCOMING_TEXT_MESSAGE.ordinal) {
                            holder.itemView.incomingMessageTime.text = DateFormatter.format(it.createdAt, DateFormatter.Template.TIME)
                            holder.itemView.incomingMessageText.text = it.text

                            if (it.actorType == "bots" && it.actorId == "changelog") {
                                holder.itemView.messageUserAvatar.isVisible = true
                                val layers = arrayOfNulls<Drawable>(2)
                                layers[0] = context.getDrawable(R.drawable.ic_launcher_background)
                                layers[1] = context.getDrawable(R.drawable.ic_launcher_foreground)
                                val layerDrawable = LayerDrawable(layers)
                                val loadBuilder = imageLoader.getImageLoader().newLoadBuilder(context).target(holder.itemView.messageUserAvatar).data(DisplayUtils.getRoundedDrawable(layerDrawable))
                                imageLoader.getImageLoader().load(loadBuilder.build())
                            } else if (it.actorType == "bots") {
                                holder.itemView.messageUserAvatar.isVisible = true
                                val drawable = TextDrawable.builder()
                                        .beginConfig()
                                        .bold()
                                        .endConfig()
                                        .buildRound(
                                                ">",
                                                context.resources.getColor(R.color.black)
                                        )
                                val loadBuilder = imageLoader.getImageLoader().newLoadBuilder(context).target(holder.itemView.messageUserAvatar).data(DisplayUtils.getRoundedDrawable(drawable))
                                imageLoader.getImageLoader().load(loadBuilder.build())
                            } else if (!it.grouped && !it.oneToOneConversation) {
                                holder.itemView.messageUserAvatar.isVisible = true
                                imageLoader.loadImage(holder.itemView.messageUserAvatar, it.user.avatar)
                            } else {
                                holder.itemView.messageUserAvatar.isVisible = false
                            }
                        } else {
                            holder.itemView.outgoingMessageTime.text = DateFormatter.format(it.createdAt, DateFormatter.Template.TIME)
                            holder.itemView.outgoingMessageText.text = it.text
                        }

                        it.parentMessage?.let { parentMessage ->
                            parentMessage.imageUrl?.let { previewMessageUrl ->
                                holder.itemView.quotedMessageImage.visibility = View.VISIBLE
                                imageLoader.loadImage(holder.itemView.quotedMessageImage, previewMessageUrl)
                            } ?: run {
                                holder.itemView.quotedMessageImage.visibility = View.GONE
                            }

                            holder.itemView.quotedMessageAuthor.text = parentMessage.actorDisplayName ?: context.getText(R.string.nc_nick_guest)
                            holder.itemView.quotedMessageAuthor.setTextColor(context.resources.getColor(R.color.colorPrimary))
                            holder.itemView.quoteColoredView.setBackgroundResource(R.color.colorPrimary)
                            holder.itemView.quotedChatMessageView.visibility = View.VISIBLE
                        } ?: run {
                            holder.itemView.quotedChatMessageView.visibility = View.GONE
                        }

                    } else if (element.type == ChatElementTypes.INCOMING_PREVIEW_MESSAGE.ordinal || element.type == ChatElementTypes.OUTGOING_PREVIEW_MESSAGE.ordinal) {
                        var previewAvailable = true
                        val mutableMap = mutableMapOf<String, String>()
                        if (it.selectedIndividualHashMap!!.containsKey("mimetype")) {
                            mutableMap.put("mimetype", it.selectedIndividualHashMap!!["mimetype"]!!)
                            if (it.imageUrl == "no-preview") {
                                previewAvailable = false
                                imageLoader.getImageLoader().loadAny(context, getDrawableResourceIdForMimeType(chatMessage.selectedIndividualHashMap!!["mimetype"]))
                            }
                        }

                        //  Before someone tells me parts of this can be refactored so there is less code:
                        // YES, I KNOW!
                        // But the way it's done now means pretty much anyone can understand it and it's easy
                        // to modify. Prefer simplicity over complexity wherever possible

                        if (element.type == ChatElementTypes.INCOMING_PREVIEW_MESSAGE.ordinal) {
                            if (previewAvailable) {
                                imageLoader.loadImage(holder.itemView.incomingPreviewImage, it.imageUrl!!)
                            }
                            if (!it.grouped && !it.oneToOneConversation) {
                                holder.itemView.messageUserAvatar.visibility = View.GONE
                            } else {
                                holder.itemView.messageUserAvatar.visibility = View.VISIBLE
                                imageLoader.loadImage(holder.itemView.messageUserAvatar, chatMessage.user.avatar)
                            }

                            when (it.messageType) {
                                ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE -> {
                                    holder.itemView.incomingPreviewMessageText.text = chatMessage.selectedIndividualHashMap!!["name"]
                                }
                                ChatMessage.MessageType.SINGLE_LINK_GIPHY_MESSAGE -> {
                                    holder.itemView.incomingPreviewMessageText.text = "GIPHY"
                                }
                                ChatMessage.MessageType.SINGLE_LINK_TENOR_MESSAGE -> {
                                    holder.itemView.incomingPreviewMessageText.text = "TENOR"
                                }
                                else -> {
                                    holder.itemView.incomingPreviewMessageText.text = ""
                                }
                            }

                            holder.itemView.incomingPreviewTime.text = DateFormatter.format(it.createdAt, DateFormatter.Template.TIME)
                        } else {
                            if (previewAvailable) {
                                imageLoader.loadImage(holder.itemView.incomingPreviewImage, it.imageUrl!!)
                            }

                            when (it.messageType) {
                                ChatMessage.MessageType.SINGLE_NC_ATTACHMENT_MESSAGE -> {
                                    holder.itemView.outgoingPreviewMessageText.text = chatMessage.selectedIndividualHashMap!!["name"]
                                }
                                ChatMessage.MessageType.SINGLE_LINK_GIPHY_MESSAGE -> {
                                    holder.itemView.outgoingPreviewMessageText.text = "GIPHY"
                                }
                                ChatMessage.MessageType.SINGLE_LINK_TENOR_MESSAGE -> {
                                    holder.itemView.outgoingPreviewMessageText.text = "TENOR"
                                }
                                else -> {
                                    holder.itemView.outgoingPreviewMessageText.text = ""
                                }
                            }

                            holder.itemView.outgoingPreviewTime.text = DateFormatter.format(it.createdAt, DateFormatter.Template.TIME)
                        }
                    } else {
                        // it's ChatElementTypes.SYSTEM_MESSAGE
                        holder.itemView.systemMessageText.text = chatMessage.text
                        holder.itemView.systemItemTime.text = DateFormatter.format(it.createdAt, DateFormatter.Template.TIME)
                    }
                }
            }
            element.type == ChatElementTypes.UNREAD_MESSAGE_NOTICE.ordinal -> {
                holder.itemView.noticeText.text = context.resources.getString(R.string.nc_new_messages)
            }
            else -> {
                // Date header
                holder.itemView.noticeText.text = (element.data as HeaderSource.Data<*, *>).header.toString()
            }
        }
    }
}
