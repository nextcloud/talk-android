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
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.utils.bundle.BundleKeys

/** Opens the chat of the message at the message, in its thread if it has one, from a screen of the app. */
fun ChatAudioKey.openMessageIntent(context: Context, threadId: Long?): Intent =
    ChatActivity.createIntent(context, internalUserId, roomToken, openMessageExtras(threadId))

/**
 * Like [openMessageIntent], but from outside the screens of the app, e.g. the media notification: as a new back stack
 * in the task of the app, see [ConversationsListActivity.createChatBackStack].
 */
fun ChatAudioKey.openMessageBackStack(context: Context, threadId: Long?): Array<Intent> =
    ConversationsListActivity.createChatBackStack(context, internalUserId, roomToken, openMessageExtras(threadId))

private fun ChatAudioKey.openMessageExtras(threadId: Long?): Bundle =
    Bundle().apply {
        putString(BundleKeys.KEY_MESSAGE_ID, messageId.toString())
        threadId?.let { putLong(BundleKeys.KEY_THREAD_ID, it) }
    }
