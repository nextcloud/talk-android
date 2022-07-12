/*
 * Nextcloud Talk application
 *
 * @author Jindrich Kolman
 * Copyright (C) 2022 Jindrich Kolman <kolman.jindrich@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils.unifiedpush

import android.annotation.SuppressLint
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.util.Log
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.services.unifiedpush.ChatAndCallMessagingService
import org.unifiedpush.android.connector.EXTRA_MESSAGE
import org.unifiedpush.android.connector.ACTION_MESSAGE
import org.unifiedpush.android.connector.ACTION_NEW_ENDPOINT
import org.unifiedpush.android.connector.EXTRA_ENDPOINT
import org.unifiedpush.android.connector.MessagingReceiver
import javax.inject.Inject

@SuppressLint("LongLogTag")
open class UnifiedPushMessagingReceiver : MessagingReceiver() {
    @JvmField
    @Inject
    var context: Context = NextcloudTalkApplication.sharedApplication!!.applicationContext

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        val strMessage = String(message)
        Log.d(TAG, "New Message: $instance $strMessage")
        scheduleJob(
            PersistableBundle().apply {
                putString("action", ACTION_MESSAGE)
                putString(EXTRA_MESSAGE, strMessage)
            }
        )
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        scheduleJob(
            PersistableBundle().apply {
                putString("action", ACTION_NEW_ENDPOINT)
                putString(EXTRA_ENDPOINT, endpoint)
            }
        )
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        Log.d(TAG, "Registration Failed: $instance")
    }

    override fun onUnregistered(context: Context, instance: String) {
        Log.d(TAG, "$instance is unregistered")
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "event received $intent")
        super.onReceive(context, intent)
    }

    open fun scheduleJob(input: PersistableBundle) {
        Log.d(TAG, "ScheduleJob")
        val jobScheduler: JobScheduler = this.context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.schedule(
            JobInfo.Builder(
                0,
                ComponentName(this.context, ChatAndCallMessagingService::class.java)
            ).apply {
                setOverrideDeadline((120 * 1000).toLong()) // Maximum delay 120s
                setExtras(input)
            }.build()
        )
    }

    companion object {
        const val TAG = "UnifiedPushMessagingReceiver"
    }
}
