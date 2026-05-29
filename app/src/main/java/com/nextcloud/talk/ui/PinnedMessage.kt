/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.ui.chat.MarkdownText
import com.nextcloud.talk.ui.theme.LocalViewThemeUtils
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.preview.ComposePreviewUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Suppress("LongMethod", "LongParameterList")
@Composable
fun PinnedMessageView(
    message: ChatMessage,
    user: User,
    viewThemeUtils: ViewThemeUtils,
    currentConversation: ConversationModel?,
    scrollToMessageWithIdWithOffset: (String) -> Unit,
    hidePinnedMessage: (ChatMessage) -> Unit,
    unPinMessage: (ChatMessage) -> Unit
) {
    message.incoming = true

    val pinnedHeadline = if (message.pinnedActorId != message.actorId) {
        if (message.pinnedActorId == currentConversation?.actorId) {
            stringResource(R.string.pinned_by_you, message.actorDisplayName.orEmpty())
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

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val colorScheme = remember { viewThemeUtils.getColorScheme(context) }

    val messageUi = remember(message, user) {
        ChatMessageUi(
            id = message.jsonMessageId,
            message = message.getRichText(),
            plainMessage = message.message.orEmpty(),
            renderMarkdown = message.renderMarkdown == true,
            actorDisplayName = message.actorDisplayName.orEmpty(),
            isThread = false,
            threadTitle = "",
            threadReplies = 0,
            incoming = true,
            isDeleted = message.isDeleted,
            avatarUrl = null,
            statusIcon = MessageStatusIcon.SENT,
            timestamp = message.timestamp,
            date = Instant.ofEpochSecond(message.timestamp).atZone(ZoneId.systemDefault()).toLocalDate(),
            content = MessageTypeContent.RegularText,
            roomToken = currentConversation?.token,
            activeUserId = user.userId,
            activeUserBaseUrl = user.baseUrl,
            messageParameters = message.messageParameters
                .orEmpty()
                .mapNotNull { (key, params) ->
                    key?.let { k ->
                        k to params.mapNotNull { (pk, pv) -> if (pk != null && pv != null) pk to pv else null }.toMap()
                    }
                }
                .toMap()
        )
    }

    val highEmphasisColor = colorScheme.onSurfaceVariant

    var expanded by remember { mutableStateOf(false) }
    val pinnedUntilStr = stringResource(R.string.pinned_until)
    val untilUnpin = stringResource(R.string.until_unpin)
    val pinnedText = remember(message.pinnedUntil) {
        message.pinnedUntil?.let {
            val format = if (DateFormat.is24HourFormat(context)) "MMM dd yyyy, HH:mm" else "MMM dd yyyy, hh:mm a"
            val timeString = Instant.ofEpochSecond(it)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DateTimeFormatter.ofPattern(format))
            "$pinnedUntilStr $timeString"
        } ?: untilUnpin
    }

    val canPin = remember {
        message.isOneToOneConversation ||
            ConversationUtils.isParticipantOwnerOrModerator(currentConversation!!)
    }

    Card(
        onClick = { scrollToMessageWithIdWithOffset(message.jsonMessageId.toString()) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(start = 8.dp, end = 0.dp)) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.outline_push_pin_24),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(24.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp, bottom = 8.dp)
            ) {
                Text(
                    text = pinnedHeadline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MarkdownText(
                    message = messageUi,
                    textColor = colorScheme.onSurface,
                    modifier = Modifier
                        .heightIn(max = 80.dp)
                        .verticalScroll(scrollState)
                )
            }
            Column {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_more_vert_24px),
                            contentDescription = stringResource(R.string.pinned_message_options)
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = pinnedText, color = highEmphasisColor) },
                            onClick = {},
                            enabled = false
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.pinned_go_to_message), color = highEmphasisColor) },
                            onClick = {
                                expanded = false
                                scrollToMessageWithIdWithOffset(message.jsonMessageId.toString())
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
    }
}

@Preview(name = "Long Content")
@Composable
fun PinnedMessageLongContentPreview() {
    PinnedMessagePreview(
        messageContent = "Line one of a long pinned message with **bold** text\n" +
            "Line two continues with _italic_ content\n" +
            "Line three adds more context to the message\n" +
            "Line four keeps going with even more text\n" +
            "Line five is the last line and should be cut off by the scroll area"
    )
}

@Preview(name = "Markdown Content")
@Composable
fun PinnedMessageMarkdownPreview() {
    PinnedMessagePreview(
        messageContent = "## Meeting agenda\n" +
            "- Review last week's action items\n" +
            "- Discuss current sprint progress\n" +
            "- Plan next release milestones"
    )
}

@Preview(
    name = "Dark Mode / R-t-L",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    locale = "ar"
)
@Composable
fun PinnedMessagePreviewDarkRtl() {
    PinnedMessagePreview()
}

@Suppress("MagicNumber")
@Preview(name = "Light Mode")
@Composable
fun PinnedMessagePreview(messageContent: String = "This is a **pinned** message _content_") {
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
        CompositionLocalProvider(LocalViewThemeUtils provides viewThemeUtils) {
            Box(modifier = Modifier.padding(16.dp)) {
                PinnedMessageView(
                    message = message,
                    user = user,
                    viewThemeUtils = viewThemeUtils,
                    currentConversation = currentConversation,
                    scrollToMessageWithIdWithOffset = {},
                    hidePinnedMessage = {},
                    unPinMessage = {}
                )
            }
        }
    }
}
