/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import com.nextcloud.talk.activities.MainActivity.Companion.accountForContactServer
import com.nextcloud.talk.data.user.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityContactAccountTest {

    private val accountA = User(id = 1, baseUrl = "https://a.example.com")
    private val accountB = User(id = 2, baseUrl = "https://b.example.com/nextcloud")
    private val otherAccountOnA = User(id = 3, baseUrl = "https://a.example.com/")

    @Test
    fun `the default account is used when the contact is on its server`() {
        assertEquals(accountA, accountForContactServer("a.example.com", listOf(otherAccountOnA, accountA), accountA))
    }

    @Test
    fun `a contact of another account than the default one opens with that account`() {
        assertEquals(accountB, accountForContactServer("b.example.com/nextcloud", listOf(accountA, accountB), accountA))
    }

    @Test
    fun `without a default account an account on the contact's server is used`() {
        assertEquals(accountA, accountForContactServer("a.example.com", listOf(accountA, accountB), null))
    }

    @Test
    fun `no account when none is on the contact's server`() {
        assertNull(accountForContactServer("c.example.com", listOf(accountA, accountB), accountA))
    }

    @Test
    fun `a server that only ends like the account's server is another server`() {
        val onMyCloud = User(id = 4, baseUrl = "https://mycloud.example.com")

        assertNull(accountForContactServer("cloud.example.com", listOf(onMyCloud), onMyCloud))
    }

    @Test
    fun `the path is part of the server`() {
        assertNull(accountForContactServer("b.example.com", listOf(accountB), accountB))
    }

    @Test
    fun `host case and a trailing slash do not matter`() {
        assertEquals(accountB, accountForContactServer("B.Example.com/nextcloud/", listOf(accountB), null))
    }

    @Test
    fun `no account for an invalid server`() {
        assertNull(accountForContactServer("", listOf(accountA), accountA))
        assertNull(accountForContactServer("not a host", listOf(accountA), accountA))
    }
}
