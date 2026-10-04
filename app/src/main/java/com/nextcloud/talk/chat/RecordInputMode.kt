/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat

/**
 * What the record button of the message input records while it is held.
 */
enum class RecordInputMode {
    VOICE,
    VIDEO;

    fun toggled(): RecordInputMode = if (this == VOICE) VIDEO else VOICE

    companion object {
        fun fromVideoFlag(isVideo: Boolean): RecordInputMode = if (isVideo) VIDEO else VOICE
    }
}
