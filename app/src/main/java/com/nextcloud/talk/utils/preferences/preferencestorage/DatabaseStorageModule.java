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

package com.nextcloud.talk.utils.preferences.preferencestorage;

import android.os.Bundle;
import android.text.TextUtils;
import autodagger.AutoInjector;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.interfaces.ConversationInfoInterface;
import com.nextcloud.talk.models.database.ArbitraryStorageEntity;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageUtils;
import com.yarolegovich.mp.io.StorageModule;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.util.Set;
import javax.inject.Inject;

@AutoInjector(NextcloudTalkApplication.class)
public class DatabaseStorageModule implements StorageModule {
  @Inject
  ArbitraryStorageUtils arbitraryStorageUtils;

  @Inject
  NcApi ncApi;

  private UserEntity conversationUser;
  private String conversationToken;
  private long accountIdentifier;

  private boolean lobbyValue;
  private boolean favoriteConversationValue;
  private boolean allowGuestsValue;

  private Boolean hasPassword;
  private String conversationNameValue;

  private String messageNotificationLevel;
  private ConversationInfoInterface conversationInfoInterface;

  public DatabaseStorageModule(UserEntity conversationUser, String conversationToken,
      ConversationInfoInterface conversationInfoInterface) {
    NextcloudTalkApplication.Companion.getSharedApplication()
        .getComponentApplication()
        .inject(this);

    this.conversationUser = conversationUser;
    this.accountIdentifier = conversationUser.getId();
    this.conversationToken = conversationToken;
    this.conversationInfoInterface = conversationInfoInterface;
  }

  @Override
  public void saveBoolean(String key, boolean value) {
    if (!key.equals("conversation_lobby") && !key.equals("allow_guests") && !key.equals(
        "favorite_conversation")) {
      arbitraryStorageUtils.storeStorageSetting(accountIdentifier, key, Boolean.toString(value),
          conversationToken);
    } else {
      switch (key) {
        case "conversation_lobby":
          lobbyValue = value;
          break;
        case "allow_guests":
          allowGuestsValue = value;
          break;
        case "favorite_conversation":
          favoriteConversationValue = value;
          break;
        default:
      }
    }
  }

  @Override
  public void saveString(String key, String value) {
    if (!key.equals("message_notification_level")
        && !key.equals("conversation_name")
        && !key.equals("conversation_password")) {
      arbitraryStorageUtils.storeStorageSetting(accountIdentifier, key, value, conversationToken);
    } else {
      if (key.equals("message_notification_level")) {
        if (conversationUser.hasSpreedFeatureCapability("notification-levels")) {
          if (!TextUtils.isEmpty(messageNotificationLevel) && !messageNotificationLevel.equals(
              value)) {
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

            ncApi.setNotificationLevel(
                ApiUtils.getCredentials(conversationUser.getUsername(),
                    conversationUser.getToken()),
                ApiUtils.getUrlForSettingNotificationlevel(conversationUser.getBaseUrl(),
                    conversationToken),
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
      } else if (key.equals("conversation_password")) {
        if (hasPassword != null) {
          ncApi.setPassword(ApiUtils.getCredentials(conversationUser.getUsername(),
              conversationUser.getToken()),
              ApiUtils.getUrlForPassword(conversationUser.getBaseUrl(),
                  conversationToken), value)
              .subscribeOn(Schedulers.io())
              .subscribe(new Observer<GenericOverall>() {
                @Override
                public void onSubscribe(Disposable d) {

                }

                @Override
                public void onNext(GenericOverall genericOverall) {
                  hasPassword = !TextUtils.isEmpty(value);
                  conversationInfoInterface.passwordSet(TextUtils.isEmpty(value));
                }

                @Override
                public void onError(Throwable e) {

                }

                @Override
                public void onComplete() {
                }
              });
        } else {
          hasPassword = Boolean.parseBoolean(value);
        }
      } else if (key.equals("conversation_name")) {
        if (!TextUtils.isEmpty(conversationNameValue) && !conversationNameValue.equals(value)) {
          ncApi.renameRoom(ApiUtils.getCredentials(conversationUser.getUsername(),
              conversationUser.getToken()), ApiUtils.getRoom(conversationUser.getBaseUrl(),
              conversationToken), value)
              .subscribeOn(Schedulers.io())
              .subscribe(new Observer<GenericOverall>() {
                @Override
                public void onSubscribe(Disposable d) {

                }

                @Override
                public void onNext(GenericOverall genericOverall) {
                  conversationNameValue = value;
                  conversationInfoInterface.conversationNameSet(value);
                }

                @Override
                public void onError(Throwable e) {

                }

                @Override
                public void onComplete() {
                }
              });
        } else {
          conversationNameValue = value;
        }
      }
    }
  }

  @Override
  public void saveInt(String key, int value) {
    arbitraryStorageUtils.storeStorageSetting(accountIdentifier, key, Integer.toString(value),
        conversationToken);
  }

  @Override
  public void saveStringSet(String key, Set<String> value) {

  }

  @Override
  public boolean getBoolean(String key, boolean defaultVal) {
    if (key.equals("conversation_lobby")) {
      return lobbyValue;
    } else if (key.equals("allow_guests")) {
      return allowGuestsValue;
    } else if (key.equals("favorite_conversation")) {
      return favoriteConversationValue;
    } else {
      ArbitraryStorageEntity valueFromDb =
          arbitraryStorageUtils.getStorageSetting(accountIdentifier, key, conversationToken);
      if (valueFromDb == null) {
        return defaultVal;
      } else {
        return Boolean.parseBoolean(valueFromDb.getValue());
      }
    }
  }

  @Override
  public String getString(String key, String defaultVal) {
    if (!key.equals("message_notification_level")
        && !key.equals("conversation_name")
        && !key.equals("conversation_password")) {
      ArbitraryStorageEntity valueFromDb =
          arbitraryStorageUtils.getStorageSetting(accountIdentifier, key, conversationToken);
      if (valueFromDb == null) {
        return defaultVal;
      } else {
        return valueFromDb.getValue();
      }
    } else if (key.equals("message_notification_level")) {
      return messageNotificationLevel;
    } else if (key.equals("conversation_name")) {
      return conversationNameValue;
    } else if (key.equals("conversation_password")) {
      return "";
    }

    return "";
  }

  @Override
  public int getInt(String key, int defaultVal) {
    ArbitraryStorageEntity valueFromDb =
        arbitraryStorageUtils.getStorageSetting(accountIdentifier, key, conversationToken);
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
