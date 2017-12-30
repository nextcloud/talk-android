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
 *
 * Parts related to account import were either copied from or inspired by the great work done by David Luhmer at:
 * https://github.com/nextcloud/ownCloud-Account-Importer
 */

package com.nextcloud.talk.utils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.ImportAccount;
import com.nextcloud.talk.persistence.entities.UserEntity;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class AccountUtils {

    private static final String TAG = "AccountUtils";

    public static List<Account> findAccounts(List<UserEntity> userEntitiesList) {
        Context context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
        final AccountManager accMgr = AccountManager.get(context);
        final Account[] accounts = accMgr.getAccounts();

        List<Account> accountsAvailable = new ArrayList<>();
        ImportAccount importAccount;
        UserEntity internalUserEntity;
        boolean accountFound;
        for (Account account : accounts) {
            accountFound = false;
            String accountType = account.type.intern();

            if (context.getResources().getString(R.string.nc_import_account_type).equals(accountType)) {
                for (int i = 0; i < userEntitiesList.size(); i++) {
                    internalUserEntity = userEntitiesList.get(i);
                    importAccount = getInformationFromAccount(account, null);
                    if (internalUserEntity.getUsername().equals(importAccount.getUsername()) &&
                            internalUserEntity.getBaseUrl().equals(importAccount.getBaseUrl())) {
                        accountFound = true;
                        break;
                    }
                }

                if (!accountFound) {
                    accountsAvailable.add(account);
                }
            }
        }

        return accountsAvailable;
    }

    public static String getAppNameBasedOnPackage(String packageName) {
        Context context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
        PackageManager packageManager = context.getPackageManager();
        String appName = "";
        try {
            appName = (String) packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get app name based on package");
        }

        return appName;
    }

    public static ImportAccount getInformationFromAccount(Account account, @Nullable Bundle data) {
        int lastAtPos = account.name.lastIndexOf("@");
        String urlString = account.name.substring(lastAtPos + 1);
        String username = account.name.substring(0, lastAtPos);

        if (!urlString.startsWith("http")) {
            urlString = "http://" + urlString;
        }

        String password = null;

        if (data != null) {
            password = data.getString(AccountManager.KEY_AUTHTOKEN);
        }

        try {
            final String urlStringOrig = urlString;
            URL url = new URL(urlStringOrig);
            urlString = url.getProtocol() + "://" + url.getHost();
            if (url.getPath().contains("/owncloud")) {
                urlString += url.getPath().substring(0, url.getPath().indexOf("/owncloud") + 9);
            } else if (url.getPath().contains("/nextcloud")) {
                urlString += url.getPath().substring(0, url.getPath().indexOf("/nextcloud") + 10);
            } else if (url.getPath().contains("/")) {
                urlString += url.getPath().substring(0, url.getPath().indexOf("/"));
            }
        } catch (Exception ex) {
            Log.e(TAG, "Something went wrong while trying to create url string");
        }

        return new ImportAccount(username, password, urlString);
    }
}

