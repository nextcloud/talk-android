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
}
