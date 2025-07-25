/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.viewmodels

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nextcloud.talk.models.LoginData
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.ssl.SSLSocketFactoryCompat
import com.nextcloud.talk.utils.ssl.TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import javax.inject.Inject
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSession

class BrowserLoginActivityViewModel @Inject constructor(
    private var userManager: UserManager,
    private var trustManager: TrustManager,
    private var socketFactory: SSLSocketFactoryCompat,
    private var appPreferences: AppPreferences,
    private var currentUserProvider: CurrentUserProviderNew,
    private var okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectionSpecs(listOf(ConnectionSpec.COMPATIBLE_TLS))
        .sslSocketFactory(socketFactory, trustManager)
        .hostnameVerifier { _: String?, _: SSLSession? -> true }
        .build()
): ViewModel() {

    private var token = ""
    private var pollUrl = ""
    private var isLoginProcessCompleted = false

    companion object {
        private val TAG = BrowserLoginActivityViewModel::class.java.simpleName
        private const val INTERVAL = 1000 * 30L
        private const val HTTP_OK = 200
        private const val USER_KEY = "user:"
        private const val SERVER_KEY = "server:"
        private const val PASS_KEY = "password:"
        private const val PREFIX = "nc://login/"
        private const val MAX_ARGS = 3
    }

    sealed class InitialLoginViewState {
        data object None : InitialLoginViewState()
        data class InitialLoginRequestSuccess(val loginUrl: String): InitialLoginViewState()
        data class InitialLoginRequestError(val exception: Exception): InitialLoginViewState()
    }

    private val _initialLoginRequestState = MutableStateFlow<InitialLoginViewState>(InitialLoginViewState.None)
    val initialLoginRequestState: Flow<InitialLoginViewState>
        get() = _initialLoginRequestState

    sealed class PostLoginViewState {
        data object None: PostLoginViewState()
        data object PostLoginRestart: PostLoginViewState()
        data object PostLoginAccountRemovalAndRestart: PostLoginViewState()
        data class PostLoginError(val e: Exception): PostLoginViewState()
        data class PostLoginUserExists(val data: LoginData): PostLoginViewState()
        data class PostLoginAccountVerification(val data: LoginData): PostLoginViewState()
    }

    private val _postLoginState = MutableStateFlow<PostLoginViewState>(PostLoginViewState.None)
    val postLoginState: Flow<PostLoginViewState>
        get() = _postLoginState

    // TODO start pool login and notify UI on result(s). Don't make viewmodel lifecycle aware, breaks MVVM

    // TODO move this to the data layer. That way I can easily test the viewModel, without mocking a network call
    fun anonymouslyPostLoginRequest(baseUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = "$baseUrl/index.php/login/v2" // TODO move to api utils
            try {
                val response = getResponseOfAnonymouslyPostLoginRequest(url)
                val jsonObject: JsonObject = JsonParser.parseString(response).asJsonObject
                val loginUrl: String = getLoginUrl(jsonObject)
                token = jsonObject.getAsJsonObject("poll").get("token").asString
                pollUrl = jsonObject.getAsJsonObject("poll").get("endpoint").asString
                _initialLoginRequestState.value = InitialLoginViewState.InitialLoginRequestSuccess(loginUrl)
            } catch (e: SSLHandshakeException) {
                Log.e(TAG, "Error caught at anonymouslyPostLoginRequest: $e")
                _initialLoginRequestState.value = InitialLoginViewState.InitialLoginRequestError(e)
            }
        }
    }

    private fun getResponseOfAnonymouslyPostLoginRequest(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .addHeader("Clear-Site-Data", "cookies")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }
            return response.body?.string()
        }
    }

    private fun getLoginUrl(response: JsonObject): String {
        var result: String? = response.get("login").asString
        if (result == null) {
            result = ""
        }

        return result
    }

    fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event === Lifecycle.Event.ON_START && token != "") {
            Log.d(TAG, "Start poolLogin")
            poolLogin()
        }
    }

    private fun poolLogin() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                while (!isLoginProcessCompleted) {
                    performLoginFlowV2()
                    delay(INTERVAL)
                }
            }
        }
    }

    private fun performLoginFlowV2() {
        val requestBody: RequestBody = FormBody.Builder()
            .add("token", token)
            .build()

        val request = Request.Builder()
            .url(pollUrl)
            .post(requestBody)
            .build()

        try {
            okHttpClient.newCall(request).execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code $response")
                    }
                    val status: Int = response.code
                    val response = response.body?.string()

                    Log.d(TAG, "performLoginFlowV2 status: $status")
                    Log.d(TAG, "performLoginFlowV2 response: $response")

                    if (response?.isNotEmpty() == true) {
                        completeLoginFlow(response, status)
                    }
                }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error caught at performLoginFlowV2: $e")
            _postLoginState.value = PostLoginViewState.PostLoginError(e)
        }
    }

    /**
     * QR returns a URI of format `nc://login/server:xxx&user:xxx&password:xxx`
     * with the variables not always been in the provided order
     *
     */
    fun parseLoginDataUrl(dataString: String) {
        if (!dataString.startsWith(PREFIX)) {
            val e = IllegalArgumentException("Invalid login URL detected")
            _postLoginState.value = PostLoginViewState.PostLoginError(e)
            return
        }

        val data = dataString.removePrefix(PREFIX)
        val values = data.split('&')

        if (values.size !in 1..MAX_ARGS) {
            val e = IllegalArgumentException("Illegal number of login URL elements detected: ${values.size}")
            _postLoginState.value = PostLoginViewState.PostLoginError(e)
            return
        }

        val loginData = LoginData()

        values.forEach { value ->
            when {
                value.startsWith(USER_KEY) -> {
                    loginData.username = URLDecoder.decode(value.removePrefix(USER_KEY), "UTF-8")
                }

                value.startsWith(PASS_KEY) -> {
                    loginData.token = URLDecoder.decode(value.removePrefix(PASS_KEY), "UTF-8")
                }

                value.startsWith(SERVER_KEY) -> {
                    loginData.serverUrl = URLDecoder.decode(value.removePrefix(SERVER_KEY), "UTF-8")
                }
            }
        }

        parseAndLogin(loginData)
    }

    private fun completeLoginFlow(response: String, status: Int) {
        try {
            val jsonObject = JSONObject(response)

            val server: String = jsonObject.getString("server")
            val loginName: String = jsonObject.getString("loginName")
            val appPassword: String = jsonObject.getString("appPassword")

            val loginData = LoginData()
            loginData.serverUrl = server
            loginData.username = loginName
            loginData.token = appPassword

            isLoginProcessCompleted =
                (status == HTTP_OK && !server.isEmpty() && !loginName.isEmpty() && !appPassword.isEmpty())

            parseAndLogin(loginData)
        } catch (e: JSONException) {
            Log.e(TAG, "Error caught at completeLoginFlow: $e")
            _postLoginState.value = PostLoginViewState.PostLoginError(e)
        }
    }

    private fun parseAndLogin(loginData: LoginData) {

        if (userManager.checkIfUserIsScheduledForDeletion(loginData.username!!, loginData.serverUrl!!).blockingGet()) {
            Log.e(TAG, "Tried to add already existing user who is scheduled for deletion.")
            // TODO notify UI
            // Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
            // // however the user is not yet deleted, just start AccountRemovalWorker again to make sure to delete it.
            // startAccountRemovalWorkerAndRestartApp()
            _postLoginState.value = PostLoginViewState.PostLoginAccountRemovalAndRestart
        } else if (userManager.checkIfUserExists(loginData.username!!, loginData.serverUrl!!).blockingGet()) {
            // TODO notify UI
            // if (reauthorizeAccount) {
            //     updateUserAndRestartApp(loginData)
            // } else {
            //     Log.w(TAG, "It was tried to add an account that account already exists. Skipped user creation.")
            //     restartApp()
            // }
            _postLoginState.value = PostLoginViewState.PostLoginUserExists(loginData)
        } else {
            // TODO notify UI
            // startAccountVerification(loginData)
            _postLoginState.value = PostLoginViewState.PostLoginAccountVerification(loginData)
        }
    }

    fun updateUser(loginData: LoginData) {
        val currentUser = currentUserProvider.currentUser.blockingGet()
        if (currentUser != null) {
            currentUser.clientCertificate = appPreferences.temporaryClientCertAlias
            currentUser.token = loginData.token
            val rowsUpdated = userManager.updateOrCreateUser(currentUser).blockingGet()
            Log.d(TAG, "User rows updated: $rowsUpdated")
            _postLoginState.value = PostLoginViewState.PostLoginRestart
        }
    }



}
