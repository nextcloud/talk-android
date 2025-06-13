/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.jobs.NotificationWorker
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.power.PowerManagerUtils
import org.greenrobot.eventbus.EventBus
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage
import org.unifiedpush.android.connector.ui.SelectDistributorDialogsBuilder
import org.unifiedpush.android.connector.ui.UnifiedPushFunctions

class UnifiedPush : PushService() {
    companion object {
        private val TAG: String? = UnifiedPush::class.java.simpleName

        private const val MESSAGE_RECEIVED_WAKE_LOCK_TIMEOUT = (60 * 1000L)

        fun getNumberOfDistributorsAvailable(context: Context) =
            UnifiedPush.getDistributors(context).size

        fun registerForPushMessaging(context: Context, accountName: String, forceChoose: Boolean): Boolean {
            var retVal = false

            object : SelectDistributorDialogsBuilder(
                context,
                object : UnifiedPushFunctions {
                    override fun tryUseDefaultDistributor(callback: (Boolean) -> Unit) =
                        UnifiedPush.tryUseDefaultDistributor(context, callback).also {
                            Log.d(TAG, "tryUseDefaultDistributor()")
                        }

                    override fun getAckDistributor(): String? =
                        UnifiedPush.getAckDistributor(context).also {
                            Log.d(TAG, "getAckDistributor() = $it")
                        }

                    override fun getDistributors(): List<String> =
                        UnifiedPush.getDistributors(context).also {
                            Log.d(TAG, "getDistributors() = $it")
                        }

                    override fun register(instance: String) =
                        UnifiedPush.register(context, instance).also {
                            Log.d(TAG, "register($instance)")
                        }

                    override fun saveDistributor(distributor: String) =
                        UnifiedPush.saveDistributor(context, distributor).also {
                            Log.d(TAG, "saveDistributor($distributor)")
                        }
                }
            ) {
                override fun onManyDistributorsFound(distributors: List<String>) =
                    Log.d(TAG, "onManyDistributorsFound($distributors)").run {
                        // true return indicates to calling activity that it should wait whilst dialog is shown
                        retVal = true
                        super.onManyDistributorsFound(distributors)
                    }

                override fun onDistributorSelected(distributor: String) =
                    super.onDistributorSelected(distributor).also {
                        // send message to main activity that it can move on after waiting
                        EventBus.getDefault().post(MainActivity.ProceedToConversationsListMessageEvent())
                    }
            }.apply {
                instances = listOf(accountName)
                mayUseCurrent = !forceChoose
                mayUseDefault = !forceChoose
            }.run()

            return retVal
        }

        fun unregisterForPushMessaging(context: Context, accountName: String) =
            // try and unregister with unified push distributor
            UnifiedPush.unregister(context, accountName).also {
                Log.d(TAG, "unregisterForPushMessaging($accountName)")
            }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        // wake lock to get the notification background job to execute more promptly since it will take eons to run
        // if phone is dozing. default client ring time is 45 seconds so it should be more than that
        PowerManagerUtils().acquireTimedPartialLock(MESSAGE_RECEIVED_WAKE_LOCK_TIMEOUT)

        Log.d(TAG, "onMessage()")

        val messageString = message.content.toString(Charsets.UTF_8)

        if (messageString.isNotEmpty() && instance.isNotEmpty()) {
            val messageData = Data.Builder()
                .putString(BundleKeys.KEY_NOTIFICATION_SUBJECT, messageString)
                .putString(BundleKeys.KEY_NOTIFICATION_SIGNATURE, instance)
                .putInt(
                    BundleKeys.KEY_NOTIFICATION_BACKEND_TYPE,
                    NotificationWorker.Companion.BackendType.UNIFIED_PUSH.value
                )
                .build()
            val notificationWork =
                OneTimeWorkRequest.Builder(NotificationWorker::class.java).setInputData(messageData)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()
            WorkManager.getInstance(this).enqueue(notificationWork)

            Log.d(TAG, "expedited NotificationWorker queued")
        }
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Log.d(TAG, "onNewEndpoint(${endpoint.url}, $instance)")
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) =
        // the registration is not possible, eg. no network
        // force unregister to make sure cleaned up. re-register will be re-attempted next time
        UnifiedPush.unregister(this, instance).also {
            Log.d(TAG, "onRegistrationFailed(${reason.name}, $instance)")
        }

    override fun onUnregistered(instance: String) =
        // this application is unregistered by the distributor from receiving push messages
        // force unregister to make sure cleaned up. re-register will be re-attempted next time
        UnifiedPush.unregister(this, instance).also {
            Log.d(TAG, "onUnregistered($instance)")
        }
}
