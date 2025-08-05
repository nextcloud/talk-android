/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.text.Spannable
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.databinding.ItemSystemMessageBinding
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.stfalcon.chatkit.messages.MessageHolders
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class SystemMessageViewHolder(itemView: View) :
    MessageHolders
        .IncomingTextMessageViewHolder<ChatMessage>(itemView) {

    private val binding: ItemSystemMessageBinding = ItemSystemMessageBinding.bind(itemView)

    @Inject
    lateinit var currentUserProvider: CurrentUserProviderNew

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
        val user = currentUserProvider.currentUser.blockingGet()
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
        binding.messageText.setTextSize(
            TypedValue.COMPLEX_UNIT_PX,
            resources.getDimension(R.dimen.chat_system_message_text_size)
        )
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
                    messageString =
                        DisplayUtils.searchAndColor(
                            messageString,
                            searchText!!,
                            mentionColor,
                            resources.getDimensionPixelSize(R.dimen.chat_system_message_text_size)
                        )
                    if (individualMap["link"] != null) {
                        val displayName = individualMap["name"] ?: ""
                        val link = (user.baseUrl + individualMap["link"])
                        val newStartIndex = messageString.indexOf(displayName)
                        if (newStartIndex != -1) {
                            val clickableSpan = object : ClickableSpan() {
                                override fun onClick(widget: View) {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, link.toUri())
                                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context?.startActivity(browserIntent)
                                }

                                override fun updateDrawState(ds: TextPaint) {
                                    super.updateDrawState(ds)
                                    ds.color = mentionColor
                                    ds.isUnderlineText = false
                                }
                            }

                            messageString.setSpan(
                                clickableSpan,
                                newStartIndex,
                                newStartIndex + displayName.length,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }
                }
            }

            binding.systemMessageLayout.visibility = View.VISIBLE
            binding.similarMessagesHint.visibility = View.GONE
            if (message.expandableParent) {
                processExpandableParent(message, messageString)
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
    }

    @SuppressLint("SetTextI18n")
    private fun processExpandableParent(message: ChatMessage, messageString: Spannable) {
        binding.expandCollapseIcon.visibility = View.VISIBLE

        if (!message.isExpanded) {
            val similarMessages = sharedApplication!!.resources.getQuantityString(
                R.plurals.see_similar_system_messages,
                message.expandableChildrenAmount,
                message.expandableChildrenAmount
            )

            binding.messageText.text = messageString
            binding.messageText.movementMethod = LinkMovementMethod.getInstance()
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
    }

    fun assignSystemMessageInterface(systemMessageInterface: SystemMessageInterface) {
        this.systemMessageInterface = systemMessageInterface
    }
}
