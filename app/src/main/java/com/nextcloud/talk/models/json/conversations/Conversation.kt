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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.converters.ConversationObjectTypeConverter
import com.nextcloud.talk.models.json.converters.EnumLobbyStateConverter
import com.nextcloud.talk.models.json.converters.EnumNotificationLevelConverter
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter
import com.nextcloud.talk.models.json.converters.EnumReadOnlyConversationConverter
import com.nextcloud.talk.models.json.converters.EnumRoomTypeConverter
import com.nextcloud.talk.models.json.participants.Participant.ParticipantType
import com.nextcloud.talk.utils.SpreedFeatures
import com.nextcloud.talk.utils.ConversationUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import kotlinx.parcelize.Parcelize

@Parcelize
@Serializable
data class Conversation(
    @SerialName("id")
    var roomId: String? = null,
    @SerialName("token")
    var token: String? = null,
    @SerialName("name")
    var name: String? = null,
    @SerialName("displayName")
    var displayName: String? = null,
    @SerialName("description")
    var description: String? = null,
    @SerialName("type", typeConverter = EnumRoomTypeConverter::class)
    var type: ConversationType? = null,
    @SerialName("lastPing")
    var lastPing: Long = 0,
    @SerialName("participantType", typeConverter = EnumParticipantTypeConverter::class)
    var participantType: ParticipantType? = null,
    @SerialName("hasPassword")
    var hasPassword: Boolean = false,
    @SerialName("sessionId")
    var sessionId: String? = null,
    @SerialName("actorId")
    var actorId: String? = null,
    @SerialName("actorType")
    var actorType: String? = null,

    var password: String? = null,

    @SerialName("isFavorite")
    var favorite: Boolean = false,

    @SerialName("lastActivity")
    var lastActivity: Long = 0,

    @SerialName("unreadMessages")
    var unreadMessages: Int = 0,

    @SerialName("unreadMention")
    var unreadMention: Boolean = false,

    @SerialName("lastMessage")
    var lastMessage: ChatMessage? = null,

    @SerialName("objectType", typeConverter = ConversationObjectTypeConverter::class)
    var objectType: ObjectType? = null,

    @SerialName("notificationLevel", typeConverter = EnumNotificationLevelConverter::class)
    var notificationLevel: NotificationLevel? = null,

    @SerialName("readOnly", typeConverter = EnumReadOnlyConversationConverter::class)
    var conversationReadOnlyState: ConversationReadOnlyState? = null,

    @SerialName("lobbyState", typeConverter = EnumLobbyStateConverter::class)
    var lobbyState: LobbyState? = null,

    @SerialName("lobbyTimer")
    var lobbyTimer: Long? = null,

    @SerialName("lastReadMessage")
    var lastReadMessage: Int = 0,

    @SerialName("hasCall")
    var hasCall: Boolean = false,

    @SerialName("callFlag")
    var callFlag: Int = 0,

    @SerialName("canStartCall")
    var canStartCall: Boolean = false,

    @SerialName("canLeaveConversation")
    var canLeaveConversation: Boolean? = null,

    @SerialName("canDeleteConversation")
    var canDeleteConversation: Boolean? = null,

    @SerialName("unreadMentionDirect")
    var unreadMentionDirect: Boolean? = null,

    @SerialName("notificationCalls")
    var notificationCalls: Int? = null,

    @SerialName("permissions")
    var permissions: Int = 0,

    @SerialName("messageExpiration")
    var messageExpiration: Int = 0,

    @SerialName("status")
    var status: String? = null,

    @SerialName("statusIcon")
    var statusIcon: String? = null,

    @SerialName("statusMessage")
    var statusMessage: String? = null,

    @SerialName("statusClearAt")
    var statusClearAt: Long? = 0,

    @SerialName("callRecording")
    var callRecording: Int = 0,

    @SerialName("avatarVersion")
    var avatarVersion: String? = null,

    // Be aware that variables with "is" at the beginning will lead to the error:
    // "//@JsonField annotation can only be used on private fields if both getter and setter are present."
    // Instead, name it with "has" at the beginning: isCustomAvatar -> hasCustomAvatar
    @SerialName("isCustomAvatar")
    var hasCustomAvatar: Boolean? = null,

    var callStartTime: Long? = null,

    @SerialName("recordingConsent")
    var recordingConsentRequired: Int = 0,

    var remoteServer: String? = null,

    var remoteToken: String? = null

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
            CapabilitiesUtil.hasSpreedFeatureCapability(
                conversationUser.capabilities?.spreedCapability!!,
                SpreedFeatures.LOCKED_ONE_TO_ONE_ROOMS
            )
    }

    @Deprecated("Use ConversationUtil")
    fun canModerate(conversationUser: User): Boolean {
        return isParticipantOwnerOrModerator &&
            ConversationUtils.isLockedOneToOne(
                ConversationModel.mapToConversationModel(this),
                conversationUser
                    .capabilities?.spreedCapability!!
            ) &&
            type != ConversationType.FORMER_ONE_TO_ONE &&
            !ConversationUtils.isNoteToSelfConversation(ConversationModel.mapToConversationModel(this))
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
        FORMER_ONE_TO_ONE
    }

    enum class ObjectType {
        DEFAULT,
        SHARE_PASSWORD,
        FILE,
        ROOM
    }
}
