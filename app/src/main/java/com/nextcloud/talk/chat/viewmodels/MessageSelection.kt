/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.viewmodels

import com.nextcloud.talk.chat.data.model.ChatMessage
import kotlinx.coroutines.CancellationException
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

    /** Drops the ids that are not in [existing] any more, e.g. a pending message replaced by its server copy. */
    fun retainOnly(existing: Set<Int>) {
        _ids.update { ids -> ids.filterTo(HashSet()) { it in existing } }
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

/** Whether a message with [messageId] can be selected. Pending messages have a negative id until the server answers. */
fun isSelectableMessageId(messageId: Int): Boolean = messageId > 0

/**
 * Loads the selected messages with [load] in the order of [ids]. A message that cannot be loaded, e.g. one deleted
 * meanwhile, is left out instead of failing the whole selection and reported to [onFailure].
 */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> loadSelected(
    ids: Collection<Int>,
    onFailure: (Int, Exception) -> Unit = { _, _ -> },
    load: suspend (Int) -> T
): List<T> =
    ids.mapNotNull { id ->
        try {
            load(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(id, e)
            null
        }
    }
