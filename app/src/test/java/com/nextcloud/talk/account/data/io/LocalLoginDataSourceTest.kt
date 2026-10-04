/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account.data.io

import com.nextcloud.talk.account.data.model.LoginCompletion
import com.nextcloud.talk.users.UserManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class LocalLoginDataSourceTest {

    private val userManager: UserManager = mock()
    private val dataSource = LocalLoginDataSource(userManager, mock(), mock())
    private val loginData = LoginCompletion(200, "https://example.com", "user", "pass")

    @Test
    fun `awaitUserRemoval returns once the user is not scheduled for deletion anymore`() =
        runTest {
            whenever(userManager.checkIfUserIsScheduledForDeletion("user", "https://example.com"))
                .thenReturn(true, true, false)

            assertTrue(dataSource.awaitUserRemoval(loginData, 10_000L))
            verify(userManager, times(3)).checkIfUserIsScheduledForDeletion("user", "https://example.com")
        }

    @Test
    fun `awaitUserRemoval gives up when the removal does not finish in time`() =
        runTest {
            whenever(userManager.checkIfUserIsScheduledForDeletion("user", "https://example.com"))
                .thenReturn(true)

            assertFalse(dataSource.awaitUserRemoval(loginData, 1_000L))
        }
}
