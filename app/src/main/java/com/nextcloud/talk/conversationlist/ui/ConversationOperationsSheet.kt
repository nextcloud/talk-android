/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.SpreedFeatures

private data class ConversationOpsVisibility(
    val showRemoveFromFavorites: Boolean,
    val showAddToFavorites: Boolean,
    val showMarkAsRead: Boolean,
    val showMarkAsUnread: Boolean,
    val showShareLink: Boolean,
    val showRename: Boolean,
    val isArchived: Boolean,
    val showLeave: Boolean,
    val showDelete: Boolean
)

private fun computeVisibility(conversation: ConversationModel, user: User): ConversationOpsVisibility {
    val spreedCap = user.capabilities?.spreedCapability
    val hasFavorites = CapabilitiesUtil.hasSpreedFeatureCapability(spreedCap, SpreedFeatures.FAVORITES)
    val hasReadMarker = CapabilitiesUtil.hasSpreedFeatureCapability(spreedCap, SpreedFeatures.CHAT_READ_MARKER)
    val hasUnread = CapabilitiesUtil.hasSpreedFeatureCapability(spreedCap, SpreedFeatures.CHAT_UNREAD)
    return ConversationOpsVisibility(
        showRemoveFromFavorites = hasFavorites && conversation.favorite,
        showAddToFavorites = hasFavorites && !conversation.favorite,
        showMarkAsRead = conversation.unreadMessages > 0 && hasReadMarker,
        showMarkAsUnread = conversation.unreadMessages <= 0 && hasUnread,
        showShareLink = !ConversationUtils.isNoteToSelfConversation(conversation),
        showRename = spreedCap != null && ConversationUtils.isNameEditable(conversation, spreedCap),
        isArchived = conversation.hasArchived,
        showLeave = conversation.canLeaveConversation,
        showDelete = conversation.canDeleteConversation
    )
}

@Composable
fun ConversationOperationsContent(
    conversation: ConversationModel,
    user: User,
    onAction: (ConversationOpsAction) -> Unit
) {
    val visibility = computeVisibility(conversation, user)
    val headerText = conversation.displayName.takeIf { it.isNotEmpty() } ?: conversation.name
    Column(modifier = Modifier.fillMaxWidth()) {
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
        ConversationOpsFavoritesGroup(visibility, onAction)
        ConversationOpsReadGroup(visibility, onAction)
        ConversationOpsManageGroup(visibility, onAction)
    }
}

@Composable
private fun ConversationOpsFavoritesGroup(
    visibility: ConversationOpsVisibility,
    onAction: (ConversationOpsAction) -> Unit
) {
    if (visibility.showRemoveFromFavorites) {
        ConversationOpsMenuItem(
            R.drawable.ic_star_black_24dp,
            stringResource(R.string.nc_remove_from_favorites)
        ) { onAction(ConversationOpsAction.RemoveFromFavorites) }
    }
    if (visibility.showAddToFavorites) {
        ConversationOpsMenuItem(
            R.drawable.ic_star_border_black_24dp,
            stringResource(R.string.nc_add_to_favorites)
        ) { onAction(ConversationOpsAction.AddToFavorites) }
    }
}

@Composable
private fun ConversationOpsReadGroup(visibility: ConversationOpsVisibility, onAction: (ConversationOpsAction) -> Unit) {
    if (visibility.showMarkAsRead) {
        ConversationOpsMenuItem(
            R.drawable.ic_mark_chat_read_24px,
            stringResource(R.string.nc_mark_as_read)
        ) { onAction(ConversationOpsAction.MarkAsRead) }
    }
    if (visibility.showMarkAsUnread) {
        ConversationOpsMenuItem(
            R.drawable.ic_mark_chat_unread_24px,
            stringResource(R.string.nc_mark_as_unread)
        ) { onAction(ConversationOpsAction.MarkAsUnread) }
    }
}

@Composable
private fun ConversationOpsManageGroup(
    visibility: ConversationOpsVisibility,
    onAction: (ConversationOpsAction) -> Unit
) {
    if (visibility.showShareLink) {
        ConversationOpsMenuItem(
            R.drawable.ic_share_action,
            stringResource(R.string.nc_share_link)
        ) { onAction(ConversationOpsAction.ShareLink) }
    }
    if (visibility.showRename) {
        ConversationOpsMenuItem(
            R.drawable.ic_pencil_grey600_24dp,
            stringResource(R.string.nc_rename)
        ) { onAction(ConversationOpsAction.Rename) }
    }
    val archiveIcon = if (visibility.isArchived) R.drawable.ic_unarchive_24px else R.drawable.outline_archive_24
    val archiveLabel = if (visibility.isArchived) {
        stringResource(R.string.unarchive_conversation)
    } else {
        stringResource(R.string.archive_conversation)
    }
    ConversationOpsMenuItem(archiveIcon, archiveLabel) { onAction(ConversationOpsAction.ToggleArchive) }
    if (visibility.showLeave) {
        ConversationOpsMenuItem(
            R.drawable.ic_exit_to_app_black_24dp,
            stringResource(R.string.nc_leave)
        ) { onAction(ConversationOpsAction.Leave) }
    }
    if (visibility.showDelete) {
        ConversationOpsMenuItem(
            R.drawable.ic_delete_grey600_24dp,
            stringResource(R.string.nc_delete_call)
        ) { onAction(ConversationOpsAction.Delete) }
    }
}

@Composable
private fun ConversationOpsMenuItem(@DrawableRes iconRes: Int, label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.bottom_sheet_item_height)),
        shape = RectangleShape,
        contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.standard_dialog_padding)),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.standard_dialog_padding)))
        Text(
            text = label,
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
        displayName = "Alice 🎉",
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
        unreadMessages = 3,
        favorite = false,
        hasArchived = false
    )

private fun previewUser() =
    User(
        id = 1L,
        userId = "user1",
        username = "user1",
        baseUrl = "https://cloud.example.com",
        token = "token",
        displayName = "Test User",
        capabilities = null
    )

@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "RTL Arabic", locale = "ar")
@Composable
private fun ConversationOperationsSheetPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            ConversationOperationsContent(
                conversation = previewConversation(),
                user = previewUser(),
                onAction = {}
            )
        }
    }
}
