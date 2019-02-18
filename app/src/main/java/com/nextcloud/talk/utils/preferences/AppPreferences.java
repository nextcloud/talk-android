/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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

package com.nextcloud.talk.utils.preferences;

import com.nextcloud.talk.R;
import net.orange_box.storebox.annotations.method.*;
import net.orange_box.storebox.annotations.option.SaveOption;
import net.orange_box.storebox.enums.SaveMode;
import net.orange_box.storebox.listeners.OnPreferenceValueChangedListener;

@SaveOption(SaveMode.APPLY)
public interface AppPreferences {

    @KeyByString("proxy_type")
    @RegisterChangeListenerMethod
    void registerProxyTypeListener(OnPreferenceValueChangedListener<String> listener);

    @KeyByString("proxy_type")
    @UnregisterChangeListenerMethod
    void unregisterProxyTypeListener(OnPreferenceValueChangedListener<String> listener);

    @KeyByString("proxy_type")
    String getProxyType();

    @KeyByString("proxy_type")
    void setProxyType(String proxyType);

    @KeyByString("proxy_server")
    @RemoveMethod
    void removeProxyType();

    @KeyByString("proxy_host")
    String getProxyHost();

    @KeyByString("proxy_host")
    void setProxyHost(String proxyHost);

    @KeyByString("proxy_host")
    @RemoveMethod
    void removeProxyHost();

    @KeyByString("proxy_port")
    String getProxyPort();

    @KeyByString("proxy_port")
    void setProxyPort(String proxyPort);

    @KeyByString("proxy_port")
    @RemoveMethod
    void removeProxyPort();

    @KeyByString("proxy_credentials")
    @RegisterChangeListenerMethod
    void registerProxyCredentialsListener(OnPreferenceValueChangedListener<Boolean> listener);

    @KeyByString("proxy_credentials")
    @UnregisterChangeListenerMethod
    void unregisterProxyCredentialsListener(OnPreferenceValueChangedListener<Boolean> listener);

    @KeyByString("proxy_credentials")
    boolean getProxyCredentials();

    @KeyByString("proxy_credentials")
    void setProxyNeedsCredentials(boolean proxyNeedsCredentials);

    @KeyByString("proxy_credentials")
    @RemoveMethod
    void removeProxyCredentials();

    @KeyByString("proxy_username")
    String getProxyUsername();

    @KeyByString("proxy_username")
    void setProxyUsername(String proxyUsername);

    @KeyByString("proxy_username")
    @RemoveMethod
    void removeProxyUsername();

    @KeyByString("proxy_password")
    String getProxyPassword();

    @KeyByString("proxy_password")
    void setProxyPassword(String proxyPassword);

    @KeyByString("proxy_password")
    @RemoveMethod
    void removeProxyPassword();

    @KeyByString("push_token")
    String getPushToken();

    @KeyByString("push_token")
    void setPushToken(String pushToken);

    @KeyByString("push_token")
    @RemoveMethod
    void removePushToken();

    @KeyByString("tempClientCertAlias")
    String getTemporaryClientCertAlias();

    @KeyByString("tempClientCertAlias")
    void setTemporaryClientCertAlias(String alias);

    @KeyByString("tempClientCertAlias")
    @RemoveMethod
    void removeTemporaryClientCertAlias();

    @KeyByString("pushToTalk_intro_shown")
    boolean getPushToTalkIntroShown();

    @KeyByString("pushToTalk_intro_shown")
    void setPushToTalkIntroShown(boolean shown);

    @KeyByString("pushToTalk_intro_shown")
    @RemoveMethod
    void removePushToTalkIntroShown();

    @KeyByString("call_ringtone")
    String getCallRingtoneUri();

    @KeyByString("call_ringtone")
    void setCallRingtoneUri(String value);

    @KeyByString("call_ringtone")
    @RemoveMethod
    void removeCallRingtoneUri();

    @KeyByString("message_ringtone")
    String getMessageRingtoneUri();

