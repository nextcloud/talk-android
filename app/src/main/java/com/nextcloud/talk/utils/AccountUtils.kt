/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 David Luhmer
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Parts related to account import were either copied from or inspired by the great work done by David Luhmer at:
 * https://github.com/nextcloud/ownCloud-Account-Importer
 */
package com.nextcloud.talk.utils

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.ImportAccount
import java.util.Arrays

object AccountUtils {

    private const val TAG = "AccountUtils"
    private const val MIN_SUPPORTED_FILES_APP_VERSION = 30060151

    fun findAvailableAccountsOnDevice(users: List<User>): List<Account> {
        val context = NextcloudTalkApplication.sharedApplication!!.applicationContext
        val accMgr = AccountManager.get(context)
        val accounts = accMgr.getAccountsByType(context.getString(R.string.nc_import_account_type))

        val accountsAvailable = ArrayList<Account>()
        var accountFound: Boolean
        for (account in accounts) {
            accountFound = false

            for (user in users) {
                if (matchAccounts(getInformationFromAccount(account), user)) {
                    accountFound = true
                    break
                }
            }

            if (!accountFound) {
                accountsAvailable.add(account)
            }
        }

        return accountsAvailable
    }

    private fun matchAccounts(importAccount: ImportAccount, user: User): Boolean {
        var accountFound = false
        if (importAccount.token != null) {
            if (UriUtils.hasHttpProtocolPrefixed(importAccount.baseUrl!!)) {
                if (
                    user.username == importAccount.username &&
                    user.baseUrl == importAccount.baseUrl
                ) {
                    accountFound = true
                }
            } else {
                if (user.username == importAccount.username &&
                    (
                        user.baseUrl == "http://" + importAccount.baseUrl ||
                            user.baseUrl == "https://" + importAccount.baseUrl
                        )
                ) {
                    accountFound = true
                }
            }
        } else {
            accountFound = true
        }

        return accountFound
    }

    fun getAppNameBasedOnPackage(packageName: String): String {
        val context = NextcloudTalkApplication.sharedApplication!!.applicationContext
        val packageManager = context.packageManager
        var appName = ""
        try {
            appName = packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.GET_META_DATA
                )
            ) as String
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get app name based on package")
        }

        return appName
    }

    fun canWeOpenFilesApp(context: Context, accountName: String): Boolean {
        val pm = context.packageManager
        try {
            val packageInfo = pm.getPackageInfo(context.getString(R.string.nc_import_accounts_from), 0)
            if (packageInfo.versionCode >= MIN_SUPPORTED_FILES_APP_VERSION) {
                val ownSignatures = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
                val filesAppSignatures = pm.getPackageInfo(
                    context.getString(R.string.nc_import_accounts_from),
                    PackageManager.GET_SIGNATURES
                ).signatures

                return if (Arrays.equals(ownSignatures, filesAppSignatures)) {
                    val accMgr = AccountManager.get(context)
                    val accounts = accMgr.getAccountsByType(context.getString(R.string.nc_import_account_type))
                    accounts.any { it.name == accountName }
                } else {
                    true
                }
            }
        } catch (appNotFoundException: PackageManager.NameNotFoundException) {
            // ignore
        }

        return false
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun getInformationFromAccount(account: Account): ImportAccount {
        val lastAtPos = account.name.lastIndexOf("@")
        var urlString = account.name.substring(lastAtPos + 1)
        val username = account.name.substring(0, lastAtPos)

        val context = NextcloudTalkApplication.sharedApplication!!.applicationContext
        val accMgr = AccountManager.get(context)

        var password: String? = null
        try {
            password = accMgr.getPassword(account)
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to import account")
        }

        if (urlString.endsWith("/")) {
            urlString = urlString.substring(0, urlString.length - 1)
        }

        return ImportAccount(username, password, urlString)
    }
}
