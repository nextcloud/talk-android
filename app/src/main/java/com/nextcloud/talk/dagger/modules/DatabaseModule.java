/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
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
package com.nextcloud.talk.dagger.modules;

import android.content.Context;

import com.nextcloud.talk.data.source.local.TalkDatabase;
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
    public TalkDatabase provideTalkDatabase(@NonNull final Context context,
                                            @NonNull final AppPreferences appPreferences) {
        return TalkDatabase.getInstance(context, appPreferences);
    }
}
