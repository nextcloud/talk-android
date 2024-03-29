/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
            CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.LOCKED_ONE_TO_ONE)
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
