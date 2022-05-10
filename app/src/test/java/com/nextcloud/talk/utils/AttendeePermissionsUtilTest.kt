package com.nextcloud.talk.utils

import junit.framework.TestCase
import org.junit.Test

class AttendeePermissionsUtilTest : TestCase() {

    @Test
    fun test_areFlagsSet() {
        val attendeePermissionsUtil = AttendeePermissionsUtil(
            AttendeePermissionsUtil.PUBLISH_SCREEN
            or AttendeePermissionsUtil.JOIN_CALL
            or AttendeePermissionsUtil.DEFAULT)

        assert(attendeePermissionsUtil.canPublishScreen)
        assert(attendeePermissionsUtil.canJoinCall)
        assert(attendeePermissionsUtil.isDefault) // if AttendeePermissionsUtil.DEFAULT is not set with setFlags and
        // checked with assertFalse, the logic fails somehow?!

        assertFalse(attendeePermissionsUtil.isCustom)
        assertFalse(attendeePermissionsUtil.canStartCall)
        assertFalse(attendeePermissionsUtil.canIgnoreLobby)
        assertFalse(attendeePermissionsUtil.canPublishAudio)
        assertFalse(attendeePermissionsUtil.canPublishVideo)

        // canPostChatShareItemsDoReaction() is not possible to test because userEntity is necessary
    }
}