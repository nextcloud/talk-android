/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

/**
 * Executes [block] and, if it throws, retries up to [retries] additional times.
 * Equivalent to RxJava's `.retry(retries)`.
 * The last exception is rethrown if all attempts fail.
 */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> withRetry(retries: Int = 1, block: suspend () -> T): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            if (attempt >= retries) throw e
            attempt++
        }
    }
}
