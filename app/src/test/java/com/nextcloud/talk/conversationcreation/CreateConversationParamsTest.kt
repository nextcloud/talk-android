/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationcreation

import com.nextcloud.talk.utils.ParticipantPermissions
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Preset parameters and how they fold onto the parameters of a new conversation.
 *
 * The expected values mirror the preset definitions of the server in `lib/RoomPresets` of the
 * `spreed` app, which the client has to apply itself: the creation endpoint derives only the
 * conversation attributes from the preset identifier and stores whatever parameters the request
 * carried.
 */
class CreateConversationParamsTest {

    @Test
    fun `default preset applies no parameters of its own`() {
        val params = CreateConversationParams().withParameters(
            ConversationPresetParameters.of(ConversationPreset.DEFAULT)
        )

        assertEquals(CreateConversationParams(), params)
    }

    @Test
    fun `voice room is listable for users and expires messages after an hour`() {
        val params = CreateConversationParams().withParameters(
            ConversationPresetParameters.of(ConversationPreset.VOICE_ROOM)
        )

        assertEquals(CreateConversationParams.LISTABLE_USERS, params.listable)
        assertEquals(CreateConversationParams.MESSAGE_EXPIRATION_ONE_HOUR, params.messageExpiration)
    }

    @Test
    fun `channel grants reactions only and is listable for users`() {
        val params = CreateConversationParams().withParameters(
            ConversationPresetParameters.of(ConversationPreset.CHANNEL)
        )

        assertEquals(
            ParticipantPermissions.CUSTOM or ParticipantPermissions.REACT,
            params.permissions
        )
        assertEquals(CreateConversationParams.LISTABLE_USERS, params.listable)
    }

    @Test
    fun `announcement is a channel that is not listable`() {
        val channel = CreateConversationParams().withParameters(
            ConversationPresetParameters.of(ConversationPreset.CHANNEL)
        )
        val announcement = CreateConversationParams().withParameters(
            ConversationPresetParameters.of(ConversationPreset.ANNOUNCEMENT)
        )

        assertEquals(channel.permissions, announcement.permissions)
        assertEquals(CreateConversationParams.LISTABLE_NONE, announcement.listable)
    }

    @Test
    fun `parameters the preset does not address are left untouched`() {
        val chosenByUser = CreateConversationParams(roomType = CreateConversationParams.ROOM_TYPE_PUBLIC)

        val params = chosenByUser.withParameters(
            ConversationPresetParameters.of(ConversationPreset.CHANNEL)
        )

        assertEquals(CreateConversationParams.ROOM_TYPE_PUBLIC, params.roomType)
    }

    @Test
    fun `an unknown preset leaves the parameters at their defaults`() {
        val params = CreateConversationParams().withParameters(ConversationPresetParameters.of("webinar"))

        assertEquals(CreateConversationParams(), params)
    }
}
