/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.dagger.modules;

import android.content.Context;

import com.nextcloud.talk.api.NcApiCoroutines;
import com.nextcloud.talk.data.network.NetworkMonitor;
import com.nextcloud.talk.data.network.NetworkMonitorImpl;
import com.nextcloud.talk.data.source.local.TalkDatabase;
import com.nextcloud.talk.serverstatus.ServerStatusRepository;
import com.nextcloud.talk.serverstatus.ServerStatusRepositoryImpl;
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.preferences.AppPreferencesImpl;

import javax.inject.Singleton;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import dagger.Module;
import dagger.Provides;
import kotlinx.coroutines.ExperimentalCoroutinesApi;

@Module
@OptIn(markerClass = ExperimentalCoroutinesApi.class)
public class DatabaseModule {
    @Provides
    @Singleton
    public AppPreferences providePreferences(@NonNull final Context poContext) {
        AppPreferences preferences = new AppPreferencesImpl(poContext);
        preferences.removeLinkPreviews();
        return preferences;
    }

    @Provides
    @Singleton
    public AppPreferencesImpl providePreferencesImpl(@NonNull final Context poContext) {
        return new AppPreferencesImpl(poContext);
    }

    @Provides
    @Singleton
    public TalkDatabase provideTalkDatabase(@NonNull final Context context) {
        return TalkDatabase.getInstance(context);
    }

    @Provides
    @Singleton
    public NetworkMonitor provideNetworkMonitor(@NonNull final Context poContext) {
        return new NetworkMonitorImpl(poContext);
    }

    @Provides
    @Singleton
    public ServerStatusRepository provideServerStatusRepository(@NonNull final NcApiCoroutines ncApiCoroutines, @NonNull final CurrentUserProviderNew currentUserProviderNew) {
        return new ServerStatusRepositoryImpl(ncApiCoroutines, currentUserProviderNew);
    }
}



