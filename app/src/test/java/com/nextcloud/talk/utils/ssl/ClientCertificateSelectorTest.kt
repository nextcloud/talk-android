/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.ssl

import com.nextcloud.talk.data.user.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClientCertificateSelectorTest {

    private val withCertA = User(id = 1, baseUrl = "https://a.example.com", clientCertificate = "certA")
    private val withoutCertB = User(id = 2, baseUrl = "https://b.example.com")
    private val withCertB = User(id = 3, baseUrl = "https://b.example.com", clientCertificate = "certB")
    private val otherCertB = User(id = 4, baseUrl = "https://b.example.com", clientCertificate = "otherB")

    @Test
    fun `the certificate of the server's account is used, not the default account's`() {
        val alias = ClientCertificateSelector.selectAlias(
            "a.example.com",
            listOf(withCertA, withoutCertB),
            withoutCertB
        )

        assertEquals("certA", alias)
    }

    @Test
    fun `no certificate for a server whose accounts use none, even if the default account has one`() {
        val alias = ClientCertificateSelector.selectAlias("b.example.com", listOf(withCertA, withoutCertB), withCertA)

        assertNull(alias)
    }

    @Test
    fun `accounts on the same server with one certificate share it`() {
        val alias = ClientCertificateSelector.selectAlias(
            "b.example.com",
            listOf(withoutCertB, withCertB),
            withoutCertB
        )

        assertEquals("certB", alias)
    }

    @Test
    fun `different certificates on the same server are decided by the default account`() {
        val users = listOf(withCertB, otherCertB, withCertA)

        assertEquals("otherB", ClientCertificateSelector.selectAlias("b.example.com", users, otherCertB))
        assertNull(ClientCertificateSelector.selectAlias("b.example.com", users, withCertA))
    }

    @Test
    fun `without a host the default account's certificate is used`() {
        assertEquals("certA", ClientCertificateSelector.selectAlias(null, listOf(withCertA, withCertB), withCertA))
    }
}
