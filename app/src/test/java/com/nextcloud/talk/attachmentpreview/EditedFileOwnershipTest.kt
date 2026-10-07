/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditedFileOwnershipTest {

    private val edited = editedFileName("photo.jpg", "20261004-115100-123", "jpg")

    @Test
    fun ownEditedNameAcceptsOnlyOurProviderAndTheSharedDirectory() {
        val segments = listOf("shared_attachments", edited)
        assertEquals(edited, ownEditedName("com.nextcloud.talk2", "com.nextcloud.talk2", segments))
    }

    @Test
    fun ownEditedNameRejectsForeignAuthority() {
        assertNull(ownEditedName("com.other", "com.nextcloud.talk2", listOf("shared_attachments", edited)))
        assertNull(ownEditedName(null, "com.nextcloud.talk2", listOf("shared_attachments", edited)))
    }

    @Test
    fun ownEditedNameRejectsNamesWithoutEditSuffix() {
        assertNull(ownEditedName("a", "a", listOf("shared_attachments", "photo.jpg")))
    }

    @Test
    fun ownEditedNameRejectsNestedOrOtherPaths() {
        assertNull(ownEditedName("a", "a", listOf("shared_attachments", "42", edited)))
        assertNull(ownEditedName("a", "a", listOf("photos", edited)))
        assertNull(ownEditedName("a", "a", listOf(edited)))
        assertNull(ownEditedName("a", "a", emptyList()))
    }

    @Test
    fun onlyJpegWithPlainRotationKeepsTheExifTag() {
        assertFalse(rotatesPixels(png = false, rotationDegrees = 90, flipped = false))
        assertFalse(rotatesPixels(png = false, rotationDegrees = 0, flipped = false))
        assertTrue(rotatesPixels(png = true, rotationDegrees = 90, flipped = false))
        assertFalse(rotatesPixels(png = true, rotationDegrees = 0, flipped = false))
        assertTrue(rotatesPixels(png = false, rotationDegrees = 0, flipped = true))
        assertTrue(rotatesPixels(png = true, rotationDegrees = 270, flipped = true))
    }
}
