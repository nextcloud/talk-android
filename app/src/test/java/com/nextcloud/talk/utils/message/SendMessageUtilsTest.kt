/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendMessageUtilsTest {

    private val sut = SendMessageUtils()

    // Matches the cross-client format from https://github.com/nextcloud/spreed/pull/19040:
    // sha256(uploadId)[0:60] + "-" + order, zero-padded to 3 digits.
    private val groupedReferenceIdPattern = Regex("^[a-f0-9]{60}-[0-9]{3}$")

    @Test
    fun `generated id matches the cross-client grouped format`() {
        val referenceId = sut.generateGroupedReferenceId("upload-id-1", 1)

        assertTrue(groupedReferenceIdPattern.matches(referenceId))
    }

    @Test
    fun `order is zero-padded to three digits`() {
        assertTrue(sut.generateGroupedReferenceId("upload-id-1", 1).endsWith("-001"))
        assertTrue(sut.generateGroupedReferenceId("upload-id-1", 42).endsWith("-042"))
        assertTrue(sut.generateGroupedReferenceId("upload-id-1", 123).endsWith("-123"))
    }

    @Test
    fun `same uploadId and order always produce the same id`() {
        val first = sut.generateGroupedReferenceId("upload-id-1", 3)
        val second = sut.generateGroupedReferenceId("upload-id-1", 3)

        assertEquals(first, second)
    }

    @Test
    fun `different orders for the same uploadId share the hash prefix`() {
        val first = sut.generateGroupedReferenceId("upload-id-1", 1)
        val second = sut.generateGroupedReferenceId("upload-id-1", 2)

        assertEquals(first.substringBefore("-"), second.substringBefore("-"))
        assertNotEquals(first, second)
    }

    @Test
    fun `different uploadIds never share the hash prefix`() {
        val first = sut.generateGroupedReferenceId("upload-id-1", 1)
        val second = sut.generateGroupedReferenceId("upload-id-2", 1)

        assertNotEquals(first.substringBefore("-"), second.substringBefore("-"))
    }
}
