/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.conversations.ConversationDto
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.ParticipantDto

object ConversationUtils {
    private val TAG = ConversationUtils::class.java.simpleName

    fun isPublic(conversation: ConversationModel): Boolean =
        ConversationEnums.ConversationType.ROOM_PUBLIC_CALL == conversation.type

    fun isGuest(conversation: ConversationModel): Boolean =
        ParticipantDto.ParticipantType.GUEST == conversation.participantType ||
            ParticipantDto.ParticipantType.GUEST_MODERATOR == conversation.participantType ||
            ParticipantDto.ParticipantType.USER_FOLLOWING_LINK == conversation.participantType

    fun isParticipantOwnerOrModerator(conversation: ConversationModel): Boolean =
        ParticipantDto.ParticipantType.OWNER == conversation.participantType ||
            ParticipantDto.ParticipantType.GUEST_MODERATOR == conversation.participantType ||
            ParticipantDto.ParticipantType.MODERATOR == conversation.participantType

    fun isLockedOneToOne(conversation: ConversationModel, spreedCapabilities: SpreedCapabilityDto?): Boolean =
        conversation.type == ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
            CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.LOCKED_ONE_TO_ONE)

    fun canModerate(conversation: ConversationModel, spreedCapabilities: SpreedCapabilityDto?): Boolean =
        isParticipantOwnerOrModerator(conversation) &&
            !isLockedOneToOne(conversation, spreedCapabilities) &&
            conversation.type != ConversationEnums.ConversationType.FORMER_ONE_TO_ONE &&
            !isNoteToSelfConversation(conversation)

    fun isConversationReadOnlyAvailable(
        conversation: ConversationModel,
        spreedCapabilities: SpreedCapabilityDto
    ): Boolean =
        CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.READ_ONLY_ROOMS) &&
            canModerate(conversation, spreedCapabilities)

    fun isLobbyViewApplicable(conversation: ConversationModel, spreedCapabilities: SpreedCapabilityDto): Boolean =
        !canModerate(conversation, spreedCapabilities) &&
            (
                conversation.type == ConversationEnums.ConversationType.ROOM_GROUP_CALL ||
                    conversation.type == ConversationEnums.ConversationType.ROOM_PUBLIC_CALL
                )

    fun isNameEditable(conversation: ConversationModel, spreedCapabilities: SpreedCapabilityDto): Boolean =
        canModerate(conversation, spreedCapabilities) &&
            ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL != conversation.type

    fun isNoteToSelfConversation(currentConversation: ConversationModel?): Boolean =
        currentConversation != null &&
            currentConversation.type == ConversationEnums.ConversationType.NOTE_TO_SELF

    private fun ConversationModel?.hasAttribute(flag: Int): Boolean =
        this?.attributes?.let { it and flag != 0 } ?: false

    fun ConversationModel?.checkIfVoiceRoom(): Boolean = hasAttribute(ConversationEnums.ATTRIBUTE_IS_VOICE_ROOM)
    fun ConversationModel?.isClassifiedAttribute(): Boolean = hasAttribute(ConversationEnums.ATTRIBUTE_IS_CLASSIFIED)
    fun ConversationModel?.isChannelAttribute(): Boolean = hasAttribute(ConversationEnums.ATTRIBUTE_IS_CHANNEL)
    fun ConversationModel?.isAnnouncementAttribute(): Boolean =
        hasAttribute(ConversationEnums.ATTRIBUTE_IS_ANNOUNCEMENT)

    fun isChannel(conversation: ConversationModel?, spreedCapabilities: SpreedCapabilityDto?): Boolean =
        conversation.isChannelAttribute() &&
            CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.ANNOUNCEMENT_PRESET)

    fun isClassified(conversation: ConversationModel, spreedCapabilities: SpreedCapabilityDto?): Boolean =
        CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.CLASSIFIED_CONVERSATIONS) &&
            conversation.isClassifiedAttribute()

    @Deprecated("Use isClassified(conversation: ConversationModel, spreedCapabilities: SpreedCapabilityDto?)")
    fun isClassified(conversation: ConversationDto, spreedCapabilities: SpreedCapabilityDto?): Boolean =
        CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.CLASSIFIED_CONVERSATIONS) &&
            ((conversation.attributes ?: 0) and ConversationEnums.ATTRIBUTE_IS_CLASSIFIED) != 0
}
