/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.message

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import android.view.View
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListDrawable
import io.noties.markwon.ext.tasklist.TaskListPlugin

class MessageUtils(val context: Context) {
    fun enrichChatReplyMessageText(
        context: Context,
        message: ChatMessage,
        incoming: Boolean,
        viewThemeUtils: ViewThemeUtils
    ): Spanned? {
        return if (message.message == null) {
            null
        } else if (message.renderMarkdown == false) {
            SpannableString(DisplayUtils.ellipsize(message.text, MAX_REPLY_LENGTH))
        } else {
            enrichChatMessageText(
                context,
                DisplayUtils.ellipsize(message.text, MAX_REPLY_LENGTH),
                incoming,
                viewThemeUtils
            )
        }
    }

    fun enrichChatMessageText(
        context: Context,
        message: ChatMessage,
        incoming: Boolean,
        viewThemeUtils: ViewThemeUtils
    ): Spanned? {
        return if (message.message == null) {
            null
        } else if (message.renderMarkdown == false) {
            SpannableString(message.message)
        } else {
            val newMessage = message.message!!.replace("\n", "  \n", false)
            enrichChatMessageText(context, newMessage, incoming, viewThemeUtils)
        }
    }

    private fun enrichChatMessageText(
        context: Context,
        message: String,
        incoming: Boolean,
        viewThemeUtils: ViewThemeUtils
    ): Spanned {
        return viewThemeUtils.talk.themeMarkdown(context, message, incoming)
    }

    fun processMessageParameters(
        themingContext: Context,
        viewThemeUtils: ViewThemeUtils,
        spannedText: Spanned,
        message: ChatMessage,
        itemView: View
    ): Spanned {
        var processedMessageText = spannedText
        val messageParameters = message.messageParameters
        if (messageParameters != null && messageParameters.size > 0) {
            processedMessageText = processMessageParameters(
                themingContext,
                viewThemeUtils,
                messageParameters,
                message,
                processedMessageText,
                itemView
            )
        }
        return processedMessageText
    }

    @Suppress("NestedBlockDepth", "LongParameterList")
    private fun processMessageParameters(
        themingContext: Context,
        viewThemeUtils: ViewThemeUtils,
        messageParameters: HashMap<String?, HashMap<String?, String?>>,
        message: ChatMessage,
        messageString: Spanned,
        itemView: View
    ): Spanned {
        var messageStringInternal = messageString
        for (key in messageParameters.keys) {
            val individualHashMap = message.messageParameters!![key]
            if (individualHashMap != null) {
                when (individualHashMap["type"]) {
                    "user", "guest", "call", "user-group", "email", "circle" -> {
                        val chip = if (individualHashMap["id"] == message.activeUser!!.userId) {
                            R.xml.chip_you
                        } else {
                            R.xml.chip_others
                        }
                        val id = if (individualHashMap["server"] != null) {
                            individualHashMap["id"] + "@" + individualHashMap["server"]
                        } else {
                            individualHashMap["id"]
                        }

                        messageStringInternal = DisplayUtils.searchAndReplaceWithMentionSpan(
                            key!!,
                            themingContext,
                            messageStringInternal,
                            id!!,
                            message.token,
                            individualHashMap["name"]!!,
                            individualHashMap["type"]!!,
                            message.activeUser!!,
                            chip,
                            viewThemeUtils,
                            individualHashMap["server"] != null
                        )
                    }

                    "file" -> {
                        itemView.setOnClickListener { v ->
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(individualHashMap["link"]))
                            context.startActivity(browserIntent)
                        }
                    }
                }
            }
        }

        return messageStringInternal
    }

    fun getRenderedMarkdownText(context: Context, markdown: String, textColor: Int): Spanned {
        val drawable = TaskListDrawable(textColor, textColor, context.getColor(R.color.bg_default))
        val markwon = Markwon.builder(context).usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder.isLinkUnderlined(true).headingBreakHeight(0)
            }

            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { view: View?, link: String? ->
                    Log.i(TAG, "Link action not implemented $view / $link")
                }
            }
        })
            .usePlugin(TaskListPlugin.create(drawable))
            .usePlugin(StrikethroughPlugin.create()).build()
        return markwon.toMarkdown(markdown)
    }

    companion object {
        private const val TAG = "MessageUtils"
        const val MAX_REPLY_LENGTH = 250
        const val HTTPS_PROTOCOL = "https://"
    }
}
