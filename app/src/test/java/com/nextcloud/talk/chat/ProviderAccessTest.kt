/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderAccessTest {
    @Test
    fun resultIsReturnedWhenTheProviderWorks() =
        runBlocking {
            var failures = 0
            val result = runProviderCall({ failures++ }) { "file" }

            assertEquals("file", result)
            assertEquals(0, failures)
        }

    @Test
    fun providerExceptionIsReportedAndDoesNotPropagate() =
        runBlocking {
            val error = SecurityException("no vCard permission")
            var reported: Exception? = null

            val result = runProviderCall<String>({ reported = it }) { throw error }

            assertNull(result)
            assertSame(error, reported)
        }

    @Test
    fun cancellationStillPropagates() {
        var failures = 0
        assertThrows(CancellationException::class.java) {
            runBlocking { runProviderCall<String>({ failures++ }) { throw CancellationException("cancelled") } }
        }
        assertEquals(0, failures)
    }
}
