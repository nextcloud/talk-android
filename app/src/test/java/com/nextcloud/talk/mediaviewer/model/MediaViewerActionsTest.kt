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
import org.junit.Test

class MediaViewerActionsTest {

    private fun actions(
        reply: Boolean = true,
        delete: Boolean = true,
        forwardFile: Boolean = true,
        canSend: Boolean = true
    ) = allowedActions().copy(
        showReply = reply,
        showDelete = delete,
        showForwardFile = forwardFile,
        canSendToConversation = canSend
    )

    private fun allowedActions() =
        MessageActionsState(
            showEmojiBar = false,
            selfReactions = emptySet(),
            showEditInfo = false,
            lastEditedBy = "",
            lastEditedAt = "",
            showReply = true,
            showReplyPrivately = false,
            showOpenThread = false,
            showForward = false,
            showForwardFile = true,
            canSendToConversation = true,
            showEdit = false,
            showCopy = false,
            showCopyMessageLink = false,
            showMarkAsUnread = false,
            showRemind = false,
            showPin = false,
            isPinned = false,
            showTranslate = false,
            showShareToNote = false,
            showShare = true,
            showSave = true,
            showOpenInFiles = false,
            showDelete = true
        )

    // counter

    @Test
    fun counter_newestItemIsTheLast() {
        // pager order is oldest first: the newest item (index 4 of 5) = 5 of 5, the oldest = 1 of 5
        assertEquals(MediaViewerCounter(5, 5, false), mediaViewerCounter(4, 5, false))
        assertEquals(MediaViewerCounter(1, 5, false), mediaViewerCounter(0, 5, false))
    }

    @Test
    fun counter_positionMovesWithTheItemWhenOlderItemsArePagedIn() {
        val newest = mediaViewerCounter(index = 4, total = 5, hasMoreOlder = true)!!
        assertEquals(MediaViewerCounter(5, 5, true), newest)
        // 50 older items are loaded: the pager index of the viewed item moves by 50, it is still the newest
        val after = mediaViewerCounter(index = 54, total = 55, hasMoreOlder = true)!!
        assertEquals(MediaViewerCounter(55, 55, true), after)

        val middle = mediaViewerCounter(index = 2, total = 5, hasMoreOlder = true)!!
        val middleAfter = mediaViewerCounter(index = 52, total = 55, hasMoreOlder = false)!!
        assertEquals(3, middle.position)
        assertEquals(middle.position + 50, middleAfter.position)
        assertFalse(middleAfter.totalIsLowerBound)
    }

    @Test
    fun counter_totalIsExactOnceNothingOlderIsLeft() {
        assertFalse(mediaViewerCounter(0, 3, hasMoreOlder = false)!!.totalIsLowerBound)
    }

    @Test
    fun counter_nullWithoutItemsOrForIndexOutOfRange() {
        assertNull(mediaViewerCounter(0, 0, false))
        assertNull(mediaViewerCounter(-1, 3, false))
        assertNull(mediaViewerCounter(3, 3, false))
    }

    // media kind

    @Test
    fun drawing_isOnlyForPhotos() {
        assertTrue(isDrawableImage("image/jpeg"))
        assertTrue(isDrawableImage("image/png"))
        assertFalse(isDrawableImage("image/gif"))
        assertFalse(isDrawableImage("image/svg+xml"))
        assertFalse(isDrawableImage("video/mp4"))
        assertFalse(isDrawableImage("application/pdf"))
    }

    // menu

    @Test
    fun menu_allEntriesForAnOwnPhotoWithAllRights() {
        val menu = mediaViewerMenuState("image/jpeg", hasLocalFile = true, actions = actions())
        assertEquals(MediaViewerMenuState(true, true, true, true, true, true, true, true), menu)
    }

    @Test
    fun menu_videoGetsEverythingButDrawing() {
        val menu = mediaViewerMenuState("video/mp4", hasLocalFile = true, actions = actions())
        assertFalse(menu.draw)
        assertTrue(menu.forward && menu.reply && menu.delete && menu.share && menu.saveToGallery)
    }

    @Test
    fun menu_withoutTheMessageInTheDatabaseOnlyWhatNeedsNoRightsRemains() {
        val menu = mediaViewerMenuState("image/jpeg", hasLocalFile = true, actions = null)
        assertEquals(MediaViewerMenuState(true, true, true, true, false, false, false, false), menu)
    }

    @Test
    fun menu_followsTheChatRules() {
        val menu = mediaViewerMenuState(
            "image/jpeg",
            hasLocalFile = true,
            actions = actions(reply = false, delete = false, forwardFile = false, canSend = false)
        )
        assertFalse(menu.reply)
        assertFalse(menu.delete)
        assertFalse(menu.forward)
        assertFalse(menu.draw)
        assertTrue(menu.showInChat && menu.showAllMedia)
    }

    @Test
    fun menu_shareSaveAndDrawNeedTheDownloadedFile_forwardDoesNot() {
        val menu = mediaViewerMenuState("image/jpeg", hasLocalFile = false, actions = actions())
        assertFalse(menu.share)
        assertFalse(menu.saveToGallery)
        assertFalse(menu.draw)
        assertTrue(menu.forward)
    }
}
