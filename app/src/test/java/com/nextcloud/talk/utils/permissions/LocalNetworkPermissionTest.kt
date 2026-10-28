/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class LocalNetworkPermissionTest {

    private fun isLocal(ip: String) = LocalNetworkPermission.isLocalAddress(InetAddress.getByName(ip))

    @Test
    fun privateLinkLocalAndCgnatAddressesAreLocal() {
        listOf("10.0.2.2", "172.16.0.1", "172.31.255.255", "192.168.1.10", "169.254.1.1", "100.64.0.1", "100.127.255.1")
            .forEach { assertTrue(it, isLocal(it)) }
    }

    @Test
    fun uniqueLocalAndLinkLocalIpv6AddressesAreLocal() {
        listOf("fd12:3456::1", "fc00::1", "fe80::1").forEach { assertTrue(it, isLocal(it)) }
    }

    @Test
    fun publicAddressesAreNotLocal() {
        listOf("8.8.8.8", "172.32.0.1", "100.128.0.1", "100.63.255.255", "2a01:4f8::1", "127.0.0.1")
            .forEach { assertFalse(it, isLocal(it)) }
    }

    @Test
    fun localNamesAndIpLiteralsAreDetectedFromTheUrl() {
        assertTrue(LocalNetworkPermission.isLocalServer("https://nextcloud.local"))
        assertTrue(LocalNetworkPermission.isLocalServer("http://192.168.178.20:8080/nextcloud"))
        assertTrue(LocalNetworkPermission.isLocalServer("https://[fd00::5]"))
        assertFalse(LocalNetworkPermission.isLocalServer("https://203.0.113.7"))
        assertFalse(LocalNetworkPermission.isLocalServer(""))
    }
}
