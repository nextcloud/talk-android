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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


public class ClosedInterfaceImpl implements ClosedInterface {
    @Override
    public void providerInstallerInstallIfNeededAsync() {
        // does absolutely nothing :)
    }

    @Override
    public boolean isPushMessagingServiceAvailable(Context context) {
        return false;
    }

    @Override
    public String pushMessagingProvider() {
        return "qa";
    }

    @Override
    public boolean registerWithServer(Context context, @Nullable String username) {
        // no push notifications for qa build flavour :(
        return false;
    }

    @Override
    public void unregisterWithServer(@NonNull Context context, @Nullable String username) {
        // no push notifications for qa build flavour :(
    }
}
