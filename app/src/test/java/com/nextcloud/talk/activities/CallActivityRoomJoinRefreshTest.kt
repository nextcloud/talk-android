/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Room session refresh decisions ([CallActivity.shouldRefreshRoomSession]).
 *
 * Joining a call with a stale room session (reaped by the server) is rejected with "no_such_room". The cached
 * session must be dropped and a fresh one fetched via the joinRoom API — but only while the call is still being
 * set up (a stray error must never disturb an established call) and only a bounded number of times (otherwise the
 * UI would retry forever instead of failing visibly).
 */
class CallActivityRoomJoinRefreshTest {

    @Test
    fun `stale session is refreshed while the call is being set up`() {
        assertTrue(CallActivity.shouldRefreshRoomSession(CallStatus.CONNECTING, 0))
        assertTrue(CallActivity.shouldRefreshRoomSession(CallStatus.JOINED, 0))
        assertTrue(CallActivity.shouldRefreshRoomSession(CallStatus.RECONNECTING, 0))
    }

    @Test
    fun `session is never refreshed once in conversation`() {
        assertFalse(CallActivity.shouldRefreshRoomSession(CallStatus.IN_CONVERSATION, 0))
    }

    @Test
    fun `gives up after the maximum number of refreshes`() {
        assertTrue(CallActivity.shouldRefreshRoomSession(CallStatus.CONNECTING, 1))
        assertFalse(CallActivity.shouldRefreshRoomSession(CallStatus.CONNECTING, 2))
        assertFalse(CallActivity.shouldRefreshRoomSession(CallStatus.CONNECTING, 3))
    }
}
