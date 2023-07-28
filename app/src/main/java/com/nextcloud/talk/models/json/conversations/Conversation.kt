/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Tim Krüger
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.models.json.conversations

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.converters.ConversationObjectTypeConverter
import com.nextcloud.talk.models.json.converters.EnumLobbyStateConverter
import com.nextcloud.talk.models.json.converters.EnumNotificationLevelConverter
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter
import com.nextcloud.talk.models.json.converters.EnumReadOnlyConversationConverter
import com.nextcloud.talk.models.json.converters.EnumRoomTypeConverter
import com.nextcloud.talk.models.json.participants.Participant.ParticipantType
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class Conversation(
    @JsonField(name = ["id"])
    var roomId: String? = null,
    @JsonField(name = ["token"])
    var token: String? = null,
    @JsonField(name = ["name"])
    var name: String? = null,
    @JsonField(name = ["displayName"])
    var displayName: String? = null,
    @JsonField(name = ["description"])
    var description: String? = null,
    @JsonField(name = ["type"], typeConverter = EnumRoomTypeConverter::class)
    var type: ConversationType? = null,
    @JsonField(name = ["lastPing"])
    var lastPing: Long = 0,
    @JsonField(name = ["participantType"], typeConverter = EnumParticipantTypeConverter::class)
    var participantType: ParticipantType? = null,
    @JsonField(name = ["hasPassword"])
    var hasPassword: Boolean = false,
    @JsonField(name = ["sessionId"])
    var sessionId: String? = null,
    @JsonField(name = ["actorId"])
    var actorId: String? = null,
    @JsonField(name = ["actorType"])
    var actorType: String? = null,

    var password: String? = null,

    @JsonField(name = ["isFavorite"])
    var favorite: Boolean = false,

    @JsonField(name = ["lastActivity"])
    var lastActivity: Long = 0,

    @JsonField(name = ["unreadMessages"])
    var unreadMessages: Int = 0,

    @JsonField(name = ["unreadMention"])
    var unreadMention: Boolean = false,

    @JsonField(name = ["lastMessage"])
    var lastMessage: ChatMessage? = null,

    @JsonField(name = ["objectType"], typeConverter = ConversationObjectTypeConverter::class)
    var objectType: ObjectType? = null,

    @JsonField(name = ["notificationLevel"], typeConverter = EnumNotificationLevelConverter::class)
    var notificationLevel: NotificationLevel? = null,

    @JsonField(name = ["readOnly"], typeConverter = EnumReadOnlyConversationConverter::class)
    var conversationReadOnlyState: ConversationReadOnlyState? = null,

    @JsonField(name = ["lobbyState"], typeConverter = EnumLobbyStateConverter::class)
    var lobbyState: LobbyState? = null,

    @JsonField(name = ["lobbyTimer"])
    var lobbyTimer: Long? = null,

    @JsonField(name = ["lastReadMessage"])
    var lastReadMessage: Int = 0,

    @JsonField(name = ["hasCall"])
    var hasCall: Boolean = false,

    @JsonField(name = ["callFlag"])
    var callFlag: Int = 0,

    @JsonField(name = ["canStartCall"])
    var canStartCall: Boolean = false,

    @JsonField(name = ["canLeaveConversation"])
    var canLeaveConversation: Boolean? = null,

    @JsonField(name = ["canDeleteConversation"])
    var canDeleteConversation: Boolean? = null,

    @JsonField(name = ["unreadMentionDirect"])
    var unreadMentionDirect: Boolean? = null,

    @JsonField(name = ["notificationCalls"])
    var notificationCalls: Int? = null,

    @JsonField(name = ["permissions"])
    var permissions: Int = 0,

    @JsonField(name = ["messageExpiration"])
    var messageExpiration: Int = 0,

    @JsonField(name = ["status"])
    var status: String? = null,

    @JsonField(name = ["statusIcon"])
    var statusIcon: String? = null,

    @JsonField(name = ["statusMessage"])
    var statusMessage: String? = null,

    @JsonField(name = ["statusClearAt"])
    var statusClearAt: Long? = 0,

    @JsonField(name = ["callRecording"])
    var callRecording: Int = 0,

    @JsonField(name = ["avatarVersion"])
    var avatarVersion: String? = null,

    // Be aware that variables with "is" at the beginning will lead to the error:
    // "@JsonField annotation can only be used on private fields if both getter and setter are present."
    // Instead, name it with "has" at the beginning: isCustomAvatar -> hasCustomAvatar
    @JsonField(name = ["isCustomAvatar"])
    var hasCustomAvatar: Boolean? = null,

    @JsonField(name = ["callStartTime"])
    var callStartTime: Long? = null

) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null)

    @Deprecated("Use ConversationUtil")
    val isPublic: Boolean
        get() = ConversationType.ROOM_PUBLIC_CALL == type

    @Deprecated("Use ConversationUtil")
    val isGuest: Boolean
        get() = ParticipantType.GUEST == participantType ||
            ParticipantType.GUEST_MODERATOR == participantType ||
            ParticipantType.USER_FOLLOWING_LINK == participantType

    @Deprecated("Use ConversationUtil")
    val isParticipantOwnerOrModerator: Boolean
        get() = ParticipantType.OWNER == participantType ||
            ParticipantType.GUEST_MODERATOR == participantType ||
            ParticipantType.MODERATOR == participantType

    @Deprecated("Use ConversationUtil")
    private fun isLockedOneToOne(conversationUser: User): Boolean {
        return type == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
            CapabilitiesUtilNew.hasSpreedFeatureCapability(conversationUser, "locked-one-to-one-rooms")
    }

    @Deprecated("Use ConversationUtil")
    fun canModerate(conversationUser: User): Boolean {
        return isParticipantOwnerOrModerator &&
            !isLockedOneToOne(conversationUser) &&
            type != ConversationType.FORMER_ONE_TO_ONE
    }

    @Deprecated("Use ConversationUtil")
    fun isLobbyViewApplicable(conversationUser: User): Boolean {
        return !canModerate(conversationUser) &&
            (type == ConversationType.ROOM_GROUP_CALL || type == ConversationType.ROOM_PUBLIC_CALL)
    }

    @Deprecated("Use ConversationUtil")
    fun isNameEditable(conversationUser: User): Boolean {
        return canModerate(conversationUser) && ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL != type
    }

    @Deprecated("Use ConversationUtil")
    fun canLeave(): Boolean {
        return if (canLeaveConversation != null) {
            // Available since APIv2
            canLeaveConversation!!
        } else {
            true
        }
    }

    @Deprecated("Use ConversationUtil")
    fun canDelete(conversationUser: User): Boolean {
        return if (canDeleteConversation != null) {
            // Available since APIv2
            canDeleteConversation!!
        } else {
            canModerate(conversationUser)
            // Fallback for APIv1
        }
    }

    enum class NotificationLevel {
        DEFAULT, ALWAYS, MENTION, NEVER
    }

    enum class LobbyState {
        LOBBY_STATE_ALL_PARTICIPANTS, LOBBY_STATE_MODERATORS_ONLY
    }

    enum class ConversationReadOnlyState {
        CONVERSATION_READ_WRITE, CONVERSATION_READ_ONLY
    }

    @Parcelize
    enum class ConversationType : Parcelable {
        DUMMY,
        ROOM_TYPE_ONE_TO_ONE_CALL,
        ROOM_GROUP_CALL,
        ROOM_PUBLIC_CALL,
        ROOM_SYSTEM,
        FORMER_ONE_TO_ONE
    }

    enum class ObjectType {
        DEFAULT,
        SHARE_PASSWORD,
        FILE,
        ROOM
    }
}
