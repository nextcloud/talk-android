/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.pow

/**
 * Executes [block] and, if it throws, retries up to [retries] additional times, waiting
 * between attempts starting at [initialDelayMillis] and multiplying by [backoffFactor] after
 * each failed attempt, capped at [maxDelayMillis].
 * Equivalent to RxJava's `.retry(retries)`, with exponential backoff.
 * The last exception is rethrown if all attempts fail.
 *
 * [retryOn] decides which failures are worth another attempt; by default every failure is. A
 * request the server answered with a permanent refusal is not worth repeating, for instance.
 */
@Suppress("TooGenericExceptionCaught", "LongParameterList")
suspend fun <T> withRetry(
    retries: Int = 1,
    initialDelayMillis: Long = 0,
    backoffFactor: Double = 2.0,
    maxDelayMillis: Long = Long.MAX_VALUE,
    retryOn: (Exception) -> Boolean = { true },
    block: suspend () -> T
): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: Exception) {
            if (attempt >= retries || !retryOn(e)) throw e
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

/**
 * Emits a value right away and then at most one value per [windowMillis], always the most recent one.
 *
 * Unlike `debounce`, which holds every value back until the upstream went quiet for the whole window,
 * an isolated change is passed on immediately, so the UI can react to it without a fixed delay, while
 * a burst of changes still collapses into one emission per window.
 */
fun <T> Flow<T>.throttleLatest(windowMillis: Long): Flow<T> =
    channelFlow {
        val latest = Channel<T>(Channel.CONFLATED)

        launch {
            this@throttleLatest.collect { latest.send(it) }
            latest.close()
        }

        for (value in latest) {
            send(value)
            delay(windowMillis)
        }
    }

/**
 * Runs [block] and, if the coroutine is cancelled while it is suspended, runs [revert] before the
 * cancellation propagates. Cancellation would otherwise skip every catch block, which for an
 * optimistic local change means the change stays applied although its request may never have been
 * sent. The revert itself runs uncancellable, so it can still touch the database.
 */
suspend fun <T> revertOnCancellation(revert: suspend () -> Unit, block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        withContext(NonCancellable) { revert() }
        throw e
    }
