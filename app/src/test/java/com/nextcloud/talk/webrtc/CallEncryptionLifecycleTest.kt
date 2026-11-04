/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import com.nextcloud.talk.call.e2ee.CallEncryption
import com.nextcloud.talk.call.e2ee.EncryptionMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class CallEncryptionLifecycleTest {

    private var enabled = true
    private val created = ArrayList<CallEncryption>()
    private val lifecycle = CallEncryptionLifecycle({ enabled }) { _, _ ->
        Mockito.mock(CallEncryption::class.java).also { created.add(it) }
    }
    private val send: (String, EncryptionMessage) -> Unit = { _, _ -> }

    @Test
    fun createdOnRoomJoinWithMcuWhenEnabled() {
        lifecycle.roomJoined("session-a", hasMCU = true, alreadyInRoom = false, send)

        assertEquals(1, created.size)
        assertSame(created[0], lifecycle.current)
    }

    @Test
    fun notCreatedWithoutPolicyMcuOrSession() {
        enabled = false
        lifecycle.roomJoined("session-a", hasMCU = true, alreadyInRoom = false, send)
        enabled = true
        lifecycle.roomJoined("session-a", hasMCU = false, alreadyInRoom = false, send)
        lifecycle.roomJoined(null, hasMCU = true, alreadyInRoom = false, send)

        assertEquals(0, created.size)
        assertNull(lifecycle.current)
    }

    @Test
    fun keptWhenTheSameRoomIsJoinedAgainAndReplacedOtherwise() {
        lifecycle.roomJoined("session-a", hasMCU = true, alreadyInRoom = false, send)
        lifecycle.roomJoined("session-a", hasMCU = true, alreadyInRoom = true, send)
        assertEquals(1, created.size)
        verify(created[0], never()).close()

        lifecycle.roomJoined("session-a", hasMCU = true, alreadyInRoom = false, send)
        assertEquals(2, created.size)
        verify(created[0]).close()
        assertSame(created[1], lifecycle.current)
    }

    @Test
    fun closedWhenRoomOrSessionIsGone() {
        lifecycle.roomJoined("session-a", hasMCU = true, alreadyInRoom = false, send)

        lifecycle.close()

        verify(created[0]).close()
        assertNull(lifecycle.current)
    }

    @Test
    fun eventsAndMessagesReachTheExchangeOnlyWhileItExists() {
        val message = EncryptionMessage(EncryptionMessage.START, id = "1")
        lifecycle.handleMessage("session-b", message)
        lifecycle.usersJoined(listOf("session-b"))

        lifecycle.roomJoined("session-a", hasMCU = true, alreadyInRoom = false, send)
        lifecycle.handleMessage("session-b", message)
        lifecycle.usersJoined(listOf("session-b"))
        lifecycle.usersLeft(listOf("session-b"))

        verify(created[0]).handleMessage("session-b", message)
        verify(created[0]).usersJoined(listOf("session-b"))
        verify(created[0]).usersLeft(listOf("session-b"))
    }
}
