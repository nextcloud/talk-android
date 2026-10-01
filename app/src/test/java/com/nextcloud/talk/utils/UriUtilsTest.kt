/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import org.junit.Assert
import org.junit.Test

class UriUtilsTest {

    @Test
    fun getMessageLink_buildsWebClientFormat() {
        val link = UriUtils.getMessageLink("https://cloud.example.com", "mwcskgpz", 4)
        Assert.assertEquals("https://cloud.example.com/call/mwcskgpz#message_4", link)
    }

    @Test
    fun getMessageLink_trimsTrailingSlashOnBaseUrl() {
        val link = UriUtils.getMessageLink("https://cloud.example.com/", "abc123", 42)
        Assert.assertEquals("https://cloud.example.com/call/abc123#message_42", link)
    }

    @Test
    fun getMessageLink_keepsSubpathBaseUrl() {
        val link = UriUtils.getMessageLink("https://example.com/nextcloud", "token1", 7)
        Assert.assertEquals("https://example.com/nextcloud/call/token1#message_7", link)
    }

    @Test
    fun `isOnServer accepts URLs of the server`() {
        Assert.assertTrue(UriUtils.isOnServer("$SERVER/core/preview?x=1", SERVER))
        Assert.assertTrue(UriUtils.isOnServer("$SERVER/", "$SERVER/"))
        Assert.assertTrue(UriUtils.isOnServer("$SUBFOLDER_SERVER/index.php", SUBFOLDER_SERVER))
    }

    @Test
    fun `isOnServer rejects other hosts that start with the server`() {
        Assert.assertFalse(UriUtils.isOnServer("https://cloud.example.com.attacker.test/image", SERVER))
        Assert.assertFalse(UriUtils.isOnServer("https://cloud.example.com@attacker.test/image", SERVER))
    }

    @Test
    fun `isOnServer rejects another scheme, port or path`() {
        Assert.assertFalse(UriUtils.isOnServer("http://cloud.example.com/image", SERVER))
        Assert.assertFalse(UriUtils.isOnServer("https://cloud.example.com:8443/image", SERVER))
        Assert.assertFalse(UriUtils.isOnServer("${SUBFOLDER_SERVER}2/image", SUBFOLDER_SERVER))
        Assert.assertFalse(UriUtils.isOnServer("not a url", SERVER))
    }

    companion object {
        private const val SERVER = "https://cloud.example.com"
        private const val SUBFOLDER_SERVER = "https://example.com/nextcloud"
    }
}
