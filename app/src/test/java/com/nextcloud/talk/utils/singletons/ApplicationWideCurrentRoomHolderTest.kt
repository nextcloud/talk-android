/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.singletons

import com.nextcloud.talk.data.user.model.User
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApplicationWideCurrentRoomHolderTest {

    private val holder = ApplicationWideCurrentRoomHolder()

    @Before
    fun setUp() {
        holder.currentRoomToken = "room"
        holder.userInRoom = User(id = 1L)
        holder.isInCall = true
    }

    @Test
    fun `call push of the call the user is in is the own call`() {
        assertTrue(holder.isInCallOfRoom("room", 1L))
    }

    @Test
    fun `call push of another room is not the own call`() {
        assertFalse(holder.isInCallOfRoom("other", 1L))
    }

    @Test
    fun `call push of another account is not the own call`() {
        assertFalse(holder.isInCallOfRoom("room", 2L))
    }

    @Test
    fun `call push while the user only has the room open is not the own call`() {
        holder.isInCall = false

        assertFalse(holder.isInCallOfRoom("room", 1L))
    }
}
