/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentToolBarLogicTest {

    @Test
    fun permissionShrinksToIconWhenFullPillIsWiderThanSlot() {
        assertTrue(shouldCompactPermission(fullWidthPx = 910, availableWidthPx = 775))
    }

    @Test
    fun permissionKeepsLabelWhenFullPillFits() {
        assertFalse(shouldCompactPermission(fullWidthPx = 775, availableWidthPx = 775))
        assertFalse(shouldCompactPermission(fullWidthPx = 600, availableWidthPx = 775))
    }
}
