/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import com.nextcloud.talk.data.user.model.User
import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Regression tests for #6710: [WebSocketInstance] retries by itself, so the helper must reuse an
 * already registered instance instead of spawning a second one whenever it is momentarily not
 * connected - otherwise the first instance keeps running unsupervised, opening a parallel
 * connection to the signaling server for the same user.
 */
class WebSocketConnectionHelperTest {

    @After
    fun tearDown() {
        instanceMap().clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun instanceMap(): MutableMap<Long, WebSocketInstance> {
        val field = WebSocketConnectionHelper::class.java.getDeclaredField("webSocketInstanceMap")
        field.isAccessible = true
        return field.get(null) as MutableMap<Long, WebSocketInstance>
    }

    @Test
    fun reusesExistingInstanceEvenWhileItIsStillReconnecting() {
        val user = User(id = USER_ID)
        val existingInstance = mock<WebSocketInstance>()
        whenever(existingInstance.isConnected).thenReturn(false)
        instanceMap()[USER_ID] = existingInstance

        val result = WebSocketConnectionHelper.getExternalSignalingInstanceForServer(
            "https://signaling.example.com",
            user,
            "ticket",
            false
        )

        assertSame(existingInstance, result)
    }

    @Test
    fun reusesExistingConnectedInstance() {
        val user = User(id = USER_ID)
        val existingInstance = mock<WebSocketInstance>()
        whenever(existingInstance.isConnected).thenReturn(true)
        instanceMap()[USER_ID] = existingInstance

        val result = WebSocketConnectionHelper.getExternalSignalingInstanceForServer(
            "https://signaling.example.com",
            user,
            "ticket",
            false
        )

        assertSame(existingInstance, result)
    }

    companion object {
        private const val USER_ID = 42L
    }
}
