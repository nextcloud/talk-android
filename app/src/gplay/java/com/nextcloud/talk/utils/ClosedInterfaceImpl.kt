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

    override fun setUpPushTokenRegistration() {
        registerLocalToken()
        setUpPeriodicLocalTokenRegistration()
        setUpPeriodicTokenRefreshFromFCM()
    }

    private fun registerLocalToken(){
        val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java).build()
        WorkManager.getInstance().enqueue(pushRegistrationWork)
    }

    private fun setUpPeriodicLocalTokenRegistration () {
        val periodicTokenRegistration = PeriodicWorkRequest.Builder(
            PushRegistrationWorker::class.java, 1,
            TimeUnit.DAYS
        )
            .build()

        WorkManager.getInstance()
            .enqueueUniquePeriodicWork(
                "periodicTokenRegistration", ExistingPeriodicWorkPolicy.REPLACE,
                periodicTokenRegistration
            )
    }

    private fun setUpPeriodicTokenRefreshFromFCM () {
        val periodicTokenRefreshFromFCM = PeriodicWorkRequest.Builder(
            GetFirebasePushTokenWorker::class.java, 30,
            TimeUnit.DAYS
        )
            .build()

        WorkManager.getInstance()
            .enqueueUniquePeriodicWork(
                "periodicTokenRefreshFromFCM", ExistingPeriodicWorkPolicy.REPLACE,
                periodicTokenRefreshFromFCM
            )
    }
}