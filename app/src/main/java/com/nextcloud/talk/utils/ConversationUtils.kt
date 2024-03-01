/*
 * Nextcloud Talk application
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.ConversationType
import com.nextcloud.talk.models.domain.ParticipantType
import com.nextcloud.talk.models.json.capabilities.SpreedCapability

object ConversationUtils {
    private val TAG = ConversationUtils::class.java.simpleName
    private const val NOTE_TO_SELF = "Note to self"

    fun isPublic(conversation: ConversationModel): Boolean {
        return ConversationType.ROOM_PUBLIC_CALL == conversation.type
    }

    fun isGuest(conversation: ConversationModel): Boolean {
        return ParticipantType.GUEST == conversation.participantType ||
            ParticipantType.GUEST_MODERATOR == conversation.participantType ||
            ParticipantType.USER_FOLLOWING_LINK == conversation.participantType
    }

    fun isParticipantOwnerOrModerator(conversation: ConversationModel): Boolean {
        return ParticipantType.OWNER == conversation.participantType ||
            ParticipantType.GUEST_MODERATOR == conversation.participantType ||
            ParticipantType.MODERATOR == conversation.participantType
    }

    fun isLockedOneToOne(conversation: ConversationModel, spreedCapabilities: SpreedCapability): Boolean {
        return conversation.type == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
            CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, "locked-one-to-one-rooms")
    }

    fun canModerate(conversation: ConversationModel, spreedCapabilities: SpreedCapability): Boolean {
        return isParticipantOwnerOrModerator(conversation) &&
            !isLockedOneToOne(conversation, spreedCapabilities) &&
            conversation.type != ConversationType.FORMER_ONE_TO_ONE &&
            !isNoteToSelfConversation(conversation)
    }

    fun isLobbyViewApplicable(conversation: ConversationModel, spreedCapabilities: SpreedCapability): Boolean {
        return !canModerate(conversation, spreedCapabilities) &&
            (
                conversation.type == ConversationType.ROOM_GROUP_CALL ||
                    conversation.type == ConversationType.ROOM_PUBLIC_CALL
                )
    }

    fun isNameEditable(conversation: ConversationModel, spreedCapabilities: SpreedCapability): Boolean {
        return canModerate(conversation, spreedCapabilities) &&
            ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL != conversation.type
    }

    fun canLeave(conversation: ConversationModel): Boolean {
        return if (conversation.canLeaveConversation != null) {
            // Available since APIv2
            conversation.canLeaveConversation!!
        } else {
            true
        }
    }

    fun canDelete(conversation: ConversationModel, spreedCapability: SpreedCapability): Boolean {
        return if (conversation.canDeleteConversation != null) {
            // Available since APIv2
            conversation.canDeleteConversation!!
        } else {
            canModerate(conversation, spreedCapability)
            // Fallback for APIv1
        }
    }

    fun isNoteToSelfConversation(currentConversation: ConversationModel?): Boolean {
        return currentConversation != null && currentConversation.name == NOTE_TO_SELF
    }
}
