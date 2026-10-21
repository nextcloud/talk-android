/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.nextcloud.talk.jobs.ConversationActionWorker.ConversationAction
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Hands a conversation action to [ConversationActionWorker] and waits for what became of it, so a
 * caller that is still on screen can take its change back once the worker has given up on it.
 *
 * Keeping WorkManager behind this seam is what lets a caller be tested without one.
 */
class ConversationActionScheduler @Inject constructor(private val context: Context) {

    /** Whether the change finally went through; false once the worker has given up on it. */
    suspend fun send(
        userId: Long,
        roomToken: String,
        action: ConversationAction,
        enabled: Boolean,
        lastReadMessage: Int? = null
    ): Boolean {
        val workId = ConversationActionWorker.enqueue(
            context = context,
            userId = userId,
            roomToken = roomToken,
            action = action,
            enabled = enabled,
            lastReadMessage = lastReadMessage
        )

        val outcome = WorkManager.getInstance(context).getWorkInfoByIdFlow(workId)
            .filterNotNull()
            .first { it.state.isFinished }

        return outcome.state != WorkInfo.State.FAILED
    }
}
