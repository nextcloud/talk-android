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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.utils.DateUtils

private const val AUTHOR_TEXT_SIZE = 12
private const val TIME_TEXT_SIZE = 12

@Composable
fun SystemMessage(message: ChatMessageUi) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val timeString = DateUtils(LocalContext.current).getLocalTimeStringFromTimestamp(message.timestamp)
        Box(modifier = Modifier.fillMaxWidth()) {
            if (message.isExpandableParent) {
                ExpandableSystemMessage(message = message)
            } else {
                Text(
                    buildStyledMessage(message.plainMessage, message.messageParameters),
                    fontSize = AUTHOR_TEXT_SIZE.sp,
                    color = colorScheme.onSurface,
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.Center)
                )
            }
            Text(
                timeString,
                fontSize = TIME_TEXT_SIZE.sp,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            )
        }
    }
}

private val placeholderPattern = Regex("\\{(\\w+)\\}")
private val actorTypes = setOf("user", "guest", "call", "email", "user-group", "circle")

private fun buildStyledMessage(
    plainMessage: String,
    messageParameters: Map<String, Map<String, String>>
): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        var lastEnd = 0
        for (match in placeholderPattern.findAll(plainMessage)) {
            if (match.range.first > lastEnd) {
                append(plainMessage.substring(lastEnd, match.range.first))
            }
            val params = messageParameters[match.groupValues[1]]
            val resolved = if (params != null) {
                val name = params["name"].orEmpty()
                if (params["type"] in actorTypes) "@$name" else name
            } else {
                match.value
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(resolved) }
            lastEnd = match.range.last + 1
        }
        if (lastEnd < plainMessage.length) append(plainMessage.substring(lastEnd))
    }

@Composable
private fun ExpandableSystemMessage(message: ChatMessageUi) {
    val chevronRes = if (message.isExpanded) R.drawable.ic_keyboard_arrow_up else R.drawable.ic_keyboard_arrow_down

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
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
                buildStyledMessage(message.plainMessage, message.messageParameters),
                fontSize = AUTHOR_TEXT_SIZE.sp,
                color = colorScheme.onSurface
            )
        }
        if (!message.isExpanded) {
            Text(
                pluralStringResource(
                    R.plurals.see_similar_system_messages,
                    message.expandableChildrenAmount,
                    message.expandableChildrenAmount
                ),
                fontSize = (AUTHOR_TEXT_SIZE - 1).sp,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}
