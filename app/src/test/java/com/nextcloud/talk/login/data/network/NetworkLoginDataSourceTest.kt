/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.login.data.network

import dagger.hilt.android.testing.HiltAndroidTest

@HiltAndroidTest
class NetworkLoginDataSourceTest {

    // @Inject lateinit var trustManager: TrustManager
    // @Inject lateinit var socketFactory: SSLSocketFactoryCompat
    // lateinit var network: NetworkLoginDataSource
    //
    // @get:Rule
    // val rule = HiltAndroidRule(this)
    //
    // @Before
    // fun setup() {
    //     rule.inject()
    //     network = NetworkLoginDataSource(trustManager, socketFactory)
    // }
    //
    // @Test
    // fun `testing anonymouslyPostLoginRequest correct path`() {
    //     val server = MockWebServer()
    //     val httpUrl = server.url("index.php/login/v2")
    //     val validResponse = """
    //     {
    //         "poll":{
    //             "token":"mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
    //             "endpoint":"https:\/\/cloud.example.com\/login\/v2\/poll"
    //         },
    //         "login":"https:\/\/cloud.example.com\/login\/v2\/flow\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
    //     }
    //     """.trimIndent()
    //     server.enqueue(MockResponse(body = validResponse))
    //     server.start()
    //
    //     val loginResponse = network.anonymouslyPostLoginRequest(httpUrl.toString())
    //     assertNotNull(loginResponse)
    // }
    //
    // @Test
    // fun `testing anonymouslyPostLoginRequest error path`() {
    //     val server = MockWebServer()
    //     val httpUrl = server.url("index.php/login/v2")
    //     val invalidResponse = MockResponse.Builder()
    //         .addHeader("Content-Type", "application/json; charset=utf-8")
    //         .addHeader("Cache-Control", "no-cache")
    //         .code(404)
    //         .body("{}")
    //         .build()
    //
    //     server.enqueue(invalidResponse)
    //     server.start()
    //
    //     val loginResponse = network.anonymouslyPostLoginRequest(httpUrl.toString())
    //     assertNull(loginResponse)
    // }
    //
    // @Test
    // fun `testing anonymouslyPostLoginRequest malformed response`() {
    //     val server = MockWebServer()
    //     val httpUrl = server.url("index.php/login/v2")
    //     val validResponse = """
    //     {
    //         "poll":{
    //             "token":"mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1"
    //         },
    //         "login":"https:\/\/cloud.example.com\/login\/v2\/flow\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
    //     }
    //     """.trimIndent()
    //     server.enqueue(MockResponse(body = validResponse))
    //     server.start()
    //
    //     val loginResponse = network.anonymouslyPostLoginRequest(httpUrl.toString())
    //     assertNull(loginResponse)
    // }
    //
    // @Test
    // fun `testing performLoginFlowV2 correct path`() {
    //     val server = MockWebServer()
    //     val httpUrl = server.url("login/v2/poll")
    //     val loginResponse = NetworkLoginDataSource.LoginResponse(
    //         token = "mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
    //         pollUrl = httpUrl.toString(),
    //         loginUrl = "https:\\/\\/cloud.example.com\\/login\\/v2\\/flow\\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
    //     )
    //     val validBody = """
    //     {
    //         "server":"https:\/\/cloud.example.com",
    //         "loginName":"username",
    //         "appPassword":"yKTVA4zgxjfivy52WqD8kW3M2pKGQr6srmUXMipRdunxjPFripJn0GMfmtNOqOolYSuJ6sCN"
    //     }
    //     """.trimIndent()
    //
    //     val validResponse = MockResponse.Builder()
    //         .body(validBody)
    //         .build()
    //     server.enqueue(validResponse)
    //     server.start()
    //
    //     val loginCompletion = network.performLoginFlowV2(loginResponse)
    //     assertNotNull(loginCompletion)
    // }
    //
    // @Test
    // fun `testing performLoginFlowV2 error path`() {
    //     val server = MockWebServer()
    //     val httpUrl = server.url("login/v2/poll")
    //     val loginResponse = NetworkLoginDataSource.LoginResponse(
    //         token = "mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
    //         pollUrl = httpUrl.toString(),
    //         loginUrl = "https:\\/\\/cloud.example.com\\/login\\/v2\\/flow\\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
    //     )
    //
    //     val invalidResponse = MockResponse.Builder()
    //         .addHeader("Content-Type", "application/json; charset=utf-8")
    //         .addHeader("Cache-Control", "no-cache")
    //         .code(404)
    //         .body("{}")
    //         .build()
    //
    //     server.enqueue(invalidResponse)
    //     server.start()
    //
    //     val loginCompletion = network.performLoginFlowV2(loginResponse)
    //     assertNull(loginCompletion)
    // }
    //
    // @Test
    // fun `testing performLoginFlowV2 malformed response`() {
    //     val server = MockWebServer()
    //     val httpUrl = server.url("login/v2/poll")
    //     val loginResponse = NetworkLoginDataSource.LoginResponse(
    //         token = "mQUYQdffOSAMJYtm8pVpkOsVqXt5hglnuSpO5EMbgJMNEPFGaiDe8OUjvrJ2WcYcBSLgqynu9jaPFvZHMl83ybMvp6aDIDARjTFIBpRWod6p32fL9LIpIStvc6k8Wrs1",
    //         pollUrl = httpUrl.toString(),
    //         loginUrl = "https:\\/\\/cloud.example.com\\/login\\/v2\\/flow\\/guyjGtcKPTKCi4epIRIupIexgJ8wNInMFSfHabACRPZUkmEaWZSM54bFkFuzWksbps7jmTFQjeskLpyJXyhpHlgK8sZBn9HXLXjohIx5iXgJKdOkkZTYCzUWHlsg3YFg"
    //     )
    //     val validBody = """
    //     {
    //         "server":"https:\/\/cloud.example.com",
    //         "loginName":"username"
    //     }
    //     """.trimIndent()
    //
    //     val validResponse = MockResponse.Builder()
    //         .body(validBody)
    //         .build()
    //     server.enqueue(validResponse)
    //     server.start()
    //
    //     val loginCompletion = network.performLoginFlowV2(loginResponse)
    //     assertNull(loginCompletion)
    // }
}
