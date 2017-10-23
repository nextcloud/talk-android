/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic
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
package com.nextcloud.talk.utils.database.cache;

import com.nextcloud.talk.persistence.entities.Cache;
import com.nextcloud.talk.persistence.entities.CacheEntity;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.requery.Persistable;
import io.requery.query.Result;
import io.requery.reactivex.ReactiveEntityStore;

public class CacheUtils {
    private ReactiveEntityStore<Persistable> dataStore;

    CacheUtils(ReactiveEntityStore<Persistable> dataStore) {
        this.dataStore = dataStore;

    }

    public boolean cacheExistsForContext(String context) {
        return (dataStore.count(Cache.class).where(CacheEntity.KEY.eq(context)).limit(1).get().value() > 0);
    }

    public Observable<CacheEntity> getViewCache(Long userId, String context) {
        return dataStore.select(CacheEntity.class).where(CacheEntity.KEY.eq(context).
                and(CacheEntity.USER_ID.eq(userId))).limit(1).get().observable()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread());
    }

    public Observable<CacheEntity> createOrUpdateViewCache(String cache, Long userId, String context) {
        Result findUserQueryResult = dataStore.select(CacheEntity.class).where(CacheEntity.KEY.eq(context).
                and(CacheEntity.USER_ID.eq(userId))).limit(1).get();

        CacheEntity cacheEntity = (CacheEntity) findUserQueryResult.firstOrNull();

        if (cacheEntity == null) {
            cacheEntity = new CacheEntity();
            cacheEntity.setKey(context);
            cacheEntity.setUserId(userId);
            cacheEntity.setValue(cache);
        } else {
            cacheEntity.setValue(cache);
        }

        return dataStore.upsert(cacheEntity)
                .toObservable()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread());
    }

}
