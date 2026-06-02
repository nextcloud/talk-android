/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui

sealed class ChatEmptyStateType {
    /** Waiting in lobby before a meeting starts. [text] is assembled by the caller and may include a timer. */
    data class Lobby(val text: String) : ChatEmptyStateType()

    /** Device is offline and no messages are cached locally. */
    data object Offline : ChatEmptyStateType()

    /** Conversation exists but has no messages yet. */
    data object NoMessages : ChatEmptyStateType()
}
