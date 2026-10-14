/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads the duration of a voice message file off the calling thread. Must be used from the main thread only.
 */
class VoiceMessageDurationLoader(
    private val ioDispatcher: CoroutineDispatcher,
    private val readDurationSeconds: (String) -> Int
) {
    private val loadingMessageIds = mutableSetOf<Int>()

    private suspend fun read(file: File): Int = withContext(ioDispatcher) { readDurationSeconds(file.absolutePath) }

    /** Returns null if the same message is already being read, so a repeated tap does not prepare it twice. */
    suspend fun readOnce(messageId: Int, file: File): Int? {
        if (!loadingMessageIds.add(messageId)) return null
        return try {
            read(file)
        } finally {
            loadingMessageIds.remove(messageId)
        }
    }
}
