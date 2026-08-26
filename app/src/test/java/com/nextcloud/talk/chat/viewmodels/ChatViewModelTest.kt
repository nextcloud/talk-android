/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.viewmodels

import com.nextcloud.talk.chat.ui.model.ChatMessageUi
import com.nextcloud.talk.chat.ui.model.MessageStatusIcon
import com.nextcloud.talk.chat.ui.model.MessageTypeContent
import com.nextcloud.talk.utils.message.SendMessageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

@Suppress("TooManyFunctions")
class ChatViewModelTest {

    // The unread marker latch: the marker position must only be derived from the visible window
    // when the window provably reaches back to the unread boundary — otherwise a window of
    // only-unread messages (e.g. after a capped fetch of the newest messages) would place the
    // marker in the middle of the unread messages.

    @Test
    fun `marker is placed at the first message above the boundary when the boundary is visible`() {
        val messages = listOf(uiMessage(39), uiMessage(40), uiMessage(41), uiMessage(42))

        assertEquals(41, ChatViewModel.findFirstUnreadMessageId(messages, lastReadMessage = 40))
    }

    @Test
    fun `marker is placed correctly when the boundary message itself is missing`() {
        // the last read message may have been deleted or expired — an older read message is
        // equally valid proof that the window reaches the boundary
        val messages = listOf(uiMessage(38), uiMessage(41), uiMessage(42))

        assertEquals(41, ChatViewModel.findFirstUnreadMessageId(messages, lastReadMessage = 40))
    }

    @Test
    fun `no marker is placed when the window floats entirely above the boundary`() {
        val messages = listOf(uiMessage(141), uiMessage(142), uiMessage(143))

        assertNull(ChatViewModel.findFirstUnreadMessageId(messages, lastReadMessage = 40))
    }

    @Test
    fun `temporary messages with negative ids are no proof of the boundary`() {
        val messages = listOf(uiMessage(-5), uiMessage(141), uiMessage(142))

        assertNull(ChatViewModel.findFirstUnreadMessageId(messages, lastReadMessage = 40))
    }

    @Test
    fun `no marker is placed when everything is read`() {
        val messages = listOf(uiMessage(38), uiMessage(39), uiMessage(40))

        assertNull(ChatViewModel.findFirstUnreadMessageId(messages, lastReadMessage = 40))
    }

    // combineFileShareGroups(): combining batch-uploaded file shares into grouped "album" bubbles.
    // Mirrors web's combineFileMessages.ts tests - see https://github.com/nextcloud/spreed/pull/19040

    private val refUtils = SendMessageUtils()

    @Test
    fun `consecutive file shares from the same batch combine into one group`() {
        val uploadId = "batch-1"
        val messages = listOf(
            mediaMessage(1, refUtils.generateGroupedReferenceId(uploadId, 1)),
            mediaMessage(2, refUtils.generateGroupedReferenceId(uploadId, 2)),
            mediaMessage(3, refUtils.generateGroupedReferenceId(uploadId, 3))
        )

        val result = combineFileShareGroups(messages)

        assertEquals(1, result.size)
        assertTrue(result[0] is CombinedUnit.Group)
        assertEquals(listOf(1, 2, 3), result[0].messages.map { it.id })
    }

    @Test
    fun `a real caption ends the group at that message`() {
        val uploadId = "batch-2"
        val messages = listOf(
            mediaMessage(1, refUtils.generateGroupedReferenceId(uploadId, 1)),
            mediaMessage(2, refUtils.generateGroupedReferenceId(uploadId, 2), plainMessage = "check these out"),
            mediaMessage(3, refUtils.generateGroupedReferenceId(uploadId, 3))
        )

        val result = combineFileShareGroups(messages)

        assertEquals(2, result.size)
        assertEquals(listOf(1, 2), (result[0] as CombinedUnit.Group).messages.map { it.id })
        assertEquals(3, (result[1] as CombinedUnit.Single).message.id)
    }

    @Test
    fun `a different reply target breaks the group`() {
        val uploadId = "batch-3"
        val parentA = mediaMessage(100, referenceId = null)
        val parentB = mediaMessage(200, referenceId = null)
        val messages = listOf(
            mediaMessage(1, refUtils.generateGroupedReferenceId(uploadId, 1), parentMessage = parentA),
            mediaMessage(2, refUtils.generateGroupedReferenceId(uploadId, 2), parentMessage = parentB)
        )

        val result = combineFileShareGroups(messages)

        assertEquals(2, result.size)
        assertTrue(result.all { it is CombinedUnit.Single })
    }

    @Test
    fun `an interleaved non-file message breaks the group`() {
        val uploadId = "batch-4"
        val messages = listOf(
            mediaMessage(1, refUtils.generateGroupedReferenceId(uploadId, 1)),
            uiMessage(2),
            mediaMessage(3, refUtils.generateGroupedReferenceId(uploadId, 3))
        )

        val result = combineFileShareGroups(messages)

        assertEquals(3, result.size)
        assertTrue(result.all { it is CombinedUnit.Single })
    }

