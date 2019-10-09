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
import androidx.annotation.NonNull;
import com.nextcloud.talk.R;
import com.nextcloud.talk.models.database.Models;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import dagger.Module;
import dagger.Provides;
import io.requery.Persistable;
import io.requery.android.sqlcipher.SqlCipherDatabaseSource;
import io.requery.reactivex.ReactiveEntityStore;
import io.requery.reactivex.ReactiveSupport;
import io.requery.sql.Configuration;
import io.requery.sql.EntityDataStore;
import net.orange_box.storebox.StoreBox;

import javax.inject.Singleton;

@Module
public class DatabaseModule {

    @Provides
    @Singleton
    public SqlCipherDatabaseSource provideSqlCipherDatabaseSource(@NonNull final Context context) {
        return new SqlCipherDatabaseSource(context, Models.DEFAULT,
                context.getResources().getString(R.string.nc_app_name).toLowerCase()
                        .replace(" ", "_").trim() + ".sqlite",
                context.getString(R.string.nc_talk_database_encryption_key), 6);
    }

    @Provides
    @Singleton
    public ReactiveEntityStore<Persistable> provideDataStore(@NonNull final SqlCipherDatabaseSource sqlCipherDatabaseSource) {
        final Configuration configuration = sqlCipherDatabaseSource.getConfiguration();
        return ReactiveSupport.toReactiveStore(new EntityDataStore<Persistable>(configuration));
    }

    @Provides
    @Singleton
    public AppPreferences providePreferences(@NonNull final Context poContext) {
        return StoreBox.create(poContext, AppPreferences.class);
    }
}
