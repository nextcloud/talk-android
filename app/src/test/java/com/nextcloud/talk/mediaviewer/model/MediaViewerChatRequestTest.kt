/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.model

import com.nextcloud.talk.chat.ui.MessageActionsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaViewerChatRequestTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun state(allowed: Boolean) =
        MessageActionsState(
            showEmojiBar = false,
            selfReactions = emptySet(),
            showEditInfo = false,
            lastEditedBy = "",
            lastEditedAt = "",
            showReply = allowed,
            showReplyPrivately = false,
            showOpenThread = false,
            showForward = false,
            showForwardFile = allowed,
            canSendToConversation = allowed,
            showEdit = false,
            showCopy = false,
            showCopyMessageLink = false,
            showMarkAsUnread = false,
            showRemind = false,
            showPin = false,
            isPinned = false,
            showTranslate = false,
            showShareToNote = false,
            showShare = false,
            showSave = false,
            showOpenInFiles = false,
            showDelete = allowed
        )

    // request

    @Test
    fun request_parsesWhatTheViewerSends() {
        assertEquals(
            MediaViewerChatRequest(MediaViewerChatAction.REPLY, 12L),
            MediaViewerChatRequest.parse("REPLY", 12L, null)
        )
        assertEquals(
            MediaViewerChatRequest(MediaViewerChatAction.DRAW, 12L, localPath = "/c/a.jpg"),
            MediaViewerChatRequest.parse("DRAW", 12L, "/c/a.jpg")
        )
        assertEquals(
            MediaViewerChatRequest(MediaViewerChatAction.FORWARD, 12L),
            MediaViewerChatRequest.parse("FORWARD", 12L, null)
        )
    }

    @Test
    fun request_rejectsIncompleteOrUnknown() {
        assertNull(MediaViewerChatRequest.parse(null, 12L, null))
        assertNull(MediaViewerChatRequest.parse("EXPLODE", 12L, null))
        assertNull(MediaViewerChatRequest.parse("REPLY", 0L, null))
        assertNull(MediaViewerChatRequest.parse("DRAW", 12L, null))
    }

    @Test
    fun chatRecheck_eachActionFollowsExactlyItsOwnFlag() {
        val none = state(false)
        val only = mapOf(
            MediaViewerChatAction.REPLY to none.copy(showReply = true),
            MediaViewerChatAction.DELETE to none.copy(showDelete = true),
            MediaViewerChatAction.FORWARD to none.copy(showForwardFile = true),
            MediaViewerChatAction.DRAW to none.copy(canSendToConversation = true)
        )
        MediaViewerChatAction.entries.forEach { action ->
            assertFalse(action.name, isMediaActionAllowed(action, none))
            MediaViewerChatAction.entries.forEach { flagOf ->
                val allowed = isMediaActionAllowed(action, only.getValue(flagOf))
                assertEquals("$action with only the flag of $flagOf", action == flagOf, allowed)
            }
        }
    }

    // forward entries

    @Test
    fun pendingForward_worksOnceAndNeedsTheKey() {
        val entry = PendingFileForward.Entry(1L, "/Talk/a.jpg", "caption")
        val key = PendingFileForward.put(entry)
        assertEquals(entry, PendingFileForward.take(key))
        assertNull(PendingFileForward.take(key))
        assertNull(PendingFileForward.take(null))
        assertNull(PendingFileForward.take("not-a-key"))
    }

    @Test
    fun pendingForward_peekDoesNotConsumeTheEntry() {
        val entry = PendingFileForward.Entry(1L, "/Talk/a.jpg", "")
        val key = PendingFileForward.put(entry)
        assertEquals(entry, PendingFileForward.peek(key))
        assertEquals(entry, PendingFileForward.peek(key))
        assertEquals(entry, PendingFileForward.take(key))
        assertNull(PendingFileForward.peek(key))
        assertNull(PendingFileForward.peek(null))
    }

    // paths

    @Test
    fun remoteSharePath_hasExactlyOneLeadingSlash() {
        assertEquals("/Talk/a.jpg", remoteSharePath("Talk/a.jpg"))
        assertEquals("/Talk/a.jpg", remoteSharePath("/Talk/a.jpg"))
        assertEquals("/Talk/a.jpg", remoteSharePath("//Talk/a.jpg"))
    }

    @Test
    fun insideDirectory_acceptsNestedFiles() {
        val dir = temp.newFolder("shared_attachments")
        val nested = File(File(dir, "42").also { it.mkdirs() }, "a.jpg").also { it.writeText("x") }
        assertTrue(isInsideDirectory(dir, nested))
    }

    @Test
    fun insideDirectory_rejectsTraversalAndOutsideFiles() {
        val dir = temp.newFolder("shared_attachments")
        val outside = temp.newFile("secret.txt")
        assertFalse(isInsideDirectory(dir, outside))
        assertFalse(isInsideDirectory(dir, File(dir, "../secret.txt")))
        assertFalse(isInsideDirectory(dir, dir))
    }
}
