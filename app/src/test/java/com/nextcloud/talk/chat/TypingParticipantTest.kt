/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class TypingParticipantTest {

    private fun typing(userId: String, session: String?) = TypingParticipant(userId, userId, session) { }

    @Test
    fun typingOfSessionWhichLeftIsRemoved() {
        val participants = mutableMapOf("alice" to typing("alice", "s-alice"), "bob" to typing("bob", "s-bob"))

        val removed = TypingParticipant.removeSessions(participants, listOf("s-alice"))

        assertTrue(removed)
        assertEquals(setOf("bob"), participants.keys)
    }

    @Test
    fun guestTypingIsRemovedWithItsSession() {
        val participants = mutableMapOf("s-guest" to typing("s-guest", "s-guest"))

        assertTrue(TypingParticipant.removeSessions(participants, listOf("s-guest")))

        assertTrue(participants.isEmpty())
    }

    @Test
    fun typingOfUserStaysWhileAnotherSessionOfTheUserLeft() {
        val participants = mutableMapOf("alice" to typing("alice", "s-alice-phone"))

        val removed = TypingParticipant.removeSessions(participants, listOf("s-alice-laptop"))

        assertFalse(removed)
        assertEquals(setOf("alice"), participants.keys)
    }

    @Test
    fun typingOfUserStaysWhileAnotherSessionOfTheUserStillTypes() {
        val alice = typing("alice", "s-alice-phone")
        alice.addSession("s-alice-laptop")
        val participants = mutableMapOf("alice" to alice)

        val removed = TypingParticipant.removeSessions(participants, listOf("s-alice-phone"))

        assertFalse("the laptop session of alice is still in the room", removed)
        assertEquals(setOf("alice"), participants.keys)
    }

    @Test
    fun typingOfUserIsRemovedWhenAllItsSessionsLeft() {
        val alice = typing("alice", "s-alice-phone")
        alice.addSession("s-alice-laptop")
        val participants = mutableMapOf("alice" to alice)

        assertFalse(TypingParticipant.removeSessions(participants, listOf("s-alice-phone")))
        assertTrue(TypingParticipant.removeSessions(participants, listOf("s-alice-laptop")))

        assertTrue(participants.isEmpty())
    }

    @Test
    fun typingOfUserIsRemovedWhenAllItsSessionsLeftAtOnce() {
        val alice = typing("alice", "s-alice-phone")
        alice.addSession("s-alice-laptop")
        val participants = mutableMapOf("alice" to alice)

        assertTrue(TypingParticipant.removeSessions(participants, listOf("s-alice-phone", "s-alice-laptop")))

        assertTrue(participants.isEmpty())
    }
}
