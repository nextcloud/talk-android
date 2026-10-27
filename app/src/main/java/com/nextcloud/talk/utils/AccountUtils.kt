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
import com.nextcloud.android.common.core.utils.AccountNameUtils
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.ImportAccount
import java.util.Arrays

object AccountUtils {

    private val TAG = AccountUtils::class.java.simpleName
    private const val MIN_SUPPORTED_FILES_APP_VERSION = 30060151
    private const val FILES_KEY_USER_ID = "oc_id"
    private const val FILES_KEY_BASE_URL = "oc_base_url"

    fun findAvailableAccountsOnDevice(users: List<User>): List<Account> {
        val context = NextcloudTalkApplication.sharedApplication!!.applicationContext
        val accMgr = AccountManager.get(context)
        val accounts = accMgr.getAccountsByType(context.getString(R.string.nc_import_account_type))

        return accounts.filter { account ->
            val canImport = users.isEmpty() || getInformationFromAccount(account).token != null
            canImport && users.none { accMgr.isSameAccount(account, it) }
        }
    }

    fun findFilesAccountName(context: Context, user: User): String? {
        val accMgr = AccountManager.get(context)
        return accMgr.getAccountsByType(context.getString(R.string.nc_import_account_type))
            .firstOrNull { accMgr.isSameAccount(it, user) }
            ?.name
    }

    fun findUserForFilesAccount(context: Context, accountName: String, users: List<User>): User? {
        val accountType = context.getString(R.string.nc_import_account_type)
        val accMgr = AccountManager.get(context)
        val account = accMgr.getAccountsByType(accountType).firstOrNull { it.name == accountName }
            ?: Account(accountName, accountType)
        return users.firstOrNull { accMgr.isSameAccount(account, it) }
    }

    fun buildAccountName(user: User): String? {
        val username = user.username
        val baseUrl = user.baseUrl
        if (username == null || baseUrl == null) {
            return null
        }

        return AccountNameUtils.build(username, AccountNameUtils.removeScheme(baseUrl))
    }

    private fun AccountManager.isSameAccount(account: Account, user: User): Boolean {
        val userBaseUrl = user.baseUrl ?: return false
        val filesBaseUrl = getUserDataOrNull(account, FILES_KEY_BASE_URL)
            ?: AccountNameUtils.getServerPart(account.name)
        val filesUserId = getUserDataOrNull(account, FILES_KEY_USER_ID)
        val userId = user.userId

        return if (filesUserId != null && userId != null) {
            AccountNameUtils.isSameAccount(filesBaseUrl, filesUserId, userBaseUrl, userId)
        } else {
            AccountNameUtils.isSameServer(filesBaseUrl, userBaseUrl) &&
                AccountNameUtils.getUserPart(account.name).equals(user.username, ignoreCase = true)
        }
    }

    private fun AccountManager.getUserDataOrNull(account: Account, key: String): String? =
        runCatching { getUserData(account, key) }.getOrNull()

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

    fun resolveFilesAccountName(context: Context, user: User): String? {
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
                    findFilesAccountName(context, user)
                } else {
                    buildAccountName(user)
                }
            }
        } catch (appNotFoundException: PackageManager.NameNotFoundException) {
            NextcloudTalkApplication.sharedApplication?.logger?.w(
                TAG,
                "Files app not found while checking its signature",
                appNotFoundException
            )
        }

        return null
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
