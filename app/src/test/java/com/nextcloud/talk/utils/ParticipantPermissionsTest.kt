/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.Conversation
import junit.framework.TestCase
import org.junit.Test

class ParticipantPermissionsTest : TestCase() {

    @Test
    fun test_areFlagsSet() {
        val spreedCapability = SpreedCapability()
        val conversation = Conversation()
        conversation.permissions = ParticipantPermissions.PUBLISH_SCREEN or
            ParticipantPermissions.JOIN_CALL or
            ParticipantPermissions.DEFAULT

        val attendeePermissions =
            ParticipantPermissions(
                spreedCapability,
                conversation
            )

        assert(attendeePermissions.canPublishScreen)
        assert(attendeePermissions.canJoinCall)
        assert(attendeePermissions.isDefault)

        assertFalse(attendeePermissions.isCustom)
        assertFalse(attendeePermissions.canStartCall())
        assertFalse(attendeePermissions.canIgnoreLobby())
        assertTrue(attendeePermissions.canPublishAudio())
        assertTrue(attendeePermissions.canPublishVideo())
    }
}