    @KeyByString("message_ringtone")
    void setMessageRingtoneUri(String value);

    @KeyByString("message_ringtone")
    @RemoveMethod
    void removeMessageRingtoneUri();

    @KeyByString("notification_channels_upgrade_to_v2")
    boolean getIsNotificationChannelUpgradedToV2();

    @KeyByString("notification_channels_upgrade_to_v2")
    void setNotificationChannelIsUpgradedToV2(boolean value);

    @KeyByString("notification_channels_upgrade_to_v2")
    @RemoveMethod
    void removeNotificationChannelUpgradeToV2();

    @KeyByString("notification_channels_upgrade_to_v3")
    boolean getIsNotificationChannelUpgradedToV3();

    @KeyByString("notification_channels_upgrade_to_v3")
    void setNotificationChannelIsUpgradedToV3(boolean value);

    @KeyByString("notification_channels_upgrade_to_v3")
    @RemoveMethod
    void removeNotificationChannelUpgradeToV3();

    @KeyByString("notifications_vibrate")
    @DefaultValue(R.bool.value_true)
    boolean getShouldVibrateSetting();

    @KeyByString("notifications_vibrate")
    void setVibrateSetting(boolean value);

    @KeyByString("notifications_vibrate")
    @RemoveMethod
    void removeVibrateSetting();

    @KeyByString("screen_security")
    @DefaultValue(R.bool.value_false)
    boolean getIsScreenSecured();

    @KeyByString("screen_security")
    void setScreenSecurity(boolean value);

    @KeyByString("screen_security")
    @RemoveMethod
    void removeScreenSecurity();

    @KeyByString("screen_security")
    @RegisterChangeListenerMethod
    void registerScreenSecurityListener(OnPreferenceValueChangedListener<Boolean> listener);

    @KeyByString("screen_security")
    @UnregisterChangeListenerMethod
    void unregisterScreenSecurityListener(OnPreferenceValueChangedListener<Boolean> listener);

    @KeyByString("screen_lock")
    @DefaultValue(R.bool.value_false)
    boolean getIsScreenLocked();

    @KeyByString("screen_lock")
    void setScreenLock(boolean value);

    @KeyByString("screen_lock")
    @RemoveMethod
    void removeScreenLock();

    @KeyByString("screen_lock")
    @RegisterChangeListenerMethod
    void registerScreenLockListener(OnPreferenceValueChangedListener<Boolean> listener);

    @KeyByString("screen_lock")
    @UnregisterChangeListenerMethod
    void unregisterScreenLockListener(OnPreferenceValueChangedListener<Boolean> listener);

    @KeyByString("incognito_keyboard")
    @DefaultValue(R.bool.value_true)
    boolean getIsKeyboardIncognito();

    @KeyByString("incognito_keyboard")
    void setIncognitoKeyboard(boolean value);

    @KeyByString("incognito_keyboard")
    @RemoveMethod
    void removeIncognitoKeyboard();

    @KeyByString("link_previews")
    @DefaultValue(R.bool.value_true)
    boolean getAreLinkPreviewsAllowed();

    @KeyByString("link_previews")
    void setLinkPreviewsAllowed(boolean value);

    @KeyByString("link_previews")
    @RemoveMethod
    void removeLinkPreviews();

    @KeyByString("screen_lock_timeout")
    @DefaultValue(R.string.nc_screen_lock_timeout_sixty)
    String getScreenLockTimeout();

    @KeyByString("screen_lock_timeout")
    void setScreenLockTimeout(int value);

    @KeyByString("screen_lock_timeout")
    @RemoveMethod
    void removeScreenLockTimeout();

    @KeyByString("screen_lock_timeout")
    @RegisterChangeListenerMethod
    void registerScreenLockTimeoutListener(OnPreferenceValueChangedListener<String> listener);

    @KeyByString("screen_lock_timeout")
    @UnregisterChangeListenerMethod
    void unregisterScreenLockTimeoutListener(OnPreferenceValueChangedListener<String> listener);


    @ClearMethod
    void clear();
}
