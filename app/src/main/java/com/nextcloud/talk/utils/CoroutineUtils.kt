/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/**
 * Executes [block] and, if it throws, retries up to [retries] additional times, waiting
 * between attempts starting at [initialDelayMillis] and multiplying by [backoffFactor] after
 * each failed attempt, capped at [maxDelayMillis].
 * Equivalent to RxJava's `.retry(retries)`, with exponential backoff.
 * The last exception is rethrown if all attempts fail.
 */
@Suppress("TooGenericExceptionCaught", "LongParameterList")
suspend fun <T> withRetry(
    retries: Int = 1,
    initialDelayMillis: Long = 0,
    backoffFactor: Double = 2.0,
    maxDelayMillis: Long = Long.MAX_VALUE,
    block: suspend () -> T
): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            if (attempt >= retries) throw e
            if (initialDelayMillis > 0) {
                val delayMillis = min(
                    initialDelayMillis * backoffFactor.pow(attempt),
                    maxDelayMillis.toDouble()
                ).toLong()
                delay(delayMillis)
            }
            attempt++
        }
    }
}
