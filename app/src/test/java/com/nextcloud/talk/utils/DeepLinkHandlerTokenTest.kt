/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkHandlerTokenTest {

    @Test
    fun `accepts alphanumeric tokens`() {
        assertTrue(DeepLinkHandler.isValidToken("abc123de"))
    }

    @Test
    fun `rejects tokens that could alter api urls`() {
        assertFalse(DeepLinkHandler.isValidToken("../../ocs/v2.php/cloud/user"))
        assertFalse(DeepLinkHandler.isValidToken("abc123?format=xml"))
        assertFalse(DeepLinkHandler.isValidToken("abc%2F123"))
    }

    @Test
    fun `rejects tokens of invalid length`() {
        assertFalse(DeepLinkHandler.isValidToken(""))
        assertFalse(DeepLinkHandler.isValidToken("abc"))
        assertFalse(DeepLinkHandler.isValidToken("a".repeat(33)))
    }
}
