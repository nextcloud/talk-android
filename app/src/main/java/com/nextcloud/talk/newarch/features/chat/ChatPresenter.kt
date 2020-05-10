package com.nextcloud.talk.newarch.features.chat

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.api.loadAny
import coil.api.newLoadBuilder
import com.amulyakhare.textdrawable.TextDrawable
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.newarch.features.chat.interfaces.ImageLoaderInterface
import com.nextcloud.talk.newarch.local.models.other.ChatMessageStatus
import com.nextcloud.talk.newarch.utils.dp
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.DrawableUtils.getDrawableResourceIdForMimeType
import com.nextcloud.talk.utils.TextMatchers
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import com.otaliastudios.elements.extensions.HeaderSource
import com.stfalcon.chatkit.utils.DateFormatter
import kotlinx.android.synthetic.main.item_message_quote.view.*
import kotlinx.android.synthetic.main.rv_chat_item.view.*
import kotlinx.android.synthetic.main.rv_chat_system_item.view.*
import kotlinx.android.synthetic.main.rv_date_and_unread_notice_item.view.*
import org.koin.core.KoinComponent

open class ChatPresenter<T : Any>(context: Context, private val onElementClickPass: ((Page, Holder, Element<T>, Map<String, String>) -> Unit)?, private val onElementLongClick: ((Page, Holder, Element<T>, Map<String, String>) -> Unit)?, private val imageLoader: ImageLoaderInterface) : Presenter<T>(context), KoinComponent {
    override val elementTypes: Collection<Int>
        get() = listOf(ChatElementTypes.SYSTEM_MESSAGE.ordinal, ChatElementTypes.UNREAD_MESSAGE_NOTICE.ordinal, ChatElementTypes.DATE_HEADER.ordinal, ChatElementTypes.CHAT_MESSAGE.ordinal)

