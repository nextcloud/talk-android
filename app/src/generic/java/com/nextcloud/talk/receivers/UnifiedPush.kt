/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.receivers

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.jobs.NotificationWorker
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.greenrobot.eventbus.EventBus
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.PREF_MASTER
import org.unifiedpush.android.connector.PREF_MASTER_NO_DISTRIB_DIALOG_ACK
import org.unifiedpush.android.connector.UnifiedPush

class UnifiedPush : MessagingReceiver() {
    private val TAG: String? = UnifiedPush::class.java.simpleName

    companion object {
        fun getNumberOfDistributorsAvailable(context: Context) : Int {
            return UnifiedPush.getDistributors(context).size
        }

        private fun resetSeenNoDistributorsInfo(context: Context) {
            context.getSharedPreferences(PREF_MASTER, Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_MASTER_NO_DISTRIB_DIALOG_ACK, false).apply()
        }

        fun registerForPushMessaging(context: Context, accountName: String): Boolean {
            // if a distributor is registered and available, re-register to ensure in sync
            if (UnifiedPush.getSavedDistributor(context) !== null) {
                UnifiedPush.registerApp(context, accountName)
                return false
            }

            val distributors = UnifiedPush.getDistributors(context)

            // if no distributors available
            if (distributors.isEmpty()) {
                // if user has already been shown the info dialog, return
                val preferences = context.getSharedPreferences(PREF_MASTER, Context.MODE_PRIVATE)
                if (preferences.getBoolean(PREF_MASTER_NO_DISTRIB_DIALOG_ACK, false) == true) {
                    return false
                }

                // show user some info about unified push
                val message = TextView(context)
                val s = SpannableString(context.getString(R.string.unified_push_no_distributors_dialog_text))
                Linkify.addLinks(s, Linkify.WEB_URLS)
                message.text = s
                message.movementMethod = LinkMovementMethod.getInstance()
                message.setPadding(32, 32, 32, 32)
                AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.unified_push_no_distributors_dialog_title))
                    .setView(message)
                    .setPositiveButton(context.getString(R.string.nc_ok)) {
                        _, _ -> preferences.edit().putBoolean(PREF_MASTER_NO_DISTRIB_DIALOG_ACK, true).apply()
                    }.setOnDismissListener {
                        // send message to main activity that it can move on to it's next default activity
                        EventBus.getDefault().post(MainActivity.ProceedToConversationsListMessageEvent())
                    }.show()

                return true     // have a dialog to show, need main activity to wait
            }

            // 1 distributor available
            if (distributors.size == 1) {
                UnifiedPush.saveDistributor(context, distributors.first())
                UnifiedPush.registerApp(context, accountName)
                return false
            }

            // multiple distributors available, show dialog for user to choose
            val distributorsArray = distributors.toTypedArray()
            val distributorsNameArray = distributorsArray.map {
                try {
                    val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getApplicationInfo(
                            it,
                            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                        )
                    } else {
                        context.packageManager.getApplicationInfo(it, 0)
                    }
                    context.packageManager.getApplicationLabel(ai)
                } catch (e: PackageManager.NameNotFoundException) {
                    it
                } as String
            }.toTypedArray()

            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.unified_push_choose_distributor_title))
                .setItems(distributorsNameArray) { _, which ->
                    val distributor = distributorsArray[which]
                    UnifiedPush.saveDistributor(context, distributor)
                    UnifiedPush.registerApp(context, accountName)
                }.setOnDismissListener {
                    // send message to main activity that it can move on to it's next default activity
                    EventBus.getDefault().post(MainActivity.ProceedToConversationsListMessageEvent())
                }.show()

            return true      // have a dialog to show, need main activity to wait
        }

        fun unregisterForPushMessaging(context: Context, accountName: String) {
            // try and unregister with unified push distributor
            UnifiedPush.unregisterApp(context, instance = accountName)
            resetSeenNoDistributorsInfo(context)
        }
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        Log.d(TAG, "UP onMessage")

        val messageString = message.toString(Charsets.UTF_8)

        if (messageString.isNotEmpty() && instance.isNotEmpty()) {
            val messageData = Data.Builder()
                .putString(BundleKeys.KEY_NOTIFICATION_SUBJECT, messageString)
                .putString(BundleKeys.KEY_NOTIFICATION_SIGNATURE, instance)
                .putInt(
                    BundleKeys.KEY_NOTIFICATION_BACKEND_TYPE, NotificationWorker.Companion.BackendType.UNIFIED_PUSH.value)
                .build()
            val notificationWork =
                OneTimeWorkRequest.Builder(NotificationWorker::class.java).setInputData(messageData)
                    .build()
            WorkManager.getInstance().enqueue(notificationWork)
        }
    }

    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        // called when a new endpoint is to be used for sending push messages
        // do nothing
    }

    override fun onRegistrationFailed(context: Context, instance: String) {
        // called when the registration is not possible, eg. no network
        // just dump the registration to make sure it is cleaned up. re-register will be auto-reattempted
        unregisterForPushMessaging(context, instance)

    }

    override fun onUnregistered(context: Context, instance: String) {
        // called when this application is remotely unregistered from receiving push messages
        unregisterForPushMessaging(context, instance)
    }
}
