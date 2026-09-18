/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * The contract every optimistic action in the app relies on: the change is visible before the answer
 * arrives, it survives a hiccup, and it is taken back whenever the request does not finally succeed -
 * including when the screen is left while the request is still in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OptimisticActionTest {

    private var state = "before"

    private val applyChange: suspend () -> (suspend () -> Unit)? = {
        val previous = state
        state = "after"
        { state = previous }
    }

    @Test
    fun `a change the server accepts stays applied`() =
        runTest {
            val result = optimisticAction(apply = applyChange, request = { "ok" })

            assertEquals("ok", result.getOrNull())
            assertEquals("after", state)
        }

    @Test
    fun `a change the server refuses is taken back`() =
        runTest {
            val result = optimisticAction<String>(apply = applyChange, request = { throw forbidden() })

            assertTrue(result.isFailure)
            assertEquals("before", state)
        }

    @Test
    fun `a connection problem is retried once before the change is taken back`() =
        runTest {
            var attempts = 0

            val result = optimisticAction<String>(
                apply = applyChange,
                request = {
                    attempts++
                    throw IOException("no connection")
                }
            )

            assertEquals(2, attempts)
            assertTrue(result.isFailure)
            assertEquals("before", state)
        }

    @Test
    fun `a single connection problem does not cost the change`() =
        runTest {
            var attempts = 0

            optimisticAction(
                apply = applyChange,
                request = {
                    attempts++
                    if (attempts == 1) throw IOException("no connection") else "ok"
                }
            )

            assertEquals("after", state)
        }

    @Test
    fun `a refusal the server reports in the payload is taken back as well`() =
        runTest {
            val result = optimisticAction(
                apply = applyChange,
                isConfirmed = { answer: String -> answer == "ok" },
                request = { "refused" }
            )

            // the request itself did not fail, so the caller still sees the answer it got
            assertEquals("refused", result.getOrNull())
            assertEquals("before", state)
        }

    @Test
    fun `nothing is taken back when nothing was applied`() =
        runTest {
            val result = optimisticAction<String>(apply = { null }, request = { throw forbidden() })

            assertTrue(result.isFailure)
            assertEquals("before", state)
        }

    @Test
    fun `leaving the screen while the request is in flight takes the change back`() =
        runTest {
            val job = launch {
                optimisticAction(apply = applyChange, request = { awaitForever() })
            }
            runCurrent()
            assertEquals("after", state)

            job.cancel()
            advanceUntilIdle()

            assertEquals("before", state)
        }

    private suspend fun awaitForever(): String = suspendCancellableCoroutine { }

    private fun forbidden(): HttpException =
        HttpException(Response.error<String>(HTTP_FORBIDDEN, "".toResponseBody("text/plain".toMediaType())))

    private companion object {
        const val HTTP_FORBIDDEN = 403
    }
}
