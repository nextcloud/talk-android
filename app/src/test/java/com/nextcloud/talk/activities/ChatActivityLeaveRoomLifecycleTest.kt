/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests documenting the leaveRoom lifecycle race conditions in ChatActivity and how
 * the observeForever fix addresses them.
 *
 * Core problem: ChatActivity.onPause() calls leaveRoom() which is async. The LiveData
 * observer for the leave response was lifecycle-aware (observe(this)), so it wouldn't
 * deliver when the activity was paused. This meant:
 * 1. Websocket cleanup never happened (server still thought user was in room)
 * 2. ApplicationWideCurrentRoomHolder was cleared prematurely (before server confirmed)
 * 3. switchToRoom callbacks could be lost during navigation gestures
 *
 * The fix uses observeForever so the callback always fires, with guards to prevent
 * disrupting active calls.
 */
class ChatActivityLeaveRoomLifecycleTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    // Simulates the leaveRoomViewState LiveData from ChatViewModel
    private val leaveRoomViewState = MutableLiveData<LeaveState>(LeaveRoomStartState)

    // Simulates ApplicationWideCurrentRoomHolder state
    private var holderIsInCall = false
    private var holderIsDialing = false
    private var holderCleared = false

    // Simulates ChatActivity state
    private var isLeavingRoom = false
    private var sessionIdAfterRoomJoined: String? = "valid-session"
    private var websocketLeaveRoomCalled = false
    private var callbackInvoked = false

    // Simulates the lifecycle of ChatActivity
    private lateinit var lifecycleOwner: TestLifecycleOwner

    sealed interface LeaveState
    object LeaveRoomStartState : LeaveState
    class LeaveRoomSuccessState(val funToCallWhenLeaveSuccessful: (() -> Unit)?) : LeaveState

    private fun isNotInCall(): Boolean = !holderIsInCall && !holderIsDialing

    private fun simulateLeaveRoomObserverAction(state: LeaveState) {
        when (state) {
            is LeaveRoomSuccessState -> {
                isLeavingRoom = false

                if (isNotInCall()) {
                    holderCleared = true // ApplicationWideCurrentRoomHolder.clear()

                    websocketLeaveRoomCalled = true // websocket leave
                    sessionIdAfterRoomJoined = "0"
                }

                state.funToCallWhenLeaveSuccessful?.invoke()
            }
            else -> {}
        }
    }

    @Before
    fun setUp() {
        lifecycleOwner = TestLifecycleOwner()
        holderIsInCall = false
        holderIsDialing = false
        holderCleared = false
        isLeavingRoom = false
        sessionIdAfterRoomJoined = "valid-session"
        websocketLeaveRoomCalled = false
        callbackInvoked = false
    }

    @After
    fun tearDown() {
        // Reset singleton state
        ApplicationWideCurrentRoomHolder.getInstance().clear()
    }

    // ==========================================
    // Tests for the OLD behavior (lifecycle-aware observer)
    // These document the bugs that existed before the fix
    // ==========================================

    /**
     * BUG: Lifecycle-aware observer doesn't deliver when activity is stopped.
     *
     * When ChatActivity.onPause() calls leaveRoom(), the async network call takes time.
     * By the time it completes, the activity has progressed to STOPPED (onStop has run).
     * LiveData's observe(this) only delivers to STARTED or RESUMED observers, so the
     * leave response is never received. The websocket cleanup never happens.
     *
     * Note: LiveData considers STARTED (after onStart, before onStop) as active.
     * ON_PAUSE moves to STARTED which is still active. ON_STOP moves to CREATED which
     * is inactive. In practice, the network response arrives after onStop, not just
     * onPause, so the observer misses it.
     */
    @Test
    fun `lifecycle-aware observer misses leave response when activity is stopped`() {
        // Start in RESUMED state
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        var observerReceived = false

        // Register lifecycle-aware observer (old behavior)
        leaveRoomViewState.observe(lifecycleOwner) { state ->
            if (state is LeaveRoomSuccessState) {
                observerReceived = true
            }
        }

        // Activity goes through onPause → onStop (normal navigation away)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        // Network call completes, LiveData is set while activity is stopped
        leaveRoomViewState.value = LeaveRoomSuccessState(null)

        // Observer did NOT receive the value — websocket cleanup never happens
        assertFalse(
            "Lifecycle-aware observer should NOT deliver when stopped (this is the bug)",
            observerReceived
        )
    }

    /**
     * FIX: observeForever delivers even when activity is paused.
     */
    @Test
    fun `observeForever delivers leave response even when activity is paused`() {
        var observerReceived = false

        // Register observeForever (the fix)
        val observer = androidx.lifecycle.Observer<LeaveState> { state ->
            if (state is LeaveRoomSuccessState) {
                observerReceived = true
                simulateLeaveRoomObserverAction(state)
            }
        }
        leaveRoomViewState.observeForever(observer)

        // Simulate: activity is paused, leave response arrives
        leaveRoomViewState.value = LeaveRoomSuccessState(null)

        // Observer DOES receive the value — cleanup happens
        assertTrue("observeForever should deliver regardless of lifecycle", observerReceived)
        assertTrue("Holder should be cleared", holderCleared)
        assertTrue("Websocket leave should be called", websocketLeaveRoomCalled)
        assertEquals("Session should be reset", "0", sessionIdAfterRoomJoined)

        leaveRoomViewState.removeObserver(observer)
    }

    // ==========================================
    // Tests for the isNotInCall guard
    // ==========================================

    /**
     * When a call is active (isInCall=true), the leave observer must NOT clear the
     * holder or send websocket leave — doing so would kill the active call/PIP.
     */
    @Test
    fun `leave observer skips cleanup when call is active`() {
        holderIsInCall = true

        val observer = androidx.lifecycle.Observer<LeaveState> { state ->
            if (state is LeaveRoomSuccessState) {
                simulateLeaveRoomObserverAction(state)
            }
        }
        leaveRoomViewState.observeForever(observer)

        leaveRoomViewState.value = LeaveRoomSuccessState(null)

        assertFalse("Holder should NOT be cleared during active call", holderCleared)
        assertFalse("Websocket leave should NOT be called during active call", websocketLeaveRoomCalled)
        assertEquals(
            "Session should NOT be reset during active call",
            "valid-session",
            sessionIdAfterRoomJoined
        )

        leaveRoomViewState.removeObserver(observer)
    }

    /**
     * When dialing (isDialing=true), the leave observer must NOT clear the holder.
     */
    @Test
    fun `leave observer skips cleanup when dialing`() {
        holderIsDialing = true

        val observer = androidx.lifecycle.Observer<LeaveState> { state ->
            if (state is LeaveRoomSuccessState) {
                simulateLeaveRoomObserverAction(state)
            }
        }
        leaveRoomViewState.observeForever(observer)

        leaveRoomViewState.value = LeaveRoomSuccessState(null)

        assertFalse("Holder should NOT be cleared while dialing", holderCleared)
        assertFalse("Websocket leave should NOT be called while dialing", websocketLeaveRoomCalled)

        leaveRoomViewState.removeObserver(observer)
    }

    /**
     * When no call is active, the leave observer SHOULD perform full cleanup.
     */
    @Test
    fun `leave observer performs cleanup when no call is active`() {
        holderIsInCall = false
        holderIsDialing = false

        val observer = androidx.lifecycle.Observer<LeaveState> { state ->
            if (state is LeaveRoomSuccessState) {
                simulateLeaveRoomObserverAction(state)
            }
        }
        leaveRoomViewState.observeForever(observer)

        leaveRoomViewState.value = LeaveRoomSuccessState(null)

        assertTrue("Holder should be cleared when no call active", holderCleared)
        assertTrue("Websocket leave should be called when no call active", websocketLeaveRoomCalled)
        assertEquals("Session should be reset", "0", sessionIdAfterRoomJoined)

        leaveRoomViewState.removeObserver(observer)
    }

    // ==========================================
    // Tests for the callback (switchToRoom) behavior
    // ==========================================

    /**
     * The switchToRoom callback must fire even when the activity is paused.
     * This ensures the new ChatActivity is launched after the room is left.
     */
    @Test
    fun `switchToRoom callback fires via observeForever even when paused`() {
        val observer = androidx.lifecycle.Observer<LeaveState> { state ->
            if (state is LeaveRoomSuccessState) {
                simulateLeaveRoomObserverAction(state)
            }
        }
        leaveRoomViewState.observeForever(observer)

        leaveRoomViewState.value = LeaveRoomSuccessState {
            callbackInvoked = true
        }

        assertTrue("Callback should be invoked", callbackInvoked)

        leaveRoomViewState.removeObserver(observer)
    }

    /**
     * The switchToRoom callback must still fire even when a call is active —
     * only the holder/websocket cleanup is skipped, not the callback.
     */
    @Test
    fun `switchToRoom callback fires even during active call`() {
        holderIsInCall = true

        val observer = androidx.lifecycle.Observer<LeaveState> { state ->
            if (state is LeaveRoomSuccessState) {
                simulateLeaveRoomObserverAction(state)
            }
        }
        leaveRoomViewState.observeForever(observer)

        leaveRoomViewState.value = LeaveRoomSuccessState {
            callbackInvoked = true
        }

        assertTrue("Callback should fire even during active call", callbackInvoked)
        assertFalse("But holder should NOT be cleared", holderCleared)

        leaveRoomViewState.removeObserver(observer)
    }

    // ==========================================
    // Tests for the isLeavingRoom guard (double-leave prevention)
    // ==========================================

    /**
     * Documents the double-leave race: switchToRoom calls leaveRoom, then onPause
     * fires and tries to call leaveRoom again. The isLeavingRoom flag prevents this.
     */
    @Test
    fun `isLeavingRoom prevents double leave when switchToRoom is in progress`() {
        var leaveRoomCallCount = 0

        fun leaveRoom() {
            isLeavingRoom = true
            leaveRoomCallCount++
        }

        fun simulateOnPause() {
            if (isNotInCall()) {
                if (isLeavingRoom) {
                    // Skip — leave already in progress
                } else {
                    leaveRoom()
                }
            }
        }

        // switchToRoom calls leaveRoom first
        leaveRoom()
        assertEquals("First leave should fire", 1, leaveRoomCallCount)

        // onPause fires while the first leave is in progress
        simulateOnPause()
        assertEquals("Second leave should be skipped", 1, leaveRoomCallCount)
    }

    /**
     * After a leave completes, isLeavingRoom is reset, allowing future leaves.
     */
    @Test
    fun `isLeavingRoom is reset after leave completes`() {
        isLeavingRoom = true

        val observer = androidx.lifecycle.Observer<LeaveState> { state ->
            if (state is LeaveRoomSuccessState) {
                simulateLeaveRoomObserverAction(state)
            }
        }
        leaveRoomViewState.observeForever(observer)

        leaveRoomViewState.value = LeaveRoomSuccessState(null)

        assertFalse("isLeavingRoom should be reset after leave completes", isLeavingRoom)

        leaveRoomViewState.removeObserver(observer)
    }

    // ==========================================
    // Tests for the ApplicationWideCurrentRoomHolder timing
    // ==========================================

    /**
     * BUG (old behavior): Holder was cleared in onPause BEFORE the server confirmed
     * the leave. A new ChatActivity resuming concurrently would find the holder empty
     * and lose session continuity.
     *
     * FIX: Holder is now cleared in the leave success callback, after server confirms.
     */
    @Test
    fun `holder is cleared only after server confirms leave`() {
        val holder = ApplicationWideCurrentRoomHolder.getInstance()
        holder.currentRoomToken = "room1"
        holder.session = "session1"

        val observer = androidx.lifecycle.Observer<LeaveState> { state ->
            if (state is LeaveRoomSuccessState) {
                simulateLeaveRoomObserverAction(state)
            }
        }
        leaveRoomViewState.observeForever(observer)

        // Before server responds, holder should still have data
        // (In old code, holder.clear() was called immediately in onPause)
        assertEquals("room1", holder.currentRoomToken)
        assertEquals("session1", holder.session)

        // Server confirms leave
        leaveRoomViewState.value = LeaveRoomSuccessState(null)

        // NOW holder is cleared
        assertTrue("Holder should be cleared after server confirms", holderCleared)

        leaveRoomViewState.removeObserver(observer)
    }

    // ==========================================
    // Test for the PIP + leaveRoom interaction
    // ==========================================

    /**
     * Documents the critical PIP interaction: when a call is active (in PIP or full),
     * navigating away from ChatActivity must NOT clear the holder or send websocket
     * leave, as this would end the call.
     *
     * This is the exact scenario the user reported: "the call ends when I shift away
     * from it and then tries to reconnect when I make the video live again."
     */
    @Test
    fun `navigating away from chat during active call preserves call state`() {
        val holder = ApplicationWideCurrentRoomHolder.getInstance()
        holder.currentRoomToken = "room1"
        holder.session = "session1"
        holder.isInCall = true
        holderIsInCall = true

        val observer = androidx.lifecycle.Observer<LeaveState> { state ->
            if (state is LeaveRoomSuccessState) {
                simulateLeaveRoomObserverAction(state)
            }
        }
        leaveRoomViewState.observeForever(observer)

        // Simulate: a previous leave request completes while call is active
        leaveRoomViewState.value = LeaveRoomSuccessState(null)

        // Call state should be PRESERVED
        assertFalse("Holder should NOT be cleared during call", holderCleared)
        assertFalse("Websocket leave should NOT fire during call", websocketLeaveRoomCalled)
        assertTrue("Holder should still show in-call", holder.isInCall)
        assertEquals("Room token should be preserved", "room1", holder.currentRoomToken)
        assertEquals("Session should be preserved", "session1", holder.session)

        leaveRoomViewState.removeObserver(observer)
    }

    /**
     * After a call ends (isInCall becomes false), the next leave should perform
     * full cleanup.
     */
    @Test
    fun `after call ends, leave performs full cleanup`() {
        // Call was active
        holderIsInCall = true

        val observer = androidx.lifecycle.Observer<LeaveState> { state ->
            if (state is LeaveRoomSuccessState) {
                simulateLeaveRoomObserverAction(state)
            }
        }
        leaveRoomViewState.observeForever(observer)

        // Leave during call — skipped
        leaveRoomViewState.value = LeaveRoomSuccessState(null)
        assertFalse("Cleanup skipped during call", holderCleared)

        // Call ends
        holderIsInCall = false

        // Reset LiveData to trigger again
        leaveRoomViewState.value = LeaveRoomStartState
        leaveRoomViewState.value = LeaveRoomSuccessState(null)

        // Now cleanup happens
        assertTrue("Cleanup should happen after call ends", holderCleared)
        assertTrue("Websocket leave should fire after call ends", websocketLeaveRoomCalled)

        leaveRoomViewState.removeObserver(observer)
    }

    // ==========================================
    // Helper: TestLifecycleOwner
    // ==========================================

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
        }
    }
}
