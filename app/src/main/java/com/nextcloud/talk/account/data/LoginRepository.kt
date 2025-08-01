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
import com.nextcloud.talk.R
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

// this handles communication with the network datasource and the User manager (abstraction over room)
class LoginRepository(
    val network: NetworkLoginDataSource,
    val local: LocalLoginDataSource
) {

    companion object {
        val TAG: String = LoginRepository::class.java.simpleName
        private const val INTERVAL = 100L
        private const val HTTP_OK = 200
        private const val USER_KEY = "user:"
        private const val SERVER_KEY = "server:"
        private const val PASS_KEY = "password:"
        private const val PREFIX = "nc://login/"
        private const val MAX_ARGS = 3
    }

    private var isLoginProcessCompleted = false
    private var shouldReauthorizeUser = false

    private val _errorFlow = MutableSharedFlow<Int>()
    val errorFlow: SharedFlow<Int> get() = _errorFlow

    private val _launchWebFlow = MutableSharedFlow<String>()
    val launchWebFlow: SharedFlow<String> get() = _launchWebFlow

    private val _restartAppFlow = MutableSharedFlow<Boolean>()
    val restartAppFlow: SharedFlow<Boolean> get() = _restartAppFlow

    private val _continueLoginFlow = MutableSharedFlow<Bundle>()
    val continueLoginFlow: SharedFlow<Bundle> get() = _continueLoginFlow

    private fun pollLogin(response: LoginResponse) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                while (!isLoginProcessCompleted) {
                    val loginData = network.performLoginFlowV2(response)
                    loginData?.let {
                        completeLoginFlow(it)
                    }
                    delay(INTERVAL)
                }
            }
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
                _errorFlow.emit(R.string.nc_common_error_sorry)
            }
        }
    }

    private fun completeLoginFlow(data: LoginCompletion) {
        isLoginProcessCompleted = (data.status == HTTP_OK)
        parseAndLogin(data)
    }

    private fun parseAndLogin(loginData: LoginCompletion) {
        if (local.checkIfUserIsScheduledForDeletion(loginData)) {
            _errorFlow.tryEmit(R.string.nc_common_error_sorry)

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
