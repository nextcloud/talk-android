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
import android.graphics.drawable.LayerDrawable
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
import com.nextcloud.talk.utils.ApiUtils
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.utils.FlexibleUtils
import eu.davidea.viewholders.FlexibleViewHolder
import kotlinx.android.synthetic.main.rv_item_conversation_with_last_message.view.*
import java.util.*
import java.util.regex.Pattern

class ConversationItem(
        val model: Conversation,
        private val user: UserNgEntity,
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
                    && user.id == inItem.user.id)
        }
        return false
    }

    override fun hashCode(): Int {
        return Objects.hash(
                model.conversationId, model.token,
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
            holder.itemView.actionProgressBar!!.visibility = View.VISIBLE
        } else {
            holder.itemView.actionProgressBar!!.visibility = View.GONE
        }

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(
                    holder.itemView.dialogName!!, model.displayName,
                    adapter.getFilter(String::class.java).toString(),
                    NextcloudTalkApplication.sharedApplication!!
                            .resources.getColor(R.color.colorPrimary)
            )
        } else {
            holder.itemView.dialogName!!.text = model.displayName
        }

        if (model.unreadMessages > 0) {
            holder.itemView.dialogUnreadBubble!!.visibility = View.VISIBLE
            if (model.unreadMessages < 100) {
                holder.itemView.dialogUnreadBubble!!.text = model.unreadMessages.toLong()
                        .toString()
            } else {
                holder.itemView.dialogUnreadBubble!!.text = context.getString(R.string.nc_99_plus)
            }

            if (model.unreadMention) {
                holder.itemView.dialogUnreadBubble!!.background =
                        context.getDrawable(R.drawable.bubble_circle_unread_mention)
            } else {
                holder.itemView.dialogUnreadBubble!!.background =
                        context.getDrawable(R.drawable.bubble_circle_unread)
            }
        } else {
            holder.itemView.dialogUnreadBubble!!.visibility = View.GONE
        }

        if (model.hasPassword) {
            holder.itemView.passwordProtectedRoomImageView!!.visibility = View.VISIBLE
        } else {
            holder.itemView.passwordProtectedRoomImageView!!.visibility = View.GONE
        }

        if (model.favorite) {
            holder.itemView.favoriteConversationImageView!!.visibility = View.VISIBLE
        } else {
            holder.itemView.favoriteConversationImageView!!.visibility = View.GONE
        }

        if (model.lastMessage != null) {
            holder.itemView.dialogDate!!.visibility = View.VISIBLE
            holder.itemView.dialogDate!!.text = DateUtils.getRelativeTimeSpanString(
                    model.lastActivity * 1000L,
                    System.currentTimeMillis(), 0, DateUtils.FORMAT_ABBREV_RELATIVE
            )

            if (!TextUtils.isEmpty(
                            model.lastMessage!!.systemMessage
                    ) || Conversation.ConversationType.SYSTEM_CONVERSATION == model.type
            ) {
                holder.itemView.dialogLastMessage!!.text = model.lastMessage!!.text
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

                holder.itemView.dialogLastMessage.text = text
            }
        } else {
            holder.itemView.dialogDate.visibility = View.GONE
            holder.itemView.dialogLastMessage.setText(R.string.nc_no_messages_yet)
        }

        holder.itemView.dialogAvatar.visibility = View.VISIBLE

        var shouldLoadAvatar = true
        val objectType: String? = model.objectType
        if (!TextUtils.isEmpty(objectType)) {
            when (objectType) {
                "share:password" -> {
                    shouldLoadAvatar = false
                    holder.itemView.dialogAvatar.load(R.drawable.ic_file_password_request) {
                        transformations(CircleCropTransformation())
                    }
                }
                "file" -> {
                    shouldLoadAvatar = false
                    holder.itemView.dialogAvatar.load(R.drawable.ic_file_icon) {
                        transformations(CircleCropTransformation())
                    }

                }
                else -> {
                }
            }
        }

        if (Conversation.ConversationType.SYSTEM_CONVERSATION == model.type) {
            val layers = arrayOfNulls<Drawable>(2)
            layers[0] = context.getDrawable(R.drawable.ic_launcher_background)
            layers[1] = context.getDrawable(R.drawable.ic_launcher_foreground)
            val layerDrawable = LayerDrawable(layers)

            holder.itemView.dialogAvatar.load(layerDrawable) {
                transformations(CircleCropTransformation())
            }

            shouldLoadAvatar = false
        }

        if (shouldLoadAvatar) {
            when (model.type) {
                ONE_TO_ONE_CONVERSATION -> if (!TextUtils.isEmpty(
                                model.name
                        )
                ) {
                    holder.itemView.dialogAvatar.load(
                            ApiUtils.getUrlForAvatarWithName(
                                    user.baseUrl,
                                    model.name, R.dimen.avatar_size
                            )
                    ) {
                        transformations(CircleCropTransformation())
                    }

                } else {
                    holder.itemView.dialogAvatar.visibility = View.GONE
                }
                Conversation.ConversationType.GROUP_CONVERSATION ->
                    holder.itemView.dialogAvatar.load(R.drawable.ic_people_group_white_24px) {
                        transformations(CircleCropTransformation())
                    }
                Conversation.ConversationType.PUBLIC_CONVERSATION ->
                    holder.itemView.dialogAvatar.load(R.drawable.ic_link_white_24px) {
                        transformations(CircleCropTransformation())
                    }
                else -> holder.itemView.dialogAvatar.visibility = View.GONE
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
    ) : FlexibleViewHolder(view, adapter)
}
