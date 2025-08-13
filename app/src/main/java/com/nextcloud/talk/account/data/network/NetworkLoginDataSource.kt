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
import com.nextcloud.talk.account.data.model.LoginCompletion
import com.nextcloud.talk.account.data.model.LoginResponse
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

//  This class handles the network and polling logic in isolation, which makes it easier to test
//  Login and Authentication is critical, thus it needs to be working properly.
class NetworkLoginDataSource(val okHttpClient: OkHttpClient) {

    companion object {
        val TAG: String = NetworkLoginDataSource::class.java.simpleName
    }

    fun anonymouslyPostLoginRequest(baseUrl: String): LoginResponse? {
        val url = "$baseUrl/index.php/login/v2"
        var result: LoginResponse? = null
        runCatching {
            val response = getResponseOfAnonymouslyPostLoginRequest(url)
            val jsonObject: JsonObject = JsonParser.parseString(response).asJsonObject
            val loginUrl: String = getLoginUrl(jsonObject)
            val token = jsonObject.getAsJsonObject("poll").get("token").asString
            val pollUrl = jsonObject.getAsJsonObject("poll").get("endpoint").asString
            result = LoginResponse(token, pollUrl, loginUrl)
        }.getOrElse { e ->
            when (e) {
                is SSLHandshakeException,
                is NullPointerException,
                is IOException -> {
                    Log.e(TAG, "Error caught at anonymouslyPostLoginRequest: $e")
                }

                else -> throw e
            }
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
        runCatching {
            okHttpClient.newCall(request).execute()
                .use { response ->
                    val status: Int = response.code
                    val responseBody = response.body?.string()

                    result = if (response.isSuccessful && responseBody?.isNotEmpty() == true) {
                        val jsonObject = JsonParser.parseString(responseBody).asJsonObject
                        val server: String = jsonObject.get("server").asString
                        val loginName: String = jsonObject.get("loginName").asString
                        val appPassword: String = jsonObject.get("appPassword").asString

                        LoginCompletion(status, server, loginName, appPassword)
                    } else {
                        LoginCompletion(status, "", "", "")
                    }
                }
        }.getOrElse { e ->
            when (e) {
                is NullPointerException,
                is SSLHandshakeException,
                is IllegalStateException,
                is IOException -> {
                    Log.e(TAG, "Error caught at performLoginFlowV2: $e")
                }

                else -> throw e
            }
        }

        return result
    }
}
