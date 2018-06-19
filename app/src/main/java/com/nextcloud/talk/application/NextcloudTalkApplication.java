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

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.content.Context;
import android.os.Build;
import android.support.multidex.MultiDex;
import android.support.multidex.MultiDexApplication;
import android.util.Log;

import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.nextcloud.talk.BuildConfig;
import com.nextcloud.talk.dagger.modules.BusModule;
import com.nextcloud.talk.dagger.modules.ContextModule;
import com.nextcloud.talk.dagger.modules.DatabaseModule;
import com.nextcloud.talk.dagger.modules.RestModule;
import com.nextcloud.talk.jobs.AccountRemovalJob;
import com.nextcloud.talk.jobs.CapabilitiesJob;
import com.nextcloud.talk.jobs.PushRegistrationJob;
import com.nextcloud.talk.jobs.creator.MagicJobCreator;
import com.nextcloud.talk.utils.ClosedInterfaceImpl;
import com.nextcloud.talk.utils.DeviceUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.database.user.UserModule;
import com.nextcloud.talk.utils.singletons.ApplicationWideStateHolder;
import com.nextcloud.talk.webrtc.MagicWebRTCUtils;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import org.webrtc.PeerConnectionFactory;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;

import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;

import javax.inject.Singleton;

import autodagger.AutoComponent;
import autodagger.AutoInjector;

@AutoComponent(
        modules = {
                BusModule.class,
                ContextModule.class,
                DatabaseModule.class,
                RestModule.class,
                UserModule.class,
        }
)

@Singleton
@AutoInjector(NextcloudTalkApplication.class)
public class NextcloudTalkApplication extends MultiDexApplication implements LifecycleObserver {
    private static final String TAG = NextcloudTalkApplication.class.getSimpleName();

    //region Public variables
    public static RefWatcher refWatcher;
    //endregion

    //region Singleton
    protected static NextcloudTalkApplication sharedApplication;
    //region Fields (components)
    protected NextcloudTalkApplicationComponent componentApplication;
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
        super.onCreate();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);

        JobManager.create(this).addJobCreator(new MagicJobCreator());

        sharedApplication = this;

        initializeWebRtc();
        DisplayUtils.useCompatVectorIfNeeded();

        try {
            buildComponent();
        } catch (final GeneralSecurityException exception) {
            if (BuildConfig.DEBUG) {
                exception.printStackTrace();
            }
        }

        componentApplication.inject(this);
        refWatcher = LeakCanary.install(this);

        new ClosedInterfaceImpl().providerInstallerInstallIfNeededAsync();
        DeviceUtils.ignoreSpecialBatteryFeatures();

        new JobRequest.Builder(PushRegistrationJob.TAG).setUpdateCurrent(true).startNow().build().scheduleAsync();
        new JobRequest.Builder(AccountRemovalJob.TAG).setUpdateCurrent(true).startNow().build().scheduleAsync();

        schedulePeriodCapabilitiesJob();
        new JobRequest.Builder(CapabilitiesJob.TAG).setUpdateCurrent(false).startNow().build().scheduleAsync();
    }

    private void schedulePeriodCapabilitiesJob() {
        boolean periodicJobFound = false;
        for (JobRequest jobRequest : JobManager.instance().getAllJobRequestsForTag(CapabilitiesJob.TAG)) {
            if (jobRequest.isPeriodic()) {
                periodicJobFound = true;
                break;
            }
        }

        if (!periodicJobFound) {
            new JobRequest.Builder(CapabilitiesJob.TAG).setUpdateCurrent(true)
                    .setPeriodic(TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(1))
                    .build().scheduleAsync();
        }
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

    //region Protected methods
    protected void buildComponent() throws GeneralSecurityException {
        componentApplication = DaggerNextcloudTalkApplicationComponent.builder()
                .busModule(new BusModule())
                .contextModule(new ContextModule(getApplicationContext()))
                .databaseModule(new DatabaseModule())
                .restModule(new RestModule())
                .userModule(new UserModule())
                .build();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
    //endregion

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private void onAppBackgrounded() {
        ApplicationWideStateHolder.getInstance().setInForeground(false);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private void onAppForegrounded() {
        ApplicationWideStateHolder.getInstance().setInForeground(true);
    }
}
