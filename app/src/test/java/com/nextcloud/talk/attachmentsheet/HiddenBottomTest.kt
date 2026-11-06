/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenBottomTest {
    @Test
    fun partialSheetKeepsBarAboveNavigation() {
        val bottom = HiddenBottom(1532)
        bottom.update(800f, 1200)
        assertEquals(468, bottom.value)
        assertEquals(1532, 800 + 1200 - bottom.value)
    }

    @Test
    fun expandedSheetDoesNotAddInsetAlreadyReservedByParent() {
        val bottom = HiddenBottom(1532)
        bottom.update(100f, 1432)
        assertEquals(0, bottom.value)
    }

    @Test
    fun expandedEdgeToEdgeSheetReservesNavigation() {
        val bottom = HiddenBottom(1532)
        bottom.update(100f, 1510)
        assertEquals(78, bottom.value)
    }

    @Test
    fun movingSheetReleasesPreviousCompensation() {
        val bottom = HiddenBottom(1610)
        bottom.update(800f, 1200)
        bottom.update(100f, 1200)
        assertEquals(0, bottom.value)
    }
}
