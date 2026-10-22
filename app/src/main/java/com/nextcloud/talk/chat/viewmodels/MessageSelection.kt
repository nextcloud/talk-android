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

// Telegram permits at most 100 messages in one forward request.
const val MAX_SELECTED_MESSAGES = 100

// Talk limits one message to 32,000 characters; also cap the combined forwarded text.
const val MAX_FORWARDED_TEXT_LENGTH = 32_000

enum class SelectionLimit { MESSAGE_COUNT, TEXT_LENGTH }

/**
 * The messages of a chat selected for an action on several of them. The selection is active while it is not empty:
 * deselecting the last message ends it.
 */
class MessageSelection {
    private val _ids = MutableStateFlow<Set<Int>>(emptySet())
    private val forwardTextLengths = mutableMapOf<Int, Int>()
    val ids: StateFlow<Set<Int>> = _ids

    val isActive: Boolean
        get() = _ids.value.isNotEmpty()

    /** Returns the reached limit when adding fails. Removing a selected message always succeeds. */
    fun toggle(messageId: Int, forwardText: String?): SelectionLimit? {
        if (messageId in _ids.value) {
            forwardTextLengths.remove(messageId)
            _ids.update { it - messageId }
            return null
        }
        return add(messageId, forwardText)
    }

    private fun add(messageId: Int, forwardText: String?): SelectionLimit? {
        val length = forwardText?.takeIf { it.isNotBlank() }?.length ?: 0
        return when {
            _ids.value.size >= MAX_SELECTED_MESSAGES -> SelectionLimit.MESSAGE_COUNT
            forwardTextLengths.values.sum() + length > MAX_FORWARDED_TEXT_LENGTH -> SelectionLimit.TEXT_LENGTH
            else -> {
                forwardTextLengths[messageId] = length
                _ids.update { it + messageId }
                null
            }
        }
    }

    fun clear() {
        forwardTextLengths.clear()
        _ids.value = emptySet()
    }

    /** Drops the ids that are not in [existing] any more, e.g. a pending message replaced by its server copy. */
    fun retainOnly(existing: Set<Int>) {
        forwardTextLengths.keys.retainAll(existing)
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
        .mapNotNull { forwardedTextOrNull(it, canForward(it)) }
    return ForwardPlan(texts, messages.size - texts.size)
}

/** The same unmodified text is counted at selection time and sent by [planForward]. */
fun forwardedTextOrNull(message: ChatMessage, canForward: Boolean): String? =
    message.message?.takeIf { canForward && it.isNotBlank() }

/** Whether a message with [messageId] can be selected. Pending messages have a negative id until the server answers. */
fun isSelectableMessageId(messageId: Int): Boolean = messageId > 0

/** Whether a message can be selected: not a system or deleted one, and not pending. Used by the row and the menu. */
fun isSelectableMessage(isSystemMessage: Boolean, isDeleted: Boolean, messageId: Int): Boolean =
    !isSystemMessage && !isDeleted && isSelectableMessageId(messageId)

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
