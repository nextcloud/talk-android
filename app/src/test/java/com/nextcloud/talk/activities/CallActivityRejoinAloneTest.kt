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
 * A rejoin alone in the call must not send "leave call": the server ends a call without active sessions
 * (`CallController::leaveCall` -> `resetActiveSince`), so the callee's ringing stops and the next join rings again.
 */
class CallActivityRejoinAloneTest {

    @Test
    fun `rejoin after a failed publisher does not leave the call while nobody else was in it`() {
        assertFalse(CallActivity.shouldLeaveCallOnHangup(false, false, CallStatus.PUBLISHER_FAILED, false))
        assertFalse(CallActivity.shouldLeaveCallOnHangup(false, false, CallStatus.RECONNECTING, false))
    }

    @Test
    fun `rejoin leaves the call once somebody else was in it`() {
        assertTrue(CallActivity.shouldLeaveCallOnHangup(false, false, CallStatus.PUBLISHER_FAILED, true))
        assertTrue(CallActivity.shouldLeaveCallOnHangup(false, false, CallStatus.RECONNECTING, true))
    }

    @Test
    fun `hanging up the screen always leaves the call`() {
        assertTrue(CallActivity.shouldLeaveCallOnHangup(true, false, CallStatus.LEAVING, false))
        assertTrue(CallActivity.shouldLeaveCallOnHangup(true, true, CallStatus.LEAVING, false))
        assertTrue(CallActivity.shouldLeaveCallOnHangup(false, true, CallStatus.PUBLISHER_FAILED, false))
    }

    @Test
    fun `join is silent after somebody else was in the call`() {
        assertFalse(CallActivity.isJoinSilent(false, false))
        assertTrue(CallActivity.isJoinSilent(false, true))
        assertTrue(CallActivity.isJoinSilent(true, false))
    }
}
