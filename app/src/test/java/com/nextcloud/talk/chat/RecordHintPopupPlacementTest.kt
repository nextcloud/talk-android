/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordHintPopupPlacementTest {

    private fun place(anchorLeft: Int, anchorTop: Int = 1000, windowWidth: Int = 1000) =
        RecordHintPopup.hintPlacement(
            anchorLeft = anchorLeft,
            anchorTop = anchorTop,
            anchorWidth = 100,
            windowWidth = windowWidth,
            windowHeight = 2000,
            hintWidth = 300,
            margin = 20,
            gap = 10,
            arrowWidth = 40
        )

    @Test
    fun hintBottomIsTheGapAboveTheAnchorTop() {
        val placement = place(anchorLeft = 450, anchorTop = 1000)
        // measured from the bottom of the window: the bottom of the hint is 10 px above the top of the anchor
        assertEquals(2000 - 1000 + 10, placement.bottomOffset)
    }

    @Test
    fun hintFollowsAnAnchorWhichMovedUp() {
        val before = place(anchorLeft = 450, anchorTop = 1000)
        val after = place(anchorLeft = 450, anchorTop = 900)
        // a button moved up by 100 px takes the hint up by 100 px, however high the hint is
        assertEquals(before.bottomOffset + 100, after.bottomOffset)
    }

    @Test
    fun hintIsCentredOnTheAnchor() {
        val placement = place(anchorLeft = 450)
        assertEquals(500 - 150, placement.x)
        // the arrow is in the middle of the hint
        assertEquals(150 - 20, placement.arrowLeftMargin)
    }

    @Test
    fun hintIsKeptInsideTheWindowAtTheRightEdge() {
        val placement = place(anchorLeft = 900)
        assertEquals(1000 - 20 - 300, placement.x)
        // the arrow still points at the anchor centre (950)
        assertEquals(950, placement.x + placement.arrowLeftMargin + 20)
    }

    @Test
    fun hintIsKeptInsideTheWindowAtTheLeftEdge() {
        val placement = place(anchorLeft = 0)
        assertEquals(20, placement.x)
        assertEquals(50 - 20 - 20, placement.arrowLeftMargin)
    }

    @Test
    fun anchorInANarrowWindowIsStillPointedAt() {
        // the chat in a pane of 600 px: only the window width matters, not the width of the screen
        val placement = place(anchorLeft = 500, windowWidth = 600)
        assertEquals(600 - 20 - 300, placement.x)
        assertEquals(550, placement.x + placement.arrowLeftMargin + 20)
    }
}
