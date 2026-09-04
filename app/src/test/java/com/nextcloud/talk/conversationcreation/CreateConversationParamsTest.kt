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
 * How the parameters the presets endpoint reports are folded onto the parameters of a new
 * conversation.
 *
 * The client has to apply them itself: the creation endpoint derives only the conversation
 * attributes from the preset identifier and stores whatever parameters the request carried. The
 * documented order is default preset, then selected preset, then the user's own selection, and
 * finally the forced preset, which the server applies on top by itself.
 */
class CreateConversationParamsTest {

    private fun preset(identifier: String, parameters: Map<String, Int>) =
        ConversationPresetModel(identifier = identifier, name = identifier, description = "", parameters = parameters)

    @Test
    fun `parameters the preset does not address are left untouched`() {
        val chosenByUser = CreateConversationParams(roomType = CreateConversationParams.ROOM_TYPE_PUBLIC)

        val params = chosenByUser.withParameters(
            mapOf(ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_USERS)
        )

        assertEquals(CreateConversationParams.ROOM_TYPE_PUBLIC, params.roomType)
        assertEquals(CreateConversationParams.LISTABLE_USERS, params.listable)
    }

    @Test
    fun `every documented parameter is folded in`() {
        val params = CreateConversationParams().withParameters(
            mapOf(
                ConversationParameter.ROOM_TYPE to CreateConversationParams.ROOM_TYPE_PUBLIC,
                ConversationParameter.READ_ONLY to 1,
                ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_ALL,
                ConversationParameter.MESSAGE_EXPIRATION to CreateConversationParams.MESSAGE_EXPIRATION_ONE_HOUR,
                ConversationParameter.LOBBY_STATE to 1,
                ConversationParameter.SIP_ENABLED to 1,
                ConversationParameter.PERMISSIONS to ParticipantPermissions.CUSTOM,
                ConversationParameter.RECORDING_CONSENT to 1,
                ConversationParameter.MENTION_PERMISSIONS to 1
            )
        )

        assertEquals(
            CreateConversationParams(
                roomType = CreateConversationParams.ROOM_TYPE_PUBLIC,
                readOnly = 1,
                listable = CreateConversationParams.LISTABLE_ALL,
                messageExpiration = CreateConversationParams.MESSAGE_EXPIRATION_ONE_HOUR,
                lobbyState = 1,
                sipEnabled = 1,
                permissions = ParticipantPermissions.CUSTOM,
                recordingConsent = 1,
                mentionPermissions = 1
            ),
            params
        )
    }

    @Test
    fun `the selected preset overrides the administrator configured default`() {
        val presets = listOf(
            preset(
                ConversationPresetId.DEFAULT,
                mapOf(
                    ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_ALL,
                    ConversationParameter.MENTION_PERMISSIONS to 1
                )
            ),
            preset(
                ConversationPresetId.CHANNEL,
                mapOf(ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_USERS)
            )
        )

        val params = presets.parametersFor(ConversationPresetId.CHANNEL)

        assertEquals(CreateConversationParams.LISTABLE_USERS, params.listable)
        assertEquals(1, params.mentionPermissions)
    }

    @Test
    fun `the default preset alone applies the administrator configured values`() {
        val presets = listOf(
            preset(
                ConversationPresetId.DEFAULT,
                mapOf(ConversationParameter.MESSAGE_EXPIRATION to CreateConversationParams.MESSAGE_EXPIRATION_ONE_HOUR)
            )
        )

        val params = presets.parametersFor(ConversationPresetId.DEFAULT)

        assertEquals(CreateConversationParams.MESSAGE_EXPIRATION_ONE_HOUR, params.messageExpiration)
    }

    @Test
    fun `what the user chose wins over both the default and the selected preset`() {
        val presets = listOf(
            preset(
                ConversationPresetId.DEFAULT,
                mapOf(ConversationParameter.ROOM_TYPE to CreateConversationParams.ROOM_TYPE_GROUP)
            ),
            preset(
                ConversationPresetId.VOICE_ROOM,
                mapOf(ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_USERS)
            )
        )
        val chosenByUser = mapOf(
            ConversationParameter.ROOM_TYPE to CreateConversationParams.ROOM_TYPE_PUBLIC,
            ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_ALL
        )

        val params = presets.parametersFor(ConversationPresetId.VOICE_ROOM, chosenByUser)

        assertEquals(CreateConversationParams.ROOM_TYPE_PUBLIC, params.roomType)
        assertEquals(CreateConversationParams.LISTABLE_ALL, params.listable)
    }

