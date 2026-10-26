/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.utils.bundle.BundleKeys

/**
 * Opens the conversation of the audio message at the message, switching to its account if needed. A message of
 * the thread [threadId] is opened in that thread.
 */
fun ChatAudioKey.openMessageIntent(context: Context, threadId: Long?): Intent =
    Intent(context, MainActivity::class.java).apply {
        putExtra(BundleKeys.KEY_INTERNAL_USER_ID, internalUserId)
        putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
        putExtras(openMessageExtras(threadId))
    }

/** The extras that make the chat jump to the audio message, in the thread [threadId] if it belongs to one. */
fun ChatAudioKey.openMessageExtras(threadId: Long?): Bundle =
    Bundle().apply {
        putString(BundleKeys.KEY_MESSAGE_ID, messageId.toString())
        threadId?.let { putLong(BundleKeys.KEY_THREAD_ID, it) }
    }
