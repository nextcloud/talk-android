/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Tim Krüger
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.utils

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew

/**
 * see https://nextcloud-talk.readthedocs.io/en/latest/constants/#attendee-permissions
 */
class ParticipantPermissions(
    private val user: User,
    private val conversation: Conversation
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

    private fun hasConversationPermissions(): Boolean {
        return CapabilitiesUtilNew.hasSpreedFeatureCapability(
            user,
            "conversation-permissions"
        )
    }

    fun canIgnoreLobby(): Boolean {
        if (hasConversationPermissions()) {
            return canIgnoreLobby
        }

        return false
    }

    fun canStartCall(): Boolean {
        return if (hasConversationPermissions()) {
            canStartCall
        } else {
            conversation.canStartCall
        }
    }

    fun canPublishAudio(): Boolean {
        return if (hasConversationPermissions()) {
            canPublishAudio
        } else {
            true
        }
    }

    fun canPublishVideo(): Boolean {
        return if (hasConversationPermissions()) {
            canPublishVideo
        } else {
            true
        }
    }

    fun hasChatPermission(): Boolean {
        if (CapabilitiesUtilNew.hasSpreedFeatureCapability(user, "chat-permission")) {
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
