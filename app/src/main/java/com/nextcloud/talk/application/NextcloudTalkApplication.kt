/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.application

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.emoji.bundled.BundledEmojiCompatConfig
import androidx.emoji.text.EmojiCompat

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.LifecycleObserver
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import autodagger.AutoComponent
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.facebook.cache.disk.DiskCacheConfig
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.R
import com.nextcloud.talk.components.filebrowser.webdav.DavUtils
import com.nextcloud.talk.dagger.modules.BusModule
import com.nextcloud.talk.dagger.modules.ContextModule
import com.nextcloud.talk.dagger.modules.DatabaseModule
import com.nextcloud.talk.dagger.modules.RestModule
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.jobs.CapabilitiesWorker
import com.nextcloud.talk.jobs.PushRegistrationWorker
import com.nextcloud.talk.jobs.SignalingSettingsWorker
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.utils.*
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageModule
import com.nextcloud.talk.utils.database.user.UserModule
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.preferences.BaseUrlPreferences
import com.nextcloud.talk.webrtc.MagicWebRTCUtils
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.googlecompat.GoogleCompatEmojiProvider

import de.cotech.hw.SecurityKeyManager
import de.cotech.hw.SecurityKeyManagerConfig
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt
import org.webrtc.PeerConnectionFactory
import org.webrtc.voiceengine.WebRtcAudioManager
import org.webrtc.voiceengine.WebRtcAudioUtils

import javax.inject.Inject
import javax.inject.Singleton
import java.security.Security
import java.util.concurrent.TimeUnit
import com.nextcloud.talk.utils.database.user.UserUtils as UserUtils1

@AutoComponent(modules = [BusModule::class, ContextModule::class, DatabaseModule::class, RestModule::class, UserModule::class, ArbitraryStorageModule::class])
@Singleton
@AutoInjector(NextcloudTalkApplication::class)
class NextcloudTalkApplication : MultiDexApplication(), LifecycleObserver {
    //region Fields (components)
    lateinit var componentApplication: NextcloudTalkApplicationComponent
        private set
    //endregion

    //region Getters

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var baseUrlPreferences: BaseUrlPreferences

    @Inject
    lateinit var okHttpClient: OkHttpClient
    //endregion



    //region private methods
    private fun initializeWebRtc() {
        try {
            if (MagicWebRTCUtils.HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
                WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true)
            }

            if (!MagicWebRTCUtils.OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
                WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true)
            }


            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this)
                    .setEnableVideoHwAcceleration(MagicWebRTCUtils.shouldEnableVideoHardwareAcceleration())
                    .createInitializationOptions())
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, e)
        }

    }

    //endregion

    //region Overridden methods
    override fun onCreate() {
        sharedApplication = this

        val securityKeyManager = SecurityKeyManager.getInstance()
        val securityKeyConfig = SecurityKeyManagerConfig.Builder()
                .setEnableDebugLogging(BuildConfig.DEBUG)
                .build()
        securityKeyManager.init(this, securityKeyConfig)

        initializeWebRtc()
        DisplayUtils.useCompatVectorIfNeeded()
        buildComponent()
        DavUtils.registerCustomFactories()

        componentApplication.inject(this)

        setAppTheme(appPreferences.theme)
        super.onCreate()

        val imagePipelineConfig = ImagePipelineConfig.newBuilder(this)
                .setNetworkFetcher(OkHttpNetworkFetcherWithCache(okHttpClient))
                .setMainDiskCacheConfig(DiskCacheConfig.newBuilder(this)
                        .setMaxCacheSize(0)
                        .setMaxCacheSizeOnLowDiskSpace(0)
                        .setMaxCacheSizeOnVeryLowDiskSpace(0)
                        .build())
                .build()

        Fresco.initialize(this, imagePipelineConfig)
        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        ClosedInterfaceImpl().providerInstallerInstallIfNeededAsync()
        DeviceUtils.ignoreSpecialBatteryFeatures()

        val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java).build()
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
        val periodicCapabilitiesUpdateWork = PeriodicWorkRequest.Builder(CapabilitiesWorker::class.java,
                12, TimeUnit.HOURS).build()
        val capabilitiesUpdateWork = OneTimeWorkRequest.Builder(CapabilitiesWorker::class.java).build()
        val signalingSettingsWork = OneTimeWorkRequest.Builder(SignalingSettingsWorker::class.java).build()

        WorkManager.getInstance().enqueue(pushRegistrationWork)
        WorkManager.getInstance().enqueue(accountRemovalWork)
        WorkManager.getInstance().enqueue(capabilitiesUpdateWork)
        WorkManager.getInstance().enqueue(signalingSettingsWork)
        WorkManager.getInstance().enqueueUniquePeriodicWork("DailyCapabilitiesUpdateWork", ExistingPeriodicWorkPolicy.REPLACE, periodicCapabilitiesUpdateWork)

        val config = BundledEmojiCompatConfig(this)
        config.setReplaceAll(true)
        val emojiCompat = EmojiCompat.init(config)

        EmojiManager.install(GoogleCompatEmojiProvider(emojiCompat))
    }

    override fun onTerminate() {
        super.onTerminate()
        sharedApplication = null
    }
    //endregion


    //region Protected methods
    protected fun buildComponent() {
        componentApplication = DaggerNextcloudTalkApplicationComponent.builder()
                .busModule(BusModule())
                .contextModule(ContextModule(applicationContext))
                .databaseModule(DatabaseModule())
                .restModule(RestModule(applicationContext))
                .userModule(UserModule())
                .arbitraryStorageModule(ArbitraryStorageModule())
                .build()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    @Inject
    lateinit var userUtils: com.nextcloud.talk.utils.database.user.UserUtils;
    //region Setters
    fun getServerURL() : String? {

        var serverURL: String? =baseUrlPreferences.baseUrl
        if(serverURL==null||serverURL .equals(""))
        {
            serverURL= sharedApplication?.getString(R.string.nc_server_url_testing);
        }
        return serverURL;
    }

    //region Setters
    fun setServerURL(serverURL: String)
    {
        var baseURL:String = "";
        baseURL= sharedApplication?.getString(R.string.nc_server_url_testing)!!;
        when (serverURL) {
            sharedApplication?.getString(R.string.nc_Production) ->baseURL= sharedApplication?.getString(R.string.nc_server_url_Production)!!;
            sharedApplication?.getString(R.string.nc_testing) -> baseURL= sharedApplication?.getString(R.string.nc_server_url_testing)!!;
            sharedApplication?.getString(R.string.nc_staging)  ->baseURL= sharedApplication?.getString(R.string.nc_server_url_staging)!!;
                // will be "follow_system" only for now
        }

        baseUrlPreferences.baseUrl=baseURL;
    }



    companion object {
        private val TAG = NextcloudTalkApplication::class.java.simpleName
        //region Singleton
        //endregion
        lateinit var userUtils1: com.nextcloud.talk.utils.database.user.UserUtils;
        var sharedApplication: NextcloudTalkApplication? = null
            protected set;
        //endregion


        //region Setters
        fun setAppTheme(theme: String) {
            /*when (theme) {
                "night_no" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "night_yes" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "battery_saver" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                else ->
                    // will be "follow_system" only for now
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }*/

                    // will be "follow_system" only for now
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        }

        fun setAppServerURL(serverURL: String) {
            when (serverURL) {
                sharedApplication?.getString(R.string.nc_Production) -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                sharedApplication?.getString(R.string.nc_testing) -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                sharedApplication?.getString(R.string.nc_staging)  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                else ->
                    // will be "follow_system" only for now
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }

            // will be "follow_system" only for now

        }

    }
    //endregion
}
