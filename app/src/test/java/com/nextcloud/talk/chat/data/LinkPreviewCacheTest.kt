/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data

import com.nextcloud.talk.models.json.opengraph.OpenGraphObjectDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkPreviewCacheTest {

    private val dispatcher = StandardTestDispatcher()
    private val cacheScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val requestedUrls = mutableListOf<String>()

    @After
    fun tearDown() {
        cacheScope.cancel()
    }

    @Test
    fun `a url is resolved only once`() =
        runTest(dispatcher) {
            val cache = cache { PREVIEW }

            assertSame(PREVIEW, cache.get(URL))
            assertSame(PREVIEW, cache.get(URL))

            assertEquals(listOf(URL), requestedUrls)
        }

    @Test
    fun `a url without a preview is not resolved again`() =
        runTest(dispatcher) {
            val cache = cache { null }

            assertNull(cache.get(URL))
            assertNull(cache.get(URL))

            assertEquals(listOf(URL), requestedUrls)
        }

    @Test
    fun `a failed request is resolved again on the next call`() =
        runTest(dispatcher) {
            var fail = true
            val cache = cache {
                if (fail) {
                    fail = false
                    throw IllegalStateException("server not reachable")
                }
                PREVIEW
            }

            assertNull(cache.get(URL))
            assertSame(PREVIEW, cache.get(URL))

            assertEquals(listOf(URL, URL), requestedUrls)
        }

    @Test
    fun `concurrent calls for a url share one request`() =
        runTest(dispatcher) {
            val response = CompletableDeferred<OpenGraphObjectDto?>()
            val cache = cache { response.await() }

            val first = async { cache.get(URL) }
            val second = async { cache.get(URL) }
            advanceUntilIdle()
            response.complete(PREVIEW)

            assertSame(PREVIEW, first.await())
            assertSame(PREVIEW, second.await())
            assertEquals(listOf(URL), requestedUrls)
        }

    @Test
    fun `the least recently used url is resolved again once the cache is full`() =
        runTest(dispatcher) {
            val cache = cache(maxSize = 2) { PREVIEW }

            cache.get(URL)
            cache.get(OTHER_URL)
            cache.get(URL)
            cache.get(THIRD_URL)
            cache.get(URL)
            cache.get(OTHER_URL)

            assertEquals(listOf(URL, OTHER_URL, THIRD_URL, OTHER_URL), requestedUrls)
        }

    private fun cache(maxSize: Int = MAX_SIZE, resolve: suspend (String) -> OpenGraphObjectDto?) =
        LinkPreviewCache(cacheScope, maxSize) { url ->
            requestedUrls.add(url)
            resolve(url)
        }

    private companion object {
        const val URL = "https://example.com/a"
        const val OTHER_URL = "https://example.com/b"
        const val THIRD_URL = "https://example.com/c"
        const val MAX_SIZE = 10
        val PREVIEW = OpenGraphObjectDto(id = "preview", name = "Example")
    }
}
