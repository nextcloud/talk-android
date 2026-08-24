/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.ui

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R
import com.nextcloud.talk.conversationinfo.model.ParticipantModel
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.ParticipantRole
import com.nextcloud.talk.utils.ParticipantRoleUtils

private data class RemoveOption(@DrawableRes val iconRes: Int, val label: String)

private data class ParticipantOpsVisibility(
    val infoPin: String? = null,
    val showPromote: Boolean = false,
    val showDemote: Boolean = false,
    val showPromoteToOwner: Boolean = false,
    val showDemoteOwnerToModerator: Boolean = false,
    val showDemoteOwnerToUser: Boolean = false,
    val remove: RemoveOption? = null,
    val showBan: Boolean = false
)

@Composable
@Suppress("LongMethod")
private fun computeVisibility(
    model: ParticipantModel,
    conversation: ConversationModel?,
    spreedCapabilities: SpreedCapability?
): ParticipantOpsVisibility {
    val participant = model.participant
    val pin = participant.attendeePin?.takeIf { it.isNotEmpty() }
    val deleteIcon = R.drawable.ic_delete_grey600_24dp
    val canDemoteFromOwner = ParticipantRoleUtils.canBeDemotedFromOwner(participant, conversation, spreedCapabilities)
    val isOwner = participant.type == Participant.ParticipantType.OWNER
    val canModerate = conversation != null && ConversationUtils.canModerate(conversation, spreedCapabilities)

    return when {
        // Without moderation rights the sheet is informational: the header and nothing else
        !canModerate -> ParticipantOpsVisibility()

        model.isSelf -> ParticipantOpsVisibility(
            infoPin = null,
            showPromote = false,
            showDemote = false,
            showPromoteToOwner = false,
            // An owner may step down, but only as far as moderator, to avoid locking themselves out
            showDemoteOwnerToModerator = canDemoteFromOwner,
            showDemoteOwnerToUser = false,
            remove = pin?.let {
                RemoveOption(R.drawable.ic_lock_grey600_24px, stringResource(R.string.nc_attendee_pin, it))
            },
            showBan = false
        )

        participant.calculatedActorType == Participant.ActorType.GROUPS -> ParticipantOpsVisibility(
            infoPin = null,
            showPromote = false,
            showDemote = false,
            showPromoteToOwner = false,
            showDemoteOwnerToModerator = false,
            showDemoteOwnerToUser = false,
            remove = RemoveOption(deleteIcon, stringResource(R.string.nc_remove_group_and_members)),
            showBan = false
        )

        participant.calculatedActorType == Participant.ActorType.CIRCLES -> ParticipantOpsVisibility(
            infoPin = null,
            showPromote = false,
            showDemote = false,
            showPromoteToOwner = false,
            showDemoteOwnerToModerator = false,
            showDemoteOwnerToUser = false,
            remove = RemoveOption(deleteIcon, stringResource(R.string.nc_remove_team_and_members)),
            showBan = false
        )

        isOwner -> ParticipantOpsVisibility(
            infoPin = pin,
            showPromote = false,
            showDemote = false,
            showPromoteToOwner = false,
            showDemoteOwnerToModerator = canDemoteFromOwner,
            showDemoteOwnerToUser = canDemoteFromOwner,
            remove = null,
            showBan = false
        )

        else -> ParticipantOpsVisibility(
            infoPin = pin,
            showPromote = participant.type == Participant.ParticipantType.USER ||
                participant.type == Participant.ParticipantType.GUEST,
            showDemote = participant.type == Participant.ParticipantType.MODERATOR ||
                participant.type == Participant.ParticipantType.GUEST_MODERATOR,
            showPromoteToOwner = ParticipantRoleUtils.canBePromotedToOwner(
                participant,
                conversation,
                spreedCapabilities
            ),
            showDemoteOwnerToModerator = false,
            showDemoteOwnerToUser = false,
            remove = RemoveOption(deleteIcon, stringResource(R.string.nc_remove_participant)),
            showBan = spreedCapabilities != null && CapabilitiesUtil.isBanningAvailable(spreedCapabilities)
        )
    }
}

