/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import android.app.Application
import com.nextcloud.talk.api.NcApi
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class AccountRemovalWorkerTest {

    private val server = MockWebServer()
    private lateinit var ncApi: NcApi

    @Before
    fun setUp() {
        server.start()
        ncApi = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()
            .create(NcApi::class.java)
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `a successful request with an empty body is recognized as success`() {
        // The push proxy answers without a body, Retrofit emits null.
        server.enqueue(MockResponse())

        val request = ncApi.unregisterDeviceForNotificationsWithProxy(server.url("/devices").toString(), emptyMap())

        assertNull(AccountRemovalWorker.awaitCompletion(request))
    }

    @Test
    fun `a failed request returns its error`() {
        server.enqueue(MockResponse(code = 500))

        val request = ncApi.unregisterDeviceForNotificationsWithProxy(server.url("/devices").toString(), emptyMap())

        assertTrue(AccountRemovalWorker.awaitCompletion(request) is HttpException)
    }
}
