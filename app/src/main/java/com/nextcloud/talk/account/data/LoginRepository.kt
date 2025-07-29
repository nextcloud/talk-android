/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.data


import android.os.Bundle
import android.text.TextUtils
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.net.URLDecoder


// this handles communication with the network datasource and the User manager (abstraction over room)
//   TODO test for response handling, error handling, and other edge cases from otherwise working data sources
//      because most of the logic should be happening in the repository layer, I should test this first
class LoginRepository(val network: NetworkLoginDataSource, val local: LocalLoginDataSource) {

    companion object {
        private const val INTERVAL = 30L
        private const val HTTP_OK = 200
        private const val USER_KEY = "user:"
        private const val SERVER_KEY = "server:"
        private const val PASS_KEY = "password:"
        private const val PREFIX = "nc://login/"
        private const val MAX_ARGS = 3
    }

    private var isLoginProcessCompleted = false

    private fun poolLogin(response: LoginResponse) {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.IO) {
                while (!isLoginProcessCompleted) {
                    network.performLoginFlowV2(response)
                    // completeLoginFlow(loginData) deal with nullable response
                    delay(INTERVAL)
                }
            }
        }
    }

    fun parseLoginDataUrl(dataString: String) {
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

    fun startLoginFlow(baseUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            // set anon post request

            // start pool login

            // notify view model to update state to launch web
        }
    }

    private fun completeLoginFlow(data: LoginCompletion) {
        try {

            isLoginProcessCompleted =
                (data.status == HTTP_OK
                    && !data.server.isEmpty()
                    && !data.loginName.isEmpty()
                    && !data.appPassword.isEmpty())

            parseAndLogin(data)
        } catch (e: JSONException) {
            // Log.e(TAG, "Error caught at completeLoginFlow: $e")
            // _postLoginState.value = PostLoginViewState.PostLoginError(e)
        }
    }

    private fun parseAndLogin(loginData: LoginCompletion) {
        if (local.checkIfUserIsScheduledForDeletion(loginData)) {
            // TODO notify viewmodel of UI state change to show error
            // Log.e(TAG, "Tried to add already existing user who is scheduled for deletion.")
            // Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()

            // however the user is not yet deleted, just start AccountRemovalWorker again to make sure to delete it.
            val liveData = local.startAccountRemovalWorkerAndRestartApp()
            liveData.observeForever { workInfo: WorkInfo? ->

                    when (workInfo?.state) {
                        WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            // TODO notify viewmodel of UI state change to restart app
                        }

                        else -> {}
                    }
                }
        } else if (local.checkIfUserExists(loginData)) {

            // TODO reauthorize won't work, because it links to here and not the server selection activity
            //
            // if (reauthorizeAccount) {
            //     updateUserAndRestartApp(loginData)
            // } else {
            //     Log.w(TAG, "It was tried to add an account that account already exists. Skipped user creation.")
            //     restartApp()
            // }
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

        // TODO notify viewmodel of UI state change to restart app

        // val intent = Intent(context, AccountVerificationActivity::class.java)
        // intent.putExtras(bundle)
        // startActivity(intent)
    }
}
