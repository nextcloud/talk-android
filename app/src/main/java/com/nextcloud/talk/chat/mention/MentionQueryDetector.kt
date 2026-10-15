/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.mention

/**
 * The `@word` the cursor is currently in. [start] and [end] delimit the whole token including the
 * leading `@`, [query] is what follows the first `@` and is sent to the server.
 */
data class MentionQuery(val start: Int, val end: Int, val query: String)

object MentionQueryDetector {

    private val MENTION_PATTERN = Regex("@+\\S*")

    /**
     * Returns the mention token the cursor touches, or null when the cursor is outside of any.
     * A cursor right before the `@` or right after the last character still counts as inside.
     */
    fun find(text: CharSequence, cursor: Int): MentionQuery? {
        if (text.isEmpty() || cursor < 0) {
            return null
        }
        return MENTION_PATTERN.findAll(text)
            .firstOrNull { cursor >= it.range.first && cursor <= it.range.last + 1 }
            ?.let { MentionQuery(it.range.first, it.range.last + 1, it.value.substring(1)) }
    }
}
