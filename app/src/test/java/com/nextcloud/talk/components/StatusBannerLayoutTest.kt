/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.components

import com.nextcloud.talk.data.network.ServerConnectionStatus.CONNECTED
import com.nextcloud.talk.data.network.ServerConnectionStatus.MAINTENANCE_MODE
import com.nextcloud.talk.data.network.ServerConnectionStatus.SERVER_UNREACHABLE
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBannerLayoutTest {

    @Test
    fun `a short unreachable status is not shown`() =
        runTest {
            val statuses = flow {
                emit(CONNECTED)
                delay(SHORT_MILLIS)
                emit(SERVER_UNREACHABLE)
                delay(SHORT_MILLIS)
                emit(CONNECTED)
            }

            assertEquals(listOf(CONNECTED), statuses.withUnreachableDelayed().toList())
        }

    @Test
    fun `a lasting unreachable status is shown`() =
        runTest {
            val statuses = flow {
                emit(CONNECTED)
                delay(SHORT_MILLIS)
                emit(SERVER_UNREACHABLE)
                delay(LONG_MILLIS)
                emit(MAINTENANCE_MODE)
            }

            assertEquals(
                listOf(CONNECTED, SERVER_UNREACHABLE, MAINTENANCE_MODE),
                statuses.withUnreachableDelayed().toList()
            )
        }

    companion object {
        private const val SHORT_MILLIS = 100L
        private const val LONG_MILLIS = 5000L
    }
}
