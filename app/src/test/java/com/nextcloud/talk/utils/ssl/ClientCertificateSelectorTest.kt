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
        assertEquals("certA", select(HOST_A, HTTPS_PORT, listOf(withCertA, withoutCertB), withoutCertB))
    }

    @Test
    fun `no certificate for a server whose accounts use none, even if the default account has one`() {
        assertNull(select(HOST_B, HTTPS_PORT, listOf(withCertA, withoutCertB), withCertA))
    }

    @Test
    fun `accounts on the same server with one certificate share it`() {
        assertEquals("certB", select(HOST_B, HTTPS_PORT, listOf(withoutCertB, withCertB), withoutCertB))
    }

    @Test
    fun `different certificates on the same server are decided by the default account`() {
        val users = listOf(withCertB, otherCertB, withCertA)

        assertEquals("otherB", select(HOST_B, HTTPS_PORT, users, otherCertB))
        assertNull(select(HOST_B, HTTPS_PORT, users, withCertA))
    }

    @Test
    fun `accounts on another port of the same host are another server`() {
        val onOtherPort = User(id = 5, baseUrl = "https://b.example.com:8443", clientCertificate = "portB")
        val users = listOf(withCertB, onOtherPort)

        assertEquals("certB", select(HOST_B, HTTPS_PORT, users, onOtherPort))
        assertEquals("portB", select(HOST_B, OTHER_PORT, users, withCertB))
    }

    @Test
    fun `an unknown port matches the host only`() {
        assertEquals("certA", select(HOST_A, UNKNOWN_PORT, listOf(withCertA), null))
    }

    @Test
    fun `without a host the default account's certificate is used`() {
        assertEquals("certA", select(null, UNKNOWN_PORT, listOf(withCertA, withCertB), withCertA))
    }

    private fun select(host: String?, port: Int, users: List<User>, defaultUser: User?) =
        ClientCertificateSelector.selectAlias(host, port, users, defaultUser)

    companion object {
        private const val HOST_A = "a.example.com"
        private const val HOST_B = "b.example.com"
        private const val HTTPS_PORT = 443
        private const val OTHER_PORT = 8443
        private const val UNKNOWN_PORT = -1
    }
}