    @Test
    fun `audio and vcard file shares never combine even from the same batch`() {
        val uploadId = "batch-5"
        val messages = listOf(
            mediaMessage(1, refUtils.generateGroupedReferenceId(uploadId, 1), mimeType = "audio/mpeg"),
            mediaMessage(2, refUtils.generateGroupedReferenceId(uploadId, 2), mimeType = "text/vcard")
        )

        val result = combineFileShareGroups(messages)

        assertEquals(2, result.size)
        assertTrue(result.all { it is CombinedUnit.Single })
    }

    @Test
    fun `a failed message is excluded and breaks the group around it`() {
        val uploadId = "batch-6"
        val messages = listOf(
            mediaMessage(1, refUtils.generateGroupedReferenceId(uploadId, 1)),
            mediaMessage(2, refUtils.generateGroupedReferenceId(uploadId, 2), statusIcon = MessageStatusIcon.FAILED),
            mediaMessage(3, refUtils.generateGroupedReferenceId(uploadId, 3))
        )

        val result = combineFileShareGroups(messages)

        assertEquals(3, result.size)
        assertTrue(result.all { it is CombinedUnit.Single })
    }

    @Test
    fun `still-uploading files from the same batch combine into one group immediately`() {
        val uploadId = "batch-7"
        val messages = listOf(
            mediaMessage(1, refUtils.generateGroupedReferenceId(uploadId, 1), isTemporary = true),
            mediaMessage(2, refUtils.generateGroupedReferenceId(uploadId, 2), isTemporary = true),
            mediaMessage(3, refUtils.generateGroupedReferenceId(uploadId, 3), isTemporary = true)
        )

        val result = combineFileShareGroups(messages)

        assertEquals(1, result.size)
        assertTrue(result[0] is CombinedUnit.Group)
        assertEquals(listOf(1, 2, 3), result[0].messages.map { it.id })
    }

    @Test
    fun `a mix of an already-synced and a still-uploading file from the same batch combine`() {
        val uploadId = "batch-8"
        val messages = listOf(
            mediaMessage(1, refUtils.generateGroupedReferenceId(uploadId, 1), isTemporary = false),
            mediaMessage(2, refUtils.generateGroupedReferenceId(uploadId, 2), isTemporary = true)
        )

        val result = combineFileShareGroups(messages)

        assertEquals(1, result.size)
        assertTrue(result[0] is CombinedUnit.Group)
        assertEquals(listOf(1, 2), result[0].messages.map { it.id })
    }

    @Test
    fun `messages with non-grouped referenceIds never combine`() {
        val messages = listOf(
            mediaMessage(1, referenceId = "abcdef0123456789abcdef0123456789"),
            mediaMessage(2, referenceId = "abcdef0123456789abcdef0123456789")
        )

        val result = combineFileShareGroups(messages)

        assertEquals(2, result.size)
        assertTrue(result.all { it is CombinedUnit.Single })
    }

    private fun uiMessage(id: Int): ChatMessageUi =
        ChatMessageUi(
            id = id,
            message = "message $id",
            renderMarkdown = false,
            actorDisplayName = "Other User",
            isThread = false,
            threadTitle = "",
            threadReplies = 0,
            incoming = true,
            isDeleted = false,
            avatarUrl = null,
            statusIcon = MessageStatusIcon.SENT,
            timestamp = id.toLong(),
            date = LocalDate.of(2026, 8, 12),
            content = MessageTypeContent.RegularText
        )

    @Suppress("LongParameterList")
    private fun mediaMessage(
        id: Int,
        referenceId: String?,
        mimeType: String = "image/jpeg",
        plainMessage: String = "{file}",
        parentMessage: ChatMessageUi? = null,
        isDeleted: Boolean = false,
        statusIcon: MessageStatusIcon = MessageStatusIcon.SENT,
        isTemporary: Boolean = false
    ): ChatMessageUi {
        val content = if (isTemporary) {
            MessageTypeContent.UploadingMedia(
                localFileUri = "file://local",
                fileName = "file-$id",
                caption = null,
                mimeType = mimeType,
                drawableResourceId = 0
            )
        } else {
            MessageTypeContent.Media(
                previewUrl = null,
                drawableResourceId = 0,
                mimeType = mimeType
            )
        }
        return ChatMessageUi(
            id = id,
            message = plainMessage,
            plainMessage = plainMessage,
            renderMarkdown = false,
            actorDisplayName = "Other User",
            isThread = false,
            threadTitle = "",
            threadReplies = 0,
            incoming = true,
            isDeleted = isDeleted,
            avatarUrl = null,
            statusIcon = statusIcon,
            timestamp = id.toLong(),
            date = LocalDate.of(2026, 8, 12),
            content = content,
            parentMessage = parentMessage,
            referenceId = referenceId
        )
    }
}
