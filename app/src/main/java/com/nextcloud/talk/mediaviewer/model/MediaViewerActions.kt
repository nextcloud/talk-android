/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.model

import com.nextcloud.talk.chat.ui.MessageActionsState
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.MimetypeUtils
import java.io.File
import java.io.IOException

/**
 * What the counter in the viewer's top bar shows. [position] counts from the oldest loaded item, so the
 * newest item is "M of M", as in other messengers. When older items are paged in, both [position] and
 * [total] grow by their number while the viewer stays on the same item.
 * [totalIsLowerBound] is true while older items may still be loaded, shown as "N of M+".
 */
data class MediaViewerCounter(val position: Int, val total: Int, val totalIsLowerBound: Boolean)

/**
 * [index] is the pager position (0 = oldest loaded item), [total] the number of loaded items and
 * [hasMoreOlder] whether older history may still be fetched. Null when there is nothing to count.
 */
fun mediaViewerCounter(index: Int, total: Int, hasMoreOlder: Boolean): MediaViewerCounter? =
    if (total <= 0 || index !in 0 until total) {
        null
    } else {
        MediaViewerCounter(position = index + 1, total = total, totalIsLowerBound = hasMoreOlder)
    }

/** Photos only: GIFs would lose their animation and SVG cannot be edited by the crop/draw tools. */
fun isDrawableImage(mimeType: String): Boolean =
    mimeType.startsWith(Mimetype.IMAGE_PREFIX) &&
        !MimetypeUtils.isGif(mimeType) &&
        !mimeType.startsWith(SVG_MIME_PREFIX)

private const val SVG_MIME_PREFIX = "image/svg"

/** Which of the viewer's menu entries and top bar buttons are offered for one item. */
data class MediaViewerMenuState(
    val saveToGallery: Boolean,
    val share: Boolean,
    val showAllMedia: Boolean,
    val showInChat: Boolean,
    val reply: Boolean,
    val delete: Boolean,
    val forward: Boolean,
    val draw: Boolean
)

/**
 * Visibility of the viewer's actions. The rules that depend on the chat (reply, delete, forward, drawing into
 * the conversation) come from [actions], built by the chat's own `buildMessageActionsState`; when the message
 * is not in the local database ([actions] null) none of them is offered, as their permissions can't be checked.
 * Sharing and saving only act on the local copy, so they just need the file to be downloaded.
 */
fun mediaViewerMenuState(mimeType: String, hasLocalFile: Boolean, actions: MessageActionsState?): MediaViewerMenuState =
    MediaViewerMenuState(
        saveToGallery = hasLocalFile && (actions?.showSave ?: true),
        share = hasLocalFile && (actions?.showShare ?: true),
        showAllMedia = true,
        showInChat = true,
        reply = actions?.showReply == true,
        delete = actions?.showDelete == true,
        forward = actions?.showForwardFile == true,
        draw = hasLocalFile && isDrawableImage(mimeType) && actions?.canSendToConversation == true
    )

/** An action the viewer hands over to the chat, which owns the message handlers it needs. */
enum class MediaViewerChatAction {
    REPLY,
    DELETE,
    FORWARD,
    DRAW
}

/**
 * The request the viewer sends to ChatActivity through intent extras (the KEY_ constants below).
 * [localPath] is the cached copy (drawing). Nothing else is trusted from the intent: what is forwarded is read from the
 * message in the database.
 */
data class MediaViewerChatRequest(
    val action: MediaViewerChatAction,
    val messageId: Long,
    val localPath: String? = null
) {
    companion object {
        const val KEY_ACTION = "MEDIA_VIEWER_CHAT_ACTION"
        const val KEY_MESSAGE_ID = "MEDIA_VIEWER_CHAT_MESSAGE_ID"
        const val KEY_LOCAL_PATH = "MEDIA_VIEWER_CHAT_LOCAL_PATH"

        /** Null for a missing or unknown action, a missing message id, or an action without the data it needs. */
        fun parse(actionName: String?, messageId: Long, localPath: String?): MediaViewerChatRequest? {
            val action = MediaViewerChatAction.entries.firstOrNull { it.name == actionName }
            val complete = when (action) {
                null -> false
                MediaViewerChatAction.DRAW -> !localPath.isNullOrBlank()
                else -> true
            }
            return if (action != null && complete && messageId > 0L) {
                MediaViewerChatRequest(action, messageId, localPath)
            } else {
                null
            }
        }
    }
}

/** The chat's own rule for [action] (see `buildMessageActionsState`), for the request received from the viewer. */
fun isMediaActionAllowed(action: MediaViewerChatAction, state: MessageActionsState): Boolean =
    when (action) {
        MediaViewerChatAction.REPLY -> state.showReply
        MediaViewerChatAction.DELETE -> state.showDelete
        MediaViewerChatAction.FORWARD -> state.showForwardFile
        MediaViewerChatAction.DRAW -> state.canSendToConversation
    }

/** The path of a file in the user's own storage as the share API expects it: with exactly one leading slash. */
fun remoteSharePath(path: String): String = "/" + path.trimStart('/')

/** True when [file] lies below [directory] (not [directory] itself), after resolving "..", symlinks and the like. */
fun isInsideDirectory(directory: File, file: File): Boolean =
    try {
        val base = directory.canonicalFile
        file.canonicalFile.path.startsWith(base.path + File.separator)
    } catch (_: IOException) {
        false
    }