    override fun onCreate(parent: ViewGroup, elementType: Int): Holder {
        return when (elementType) {
            ChatElementTypes.CHAT_MESSAGE.ordinal -> {
                Holder(getLayoutInflater().inflate(R.layout.rv_chat_item, parent, false))
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
            onElementLongClick?.invoke(page, holder, element, mapOf())
            true
        }

        var chatElement: ChatElement? = null
        var chatMessage: ChatMessage? = null

        if (element.data is ChatElement) {
            chatElement = element.data as ChatElement
            chatMessage = chatElement.data as ChatMessage?
        }

        when {
            chatMessage != null -> {
                val elementType = chatElement!!.elementType
                chatMessage.let {
                    if (elementType == ChatElementTypes.CHAT_MESSAGE) {
                        var shouldShowNameAndAvatar = true
                        val previousElement = getAdapter().elementAt(holder.adapterPosition - 1)
                        val isOutgoingMessage = it.actorId == it.activeUser?.userId
                        var isGrouped = false
                        if (isOutgoingMessage) {
                            shouldShowNameAndAvatar = false
                        }

                        if (previousElement != null && previousElement.element.data != null && previousElement.element.data is ChatElement) {
                            val previousChatElement = previousElement.element.data as ChatElement
                            if (previousChatElement.elementType == ChatElementTypes.CHAT_MESSAGE) {
                                val previousChatMessage = previousChatElement.data as ChatMessage
                                if (previousChatMessage.actorId == it.actorId) {
                                    shouldShowNameAndAvatar = false
                                    isGrouped = true
                                }
                            }
                        }

                        holder.itemView.messageTime?.text = DateFormatter.format(it.createdAt, DateFormatter.Template.TIME)
                        holder.itemView.sendingProgressBar.isVisible = it.chatMessageStatus != ChatMessageStatus.RECEIVED && it.chatMessageStatus != ChatMessageStatus.FAILED
                        holder.itemView.failedToSendNotice.isVisible = it.chatMessageStatus == ChatMessageStatus.FAILED
                        holder.itemView.chatMessage.text = it.text
                        if (TextMatchers.isMessageWithSingleEmoticonOnly(it.text)) {
                            holder.itemView.chatMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                        } else {
                            holder.itemView.chatMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                        }

                        if (shouldShowNameAndAvatar) {
                            holder.itemView.authorLayout.isVisible = true
                            holder.itemView.authorName.isVisible = true

                            holder.itemView.authorName?.text = if (it.user.name.isNotEmpty()) it.user.name else context.resources.getText(R.string.nc_guest)
                            if (it.actorType == "bots" && it.actorId == "changelog") {
                                val layers = arrayOfNulls<Drawable>(2)
                                layers[0] = context.getDrawable(R.drawable.ic_launcher_background)
                                layers[1] = context.getDrawable(R.drawable.ic_launcher_foreground)
                                val layerDrawable = LayerDrawable(layers)
                                val loadBuilder = imageLoader.getImageLoader().newLoadBuilder(context).target(holder.itemView.authorAvatar).data(DisplayUtils.getRoundedDrawable(layerDrawable))
                                imageLoader.getImageLoader().load(loadBuilder.build())
                            } else if (it.actorType == "bots") {
                                val drawable = TextDrawable.builder()
                                        .beginConfig()
                                        .width(24.dp)
                                        .height(24.dp)
                                        .bold()
                                        .endConfig()
                                        .buildRect(
                                                ">_",
                                                context.resources.getColor(R.color.black)
                                        )
                                holder.itemView.authorAvatar.loadAny(drawable, imageLoader.getImageLoader())
                            } else {
                                imageLoader.loadImage(holder.itemView.authorAvatar, it.user.avatar)
                            }
                        } else {
                            holder.itemView.authorLayout.isVisible = false
                            holder.itemView.authorName.isVisible = false
                        }

                        val messageLayoutParams = holder.itemView.messageLayout.layoutParams as RelativeLayout.LayoutParams

                        if (isOutgoingMessage) {
                            messageLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_END, 1)
                            messageLayoutParams.marginStart = 40.dp
                        } else {
                            messageLayoutParams.removeRule(RelativeLayout.ALIGN_PARENT_END)
                            messageLayoutParams.marginEnd = 40.dp
                        }


                        if (isGrouped) {

                            if (isOutgoingMessage) {
                                messageLayoutParams.marginEnd = 8.dp
                                holder.itemView.messageLayout.background = context.resources.getDrawable(R.drawable.outgoing_grouped_message_background)
                            } else {
                                messageLayoutParams.marginStart = 40.dp
                                holder.itemView.messageLayout.background = context.resources.getDrawable(R.drawable.incoming_grouped_message_background)
                            }
                            holder.itemView.messageLayout.layoutParams = messageLayoutParams

                        } else {

                            if (isOutgoingMessage) {
                                messageLayoutParams.marginEnd = 8.dp
                                holder.itemView.messageLayout.background = context.resources.getDrawable(R.drawable.outgoing_message_background)
                            } else {
                                messageLayoutParams.marginStart = 0
                                holder.itemView.messageLayout.background = context.resources.getDrawable(R.drawable.incoming_message_background)
                            }

                            holder.itemView.messageLayout.layoutParams = messageLayoutParams
                        }

                        it.parentMessage?.let { parentMessage ->
                            holder.itemView.quotedMessageLayout.isVisible = true

                            holder.itemView.quotedMessageLayout.setOnClickListener {
                                onElementLongClick?.invoke(page, holder, element, mapOf("parentMessage" to "yes"))
                                true
                            }

                            holder.itemView.quoteColoredView.setBackgroundResource(R.color.colorPrimary)
                            holder.itemView.quotedPreviewImage.setOnClickListener {
                                onElementClickPass?.invoke(page, holder, element, mapOf("parentMessage" to "yes"))
                                true
                            }

                            parentMessage.imageUrl?.let { previewMessageUrl ->
                                if (previewMessageUrl == "no-preview") {
                                    if (parentMessage.selectedIndividualHashMap?.containsKey("mimetype") == true) {
                                        holder.itemView.quotedPreviewImage.visibility = View.VISIBLE
                                        imageLoader.getImageLoader().loadAny(context, getDrawableResourceIdForMimeType(parentMessage.selectedIndividualHashMap!!["mimetype"])) {
                                            target(holder.itemView.quotedPreviewImage)
                                        }
                                    } else {
                                        holder.itemView.quotedPreviewImage.visibility = View.GONE
                                    }
                                } else {
                                    holder.itemView.quotedPreviewImage.visibility = View.VISIBLE
                                    val mutableMap = mutableMapOf<String, String>()
                                    if (parentMessage.selectedIndividualHashMap?.containsKey("mimetype") == true) {
                                        mutableMap["mimetype"] = parentMessage.selectedIndividualHashMap!!["mimetype"]!!
                                    }

                                    imageLoader.loadImage(holder.itemView.quotedPreviewImage, previewMessageUrl, mutableMap)
                                }
                            } ?: run {
                                holder.itemView.quotedPreviewImage.visibility = View.GONE
                            }

                            imageLoader.loadImage(holder.itemView.quotedUserAvatar, parentMessage.user.avatar)
                            holder.itemView.quotedAuthor.text = if (parentMessage.user.name.isNotEmpty()) parentMessage.user.name else context.resources.getText(R.string.nc_guest)
                            holder.itemView.quotedChatText.text = parentMessage.text
                            holder.itemView.quotedMessageTime?.text = DateFormatter.format(it.createdAt, DateFormatter.Template.TIME)
                            if (isOutgoingMessage) {
                                holder.itemView.quoteColoredView.setBackgroundColor(context.resources.getColor(R.color.bg_message_list_incoming_bubble))
                            } else {
                                holder.itemView.quoteColoredView.setBackgroundColor(context.resources.getColor(R.color.bg_message_list_outcoming_bubble))
                            }
                        } ?: run {
                            holder.itemView.quotedMessageLayout.isVisible = false
                        }

                        it.imageUrl?.let { imageUrl ->
                            holder.itemView.previewImage.setOnClickListener {
                                onElementClickPass?.invoke(page, holder, element, emptyMap())
                                true
                            }

                            if (imageUrl == "no-preview") {
                                if (it.selectedIndividualHashMap?.containsKey("mimetype") == true) {
                                    holder.itemView.previewImage.visibility = View.VISIBLE
                                    imageLoader.getImageLoader().loadAny(context, getDrawableResourceIdForMimeType(it.selectedIndividualHashMap!!["mimetype"])) {
                                        target(holder.itemView.previewImage)
                                    }
                                } else {
                                    holder.itemView.previewImage.visibility = View.GONE
                                }
                            } else {
                                holder.itemView.previewImage.visibility = View.VISIBLE
                                val mutableMap = mutableMapOf<String, String>()
                                if (it.selectedIndividualHashMap?.containsKey("mimetype") == true) {
                                    mutableMap["mimetype"] = it.selectedIndividualHashMap!!["mimetype"]!!
                                }

                                imageLoader.loadImage(holder.itemView.previewImage, imageUrl, mutableMap)
                            }
                        } ?: run {
                            holder.itemView.previewImage.visibility = View.GONE
                        }

                    } else {
                        holder.itemView.systemMessageText.text = it.text
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
