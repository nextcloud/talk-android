/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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
package com.nextcloud.talk.utils.database.user;

import android.text.TextUtils;

import com.nextcloud.talk.models.database.User;
import com.nextcloud.talk.models.database.UserEntity;

import java.util.List;

import androidx.annotation.Nullable;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.requery.Persistable;
import io.requery.query.Result;
import io.requery.reactivex.ReactiveEntityStore;

/**
 * @deprecated use {@link com.nextcloud.talk.users.UserManager} instead.
 *
 * TODO: remove this class with a major version, 15.0.0 or 16.0.0.
 */
@Deprecated
public class UserUtils {
    private ReactiveEntityStore<Persistable> dataStore;

    UserUtils(ReactiveEntityStore<Persistable> dataStore) {
        this.dataStore = dataStore;
    }
}
