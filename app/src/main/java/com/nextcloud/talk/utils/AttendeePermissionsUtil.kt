package com.nextcloud.talk.utils

import com.nextcloud.talk.models.database.CapabilitiesUtil
import com.nextcloud.talk.models.database.UserEntity

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

    fun hasChatPermission(user: UserEntity): Boolean {
        if (CapabilitiesUtil.hasSpreedFeatureCapability(user, "chat-permission")) {
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