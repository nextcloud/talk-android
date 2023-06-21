package com.nextcloud.talk.utils

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.ConversationType
import com.nextcloud.talk.models.domain.ParticipantType
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew

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

object ConversationUtils {
    private val TAG = ConversationUtils::class.java.simpleName

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

    private fun isLockedOneToOne(conversation: ConversationModel, conversationUser: User): Boolean {
        return conversation.type == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
            CapabilitiesUtilNew.hasSpreedFeatureCapability(conversationUser, "locked-one-to-one-rooms")
    }

    fun canModerate(conversation: ConversationModel, conversationUser: User): Boolean {
        return isParticipantOwnerOrModerator(conversation) &&
            !isLockedOneToOne(conversation, conversationUser) &&
            conversation.type != ConversationType.FORMER_ONE_TO_ONE
    }

    fun isLobbyViewApplicable(conversation: ConversationModel, conversationUser: User): Boolean {
        return !canModerate(conversation, conversationUser) &&
            (
                conversation.type == ConversationType.ROOM_GROUP_CALL ||
                    conversation.type == ConversationType.ROOM_PUBLIC_CALL
                )
    }

    fun isNameEditable(conversation: ConversationModel, conversationUser: User): Boolean {
        return canModerate(conversation, conversationUser) &&
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

    fun canDelete(conversation: ConversationModel, conversationUser: User): Boolean {
        return if (conversation.canDeleteConversation != null) {
            // Available since APIv2
            conversation.canDeleteConversation!!
        } else {
            canModerate(conversation, conversationUser)
            // Fallback for APIv1
        }
    }
}
