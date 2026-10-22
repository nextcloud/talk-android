/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling

import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class SignalingMessageReceiverSessionsLeftTest {

    private lateinit var receiver: SignalingMessageReceiver
    private lateinit var listener: SignalingMessageReceiver.ConversationMessageListener

    @Before
    fun setUp() {
        receiver = object : SignalingMessageReceiver() {}
        listener = mock(SignalingMessageReceiver.ConversationMessageListener::class.java)
        receiver.addListener(listener)
    }

    @Test
    fun leaveEventOfTheRoomNotifiesTheSessionsWhichLeft() {
        receiver.processEvent(mapOf("target" to "room", "type" to "leave", "leave" to listOf("s-alice", "s-bob")))

        verify(listener).onSessionsLeft(listOf("s-alice", "s-bob"))
    }

    @Test
    fun leaveEventWithoutSessionsNotifiesNobody() {
        receiver.processEvent(mapOf("target" to "room", "type" to "leave", "leave" to emptyList<String>()))

        verify(listener, never()).onSessionsLeft(emptyList())
    }

    @Test
    fun joinEventIsNotALeave() {
        receiver.processEvent(mapOf("target" to "room", "type" to "join", "join" to listOf(mapOf("sessionid" to "s"))))

        verify(listener, never()).onSessionsLeft(listOf("s"))
    }
}
