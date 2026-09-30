/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ValidLinkRegexTest {

    private fun firstLink(text: String): String? = validLinkRegex.find(text)?.value

    @Test
    fun `link without port is matched completely`() {
        assertEquals(
            "https://example.com/index.php/f/12345",
            firstLink("see https://example.com/index.php/f/12345 please")
        )
    }

    @Test
    fun `link with non-standard port keeps the port`() {
        assertEquals(
            "https://example.com:4446/index.php/f/12345",
            firstLink("see https://example.com:4446/index.php/f/12345 please")
        )
    }

    @Test
    fun `link with port and without path keeps the port`() {
        assertEquals("http://cloud.example.com:8080", firstLink("http://cloud.example.com:8080 is up"))
    }
}
