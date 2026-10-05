/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AttachmentActionTest {

    private val everything = AttachmentVisibilityInput(
        isRemoteConversation = false,
        hasGeoLocationCapability = true,
        hasPollsCapability = true,
        isOneToOneConversation = false,
        hasThreadsCapability = true,
        isInsideThread = false,
        hasCamera = true
    )

    @Test
    fun localGroupConversationOffersEverything() {
        assertEquals(AttachmentAction.entries, resolveAttachmentActions(everything))
    }

    @Test
    fun remoteConversationKeepsOnlyGalleryPollAndThread() {
        val actions = resolveAttachmentActions(everything.copy(isRemoteConversation = true))
        assertEquals(
            listOf(AttachmentAction.GALLERY, AttachmentAction.CREATE_THREAD, AttachmentAction.CREATE_POLL),
            actions
        )
    }

    @Test
    fun missingCameraHidesPhotoAndVideo() {
        val actions = resolveAttachmentActions(everything.copy(hasCamera = false))
        assertFalse(AttachmentAction.VIDEO_FROM_CAM in actions)
        assertFalse(AttachmentAction.PICTURE_FROM_CAM in actions)
    }

    @Test
    fun missingGeoCapabilityHidesLocation() {
        val actions = resolveAttachmentActions(everything.copy(hasGeoLocationCapability = false))
        assertFalse(AttachmentAction.SHARE_LOCATION in actions)
    }

    @Test
    fun pollNeedsCapabilityAndGroupConversation() {
        val withoutCapability = resolveAttachmentActions(everything.copy(hasPollsCapability = false))
        val oneToOne = resolveAttachmentActions(everything.copy(isOneToOneConversation = true))
        assertFalse(AttachmentAction.CREATE_POLL in withoutCapability)
        assertFalse(AttachmentAction.CREATE_POLL in oneToOne)
    }

    @Test
    fun threadNeedsCapabilityAndNoThreadOpen() {
        val withoutCapability = resolveAttachmentActions(everything.copy(hasThreadsCapability = false))
        val insideThread = resolveAttachmentActions(everything.copy(isInsideThread = true))
        assertFalse(AttachmentAction.CREATE_THREAD in withoutCapability)
        assertFalse(AttachmentAction.CREATE_THREAD in insideThread)
    }

    @Test
    fun remoteConversationWithoutCapabilitiesKeepsOnlyGallery() {
        val actions = resolveAttachmentActions(
            everything.copy(
                isRemoteConversation = true,
                hasPollsCapability = false,
                hasThreadsCapability = false,
                hasCamera = false
            )
        )
        assertEquals(listOf(AttachmentAction.GALLERY), actions)
    }
}
