/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant

object ConversationUtils {
    private val TAG = ConversationUtils::class.java.simpleName

    fun isPublic(conversation: ConversationModel): Boolean =
        ConversationEnums.ConversationType.ROOM_PUBLIC_CALL == conversation.type

    fun isGuest(conversation: ConversationModel): Boolean =
        Participant.ParticipantType.GUEST == conversation.participantType ||
            Participant.ParticipantType.GUEST_MODERATOR == conversation.participantType ||
            Participant.ParticipantType.USER_FOLLOWING_LINK == conversation.participantType

    fun isParticipantOwnerOrModerator(conversation: ConversationModel): Boolean =
        Participant.ParticipantType.OWNER == conversation.participantType ||
            Participant.ParticipantType.GUEST_MODERATOR == conversation.participantType ||
            Participant.ParticipantType.MODERATOR == conversation.participantType

    fun isLockedOneToOne(conversation: ConversationModel, spreedCapabilities: SpreedCapability): Boolean =
        conversation.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
            CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.LOCKED_ONE_TO_ONE)

    fun canModerate(conversation: ConversationModel, spreedCapabilities: SpreedCapability): Boolean =
        isParticipantOwnerOrModerator(conversation) &&
            !isLockedOneToOne(conversation, spreedCapabilities) &&
            conversation.type != ConversationEnums.ConversationType.FORMER_ONE_TO_ONE &&
            !isNoteToSelfConversation(conversation)

    fun isConversationReadOnlyAvailable(
        conversation: ConversationModel,
        spreedCapabilities: SpreedCapability
    ): Boolean =
        CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.READ_ONLY_ROOMS) &&
            canModerate(conversation, spreedCapabilities)

    fun isLobbyViewApplicable(conversation: ConversationModel, spreedCapabilities: SpreedCapability): Boolean =
        !canModerate(conversation, spreedCapabilities) &&
            (
                conversation.type == ConversationEnums.ConversationType.ROOM_GROUP_CALL ||
                    conversation.type == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL
                )

    fun isNameEditable(conversation: ConversationModel, spreedCapabilities: SpreedCapability): Boolean =
        canModerate(conversation, spreedCapabilities) &&
            ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL != conversation.type

    fun isNoteToSelfConversation(currentConversation: ConversationModel?): Boolean =
        currentConversation != null &&
            currentConversation.type == ConversationEnums.ConversationType.NOTE_TO_SELF
}
