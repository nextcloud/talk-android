/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.security.ProviderInstaller
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.interfaces.ClosedInterface
import com.nextcloud.talk.jobs.GetFirebasePushTokenWorker
import java.util.concurrent.TimeUnit

@AutoInjector(NextcloudTalkApplication::class)
class ClosedInterfaceImpl :
    ClosedInterface,
    ProviderInstaller.ProviderInstallListener {

    override val isGooglePlayServicesAvailable: Boolean = isGPlayServicesAvailable()

    override fun providerInstallerInstallIfNeededAsync() {
        NextcloudTalkApplication.sharedApplication?.let {
            ProviderInstaller.installIfNeededAsync(
                it.applicationContext,
                this
            )
        }
    }

    override fun onProviderInstalled() {
        // unused atm
    }

    override fun onProviderInstallFailed(p0: Int, p1: Intent?) {
        // unused atm
    }

    private fun isGPlayServicesAvailable(): Boolean {
        val api = GoogleApiAvailability.getInstance()
        val code =
            NextcloudTalkApplication.sharedApplication?.let {
                api.isGooglePlayServicesAvailable(it.applicationContext)
            }
        return if (code == ConnectionResult.SUCCESS) {
            true
        } else {
            Log.w(TAG, "GooglePlayServices are not available. Code:$code")
            false
        }
    }

    override fun setUpPushTokenRegistration() {
        val firebasePushTokenWorker = OneTimeWorkRequest.Builder(GetFirebasePushTokenWorker::class.java).build()
        WorkManager.getInstance().enqueue(firebasePushTokenWorker)

        setUpPeriodicTokenRefreshFromFCM()
    }

    private fun setUpPeriodicTokenRefreshFromFCM() {
        val periodicTokenRefreshFromFCM = PeriodicWorkRequest.Builder(
            GetFirebasePushTokenWorker::class.java,
            DAILY,
            TimeUnit.HOURS,
            FLEX_INTERVAL,
            TimeUnit.HOURS
        ).build()

        WorkManager.getInstance()
            .enqueueUniquePeriodicWork(
                "periodicTokenRefreshFromFCM",
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicTokenRefreshFromFCM
            )
    }

    companion object {
        private val TAG = ClosedInterfaceImpl::class.java.simpleName
        const val DAILY: Long = 24
        const val FLEX_INTERVAL: Long = 10
    }
}
