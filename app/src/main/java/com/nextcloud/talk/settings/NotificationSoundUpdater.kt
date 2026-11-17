/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import javax.inject.Inject

/** Changes the sound of a notification channel, which recreates the channel. */
class NotificationSoundUpdater @Inject constructor(
    private val context: Context,
    private val appPreferences: AppPreferences
) {
    /**
     * @param uri null for no sound
     * @return false if the sound could not be set
     */
    @WorkerThread
    fun update(sound: NotificationSound, uri: String?): Boolean =
        when (sound) {
            NotificationSound.CALL -> NotificationUtils.updateCallRingtone(context, appPreferences, uri?.toUri())
            NotificationSound.MESSAGE -> NotificationUtils.updateMessageRingtone(context, appPreferences, uri?.toUri())
        }
}
