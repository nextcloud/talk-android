/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.services

import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.nextcloud.talk.jobs.NotificationWorker
import com.nextcloud.talk.jobs.PushRegistrationWorker
import com.nextcloud.talk.utils.UnifiedPushUtils.toByteArray
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.json.JSONException
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class UnifiedPushService: PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Log.d(TAG, "New endpoint for $instance")
        val endpointBA = endpoint.toByteArray() ?: run {
            Log.w(TAG, "Couldn't serialize endpoint!")
            return
        }
        val data = Data.Builder()
            .putString(PushRegistrationWorker.ORIGIN, "UnifiedPushService#onNewEndpoint")
            .putLong(PushRegistrationWorker.USER_ID, instance.toLong())
            .putByteArray(PushRegistrationWorker.UNIFIEDPUSH_ENDPOINT, endpointBA)
            .build()
        val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java)
            .setInputData(data)
            .build()
        WorkManager.getInstance(this).enqueue(pushRegistrationWork)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        Log.d(TAG, "New message for $instance")
        try {
            val mObj = JSONObject(message.content.toString(Charsets.UTF_8))
            val token = mObj.getString("activationToken")
            onActivationToken(token, instance)
        } catch (_: JSONException) {
            // Messages are encrypted following RFC8291, and UnifiedPush lib handle the decryption itself:
            // message.content is the cleartext
            val messageData = Data.Builder()
                .putLong(BundleKeys.KEY_NOTIFICATION_USER_ID, instance.toLong())
                .putString(BundleKeys.KEY_NOTIFICATION_CLEARTEXT_SUBJECT, message.content.toString(Charsets.UTF_8))
                .build()
            val notificationWork =
                OneTimeWorkRequest.Builder(NotificationWorker::class.java).setInputData(messageData)
                    .build()
            WorkManager.getInstance(this).enqueue(notificationWork)
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w(TAG, "Registration failed for $instance, reason=$reason")
        // Do nothing, we let the periodic worker try to re-register later
    }

    override fun onUnregistered(instance: String) {
        Log.d(TAG, "$instance unregistered")
    }

    private fun onActivationToken(activationToken: String, instance: String) {
        val data = Data.Builder()
            .putString(PushRegistrationWorker.ORIGIN, "UnifiedPushService#onActivationToken")
            .putLong(PushRegistrationWorker.USER_ID, instance.toLong())
            .putString(PushRegistrationWorker.ACTIVATION_TOKEN, activationToken)
            .build()
        val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java)
            .setInputData(data)
            .build()
        WorkManager.getInstance(this).enqueue(pushRegistrationWork)
    }

    companion object {
        const val TAG = "UnifiedPushService"
    }
}
