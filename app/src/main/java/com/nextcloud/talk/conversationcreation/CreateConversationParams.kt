/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation

import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.ParticipantPermissions

/**
 * The parameters a conversation is created with, before the request body is built.
 */
data class CreateConversationParams(
    val roomType: Int = ROOM_TYPE_GROUP,
    val readOnly: Int = READ_WRITE,
    val listable: Int = LISTABLE_NONE,
    val messageExpiration: Int = MESSAGE_EXPIRATION_OFF,
    val lobbyState: Int = LOBBY_DISABLED,
    val sipEnabled: Int = SIP_DISABLED,
    val permissions: Int = ParticipantPermissions.DEFAULT,
    val recordingConsent: Int = CapabilitiesUtil.RECORDING_CONSENT_NOT_REQUIRED,
    val mentionPermissions: Int = MENTION_PERMISSIONS_EVERYONE
) {
    companion object {
        const val ROOM_TYPE_GROUP = 2
        const val ROOM_TYPE_PUBLIC = 3

        const val READ_WRITE = 0

        const val LISTABLE_NONE = 0
        const val LISTABLE_USERS = 1
        const val LISTABLE_ALL = 2

        const val MESSAGE_EXPIRATION_OFF = 0
        const val MESSAGE_EXPIRATION_ONE_HOUR = 3600

        const val LOBBY_DISABLED = 0

        const val SIP_DISABLED = 0

        const val MENTION_PERMISSIONS_EVERYONE = 0
    }
}

/**
 * Names of the conversation parameters as the server addresses them.
 */
object ConversationParameter {
    const val ROOM_TYPE = "roomType"
    const val READ_ONLY = "readOnly"
    const val LISTABLE = "listable"
    const val MESSAGE_EXPIRATION = "messageExpiration"
    const val LOBBY_STATE = "lobbyState"
    const val SIP_ENABLED = "sipEnabled"
    const val PERMISSIONS = "permissions"
    const val RECORDING_CONSENT = "recordingConsent"
    const val MENTION_PERMISSIONS = "mentionPermissions"
}

/**
 * Identifiers of the conversation presets the server offers.
 */
object ConversationPreset {
    const val DEFAULT = "default"
    const val VOICE_ROOM = "voiceroom"
    const val CHANNEL = "channel"
    const val ANNOUNCEMENT = "announcement"
}

/**
 * Parameters each preset applies, mirroring the definitions of the server.
 */
object ConversationPresetParameters {

    fun of(preset: String): Map<String, Int> = presets[preset].orEmpty()

    private val channel = mapOf(
        ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_USERS,
        ConversationParameter.PERMISSIONS to (ParticipantPermissions.CUSTOM or ParticipantPermissions.REACT)
    )

    private val presets = mapOf(
        ConversationPreset.DEFAULT to emptyMap(),
        ConversationPreset.VOICE_ROOM to mapOf(
            ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_USERS,
            ConversationParameter.MESSAGE_EXPIRATION to CreateConversationParams.MESSAGE_EXPIRATION_ONE_HOUR
        ),
        ConversationPreset.CHANNEL to channel,
        ConversationPreset.ANNOUNCEMENT to channel + mapOf(
            ConversationParameter.LISTABLE to CreateConversationParams.LISTABLE_NONE
        )
    )
}

/**
 * Returns these parameters with the given ones applied on top, leaving those the caller does not
 * address untouched.
 */
fun CreateConversationParams.withParameters(parameters: Map<String, Int>): CreateConversationParams =
    copy(
        roomType = parameters[ConversationParameter.ROOM_TYPE] ?: roomType,
        readOnly = parameters[ConversationParameter.READ_ONLY] ?: readOnly,
        listable = parameters[ConversationParameter.LISTABLE] ?: listable,
        messageExpiration = parameters[ConversationParameter.MESSAGE_EXPIRATION] ?: messageExpiration,
        lobbyState = parameters[ConversationParameter.LOBBY_STATE] ?: lobbyState,
        sipEnabled = parameters[ConversationParameter.SIP_ENABLED] ?: sipEnabled,
        permissions = parameters[ConversationParameter.PERMISSIONS] ?: permissions,
        recordingConsent = parameters[ConversationParameter.RECORDING_CONSENT] ?: recordingConsent,
        mentionPermissions = parameters[ConversationParameter.MENTION_PERMISSIONS] ?: mentionPermissions
    )
