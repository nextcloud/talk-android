/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.message

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import android.view.View
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListDrawable
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.lang.ref.WeakReference

class MessageUtils(val context: Context) {

    fun processEditMessageParameters(
        messageParameters: HashMap<String?, HashMap<String?, String?>>,
        message: ChatMessage?,
        inputEditText: String
    ): Spanned {
        var result = inputEditText
        for (key in messageParameters.keys) {
            val individualHashMap = message?.messageParameters?.get(key)
            if (individualHashMap != null) {
                val mentionId = individualHashMap["mention-id"]
                val type = individualHashMap["type"]
                val name = individualHashMap["name"]
                val placeholder = "@$name"
                result = when (type) {
                    "user", "guest", "email" -> result.replace(placeholder, "@$mentionId", ignoreCase = false)
                    "user-group", "circle" -> result.replace(placeholder, "@\"$mentionId\"", ignoreCase = false)
                    "call" -> result.replace(placeholder, "@all", ignoreCase = false)
                    else -> result
                }
            }
        }
        return SpannableString(result)
    }

    fun getRenderedMarkdownText(context: Context, markdown: String, textColor: Int): Spanned =
        buildMarkwon(context, textColor).toMarkdown(markdown)

    companion object {
        private const val TAG = "MessageUtils"

        private var cachedMarkwon: Markwon? = null
        private var cachedContextRef: WeakReference<Context>? = null
        private var cachedTextColor: Int = 0

        fun buildMarkwon(context: Context, textColor: Int): Markwon {
            val cached = cachedMarkwon
            if (cached != null && cachedContextRef?.get() === context && cachedTextColor == textColor) {
                return cached
            }
            val drawable = TaskListDrawable(textColor, textColor, context.getColor(R.color.bg_default))
            val markwon = Markwon.builder(context)
                .usePlugin(object : AbstractMarkwonPlugin() {
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
                .usePlugin(TablePlugin.create { _ -> })
                .usePlugin(StrikethroughPlugin.create())
                .build()
            cachedMarkwon = markwon
            cachedContextRef = WeakReference(context)
            cachedTextColor = textColor
            return markwon
        }
    }
}
