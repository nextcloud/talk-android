/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.model

import com.nextcloud.talk.utils.message.SendMessageUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaViewerItemTest {

    private val refUtils = SendMessageUtils()

    private fun item(messageId: Long, referenceId: String?) =
        MediaViewerItem(
            messageId = messageId,
            referenceId = referenceId,
            fileId = "file-$messageId",
            fileName = "file-$messageId.jpg",
            mimeType = "image/jpeg",
            path = "/remote.php/dav/files/user/file-$messageId.jpg",
            link = "https://example.com/f/$messageId",
            fileSize = 1024L,
            previewUrl = null,
            actorDisplayName = "Jane Doe",
            timestamp = messageId
        )

    @Test
    fun `consecutive items from the same batch form one group`() {
        val uploadId = "batch-1"
        val items = listOf(
            item(1, refUtils.generateGroupedReferenceId(uploadId, 1)),
            item(2, refUtils.generateGroupedReferenceId(uploadId, 2)),
            item(3, refUtils.generateGroupedReferenceId(uploadId, 3))
        )

        val groups = items.toMediaViewerGroups()

        assertEquals(1, groups.size)
        assertEquals(listOf(1L, 2L, 3L), groups[0].items.map { it.messageId })
    }

    @Test
    fun `a different batch hash starts a new group`() {
        val items = listOf(
            item(1, refUtils.generateGroupedReferenceId("batch-1", 1)),
            item(2, refUtils.generateGroupedReferenceId("batch-1", 2)),
            item(3, refUtils.generateGroupedReferenceId("batch-2", 1))
        )

        val groups = items.toMediaViewerGroups()

        assertEquals(2, groups.size)
        assertEquals(listOf(1L, 2L), groups[0].items.map { it.messageId })
        assertEquals(listOf(3L), groups[1].items.map { it.messageId })
    }

    @Test
    fun `items with no grouped referenceId each stay their own group`() {
        val items = listOf(
            item(1, "plain-hex-reference-id-not-matching-the-batch-format"),
            item(2, null)
        )

        val groups = items.toMediaViewerGroups()

        assertEquals(2, groups.size)
        assertEquals(listOf(1L), groups[0].items.map { it.messageId })
        assertEquals(listOf(2L), groups[1].items.map { it.messageId })
    }

    @Test
    fun `an empty list produces no groups`() {
        val groups = emptyList<MediaViewerItem>().toMediaViewerGroups()

        assertEquals(0, groups.size)
    }

    @Test
    fun `group key is stable and unique per group`() {
        val items = listOf(
            item(1, refUtils.generateGroupedReferenceId("batch-1", 1)),
            item(2, refUtils.generateGroupedReferenceId("batch-1", 2)),
            item(3, refUtils.generateGroupedReferenceId("batch-2", 1))
        )

        val keys = items.toMediaViewerGroups().map { it.key }

        assertEquals(keys.size, keys.toSet().size)
    }
}
