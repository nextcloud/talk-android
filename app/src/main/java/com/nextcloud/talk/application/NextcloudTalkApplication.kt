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

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.emoji.bundled.BundledEmojiCompatConfig
import androidx.emoji.text.EmojiCompat
import androidx.lifecycle.LifecycleObserver
import androidx.multidex.MultiDex
import androidx.work.*
import coil.Coil
import coil.ImageLoader
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.BuildConfig
import com.nextcloud.talk.components.filebrowser.webdav.DavUtils
import com.nextcloud.talk.jobs.AccountRemovalWorker
import com.nextcloud.talk.jobs.CapabilitiesWorker
import com.nextcloud.talk.jobs.SignalingSettingsWorker
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.push.PushConfiguration
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettings
import com.nextcloud.talk.newarch.di.module.CommunicationModule
import com.nextcloud.talk.newarch.di.module.NetworkModule
import com.nextcloud.talk.newarch.di.module.ServiceModule
import com.nextcloud.talk.newarch.di.module.StorageModule
import com.nextcloud.talk.newarch.domain.di.module.UseCasesModule
import com.nextcloud.talk.newarch.features.account.di.module.AccountModule
import com.nextcloud.talk.newarch.features.contactsflow.di.module.ContactsFlowModule
import com.nextcloud.talk.newarch.features.conversationslist.di.module.ConversationsListModule
import com.nextcloud.talk.newarch.local.dao.UsersDao
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.local.models.other.UserStatus.*
import com.nextcloud.talk.newarch.local.models.toUserEntity
import com.nextcloud.talk.newarch.services.shortcuts.ShortcutService
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.webrtc.MagicWebRTCUtils
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.googlecompat.GoogleCompatEmojiProvider
import de.cotech.hw.SecurityKeyManager
import de.cotech.hw.SecurityKeyManagerConfig
import io.requery.Persistable
import io.requery.reactivex.ReactiveEntityStore
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.conscrypt.Conscrypt
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.webrtc.PeerConnectionFactory
import org.webrtc.voiceengine.WebRtcAudioManager
import org.webrtc.voiceengine.WebRtcAudioUtils
import java.security.Security
import java.util.concurrent.TimeUnit

class NextcloudTalkApplication : Application(), LifecycleObserver, Configuration.Provider {
    //region Getters

    val userUtils: UserUtils by inject()
    val dataStore: ReactiveEntityStore<Persistable> by inject()

    val imageLoader: ImageLoader by inject()
    val appPreferences: AppPreferences by inject()
    val usersDao: UsersDao by inject()
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

            PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(this)
                            .setEnableVideoHwAcceleration(
                                    MagicWebRTCUtils.shouldEnableVideoHardwareAcceleration()
                            )
                            .createInitializationOptions()
            )
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
        startKoin()
        DavUtils.registerCustomFactories()
        Coil.setDefaultImageLoader(imageLoader)
        migrateUsers()

        setAppTheme(appPreferences.theme)
        super.onCreate()

        val shortcutService: ShortcutService = get()

        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        ClosedInterfaceImpl().providerInstallerInstallIfNeededAsync()

        val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java)
                .build()
        val periodicCapabilitiesUpdateWork = PeriodicWorkRequest.Builder(
                        CapabilitiesWorker::class.java,
                        12, TimeUnit.HOURS
                )
                .build()
        val signalingSettingsWork = OneTimeWorkRequest.Builder(SignalingSettingsWorker::class.java)
                .build()

        WorkManager.getInstance(this)
                .enqueue(accountRemovalWork)
        WorkManager.getInstance(this)
                .enqueue(signalingSettingsWork)
        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                        "DailyCapabilitiesUpdateWork", ExistingPeriodicWorkPolicy.REPLACE,
                        periodicCapabilitiesUpdateWork
                )

        val config = BundledEmojiCompatConfig(this@NextcloudTalkApplication)
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
    private fun startKoin() {
        startKoin {
            androidContext(this@NextcloudTalkApplication)
            androidLogger()
            modules(listOf(CommunicationModule, StorageModule, NetworkModule, ConversationsListModule, ServiceModule, AccountModule, UseCasesModule, ContactsFlowModule))
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

    private fun migrateUsers() {
        if (!appPreferences.migrationToRoomFinished) {
            GlobalScope.launch {
                val users: List<UserEntity> = userUtils.users as List<UserEntity>
                var newUser: User
                val newUsers = mutableListOf<User>()
                for (user in users) {
                    newUser = User(userId = user.userId, username = user.username, baseUrl = user.baseUrl, token = user.token, displayName = user.displayName)
                    try {
                        newUser.pushConfiguration =
                                LoganSquare.parse(user.pushConfigurationState, PushConfiguration::class.java)
                    } catch (e: Exception) {
                        // no push
                    }
                    if (user.capabilities != null) {
                        newUser.capabilities = LoganSquare.parse(user.capabilities, Capabilities::class.java)
                    }
                    newUser.clientCertificate = user.clientCertificate
                    try {
                        val external = LoganSquare.parse(user.externalSignalingServer, ExternalSignalingServer::class.java)
                        val signalingSettings = SignalingSettings()
                        signalingSettings.externalSignalingServer = external.externalSignalingServer
                        signalingSettings.externalSignalingTicket = external.externalSignalingTicket
                        newUser.signalingSettings = signalingSettings
                    } catch (e: Exception) {
                        // no external signaling
                    }
                    if (user.current) {
                        newUser.status = ACTIVE
                    } else {
                        if (user.scheduledForDeletion) {
                            newUser.status = PENDING_DELETE
                        } else {
                            newUser.status = DORMANT
                        }
                    }

                    newUsers.add(newUser)
                }

                val userEntities = newUsers.map {
                    it.toUserEntity()
                }

                usersDao.saveUsers(*userEntities.toTypedArray())
                dataStore.delete()
                appPreferences.migrationToRoomFinished = true
            }
        }
    }

    companion object {
        private val TAG = NextcloudTalkApplication::class.java.simpleName
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
                "battery_saver" -> AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                )
                else ->
                    // will be "follow_system" only for now
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    override fun getWorkManagerConfiguration(): Configuration {
        return Configuration.Builder().build()
    }
    //endregion
}
