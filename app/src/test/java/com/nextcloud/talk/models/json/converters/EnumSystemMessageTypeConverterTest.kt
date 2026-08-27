/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.converters

import com.nextcloud.talk.chat.data.model.ChatMessage
import org.junit.Assert
import org.junit.Test

class EnumSystemMessageTypeConverterTest {

    private val converter = EnumSystemMessageTypeConverter()

    @Test
    fun getFromString_mapsPreserveConversation() {
        Assert.assertEquals(
            ChatMessage.SystemMessageType.PRESERVE_CONVERSATION,
            converter.getFromString("preserve_conversation")
        )
        Assert.assertEquals(
            ChatMessage.SystemMessageType.PRESERVE_CONVERSATION_OFF,
            converter.getFromString("preserve_conversation_off")
        )
    }

    @Test
    fun getFromString_mapsPhoneRemoved() {
        Assert.assertEquals(
            ChatMessage.SystemMessageType.PHONE_REMOVED,
            converter.getFromString("phone_removed")
        )
    }

    @Test
    fun getFromString_dummyOnlyForAbsentSystemMessage() {
        Assert.assertEquals(ChatMessage.SystemMessageType.DUMMY, converter.getFromString(""))
        Assert.assertEquals(ChatMessage.SystemMessageType.DUMMY, converter.getFromString(null))
    }

    @Test
    fun getFromString_unknownIdentifierStaysASystemMessage() {
        val type = converter.getFromString("some_future_server_message")
        Assert.assertEquals(ChatMessage.SystemMessageType.UNKNOWN, type)
        Assert.assertTrue(ChatMessage(systemMessageType = type).isSystemMessage)
    }

    @Test
    fun everyTypeSurvivesARoundTrip() {
        ChatMessage.SystemMessageType.entries.forEach { type ->
            Assert.assertEquals(
                "$type is not restored from its serialized form",
                type,
                converter.getFromString(converter.convertToString(type))
            )
        }
    }
}