    @Test
    fun `a preset overrides the default it does not agree with`() {
        val presets = listOf(
            preset(
                ConversationPresetId.DEFAULT,
                mapOf(ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_NONE)
            ),
            preset(
                ConversationPresetId.CHANNEL,
                mapOf(ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_USERS)
            )
        )

        val params = presets.parametersFor(ConversationPresetId.CHANNEL)

        assertEquals(CreateConversationParams.LISTABLE_USERS, params.listable)
    }

    @Test
    fun `a preset the server does not offer leaves the parameters untouched`() {
        val presets = listOf(preset(ConversationPresetId.DEFAULT, emptyMap()))

        val params = presets.parametersFor(ConversationPresetId.WEBINAR)

        assertEquals(CreateConversationParams(), params)
    }

    @Test
    fun `a value the creation endpoint would refuse is ignored`() {
        val presets = listOf(
            preset(
                ConversationPresetId.DEFAULT,
                mapOf(
                    ConversationParameter.RECORDING_CONSENT to 2,
                    ConversationParameter.ROOM_TYPE to 1,
                    ConversationParameter.MESSAGE_EXPIRATION to -1,
                    ConversationParameter.PERMISSIONS to CreateConversationParams.PERMISSIONS_MAX + 1
                )
            )
        )

        val params = presets.parametersFor(ConversationPresetId.DEFAULT)

        assertEquals(CreateConversationParams(), params)
    }

    @Test
    fun `without any presets the parameters the user chose still apply`() {
        val chosenByUser = mapOf(ConversationParameter.ROOM_TYPE to CreateConversationParams.ROOM_TYPE_PUBLIC)

        val params = emptyList<ConversationPresetModel>().parametersFor(ConversationPresetId.DEFAULT, chosenByUser)

        assertEquals(CreateConversationParams.ROOM_TYPE_PUBLIC, params.roomType)
    }

    @Test
    fun `what an administrator pinned wins over everything else`() {
        val presets = listOf(
            preset(
                ConversationPresetId.FORCED,
                mapOf(ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_NONE)
            ),
            preset(
                ConversationPresetId.CHANNEL,
                mapOf(ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_USERS)
            )
        )
        val chosenByUser = mapOf(ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_ALL)

        val params = presets.parametersFor(ConversationPresetId.CHANNEL, chosenByUser)

        assertEquals(CreateConversationParams.LISTABLE_NONE, params.listable)
    }

    @Test
    fun `the summary leaves out the parameters an administrator pinned`() {
        val presets = listOf(
            preset(
                ConversationPresetId.FORCED,
                mapOf(ConversationParameter.MESSAGE_EXPIRATION to CreateConversationParams.MESSAGE_EXPIRATION_ONE_HOUR)
            ),
            preset(
                ConversationPresetId.VOICE_ROOM,
                mapOf(
                    ConversationParameter.MESSAGE_EXPIRATION to CreateConversationParams.MESSAGE_EXPIRATION_ONE_HOUR,
                    ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_USERS
                )
            )
        )

        val preview = presets.parametersToPreviewFor(ConversationPresetId.VOICE_ROOM)

        assertEquals(mapOf(ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_USERS), preview)
    }

    @Test
    fun `the summary also lists what the administrator configured as the default`() {
        val presets = listOf(
            preset(
                ConversationPresetId.DEFAULT,
                mapOf(ConversationParameter.MESSAGE_EXPIRATION to CreateConversationParams.MESSAGE_EXPIRATION_ONE_HOUR)
            ),
            preset(
                ConversationPresetId.WEBINAR,
                mapOf(ConversationParameter.LOBBY_STATE to 1)
            )
        )

        val preview = presets.parametersToPreviewFor(ConversationPresetId.WEBINAR)

        assertEquals(
            mapOf(
                ConversationParameter.MESSAGE_EXPIRATION to CreateConversationParams.MESSAGE_EXPIRATION_ONE_HOUR,
                ConversationParameter.LOBBY_STATE to 1
            ),
            preview
        )
    }

    @Test
    fun `the forced preset is not offered as a conversation type`() {
        val presets = listOf(
            preset(ConversationPresetId.DEFAULT, emptyMap()),
            preset(ConversationPresetId.FORCED, mapOf(ConversationParameter.LISTABLE to 0)),
            preset(ConversationPresetId.CHANNEL, emptyMap())
        )

        assertEquals(
            listOf(ConversationPresetId.DEFAULT, ConversationPresetId.CHANNEL),
            presets.selectable().map { it.identifier }
        )
    }
}
