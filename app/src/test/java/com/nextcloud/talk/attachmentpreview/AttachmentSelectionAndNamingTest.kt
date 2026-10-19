/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentSelectionAndNamingTest {

    private val files = listOf("a", "b", "c")

    @Test
    fun replaceUriKeepsPosition() {
        assertEquals(listOf("a", "B", "c"), replaceUri(files, "b", "B"))
    }

    @Test
    fun replaceUriOfUnknownFileChangesNothing() {
        assertSame(files, replaceUri(files, "x", "y"))
    }

    @Test
    fun replaceUriWithSameValueChangesNothing() {
        assertSame(files, replaceUri(files, "a", "a"))
    }

    @Test
    fun toggleExcludesAndReincludesFile() {
        val excluded = toggleSelection(files, emptySet(), "b")
        assertEquals(setOf("b"), excluded)
        assertEquals(emptySet<String>(), toggleSelection(files, excluded, "b"))
    }

    @Test
    fun lastSelectedFileCannotBeExcluded() {
        val unselected = setOf("a", "b")
        assertEquals(unselected, toggleSelection(files, unselected, "c"))
    }

    @Test
    fun singleFileCannotBeExcluded() {
        assertEquals(emptySet<String>(), toggleSelection(listOf("a"), emptySet(), "a"))
    }

    @Test
    fun toggleOfUnknownFileChangesNothing() {
        assertEquals(setOf("a"), toggleSelection(files, setOf("a"), "zzz"))
    }

    @Test
    fun selectedFilesKeepListOrder() {
        assertEquals(listOf("a", "c"), selectedFiles(files, setOf("b")))
    }

    @Test
    fun editedFileNameAddsSuffixAndStamp() {
        assertEquals("photo_edited_20261004-115100-123.jpg", editedFileName("photo.jpg", "20261004-115100-123", "jpg"))
    }

    @Test
    fun editedFileNameDoesNotStackSuffixes() {
        val once = editedFileName("photo.jpg", "20261004-115100-123", "jpg")
        assertEquals("photo_edited_20261004-120000-001.png", editedFileName(once, "20261004-120000-001", "png"))
    }

    @Test
    fun editedFileNameHandlesNamesWithoutExtensionOrBase() {
        assertEquals("scan_edited_1.jpg", editedFileName("scan", "1", "jpg"))
        assertEquals("image_edited_1.jpg", editedFileName(".jpg", "1", "jpg"))
    }

    @Test
    fun editedFileNamesAreRecognisedAndOriginalsAreNot() {
        assertTrue(isEditedFileName(editedFileName("photo.jpg", "20261004-115100-123", "jpg")))
        assertFalse(isEditedFileName("photo.jpg"))
        assertFalse(isEditedFileName("2026-10-04 11-51-00.jpg"))
        assertFalse(isEditedFileName("my_edited_notes.jpg"))
    }

    @Test
    fun onlyPngSourcesStayPng() {
        assertTrue(editOutputIsPng("image/png"))
        assertFalse(editOutputIsPng("image/jpeg"))
        assertFalse(editOutputIsPng(null))
    }
}
