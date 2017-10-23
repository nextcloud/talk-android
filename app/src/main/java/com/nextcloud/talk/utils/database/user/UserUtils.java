/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic
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
package com.nextcloud.talk.utils.database.user;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.nextcloud.talk.persistence.entities.User;
import com.nextcloud.talk.persistence.entities.UserEntity;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.requery.Persistable;
import io.requery.query.Result;
import io.requery.reactivex.ReactiveEntityStore;

public class UserUtils {
    private ReactiveEntityStore<Persistable> dataStore;

    UserUtils(ReactiveEntityStore<Persistable> dataStore) {
        this.dataStore = dataStore;

    }

    public boolean anyUserExists() {
        return (dataStore.count(User.class).limit(1).get().value() > 0);
    }

    // temporary method while we only support 1 user
    public UserEntity getCurrentUser() {
        Result findUserQueryResult = dataStore.select(User.class).limit(1).get();

        return (UserEntity) findUserQueryResult.firstOrNull();
    }

    public Completable deleteUser(String username, String serverUrl) {
        Result findUserQueryResult = dataStore.select(User.class).where(UserEntity.USERNAME.eq(username).
                and(UserEntity.BASE_URL.eq(serverUrl.toLowerCase()))).limit(1).get();

        UserEntity user = (UserEntity) findUserQueryResult.firstOrNull();

        return dataStore.delete(user)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread());

    }

    public Observable<UserEntity> createOrUpdateUser(String username, String token, String serverUrl,
                                                     @Nullable String displayName) {
        Result findUserQueryResult = dataStore.select(User.class).where(UserEntity.USERNAME.eq(username).
                and(UserEntity.BASE_URL.eq(serverUrl.toLowerCase()))).limit(1).get();

        UserEntity user = (UserEntity) findUserQueryResult.firstOrNull();

        if (user == null) {
            user = new UserEntity();
            user.setBaseUrl(serverUrl);
            user.setUsername(username);
            user.setToken(token);

            if (!TextUtils.isEmpty(displayName)) {
                user.setDisplayName(displayName);
            }

        } else {
            if (!token.equals(user.getToken())) {
                user.setToken(token);
            }

            if (!TextUtils.isEmpty(displayName) && !displayName.equals(user.getDisplayName())) {
                user.setDisplayName(displayName);
            }
        }

        return dataStore.upsert(user)
                .toObservable()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread());
    }

}
