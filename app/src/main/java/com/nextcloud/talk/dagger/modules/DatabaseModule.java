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
package com.nextcloud.talk.dagger.modules;

import android.content.Context;
import android.support.annotation.NonNull;

import com.nextcloud.talk.R;
import com.nextcloud.talk.persistence.entities.Models;

import net.orange_box.storebox.StoreBox;

import java.util.prefs.Preferences;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import io.requery.Persistable;
import io.requery.android.sqlcipher.SqlCipherDatabaseSource;
import io.requery.reactivex.ReactiveEntityStore;
import io.requery.reactivex.ReactiveSupport;
import io.requery.sql.Configuration;
import io.requery.sql.EntityDataStore;

@Module
public class DatabaseModule {

    @Provides
    @Singleton
    public ReactiveEntityStore<Persistable> provideDataStore(@NonNull final Context context) {

        final SqlCipherDatabaseSource source = new SqlCipherDatabaseSource(context, Models.DEFAULT,
                context.getResources().getString(R.string.nc_app_name).toLowerCase()
                        .replace(" ", "_").trim() + ".sqlite",
                context.getString(R.string.nc_talk_database_encryption_key), 1);
        final Configuration configuration = source.getConfiguration();
        return ReactiveSupport.toReactiveStore(new EntityDataStore<Persistable>(configuration));
    }

    @Provides
    @Singleton
    public Preferences providePreferences(@NonNull final Context poContext) {
        return StoreBox.create(poContext, Preferences.class);
    }
}
