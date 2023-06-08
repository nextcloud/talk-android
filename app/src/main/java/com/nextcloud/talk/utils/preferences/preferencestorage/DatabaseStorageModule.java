/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
 * @author Tim Krüger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
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

package com.nextcloud.talk.utils.preferences.preferencestorage;

import android.text.TextUtils;
import android.util.Log;

import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager;
import com.nextcloud.talk.data.storage.model.ArbitraryStorage;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.UserIdUtils;
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew;

import javax.inject.Inject;

import autodagger.AutoInjector;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@AutoInjector(NextcloudTalkApplication.class)
public class DatabaseStorageModule {
    private static final String TAG = "DatabaseStorageModule";
    @Inject
    ArbitraryStorageManager arbitraryStorageManager;

    @Inject
    NcApi ncApi;

    private int messageExpiration;
    private final User conversationUser;
    private final String conversationToken;
    private final long accountIdentifier;

    private boolean lobbyValue;

    private String messageNotificationLevel;

    public DatabaseStorageModule(User conversationUser, String conversationToken) {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        this.conversationUser = conversationUser;
        this.accountIdentifier = UserIdUtils.INSTANCE.getIdForUser(conversationUser);
        this.conversationToken = conversationToken;
    }

    public void saveBoolean(String key, boolean value) {
        if ("call_notifications_switch".equals(key)) {
            int apiVersion = ApiUtils.getConversationApiVersion(conversationUser, new int[]{4});
            ncApi.notificationCalls(ApiUtils.getCredentials(conversationUser.getUsername(),
                                                            conversationUser.getToken()),
                                    ApiUtils.getUrlForRoomNotificationCalls(apiVersion,
                                                                            conversationUser.getBaseUrl(),
                                                                            conversationToken),
                                    value ? 1 : 0)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                               @Override
                               public void onSubscribe(@NonNull Disposable d) {
                                   // unused atm
                               }

                               @Override
                               public void onNext(@NonNull GenericOverall genericOverall) {
                                   Log.d(TAG, "Toggled notification calls");
                               }

                               @Override
                               public void onError(@NonNull Throwable e) {
                                   Log.e(TAG, "Error when trying to toggle notification calls", e);
                               }

                               @Override
                               public void onComplete() {
                                   // unused atm
                               }
                           }
                          );
        }

        if (!"lobby_switch".equals(key)) {
            arbitraryStorageManager.storeStorageSetting(accountIdentifier,
                                                        key,
                                                        Boolean.toString(value),
                                                        conversationToken);
        } else {
            lobbyValue = value;
        }
    }

    public void saveString(String key, String value) {
        if ("conversation_settings_dropdown".equals(key)) {
            int apiVersion = ApiUtils.getConversationApiVersion(conversationUser, new int[]{4});

            String trimmedValue = value.replace("expire_", "");
            int valueInt = Integer.parseInt(trimmedValue);

            ncApi.setMessageExpiration(
                    ApiUtils.getCredentials(
                        conversationUser.getUsername(),
                        conversationUser.getToken()),
                    ApiUtils.getUrlForMessageExpiration(
                        apiVersion,
                        conversationUser.getBaseUrl(),
                        conversationToken),
                    valueInt)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        // unused atm
                    }

                    @Override
                    public void onNext(@NonNull GenericOverall genericOverall) {
                        messageExpiration = valueInt;
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Log.e(TAG, "Error when trying to set message expiration", e);
                    }

                    @Override
                    public void onComplete() {
                        // unused atm
                    }
                });

        } else if ("conversation_info_message_notifications_dropdown".equals(key)) {
            if (CapabilitiesUtilNew.hasSpreedFeatureCapability(conversationUser, "notification-levels")) {
                if (TextUtils.isEmpty(messageNotificationLevel) || !messageNotificationLevel.equals(value)) {
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

                    int apiVersion = ApiUtils.getConversationApiVersion(conversationUser, new int[]{ApiUtils.APIv4, 1});

                    ncApi.setNotificationLevel(ApiUtils.getCredentials(conversationUser.getUsername(),
                                                                       conversationUser.getToken()),
                                               ApiUtils.getUrlForRoomNotificationLevel(apiVersion,
                                                                                       conversationUser.getBaseUrl(),
                                                                                       conversationToken),
                                               intValue)
                        .subscribeOn(Schedulers.io())
                        .subscribe(new Observer<GenericOverall>() {
                            @Override
                            public void onSubscribe(Disposable d) {
                                // unused atm
                            }

                            @Override
                            public void onNext(GenericOverall genericOverall) {messageNotificationLevel = value;}

                            @Override
                            public void onError(Throwable e) {
                                // unused atm
                            }

                            @Override
                            public void onComplete() {
                                // unused atm
                            }
                        });
                } else {
                    messageNotificationLevel = value;
                }
            }
        } else {
            arbitraryStorageManager.storeStorageSetting(accountIdentifier, key, value, conversationToken);
        }
    }
    public boolean getBoolean(String key, boolean defaultVal) {
        if ("lobby_switch".equals(key)) {
            return lobbyValue;
        } else {
            return arbitraryStorageManager
                .getStorageSetting(accountIdentifier, key, conversationToken)
                .map(arbitraryStorage -> Boolean.parseBoolean(arbitraryStorage.getValue()))
                .blockingGet(defaultVal);
        }
    }

    public String getString(String key, String defaultVal) {
        if ("conversation_settings_dropdown".equals(key)) {
            switch (messageExpiration) {
                case 2419200:
                    return "expire_2419200";
                case 604800:
                    return "expire_604800";
                case 86400:
                    return "expire_86400";
                case 28800:
                    return "expire_28800";
                case 3600:
                    return "expire_3600";
                default:
                    return "expire_0";
            }
        } else if ("conversation_info_message_notifications_dropdown".equals(key)) {
            return messageNotificationLevel;
        } else {
            return arbitraryStorageManager
                .getStorageSetting(accountIdentifier, key, conversationToken)
                .map(ArbitraryStorage::getValue)
                .blockingGet(defaultVal);
        }
    }

    public void setMessageExpiration(int messageExpiration) {
        this.messageExpiration = messageExpiration;
    }
}
