/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.api

import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.UrlUtils
import at.bitfire.dav4jvm.property.ResourceType
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.nextcloud.android.lib.resources.users.UserApi
import com.nextcloud.talk.filebrowser.webdav.DavUtils
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import java.net.HttpURLConnection

/**
 * Showcases the android-library's Retrofit [UserApi] with coroutines and the app's dav4jvm (WebDAV) client
 * against the server given by the `TEST_SERVER_*` instrumentation arguments.
 */
class RetrofitAndDav4jvmIT {
    private lateinit var baseUrl: String
    private lateinit var username: String
    private lateinit var credentials: String

    @Before
    fun setUp() {
        val arguments = InstrumentationRegistry.getArguments()
        baseUrl = arguments.getString(ARG_SERVER_URL).orEmpty()
        username = arguments.getString(ARG_USERNAME).orEmpty()
        credentials = ApiUtils.getCredentials(username, arguments.getString(ARG_PASSWORD)).orEmpty()
    }

    @Test
    fun retrofitSuspendFunctionReturnsBodyOrThrowsHttpException() {
        val userApi = buildRetrofit(credentials).create(UserApi::class.java)
        val unauthorizedApi =
            buildRetrofit(ApiUtils.getCredentials(INVALID_LOGIN, INVALID_LOGIN).orEmpty()).create(UserApi::class.java)

        // suspend functions run the call on OkHttp's dispatcher, so no Dispatchers.IO switch is needed
        val user = runBlocking { userApi.fetchUser(FORMAT_JSON).ocs.data }
        val error = assertThrows(HttpException::class.java) {
            runBlocking { unauthorizedApi.fetchUser(FORMAT_JSON) }
        }

        assertEquals(username, user.id)
        assertEquals(HttpURLConnection.HTTP_UNAUTHORIZED, error.code())
    }

    @Test
    fun dav4jvmMkColThenPropfindRootListsFolder() {
        // dav4jvm handles redirects itself and requires the underlying client not to follow them
        val davClient = authenticatedOkHttpClient(credentials).newBuilder().followRedirects(false).build()
        val rootUrl = "$baseUrl${DavUtils.DAV_PATH}$username/"
        val folderName = FOLDER_PREFIX + System.currentTimeMillis()
        val folder = DavResource(davClient, "$rootUrl$folderName/".toHttpUrl())

        folder.mkCol(null) { }
        try {
            val members = mutableListOf<Response>()
            var self: Response? = null
            DavResource(davClient, rootUrl.toHttpUrl()).propfind(DEPTH_ONE, ResourceType.NAME) { response, relation ->
                when (relation) {
                    Response.HrefRelation.SELF -> self = response
                    Response.HrefRelation.MEMBER -> members.add(response)
                    else -> Unit
                }
            }

            assertTrue(self?.get(ResourceType::class.java)?.types?.contains(ResourceType.COLLECTION) == true)
            // dav4jvm 2.x hrefName() returns "" for collections (trailing slash), so compare the full URL
            val created = members.firstOrNull { UrlUtils.equals(it.href, folder.location) }
            assertNotNull("$folderName missing in PROPFIND result", created)
            assertTrue(created?.get(ResourceType::class.java)?.types?.contains(ResourceType.COLLECTION) == true)
        } finally {
            folder.delete { }
        }
    }

    private fun buildRetrofit(credentials: String): Retrofit =
        Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(authenticatedOkHttpClient(credentials))
            .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
            .build()

    private fun authenticatedOkHttpClient(credentials: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header(AUTHORIZATION, credentials).build())
            }
            .build()

    companion object {
        private const val ARG_SERVER_URL = "TEST_SERVER_URL"
        private const val ARG_USERNAME = "TEST_SERVER_USERNAME"
        private const val ARG_PASSWORD = "TEST_SERVER_PASSWORD"
        private const val AUTHORIZATION = "Authorization"
        private const val FORMAT_JSON = "json"
        private const val JSON_MEDIA_TYPE = "application/json"
        private const val FOLDER_PREFIX = "talk-dav4jvmShowcase"
        private const val INVALID_LOGIN = "invalid"
        private const val DEPTH_ONE = 1

        private val json = Json { ignoreUnknownKeys = true }
    }
}
