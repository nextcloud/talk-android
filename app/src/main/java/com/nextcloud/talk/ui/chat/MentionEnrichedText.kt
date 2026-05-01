/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.nextcloud.talk.chat.ui.model.ChatMessageUi

private val messageTokenRegex =
    Regex("""(\{[^{}]+\}|\*\*.*?\*\*|\*.*?\*|~~.*?~~|`.*?`|\[.*?]\(.*?\)|https?://\S+)""")

private data class MentionRichText(val annotated: AnnotatedString, val inlineContent: Map<String, InlineTextContent>)

@Composable
fun MentionEnrichedText(
    message: ChatMessageUi,
    modifier: Modifier = Modifier,
    textStyle: TextStyle,
    maxLines: Int = Int.MAX_VALUE
) {
    var isMultilineLayout by remember(message.id, message.message) {
        mutableStateOf(message.message.contains("\n") || message.message.contains("\r"))
    }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val richText = remember(message, isMultilineLayout, linkColor, codeBackground, textStyle) {
        buildMentionRichText(
            message = message,
            linkColor = linkColor,
            codeBackground = codeBackground,
            textStyle = textStyle,
            isMultilineLayout = isMultilineLayout
        )
    }
    val resolvedTextStyle = if (richText.inlineContent.isEmpty()) {
        textStyle
    } else {
        textStyle.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified)
    }

    Text(
        modifier = modifier,
        text = richText.annotated,
        inlineContent = richText.inlineContent,
        style = resolvedTextStyle,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        onTextLayout = { textLayoutResult ->
            val isCurrentlyMultiline = textLayoutResult.lineCount > 1
            if (isMultilineLayout != isCurrentlyMultiline) {
                isMultilineLayout = isCurrentlyMultiline
            }
        }
    )
}

private fun buildMentionRichText(
    message: ChatMessageUi,
    linkColor: Color,
    codeBackground: Color,
    textStyle: TextStyle,
    isMultilineLayout: Boolean
): MentionRichText {
    val inlineContent = linkedMapOf<String, InlineTextContent>()
    var mentionCounter = 0

    val annotated = buildAnnotatedString {
        var lastIndex = 0
        for (match in messageTokenRegex.findAll(message.plainMessage)) {
            val range = match.range
            if (lastIndex < range.first) {
                append(message.plainMessage.substring(lastIndex, range.first))
            }

            val token = match.value
            when {
                token.startsWith("{") && token.endsWith("}") -> {
                    val key = token.removePrefix("{").removeSuffix("}")
                    val mention = parseMentionChipModel(
                        key = key,
                        messageParameters = message.messageParameters,
                        activeUserId = message.activeUserId,
                        activeUserBaseUrl = message.activeUserBaseUrl,
                        roomToken = message.roomToken
                    )
                    if (mention == null) {
                        appendFallbackParameter(token, message.messageParameters)
                    } else {
                        val inlineId = "mention-${message.id}-$mentionCounter"
                        mentionCounter += 1
                        appendMentionChip(inlineId, mention, inlineContent, textStyle, isMultilineLayout)
                    }
                }

                token.startsWith(
                    "**"
                ) -> appendStyledToken(token.removeSurrounding("**"), SpanStyle(fontWeight = FontWeight.Bold))
                token.startsWith(
                    "*"
                ) -> appendStyledToken(token.removeSurrounding("*"), SpanStyle(fontStyle = FontStyle.Italic))
                token.startsWith(
                    "~~"
                ) -> appendStyledToken(
                    token.removeSurrounding("~~"),
                    SpanStyle(textDecoration = TextDecoration.LineThrough)
                )
                token.startsWith("`") -> {
                    appendStyledToken(
                        token.removeSurrounding("`"),
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
                    )
                }

                token.startsWith("[") -> {
                    val textPart = token.substringAfter("[").substringBefore("]")
                    val url = token.substringAfter("(").substringBefore(")")
                    appendLinkedToken(textPart, url, linkColor)
                }

                token.startsWith("http") -> appendLinkedToken(token, token, linkColor)
            }

            lastIndex = range.last + 1
        }

        if (lastIndex < message.plainMessage.length) {
            append(message.plainMessage.substring(lastIndex))
        }
    }

    return MentionRichText(annotated = annotated, inlineContent = inlineContent)
}

private fun AnnotatedString.Builder.appendStyledToken(text: String, style: SpanStyle) {
    val start = length
    append(text)
    addStyle(style, start, length)
}

private fun AnnotatedString.Builder.appendLinkedToken(text: String, url: String, linkColor: Color) {
    val start = length
    append(text)
    addStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline), start, length)
    addLink(LinkAnnotation.Url(url), start, length)
}

private fun AnnotatedString.Builder.appendFallbackParameter(
    token: String,
    messageParameters: Map<String, Map<String, String>>
) {
    val key = token.removePrefix("{").removeSuffix("}")
    val replacementText = messageParameters[key]?.get("name")
    if (replacementText == null) {
        append(token)
    } else {
        append(replacementText)
    }
}
