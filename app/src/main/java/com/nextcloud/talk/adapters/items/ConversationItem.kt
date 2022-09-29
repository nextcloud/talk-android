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
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.interfaces.DraweeController
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.ConversationItem.ConversationItemViewHolder
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemConversationWithLastMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.Conversation.ConversationType
import com.nextcloud.talk.ui.StatusDrawable
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
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
        holder.binding.dialogAvatar.controller = null
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
            holder.binding.dialogName.setTypeface(holder.binding.dialogName.typeface, Typeface.BOLD)
            holder.binding.dialogLastMessage.setTypeface(holder.binding.dialogLastMessage.typeface, Typeface.BOLD)
            holder.binding.dialogUnreadBubble.visibility = View.VISIBLE
            if (model.unreadMessages < 1000) {
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
                        viewThemeUtils.material.colorChipOutlined(holder.binding.dialogUnreadBubble, 6.0f)
                    }
                } else {
                    viewThemeUtils.material.colorChipBackground(holder.binding.dialogUnreadBubble)
                }
            } else {
                holder.binding.dialogUnreadBubble.chipBackgroundColor = lightBubbleFillColor
                holder.binding.dialogUnreadBubble.setTextColor(lightBubbleTextColor)
            }
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
        if (model != null && ConversationType.ROOM_SYSTEM !== model.type) {
            val size = DisplayUtils.convertDpToPixel(STATUS_SIZE_IN_DP, appContext)
            holder.binding.userStatusImage.visibility = View.VISIBLE
            holder.binding.userStatusImage.setImageDrawable(
                StatusDrawable(
                    model.status,
                    model.status,
                    size,
                    context.resources.getColor(R.color.bg_default),
                    appContext
                )
            )
        } else {
            holder.binding.userStatusImage.visibility = View.GONE
        }
        if (model.lastMessage != null) {
            holder.binding.dialogDate.visibility = View.VISIBLE
            holder.binding.dialogDate.text = DateUtils.getRelativeTimeSpanString(
                model.lastActivity * 1000L,
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
                val text: String
                if (model.lastMessage!!.getCalculateMessageType() === ChatMessage.MessageType.REGULAR_TEXT_MESSAGE) {
                    if (model.lastMessage!!.actorId == user.userId) {
                        text = String.format(
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
                        text = String.format(
                            appContext.getString(R.string.nc_formatted_message),
                            authorDisplayName,
                            model.lastMessage!!.lastMessageDisplayText
                        )
                    }
                } else {
                    text = model.lastMessage!!.lastMessageDisplayText
                }
                holder.binding.dialogLastMessage.text = text
            }
        } else {
            holder.binding.dialogDate.visibility = View.GONE
            holder.binding.dialogLastMessage.setText(R.string.nc_no_messages_yet)
        }
        holder.binding.dialogAvatar.visibility = View.VISIBLE
        var shouldLoadAvatar = true
        var objectType: String?
        if (!TextUtils.isEmpty(model.objectType.also { objectType = it })) {
            when (objectType) {
                "share:password" -> {
                    shouldLoadAvatar = false
                    holder.binding.dialogAvatar.setImageDrawable(
                        ContextCompat.getDrawable(
                            context,
                            R.drawable.ic_circular_lock
                        )
                    )
                }
                "file" -> {
                    shouldLoadAvatar = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        holder.binding.dialogAvatar.setImageDrawable(
                            DisplayUtils.getRoundedDrawable(
                                viewThemeUtils.talk.themePlaceholderAvatar(
                                    holder.binding.dialogAvatar,
                                    R.drawable.ic_avatar_document
                                )
                            )
                        )
                    } else {
                        holder.binding.dialogAvatar.setImageDrawable(
                            ContextCompat.getDrawable(context, R.drawable.ic_circular_document)
                        )
                    }
                }
                else -> {}
            }
        }
        if (ConversationType.ROOM_SYSTEM == model.type) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val layers = arrayOfNulls<Drawable>(2)
                layers[0] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)
                layers[1] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)
                val layerDrawable = LayerDrawable(layers)
                holder.binding.dialogAvatar.hierarchy.setPlaceholderImage(
                    DisplayUtils.getRoundedDrawable(layerDrawable)
                )
            } else {
                holder.binding.dialogAvatar.hierarchy.setPlaceholderImage(R.mipmap.ic_launcher)
            }
            shouldLoadAvatar = false
        }
        if (shouldLoadAvatar) {
            when (model.type) {
                ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL -> if (!TextUtils.isEmpty(model.name)) {
                    val draweeController: DraweeController = Fresco.newDraweeControllerBuilder()
                        .setOldController(holder.binding.dialogAvatar.controller)
                        .setAutoPlayAnimations(true)
                        .setImageRequest(
                            DisplayUtils.getImageRequestForUrl(
                                ApiUtils.getUrlForAvatar(
                                    user.baseUrl,
                                    model.name,
                                    true
                                ),
                                user
                            )
                        )
                        .build()
                    holder.binding.dialogAvatar.controller = draweeController
                } else {
                    holder.binding.dialogAvatar.visibility = View.GONE
                }
                ConversationType.ROOM_GROUP_CALL -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.binding.dialogAvatar.setImageDrawable(
                        DisplayUtils.getRoundedDrawable(
                            viewThemeUtils.talk.themePlaceholderAvatar(
                                holder.binding.dialogAvatar,
                                R.drawable.ic_avatar_group
                            )
                        )
                    )
                } else {
                    holder.binding.dialogAvatar.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_circular_group)
                    )
                }
                ConversationType.ROOM_PUBLIC_CALL -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.binding.dialogAvatar.setImageDrawable(
                        DisplayUtils.getRoundedDrawable(
                            viewThemeUtils.talk.themePlaceholderAvatar(
                                holder.binding.dialogAvatar,
                                R.drawable.ic_avatar_link
                            )
                        )
                    )
                } else {
                    holder.binding.dialogAvatar.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_circular_link)
                    )
                }
                else -> holder.binding.dialogAvatar.visibility = View.GONE
            }
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
        private const val STATUS_SIZE_IN_DP = 9f
    }
}
