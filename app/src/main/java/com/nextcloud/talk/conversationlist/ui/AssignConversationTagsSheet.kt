/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.tags.ConversationTag

@Composable
fun AssignConversationTagsSheetContent(
    conversation: ConversationModel,
    tags: List<ConversationTag>,
    onToggleTag: (String) -> Unit,
    onManageTagsClick: () -> Unit
) {
    val headerText = conversation.displayName.takeIf { it.isNotEmpty() } ?: conversation.name
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        Text(
            text = headerText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = dimensionResource(R.dimen.standard_dialog_padding),
                    vertical = dimensionResource(R.dimen.standard_half_padding)
                ),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.nc_conversation_tags_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = dimensionResource(R.dimen.standard_dialog_padding),
                        vertical = dimensionResource(R.dimen.standard_half_padding)
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            tags.forEach { tag ->
                AssignableTagRow(
                    tag = tag,
                    isAssigned = conversation.tagIds.contains(tag.id),
                    onToggleTag = onToggleTag
                )
            }
        }
        ManageTagsRow(onManageTagsClick)
    }
}

@Composable
private fun AssignableTagRow(tag: ConversationTag, isAssigned: Boolean, onToggleTag: (String) -> Unit) {
    TextButton(
        onClick = { onToggleTag(tag.id) },
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.bottom_sheet_item_height)),
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.standard_dialog_padding)),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Label,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.standard_dialog_padding)))
        Text(
            text = tag.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start
        )
        Checkbox(checked = isAssigned, onCheckedChange = { onToggleTag(tag.id) })
    }
}

@Composable
private fun ManageTagsRow(onManageTagsClick: () -> Unit) {
    TextButton(
        onClick = onManageTagsClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.bottom_sheet_item_height)),
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.standard_dialog_padding)),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.standard_dialog_padding)))
        Text(
            text = stringResource(R.string.nc_conversation_tags_manage),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start
        )
    }
}

private fun previewConversation() =
    ConversationModel(
        internalId = "1@tok",
        accountId = 1L,
        token = "tok",
        name = "alice",
        displayName = "Alice",
        description = "",
        type = ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL,
        participantType = Participant.ParticipantType.USER,
        sessionId = "",
        actorId = "user1",
        actorType = "users",
        objectType = ConversationEnums.ObjectType.DEFAULT,
        notificationLevel = ConversationEnums.NotificationLevel.DEFAULT,
        conversationReadOnlyState = ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE,
        lobbyState = ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS,
        lobbyTimer = 0L,
        canLeaveConversation = true,
        canDeleteConversation = true,
        unreadMentionDirect = false,
        notificationCalls = 0,
        avatarVersion = "",
        hasCustomAvatar = false,
        callStartTime = 0L,
        tagIds = listOf("2")
    )

private fun previewTags() =
    listOf(
        ConversationTag(id = "1", name = "Work", sortOrder = 0),
        ConversationTag(id = "2", name = "Family", sortOrder = 1)
    )

@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AssignConversationTagsSheetPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            AssignConversationTagsSheetContent(
                conversation = previewConversation(),
                tags = previewTags(),
                onToggleTag = {},
                onManageTagsClick = {}
            )
        }
    }
}
