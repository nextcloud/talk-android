/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The registry must hold exactly the signaling sessions of the room the connection is in. The signaling server
 * delivers messages addressed to a session no matter which room that session is in, so sessions of other rooms
 * must neither receive our typing messages nor be trusted as the sender of a typing message.
 */
class RoomSessionRegistryTest {

    private fun user(sessionId: String?, userId: String, name: String): Map<String, Any> {
        val event = mutableMapOf<String, Any>("userid" to userId, "user" to mapOf("displayname" to name))
        sessionId?.let { event["sessionid"] = it }
        return event
    }

    private fun guest(sessionId: String) = mapOf<String, Any>("sessionid" to sessionId)

    private fun RoomSessionRegistry.enterRoom(room: String, vararg joins: Map<String, Any>) {
        onRoomResponse(room)
        onJoin(joins.toList())
    }

    @Test
    fun sessionsOfJoinedRoomAreKnownWithDisplayName() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice", "alice", "Alice"), user("s-bob", "bob", "Bob"))

        assertTrue(registry.contains("s-alice"))
        assertEquals("Alice", registry.displayNameFor("s-alice"))
        assertEquals(setOf("s-alice", "s-bob"), registry.sessionIdsExcept(null).toSet())
    }

    @Test
    fun switchingConversationDropsSessionsOfPreviousRoom() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice-a", "alice", "Alice"))

        registry.enterRoom("roomB", user("s-bob-b", "bob", "Bob"))

        assertEquals(
            "no typing message to a session of the previous room",
            listOf("s-bob-b"),
            registry.sessionIdsExcept(null)
        )
        assertFalse("typing message of a session of the previous room is ignored", registry.contains("s-alice-a"))
    }

    @Test
    fun leavingRoomDropsAllSessions() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice", "alice", "Alice"))

        registry.reset()
        registry.onRoomResponse("")

        assertTrue(registry.sessionIdsExcept(null).isEmpty())
        assertFalse(registry.contains("s-alice"))
    }

    @Test
    fun roomResponseForSameRoomKeepsSessions() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice", "alice", "Alice"))

        registry.onRoomResponse("roomA")

        assertTrue(registry.contains("s-alice"))
    }

    @Test
    fun resumedConnectionKeepsSessionsAndAppliesQueuedJoinAndLeave() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice", "alice", "Alice"), user("s-bob", "bob", "Bob"))

        // resume: no reset; the events collected while the socket was down are delivered afterwards
        registry.onLeave(listOf("s-bob"))
        registry.onJoin(listOf(user("s-carol", "carol", "Carol")))

        assertEquals(setOf("s-alice", "s-carol"), registry.sessionIdsExcept(null).toSet())
    }

    @Test
    fun newSignalingSessionWithoutResumeStartsEmptyUntilJoinListArrives() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice", "alice", "Alice"))

        registry.reset()

        assertTrue("sessions of the expired connection are gone", registry.sessionIdsExcept(null).isEmpty())
        assertFalse(registry.contains("s-alice"))

        registry.enterRoom("roomA", user("s-alice-new", "alice", "Alice"))
        assertEquals(listOf("s-alice-new"), registry.sessionIdsExcept(null))
    }

    @Test
    fun realGuestStaysInRegistryWithoutName() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", guest("s-guest"))

        assertTrue(registry.contains("s-guest"))
        assertEquals("", registry.displayNameFor("s-guest"))
    }

    @Test
    fun sessionWithoutJoinIsUnknown() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice", "alice", "Alice"))

        assertFalse(registry.contains("s-stranger"))
        assertFalse(registry.contains(null))
        registry.onJoin(listOf(user(null, "alice", "Alice")))
        assertEquals(listOf("s-alice"), registry.sessionIdsExcept(null))
        assertEquals("", registry.displayNameFor("s-stranger"))
    }

    @Test
    fun ownSessionIsNotATypingTarget() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-me", "me", "Me"), user("s-alice", "alice", "Alice"))

        assertEquals(listOf("s-alice"), registry.sessionIdsExcept("s-me"))
    }

    @Test
    fun accountsDoNotShareSessions() {
        val firstAccount = RoomSessionRegistry()
        val secondAccount = RoomSessionRegistry()
        firstAccount.enterRoom("roomA", user("s-alice", "alice", "Alice"))
        secondAccount.enterRoom("roomB", user("s-bob", "bob", "Bob"))

        firstAccount.enterRoom("roomC", user("s-carol", "carol", "Carol"))

        assertEquals(listOf("s-carol"), firstAccount.sessionIdsExcept(null))
        assertEquals(listOf("s-bob"), secondAccount.sessionIdsExcept(null))
        assertFalse(secondAccount.contains("s-carol"))
    }

    @Test
    fun readingWhileWritingFromAnotherThreadIsSafe() {
        val registry = RoomSessionRegistry()
        val executor = Executors.newFixedThreadPool(2)
        val failure = AtomicReference<Throwable?>()
        val done = CountDownLatch(2)
        executor.execute {
            runCatching {
                repeat(5_000) { i ->
                    registry.onJoin(listOf(user("s-$i", "u$i", "User $i")))
                    if (i % 50 == 0) registry.onRoomResponse("room-$i")
                }
            }.onFailure { failure.set(it) }
            done.countDown()
        }
        executor.execute {
            runCatching { repeat(5_000) { registry.sessionIdsExcept("s-0") } }.onFailure { failure.set(it) }
            done.countDown()
        }

        assertTrue(done.await(30, TimeUnit.SECONDS))
        executor.shutdownNow()
        assertEquals(null, failure.get())
    }
}
