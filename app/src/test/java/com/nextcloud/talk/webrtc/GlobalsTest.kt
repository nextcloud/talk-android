/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Samanwith KSN <samanwith21@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
