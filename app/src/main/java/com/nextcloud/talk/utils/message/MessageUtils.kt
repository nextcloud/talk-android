/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2023 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.utils.message

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spanned
import android.util.Log
import android.view.View
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListDrawable
import io.noties.markwon.ext.tasklist.TaskListPlugin

class MessageUtils(val context: Context) {

    fun enrichChatMessageText(context: Context, message: ChatMessage, textColor: Int): Spanned? {
        return if (message.message == null) {
            null
        } else {
            enrichChatMessageText(context, message.message!!, textColor)
        }
    }

    fun enrichChatMessageText(context: Context, message: String, textColor: Int): Spanned {
        return getRenderedMarkdownText(context, message, textColor)
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
                    "user", "guest", "call", "user-group" -> {
                        val chip = if (individualHashMap["id"] == message.activeUser!!.userId) {
                            R.xml.chip_you
                        } else {
                            R.xml.chip_others
                        }
                        messageStringInternal = DisplayUtils.searchAndReplaceWithMentionSpan(
                            key,
                            themingContext,
                            messageStringInternal,
                            individualHashMap["id"]!!,
                            individualHashMap["name"]!!,
                            individualHashMap["type"]!!,
                            message.activeUser!!,
                            chip,
                            viewThemeUtils
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

    private fun getRenderedMarkdownText(context: Context, markdown: String, textColor: Int): Spanned {
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
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(drawable))
            .usePlugin(StrikethroughPlugin.create()).build()
        return markwon.toMarkdown(markdown)
    }

    companion object {
        private const val TAG = "MessageUtils"
    }
}
