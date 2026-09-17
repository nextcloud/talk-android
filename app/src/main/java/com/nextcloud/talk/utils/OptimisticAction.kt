/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_INTERNAL_SERVER_ERROR = 500
private const val RETRY_DELAY_MS = 500L

/**
 * Whether a failed request is worth another attempt: a connection problem, a rate limit or a server
 * error. An answer the server actually gave - a refusal, a permission error - is not.
 */
fun isTransientFailure(error: Exception): Boolean =
    when (error) {
        is HttpException -> error.code() == HTTP_TOO_MANY_REQUESTS || error.code() >= HTTP_INTERNAL_SERVER_ERROR
        else -> error is IOException
    }

/**
 * Applies a change locally, has the server confirm it and undoes it when that fails, which is how
 * every user action that the client can predict should behave: the screen reacts to the tap, the
 * request follows, and only a request that finally fails takes the change back.
 *
 * [apply] writes the local state and returns the action that undoes it again, or null when there was
 * nothing to change - in which case nothing is undone later either. [request] is retried once for a
 * transient failure. [isConfirmed] decides whether an answer that did not throw still counts as a
 * refusal, for endpoints that report one in the payload rather than in the status code.
 *
 * Cancellation is part of the contract: closing the screen while the request is in flight undoes the
 * change as well, instead of leaving it applied although the server may never have heard of it.
 */
suspend fun <T> optimisticAction(
    apply: suspend () -> (suspend () -> Unit)?,
    isConfirmed: (T) -> Boolean = { true },
    request: suspend () -> T
): Result<T> {
    val revert = apply()
    // a revert that is itself cancelled would leave the change applied, which is the very thing it
    // is there to prevent
    val revertUninterruptibly: suspend () -> Unit = { withContext(NonCancellable) { revert?.invoke() } }

    return try {
        val answer = revertOnCancellation(revertUninterruptibly) {
            withRetry(retries = 1, initialDelayMillis = RETRY_DELAY_MS, retryOn = ::isTransientFailure) {
                request()
            }
        }
        if (!isConfirmed(answer)) {
            revertUninterruptibly()
        }
        Result.success(answer)
    } catch (e: HttpException) {
        revertUninterruptibly()
        Result.failure(e)
    } catch (e: IOException) {
        revertUninterruptibly()
        Result.failure(e)
    }
}
