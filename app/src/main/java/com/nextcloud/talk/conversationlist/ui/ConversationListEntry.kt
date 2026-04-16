/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.models.json.participants.Participant

/**
 * Sealed class that represents every possible entry in the conversation list LazyColumn.
 */
sealed class ConversationListEntry {
    /** Section header (e.g. "Conversations", "Users", "Messages") */
    data class Header(val title: String) : ConversationListEntry()

    /** A single conversation item */
    data class ConversationEntry(val model: ConversationModel) : ConversationListEntry()

    /** A message search result */
    data class MessageResultEntry(val result: SearchMessageEntry) : ConversationListEntry()

    /** A contact / user search result */
    data class ContactEntry(val participant: Participant) : ConversationListEntry()

    /** "Load more" button at the end of message search results */
    data object LoadMore : ConversationListEntry()
}
