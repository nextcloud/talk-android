/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("TooGenericExceptionThrown")
class CoroutineUtilsTest {

    @Test
    fun `withRetry returns result on first success`() =
        runTest {
            var callCount = 0
            val result = withRetry(retries = 1) {
                callCount++
                "success"
            }
            assertEquals("success", result)
            assertEquals(1, callCount)
        }

    @Test
    fun `withRetry retries once and returns result on second attempt`() =
        runTest {
            var callCount = 0
            val result = withRetry(retries = 1) {
                callCount++
                if (callCount < 2) throw RuntimeException("transient error")
                "success after retry"
            }
            assertEquals("success after retry", result)
            assertEquals(2, callCount)
        }

    @Test(expected = RuntimeException::class)
    fun `withRetry rethrows exception when all attempts fail`() =
        runTest {
            withRetry(retries = 1) {
                throw RuntimeException("permanent error")
            }
        }

    @Test
    fun `withRetry makes exactly retries plus one attempts before failing`() =
        runTest {
            var callCount = 0
            val expectedException = RuntimeException("permanent")
            val thrownException = runCatching {
                withRetry(retries = 2) {
                    callCount++
                    throw expectedException
                }
            }.exceptionOrNull()

            assertEquals(3, callCount)
            assertSame(expectedException, thrownException)
        }

    @Test
    fun `withRetry with zero retries does not retry`() =
        runTest {
            var callCount = 0
            runCatching {
                withRetry(retries = 0) {
                    callCount++
                    throw RuntimeException("error")
                }
            }
            assertEquals(1, callCount)
        }

    @Test
    fun `withRetry waits initialDelayMillis before the first retry`() =
        runTest {
            var callCount = 0
            withRetry(retries = 1, initialDelayMillis = 1000) {
                callCount++
                if (callCount < 2) throw RuntimeException("transient error")
                "success"
            }
            assertEquals(2, callCount)
            assertEquals(1000, currentTime)
        }

    @Test
    fun `withRetry does not wait when initialDelayMillis is zero`() =
        runTest {
            var callCount = 0
            withRetry(retries = 2, initialDelayMillis = 0) {
                callCount++
                if (callCount < 3) throw RuntimeException("transient error")
                "success"
            }
            assertEquals(3, callCount)
            assertTrue(currentTime == 0L)
        }

    @Test
    fun `withRetry backs off exponentially between attempts`() =
        runTest {
            var callCount = 0
            withRetry(retries = 2, initialDelayMillis = 1000, backoffFactor = 2.0) {
                callCount++
                if (callCount < 3) throw RuntimeException("transient error")
                "success"
            }
            assertEquals(3, callCount)
            // 1000ms before the 2nd attempt, then 2000ms before the 3rd
            assertEquals(3000, currentTime)
        }

    @Test
    fun `withRetry caps the backoff delay at maxDelayMillis`() =
        runTest {
            var callCount = 0
            withRetry(
                retries = 3,
                initialDelayMillis = 1000,
                backoffFactor = 2.0,
                maxDelayMillis = 1500
            ) {
                callCount++
                if (callCount < 4) throw RuntimeException("transient error")
                "success"
            }
            assertEquals(4, callCount)
            // Uncapped would be 1000 + 2000 + 4000; capped at 1500 each time it exceeds it
            assertEquals(1000 + 1500 + 1500, currentTime)
        }
}
