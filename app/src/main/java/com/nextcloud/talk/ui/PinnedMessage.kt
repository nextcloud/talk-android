/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ConversationUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val SPACE_16 = 16
const val CORNER_RADIUS = 16
const val ICON_SIZE = 24
const val ELEVATION = 4
const val MAX_HEIGHT = 100

@Suppress("LongMethod", "LongParameterList")
@Composable
fun PinnedMessageView(
    message: ChatMessage,
    viewThemeUtils: ViewThemeUtils,
    currentConversation: ConversationModel?,
    scrollToMessageWithIdWithOffset: (String) -> Unit,
    hidePinnedMessage: (ChatMessage) -> Unit,
    unPinMessage: (ChatMessage) -> Unit
) {
    message.incoming = true

    val pinnedBy = stringResource(R.string.pinned_by)

    message.actorDisplayName = remember(message.pinnedActorDisplayName) {
        "${message.actorDisplayName}\n$pinnedBy ${message.pinnedActorDisplayName}"
    }
    val scrollState = rememberScrollState()

    val context = LocalContext.current

    val outgoingBubbleColor = remember {
        val colorInt = viewThemeUtils.talk
            .getOutgoingMessageBubbleColor(context, message.isDeleted, false)

        Color(colorInt)
    }

    val colorScheme = remember {
        viewThemeUtils.getColorScheme(context)
    }

    val highEmphasisColor = colorScheme.onSurfaceVariant

    val incomingBubbleColor = colorResource(R.color.bg_message_list_incoming_bubble)

    val canPin = remember {
        message.isOneToOneConversation ||
            ConversationUtils.isParticipantOwnerOrModerator(currentConversation!!)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy((-SPACE_16).dp),
        modifier = Modifier
    ) {
        Box(
            modifier = Modifier
                .shadow(ELEVATION.dp, shape = RoundedCornerShape(CORNER_RADIUS.dp))
                .background(incomingBubbleColor, RoundedCornerShape(CORNER_RADIUS.dp))
                .padding(SPACE_16.dp)
                .heightIn(max = MAX_HEIGHT.dp)
                .customVerticalScrollbar(scrollState, color = outgoingBubbleColor)
                .verticalScroll(scrollState)
                .clickable {
                    scrollToMessageWithIdWithOffset(message.id)
                }

        ) {
            ComposeChatAdapter().GetComposableForMessage(message)
        }

        var expanded by remember { mutableStateOf(false) }

        val pinnedUntilStr = stringResource(R.string.pinned_until)
        val untilUnpin = stringResource(R.string.until_unpin)
        val pinnedText = remember(message.pinnedUntil) {
            message.pinnedUntil?.let {
                val format = if (DateFormat.is24HourFormat(context)) {
                    "MMM dd yyyy, HH:mm"
                } else {
                    "MMM dd yyyy, hh:mm a"
                }

                val localDateTime = Instant.ofEpochSecond(it)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()

                val timeString = localDateTime.format(DateTimeFormatter.ofPattern(format))

                "$pinnedUntilStr $timeString"
            } ?: untilUnpin
        }

        Box(
            modifier = Modifier
                .offset(SPACE_16.dp, 0.dp)
                .background(outgoingBubbleColor, RoundedCornerShape(CORNER_RADIUS.dp))
        ) {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Default.Menu, // Or use a Pin icon here
                    contentDescription = "Pinned Message Options",
                    tint = highEmphasisColor
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(outgoingBubbleColor)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = pinnedText,
                            color = highEmphasisColor,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onClick = { /* No-op or toggle expansion */ },
                    enabled = false // Visually distinct as information, not action
                )

                Divider()

                DropdownMenuItem(
                    text = { Text("Go to message", color = highEmphasisColor) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.baseline_chat_bubble_outline_24),
                            contentDescription = null,
                            modifier = Modifier.size(ICON_SIZE.dp),
                            tint = highEmphasisColor
                        )
                    },
                    onClick = {
                        expanded = false
                        scrollToMessageWithIdWithOffset(message.id)
                    }
                )

                DropdownMenuItem(
                    text = { Text("Dismiss", color = highEmphasisColor) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_eye_off),
                            contentDescription = null,
                            modifier = Modifier.size(ICON_SIZE.dp),
                            tint = highEmphasisColor
                        )
                    },
                    onClick = {
                        expanded = false
                        hidePinnedMessage(message)
                    }
                )

                if (canPin) {
                    DropdownMenuItem(
                        text = { Text("Unpin", color = highEmphasisColor) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.keep_off_24px),
                                contentDescription = null,
                                modifier = Modifier.size(ICON_SIZE.dp),
                                tint = highEmphasisColor
                            )
                        },
                        onClick = {
                            expanded = false
                            unPinMessage(message)
                        }
                    )
                }
            }
        }
    }
}
