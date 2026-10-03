/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.source.local.converters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedHashMapConverterTest {

    private val converter = LinkedHashMapConverter()

    @Test
    fun stringToLinkedHashMap_parsesEmojiReactions() {
        val result = converter.stringToLinkedHashMap("{\"👍\":1,\"👎\":2}")

        assertEquals(linkedMapOf("👍" to 1, "👎" to 2), result)
    }

    @Test
    fun stringToLinkedHashMap_parsesNonEmojiReactionWithColons() {
        val result = converter.stringToLinkedHashMap("{\":thank_you:\":1,\"👍\":3}")

        assertEquals(linkedMapOf(":thank_you:" to 1, "👍" to 3), result)
    }

    @Test
    fun stringToLinkedHashMap_skipsEntryWithoutNumericCount() {
        val result = converter.stringToLinkedHashMap("{\"👍\":x,\"👎\":2}")

        assertEquals(linkedMapOf("👎" to 2), result)
    }

    @Test
    fun stringToLinkedHashMap_returnsEmptyMapForEmptyInput() {
        assertTrue(converter.stringToLinkedHashMap(null).isEmpty())
        assertTrue(converter.stringToLinkedHashMap("").isEmpty())
        assertTrue(converter.stringToLinkedHashMap("{}").isEmpty())
    }
}
