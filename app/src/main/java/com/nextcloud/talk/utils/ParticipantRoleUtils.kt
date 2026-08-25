/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.nextcloud.talk.R
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.participants.Participant

/**
 * Moderation rank of a participant, as far as it is worth showing in the participants list.
 */
enum class ParticipantRole {
    OWNER,
    MODERATOR,
    NONE
}

object ParticipantRoleUtils {

    /**
     * Conversation types in which no participant carries a meaningful rank: both participants of a
     * one-to-one conversation are owners by design, and the changelog conversation only ever has a
     * single participant.
     */
    private val RANKLESS_CONVERSATION_TYPES = setOf(
        ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL,
        ConversationEnums.ConversationType.FORMER_ONE_TO_ONE,
        ConversationEnums.ConversationType.ROOM_SYSTEM
    )

    /**
     * A null [conversationType] yields [ParticipantRole.NONE]: without knowing the conversation type
     * we cannot tell whether a rank is meaningful, and not showing one is the safer default.
     */
    fun roleOf(participant: Participant, conversationType: ConversationEnums.ConversationType?): ParticipantRole {
        if (conversationType == null || conversationType in RANKLESS_CONVERSATION_TYPES) {
            return ParticipantRole.NONE
        }

        return when (participant.type) {
            Participant.ParticipantType.OWNER -> ParticipantRole.OWNER
            Participant.ParticipantType.MODERATOR,
            Participant.ParticipantType.GUEST_MODERATOR -> ParticipantRole.MODERATOR
            else -> ParticipantRole.NONE
        }
    }

    @StringRes
    fun labelRes(role: ParticipantRole): Int? =
        when (role) {
            ParticipantRole.OWNER -> R.string.nc_owner
            ParticipantRole.MODERATOR -> R.string.nc_moderator
            ParticipantRole.NONE -> null
        }

    @DrawableRes
    fun iconRes(role: ParticipantRole): Int? =
        when (role) {
            ParticipantRole.OWNER -> R.drawable.outline_crown_24
            ParticipantRole.MODERATOR -> R.drawable.outline_shield_24
            ParticipantRole.NONE -> null
        }

    /**
     * Conversation types in which the owner rank can be handed out. One-to-one conversations already
     * have two owners by design, and note-to-self and changelog conversations only ever have one
     * participant.
     */
    private val OWNER_CHANGE_CONVERSATION_TYPES = setOf(
        ConversationEnums.ConversationType.ROOM_GROUP_CALL,
        ConversationEnums.ConversationType.ROOM_PUBLIC_CALL
    )

    /**
     * Object types in which the owner rank can be handed out. Everything else binds the conversation
     * to an object that already implies an owner, such as a share, a calendar event or a parent
     * conversation.
     *
     * The server also allows its `classified_persist` and `extended_conversation` object types, which
     * [ConversationEnums.ObjectType] does not model; those decode to
     * [ConversationEnums.ObjectType.DEFAULT] here.
     */
    private val OWNER_CHANGE_OBJECT_TYPES = setOf(
        ConversationEnums.ObjectType.DEFAULT,
        ConversationEnums.ObjectType.CLASSIFIED,
        ConversationEnums.ObjectType.INSTANT_MEETING
    )

    /** `USER_FOLLOWING_LINK` is this client's name for the server's `USER_SELF_JOINED`. */
    private val PROMOTABLE_TO_OWNER = setOf(
        Participant.ParticipantType.USER,
        Participant.ParticipantType.USER_FOLLOWING_LINK,
        Participant.ParticipantType.MODERATOR
    )

    /**
     * Whether ownership can be handed over at all: only an owner may do it, only a user may hold it,
     * and only in a conversation that is not bound to an object with an implicit owner. Mirrors
     * `ParticipantService::updateParticipantTypeByModerator`, which enforces all of it again.
     */
    fun canChangeOwnership(
        participant: Participant,
        conversation: ConversationModel?,
        spreedCapabilities: SpreedCapability?
    ): Boolean =
        CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.PROMOTE_DEMOTE_OWNER) &&
            conversation != null &&
            conversation.participantType == Participant.ParticipantType.OWNER &&
            conversation.type in OWNER_CHANGE_CONVERSATION_TYPES &&
            conversation.objectType in OWNER_CHANGE_OBJECT_TYPES &&
            participant.calculatedActorType == Participant.ActorType.USERS

    fun canBePromotedToOwner(
        participant: Participant,
        conversation: ConversationModel?,
        spreedCapabilities: SpreedCapability?
    ): Boolean =
        canChangeOwnership(participant, conversation, spreedCapabilities) &&
            participant.type in PROMOTABLE_TO_OWNER

    fun canBeDemotedFromOwner(
        participant: Participant,
        conversation: ConversationModel?,
        spreedCapabilities: SpreedCapability?
    ): Boolean =
        canChangeOwnership(participant, conversation, spreedCapabilities) &&
            participant.type == Participant.ParticipantType.OWNER
}
