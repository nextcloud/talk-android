/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.utils

import android.content.Intent
import androidx.work.Data
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
import com.nextcloud.talk.jobs.PushRegistrationWorker
import java.util.concurrent.TimeUnit

@AutoInjector(NextcloudTalkApplication::class)
class ClosedInterfaceImpl : ClosedInterface, ProviderInstaller.ProviderInstallListener {

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
                api.isGooglePlayServicesAvailable(
                    it.applicationContext
                )
            }
        return code == ConnectionResult.SUCCESS
    }

    override fun setUpPushTokenRegistration() {
        registerLocalToken()
        setUpPeriodicLocalTokenRegistration()
        setUpPeriodicTokenRefreshFromFCM()
    }

    private fun registerLocalToken() {
        val data: Data = Data.Builder().putString(
            PushRegistrationWorker.ORIGIN,
            "ClosedInterfaceImpl#registerLocalToken"
        )
            .build()
        val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java)
            .setInputData(data)
            .build()
        WorkManager.getInstance().enqueue(pushRegistrationWork)
    }

    private fun setUpPeriodicLocalTokenRegistration() {
        val data: Data = Data.Builder().putString(
            PushRegistrationWorker.ORIGIN,
            "ClosedInterfaceImpl#setUpPeriodicLocalTokenRegistration"
        )
            .build()

        val periodicTokenRegistration = PeriodicWorkRequest.Builder(
            PushRegistrationWorker::class.java,
            DAILY,
            TimeUnit.HOURS,
            FLEX_INTERVAL,
            TimeUnit.HOURS
        )
            .setInputData(data)
            .build()

        WorkManager.getInstance()
            .enqueueUniquePeriodicWork(
                "periodicTokenRegistration", ExistingPeriodicWorkPolicy.REPLACE,
                periodicTokenRegistration
            )
    }

    private fun setUpPeriodicTokenRefreshFromFCM() {
        val periodicTokenRefreshFromFCM = PeriodicWorkRequest.Builder(
            GetFirebasePushTokenWorker::class.java,
            MONTHLY,
            TimeUnit.DAYS,
            FLEX_INTERVAL,
            TimeUnit.DAYS,
        )
            .build()

        WorkManager.getInstance()
            .enqueueUniquePeriodicWork(
                "periodicTokenRefreshFromFCM", ExistingPeriodicWorkPolicy.REPLACE,
                periodicTokenRefreshFromFCM
            )
    }

    companion object {
        const val DAILY: Long = 24
        const val MONTHLY: Long = 30
        const val FLEX_INTERVAL: Long = 10
    }
}
