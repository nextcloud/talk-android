/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.callnotification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for how CallNotificationActivity resolves the notification id that backs the ringing call window.
 *
 * The notification id is the push message timestamp, always a positive value. A missing
 * KEY_NOTIFICATION_TIMESTAMP extra decodes to 0 via Bundle.getInt(), which is not a real call notification.
 * notificationIdOrNull() maps that 0 to null so dismissCallNotification() (notificationTimestamp?.let { cancel(it) })
 * never cancels an unrelated notification id 0, and so setupNotificationCanceledRoutine() can close a window that
 * has no backing notification instead of polling for id 0 forever.
 */
class CallNotificationActivityNotificationTest {

    @Test
    fun `missing timestamp extra (0) resolves to null`() {
        assertNull(
            "A 0 timestamp means no notification backs the window",
            CallNotificationActivity.notificationIdOrNull(0)
        )
    }

    @Test
    fun `a real push timestamp is used verbatim as the notification id`() {
        val timestamp = 1_700_000_000
        assertEquals(
            timestamp,
            CallNotificationActivity.notificationIdOrNull(timestamp)
        )
    }

    @Test
    fun `the cancel guard cancels only when a notification actually backs the window`() {
        // Models the dismissCallNotification() guard notificationTimestamp?.let { cancel(it) } against the raw extra value.
        // Before the fix, a missing key gave 0 and cancel(0) ran; now 0 yields null and nothing is cancelled,
        // while a real id is still cancelled.
        assertNull("No id -> no cancel", CallNotificationActivity.notificationIdOrNull(0))
        assertEquals("Real id -> cancel that id", 42, CallNotificationActivity.notificationIdOrNull(42))
    }
}
