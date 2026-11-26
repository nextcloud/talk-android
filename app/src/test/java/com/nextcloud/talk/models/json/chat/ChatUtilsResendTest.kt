/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatUtilsResendTest {

    private fun parameter(vararg entries: Pair<String, String>): HashMap<String?, String?> =
        hashMapOf<String?, String?>().apply { entries.forEach { (key, value) -> put(key, value) } }

    private fun resend(message: String, vararg parameters: Pair<String, HashMap<String?, String?>>): String? =
        ChatUtils.getMessageToResend(message, hashMapOf<String?, HashMap<String?, String?>>(*parameters))

    @Test
    fun `a user mention becomes the mention id, not the display name`() {
        val text = resend(
            "Hi {mention-user1}!",
            "mention-user1" to parameter(
                "type" to "user",
                "id" to "marcel",
                "mention-id" to "marcel",
                "name" to "Marcel Hibbe (Sermo)"
            )
        )

        assertEquals("Hi @marcel!", text)
    }

    @Test
    fun `ids with a space, an at sign or a type prefix are quoted`() {
        assertEquals("@\"john doe\"", ChatUtils.mentionSyntax("john doe"))
        assertEquals(
            "@\"federated_user/alice@cloud.example.com\"",
            ChatUtils.mentionSyntax("federated_user/alice@cloud.example.com")
        )
        assertEquals("@\"guest/abc123\"", ChatUtils.mentionSyntax("guest/abc123"))
        assertEquals("@\"group/admins\"", ChatUtils.mentionSyntax("group/admins"))
        assertEquals("@\"email/abc123\"", ChatUtils.mentionSyntax("email/abc123"))
        assertEquals("@\"team/abc123\"", ChatUtils.mentionSyntax("team/abc123"))
        assertEquals("@alice", ChatUtils.mentionSyntax("alice"))
    }

    @Test
    fun `a group mention is quoted`() {
        val text = resend(
            "{mention-group1} please",
            "mention-group1" to parameter(
                "type" to "user-group",
                "id" to "admins",
                "mention-id" to "group/admins",
                "name" to "Admins"
            )
        )

        assertEquals("@\"group/admins\" please", text)
    }

    @Test
    fun `the mention of the whole conversation becomes its name and notifies nobody`() {
        val text = resend(
            "{mention-call1} meeting",
            "mention-call1" to parameter("type" to "call", "id" to "abcd", "mention-id" to "all", "name" to "Team room")
        )

        assertEquals("Team room meeting", text)
    }

    @Test
    fun `a mention without a mention id becomes the plain name`() {
        val text = resend(
            "Hi {mention-user1}",
            "mention-user1" to parameter("type" to "user", "id" to "marcel", "name" to "Marcel Hibbe (Sermo)")
        )

        assertEquals("Hi Marcel Hibbe (Sermo)", text)
    }

    @Test
    fun `other parameters are resolved to their names`() {
        val text = resend(
            "See {file}",
            "file" to parameter("type" to "file", "id" to "1", "name" to "report.pdf", "link" to "https://example.com")
        )

        assertEquals("See report.pdf", text)
    }
}
