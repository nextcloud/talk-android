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
import java.util.UUID
import javax.inject.Inject

/**
 * Hands a conversation action to [ConversationActionWorker], and lets a caller that is still on screen
 * wait for what became of it, so it can take its change back once the worker has given up on it.
 *
 * Handing over and waiting are separate: the change has to be handed over even when the caller is gone
 * a moment later, while waiting only matters as long as it is there.
 *
 * Keeping WorkManager behind this seam is what lets a caller be tested without one.
 */
class ConversationActionScheduler @Inject constructor(private val context: Context) {

    /** Hands the change over right away and returns the id of the work that carries it. */
    fun enqueue(
        userId: Long,
        roomToken: String,
        action: ConversationAction,
        enabled: Boolean,
        lastReadMessage: Int? = null
    ): UUID =
        ConversationActionWorker.enqueue(
            context = context,
            userId = userId,
            roomToken = roomToken,
            action = action,
            enabled = enabled,
            lastReadMessage = lastReadMessage
        )

    /** Whether the change finally went through; false once the worker has given up on it. */
    suspend fun awaitOutcome(workId: UUID): Boolean {
        val outcome = WorkManager.getInstance(context).getWorkInfoByIdFlow(workId)
            .filterNotNull()
            .first { it.state.isFinished }

        return outcome.state != WorkInfo.State.FAILED
    }
}
