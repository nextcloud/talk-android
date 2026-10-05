/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditNameClaimTest {

    @Test
    fun claimFirstFreeKeepsThePlainNameWhenItIsFree() {
        assertEquals("a", claimFirstFree(3, { if (it == 0) "a" else "a-$it" }, { true }))
    }

    @Test
    fun claimFirstFreeAddsNumbersWhileNamesAreTaken() {
        val taken = setOf("a", "a-1")
        val claimed = mutableListOf<String>()
        val result = claimFirstFree(
            maxAttempts = 5,
            candidate = { if (it == 0) "a" else "a-$it" },
            claim = { name -> (name !in taken).also { if (it) claimed.add(name) } }
        )
        assertEquals("a-2", result)
        assertEquals(listOf("a-2"), claimed)
    }

    @Test
    fun claimFirstFreeGivesUpAfterTheLastAttempt() {
        var tried = 0
        val result = claimFirstFree(3, { it }) {
            tried++
            false
        }
        assertNull(result)
        assertEquals(3, tried)
    }

    @Test
    fun numberedEditNamesStillCountAsOwnEdits() {
        // the numeric suffix goes into the stamp, which the edit-name recognition accepts
        val numbered = editedFileName("photo.jpg", "20261004-115100-123-2", "jpg")
        assertTrue(isEditedFileName(numbered))
        assertEquals("photo_edited_20261004-120000-001.jpg", editedFileName(numbered, "20261004-120000-001", "jpg"))
    }
}
