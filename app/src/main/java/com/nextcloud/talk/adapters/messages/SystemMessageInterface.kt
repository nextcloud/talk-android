/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import com.nextcloud.talk.models.json.chat.ChatMessage

interface SystemMessageInterface {
    fun expandSystemMessage(chatMessage: ChatMessage)
    fun collapseSystemMessages()
}
