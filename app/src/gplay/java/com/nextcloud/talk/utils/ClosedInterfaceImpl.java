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

package com.nextcloud.talk.utils;


import android.content.Intent;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.security.ProviderInstaller;
import com.google.android.gms.security.ProviderInstaller.ProviderInstallListener;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.interfaces.ClosedInterface;

public class ClosedInterfaceImpl implements ClosedInterface, ProviderInstallListener {
    @Override
    public void providerInstallerInstallIfNeededAsync() {
        ProviderInstaller.installIfNeededAsync(NextcloudTalkApplication.getSharedApplication().getApplicationContext(),
                this);
    }

    @Override
    public boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        int code = api.isGooglePlayServicesAvailable(NextcloudTalkApplication.getSharedApplication().getApplicationContext());
        return code == ConnectionResult.SUCCESS;
    }

    @Override
    public void onProviderInstalled() {

    }

    @Override
    public void onProviderInstallFailed(int i, Intent intent) {

    }
}
