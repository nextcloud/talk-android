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
import androidx.annotation.NonNull;
import com.nextcloud.talk.R;
import com.nextcloud.talk.data.source.local.TalkDatabase;
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
import net.sqlcipher.database.SQLiteDatabase;

import javax.inject.Singleton;

@Module
public class DatabaseModule {
    public static final int DB_VERSION = 7;

    @Provides
    @Singleton
    public SqlCipherDatabaseSource provideSqlCipherDatabaseSource(
        @NonNull final Context context,
        final AppPreferences appPreferences) {
        int version = DB_VERSION;
        if (appPreferences.getIsDbRoomMigrated()) {
            version++;
        }
        return new SqlCipherDatabaseSource(
            context,
            Models.DEFAULT,
            context
                .getResources()
                .getString(R.string.nc_app_product_name)
                .toLowerCase()
                .replace(" ", "_")
                .trim()
                + ".sqlite",
            context.getString(R.string.nc_talk_database_encryption_key),
            version) {
            @Override
            public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                if (newVersion < 7) {
                    super.onDowngrade(db, oldVersion, newVersion);
                }
            }
        };
    }

    @Provides
    @Singleton
    public ReactiveEntityStore<Persistable> provideDataStore(
        @NonNull final SqlCipherDatabaseSource sqlCipherDatabaseSource) {
        final Configuration configuration = sqlCipherDatabaseSource.getConfiguration();
        return ReactiveSupport.toReactiveStore(new EntityDataStore<>(configuration));
    }

    @Provides
    @Singleton
    public AppPreferences providePreferences(@NonNull final Context poContext) {
        AppPreferences preferences = StoreBox.create(poContext, AppPreferences.class);
        preferences.removeLinkPreviews();
        return preferences;
    }

    @Provides
    @Singleton
    public TalkDatabase provideTalkDatabase(@NonNull final Context context) {
        return TalkDatabase.Companion.getInstance(context);
    }
}
