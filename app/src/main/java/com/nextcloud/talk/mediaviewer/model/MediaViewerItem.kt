/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.model

import android.os.Parcelable
import com.nextcloud.talk.shareditems.model.SharedFileItem
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.message.groupHashOf
import kotlinx.parcelize.Parcelize

/**
 * A single swipeable item in the media viewer - built from either a locally loaded chat message
 * (already-synced upload batches) or a network-fetched [com.nextcloud.talk.shareditems.model.SharedFileItem]
 * (older history beyond what's loaded locally). Both sources converge on this shape so the viewer
 * and its grouping logic don't need to know which one an item came from. Parcelable so a seed list
 * of these can be passed directly through the Intent that launches the viewer.
 */
@Parcelize
data class MediaViewerItem(
    val messageId: Long,
    val referenceId: String?,
    val fileId: String,
    val fileName: String,
    val mimeType: String,
    val path: String,
    val link: String,
    val fileSize: Long,
    val previewUrl: String?,
    val actorDisplayName: String,
    /** Epoch seconds, matching ChatMessage/ChatMessageJson's own timestamp convention. */
    val timestamp: Long
) : Parcelable

/**
 * A run of consecutive [MediaViewerItem]s uploaded together in one batch (matching referenceId
 * hash, see [groupHashOf]), or a single lone item - mirrors ChatViewModel.MediaGroupItem, but
 * independent of it since it must also represent items fetched from the network. Ordered
 * chronologically, oldest first.
 */
data class MediaViewerGroup(val items: List<MediaViewerItem>) {
    val key: Any get() = items.first().referenceId?.takeIf { it.isNotBlank() } ?: items.first().messageId
}

/**
 * Clusters a chronologically ordered (oldest first) list of items into [MediaViewerGroup]s,
 * exactly like ChatViewModel.combineFileShareGroups() does for the chat list - consecutive items
 * sharing the same upload-batch hash become one group, anything else stays a group of one.
 */
fun List<MediaViewerItem>.toMediaViewerGroups(): List<MediaViewerGroup> {
    val result = mutableListOf<MediaViewerGroup>()
    var pending = mutableListOf<MediaViewerItem>()

    fun flush() {
        if (pending.isNotEmpty()) {
            result.add(MediaViewerGroup(pending.toList()))
        }
        pending = mutableListOf()
    }

    for (item in this) {
        val pendingHash = pending.lastOrNull()?.let { groupHashOf(it.referenceId) }
        val itemHash = groupHashOf(item.referenceId)
        if (pending.isNotEmpty() && (pendingHash == null || pendingHash != itemHash)) {
            flush()
        }
        pending.add(item)
    }
    flush()

    return result
}

/**
 * Caps a chronologically ordered item list to at most [maxItems] centered on the item with
 * [anchorMessageId], so the seed passed through the launching Intent's Parcelable extras stays
 * well under Android's ~1MB Binder transaction limit. Left uncapped, e.g. every image/video a user
 * has scrolled through in the Shared Items gallery (which keeps accumulating pages, unbounded) gets
 * Parcelled at once and risks a TransactionTooLargeException crash.
 *
 * Trimming is safe in the "older" direction - MediaViewerViewModel.loadOlderGroups() pages further
 * back via the network once the seed's oldest item is reached. There's no equivalent for "newer":
 * the shared-items endpoint only supports paging backwards, so items newer than the window are
 * simply unreachable by swiping - an existing, accepted limitation (see MediaViewerViewModel's
 * class doc), just with a lower ceiling than "everything currently loaded".
 */
fun List<MediaViewerItem>.capSeedAroundMessage(
    anchorMessageId: Long,
    maxItems: Int = MAX_SEED_ITEMS
): List<MediaViewerItem> {
    if (size <= maxItems) return this
    val anchorIndex = indexOfFirst { it.messageId == anchorMessageId }.coerceAtLeast(0)
    val start = (anchorIndex - maxItems / 2).coerceIn(0, size - maxItems)
    return subList(start, start + maxItems)
}

private const val MAX_SEED_ITEMS = 200

fun SharedFileItem.isImageOrVideo(): Boolean =
    mimeType.startsWith(Mimetype.IMAGE_PREFIX) || mimeType.startsWith(Mimetype.VIDEO_PREFIX)

fun SharedFileItem.toMediaViewerItem() =
    MediaViewerItem(
        messageId = messageId,
        referenceId = referenceId,
        fileId = id,
        fileName = name,
        mimeType = mimeType,
        path = path,
        link = link,
        fileSize = fileSize,
        previewUrl = previewLink.takeIf { previewAvailable },
        actorDisplayName = actorName,
        timestamp = timestamp
    )
