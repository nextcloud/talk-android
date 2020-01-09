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

package com.nextcloud.talk.newarch.features.conversationsList

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import coil.api.load
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.services.GlobalService
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.utils.ApiUtils
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Presenter
import kotlinx.android.synthetic.main.rv_item_conversation_with_last_message.view.*
import org.koin.core.KoinComponent
import org.koin.core.inject

open class ConversationsPresenter(context: Context, onElementClick: ((Page, Holder, Element<Conversation>) -> Unit)?, private val onElementLongClick: ((Page, Holder, Element<Conversation>) -> Unit)?) : Presenter<Conversation>(context, onElementClick), KoinComponent {
    private val globalService: GlobalService by inject()

    override val elementTypes: Collection<Int>
        get() = listOf(0)

    override fun onCreate(parent: ViewGroup, elementType: Int): Holder {
        return Holder(getLayoutInflater().inflate(R.layout.rv_item_conversation_with_last_message, parent, false))
    }

    override fun onBind(page: Page, holder: Holder, element: Element<Conversation>, payloads: List<Any>) {
        super.onBind(page, holder, element, payloads)

        holder.itemView.setOnLongClickListener {
            onElementLongClick?.invoke(page, holder, element)
            true
        }

        val conversation = element.data
        val user = globalService.currentUserLiveData.value

        user?.let { user ->
            conversation?.let { conversation ->
                holder.itemView.actionProgressBar.isVisible = conversation.changing
                holder.itemView.dialogName!!.text = conversation.displayName

                if (conversation.unreadMessages > 0) {
                    holder.itemView.dialogUnreadBubble!!.visibility = View.VISIBLE
                    if (conversation.unreadMessages < 100) {
                        holder.itemView.dialogUnreadBubble!!.text = conversation.unreadMessages.toLong()
                                .toString()
                    } else {
                        holder.itemView.dialogUnreadBubble!!.text = context.getString(R.string.nc_99_plus)
                    }

                    if (conversation.unreadMention) {
                        holder.itemView.dialogUnreadBubble!!.background =
                                context.getDrawable(R.drawable.bubble_circle_unread_mention)
                    } else {
                        holder.itemView.dialogUnreadBubble!!.background =
                                context.getDrawable(R.drawable.bubble_circle_unread)
                    }
                } else {
                    holder.itemView.dialogUnreadBubble!!.visibility = View.GONE
                }

                holder.itemView.passwordProtectedRoomImageView.isVisible = conversation.hasPassword
                holder.itemView.favoriteConversationImageView.isVisible = conversation.favorite

                if (conversation.lastMessage != null) {
                    holder.itemView.dialogDate!!.visibility = View.VISIBLE
                    holder.itemView.dialogDate!!.text = DateUtils.getRelativeTimeSpanString(
                            conversation.lastActivity * 1000L,
                            System.currentTimeMillis(), 0, DateUtils.FORMAT_ABBREV_RELATIVE
                    )

                    if (!TextUtils.isEmpty(
                                    conversation.lastMessage!!.systemMessage
                            ) || Conversation.ConversationType.SYSTEM_CONVERSATION == conversation.type
                    ) {
                        holder.itemView.dialogLastMessage!!.text = conversation.lastMessage!!.text
                    } else {
                        var authorDisplayName = ""
                        conversation.lastMessage!!.activeUser = user
                        val text: String
                        if (conversation.lastMessage!!
                                        .messageType == ChatMessage.MessageType.REGULAR_TEXT_MESSAGE && (!(Conversation.ConversationType.ONE_TO_ONE_CONVERSATION).equals(
                                        conversation.type) || conversation.lastMessage!!.actorId == user.userId)
                        ) {
                            if (conversation.lastMessage!!.actorId == user.userId) {
                                text = String.format(
                                        context.getString(R.string.nc_formatted_message_you),
                                        conversation.lastMessage!!.lastMessageDisplayText
                                )
                            } else {
                                authorDisplayName = if (!TextUtils.isEmpty(conversation.lastMessage!!.actorDisplayName))
                                    conversation.lastMessage!!.actorDisplayName
                                else if ("guests" == conversation.lastMessage!!.actorType)
                                    context.getString(R.string.nc_guest)
                                else
                                    ""
                                text = String.format(
                                        context.getString(R.string.nc_formatted_message),
                                        authorDisplayName,
                                        conversation.lastMessage!!.lastMessageDisplayText
                                )
                            }
                        } else {
                            text = conversation.lastMessage!!.lastMessageDisplayText
                        }

                        holder.itemView.dialogLastMessage.text = text
                    }
                } else {
                    holder.itemView.dialogDate.visibility = View.GONE
                    holder.itemView.dialogLastMessage.setText(R.string.nc_no_messages_yet)
                }

                val conversationDrawable: Drawable? = Images().getImageForConversation(context, conversation)

                conversationDrawable?.let {
                    holder.itemView.dialogAvatar.load(conversationDrawable)
                } ?: run {
                    holder.itemView.dialogAvatar.load(ApiUtils.getUrlForAvatarWithName(
                            user.baseUrl,
                            conversation.name, R.dimen.avatar_size))
                    {
                        addHeader("Authorization", user.getCredentials())
                        transformations(CircleCropTransformation())
                    }
                }
            }
        }
    }
}