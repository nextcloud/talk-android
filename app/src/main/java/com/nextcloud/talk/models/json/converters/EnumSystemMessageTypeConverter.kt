/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.converters

import com.bluelinelabs.logansquare.typeconverters.StringBasedTypeConverter
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.AUDIO_RECORDING_STARTED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.AUDIO_RECORDING_STOPPED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.AVATAR_REMOVED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.AVATAR_SET
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.BREAKOUT_ROOMS_STARTED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.BREAKOUT_ROOMS_STOPPED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.CALL_ENDED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.CALL_ENDED_EVERYONE
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.CALL_JOINED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.CALL_LEFT
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.CALL_MISSED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.CALL_STARTED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.CALL_TRIED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.CIRCLE_ADDED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.CIRCLE_REMOVED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.CLEARED_CHAT
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.CONVERSATION_CREATED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.CONVERSATION_RENAMED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.DESCRIPTION_REMOVED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.DESCRIPTION_SET
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.DUMMY
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.FEDERATED_USER_ADDED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.FEDERATED_USER_REMOVED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.FILE_SHARED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.GROUP_ADDED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.GROUP_REMOVED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.GUESTS_ALLOWED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.GUESTS_DISALLOWED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.GUEST_MODERATOR_DEMOTED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.GUEST_MODERATOR_PROMOTED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.LISTABLE_ALL
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.LISTABLE_NONE
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.LISTABLE_USERS
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.LOBBY_NONE
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.LOBBY_NON_MODERATORS
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.LOBBY_OPEN_TO_EVERYONE
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.MATTERBRIDGE_CONFIG_ADDED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.MATTERBRIDGE_CONFIG_DISABLED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.MATTERBRIDGE_CONFIG_EDITED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.MATTERBRIDGE_CONFIG_ENABLED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.MATTERBRIDGE_CONFIG_REMOVED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.MESSAGE_DELETED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.MESSAGE_EXPIRATION_DISABLED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.MESSAGE_EXPIRATION_ENABLED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.MODERATOR_DEMOTED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.MODERATOR_PROMOTED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.OBJECT_SHARED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.PASSWORD_REMOVED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.PASSWORD_SET
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.POLL_CLOSED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.POLL_VOTED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.REACTION
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.REACTION_DELETED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.REACTION_REVOKED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.READ_ONLY
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.READ_ONLY_OFF
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.RECORDING_FAILED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.RECORDING_STARTED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.RECORDING_STOPPED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.USER_ADDED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.USER_REMOVED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.PHONE_ADDED
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType.THREAD_CREATED

/*
* see https://nextcloud-talk.readthedocs.io/en/latest/chat/#system-messages
*
*/
class EnumSystemMessageTypeConverter : StringBasedTypeConverter<ChatMessage.SystemMessageType>() {
    @Suppress("Detekt.LongMethod")
    override fun getFromString(string: String): ChatMessage.SystemMessageType =
        when (string) {
            "conversation_created" -> CONVERSATION_CREATED
            "conversation_renamed" -> CONVERSATION_RENAMED
            "description_set" -> DESCRIPTION_SET
            "description_removed" -> DESCRIPTION_REMOVED
            "call_started" -> CALL_STARTED
            "call_joined" -> CALL_JOINED
            "call_left" -> CALL_LEFT
            "call_ended" -> CALL_ENDED
            "call_ended_everyone" -> CALL_ENDED_EVERYONE
            "call_missed" -> CALL_MISSED
            "call_tried" -> CALL_TRIED
            "read_only_off" -> READ_ONLY_OFF
            "read_only" -> READ_ONLY
            "listable_none" -> LISTABLE_NONE
            "listable_users" -> LISTABLE_USERS
            "listable_all" -> LISTABLE_ALL
            "lobby_none" -> LOBBY_NONE
            "lobby_non_moderators" -> LOBBY_NON_MODERATORS
            "lobby_timer_reached" -> LOBBY_OPEN_TO_EVERYONE
            "guests_allowed" -> GUESTS_ALLOWED
            "guests_disallowed" -> GUESTS_DISALLOWED
            "password_set" -> PASSWORD_SET
            "password_removed" -> PASSWORD_REMOVED
            "user_added" -> USER_ADDED
            "user_removed" -> USER_REMOVED
            "group_added" -> GROUP_ADDED
            "group_removed" -> GROUP_REMOVED
            "circle_added" -> CIRCLE_ADDED
            "circle_removed" -> CIRCLE_REMOVED
            "moderator_promoted" -> MODERATOR_PROMOTED
            "moderator_demoted" -> MODERATOR_DEMOTED
            "guest_moderator_promoted" -> GUEST_MODERATOR_PROMOTED
            "guest_moderator_demoted" -> GUEST_MODERATOR_DEMOTED
            "message_deleted" -> MESSAGE_DELETED
            "message_edited" -> ChatMessage.SystemMessageType.MESSAGE_EDITED
            "file_shared" -> FILE_SHARED
            "object_shared" -> OBJECT_SHARED
            "matterbridge_config_added" -> MATTERBRIDGE_CONFIG_ADDED
            "matterbridge_config_edited" -> MATTERBRIDGE_CONFIG_EDITED
            "matterbridge_config_removed" -> MATTERBRIDGE_CONFIG_REMOVED
            "matterbridge_config_enabled" -> MATTERBRIDGE_CONFIG_ENABLED
            "matterbridge_config_disabled" -> MATTERBRIDGE_CONFIG_DISABLED
            "history_cleared" -> CLEARED_CHAT
            "reaction" -> REACTION
            "reaction_deleted" -> REACTION_DELETED
            "reaction_revoked" -> REACTION_REVOKED
            "poll_voted" -> POLL_VOTED
            "poll_closed" -> POLL_CLOSED
            "message_expiration_enabled" -> MESSAGE_EXPIRATION_ENABLED
            "message_expiration_disabled" -> MESSAGE_EXPIRATION_DISABLED
            "recording_started" -> RECORDING_STARTED
            "recording_stopped" -> RECORDING_STOPPED
            "audio_recording_started" -> AUDIO_RECORDING_STARTED
            "audio_recording_stopped" -> AUDIO_RECORDING_STOPPED
            "recording_failed" -> RECORDING_FAILED
            "breakout_rooms_started" -> BREAKOUT_ROOMS_STARTED
            "breakout_rooms_stopped" -> BREAKOUT_ROOMS_STOPPED
            "avatar_set" -> AVATAR_SET
            "avatar_removed" -> AVATAR_REMOVED
            "federated_user_added" -> FEDERATED_USER_ADDED
            "federated_user_removed" -> FEDERATED_USER_REMOVED
            "phone_added" -> PHONE_ADDED
            "thread_created" -> THREAD_CREATED
            else -> DUMMY
        }

