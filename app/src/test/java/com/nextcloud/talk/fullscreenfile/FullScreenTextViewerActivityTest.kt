/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.fullscreenfile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FullScreenTextViewerActivityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun missingFileReadsAsNullNotEmptyString() {
        assertNull(FullScreenTextViewerActivity.readTextFileOrNull(tmp.root.resolve("gone.md")))
    }

    @Test
    fun emptyFileReadsAsEmptyStringNotNull() {
        val file = tmp.newFile("empty.md")
        assertEquals("", FullScreenTextViewerActivity.readTextFileOrNull(file))
    }

    @Test
    fun markdownFileIsReadAsUtf8() {
        val file = tmp.newFile("notes.md").apply { writeText("# Заголовок\n- a", Charsets.UTF_8) }
        assertEquals("# Заголовок\n- a", FullScreenTextViewerActivity.readTextFileOrNull(file))
    }
}
