/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

/**
 * Entries of the attachment sheet that open another screen or flow. The declaration order is the display order.
 */
enum class AttachmentAction {
    PICTURE_FROM_CAM,
    VIDEO_FROM_CAM,
    GALLERY,
    FILE_FROM_LOCAL,
    FILE_FROM_CLOUD,
    CREATE_THREAD,
    CREATE_POLL,
    SHARE_LOCATION,
    SHARE_CONTACT
}

data class AttachmentVisibilityInput(
    val isRemoteConversation: Boolean,
    val hasGeoLocationCapability: Boolean,
    val hasPollsCapability: Boolean,
    val isOneToOneConversation: Boolean,
    val hasThreadsCapability: Boolean,
    val isInsideThread: Boolean,
    val hasCamera: Boolean
)

/**
 * Decides which entries are offered. Federated conversations only allow the gallery, polls and threads; the video
 * entry additionally needs camera hardware.
 */
fun resolveAttachmentActions(input: AttachmentVisibilityInput): List<AttachmentAction> =
    AttachmentAction.entries.filter { action ->
        when (action) {
            AttachmentAction.PICTURE_FROM_CAM,
            AttachmentAction.FILE_FROM_LOCAL,
            AttachmentAction.FILE_FROM_CLOUD,
            AttachmentAction.SHARE_CONTACT -> !input.isRemoteConversation

            AttachmentAction.VIDEO_FROM_CAM -> !input.isRemoteConversation && input.hasCamera

            AttachmentAction.GALLERY -> true

            AttachmentAction.SHARE_LOCATION -> !input.isRemoteConversation && input.hasGeoLocationCapability

            AttachmentAction.CREATE_POLL -> input.hasPollsCapability && !input.isOneToOneConversation

            AttachmentAction.CREATE_THREAD -> input.hasThreadsCapability && !input.isInsideThread
        }
    }
