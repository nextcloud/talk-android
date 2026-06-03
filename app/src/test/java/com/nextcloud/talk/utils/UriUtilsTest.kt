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
}
