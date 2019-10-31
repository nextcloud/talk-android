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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import autodagger.AutoComponent
import autodagger.AutoInjector
import coil.Coil
import coil.ImageLoader
import com.bluelinelabs.logansquare.LoganSquare
import com.facebook.cache.disk.DiskCacheConfig
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig
import com.nextcloud.talk.BuildConfig
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
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.push.PushConfigurationState
import com.nextcloud.talk.newarch.di.module.CommunicationModule
import com.nextcloud.talk.newarch.di.module.NetworkModule
import com.nextcloud.talk.newarch.di.module.StorageModule
import com.nextcloud.talk.newarch.features.conversationsList.di.module.ConversationsListModule
import com.nextcloud.talk.newarch.local.dao.UsersDao
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.other.UserStatus.ACTIVE
import com.nextcloud.talk.newarch.local.models.other.UserStatus.DORMANT
import com.nextcloud.talk.utils.ClosedInterfaceImpl
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.OkHttpNetworkFetcherWithCache
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageModule
import com.nextcloud.talk.utils.database.user.UserModule
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.webrtc.MagicWebRTCUtils
import com.vanniktech.emoji.EmojiManager
import com.vanniktech.emoji.googlecompat.GoogleCompatEmojiProvider
import de.cotech.hw.SecurityKeyManager
import de.cotech.hw.SecurityKeyManagerConfig
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.conscrypt.Conscrypt
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.webrtc.PeerConnectionFactory
import org.webrtc.voiceengine.WebRtcAudioManager
import org.webrtc.voiceengine.WebRtcAudioUtils
import java.security.Security
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@AutoComponent(
    modules = [BusModule::class, ContextModule::class, DatabaseModule::class, RestModule::class, UserModule::class, ArbitraryStorageModule::class]
)
@Singleton
@AutoInjector(NextcloudTalkApplication::class)
class NextcloudTalkApplication : Application(), LifecycleObserver {
  //region Fields (components)
  lateinit var componentApplication: NextcloudTalkApplicationComponent
    private set
  //endregion

  //region Getters
  @Inject
  lateinit var userUtils: UserUtils

  val imageLoader: ImageLoader by inject()
  val appPreferences: AppPreferences by inject()
  val okHttpClient: OkHttpClient by inject()
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
    buildComponent()
    DavUtils.registerCustomFactories()

    componentApplication.inject(this)
    Coil.setDefaultImageLoader(imageLoader)
    migrateUsers()

    setAppTheme(appPreferences.theme)
    super.onCreate()

    val imagePipelineConfig = ImagePipelineConfig.newBuilder(this)
        .setNetworkFetcher(OkHttpNetworkFetcherWithCache(okHttpClient))
        .setMainDiskCacheConfig(
            DiskCacheConfig.newBuilder(this)
                .setMaxCacheSize(0)
                .setMaxCacheSizeOnLowDiskSpace(0)
                .setMaxCacheSizeOnVeryLowDiskSpace(0)
                .build()
        )
        .build()

    Fresco.initialize(this, imagePipelineConfig)
    Security.insertProviderAt(Conscrypt.newProvider(), 1)

    ClosedInterfaceImpl().providerInstallerInstallIfNeededAsync()

    val pushRegistrationWork = OneTimeWorkRequest.Builder(PushRegistrationWorker::class.java)
        .build()
    val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java)
        .build()
    val periodicCapabilitiesUpdateWork = PeriodicWorkRequest.Builder(
        CapabilitiesWorker::class.java,
        12, TimeUnit.HOURS
    )
        .build()
    val capabilitiesUpdateWork = OneTimeWorkRequest.Builder(CapabilitiesWorker::class.java)
        .build()
    val signalingSettingsWork = OneTimeWorkRequest.Builder(SignalingSettingsWorker::class.java)
        .build()

    WorkManager.getInstance()
        .enqueue(pushRegistrationWork)
    WorkManager.getInstance()
        .enqueue(accountRemovalWork)
    WorkManager.getInstance()
        .enqueue(capabilitiesUpdateWork)
    WorkManager.getInstance()
        .enqueue(signalingSettingsWork)
    WorkManager.getInstance()
        .enqueueUniquePeriodicWork(
            "DailyCapabilitiesUpdateWork", ExistingPeriodicWorkPolicy.REPLACE,
            periodicCapabilitiesUpdateWork
        )

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

    startKoin {
      androidContext(this@NextcloudTalkApplication)
      androidLogger()
      modules(listOf(CommunicationModule, StorageModule, NetworkModule, ConversationsListModule))
    }
  }

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    MultiDex.install(this)
  }

  fun migrateUsers() {
    GlobalScope.launch {
      val users: List<UserEntity> = userUtils.users as List<UserEntity>
      var userNg: UserNgEntity
      val newUsers = mutableListOf<UserNgEntity>()
      for (user in users) {
        userNg = UserNgEntity(user.id, user.userId, user.username, user.baseUrl)
        userNg.token = user.token
        userNg.displayName = user.displayName
        try {
          userNg.pushConfiguration =
            LoganSquare.parse(user.pushConfigurationState, PushConfigurationState::class.java)
        } catch (e: Exception) {
          // no push
        }
        if (user.capabilities != null) {
          userNg.capabilities = LoganSquare.parse(user.capabilities, Capabilities::class.java)
        }
        userNg.clientCertificate = user.clientCertificate
        try {
          userNg.externalSignaling =
            LoganSquare.parse(user.externalSignalingServer, ExternalSignalingServer::class.java)
        } catch (e: Exception) {
          // no external signaling
        }
        if (user.current) {
          userNg.status = ACTIVE
        } else {
          userNg.status = DORMANT
        }
        newUsers.add(userNg)
      }
      usersDao.saveUsers(*newUsers.toTypedArray())
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
  //endregion
}
