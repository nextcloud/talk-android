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

package com.nextcloud.talk.utils.preferencestorage;

import android.os.Bundle;
import android.text.TextUtils;
import autodagger.AutoInjector;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.ArbitraryStorageEntity;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageUtils;
import com.yarolegovich.mp.io.StorageModule;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import javax.inject.Inject;
import java.util.Set;

@AutoInjector(NextcloudTalkApplication.class)
public class DatabaseStorageModule implements StorageModule {
    @Inject
    ArbitraryStorageUtils arbitraryStorageUtils;

    @Inject
    NcApi ncApi;

    private UserEntity conversationUser;
    private String conversationToken;
    private long accountIdentifier;

    private String messageNotificationLevel;
    public DatabaseStorageModule(UserEntity conversationUser, String conversationToken) {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        this.conversationUser = conversationUser;
        this.accountIdentifier = conversationUser.getId();
        this.conversationToken = conversationToken;
    }

    @Override
    public void saveBoolean(String key, boolean value) {
        arbitraryStorageUtils.storeStorageSetting(accountIdentifier, key, Boolean.toString(value), conversationToken);
    }

    @Override
    public void saveString(String key, String value) {
        if (!key.equals("message_notification_level")) {
            arbitraryStorageUtils.storeStorageSetting(accountIdentifier, key, value, conversationToken);
        } else {
            if (conversationUser.hasSpreedCapabilityWithName("notification-levels")) {
                if (!TextUtils.isEmpty(messageNotificationLevel) && !messageNotificationLevel.equals(value)) {
                    int intValue;
                    switch (value) {
                        case "never":
                            intValue = 3;
                            break;
                        case "mention":
                            intValue = 2;
                            break;
                        case "always":
                            intValue = 1;
                            break;
                        default:
                            intValue = 0;
                    }

                    ncApi.setNotificationLevel(ApiUtils.getCredentials(conversationUser.getUsername(), conversationUser.getToken()),
                            ApiUtils.getUrlForSettingNotificationlevel(conversationUser.getBaseUrl(), conversationToken),
                            intValue)
                            .subscribeOn(Schedulers.io())
                            .subscribe(new Observer<GenericOverall>() {
                                @Override
                                public void onSubscribe(Disposable d) {

                                }

                                @Override
                                public void onNext(GenericOverall genericOverall) {
                                    messageNotificationLevel = value;
                                }

                                @Override
                                public void onError(Throwable e) {

                                }

                                @Override
                                public void onComplete() {
                                }
                            });
                } else {
                    messageNotificationLevel = value;
                }
            }
        }
    }

    @Override
    public void saveInt(String key, int value) {
        arbitraryStorageUtils.storeStorageSetting(accountIdentifier, key, Integer.toString(value), conversationToken);
    }

    @Override
    public void saveStringSet(String key, Set<String> value) {

    }

    @Override
    public boolean getBoolean(String key, boolean defaultVal) {
        ArbitraryStorageEntity valueFromDb = arbitraryStorageUtils.getStorageSetting(accountIdentifier, key, conversationToken);
        if (valueFromDb == null) {
            return defaultVal;
        } else {
            return Boolean.parseBoolean(valueFromDb.getValue());
        }
    }

    @Override
    public String getString(String key, String defaultVal) {
        if (!key.equals("message_notification_level")) {
            ArbitraryStorageEntity valueFromDb = arbitraryStorageUtils.getStorageSetting(accountIdentifier, key, conversationToken);
            if (valueFromDb == null) {
                return defaultVal;
            } else {
                return valueFromDb.getValue();
            }
        } else {
            return messageNotificationLevel;
        }
    }

    @Override
    public int getInt(String key, int defaultVal) {
        ArbitraryStorageEntity valueFromDb = arbitraryStorageUtils.getStorageSetting(accountIdentifier, key, conversationToken);
        if (valueFromDb == null) {
            return defaultVal;
        } else {
            return Integer.parseInt(valueFromDb.getValue());
        }
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defaultVal) {
        return null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {

    }

    @Override
    public void onRestoreInstanceState(Bundle savedState) {

    }
}
