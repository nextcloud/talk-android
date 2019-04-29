/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.utils.database.arbitrarystorage;

import androidx.annotation.Nullable;
import com.nextcloud.talk.models.database.ArbitraryStorage;
import com.nextcloud.talk.models.database.ArbitraryStorageEntity;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.requery.Persistable;
import io.requery.query.Result;
import io.requery.reactivex.ReactiveEntityStore;
import io.requery.reactivex.ReactiveScalar;

public class ArbitraryStorageUtils {
    private ReactiveEntityStore<Persistable> dataStore;

    ArbitraryStorageUtils(ReactiveEntityStore<Persistable> dataStore) {
        this.dataStore = dataStore;
    }


    public void storeStorageSetting(long accountIdentifier, String key, String value, String object) {
        ArbitraryStorageEntity arbitraryStorageEntity = new ArbitraryStorageEntity();
        arbitraryStorageEntity.setAccountIdentifier(accountIdentifier);
        arbitraryStorageEntity.setKey(key);
        arbitraryStorageEntity.setValue(value);
        arbitraryStorageEntity.setObject(object);

        dataStore.upsert(arbitraryStorageEntity)
                .toObservable()
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    public ArbitraryStorageEntity getStorageSetting(long accountIdentifier, String key, @Nullable String object) {
        Result findStorageQueryResult = dataStore.select(ArbitraryStorage.class)
                .where(ArbitraryStorageEntity.ACCOUNT_IDENTIFIER.eq(accountIdentifier)
                        .and(ArbitraryStorageEntity.KEY.eq(key)).and(ArbitraryStorageEntity.OBJECT.eq(object)))
                .limit(1).get();

        return (ArbitraryStorageEntity) findStorageQueryResult.firstOrNull();
    }

    public Observable deleteAllEntriesForAccountIdentifier(long accountIdentifier) {
        ReactiveScalar<Integer> deleteResult = dataStore.delete(ArbitraryStorage.class).where(ArbitraryStorageEntity.ACCOUNT_IDENTIFIER.eq(accountIdentifier)).get();

        return deleteResult.single().toObservable()
                .subscribeOn(Schedulers.io());
    }
}
