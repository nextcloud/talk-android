/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.content.IntentCompat
import androidx.core.net.toUri

/** Opens the system ringtone picker. */
class PickSoundContract : ActivityResultContract<PickSoundContract.Request, PickSoundContract.Result?>() {

    /**
     * @param existingUri the sound selected in the picker, null for no sound
     */
    data class Request(val sound: NotificationSound, val title: String, val existingUri: String?)

    /** @param uri the picked sound, null for no sound */
    data class Result(val uri: String?)

    override fun createIntent(context: Context, input: Request): Intent {
        val (ringtoneType, systemDefaultUri) = when (input.sound) {
            NotificationSound.CALL -> RingtoneManager.TYPE_RINGTONE to Settings.System.DEFAULT_RINGTONE_URI
            NotificationSound.MESSAGE -> RingtoneManager.TYPE_NOTIFICATION to Settings.System.DEFAULT_NOTIFICATION_URI
        }
        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, ringtoneType)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, input.title)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, systemDefaultUri)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, input.existingUri?.toUri())
    }

    /** @return null if nothing was picked; a missing extra must not be treated as "no sound" */
    override fun parseResult(resultCode: Int, intent: Intent?): Result? {
        if (resultCode != Activity.RESULT_OK || intent?.hasExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) != true) {
            return null
        }
        val pickedUri = IntentCompat.getParcelableExtra(
            intent,
            RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
            Uri::class.java
        )
        return Result(pickedUri?.toString())
    }
}
