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
import okhttp3.Credentials
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
        const val ONE_TIME_PREFIX = "nc://onetime-login/"
        private const val MAX_ARGS = 3
    }

    /**
     * Result of [parseAndLogin].
     */
    sealed interface LoginResult {
        /** A new account, to be verified and stored with the data in [bundle]. */
        data class NewAccount(val bundle: Bundle) : LoginResult

        /** An existing account, reauthorized or not, or an account that is being removed. */
        data object ExistingAccount : LoginResult

        /** A reauthorization in which a different account than the one to reauthorize logged in. */
        data object DifferentAccount : LoginResult
    }

    private var shouldReauthorizeUser = false
    private var accountToReauthorize: Long? = null
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
    fun startLoginFlowFromQR(
        dataString: String,
        reAuth: Boolean = false,
        accountToReauthorize: Long? = null
    ): LoginCompletion? {
        shouldReauthorizeUser = reAuth
        this.accountToReauthorize = accountToReauthorize

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
     * Entry point for a one-time QR code
     */
    suspend fun startOTPLoginFlow(
        dataString: String,
        reAuth: Boolean = false,
        accountToReauthorize: Long? = null
    ): LoginCompletion? =
        withContext(Dispatchers.IO) {
            shouldReauthorizeUser = reAuth
            this@LoginRepository.accountToReauthorize = accountToReauthorize

            if (!dataString.startsWith(ONE_TIME_PREFIX)) {
                Log.e(TAG, "Invalid login URL detected")
                return@withContext null
            }

            val data = dataString.removePrefix(ONE_TIME_PREFIX)
            val values = data.split('&')

            if (values.size !in 1..MAX_ARGS) {
                Log.e(TAG, "Illegal number of login URL elements detected: ${values.size}")
                return@withContext null
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

            // Need to use the qr code token to create temporary credentials to get access to the actual app password
            val credentials = Credentials.basic(loginName, appPassword)
            val oneTimePassword = network.oneTimePasswordRequest(server, credentials)

            return@withContext if (server.isNotEmpty() && loginName.isNotEmpty() && oneTimePassword != null) {
                LoginCompletion(HTTP_OK, server, loginName, oneTimePassword)
            } else {
                null
            }
        }

    /**
     * Entry point to the login process
     */
    suspend fun startLoginFlow(
        baseUrl: String,
        reAuth: Boolean = false,
        accountToReauthorize: Long? = null
    ): LoginResponse? =
        withContext(Dispatchers.IO) {
            shouldReauthorizeUser = reAuth
            this@LoginRepository.accountToReauthorize = accountToReauthorize
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
     * Returns [LoginResult.NewAccount] if the account is not scheduled for deletion and doesn't exist yet. During a
     * reauthorization, [LoginResult.DifferentAccount] if another account than the one to reauthorize logged in, set
     * up or not, which is then left unchanged.
     */
    suspend fun parseAndLogin(loginData: LoginCompletion): LoginResult {
        if (local.checkIfUserIsScheduledForDeletion(loginData)) {
            // however the user is not yet deleted, just start AccountRemovalWorker again to make sure to delete it.
            local.startAccountRemovalWorker()
            return LoginResult.ExistingAccount
        } else if (local.checkIfUserExists(loginData)) {
            if (shouldReauthorizeUser) {
                if (!local.updateUser(loginData, accountToReauthorize)) {
                    Log.w(TAG, "Logged in with a different account than the one to reauthorize. Skipped update.")
                    return LoginResult.DifferentAccount
                }
            } else {
                Log.w(TAG, "Tried to add an account that account already exists. Skipped user creation.")
            }

            return LoginResult.ExistingAccount
        } else if (shouldReauthorizeUser) {
            // A reauthorization must not add the account that logged in instead.
            Log.w(TAG, "Logged in with an account that is not set up during a reauthorization. Skipped user creation.")
            return LoginResult.DifferentAccount
        } else {
            return LoginResult.NewAccount(startAccountVerification(loginData))
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
