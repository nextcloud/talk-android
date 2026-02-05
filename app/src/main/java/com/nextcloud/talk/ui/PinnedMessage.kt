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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.preview.ComposePreviewUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val SPACE_16 = 16
const val CORNER_RADIUS = 16
val ELEVATION = 2.dp
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

    val pinnedHeadline = if (message.pinnedActorId != message.actorId) {
        if (message.pinnedActorId == currentConversation?.actorId) {
            stringResource(
                R.string.pinned_by_you,
                message.actorDisplayName.orEmpty()
            )
        } else {
            stringResource(
                R.string.pinned_by_author,
                message.actorDisplayName.orEmpty(),
                message.pinnedActorDisplayName.orEmpty()
            )
        }
    } else {
        "${message.actorDisplayName}"
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

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .shadow(
                elevation = ELEVATION,
                shape = RoundedCornerShape(CORNER_RADIUS.dp),
                clip = false
            )
            .background(
                incomingBubbleColor,
                RoundedCornerShape(CORNER_RADIUS.dp)
            )
            .padding(SPACE_16.dp)
            .heightIn(max = MAX_HEIGHT.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                scrollToMessageWithIdWithOffset(message.id)
            }
    ) {
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

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(end = 40.dp)
        ) {
            Text(
                text = pinnedHeadline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(message.text)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
        ) {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.pinned_message_options),
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
                            color = highEmphasisColor
                        )
                    },
                    onClick = {},
                    enabled = false
                )

                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pinned_go_to_message), color = highEmphasisColor) },
                    onClick = {
                        expanded = false
                        scrollToMessageWithIdWithOffset(message.id)
                    }
                )

                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pinned_dismiss), color = highEmphasisColor) },
                    onClick = {
                        expanded = false
                        hidePinnedMessage(message)
                    }
                )

                if (canPin) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.unpin_message), color = highEmphasisColor) },
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

@Preview(name = "Dark Mode", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PinnedMessagePreviewDark() {
    PinnedMessagePreview()
}

@Preview(name = "Long Content")
@Composable
fun PinnedMessageLongContentPreview() {
    PinnedMessagePreview(
        messageContent = "This is a **very long** _pinned_ ??\ncontent that should demonstrate how the " +
            "scrollable box behaves when there is more text than what can fit in the maximum height of the pinned " +
            "message view. It should show a scrollbar or at least allow vertical scrolling to see the rest of " +
            "the message. Adding even more text here to ensure it exceeds 100dp."
    )
}

@Preview(name = "Light Mode")
@Composable
fun PinnedMessagePreview(
    messageContent: String = "This is a **pinned** message _content_"
) {
    val context = LocalContext.current
    val previewUtils = ComposePreviewUtils.getInstance(context)
    val viewThemeUtils = previewUtils.viewThemeUtils
    val colorScheme = viewThemeUtils.getColorScheme(context)

    val user = User(id = 1L, userId = "user_id")
    val conversation = Conversation(
        token = "token",
        participantType = Participant.ParticipantType.OWNER,
        type = ConversationEnums.ConversationType.ROOM_GROUP_CALL
    )
    val currentConversation = ConversationModel.mapToConversationModel(conversation, user)

    val message = ChatMessage().apply {
        jsonMessageId = 1
        actorDisplayName = "Author One"
        pinnedActorDisplayName = "User Two"
        message = messageContent
        timestamp = System.currentTimeMillis() / 1000
        pinnedAt = System.currentTimeMillis() / 1000
    }

    MaterialTheme(colorScheme = colorScheme) {
        Box(modifier = Modifier.padding(16.dp)) {
            PinnedMessageView(
                message = message,
                viewThemeUtils = viewThemeUtils,
                currentConversation = currentConversation,
                scrollToMessageWithIdWithOffset = {},
                hidePinnedMessage = {},
                unPinMessage = {}
            )
        }
    }
}
