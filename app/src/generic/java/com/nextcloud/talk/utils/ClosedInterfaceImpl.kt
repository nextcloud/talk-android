/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils;


import android.content.Context;
import com.nextcloud.talk.interfaces.ClosedInterface;
import com.nextcloud.talk.receivers.UnifiedPush;
import androidx.annotation.NonNull;


public class ClosedInterfaceImpl implements ClosedInterface {
    @Override
    public void providerInstallerInstallIfNeededAsync() {
        // does absolutely nothing :)
    }

    @Override
    public boolean isPushMessagingServiceAvailable(Context context) {
        return (UnifiedPush.Companion.getNumberOfDistributorsAvailable(context) > 0);
    }

    @Override
    public String pushMessagingProvider() {
        return "unifiedpush";
    }

    @Override
    public boolean registerWithServer(@NonNull Context context, String username) {
        // unified push available in generic build
        if (username == null)
            return false;
        return UnifiedPush.Companion.registerForPushMessaging(context, username);
    }

    @NonNull
    @Override
    public void unregisterWithServer(@NonNull Context context, @NonNull String username) {
        // unified push available in generic build
        if (username == null)
            return;
        UnifiedPush.Companion.unregisterForPushMessaging(context, username);
    }
}
