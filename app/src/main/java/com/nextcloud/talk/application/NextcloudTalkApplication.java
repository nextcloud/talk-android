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
package com.nextcloud.talk.application;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.emoji.bundled.BundledEmojiCompatConfig;
import androidx.emoji.text.EmojiCompat;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.LifecycleObserver;
import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import autodagger.AutoComponent;
import autodagger.AutoInjector;
import com.facebook.cache.disk.DiskCacheConfig;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipelineConfig;
import com.nextcloud.talk.BuildConfig;
import com.nextcloud.talk.components.filebrowser.webdav.DavUtils;
import com.nextcloud.talk.dagger.modules.BusModule;
import com.nextcloud.talk.dagger.modules.ContextModule;
import com.nextcloud.talk.dagger.modules.DatabaseModule;
import com.nextcloud.talk.dagger.modules.RestModule;
import com.nextcloud.talk.jobs.AccountRemovalWorker;
import com.nextcloud.talk.jobs.CapabilitiesWorker;
import com.nextcloud.talk.jobs.PushRegistrationWorker;
import com.nextcloud.talk.jobs.SignalingSettingsWorker;
import com.nextcloud.talk.utils.ClosedInterfaceImpl;
import com.nextcloud.talk.utils.DeviceUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.OkHttpNetworkFetcherWithCache;
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageModule;
import com.nextcloud.talk.utils.database.user.UserModule;
import com.nextcloud.talk.utils.singletons.MerlinTheWizard;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.webrtc.MagicWebRTCUtils;
import com.vanniktech.emoji.EmojiManager;
import com.vanniktech.emoji.googlecompat.GoogleCompatEmojiProvider;

import de.cotech.hw.SecurityKeyManager;
import de.cotech.hw.SecurityKeyManagerConfig;
import okhttp3.OkHttpClient;
import org.conscrypt.Conscrypt;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.security.Security;
import java.util.concurrent.TimeUnit;

@AutoComponent(
        modules = {
                BusModule.class,
                ContextModule.class,
                DatabaseModule.class,
                RestModule.class,
                UserModule.class,
                ArbitraryStorageModule.class,
        }
)

@Singleton
@AutoInjector(NextcloudTalkApplication.class)
public class NextcloudTalkApplication extends MultiDexApplication implements LifecycleObserver {
    private static final String TAG = NextcloudTalkApplication.class.getSimpleName();
    //region Singleton
    protected static NextcloudTalkApplication sharedApplication;
    //region Fields (components)
    protected NextcloudTalkApplicationComponent componentApplication;

    @Inject
    AppPreferences appPreferences;
    @Inject
    OkHttpClient okHttpClient;
    //endregion

    public static NextcloudTalkApplication getSharedApplication() {
        return sharedApplication;
    }
    //endregion

    //region private methods
    private void initializeWebRtc() {
        try {
            if (MagicWebRTCUtils.HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
                WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
            }

            if (!MagicWebRTCUtils.OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
                WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
            }


            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this)
                    .setEnableVideoHwAcceleration(MagicWebRTCUtils.shouldEnableVideoHardwareAcceleration())
                    .createInitializationOptions());
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, e);
        }
    }

    //endregion

    //region Overridden methods
    @Override
    public void onCreate() {
        sharedApplication = this;

        SecurityKeyManager securityKeyManager = SecurityKeyManager.getInstance();
        SecurityKeyManagerConfig securityKeyConfig = new SecurityKeyManagerConfig.Builder()
                .setEnableDebugLogging(BuildConfig.DEBUG)
                .build();
        securityKeyManager.init(this, securityKeyConfig);

        initializeWebRtc();
        DisplayUtils.useCompatVectorIfNeeded();
        buildComponent();
        DavUtils.registerCustomFactories();

        componentApplication.inject(this);

        setAppTheme(appPreferences.getTheme());
        super.onCreate();

        ImagePipelineConfig imagePipelineConfig = ImagePipelineConfig.newBuilder(this)
                .setNetworkFetcher(new OkHttpNetworkFetcherWithCache(okHttpClient))
                .setMainDiskCacheConfig(DiskCacheConfig.newBuilder(this)
                        .setMaxCacheSize(0)
                        .setMaxCacheSizeOnLowDiskSpace(0)
                        .setMaxCacheSizeOnVeryLowDiskSpace(0)
                        .build())
                .build();

        Fresco.initialize(this, imagePipelineConfig);
        Security.insertProviderAt(Conscrypt.newProvider(), 1);

        new ClosedInterfaceImpl().providerInstallerInstallIfNeededAsync();
        DeviceUtils.ignoreSpecialBatteryFeatures();

        OneTimeWorkRequest pushRegistrationWork = new OneTimeWorkRequest.Builder(PushRegistrationWorker.class).build();
        OneTimeWorkRequest accountRemovalWork = new OneTimeWorkRequest.Builder(AccountRemovalWorker.class).build();
        PeriodicWorkRequest periodicCapabilitiesUpdateWork = new PeriodicWorkRequest.Builder(CapabilitiesWorker.class,
                12, TimeUnit.HOURS).build();
        OneTimeWorkRequest capabilitiesUpdateWork = new OneTimeWorkRequest.Builder(CapabilitiesWorker.class).build();
        OneTimeWorkRequest signalingSettingsWork = new OneTimeWorkRequest.Builder(SignalingSettingsWorker.class).build();

        new MerlinTheWizard().initMerlin();

        WorkManager.getInstance().enqueue(pushRegistrationWork);
        WorkManager.getInstance().enqueue(accountRemovalWork);
        WorkManager.getInstance().enqueue(capabilitiesUpdateWork);
        WorkManager.getInstance().enqueue(signalingSettingsWork);
        WorkManager.getInstance().enqueueUniquePeriodicWork("DailyCapabilitiesUpdateWork", ExistingPeriodicWorkPolicy.REPLACE, periodicCapabilitiesUpdateWork);

        final EmojiCompat.Config config = new BundledEmojiCompatConfig(this);
        config.setReplaceAll(true);
        EmojiCompat emojiCompat = EmojiCompat.init(config);

        EmojiManager.install(new GoogleCompatEmojiProvider(emojiCompat));
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        sharedApplication = null;
    }

    //endregion

    //region Getters
    public NextcloudTalkApplicationComponent getComponentApplication() {
        return componentApplication;
    }
    //endregion

    //region Setters
    public static void setAppTheme(String theme) {
        switch (theme) {
            case "night_no":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "night_yes":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case "battery_saver":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY);
                break;
            default:
                // will be "follow_system" only for now
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        }
    }
    //endregion



    //region Protected methods
    protected void buildComponent() {
        componentApplication = DaggerNextcloudTalkApplicationComponent.builder()
                .busModule(new BusModule())
                .contextModule(new ContextModule(getApplicationContext()))
                .databaseModule(new DatabaseModule())
                .restModule(new RestModule(getApplicationContext()))
                .userModule(new UserModule())
                .arbitraryStorageModule(new ArbitraryStorageModule())
                .build();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
    //endregion
}
