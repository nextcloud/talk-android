/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability

/**
 * see https://nextcloud-talk.readthedocs.io/en/latest/constants/#attendee-permissions
 */
class ParticipantPermissions(
    private val spreedCapabilities: SpreedCapability,
    private val conversation: ConversationModel
) {
    val isDefault = (conversation.permissions and DEFAULT) == DEFAULT
    val isCustom = (conversation.permissions and CUSTOM) == CUSTOM
    private val canStartCall = (conversation.permissions and START_CALL) == START_CALL
    val canJoinCall = (conversation.permissions and JOIN_CALL) == JOIN_CALL
    private val canIgnoreLobby = (conversation.permissions and CAN_IGNORE_LOBBY) == CAN_IGNORE_LOBBY
    private val canPublishAudio = (conversation.permissions and PUBLISH_AUDIO) == PUBLISH_AUDIO
    private val canPublishVideo = (conversation.permissions and PUBLISH_VIDEO) == PUBLISH_VIDEO
    val canPublishScreen = (conversation.permissions and PUBLISH_SCREEN) == PUBLISH_SCREEN
    private val hasChatPermission = (conversation.permissions and CHAT) == CHAT

    private fun hasConversationPermissions(): Boolean =
        CapabilitiesUtil.hasSpreedFeatureCapability(
            spreedCapabilities,
            SpreedFeatures.CONVERSATION_PERMISSION
        )

    fun canIgnoreLobby(): Boolean {
        if (hasConversationPermissions()) {
            return canIgnoreLobby
        }

        return false
    }

    fun canStartCall(): Boolean =
        if (hasConversationPermissions()) {
            canStartCall
        } else {
            conversation.canStartCall
        }

    fun canPublishAudio(): Boolean =
        if (hasConversationPermissions()) {
            canPublishAudio
        } else {
            true
        }

    fun canPublishVideo(): Boolean =
        if (hasConversationPermissions()) {
            canPublishVideo
        } else {
            true
        }

    fun hasChatPermission(): Boolean {
        if (CapabilitiesUtil.hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.CHAT_PERMISSION)) {
            return hasChatPermission
        }
        // if capability is not available then the spreed version doesn't support to restrict this
        return true
    }

    companion object {

        val TAG = ParticipantPermissions::class.simpleName
        const val DEFAULT = 0
        const val CUSTOM = 1
        const val START_CALL = 2
        const val JOIN_CALL = 4
        const val CAN_IGNORE_LOBBY = 8
        const val PUBLISH_AUDIO = 16
        const val PUBLISH_VIDEO = 32
        const val PUBLISH_SCREEN = 64
        const val CHAT = 128
    }
}
