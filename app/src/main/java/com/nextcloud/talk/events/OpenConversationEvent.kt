/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.events

import android.os.Bundle
import com.nextcloud.talk.models.json.conversations.Conversation

class OpenConversationEvent {
    var conversation: Conversation? = null
    var bundle: Bundle? = null

    constructor(conversation: Conversation?, bundle: Bundle?) {
        this.conversation = conversation
        this.bundle = bundle
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OpenConversationEvent

        if (conversation != other.conversation) return false
        if (bundle != other.bundle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = conversation?.hashCode() ?: 0
        result = 31 * result + (bundle?.hashCode() ?: 0)
        return result
    }
}
