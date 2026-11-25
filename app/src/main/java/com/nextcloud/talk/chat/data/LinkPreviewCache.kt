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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Resolves link previews and keeps the results of the [maxSize] most recently used URLs. Calls for the same URL share
 * one request, which runs in [scope] and therefore continues when the caller that started it goes away. A URL without
 * a preview is remembered as such; a failed request is made again by the next call.
 */
class LinkPreviewCache(
    private val scope: CoroutineScope,
    private val maxSize: Int,
    private val resolve: suspend (String) -> OpenGraphObjectDto?
) {
    private val requests = object : LinkedHashMap<String, Deferred<OpenGraphObjectDto?>>(maxSize, 1f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Deferred<OpenGraphObjectDto?>>) =
            size > maxSize
    }

    /** Returns the preview of [url], or null if it has none or could not be resolved. */
    suspend fun get(url: String): OpenGraphObjectDto? {
        val request = synchronized(requests) {
            requests[url]?.takeUnless { it.isCancelled } ?: scope.async { resolve(url) }.also { requests[url] = it }
        }
        return try {
            request.await()
        } catch (_: CancellationException) {
            // Only the caller's own cancellation ends it; a cancelled request is made again by the next call.
            currentCoroutineContext().ensureActive()
            null
        } catch (_: Exception) {
            null
        }
    }
}
