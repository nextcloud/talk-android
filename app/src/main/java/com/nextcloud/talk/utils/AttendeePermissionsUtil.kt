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
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew

/**
 * see https://nextcloud-talk.readthedocs.io/en/latest/constants/#attendee-permissions
 */
class AttendeePermissionsUtil(flag: Int) {

    val isDefault = (flag and DEFAULT) == DEFAULT
    val isCustom = (flag and CUSTOM) == CUSTOM
    val canStartCall = (flag and START_CALL) == START_CALL
    val canJoinCall = (flag and JOIN_CALL) == JOIN_CALL
    val canIgnoreLobby = (flag and CAN_IGNORE_LOBBY) == CAN_IGNORE_LOBBY
    val canPublishAudio = (flag and PUBLISH_AUDIO) == PUBLISH_AUDIO
    val canPublishVideo = (flag and PUBLISH_VIDEO) == PUBLISH_VIDEO
    val canPublishScreen = (flag and PUBLISH_SCREEN) == PUBLISH_SCREEN
    private val hasChatPermission = (flag and CHAT) == CHAT

    fun hasChatPermission(user: User): Boolean {
        if (CapabilitiesUtilNew.hasSpreedFeatureCapability(user, "chat-permission")) {
            return hasChatPermission
        }
        // if capability is not available then the spreed version doesn't support to restrict this
        return true
    }

    companion object {
        val TAG = AttendeePermissionsUtil::class.simpleName
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
