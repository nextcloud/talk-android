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
        const val READ_ONLY = 1

        const val LISTABLE_NONE = 0
        const val LISTABLE_USERS = 1
        const val LISTABLE_ALL = 2

        const val MESSAGE_EXPIRATION_OFF = 0
        const val MESSAGE_EXPIRATION_ONE_HOUR = 3600

        const val LOBBY_DISABLED = 0
        const val LOBBY_MODERATORS_ONLY = 1

        const val SIP_DISABLED = 0
        const val SIP_WITHOUT_PIN = 2

        const val MENTION_PERMISSIONS_EVERYONE = 0
        const val MENTION_PERMISSIONS_MODERATORS = 1

        const val PERMISSIONS_MAX = 511
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
 * Returns these parameters with the given ones applied on top, leaving untouched what the caller
 * does not address and what the conversation creation endpoint would refuse.
 */
fun CreateConversationParams.withParameters(parameters: Map<String, Int>): CreateConversationParams =
    copy(
        roomType = parameters.accepted(ConversationParameter.ROOM_TYPE, roomType) { it in roomTypes },
        readOnly = parameters.accepted(ConversationParameter.READ_ONLY, readOnly) { it in readOnlyStates },
        listable = parameters.accepted(ConversationParameter.LISTABLE, listable) { it in listableScopes },
        messageExpiration = parameters.accepted(ConversationParameter.MESSAGE_EXPIRATION, messageExpiration) {
            it >= CreateConversationParams.MESSAGE_EXPIRATION_OFF
        },
        lobbyState = parameters.accepted(ConversationParameter.LOBBY_STATE, lobbyState) { it in lobbyStates },
        sipEnabled = parameters.accepted(ConversationParameter.SIP_ENABLED, sipEnabled) { it in sipStates },
        permissions = parameters.accepted(ConversationParameter.PERMISSIONS, permissions) {
            it in permissionsRange
        },
        recordingConsent = parameters.accepted(ConversationParameter.RECORDING_CONSENT, recordingConsent) {
            it in recordingConsentStates
        },
        mentionPermissions = parameters.accepted(ConversationParameter.MENTION_PERMISSIONS, mentionPermissions) {
            it in mentionPermissionsStates
        }
    )

private fun Map<String, Int>.accepted(parameter: String, current: Int, isAccepted: (Int) -> Boolean): Int =
    this[parameter]?.takeIf(isAccepted) ?: current

private val roomTypes = setOf(CreateConversationParams.ROOM_TYPE_GROUP, CreateConversationParams.ROOM_TYPE_PUBLIC)
private val readOnlyStates = CreateConversationParams.READ_WRITE..CreateConversationParams.READ_ONLY
private val listableScopes = CreateConversationParams.LISTABLE_NONE..CreateConversationParams.LISTABLE_ALL
private val lobbyStates = CreateConversationParams.LOBBY_DISABLED..CreateConversationParams.LOBBY_MODERATORS_ONLY
private val sipStates = CreateConversationParams.SIP_DISABLED..CreateConversationParams.SIP_WITHOUT_PIN
private val permissionsRange = ParticipantPermissions.DEFAULT..CreateConversationParams.PERMISSIONS_MAX
private val recordingConsentStates =
    CapabilitiesUtil.RECORDING_CONSENT_NOT_REQUIRED..CapabilitiesUtil.RECORDING_CONSENT_REQUIRED
private val mentionPermissionsStates =
    CreateConversationParams.MENTION_PERMISSIONS_EVERYONE..CreateConversationParams.MENTION_PERMISSIONS_MODERATORS
