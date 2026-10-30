/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FileAttachmentPreviewResultTest {

    @Test
    fun resultKeepsFilesCaptionAndFlags() {
        val bundle = FileAttachmentPreviewFragment.packResult(listOf("a", "b"), "caption", true, false)

        assertEquals(listOf("a", "b"), bundle.getStringArrayList(FileAttachmentPreviewFragment.RESULT_FILES))
        assertEquals("caption", bundle.getString(FileAttachmentPreviewFragment.RESULT_CAPTION))
        assertTrue(bundle.getBoolean(FileAttachmentPreviewFragment.RESULT_COMPRESS_IMAGES))
        assertFalse(bundle.getBoolean(FileAttachmentPreviewFragment.RESULT_ALLOW_UPDATE))
    }

    @Test
    fun resultKeepsFileOrderAndAllowUpdate() {
        val bundle = FileAttachmentPreviewFragment.packResult(listOf("2", "1", "3"), "", false, true)

        assertEquals(listOf("2", "1", "3"), bundle.getStringArrayList(FileAttachmentPreviewFragment.RESULT_FILES))
        assertTrue(bundle.getBoolean(FileAttachmentPreviewFragment.RESULT_ALLOW_UPDATE))
    }
}
