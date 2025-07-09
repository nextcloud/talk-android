/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.json

import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.data.database.mappers.asEntity
import com.nextcloud.talk.data.database.mappers.asModel
import com.nextcloud.talk.data.database.model.ConversationEntity
import com.nextcloud.talk.models.json.conversations.ConversationEnums
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.participants.Participant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class ConversationConversionTest(private val jsonFileName: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: testDeserialization({0})")
        fun data(): List<String> =
            listOf(
                "RoomOverallExample_APIv1.json",
                "RoomOverallExample_APIv2.json",
                "RoomOverallExample_APIv4.json"
            )
    }

    @Test
    fun testDeserialization() {
        val jsonFile = File("src/test/resources/$jsonFileName")
        val jsonString = jsonFile.readText()

        val roomOverall: RoomOverall = LoganSquare.parse(jsonString, RoomOverall::class.java)
        assertNotNull(roomOverall)

        val conversationJson = roomOverall.ocs!!.data!!
        assertNotNull(conversationJson)

        val conversationEntity = conversationJson.asEntity(1)
        assertNotNull(conversationEntity)

        val apiVersion: Int = jsonFileName.substringAfterLast("APIv").first().digitToInt()

        checkConversationEntity(conversationEntity, apiVersion)

        val conversationModel = conversationEntity.asModel()
        val conversationEntityConvertedBack = conversationModel.asEntity()

        checkConversationEntity(conversationEntityConvertedBack, apiVersion)
    }

    private fun checkConversationEntity(conversationEntity: ConversationEntity, apiVersion: Int) {
        assertEquals("1@juwd77g6", conversationEntity.internalId)
        assertEquals(1, conversationEntity.accountId)

        // check if default values are set for the fields when API_V1 is used
        if (apiVersion == 1) {
            checkConversationEntityV1(conversationEntity)
        }

        if (apiVersion >= 1) {
            checkConversationEntityLargerThanV1(conversationEntity)
        }

        if (apiVersion >= 2) {
            assertEquals(false, conversationEntity.canDeleteConversation)
            assertEquals(true, conversationEntity.canLeaveConversation)
        }

        if (apiVersion >= 3) {
            assertEquals("test", conversationEntity.description)
            // assertEquals("", conversationEntity.attendeeId)      // Not implemented
            // assertEquals("", conversationEntity.attendeePin)     // Not implemented
            assertEquals("users", conversationEntity.actorType)
            assertEquals("marcel2", conversationEntity.actorId)

            // assertEquals("", conversationEntity.listable)     // Not implemented
            assertEquals(0, conversationEntity.callFlag)
            // assertEquals("", conversationEntity.sipEnabled)     // Not implemented
            // assertEquals("", conversationEntity.canEnableSIP)     // Not implemented
            assertEquals(92320, conversationEntity.lastCommonReadMessage)
        }

        if (apiVersion >= 4) {
            checkConversationEntityV4(conversationEntity)
        }
    }

    private fun checkConversationEntityLargerThanV1(conversationEntity: ConversationEntity) {
        assertEquals("juwd77g6", conversationEntity.token)
        assertEquals(ConversationEnums.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL, conversationEntity.type)
        assertEquals("marcel", conversationEntity.name)
        assertEquals("Marcel", conversationEntity.displayName)
        assertEquals(Participant.ParticipantType.OWNER, conversationEntity.participantType)
        assertEquals(
            ConversationEnums.ConversationReadOnlyState.CONVERSATION_READ_WRITE,
            conversationEntity.conversationReadOnlyState
        )
        assertEquals(1727185155, conversationEntity.lastPing)
        assertEquals("0", conversationEntity.sessionId)
        assertEquals(false, conversationEntity.hasPassword)
        assertEquals(false, conversationEntity.hasCall)
        assertEquals(true, conversationEntity.canStartCall)
        assertEquals(1727098966, conversationEntity.lastActivity)
        assertEquals(false, conversationEntity.favorite)
        assertEquals(ConversationEnums.NotificationLevel.ALWAYS, conversationEntity.notificationLevel)
        assertEquals(ConversationEnums.LobbyState.LOBBY_STATE_ALL_PARTICIPANTS, conversationEntity.lobbyState)
        assertEquals(0, conversationEntity.lobbyTimer)
        assertEquals(0, conversationEntity.unreadMessages)
        assertEquals(false, conversationEntity.unreadMention)
        assertEquals(92320, conversationEntity.lastReadMessage)
        assertNotNull(conversationEntity.lastMessage)
        assertTrue(conversationEntity.lastMessage is String)
        assertTrue(conversationEntity.lastMessage!!.contains("token"))
        assertEquals(ConversationEnums.ObjectType.DEFAULT, conversationEntity.objectType)
    }

    private fun checkConversationEntityV4(conversationEntity: ConversationEntity) {
        assertEquals("143a9df3", conversationEntity.avatarVersion)
        assertEquals(0, conversationEntity.callStartTime)
        assertEquals(0, conversationEntity.callRecording)
        assertEquals(false, conversationEntity.unreadMentionDirect)
        // assertEquals(, conversationEntity.breakoutRoomMode)     // Not implemented
        // assertEquals(, conversationEntity.breakoutRoomStatus)     // Not implemented
        assertEquals("away", conversationEntity.status)
        assertEquals("👻", conversationEntity.statusIcon)
        assertEquals("buuuuh", conversationEntity.statusMessage)
        assertEquals(null, conversationEntity.statusClearAt)
        assertEquals("143a9df3", conversationEntity.avatarVersion)
        // assertEquals("", conversationEntity.isCustomAvatar)     // Not implemented
        assertEquals(0, conversationEntity.callStartTime)
        assertEquals(0, conversationEntity.callRecording)
        // assertEquals("", conversationEntity.recordingConsent)     // Not implemented
        // assertEquals("", conversationEntity.mentionPermissions)     // Not implemented
        // assertEquals("", conversationEntity.isArchived)     // Not implemented
    }

    private fun checkConversationEntityV1(conversationEntity: ConversationEntity) {
        // default values for API_V2 fields
        assertEquals(false, conversationEntity.canDeleteConversation)
        assertEquals(true, conversationEntity.canLeaveConversation)

        // default values for API_V3 fields
        assertEquals("", conversationEntity.description)
        assertEquals("", conversationEntity.actorType)
        assertEquals("", conversationEntity.actorId)
        assertEquals(0, conversationEntity.callFlag)
        assertEquals(0, conversationEntity.lastCommonReadMessage)

        // default values for API_V4 fields
        assertEquals("", conversationEntity.avatarVersion)
        assertEquals(0, conversationEntity.callStartTime)
        assertEquals(0, conversationEntity.callRecording)
        assertEquals(false, conversationEntity.unreadMentionDirect)
        assertEquals("", conversationEntity.status)
        assertEquals("", conversationEntity.statusIcon)
        assertEquals("", conversationEntity.statusMessage)
        assertEquals(null, conversationEntity.statusClearAt)
        assertEquals("", conversationEntity.avatarVersion)
        assertEquals(0, conversationEntity.callStartTime)
        assertEquals(0, conversationEntity.callRecording)
    }
}
