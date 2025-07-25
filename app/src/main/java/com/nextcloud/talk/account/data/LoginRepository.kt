/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.data

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import com.nextcloud.talk.account.data.io.LocalLoginDataSource
import com.nextcloud.talk.account.data.model.LoginCompletion
import com.nextcloud.talk.account.data.model.LoginResponse
import com.nextcloud.talk.account.data.network.NetworkLoginDataSource
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ORIGINAL_PROTOCOL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USERNAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLDecoder

@Suppress("TooManyFunctions", "ReturnCount")
class LoginRepository(val network: NetworkLoginDataSource, val local: LocalLoginDataSource) {

    companion object {
        val TAG: String = LoginRepository::class.java.simpleName
        private const val INTERVAL = 250L
        private const val HTTP_OK = 200
        private const val USER_KEY = "user:"
        private const val SERVER_KEY = "server:"
        private const val PASS_KEY = "password:"
        private const val PREFIX = "nc://login/"
        private const val MAX_ARGS = 3
    }

    private var shouldReauthorizeUser = false
    private var shouldLoop = true

    suspend fun pollLogin(response: LoginResponse): LoginCompletion? =
        withContext(Dispatchers.IO) {
            while (shouldLoop) {
                val loginData = network.performLoginFlowV2(response)
                if (loginData == null) {
                    break
                }

                if (loginData.status == HTTP_OK) {
                    return@withContext loginData
                }

                delay(INTERVAL) // No response yet, retry
            }
            return@withContext null
        }

    /**
     * Entry point for QR scanner
     *
     */
    fun startLoginFlowFromQR(dataString: String, reAuth: Boolean = false): LoginCompletion? {
        shouldReauthorizeUser = reAuth

        if (!dataString.startsWith(PREFIX)) {
            Log.e(TAG, "Invalid login URL detected")
            return null
        }

        val data = dataString.removePrefix(PREFIX)
        val values = data.split('&')

        if (values.size !in 1..MAX_ARGS) {
            Log.e(TAG, "Illegal number of login URL elements detected: ${values.size}")
            return null
        }

        var server = ""
        var loginName = ""
        var appPassword = ""
        values.forEach { value ->
            when {
                value.startsWith(USER_KEY) -> {
                    loginName = URLDecoder.decode(value.removePrefix(USER_KEY), "UTF-8")
                }

                value.startsWith(PASS_KEY) -> {
                    appPassword = URLDecoder.decode(value.removePrefix(PASS_KEY), "UTF-8")
                }

                value.startsWith(SERVER_KEY) -> {
                    server = URLDecoder.decode(value.removePrefix(SERVER_KEY), "UTF-8")
                }
            }
        }

        return if (server.isNotEmpty() && loginName.isNotEmpty() && appPassword.isNotEmpty()) {
            LoginCompletion(HTTP_OK, server, loginName, appPassword)
        } else {
            null
        }
    }

    /**
     * Entry point to the login process
     */
    suspend fun startLoginFlow(baseUrl: String, reAuth: Boolean = false): LoginResponse? =
        withContext(Dispatchers.IO) {
            shouldReauthorizeUser = reAuth
            val response = network.anonymouslyPostLoginRequest(baseUrl)
            return@withContext response
        }

    /**
     * Ends normal login process by canceling the polling
     */
    fun cancelLoginFlow() {
        shouldLoop = false
    }

    /**
     * Returns bundle if user is not scheduled for deletion or doesn't already exist, null otherwise
     */
    fun parseAndLogin(loginData: LoginCompletion): Bundle? {
        if (local.checkIfUserIsScheduledForDeletion(loginData)) {
            // however the user is not yet deleted, just start AccountRemovalWorker again to make sure to delete it.
            local.startAccountRemovalWorker()
            return null
        } else if (local.checkIfUserExists(loginData)) {
            if (shouldReauthorizeUser) {
                local.updateUser(loginData)
            } else {
                Log.w(TAG, "Tried to add an account that account already exists. Skipped user creation.")
            }

            return null
        } else {
            return startAccountVerification(loginData)
        }
    }

    private fun startAccountVerification(loginData: LoginCompletion): Bundle {
        val bundle = Bundle()
        bundle.putString(KEY_USERNAME, loginData.loginName)
        bundle.putString(KEY_TOKEN, loginData.appPassword)
        bundle.putString(KEY_BASE_URL, loginData.server)
        var protocol = ""
        if (loginData.server.startsWith("http://")) {
            protocol = "http://"
        } else if (loginData.server.startsWith("https://")) {
            protocol = "https://"
        }
        if (!TextUtils.isEmpty(protocol)) {
            bundle.putString(KEY_ORIGINAL_PROTOCOL, protocol)
        }

        return bundle
    }
}
