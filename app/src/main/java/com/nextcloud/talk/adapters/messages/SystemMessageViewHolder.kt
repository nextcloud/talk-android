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
package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.databinding.ItemSystemMessageBinding
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class SystemMessageViewHolder(itemView: View) : MessageHolders.IncomingTextMessageViewHolder<ChatMessage>(itemView) {

    private val binding: ItemSystemMessageBinding = ItemSystemMessageBinding.bind(itemView)

    @JvmField
    @Inject
    var appPreferences: AppPreferences? = null

    @JvmField
    @Inject
    var context: Context? = null

    @JvmField
    @Inject
    var dateUtils: DateUtils? = null
    protected var background: ViewGroup

    lateinit var systemMessageInterface: SystemMessageInterface

    init {
        sharedApplication!!.componentApplication.inject(this)
        background = itemView.findViewById(R.id.container)
    }

    @SuppressLint("SetTextI18n")
    override fun onBind(message: ChatMessage) {
        super.onBind(message)
        val resources = itemView.resources
        val pressedColor: Int = resources.getColor(R.color.bg_message_list_incoming_bubble)
        val mentionColor: Int = resources.getColor(R.color.textColorMaxContrast)
        val bubbleDrawable = DisplayUtils.getMessageSelector(
            resources.getColor(R.color.transparent),
            resources.getColor(R.color.transparent),
            pressedColor,
            R.drawable.shape_grouped_incoming_message
        )
        ViewCompat.setBackground(background, bubbleDrawable)
        var messageString: Spannable = SpannableString(message.text)
        if (message.messageParameters != null && message.messageParameters!!.size > 0) {
            for (key in message.messageParameters!!.keys) {
                val individualMap: Map<String?, String?>? = message.messageParameters!![key]
                if (individualMap != null && individualMap.containsKey("name")) {
                    var searchText: String? = if ("user" == individualMap["type"] ||
                        "guest" == individualMap["type"] ||
                        "call" == individualMap["type"]
                    ) {
                        "@" + individualMap["name"]
                    } else {
                        individualMap["name"]
                    }
                    messageString = DisplayUtils.searchAndColor(messageString, searchText, mentionColor)
                }
            }
        }

        binding.systemMessageLayout.visibility = View.VISIBLE
        binding.similarMessagesHint.visibility = View.GONE
        if (message.expandableParent) {
            binding.expandCollapseIcon.visibility = View.VISIBLE

            if (!message.isExpanded) {
                val similarMessages = String.format(
                    sharedApplication!!.resources.getString(R.string.see_similar_system_messages),
                    message.expandableChildrenAmount
                )

                binding.messageText.text = messageString
                binding.similarMessagesHint.visibility = View.VISIBLE
                binding.similarMessagesHint.text = similarMessages

                binding.expandCollapseIcon.setImageDrawable(
                    ContextCompat.getDrawable(context!!, R.drawable.baseline_unfold_more_24)
                )
                binding.systemMessageLayout.setOnClickListener { systemMessageInterface.expandSystemMessage(message) }
                binding.messageText.setOnClickListener { systemMessageInterface.expandSystemMessage(message) }
            } else {
                binding.messageText.text = messageString
                binding.similarMessagesHint.visibility = View.GONE
                binding.similarMessagesHint.text = ""

                binding.expandCollapseIcon.setImageDrawable(
                    ContextCompat.getDrawable(context!!, R.drawable.baseline_unfold_less_24)
                )
                binding.systemMessageLayout.setOnClickListener { systemMessageInterface.collapseSystemMessages() }
                binding.messageText.setOnClickListener { systemMessageInterface.collapseSystemMessages() }
            }
        } else if (message.hiddenByCollapse) {
            binding.systemMessageLayout.visibility = View.GONE
        } else {
            binding.expandCollapseIcon.visibility = View.GONE
            binding.messageText.text = messageString
            binding.expandCollapseIcon.setImageDrawable(null)
            binding.systemMessageLayout.setOnClickListener(null)
        }

        if (!message.expandableParent && message.lastItemOfExpandableGroup != 0) {
            binding.systemMessageLayout.setOnClickListener { systemMessageInterface.collapseSystemMessages() }
            binding.messageText.setOnClickListener { systemMessageInterface.collapseSystemMessages() }
        }

        binding.messageTime.text = dateUtils!!.getLocalTimeStringFromTimestamp(message.timestamp)
        itemView.setTag(R.string.replyable_message_view_tag, message.replyable)
    }

    fun assignSystemMessageInterface(systemMessageInterface: SystemMessageInterface) {
        this.systemMessageInterface = systemMessageInterface
    }
}
