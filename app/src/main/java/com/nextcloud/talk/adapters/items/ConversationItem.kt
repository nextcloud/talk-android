/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * @author Tim Krüger
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.ConversationItem.ConversationItemViewHolder
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemConversationWithLastMessageBinding
import com.nextcloud.talk.extensions.loadAvatar
import com.nextcloud.talk.extensions.loadGroupCallAvatar
import com.nextcloud.talk.extensions.loadPublicCallAvatar
import com.nextcloud.talk.extensions.loadSystemAvatar
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.Conversation.ConversationType
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew.hasSpreedFeatureCapability
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.items.ISectionable
import eu.davidea.viewholders.FlexibleViewHolder
import java.util.regex.Pattern

class ConversationItem(
    val model: Conversation,
    private val user: User,
    private val context: Context,
    private val viewThemeUtils: ViewThemeUtils
) : AbstractFlexibleItem<ConversationItemViewHolder>(),
    ISectionable<ConversationItemViewHolder, GenericTextHeaderItem?>,
    IFilterable<String?> {
    private var header: GenericTextHeaderItem? = null

    constructor(
        conversation: Conversation,
        user: User,
        activityContext: Context,
        genericTextHeaderItem: GenericTextHeaderItem?,
        viewThemeUtils: ViewThemeUtils
    ) : this(conversation, user, activityContext, viewThemeUtils) {
        header = genericTextHeaderItem
    }

    override fun equals(other: Any?): Boolean {
        if (other is ConversationItem) {
            return model == other.model
        }
        return false
    }

    override fun hashCode(): Int {
        var result = model.hashCode()
        result *= 31
        return result
    }

    override fun getLayoutRes(): Int {
        return R.layout.rv_item_conversation_with_last_message
    }

    override fun getItemViewType(): Int {
        return VIEW_TYPE
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<IFlexible<*>?>?): ConversationItemViewHolder {
        return ConversationItemViewHolder(view, adapter)
    }

    @SuppressLint("SetTextI18n")
    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<*>?>,
        holder: ConversationItemViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        val appContext = sharedApplication!!.applicationContext
        holder.binding.dialogName.setTextColor(
            ResourcesCompat.getColor(
                context.resources,
                R.color.conversation_item_header,
                null
            )
        )
        if (adapter.hasFilter()) {
            viewThemeUtils.platform.highlightText(
                holder.binding.dialogName,
                model.displayName!!, adapter.getFilter(String::class.java).toString()
            )
        } else {
            holder.binding.dialogName.text = model.displayName
        }
        if (model.unreadMessages > 0) {
            showUnreadMessages(holder)
        } else {
            holder.binding.dialogName.setTypeface(null, Typeface.NORMAL)
            holder.binding.dialogDate.setTypeface(null, Typeface.NORMAL)
            holder.binding.dialogLastMessage.setTypeface(null, Typeface.NORMAL)
            holder.binding.dialogUnreadBubble.visibility = View.GONE
        }
        if (model.favorite) {
            holder.binding.favoriteConversationImageView.visibility = View.VISIBLE
        } else {
            holder.binding.favoriteConversationImageView.visibility = View.GONE
        }
        if (ConversationType.ROOM_SYSTEM !== model.type) {
            val size = DisplayUtils.convertDpToPixel(STATUS_SIZE_IN_DP, appContext)
            holder.binding.userStatusImage.visibility = View.VISIBLE
            holder.binding.userStatusImage.setImageDrawable(
                StatusDrawable(
                    model.status,
                    model.statusIcon,
                    size,
                    context.resources.getColor(R.color.bg_default),
                    appContext
                )
            )
        } else {
            holder.binding.userStatusImage.visibility = View.GONE
        }
        setLastMessage(holder, appContext)
        showAvatar(holder)
    }

    private fun showAvatar(holder: ConversationItemViewHolder) {
        holder.binding.dialogAvatar.visibility = View.VISIBLE
        var shouldLoadAvatar = shouldLoadAvatar(holder)
        if (ConversationType.ROOM_SYSTEM == model.type) {
            holder.binding.dialogAvatar.loadSystemAvatar()
            shouldLoadAvatar = false
        }
        if (shouldLoadAvatar) {
            when (model.type) {
                ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL -> if (!TextUtils.isEmpty(model.name)) {
                    holder.binding.dialogAvatar.loadAvatar(user, model.name!!)
                } else {
                    holder.binding.dialogAvatar.visibility = View.GONE
                }
                ConversationType.ROOM_GROUP_CALL ->
                    holder.binding.dialogAvatar.loadGroupCallAvatar(viewThemeUtils)
                ConversationType.ROOM_PUBLIC_CALL ->
                    holder.binding.dialogAvatar.loadPublicCallAvatar(viewThemeUtils)
                else -> holder.binding.dialogAvatar.visibility = View.GONE
            }
        }
    }

    private fun shouldLoadAvatar(
        holder: ConversationItemViewHolder
    ): Boolean {
        var objectType: String?
        var returnValue = true
        if (!TextUtils.isEmpty(model.objectType.also { objectType = it })) {
            when (objectType) {
                "share:password" -> {
                    holder.binding.dialogAvatar.setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_circular_lock
                        )
                    )
                    returnValue = false
                }
                "file" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        holder.binding.dialogAvatar.loadAvatar(
                            viewThemeUtils.talk.themePlaceholderAvatar(
                                holder.binding.dialogAvatar,
                                R.drawable.ic_avatar_document
                            )
                        )
                    } else {
                        holder.binding.dialogAvatar.loadAvatar(
                            R.drawable.ic_circular_document
                        )
                    }
                    returnValue = false
                }
            }
        }
        return returnValue
    }

    private fun setLastMessage(
        holder: ConversationItemViewHolder,
        appContext: Context
    ) {
        if (model.lastMessage != null) {
            holder.binding.dialogDate.visibility = View.VISIBLE
            holder.binding.dialogDate.text = DateUtils.getRelativeTimeSpanString(
                model.lastActivity * MILLIES,
                System.currentTimeMillis(),
                0,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            if (!TextUtils.isEmpty(model.lastMessage!!.systemMessage) ||
                ConversationType.ROOM_SYSTEM === model.type
            ) {
                holder.binding.dialogLastMessage.text = model.lastMessage!!.text
            } else {
                model.lastMessage!!.activeUser = user

                val text = if (model.lastMessage!!.getCalculateMessageType() === ChatMessage.MessageType
                    .REGULAR_TEXT_MESSAGE
                ) {
                    calculateRegularLastMessageText(appContext)
                } else {
                    model.lastMessage!!.lastMessageDisplayText
                }
                holder.binding.dialogLastMessage.text = text
            }
        } else {
            holder.binding.dialogDate.visibility = View.GONE
            holder.binding.dialogLastMessage.setText(R.string.nc_no_messages_yet)
        }
    }

    private fun calculateRegularLastMessageText(appContext: Context): String {
        return if (model.lastMessage!!.actorId == user.userId) {
            String.format(
                appContext.getString(R.string.nc_formatted_message_you),
                model.lastMessage!!.lastMessageDisplayText
            )
        } else {
            val authorDisplayName =
                if (!TextUtils.isEmpty(model.lastMessage!!.actorDisplayName)) {
                    model.lastMessage!!.actorDisplayName
                } else if ("guests" == model.lastMessage!!.actorType) {
                    appContext.getString(R.string.nc_guest)
                } else {
                    ""
                }
            String.format(
                appContext.getString(R.string.nc_formatted_message),
                authorDisplayName,
                model.lastMessage!!.lastMessageDisplayText
            )
        }
    }

    private fun showUnreadMessages(holder: ConversationItemViewHolder) {
        holder.binding.dialogName.setTypeface(holder.binding.dialogName.typeface, Typeface.BOLD)
        holder.binding.dialogLastMessage.setTypeface(holder.binding.dialogLastMessage.typeface, Typeface.BOLD)
        holder.binding.dialogUnreadBubble.visibility = View.VISIBLE
        if (model.unreadMessages < UNREAD_MESSAGES_TRESHOLD) {
            holder.binding.dialogUnreadBubble.text = model.unreadMessages.toLong().toString()
        } else {
            holder.binding.dialogUnreadBubble.setText(R.string.tooManyUnreadMessages)
        }
        val lightBubbleFillColor = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                R.color.conversation_unread_bubble
            )
        )
        val lightBubbleTextColor = ContextCompat.getColor(
            context,
            R.color.conversation_unread_bubble_text
        )
        if (model.type === ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
            viewThemeUtils.material.colorChipBackground(holder.binding.dialogUnreadBubble)
        } else if (model.unreadMention) {
            if (hasSpreedFeatureCapability(user, "direct-mention-flag")) {
                if (model.unreadMentionDirect!!) {
                    viewThemeUtils.material.colorChipBackground(holder.binding.dialogUnreadBubble)
                } else {
                    viewThemeUtils.material.colorChipOutlined(
                        holder.binding.dialogUnreadBubble,
                        UNREAD_BUBBLE_STROKE_WIDTH
                    )
                }
            } else {
                viewThemeUtils.material.colorChipBackground(holder.binding.dialogUnreadBubble)
            }
        } else {
            holder.binding.dialogUnreadBubble.chipBackgroundColor = lightBubbleFillColor
            holder.binding.dialogUnreadBubble.setTextColor(lightBubbleTextColor)
        }
    }

    override fun filter(constraint: String?): Boolean {
        return model.displayName != null &&
            Pattern
                .compile(constraint!!, Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
                .matcher(model.displayName!!.trim { it <= ' ' })
                .find()
    }

    override fun getHeader(): GenericTextHeaderItem? {
        return header
    }

    override fun setHeader(header: GenericTextHeaderItem?) {
        this.header = header
    }

    class ConversationItemViewHolder(view: View?, adapter: FlexibleAdapter<*>?) : FlexibleViewHolder(view, adapter) {
        var binding: RvItemConversationWithLastMessageBinding

        init {
            binding = RvItemConversationWithLastMessageBinding.bind(view!!)
        }
    }

    companion object {
        const val VIEW_TYPE = R.layout.rv_item_conversation_with_last_message
        private const val MILLIES = 1000L
        private const val STATUS_SIZE_IN_DP = 9f
        private const val UNREAD_BUBBLE_STROKE_WIDTH = 6.0f
        private const val UNREAD_MESSAGES_TRESHOLD = 1000
    }
}