    @Suppress("Detekt.ComplexMethod", "Detekt.LongMethod")
    override fun convertToString(`object`: ChatMessage.SystemMessageType?): String =
        when (`object`) {
            null -> ""
            CONVERSATION_CREATED -> "conversation_created"
            CONVERSATION_RENAMED -> "conversation_renamed"
            DESCRIPTION_REMOVED -> "description_removed"
            DESCRIPTION_SET -> "description_set"
            CALL_STARTED -> "call_started"
            CALL_JOINED -> "call_joined"
            CALL_LEFT -> "call_left"
            CALL_ENDED -> "call_ended"
            CALL_ENDED_EVERYONE -> "call_ended_everyone"
            CALL_MISSED -> "call_missed"
            CALL_TRIED -> "call_tried"
            READ_ONLY_OFF -> "read_only_off"
            READ_ONLY -> "read_only"
            LISTABLE_NONE -> "listable_none"
            LISTABLE_USERS -> "listable_users"
            LISTABLE_ALL -> "listable_all"
            LOBBY_NONE -> "lobby_none"
            LOBBY_NON_MODERATORS -> "lobby_non_moderators"
            LOBBY_OPEN_TO_EVERYONE -> "lobby_timer_reached"
            GUESTS_ALLOWED -> "guests_allowed"
            GUESTS_DISALLOWED -> "guests_disallowed"
            PASSWORD_SET -> "password_set"
            PASSWORD_REMOVED -> "password_removed"
            USER_ADDED -> "user_added"
            USER_REMOVED -> "user_removed"
            GROUP_ADDED -> "group_added"
            GROUP_REMOVED -> "group_removed"
            CIRCLE_ADDED -> "circle_added"
            CIRCLE_REMOVED -> "circle_removed"
            MODERATOR_PROMOTED -> "moderator_promoted"
            MODERATOR_DEMOTED -> "moderator_demoted"
            GUEST_MODERATOR_PROMOTED -> "guest_moderator_promoted"
            GUEST_MODERATOR_DEMOTED -> "guest_moderator_demoted"
            MESSAGE_DELETED -> "message_deleted"
            ChatMessage.SystemMessageType.MESSAGE_EDITED -> "message_edited"
            FILE_SHARED -> "file_shared"
            OBJECT_SHARED -> "object_shared"
            MATTERBRIDGE_CONFIG_ADDED -> "matterbridge_config_added"
            MATTERBRIDGE_CONFIG_EDITED -> "matterbridge_config_edited"
            MATTERBRIDGE_CONFIG_REMOVED -> "matterbridge_config_removed"
            MATTERBRIDGE_CONFIG_ENABLED -> "matterbridge_config_enabled"
            MATTERBRIDGE_CONFIG_DISABLED -> "matterbridge_config_disabled"
            CLEARED_CHAT -> "clear_history"
            REACTION -> "reaction"
            REACTION_DELETED -> "reaction_deleted"
            REACTION_REVOKED -> "reaction_revoked"
            POLL_VOTED -> "poll_voted"
            POLL_CLOSED -> "poll_closed"
            MESSAGE_EXPIRATION_ENABLED -> "message_expiration_enabled"
            MESSAGE_EXPIRATION_DISABLED -> "message_expiration_disabled"
            RECORDING_STARTED -> "recording_started"
            RECORDING_STOPPED -> "recording_stopped"
            AUDIO_RECORDING_STARTED -> "audio_recording_started"
            AUDIO_RECORDING_STOPPED -> "audio_recording_stopped"
            RECORDING_FAILED -> "recording_failed"
            BREAKOUT_ROOMS_STARTED -> "breakout_rooms_started"
            BREAKOUT_ROOMS_STOPPED -> "breakout_rooms_stopped"
            AVATAR_SET -> "avatar_set"
            AVATAR_REMOVED -> "avatar_removed"
            FEDERATED_USER_ADDED -> "federated_user_added"
            FEDERATED_USER_REMOVED -> "federated_user_removed"
            PHONE_ADDED -> "phone_added"
            THREAD_CREATED -> "thread_created"
            else -> ""
        }
}
