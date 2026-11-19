/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.webrtc

import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.models.json.signaling.settings.FederationHelloAuthParamsDto
import com.nextcloud.talk.models.json.signaling.settings.FederationJoinAuthParamsDto
import com.nextcloud.talk.models.json.signaling.settings.FederationSettingsDto
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock

/**
 * The own signaling server requires the "joinAuthParams" token issued by the own Nextcloud server to join a
 * federated room (nextcloud/spreed#20254), so it has to be forwarded from the signaling settings into the
 * "room" message as "federation.auth".
 */
class WebSocketConnectionHelperFederationJoinTest {

    // The real constructor injects from the application component, which is not available in unit tests.
    private val helper = mock<WebSocketConnectionHelper>(defaultAnswer = Mockito.CALLS_REAL_METHODS)

    @Test
    fun parsesJoinAuthParamsFromSignalingSettings() {
        val settings = LoganSquare.parse(SIGNALING_SETTINGS_JSON, SignalingSettingsOverall::class.java)

        assertEquals(JOIN_TOKEN, settings.ocs!!.settings!!.federation!!.joinAuthParams!!.token)
    }

    @Test
    fun forwardsJoinAuthParamsInFederatedJoinMessage() {
        val federationJson = joinMessageFederation(federation(joinToken = JOIN_TOKEN))

        assertEquals(HELLO_TOKEN, federationJson.string("token"))
        val auth = federationJson.getValue("auth").jsonObject
        assertEquals("1.0", auth.string("version"))
        assertEquals(JOIN_TOKEN, auth.string("token"))
    }

    @Test
    fun omitsJoinAuthParamsWhenServerDoesNotProvideThem() {
        val federationJson = joinMessageFederation(federation(joinToken = null))

        assertEquals(HELLO_TOKEN, federationJson.string("token"))
        assertFalse(federationJson.containsKey("auth"))
    }

    @Test
    fun omitsJoinAuthParamsWhenTokenIsEmpty() {
        val federationJson = joinMessageFederation(federation(joinToken = ""))

        assertFalse(federationJson.containsKey("auth"))
    }

    @Test
    fun nonFederatedJoinHasNoFederationBlock() {
        val message = helper.getAssembledJoinOrLeaveRoomModel(ROOM_TOKEN, SESSION_ID, null)

        assertNull(message.roomWebSocketMessage!!.roomFederationWebSocketMessage)
    }

    private fun federation(joinToken: String?) =
        FederationSettingsDto(
            server = "https://signaling.remote.example.com",
            nextcloudServer = "https://remote.example.com",
            helloAuthParams = FederationHelloAuthParamsDto(HELLO_TOKEN),
            joinAuthParams = joinToken?.let { FederationJoinAuthParamsDto(it) },
            roomId = "remoteRoomId"
        )

    private fun joinMessageFederation(federation: FederationSettingsDto): JsonObject {
        val message = helper.getAssembledJoinOrLeaveRoomModel(ROOM_TOKEN, SESSION_ID, federation)
        return Json.parseToJsonElement(LoganSquare.serialize(message)).jsonObject
            .getValue("room").jsonObject
            .getValue("federation").jsonObject
    }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    companion object {
        private const val ROOM_TOKEN = "localRoomToken"
        private const val SESSION_ID = "sessionId"
        private const val HELLO_TOKEN = "helloToken"
        private const val JOIN_TOKEN = "joinJwt"

        private val SIGNALING_SETTINGS_JSON = """
            {
              "ocs": {
                "data": {
                  "federation": {
                    "server": "https://signaling.remote.example.com",
                    "nextcloudServer": "https://remote.example.com",
                    "joinAuthParams": { "token": "$JOIN_TOKEN" },
                    "helloAuthParams": { "token": "$HELLO_TOKEN" },
                    "roomId": "remoteRoomId"
                  }
                }
              }
            }
        """.trimIndent()
    }
}
