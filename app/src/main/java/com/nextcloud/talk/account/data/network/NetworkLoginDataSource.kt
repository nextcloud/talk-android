/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.account.data.network

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.nextcloud.talk.utils.ssl.SSLSocketFactoryCompat
import com.nextcloud.talk.utils.ssl.TrustManager
import okhttp3.ConnectionSpec
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSession

//  This class handles the network and polling logic in isolation, which makes it easier to test
//  Login and Authentication is critical, thus it needs to be working properly.
class NetworkLoginDataSource(
    trustManager: TrustManager,
    socketFactory: SSLSocketFactoryCompat
) {

    companion object {
        val TAG: String = NetworkLoginDataSource::class.java.simpleName
    }

    private var okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(CookieJar.NO_COOKIES)
        .connectionSpecs(listOf(ConnectionSpec.COMPATIBLE_TLS))
        .sslSocketFactory(socketFactory, trustManager)
        .hostnameVerifier { _: String?, _: SSLSession? -> true }
        .build()

    data class LoginResponse(
        val token: String,
        val pollUrl: String,
        val loginUrl: String
    )

    data class LoginCompletion(
        val status: Int,
        val server: String,
        val loginName: String,
        val appPassword: String
    )

    fun anonymouslyPostLoginRequest(baseUrl: String): LoginResponse? {
        val url = "$baseUrl/index.php/login/v2"
        var result: LoginResponse? = null
        try {
            val response = getResponseOfAnonymouslyPostLoginRequest(url)
            val jsonObject: JsonObject = JsonParser.parseString(response).asJsonObject
            val loginUrl: String = getLoginUrl(jsonObject)
            val token = jsonObject.getAsJsonObject("poll").get("token").asString
            val pollUrl = jsonObject.getAsJsonObject("poll").get("endpoint").asString
            result = LoginResponse(token, pollUrl, loginUrl)
        } catch (e: SSLHandshakeException) {
            Log.e(TAG, "Error caught at anonymouslyPostLoginRequest: $e")
        } catch (e: NullPointerException) {
            Log.e(TAG, "Error caught at performLoginFlowV2: $e")
        }

        return result

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

    fun performLoginFlowV2(response: LoginResponse): LoginCompletion? {
        val requestBody: RequestBody = FormBody.Builder()
            .add("token", response.token)
            .build()

        val request = Request.Builder()
            .url(response.pollUrl)
            .post(requestBody)
            .build()

        var result: LoginCompletion? = null
        try {
            okHttpClient.newCall(request).execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code $response")
                    }
                    val status: Int = response.code
                    val response = response.body?.string()
                    val jsonObject = JsonParser.parseString(response).asJsonObject

                    val server: String = jsonObject.get("server").asString
                    val loginName: String = jsonObject.get("loginName").asString
                    val appPassword: String = jsonObject.get("appPassword").asString


                    Log.d(TAG, "performLoginFlowV2 status: $status")
                    Log.d(TAG, "performLoginFlowV2 response: $response")

                    if (response?.isNotEmpty() == true) {
                        result = LoginCompletion(status, server, loginName, appPassword)
                    }
                }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Error caught at performLoginFlowV2: $e")
        } catch (e: NullPointerException) {
            Log.e(TAG, "Error caught at performLoginFlowV2: $e")
        }

        return result
    }
}
