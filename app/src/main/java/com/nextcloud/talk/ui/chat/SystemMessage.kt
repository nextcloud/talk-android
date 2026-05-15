/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.utils.DateUtils

private const val TIME_TEXT_SIZE = 12

private val systemMessageTextStyle
    @Composable get() = MaterialTheme.typography.bodyMedium

@Composable
fun SystemMessage(message: ChatMessageUi) {
    val timeString = DateUtils(LocalContext.current).getLocalTimeStringFromTimestamp(message.timestamp)
    val highlightSearchTerm = LocalHighlightSearchTerm.current
    if (message.isExpandableParent) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ExpandableSystemMessage(message = message, highlightSearchTerm = highlightSearchTerm)
            Text(
                timeString,
                fontSize = TIME_TEXT_SIZE.sp,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 4.dp)
            )
        }
    } else {
        val textStyle = systemMessageTextStyle
        val addLineBreaks = remember(message.id, message.plainMessage) { mutableStateOf(false) }
        val (annotated, inlineContent) = remember(message, textStyle, addLineBreaks.value) {
            buildSystemMessageContent(message, textStyle, addLineBreaks.value)
        }
        val highlightedAnnotated = remember(annotated, highlightSearchTerm) {
            annotated.withSearchHighlight(highlightSearchTerm)
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                highlightedAnnotated,
                style = systemMessageTextStyle,
                color = colorScheme.onSurface,
                inlineContent = inlineContent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 64.dp, vertical = 4.dp)
                    .align(Alignment.Center),
                onTextLayout = { result ->
                    if (!addLineBreaks.value && result.lineCount > 1) {
                        addLineBreaks.value = true
                    }
                }
            )
            Text(
                timeString,
                fontSize = TIME_TEXT_SIZE.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun ExpandableSystemMessage(message: ChatMessageUi, highlightSearchTerm: String?) {
    val chevronRes = if (message.isExpanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down
    val textStyle = systemMessageTextStyle
    val (annotated, inlineContent) = buildSystemMessageContent(message, textStyle)
    val highlightedAnnotated = remember(annotated, highlightSearchTerm) {
        annotated.withSearchHighlight(highlightSearchTerm)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(chevronRes),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(16.dp),
                tint = colorScheme.onSurfaceVariant
            )
            Text(
                highlightedAnnotated,
                style = textStyle,
                color = colorScheme.onSurface,
                inlineContent = inlineContent
            )
        }
        if (!message.isExpanded) {
            Text(
                pluralStringResource(
                    R.plurals.see_similar_system_messages,
                    message.expandableChildrenAmount,
                    message.expandableChildrenAmount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

private val placeholderPattern = Regex("\\{(\\w+)\\}")

private fun buildSystemMessageContent(
    message: ChatMessageUi,
    textStyle: TextStyle,
    addLineBreaks: Boolean = false
): Pair<androidx.compose.ui.text.AnnotatedString, Map<String, InlineTextContent>> {
    val inlineContent = linkedMapOf<String, InlineTextContent>()
    var mentionCounter = 0
    val annotated = buildAnnotatedString {
        var lastEnd = 0
        for (match in placeholderPattern.findAll(message.plainMessage)) {
            if (match.range.first > lastEnd) {
                append(message.plainMessage.substring(lastEnd, match.range.first))
            }
            val key = match.groupValues[1]
            val mention = parseMentionChipModel(
                key = key,
                messageParameters = message.messageParameters,
                activeUserId = message.activeUserId,
                activeUserBaseUrl = message.activeUserBaseUrl,
                roomToken = message.roomToken
            )
            if (mention != null) {
                val inlineId = "sysmsg-${message.id}-$mentionCounter"
                mentionCounter++
                appendMentionChip(inlineId, mention, inlineContent, textStyle, isMultilineLayout = false)
                if (addLineBreaks) append("\n")
            } else {
                val name = message.messageParameters[key]?.get("name") ?: match.value
                val start = length
                append(name)
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
            }
            lastEnd = match.range.last + 1
        }
        if (lastEnd < message.plainMessage.length) {
            append(message.plainMessage.substring(lastEnd))
        }
    }
    return annotated to inlineContent
}
