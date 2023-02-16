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
import junit.framework.TestCase
import org.junit.Test

class ParticipantPermissionsTest : TestCase() {

    @Test
    fun test_areFlagsSet() {
        val user = User()
        val conversation = Conversation()
        conversation.permissions = ParticipantPermissions.PUBLISH_SCREEN or
            ParticipantPermissions.JOIN_CALL or
            ParticipantPermissions.DEFAULT

        val attendeePermissions =
            ParticipantPermissions(
                user,
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
