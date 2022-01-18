package com.nextcloud.talk.utils

import android.content.Intent
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

    override val isGooglePlayServicesAvailable : Boolean = isGPlayServicesAvailable()

    override fun providerInstallerInstallIfNeededAsync() {
        NextcloudTalkApplication.sharedApplication?.let {
            ProviderInstaller.installIfNeededAsync(
                it.applicationContext,
                this
            )
        }
    }

    override fun onProviderInstalled() {
    }

    override fun onProviderInstallFailed(p0: Int, p1: Intent?) {
    }

    override fun setUpPushTokenRegistration() {
        val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java).build()
        WorkManager.getInstance().enqueue(pushRegistrationWork)

        val periodicPushRegistration = PeriodicWorkRequest.Builder(
            GetFirebasePushTokenWorker::class.java, 15,  // TODO: discuss intervall. joas 24h, google 1 month
            TimeUnit.MINUTES
        )
            .build()

        WorkManager.getInstance()
            .enqueueUniquePeriodicWork(
                "periodicPushRegistration", ExistingPeriodicWorkPolicy.REPLACE,
                periodicPushRegistration
            )
    }

    private fun isGPlayServicesAvailable() : Boolean {
        val api = GoogleApiAvailability.getInstance()
        val code =
            NextcloudTalkApplication.sharedApplication?.let {
                api.isGooglePlayServicesAvailable(
                    it.applicationContext
                )
            }
        return code == ConnectionResult.SUCCESS
    }
}