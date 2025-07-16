/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Christian Reiner <foss@christian-reiner.info>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.preferences;

import android.annotation.SuppressLint;

import com.nextcloud.talk.ui.PlaybackSpeed;

import kotlin.Pair;

@SuppressLint("NonConstantResourceId")
public interface AppPreferences {

    String getProxyType();

    void setProxyType(String proxyType);

    void removeProxyType();

    String getProxyHost();

    void setProxyHost(String proxyHost);

    void removeProxyHost();

    String getProxyPort();

    void setProxyPort(String proxyPort);

    void removeProxyPort();

    boolean getProxyCredentials();

    void setProxyNeedsCredentials(boolean proxyNeedsCredentials);

    void removeProxyCredentials();

    String getProxyUsername();

    void setProxyUsername(String proxyUsername);

    void removeProxyUsername();

    String getProxyPassword();

    void setProxyPassword(String proxyPassword);

    void removeProxyPassword();

    String getPushToken();

    void setPushToken(String pushToken);

    Long getPushTokenLatestGeneration();

    void setPushTokenLatestGeneration(Long date);

    Long getPushTokenLatestFetch();

    void setPushTokenLatestFetch(Long date);

    void removePushToken();

    String getTemporaryClientCertAlias();

    void setTemporaryClientCertAlias(String alias);

    void removeTemporaryClientCertAlias();

    boolean getPushToTalkIntroShown();

    void setPushToTalkIntroShown(boolean shown);

    void removePushToTalkIntroShown();

    String getCallRingtoneUri();

    void setCallRingtoneUri(String value);

    void removeCallRingtoneUri();

    String getMessageRingtoneUri();

    void setMessageRingtoneUri(String value);

    void removeMessageRingtoneUri();

    boolean getIsNotificationChannelUpgradedToV2();

    void setNotificationChannelIsUpgradedToV2(boolean value);

    void removeNotificationChannelUpgradeToV2();

    boolean getIsNotificationChannelUpgradedToV3();

    void setNotificationChannelIsUpgradedToV3(boolean value);

    void removeNotificationChannelUpgradeToV3();

    boolean getIsScreenSecured();

    void setScreenSecurity(boolean value);

    void removeScreenSecurity();

    boolean getIsScreenLocked();

    void setScreenLock(boolean value);

    void removeScreenLock();

    boolean getIsKeyboardIncognito();

    void setIncognitoKeyboard(boolean value);

    void removeIncognitoKeyboard();

    boolean isPhoneBookIntegrationEnabled();

    void setPhoneBookIntegration(boolean value);

    // TODO Remove in 13.0.0
    void removeLinkPreviews();

    String getScreenLockTimeout();

    void setScreenLockTimeout(String value);

    void removeScreenLockTimeout();

    String getTheme();

    void setTheme(String newValue);

    void removeTheme();

    boolean isDbCypherToUpgrade();

    void setDbCypherToUpgrade(boolean value);

    void setPhoneBookIntegrationLastRun(long currentTimeMillis);

    long getPhoneBookIntegrationLastRun(Long defaultValue);

    void setReadPrivacy(boolean value);

    boolean getReadPrivacy();

    void setTypingStatus(boolean value);

    boolean getTypingStatus();

    void setSorting(String value);

    String getSorting();

    void saveWaveFormForFile(String filename, Float[] array);

    Float[] getWaveFormFromFile(String filename);

    void saveLastKnownId(String internalConversationId, int lastReadId);

    int getLastKnownId(String internalConversationId, int defaultValue);

    void deleteAllMessageQueuesFor(String userId);

    void savePreferredPlayback(String userId, PlaybackSpeed speed);

    PlaybackSpeed getPreferredPlayback(String userId);

    Long getNotificationWarningLastPostponedDate();

    void setNotificationWarningLastPostponedDate(Long showNotificationWarning);

    Boolean getShowRegularNotificationWarning();

    void setShowRegularNotificationWarning(boolean value);

    void setConversationListPositionAndOffset(int position, int offset);

    Pair<Integer, Integer> getConversationListPositionAndOffset();

    void clear();
}
