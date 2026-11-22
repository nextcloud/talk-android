/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import com.nextcloud.talk.chat.viewmodels.ChatViewModel.ChatItem

internal fun downloadingMessageKeys(items: List<ChatItem>, downloadingFileIds: List<String>): Set<Any> {
    if (downloadingFileIds.isEmpty()) return emptySet()
    val fileIds = downloadingFileIds.toHashSet()
    return items.mapNotNull { item ->
        val fileId = item.messageOrNull()?.messageParameters?.get("file")?.get("id")
        item.stableKey().takeIf { fileId in fileIds }
    }.toSet()
}
