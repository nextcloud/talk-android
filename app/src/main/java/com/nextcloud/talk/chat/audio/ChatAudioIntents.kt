/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import android.content.Context
import android.content.Intent
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.utils.bundle.BundleKeys

/** Opens the conversation of the audio message at the message, switching to its account if needed. */
fun ChatAudioKey.openMessageIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        putExtra(BundleKeys.KEY_INTERNAL_USER_ID, internalUserId)
        putExtra(BundleKeys.KEY_ROOM_TOKEN, roomToken)
        putExtra(BundleKeys.KEY_MESSAGE_ID, messageId.toString())
    }
