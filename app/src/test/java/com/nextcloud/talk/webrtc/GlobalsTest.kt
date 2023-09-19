/*
 * Nextcloud Talk application
 *
 * @author Samanwith KSN
 * Copyright (C) 2023 Samanwith KSN <samanwith21@gmail.com>
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
package com.nextcloud.talk.webrtc

import org.junit.Assert
import org.junit.Test

class GlobalsTest {
    @Test
    fun testRoomToken() {
        Assert.assertEquals("roomToken", Globals.ROOM_TOKEN)
    }

    @Test
    fun testTargetParticipants() {
        Assert.assertEquals("participants", Globals.TARGET_PARTICIPANTS)
    }

    @Test
    fun testTargetRoom() {
        Assert.assertEquals("room", Globals.TARGET_ROOM)
    }
}
