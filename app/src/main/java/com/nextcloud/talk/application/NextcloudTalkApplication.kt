/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * @author Mario Danic
 * @author Tim Krüger
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.application

import android.content.Context
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import androidx.lifecycle.LifecycleObserver
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import autodagger.AutoComponent
import autodagger.AutoInjector
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.memory.MemoryCache
import coil.util.DebugLogger
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.components.filebrowser.webdav.DavUtils
import com.nextcloud.talk.dagger.modules.BusModule
import com.nextcloud.talk.dagger.modules.ContextModule
import com.nextcloud.talk.dagger.modules.DatabaseModule
import com.nextcloud.talk.dagger.modules.RepositoryModule
import com.nextcloud.talk.dagger.modules.RestModule
import com.nextcloud.talk.dagger.modules.UtilsModule
import com.nextcloud.talk.dagger.modules.ViewModelModule
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.jobs.CapabilitiesWorker
import com.nextcloud.talk.jobs.SignalingSettingsWorker
import com.nextcloud.talk.jobs.WebsocketConnectionsWorker
import com.nextcloud.talk.ui.theme.ThemeModule
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.DeviceUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageModule
import com.nextcloud.talk.utils.database.user.UserModule
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.webrtc.MagicWebRTCUtils
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.google.GoogleEmojiProvider
import de.cotech.hw.SecurityKeyManager
import de.cotech.hw.SecurityKeyManagerConfig
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SQLiteDatabaseHook
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt
import org.webrtc.PeerConnectionFactory
import org.webrtc.voiceengine.WebRtcAudioManager
import org.webrtc.voiceengine.WebRtcAudioUtils
import java.security.Security
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@AutoComponent(
    modules = [
        BusModule::class,
        ContextModule::class,
        DatabaseModule::class,
        RestModule::class,
        UserModule::class,
        ArbitraryStorageModule::class,
        ViewModelModule::class,
        RepositoryModule::class,
        UtilsModule::class,
        ThemeModule::class
    ]
)
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
    lateinit var okHttpClient: OkHttpClient
    //endregion

    val hook: SQLiteDatabaseHook = object : SQLiteDatabaseHook {
        override fun preKey(database: SQLiteDatabase) {
            // unused atm
        }

        override fun postKey(database: SQLiteDatabase) {
            Log.i("TalkApplication", "DB cipher_migrate START")
            database.rawExecSQL("PRAGMA cipher_migrate;")
            Log.i("TalkApplication", "DB cipher_migrate END")
        }
    }

    //region private methods
    private fun initializeWebRtc() {
        try {
            if (MagicWebRTCUtils.HARDWARE_AEC_EXCLUDE_SET.contains(Build.MODEL)) {
                WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true)
            }

            if (!MagicWebRTCUtils.OPEN_SL_ES_INCLUDE_SET.contains(Build.MODEL)) {
                WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true)
            }

            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(this)
                    .createInitializationOptions()
            )
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, e)
        }
    }

    //endregion

    //region Overridden methods
    override fun onCreate() {
        Log.d(TAG, "onCreate")
        sharedApplication = this

        val securityKeyManager = SecurityKeyManager.getInstance()
        val securityKeyConfig = SecurityKeyManagerConfig.Builder()
            .setEnableDebugLogging(BuildConfig.DEBUG)
            .build()
        securityKeyManager.init(this, securityKeyConfig)

        initializeWebRtc()
        buildComponent()
        DavUtils.registerCustomFactories()

        componentApplication.inject(this)

        Coil.setImageLoader(buildDefaultImageLoader())
        setAppTheme(appPreferences.theme)
        super.onCreate()

        Security.insertProviderAt(Conscrypt.newProvider(), 1)

        ClosedInterfaceImpl().providerInstallerInstallIfNeededAsync()
        DeviceUtils.ignoreSpecialBatteryFeatures()

        initWorkers()

        val config = BundledEmojiCompatConfig(this)
        config.setReplaceAll(true)
        val emojiCompat = EmojiCompat.init(config)

        EmojiManager.install(GoogleEmojiProvider())

        NotificationUtils.registerNotificationChannels(applicationContext, appPreferences)
    }

    private fun initWorkers() {
        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        val capabilitiesUpdateWork = OneTimeWorkRequest.Builder(CapabilitiesWorker::class.java)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        val signalingSettingsWork = OneTimeWorkRequest.Builder(SignalingSettingsWorker::class.java)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        val websocketConnectionsWorker = OneTimeWorkRequest.Builder(WebsocketConnectionsWorker::class.java)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(applicationContext)
            .beginWith(accountRemovalWork)
            .then(capabilitiesUpdateWork)
            .then(signalingSettingsWork)
            .then(websocketConnectionsWorker)
            .enqueue()

        val periodicCapabilitiesUpdateWork = PeriodicWorkRequest.Builder(
            CapabilitiesWorker::class.java,
            HALF_DAY,
            TimeUnit.HOURS
        )
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "DailyCapabilitiesUpdateWork",
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicCapabilitiesUpdateWork
        )
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
            .arbitraryStorageModule(ArbitraryStorageModule())
            .build()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    private fun buildDefaultImageLoader(): ImageLoader {
        val imageLoaderBuilder = ImageLoader.Builder(applicationContext)
            .memoryCache {
                // Use 50% of the application's available memory.
                MemoryCache.Builder(applicationContext).maxSizePercent(FIFTY_PERCENT).build()
            }
            .crossfade(true) // Show a short crossfade when loading images from network or disk into an ImageView.
            .components {
                if (SDK_INT >= P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
            }

        if (BuildConfig.DEBUG) {
            imageLoaderBuilder.logger(DebugLogger())
        }

        imageLoaderBuilder.okHttpClient(okHttpClient)

        return imageLoaderBuilder.build()
    }

    companion object {
        private val TAG = NextcloudTalkApplication::class.java.simpleName
        const val FIFTY_PERCENT = 0.5
        const val HALF_DAY: Long = 12
        const val CIPHER_V4_MIGRATION: Int = 7
        //region Singleton
        //endregion

        var sharedApplication: NextcloudTalkApplication? = null
            protected set
        //endregion

        //region Setters
        fun setAppTheme(theme: String) {
            when (theme) {
                "night_no" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "night_yes" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                "battery_saver" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
                else ->
                    // will be "follow_system" only for now
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }
    //endregion
}
