/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.login.data.network

import com.nextcloud.talk.account.data.model.LoginResponse
import com.nextcloud.talk.account.data.network.NetworkLoginDataSource
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations

@Suppress("ktlint:standard:max-line-length", "MaxLineLength")
class NetworkLoginDataSourceTest {

    lateinit var network: NetworkLoginDataSource
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        network = NetworkLoginDataSource(okHttpClient)
    }

    @Test
    fun `testing anonymouslyPostLoginRequest correct path`() {
        val server = MockWebServer()
        server.start(0)
        val httpUrl = server.url("index.php/login/v2")
        val validResponse = """
        {
            "poll":{
                "token":"mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
                "endpoint":"https:\/\/cloud.example.com\/login\/v2\/poll"
            },
            "login":"https:\/\/cloud.example.com\/login\/v2\/flow\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
        }
        """.trimIndent()
        val mockResponse = MockResponse().setBody(validResponse)
        server.enqueue(mockResponse)

        val loginResponse = network.anonymouslyPostLoginRequest(httpUrl.toString())
        assertNotNull(loginResponse)
    }

    @Test
    fun `testing anonymouslyPostLoginRequest error path`() {
        val server = MockWebServer()
        val invalidResponse = MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Cache-Control", "no-cache")
            .setResponseCode(404)
            .setBody("{}")

        server.start()
        server.enqueue(invalidResponse)
        val httpUrl = server.url("index.php/login/v2")

        val loginResponse = network.anonymouslyPostLoginRequest(httpUrl.toString())
        assertNull(loginResponse)
    }

    @Test
    fun `testing anonymouslyPostLoginRequest malformed response`() {
        val server = MockWebServer()
        val validResponse = """
        {
            "poll":{
                "token":"mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1"
            },
            "login":"https:\/\/cloud.example.com\/login\/v2\/flow\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
        }
        """.trimIndent()

        val mockResponse = MockResponse().setBody(validResponse)
        server.enqueue(mockResponse)
        server.start()
        val httpUrl = server.url("index.php/login/v2")

        val loginResponse = network.anonymouslyPostLoginRequest(httpUrl.toString())
        assertNull(loginResponse)
    }

    @Test
    fun `testing performLoginFlowV2 correct path`() {
        val server = MockWebServer()
        val validBody = """
        {
            "server":"https:\/\/cloud.example.com",
            "loginName":"username",
            "appPassword":"yKTVA4zgxjfivy52WqD8kW3M2pKGQr6srmUXMipRdunxjPFripJn0GMfmtNOqOolYSuJ6sCN"
        }
        """.trimIndent()

        val validResponse = MockResponse()
            .setBody(validBody)

        server.enqueue(validResponse)
        server.start()
        val httpUrl = server.url("login/v2/poll")
        val loginResponse = LoginResponse(
            token = "mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
            pollUrl = httpUrl.toString(),
            loginUrl = "https:\\/\\/cloud.example.com\\/login\\/v2\\/flow\\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
        )

        val loginCompletion = network.performLoginFlowV2(loginResponse)
        assertNotNull(loginCompletion)
    }

    @Test
    fun `testing performLoginFlowV2 error path`() {
        val server = MockWebServer()

        val invalidResponse = MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Cache-Control", "no-cache")
            .setResponseCode(404)
            .setBody("{}")

        server.enqueue(invalidResponse)
        server.start()
        val httpUrl = server.url("login/v2/poll")
        val loginResponse = LoginResponse(
            token = "mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
            pollUrl = httpUrl.toString(),
            loginUrl = "https:\\/\\/cloud.example.com\\/login\\/v2\\/flow\\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
        )

        val loginCompletion = network.performLoginFlowV2(loginResponse)
        assert(loginCompletion?.status == 404)
    }

    @Test
    fun `testing performLoginFlowV2 malformed response`() {
        val server = MockWebServer()
        val validBody = """
        {
            "server":"https:\/\/cloud.example.com",
            "loginName":"username"
        }
        """.trimIndent()

        val validResponse = MockResponse()
            .setBody(validBody)

        server.enqueue(validResponse)
        server.start()
        val httpUrl = server.url("login/v2/poll")
        val loginResponse = LoginResponse(
            token = "mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
            pollUrl = httpUrl.toString(),
            loginUrl = "https:\\/\\/cloud.example.com\\/login\\/v2\\/flow\\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
        )

        val loginCompletion = network.performLoginFlowV2(loginResponse)
        assertNull(loginCompletion)
    }
}