@Composable
fun ParticipantOperationsContent(
    model: ParticipantModel,
    conversation: ConversationModel?,
    spreedCapabilities: SpreedCapability?,
    onAction: (ParticipantOpsAction) -> Unit
) {
    val visibility = computeVisibility(model, conversation, spreedCapabilities)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
    ) {
        ParticipantOpsHeader(model)
        visibility.infoPin?.let { ParticipantOpsInfoRow(R.drawable.ic_lock_grey600_24px, it) }
        // The icon shows the rank the action leads to, not the action itself
        if (visibility.showPromoteToOwner) {
            ParticipantOpsMenuItem(R.drawable.outline_crown_24, stringResource(R.string.nc_promote_to_owner)) {
                onAction(ParticipantOpsAction.PromoteToOwner)
            }
        }
        if (visibility.showPromote) {
            ParticipantOpsMenuItem(R.drawable.outline_shield_24, stringResource(R.string.nc_promote)) {
                onAction(ParticipantOpsAction.PromoteToModerator)
            }
        }
        if (visibility.showDemoteOwnerToModerator) {
            ParticipantOpsMenuItem(
                R.drawable.outline_shield_24,
                stringResource(R.string.nc_demote_owner_to_moderator)
            ) {
                onAction(ParticipantOpsAction.DemoteOwnerToModerator)
            }
        }
        if (visibility.showDemoteOwnerToUser) {
            ParticipantOpsMenuItem(
                R.drawable.ic_baseline_person_24,
                stringResource(R.string.nc_demote_owner_to_participant)
            ) {
                onAction(ParticipantOpsAction.DemoteOwnerToUser)
            }
        }
        if (visibility.showDemote) {
            ParticipantOpsMenuItem(R.drawable.ic_baseline_person_24, stringResource(R.string.nc_demote)) {
                onAction(ParticipantOpsAction.DemoteFromModerator)
            }
        }
        visibility.remove?.let { remove ->
            ParticipantOpsMenuItem(remove.iconRes, remove.label) {
                onAction(ParticipantOpsAction.RemoveFromConversation)
            }
        }
        if (visibility.showBan) {
            ParticipantOpsMenuItem(R.drawable.baseline_block_24, stringResource(R.string.ban_participant)) {
                onAction(ParticipantOpsAction.Ban)
            }
        }
    }
}

@Composable
private fun ParticipantOpsHeader(model: ParticipantModel) {
    val displayName = model.participant.displayName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.nc_guest)
    val roleLabelRes = ParticipantRoleUtils.labelRes(model.role)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(R.dimen.standard_dialog_padding),
                vertical = dimensionResource(R.dimen.standard_half_padding)
            )
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (roleLabelRes != null) {
            Text(
                text = stringResource(roleLabelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ParticipantOpsInfoRow(@DrawableRes iconRes: Int, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.bottom_sheet_item_height))
            .padding(horizontal = dimensionResource(R.dimen.standard_dialog_padding)),
        verticalAlignment = Alignment.CenterVertically
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun ParticipantOpsMenuItem(@DrawableRes iconRes: Int, label: String, onClick: () -> Unit) {
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

private fun previewParticipant(
    displayName: String,
    type: Participant.ParticipantType,
    role: ParticipantRole,
    attendeePin: String? = null,
    isSelf: Boolean = false
) = ParticipantModel(
    participant = Participant(
        actorType = Participant.ActorType.USERS,
        actorId = displayName.lowercase(),
        displayName = displayName,
        type = type,
        attendeePin = attendeePin
    ),
    isOnline = true,
    role = role,
    isSelf = isSelf
)

@Composable
private fun ParticipantOpsPreviewWrapper(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column { content() }
        }
    }
}

@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, name = "RTL Arabic", locale = "ar")
@Composable
private fun ParticipantOperationsSheetModeratorPreview() {
    ParticipantOpsPreviewWrapper {
        ParticipantOperationsContent(
            model = previewParticipant(
                "Bob Smith",
                Participant.ParticipantType.MODERATOR,
                ParticipantRole.MODERATOR
            ),
            conversation = null,
            spreedCapabilities = null,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ParticipantOperationsSheetUserWithPinPreview() {
    ParticipantOpsPreviewWrapper {
        ParticipantOperationsContent(
            model = previewParticipant(
                "Carol Danvers",
                Participant.ParticipantType.USER,
                ParticipantRole.NONE,
                attendeePin = "123456"
            ),
            conversation = null,
            spreedCapabilities = null,
            onAction = {}
        )
    }
}

/** Without moderation rights the sheet only names the participant and their rank. */
@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ParticipantOperationsSheetInformationalPreview() {
    ParticipantOpsPreviewWrapper {
        ParticipantOperationsContent(
            model = previewParticipant(
                "Alice Johnson",
                Participant.ParticipantType.OWNER,
                ParticipantRole.OWNER
            ),
            conversation = null,
            spreedCapabilities = null,
            onAction = {}
        )
    }
}

@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ParticipantOperationsSheetOwnerPreview() {
    ParticipantOpsPreviewWrapper {
        ParticipantOperationsContent(
            model = previewParticipant(
                "Alice Johnson",
                Participant.ParticipantType.OWNER,
                ParticipantRole.OWNER
            ),
            conversation = null,
            spreedCapabilities = null,
            onAction = {}
        )
    }
}
