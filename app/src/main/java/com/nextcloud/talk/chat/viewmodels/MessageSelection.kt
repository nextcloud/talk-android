/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.viewmodels

import com.nextcloud.talk.chat.data.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The messages of a chat selected for an action on several of them. The selection is active while it is not empty:
 * deselecting the last message ends it.
 */
class MessageSelection {
    private val _ids = MutableStateFlow<Set<Int>>(emptySet())
    val ids: StateFlow<Set<Int>> = _ids

    val isActive: Boolean
        get() = _ids.value.isNotEmpty()

    fun toggle(messageId: Int) {
        _ids.update { if (messageId in it) it - messageId else it + messageId }
    }

    fun clear() {
        _ids.value = emptySet()
    }

    /** Ends an active selection for the back action. Returns false, when there is none and back is not handled. */
    fun consumeBack(): Boolean {
        if (!isActive) {
            return false
        }
        clear()
        return true
    }
}

/** What a forward of selected messages sends: one text per message in chat order, and how many were left out. */
data class ForwardPlan(val texts: List<String>, val skipped: Int) {
    /** What the user is told: nothing to forward stops the forward, left out messages are named, all sent is silent. */
    val outcome: ForwardOutcome
        get() = when {
            texts.isEmpty() -> ForwardOutcome.NOTHING_TO_FORWARD
            skipped > 0 -> ForwardOutcome.SOME_SKIPPED
            else -> ForwardOutcome.ALL
        }
}

enum class ForwardOutcome { ALL, SOME_SKIPPED, NOTHING_TO_FORWARD }

/** Plans the forward of [messages] (any order): each forwardable message becomes its own text, ordered by id. */
fun planForward(messages: List<ChatMessage>, canForward: (ChatMessage) -> Boolean): ForwardPlan {
    val texts = messages.sortedBy { it.jsonMessageId }
        .filter(canForward)
        .map { it.message.orEmpty() }
        .filter { it.isNotBlank() }
    return ForwardPlan(texts, messages.size - texts.size)
}
