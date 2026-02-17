/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.app.Activity
import android.content.Context
import android.os.Parcel
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.jobs.PushRegistrationWorker
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.ResolvedDistributor
import java.util.concurrent.TimeUnit

object UnifiedPushUtils {
    private val TAG: String = UnifiedPushUtils::class.java.getSimpleName()
    const val DAILY: Long = 24
    const val FLEX_INTERVAL: Long = 10

    /**
     * Use default distributor, register all accounts that support webpush
     *
     * Unregister proxy push for account if succeed
     * Re-register proxy push for the others
     *
     * @param activity: Context needs to be an activity, to get a result
     * @param userManager: Used to register all accounts
     * @param ncApi: API
     * @param callback: run with the push service name if available
     */
    @JvmStatic
    fun useDefaultDistributor(
        activity: Activity,
        callback: (String?) -> Unit
    ) {
        Log.d(TAG, "Using default UnifiedPush distributor")
        UnifiedPush.tryUseDefaultDistributor(activity) { res ->
            if (res) {
                enqueuePushWorker(activity, true, "useDefaultDistributor")
                callback(UnifiedPush.getSavedDistributor(activity))
            } else {
                callback(null)
            }
        }
    }

    /**
     * Does [useDefaultDistributor] show an OS screen to ask the user
     * to pick a distributor ?
     */
    @JvmStatic
    fun usingDefaultDistributorNeedsIntro(context: Context): Boolean =
        UnifiedPush.resolveDefaultDistributor(context) == ResolvedDistributor.ToSelect

    @JvmStatic
    fun registerWithCurrentDistributor(context: Context) {
        enqueuePushWorker(context, true, "registerWithCurrentDistributor")
    }

    /**
     * Pick another distributor, register all accounts that support webpush
     *
     * Unregister proxy push for account if succeed
     * Re-register proxy push for the others
     *
     * @param activity: Context needs to be an activity, to get a result
     * @param accountManager: Used to register all accounts
     * @param callback: run with the push service name if available
     */
    @JvmStatic
    fun pickDistributor(
        activity: Activity,
        callback: (String?) -> Unit
    ) {
        Log.d(TAG, "Picking another UnifiedPush distributor")
        UnifiedPush.tryPickDistributor(activity as Context) { res ->
            if (res) {
                enqueuePushWorker(activity, true, "pickDistributor")
                callback(UnifiedPush.getSavedDistributor(activity))
            } else {
                callback(null)
            }
        }
    }

    /**
     * Disable UnifiedPush and try to register with proxy push again
     */
    @JvmStatic
    fun disableExternalUnifiedPush(
        context: Context
    ) {
        enqueuePushWorker(context, false, "disableExternalUnifiedPush")
    }

    private fun enqueuePushWorker(context: Context, useUnifiedPush: Boolean, origin: String) {
        val data = Data.Builder()
            .putString(PushRegistrationWorker.ORIGIN, "UnifiedPushUtils#$origin")
            .putBoolean(PushRegistrationWorker.USE_UNIFIEDPUSH, useUnifiedPush)
            .build()
        val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(pushRegistrationWork)
    }

    /**
     * Call only if [com.nextcloud.talk.utils.preferences.AppPreferences.getUseUnifiedPush],
     * else [ClosedInterfaceImpl.setUpPushTokenRegistration] is called and does the same as
     * this function
     */
    fun setPeriodicPushRegistrationWorker(context: Context) {
        val data = Data.Builder()
            .putString(PushRegistrationWorker.ORIGIN, "UnifiedPushUtils#setPeriodicPushRegistrationWorker")
            .build()
        val periodicPushRegistrationWork = PeriodicWorkRequest.Builder(
            PushRegistrationWorker::class.java,
            DAILY,
            TimeUnit.HOURS,
            FLEX_INTERVAL,
            TimeUnit.HOURS
        )
            .setInputData(data)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "periodicPushRegistrationWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicPushRegistrationWork
            )
    }

    /**
     * Get UnifiedPush instance for user
     *
     * This is simply the [User.id] (long) in String, but it allows defining it in a single place
     */
    fun instanceFor(user: User): String = "${user.id}"

    fun PushEndpoint.toByteArray(): ByteArray? {
        val parcel = Parcel.obtain()
        return try {
            writeToParcel(parcel, 0)
            parcel.marshall()
        } catch (_: Exception) {
            null
        } finally {
            parcel.recycle()
        }
    }

    fun ByteArray.toPushEndpoint(): PushEndpoint? {
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(this, 0, size)
            parcel.setDataPosition(0) // Reset Parcel position to read from the start
            PushEndpoint.createFromParcel(parcel)
        } catch (_: Exception) {
            null
        } finally {
            parcel.recycle()
        }
    }
}
