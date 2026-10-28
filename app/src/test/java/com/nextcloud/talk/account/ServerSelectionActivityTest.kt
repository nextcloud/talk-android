/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerSelectionActivityTest {

    @Test
    fun serverIsReadFromAnyPositionAndDecoded() {
        val server = "https://192.168.1.10/nextcloud"
        listOf(
            "nc://login/user:alice&password:secret&server:https%3A%2F%2F192.168.1.10%2Fnextcloud",
            "nc://login/server:https%3A%2F%2F192.168.1.10%2Fnextcloud&user:alice&password:secret",
            "nc://onetime-login/user:alice&password:secret&server:https%3A%2F%2F192.168.1.10%2Fnextcloud"
        ).forEach { assertEquals(it, server, ServerSelectionActivity.serverFromQr(it)) }
    }

    @Test
    fun missingServerGivesNull() {
        assertNull(ServerSelectionActivity.serverFromQr("nc://login/user:alice&password:secret"))
    }
}
