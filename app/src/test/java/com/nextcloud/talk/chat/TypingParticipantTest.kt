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
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class TypingParticipantTest {

    private fun typing(userId: String, session: String) = TypingParticipant(userId, userId, session) { }

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

    @Test
    fun stopOfOneSessionKeepsTypingOfUserUntilLastSessionStops() {
        val alice = typing("alice", "s-alice-phone")
        alice.addSession("s-alice-laptop")
        val participants = mutableMapOf("alice" to alice)

        assertFalse(TypingParticipant.stopTyping(participants, "alice", "s-alice-phone"))
        assertEquals(setOf("alice"), participants.keys)

        assertTrue(TypingParticipant.stopTyping(participants, "alice", "s-alice-laptop"))
        assertTrue(participants.isEmpty())
    }

    private fun typingWithTimeout(participants: MutableMap<String, TypingParticipant>, session: String) =
        TypingParticipant("alice", "alice", session) { participants.remove("alice", it) }
            .also { participants["alice"] = it }

    private fun idle(seconds: Long) = ShadowLooper.idleMainLooper(seconds, TimeUnit.SECONDS)

    @Test
    fun stopOfOneSessionLeavesTheOtherSessionToExpireOnItsOwn() {
        val participants = mutableMapOf<String, TypingParticipant>()
        val alice = typingWithTimeout(participants, "s-alice-laptop")
        idle(10)
        alice.addSession("s-alice-phone")
        idle(1)

        assertFalse(TypingParticipant.stopTyping(participants, "alice", "s-alice-phone"))
        idle(3)
        assertEquals("laptop has 1 s left at 14 s", setOf("alice"), participants.keys)

        idle(1)
        assertTrue("laptop expired at 15 s", participants.isEmpty())
    }

    @Test
    fun stopOfLastSessionRemovesTheParticipantAfterAnotherSessionExpired() {
        val participants = mutableMapOf<String, TypingParticipant>()
        val alice = typingWithTimeout(participants, "s-alice-phone")
        idle(10)
        alice.addSession("s-alice-laptop")
        idle(6)
        assertEquals("phone expired at 15 s, laptop still types", setOf("alice"), participants.keys)

        assertTrue(TypingParticipant.stopTyping(participants, "alice", "s-alice-laptop"))

        assertTrue(participants.isEmpty())
    }

    @Test
    fun repeatedStartOfTheSameSessionRestartsItsTimer() {
        var timeUps = 0
        val participants = mutableMapOf<String, TypingParticipant>()
        val alice = TypingParticipant("alice", "alice", "s-alice-phone") {
            timeUps++
            participants.remove("alice", it)
        }
        participants["alice"] = alice
        idle(10)
        alice.addSession("s-alice-phone")
        idle(5)
        assertEquals("restarted at 10 s, expires at 25 s", setOf("alice"), participants.keys)

        idle(10)

        assertTrue(participants.isEmpty())
        assertEquals(1, timeUps)
    }
}
