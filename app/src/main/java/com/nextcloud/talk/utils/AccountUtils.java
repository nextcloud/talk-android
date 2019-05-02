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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.ImportAccount;
import com.nextcloud.talk.models.database.UserEntity;

import java.util.ArrayList;
import java.util.List;

public class AccountUtils {

    private static final String TAG = "AccountUtils";

    public static List<Account> findAccounts(List<UserEntity> userEntitiesList) {
        Context context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
        final AccountManager accMgr = AccountManager.get(context);
        final Account[] accounts = accMgr.getAccountsByType(context.getString(R.string.nc_import_account_type));

        List<Account> accountsAvailable = new ArrayList<>();
        ImportAccount importAccount;
        UserEntity internalUserEntity;
        boolean accountFound;
        for (Account account : accounts) {
            accountFound = false;

            for (int i = 0; i < userEntitiesList.size(); i++) {
                internalUserEntity = userEntitiesList.get(i);
                importAccount = getInformationFromAccount(account);
                if (importAccount.getToken() != null) {
                    if (importAccount.getBaseUrl().startsWith("http://") ||
                            importAccount.getBaseUrl().startsWith("https://")) {
                        if (internalUserEntity.getUsername().equals(importAccount.getUsername()) &&
                                internalUserEntity.getBaseUrl().equals(importAccount.getBaseUrl())) {
                            accountFound = true;
                            break;
                        }
                    } else {
                        if (internalUserEntity.getUsername().equals(importAccount.getUsername()) &&
                                (internalUserEntity.getBaseUrl().equals("http://" + importAccount.getBaseUrl()) ||
                                        internalUserEntity.getBaseUrl().equals("https://" +
                                                importAccount.getBaseUrl()))) {
                            accountFound = true;
                            break;
                        }

                    }
                } else {
                    accountFound = true;
                    break;
                }
            }

            if (!accountFound) {
                accountsAvailable.add(account);
            }
        }

        return accountsAvailable;
    }

    public static String getAppNameBasedOnPackage(String packageName) {
        Context context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
        PackageManager packageManager = context.getPackageManager();
        String appName = "";
        try {
            appName = (String) packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName,
                    PackageManager.GET_META_DATA));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to get app name based on package");
        }

        return appName;
    }

    public static boolean canWeOpenFilesApp(Context context, String accountName) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo packageInfo =
                    pm.getPackageInfo(context.getString(R.string.nc_import_accounts_from), 0);
            if (packageInfo.versionCode >= 30060151) {
                final AccountManager accMgr = AccountManager.get(context);
                final Account[] accounts = accMgr.getAccountsByType(context.getString(R.string.nc_import_account_type));
                for (Account account : accounts) {
                    if (account.name.equals(accountName)) {
                        return true;
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException appNotFoundException) {

        }
        return false;
    }

    public static ImportAccount getInformationFromAccount(Account account) {
        int lastAtPos = account.name.lastIndexOf("@");
        String urlString = account.name.substring(lastAtPos + 1);
        String username = account.name.substring(0, lastAtPos);

        Context context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
        final AccountManager accMgr = AccountManager.get(context);

        String password = null;
        try {
            password = accMgr.getPassword(account);
        } catch (Exception exception) {
            Log.e(TAG, "Failed to import account");
        }

        if (urlString.endsWith("/")) {
            urlString = urlString.substring(0, urlString.length() - 1);
        }

        return new ImportAccount(username, password, urlString);
    }
}

