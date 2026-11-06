/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.database.dao.ChatMessagesDao
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import java.util.UUID
import javax.inject.Inject
import kotlin.concurrent.thread

/**
 * Cancels an upload from its notification the same way the chat does: the worker aborts and the placeholder
 * message of the upload is removed.
 */
@AutoInjector(NextcloudTalkApplication::class)
class UploadCancelReceiver : BroadcastReceiver() {

    @Inject
    lateinit var chatDao: ChatMessagesDao

    @Suppress("TooGenericExceptionCaught")
    override fun onReceive(context: Context, intent: Intent) {
        val workId = intent.getStringExtra(KEY_WORK_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val referenceId = intent.getStringExtra(KEY_REFERENCE_ID).orEmpty()
        val internalConversationId = intent.getStringExtra(KEY_INTERNAL_CONVERSATION_ID).orEmpty()
        if (workId == null) {
            Log.w(TAG, "Cancel request without a valid work id")
            return
        }

        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        val pendingResult = goAsync()
        thread {
            try {
                UploadAndShareFilesWorker.cancelUpload(referenceId, workId)
                if (referenceId.isNotEmpty() && internalConversationId.isNotEmpty()) {
                    chatDao.deleteTempChatMessageIfPending(internalConversationId, referenceId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel upload", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val TAG = UploadCancelReceiver::class.simpleName
        const val KEY_WORK_ID = "KEY_UPLOAD_WORK_ID"
        const val KEY_REFERENCE_ID = "KEY_UPLOAD_REFERENCE_ID"
        const val KEY_INTERNAL_CONVERSATION_ID = "KEY_UPLOAD_INTERNAL_CONVERSATION_ID"
    }
}
