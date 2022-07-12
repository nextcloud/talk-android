/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * @author Jindrich Kolman
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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
import com.nextcloud.talk.utils.unifiedpush.ProviderInstaller
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.interfaces.ClosedInterface
import android.util.Log

@AutoInjector(NextcloudTalkApplication::class)
class ClosedInterfaceImpl : ClosedInterface, ProviderInstaller.ProviderInstallListener {

    override val isGooglePlayServicesAvailable: Boolean = isUnifiedPushAvailable()

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

    private fun isUnifiedPushAvailable(): Boolean {
        val unifiedPushAvailable =
            NextcloudTalkApplication.sharedApplication?.let {
                ProviderInstaller.isUnifiedPushAvailable(
                    it.applicationContext
                )
            }
        return unifiedPushAvailable == true
    }

    override fun setUpPushTokenRegistration() {
        android.util.Log.d(TAG, "setUpPushTokenRegistration")
    }

    companion object {
        const val TAG = "ClosedInterfaceImpl"
    }
}
