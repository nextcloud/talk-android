/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.message

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import android.view.View
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.SoftBreakAddsNewLinePlugin
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

        private const val TABLE_CELL_PADDING_DP = 8f
        private const val TABLE_BORDER_WIDTH_DP = 1f
        private const val TABLE_BORDER_ALPHA = 0x4D // ~30 %
        private const val TABLE_HEADER_BG_ALPHA = 0x1A // ~10 %
        private const val COLOR_ALPHA_SHIFT = 24
        private const val COLOR_RGB_MASK = 0x00FFFFFF

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
                            val url = link?.takeIf { it.isNotBlank() } ?: return@linkResolver
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (e: ActivityNotFoundException) {
                                Log.w(TAG, "No activity found to open markdown link: $url", e)
                            }
                        }
                    }
                })
                .usePlugin(TaskListPlugin.create(drawable))
                .usePlugin(
                    TablePlugin.create { tableTheme ->
                        val density = context.resources.displayMetrics.density
                        val cellPaddingPx = (TABLE_CELL_PADDING_DP * density).toInt()
                        val borderWidthPx = maxOf(1, (TABLE_BORDER_WIDTH_DP * density).toInt())
                        // Derive border/header colors from textColor so they adapt to
                        // both light (dark text) and dark (light text) message bubbles.
                        val rgb = textColor and COLOR_RGB_MASK
                        tableTheme
                            .tableCellPadding(cellPaddingPx)
                            .tableBorderWidth(borderWidthPx)
                            .tableBorderColor((TABLE_BORDER_ALPHA shl COLOR_ALPHA_SHIFT) or rgb)
                            .tableHeaderRowBackgroundColor((TABLE_HEADER_BG_ALPHA shl COLOR_ALPHA_SHIFT) or rgb)
                    }
                )
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(SoftBreakAddsNewLinePlugin.create())
                .build()
            cachedMarkwon = markwon
            cachedContextRef = WeakReference(context)
            cachedTextColor = textColor
            return markwon
        }
    }
}
