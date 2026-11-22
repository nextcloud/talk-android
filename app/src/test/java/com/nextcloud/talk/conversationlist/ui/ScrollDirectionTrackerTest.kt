/*
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollDirectionTrackerTest {
    @Test
    fun `initial position and unchanged position do not move the controls`() {
        val tracker = ScrollDirectionTracker()
        assertNull(tracker.onPosition(3, 80))
        assertNull(tracker.onPosition(3, 80))
    }

    @Test
    fun `a long scroll updates controls only once per direction`() {
        val tracker = ScrollDirectionTracker()
        val events = (0..500).mapNotNull { tracker.onPosition(0, it) }
        assertEquals(listOf(true), events)
    }

    @Test
    fun `crossing rows does not reverse the direction when the offset resets`() {
        val tracker = ScrollDirectionTracker()
        val positions = listOf(0 to 80, 0 to 90, 1 to 0, 1 to 30, 1 to 20, 0 to 90)
        assertEquals(listOf(true, false), positions.mapNotNull { tracker.onPosition(it.first, it.second) })
    }

    @Test
    fun `direction changes respond immediately after a pause`() {
        val tracker = ScrollDirectionTracker()
        val positions = listOf(0, 10, 10, 9, 9, 10)
        assertEquals(listOf(true, false, true), positions.mapNotNull { tracker.onPosition(0, it) })
    }
}
