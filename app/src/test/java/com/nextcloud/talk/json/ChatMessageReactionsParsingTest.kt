/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.json

import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.models.json.chat.ChatOverall
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageReactionsParsingTest {

    // The server sends emoji as JSON-escaped surrogate pairs, and reactions use them as object keys.
    // Parsing from a stream takes the UTF-8 byte parser path that Retrofit uses.
    @Test
    fun parsesEscapedEmojiReactionKeysFromStream() {
        val json = """
            {"ocs":{"meta":{"status":"ok","statuscode":200,"message":"OK"},"data":[
              {"id":2,"message":"ESCd83cESCdf89 done","reactions":{"ESCd83cESCdf89":2,"ESCd83dESCdc4d":1},
               "parent":{"id":1,"message":"release","reactions":{"ESC2764ESCfe0f":1}}}
            ]}}
        """.trimIndent().replace("ESC", JSON_UNICODE_ESCAPE)

        val chatOverall = LoganSquare.parse(json.byteInputStream(), ChatOverall::class.java)

        val message = chatOverall.ocs!!.data!!.single()
        assertEquals("🎉 done", message.message)
        assertEquals(mapOf("🎉" to 2, "👍" to 1), message.reactions)
        assertEquals(mapOf("❤️" to 1), message.parentMessage!!.reactions)
    }

    companion object {
        private const val JSON_UNICODE_ESCAPE = "\\" + "u"
    }
}
