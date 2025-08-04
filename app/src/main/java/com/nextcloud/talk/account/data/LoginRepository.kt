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
import androidx.work.WorkInfo
import com.nextcloud.talk.account.data.io.LocalLoginDataSource
import com.nextcloud.talk.account.data.network.NetworkLoginDataSource
import com.nextcloud.talk.account.data.network.NetworkLoginDataSource.LoginCompletion
import com.nextcloud.talk.account.data.network.NetworkLoginDataSource.LoginResponse
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ORIGINAL_PROTOCOL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USERNAME
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder

// this handles communication with the network datasource and the User manager (abstraction over room)
class LoginRepository(
    val network: NetworkLoginDataSource,
    val local: LocalLoginDataSource
) {

    companion object {
        val TAG: String = LoginRepository::class.java.simpleName
        const val START_LOGIN_FLOW = "startLoginFlow"
        const val PARSE_LOGIN = "parseAndLogin"
        private const val INTERVAL = 250L
        private const val HTTP_OK = 200
        private const val USER_KEY = "user:"
        private const val SERVER_KEY = "server:"
        private const val PASS_KEY = "password:"
        private const val PREFIX = "nc://login/"
        private const val MAX_ARGS = 3
    }

    private var shouldReauthorizeUser = false

    private val _errorFlow = MutableSharedFlow<Pair<String, String>>()
    val errorFlow: Flow<Pair<String, String>> get() = _errorFlow

    private val _launchWebFlow = MutableSharedFlow<String>()
    val launchWebFlow: Flow<String> get() = _launchWebFlow

    private val _restartAppFlow = MutableSharedFlow<Boolean>()
    val restartAppFlow: Flow<Boolean> get() = _restartAppFlow

    private val _continueLoginFlow = MutableSharedFlow<Bundle>()
    val continueLoginFlow: Flow<Bundle> get() = _continueLoginFlow

    private suspend fun pollLogin(response: LoginResponse) {
        while (true) {
            val loginData = network.performLoginFlowV2(response)
            if (loginData == null) {
                break // Error occurred, terminating
            }

            if (loginData.status != HTTP_OK) {
                delay(INTERVAL) // No response yet, retry
            }

            parseAndLogin(loginData)
            break // process completed
        }
    }

    /**
     * Entry point for QR scanner
     *
     * @throws IllegalArgumentException
     */
    fun startLoginFlowFromQR(dataString: String, reAuth: Boolean = false) {
        shouldReauthorizeUser = reAuth

        if (!dataString.startsWith(PREFIX)) {
            throw IllegalArgumentException("Invalid login URL detected")
        }

        val data = dataString.removePrefix(PREFIX)
        val values = data.split('&')

        if (values.size !in 1..MAX_ARGS) {
            throw IllegalArgumentException("Illegal number of login URL elements detected: ${values.size}")
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

        val loginCompletion = LoginCompletion(HTTP_OK, server, loginName, appPassword)

        parseAndLogin(loginCompletion)
    }

    /**ur
     * Entry point to the login process
     */
    fun startLoginFlow(baseUrl: String, reAuth: Boolean = false) {
        shouldReauthorizeUser = reAuth

        CoroutineScope(Dispatchers.IO).launch {
            val response = network.anonymouslyPostLoginRequest(baseUrl)

            if (response != null) {
                _launchWebFlow.emit(response.loginUrl)
                pollLogin(response)
            } else {
                val pair = Pair(START_LOGIN_FLOW, "No Response from anonymous request")
                _errorFlow.emit(pair)
            }
        }
    }

    private fun parseAndLogin(loginData: LoginCompletion) {
        if (local.checkIfUserIsScheduledForDeletion(loginData)) {
            val pair = Pair(PARSE_LOGIN, "User is scheduled for deletion")
            _errorFlow.tryEmit(pair)

            // however the user is not yet deleted, just start AccountRemovalWorker again to make sure to delete it.
            val liveData = local.startAccountRemovalWorker()
            liveData.observeForever { workInfo: WorkInfo? ->
                when (workInfo?.state) {
                    WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        _restartAppFlow.tryEmit(true)
                    }

                    else -> {}
                }
            }
        } else if (local.checkIfUserExists(loginData)) {
            // FIXME LOW PRIORITY Refactor entry point to take you to server selection, instead of browser
            if (shouldReauthorizeUser) {
                local.updateUser(loginData)
                _restartAppFlow.tryEmit(true)
            } else {
                Log.w(TAG, "Tried to add an account that account already exists. Skipped user creation.")
                _restartAppFlow.tryEmit(true)
            }
        } else {
            startAccountVerification(loginData)
        }
    }

    private fun startAccountVerification(loginData: LoginCompletion) {
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

        _continueLoginFlow.tryEmit(bundle)
    }
}
