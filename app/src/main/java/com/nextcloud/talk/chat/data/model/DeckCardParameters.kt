/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.model

class DeckCardParameters(
    messageParameters: Map<String, Map<String, String>>?
) : RichObjectParameters(messageParameters, "deck-card") {

    val id = string("id")
    val name = string("name")

    val boardName = string("boardname")
    val stackName = string("stackname")

    val link = string("link")
}

