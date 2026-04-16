/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

sealed class ConversationOpsAction {
    object AddToFavorites : ConversationOpsAction()
    object RemoveFromFavorites : ConversationOpsAction()
    object MarkAsRead : ConversationOpsAction()
    object MarkAsUnread : ConversationOpsAction()
    object ShareLink : ConversationOpsAction()
    object Rename : ConversationOpsAction()
    object ToggleArchive : ConversationOpsAction()
    object Leave : ConversationOpsAction()
    object Delete : ConversationOpsAction()
}
