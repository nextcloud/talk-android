/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.app.Application
import com.nextcloud.talk.activities.DeepLinkRoutingViewModel.Companion.resolveTargetUser
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.DeepLinkHandler.DeepLinkResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The account that opens a deep link: the one it names, else the current one on its server, else another one. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class DeepLinkRoutingViewModelTargetUserTest {

    private val alice = User(id = 1L, username = "alice", baseUrl = "https://cloud.example.com")
    private val bob = User(id = 2L, username = "bob", baseUrl = "https://cloud.example.com")
    private val carol = User(id = 3L, username = "carol", baseUrl = "https://other.example.com")
    private val users = listOf(alice, bob, carol)

    private fun linkTo(username: String?) = DeepLinkResult("abcd1234", "https://cloud.example.com", username)

    @Test
    fun `the account the link names opens it`() {
        assertEquals(alice, resolveTargetUser(users, linkTo("alice"), defaultUser = bob))
    }

    @Test
    fun `a link without a user name is opened by the current account on its server`() {
        assertEquals(bob, resolveTargetUser(users, linkTo(null), defaultUser = bob))
    }

    @Test
    fun `a link to another server than the current account is opened by an account on it`() {
        assertEquals(alice, resolveTargetUser(users, linkTo(null), defaultUser = carol))
    }

    @Test
    fun `a link to a server without an account is opened by none`() {
        val link = DeepLinkResult("abcd1234", "https://unknown.example.com", "alice")

        assertNull(resolveTargetUser(users, link, defaultUser = alice))
    }
}
