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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.support.multidex.MultiDex;
import android.support.multidex.MultiDexApplication;
import android.support.v7.widget.AppCompatDrawableManager;
import android.util.Log;

import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.nextcloud.talk.BuildConfig;
import com.nextcloud.talk.dagger.modules.BusModule;
import com.nextcloud.talk.dagger.modules.ContextModule;
import com.nextcloud.talk.dagger.modules.DatabaseModule;
import com.nextcloud.talk.dagger.modules.RestModule;
import com.nextcloud.talk.jobs.AccountRemovalJob;
import com.nextcloud.talk.jobs.PushRegistrationJob;
import com.nextcloud.talk.jobs.creator.MagicJobCreator;
import com.nextcloud.talk.utils.database.user.UserModule;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import org.webrtc.PeerConnectionFactory;
import org.webrtc.voiceengine.WebRtcAudioManager;
import org.webrtc.voiceengine.WebRtcAudioUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Set;

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
public class NextcloudTalkApplication extends MultiDexApplication {
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
    // Solution inspired by https://stackoverflow.com/questions/34936590/why-isnt-my-vector-drawable-scaling-as-expected
    private void useCompatVectorIfNeeded() {
        int sdkInt = Build.VERSION.SDK_INT;
        if (sdkInt == 21 || sdkInt == 22) {
            try {
                @SuppressLint("RestrictedApi") AppCompatDrawableManager drawableManager = AppCompatDrawableManager.get();
                Class<?> inflateDelegateClass = Class.forName("android.support.v7.widget.AppCompatDrawableManager$InflateDelegate");
                Class<?> vdcInflateDelegateClass = Class.forName("android.support.v7.widget.AppCompatDrawableManager$VdcInflateDelegate");

                Constructor<?> constructor = vdcInflateDelegateClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object vdcInflateDelegate = constructor.newInstance();

                Class<?> args[] = {String.class, inflateDelegateClass};
                Method addDelegate = AppCompatDrawableManager.class.getDeclaredMethod("addDelegate", args);
                addDelegate.setAccessible(true);
                addDelegate.invoke(drawableManager, "vector", vdcInflateDelegate);
            } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException |
                    InvocationTargetException | IllegalAccessException e) {
                Log.d(TAG, "Failed to use reflection to enable proper vector scaling");
            }
        }
    }

    /*
       AEC blacklist and SL_ES_WHITELIST are borrowed from Signal
       https://github.com/WhisperSystems/Signal-Android/blob/551470123d006b76a68d705d131bb12513a5e683/src/org/thoughtcrime/securesms/ApplicationContext.java
    */
    private void initializeWebRtc() {
        try {
            Set<String> HARDWARE_AEC_BLACKLIST = new HashSet<String>() {{
                add("D6503"); // Sony Xperia Z2 D6503
                add("ONE A2005"); // OnePlus 2
                add("MotoG3"); // Moto G (3rd Generation)
                add("Nexus 6P"); // Nexus 6p
                add("Pixel"); // Pixel
                add("Pixel XL"); // Pixel XL
                add("MI 4LTE"); // Xiami Mi4
                add("Redmi Note 3"); // Redmi Note 3
                add("Redmi Note 4"); // Redmi Note 4
                add("SM-G900F"); // Samsung Galaxy S5
                add("g3_kt_kr"); // LG G3
                add("SM-G930F"); // Samsung Galaxy S7
                add("Xperia SP"); // Sony Xperia SP
                add("Nexus 6"); // Nexus 6
                add("ONE E1003"); // OnePlus X
                add("One"); // OnePlus One
                add("Moto G5");
            }};

            Set<String> OPEN_SL_ES_WHITELIST = new HashSet<String>() {{
                add("Pixel");
                add("Pixel XL");
            }};

            if (HARDWARE_AEC_BLACKLIST.contains(Build.MODEL)) {
                WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);
            }

            if (!OPEN_SL_ES_WHITELIST.contains(Build.MODEL)) {
                WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(true);
            }

            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(this)
                    .setEnableVideoHwAcceleration(true)
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
        JobManager.create(this).addJobCreator(new MagicJobCreator());
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(false);

        sharedApplication = this;

        initializeWebRtc();
        useCompatVectorIfNeeded();


        try {
            buildComponent();
        } catch (final GeneralSecurityException exception) {
            if (BuildConfig.DEBUG) {
                exception.printStackTrace();
            }
        }

        componentApplication.inject(this);
        refWatcher = LeakCanary.install(this);

        new JobRequest.Builder(PushRegistrationJob.TAG).setUpdateCurrent(true).startNow().build().schedule();
        new JobRequest.Builder(AccountRemovalJob.TAG).setUpdateCurrent(true).startNow().build().schedule();

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
}
