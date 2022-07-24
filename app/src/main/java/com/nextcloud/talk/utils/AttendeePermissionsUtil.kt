/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
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
    var isDefault: Boolean = false
    var isCustom: Boolean = false
    var canStartCall: Boolean = false
    var canJoinCall: Boolean = false
    var canIgnoreLobby: Boolean = false
    var canPublishAudio: Boolean = false
    var canPublishVideo: Boolean = false
    var canPublishScreen: Boolean = false
    private var hasChatPermission: Boolean = false

    init {
        isDefault = (flag and DEFAULT) == DEFAULT
        isCustom = (flag and CUSTOM) == CUSTOM
        canStartCall = (flag and START_CALL) == START_CALL
        canJoinCall = (flag and JOIN_CALL) == JOIN_CALL
        canIgnoreLobby = (flag and CAN_IGNORE_LOBBY) == CAN_IGNORE_LOBBY
        canPublishAudio = (flag and PUBLISH_AUDIO) == PUBLISH_AUDIO
        canPublishVideo = (flag and PUBLISH_VIDEO) == PUBLISH_VIDEO
        canPublishScreen = (flag and PUBLISH_SCREEN) == PUBLISH_SCREEN
        hasChatPermission = (flag and CHAT) == CHAT
    }

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
