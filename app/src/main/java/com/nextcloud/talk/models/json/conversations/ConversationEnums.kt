/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.conversations

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

class ConversationEnums {
    enum class NotificationLevel {
        DEFAULT,
        ALWAYS,
        MENTION,
        NEVER
    }

    enum class LobbyState {
        LOBBY_STATE_ALL_PARTICIPANTS,
        LOBBY_STATE_MODERATORS_ONLY
    }

    enum class ConversationReadOnlyState {
        CONVERSATION_READ_WRITE,
        CONVERSATION_READ_ONLY
    }

    @Parcelize
    enum class ConversationType : Parcelable {
        DUMMY,
        ROOM_TYPE_ONE_TO_ONE_CALL,
        ROOM_GROUP_CALL,
        ROOM_PUBLIC_CALL,
        ROOM_SYSTEM,
        FORMER_ONE_TO_ONE,
        NOTE_TO_SELF
    }

    enum class ObjectType {
        DEFAULT,
        SHARE_PASSWORD,
        FILE,
        ROOM,
        EVENT,
        PHONE_TEMPORARY,
        PHONE_PERSIST,
        INSTANT_MEETING
    }
}
