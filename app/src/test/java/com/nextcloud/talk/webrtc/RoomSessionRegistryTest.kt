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
@Suppress("TooManyFunctions")
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
    fun roomResponseOfOtherRoomDropsSessionsOfPreviousRoom() {
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
    fun sessionIsOnlyInTheRoomTheConnectionIsIn() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice-a", "alice", "Alice"))

        assertTrue(registry.contains("s-alice-a", "roomA"))
        assertFalse(
            "typing message of room A is ignored by the conversation of room B before room B is answered",
            registry.contains("s-alice-a", "roomB")
        )
    }

    @Test
    fun resetDropsAllSessions() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice", "alice", "Alice"))

        registry.reset()

        assertTrue(registry.sessionIdsExcept(null).isEmpty())
        assertFalse(registry.contains("s-alice"))
    }

    @Test
    fun lateJoinOfTheLeftRoomIsNotAcceptedBeforeTheAnswerToLeave() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice", "alice", "Alice"))
        registry.reset()

        registry.onJoin(listOf(user("s-late", "bob", "Bob")))

        assertTrue("no typing message to the late session", registry.sessionIdsExcept(null).isEmpty())
        assertFalse("typing message of the late session is ignored", registry.contains("s-late"))

        registry.onRoomResponse("")

        registry.onJoin(listOf(user("s-after-leave", "bob", "Bob")))

        assertTrue("no join is accepted after the answer to the leave", registry.sessionIdsExcept(null).isEmpty())
    }

    /**
     * Pauses onJoin() of [lateSession] right after its check of the room (before it stores the session) and runs
     * [change] on another thread meanwhile. Without a common lock [change] finishes while onJoin() is paused and
     * onJoin() stores the session afterwards; with it, [change] waits for onJoin().
     */
    private fun RoomSessionRegistry.joinPausedWhile(lateSession: String, change: RoomSessionRegistry.() -> Unit) {
        val insideJoin = CountDownLatch(1)
        val changeMayRun = CountDownLatch(1)
        // The map hands the thread over right after onJoin passed its check of the room, before it stores the session.
        val pausingEvent = object : HashMap<String, Any>(guest(lateSession)) {
            override fun get(key: String): Any? {
                insideJoin.countDown()
                changeMayRun.await(5, TimeUnit.SECONDS)
                return super.get(key)
            }
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val joined = executor.submit { onJoin(listOf(pausingEvent)) }
            assertTrue(insideJoin.await(5, TimeUnit.SECONDS))
            val changed = executor.submit { change() }
            runCatching { changed.get(500, TimeUnit.MILLISECONDS) }
            changeMayRun.countDown()
            joined.get(5, TimeUnit.SECONDS)
            changed.get(5, TimeUnit.SECONDS)
        } finally {
            changeMayRun.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun resetDuringJoinDoesNotLeaveSessionOfTheLeftRoom() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice", "alice", "Alice"))

        registry.joinPausedWhile("s-late") { reset() }

        assertFalse("session stored by a join which raced with reset() stays", registry.contains("s-late"))
        assertTrue(registry.sessionIdsExcept(null).isEmpty())
    }

    @Test
    fun roomResponseDuringJoinDoesNotLeaveSessionOfThePreviousRoom() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice", "alice", "Alice"))

        registry.joinPausedWhile("s-late") { onRoomResponse("roomB") }

        assertFalse("session of room A stored after the answer for room B", registry.contains("s-late"))
        assertTrue(registry.sessionIdsExcept(null).isEmpty())
        registry.onJoin(listOf(user("s-bob-b", "bob", "Bob")))
        assertEquals(listOf("s-bob-b"), registry.sessionIdsExcept(null))
    }

    @Test
    fun roomResponseForSameRoomKeepsSessions() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice", "alice", "Alice"))

        registry.onRoomResponse("roomA")

        assertTrue(registry.contains("s-alice"))
    }

    @Test
    fun joinAndLeaveEventsUpdateTheSessions() {
        val registry = RoomSessionRegistry()
        registry.enterRoom("roomA", user("s-alice", "alice", "Alice"), user("s-bob", "bob", "Bob"))

        registry.onLeave(listOf("s-bob"))
        registry.onJoin(listOf(user("s-carol", "carol", "Carol")))

        assertEquals(setOf("s-alice", "s-carol"), registry.sessionIdsExcept(null).toSet())
    }

    @Test
    fun resetDropsSessionsUntilNewJoinListArrives() {
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
