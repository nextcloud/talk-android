/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils;


import com.nextcloud.talk.interfaces.ClosedInterface;

public class ClosedInterfaceImpl implements ClosedInterface {
    @Override
    public void providerInstallerInstallIfNeededAsync() {
        // does absolutely nothing :)
    }

    @Override
    public boolean isGooglePlayServicesAvailable() {
        return false;
    }

    @Override
    public void setUpPushTokenRegistration() {
        // no push notifications for generic build variant
    }
}
