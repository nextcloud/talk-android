/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data

import com.nextcloud.talk.models.json.opengraph.OpenGraphObjectDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * Resolves link previews at most once per URL. Requests for the same URL share one request, which runs in [scope]
 * and therefore continues when the caller that started it goes away. A URL without a preview is remembered as such;
 * a failed request is forgotten, so the next call tries again. Keeps the [maxSize] most recently used URLs.
 */
class LinkPreviewCache(
    private val scope: CoroutineScope,
    private val maxSize: Int,
    private val resolve: suspend (String) -> OpenGraphObjectDto?
) {
    private val requests = object : LinkedHashMap<String, Deferred<OpenGraphObjectDto?>>(
        INITIAL_CAPACITY,
        LOAD_FACTOR,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Deferred<OpenGraphObjectDto?>>) =
            size > maxSize
    }

    /** Returns the preview of [url], or null if it has none or could not be resolved. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun get(url: String): OpenGraphObjectDto? {
        val request = synchronized(requests) {
            requests.getOrPut(url) { scope.async { resolve(url) } }
        }
        return try {
            request.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            synchronized(requests) {
                if (requests[url] === request) {
                    requests.remove(url)
                }
            }
            null
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
