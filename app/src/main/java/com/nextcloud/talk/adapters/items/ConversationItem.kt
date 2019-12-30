/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.adapters.items

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.View
import coil.api.load
import coil.transform.CircleCropTransformation
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.Conversation.ConversationType.ONE_TO_ONE_CONVERSATION
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.utils.Images
import com.nextcloud.talk.utils.ApiUtils
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.utils.FlexibleUtils
import eu.davidea.viewholders.FlexibleViewHolder
import kotlinx.android.extensions.LayoutContainer
import kotlinx.android.synthetic.main.rv_item_conversation_with_last_message.*
import java.util.*
import java.util.regex.Pattern

class ConversationItem(
        val model: Conversation,
        val user: UserNgEntity,
        private val context: Context
) : AbstractFlexibleItem<ConversationItem.ConversationItemViewHolder>(), IFilterable<String> {

    override fun equals(other: Any?): Boolean {
        if (other is ConversationItem) {
            val inItem = other as ConversationItem?
            val comparedConversation = inItem!!.model

            return (model.conversationId == comparedConversation.conversationId
                    && model.token == comparedConversation.token
                    && model.name == comparedConversation.name
                    && model.displayName == comparedConversation.displayName
                    && model.type == comparedConversation.type
                    && model.lastMessage == comparedConversation.lastMessage
                    && model.favorite == comparedConversation.favorite
                    && model.hasPassword == comparedConversation.hasPassword
                    && model.unreadMessages == comparedConversation.unreadMessages
                    && model.unreadMention == comparedConversation.unreadMention
                    && model.objectType == comparedConversation.objectType
                    && model.changing == comparedConversation.changing
                    && inItem.user.id == user.id)
        }
        return false
    }

    override fun hashCode(): Int {
        return Objects.hash(
                model.token,
                user.id
        )
    }

    override fun getLayoutRes(): Int {
        return R.layout.rv_item_conversation_with_last_message
    }

    override fun createViewHolder(
            view: View,
            adapter: FlexibleAdapter<IFlexible<*>>
    ): ConversationItemViewHolder {
        return ConversationItemViewHolder(view, adapter)
    }

    override fun bindViewHolder(
            adapter: FlexibleAdapter<IFlexible<*>>,
            holder: ConversationItemViewHolder,
            position: Int,
            payloads: List<Any>
    ) {
        val appContext = NextcloudTalkApplication.sharedApplication!!.applicationContext

        if (model.changing) {
            holder.actionProgressBar!!.visibility = View.VISIBLE
        } else {
            holder.actionProgressBar!!.visibility = View.GONE
        }

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(
                    holder.dialogName!!, model.displayName,
                    adapter.getFilter(String::class.java).toString(),
                    NextcloudTalkApplication.sharedApplication!!
                            .resources.getColor(R.color.colorPrimary)
            )
        } else {
            holder.dialogName!!.text = model.displayName
        }

        if (model.unreadMessages > 0) {
            holder.dialogUnreadBubble!!.visibility = View.VISIBLE
            if (model.unreadMessages < 100) {
                holder.dialogUnreadBubble!!.text = model.unreadMessages.toLong()
                        .toString()
            } else {
                holder.dialogUnreadBubble!!.text = context.getString(R.string.nc_99_plus)
            }

            if (model.unreadMention) {
                holder.dialogUnreadBubble!!.background =
                        context.getDrawable(R.drawable.bubble_circle_unread_mention)
            } else {
                holder.dialogUnreadBubble!!.background =
                        context.getDrawable(R.drawable.bubble_circle_unread)
            }
        } else {
            holder.dialogUnreadBubble!!.visibility = View.GONE
        }

        if (model.hasPassword) {
            holder.passwordProtectedRoomImageView!!.visibility = View.VISIBLE
        } else {
            holder.passwordProtectedRoomImageView!!.visibility = View.GONE
        }

        if (model.favorite) {
            holder.favoriteConversationImageView!!.visibility = View.VISIBLE
        } else {
            holder.favoriteConversationImageView!!.visibility = View.GONE
        }

        if (model.lastMessage != null) {
            holder.dialogDate!!.visibility = View.VISIBLE
            holder.dialogDate!!.text = DateUtils.getRelativeTimeSpanString(
                    model.lastActivity * 1000L,
                    System.currentTimeMillis(), 0, DateUtils.FORMAT_ABBREV_RELATIVE
            )

            if (!TextUtils.isEmpty(
                            model.lastMessage!!.systemMessage
                    ) || Conversation.ConversationType.SYSTEM_CONVERSATION == model.type
            ) {
                holder.dialogLastMessage!!.text = model.lastMessage!!.text
            } else {
                var authorDisplayName = ""
                model.lastMessage!!.activeUser = user
                val text: String
                if (model.lastMessage!!
                                .messageType == ChatMessage.MessageType.REGULAR_TEXT_MESSAGE && (!(ONE_TO_ONE_CONVERSATION).equals(
                                model.type) || model.lastMessage!!.actorId == user.userId)
                ) {
                    if (model.lastMessage!!.actorId == user.userId) {
                        text = String.format(
                                appContext.getString(R.string.nc_formatted_message_you),
                                model.lastMessage!!.lastMessageDisplayText
                        )
                    } else {
                        authorDisplayName = if (!TextUtils.isEmpty(model.lastMessage!!.actorDisplayName))
                            model.lastMessage!!.actorDisplayName
                        else if ("guests" == model.lastMessage!!.actorType)
                            appContext.getString(R.string.nc_guest)
                        else
                            ""
                        text = String.format(
                                appContext.getString(R.string.nc_formatted_message),
                                authorDisplayName,
                                model.lastMessage!!.lastMessageDisplayText
                        )
                    }
                } else {
                    text = model.lastMessage!!.lastMessageDisplayText
                }

                holder.dialogLastMessage.text = text
            }
        } else {
            holder.dialogDate.visibility = View.GONE
            holder.dialogLastMessage.setText(R.string.nc_no_messages_yet)
        }

        val conversationDrawable: Drawable? = Images().getImageForConversation(context, model)
        if (conversationDrawable != null) {
            holder.dialogAvatar.load(conversationDrawable)
        } else {
            holder.dialogAvatar.load(
                    ApiUtils.getUrlForAvatarWithName(
                            user.baseUrl,
                            model.name, R.dimen.avatar_size
                    )
            ) {
                addHeader("Authorization", user.getCredentials())
                transformations(CircleCropTransformation())
            }
        }
    }

    override fun filter(constraint: String): Boolean {
        return model.displayName != null && Pattern.compile(
                constraint, Pattern.CASE_INSENSITIVE or Pattern.LITERAL
        )
                .matcher(model.displayName!!.trim { it <= ' ' })
                .find()
    }

    class ConversationItemViewHolder(
            view: View,
            adapter: FlexibleAdapter<*>
    ) : FlexibleViewHolder(view, adapter), LayoutContainer {
        override val containerView: View?
            get() = itemView
    }
}
