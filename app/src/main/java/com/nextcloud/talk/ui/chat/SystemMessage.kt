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
                    message.message,
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
                message.message,
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
