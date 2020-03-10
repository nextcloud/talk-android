/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.models.json.conversations

import androidx.annotation.NonNull
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonIgnore
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.nextcloud.talk.models.json.converters.*
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import kotlinx.serialization.Serializable
import lombok.Data
import org.parceler.Parcel
import org.parceler.ParcelConstructor
import java.util.*

@Parcel
@Data
@JsonObject(serializeNullCollectionElements = true, serializeNullObjects = true)
@Serializable
class Conversation {
    @JsonIgnore
    var databaseId: String? = null

    @JsonIgnore
    @NonNull
    var databaseUserId: Long? = null

    @JsonField(name = ["id"])
    var conversationId: String? = null

    @JsonField(name = ["token"])
    var token: String? = null

    @JsonField(name = ["name"])
    var name: String? = null

    @JsonField(name = ["displayName"])
    var displayName: String? = null

    @JsonField(name = ["type"], typeConverter = EnumRoomTypeConverter::class)
    var type: ConversationType? = null

    @JsonField(name = ["count"])
    var count: Long = 0

    /*@JsonField(name = ["lastPing"])
    var lastPing: Long = 0*/
    @JsonField(name = ["numGuests"])
    var numberOfGuests: Long = 0

    /*@JsonField(name = ["guestList"])
    var guestList: HashMap<String, HashMap<String, Any>>? = null*/
    @JsonField(name = ["participants"])
    var participants: HashMap<String, Participant>? = null

    @JsonField(name = ["participantType"], typeConverter = EnumParticipantTypeConverter::class)
    var participantType: Participant.ParticipantType? = null

    @JsonField(name = ["hasPassword"])
    var hasPassword: Boolean = false

    @JsonField(name = ["sessionId"])
    var sessionId: String? = null

    @JsonIgnore
    var password: String? = null

    @JsonField(name = ["isFavorite"])
    var favorite: Boolean = false

    @JsonField(name = ["lastActivity"])
    var lastActivity: Long = 0

    @JsonField(name = ["unreadMessages"])
    var unreadMessages: Int = 0

    @JsonField(name = ["unreadMention"])
    var unreadMention: Boolean = false

    @JsonField(name = ["lastMessage"])
    var lastMessage: ChatMessage? = null

    @JsonField(name = ["objectType"])
    var objectType: String? = null

    @JsonField(name = ["notificationLevel"], typeConverter = EnumNotificationLevelConverter::class)
    var notificationLevel: NotificationLevel? = null

    @JsonField(name = ["readOnly"], typeConverter = EnumReadOnlyConversationConverter::class)
    var conversationReadOnlyState:
            ConversationReadOnlyState? = null

    @JsonField(name = ["lobbyState"], typeConverter = EnumLobbyStateConverter::class)
    var lobbyState: LobbyState? = null

    @JsonField(name = ["lobbyTimer"])
    var lobbyTimer: Long? = 0

    @JsonField(name = ["lastReadMessageId"])
    var lastReadMessageId: Long = 0

    @JsonField(name = ["canStartCall"])
    var canStartCall: Boolean? = true

    @JsonIgnore
    var changing: Boolean = false


    @JsonIgnore
    val isPublic: Boolean = ConversationType.PUBLIC_CONVERSATION == type

    @JsonIgnore
    val isGuest: Boolean =
            Participant.ParticipantType.GUEST == participantType ||
                    Participant.ParticipantType.USER_FOLLOWING_LINK == participantType

    val deleteWarningMessage: String
        get() {
            val resources = NextcloudTalkApplication.sharedApplication!!.resources
            if (type == ConversationType.ONE_TO_ONE_CONVERSATION) {
                return String.format(
                        resources.getString(R.string.nc_delete_conversation_one2one),
                        displayName!!
                )
            } else if (participants!!.size > 1) {
                return resources.getString(R.string.nc_delete_conversation_more)
            }

            return resources.getString(R.string.nc_delete_conversation_default)
        }

    private fun isLockedOneToOne(conversationUser: UserNgEntity): Boolean {
        return type == ConversationType.ONE_TO_ONE_CONVERSATION && conversationUser
                .hasSpreedFeatureCapability(
                        "locked-one-to-one-rooms"
                )
    }

    fun canModerate(conversationUser: UserNgEntity): Boolean {
        return (Participant.ParticipantType.OWNER == participantType || Participant.ParticipantType.MODERATOR == participantType) && !isLockedOneToOne(
                conversationUser
        )
    }

    fun shouldShowLobby(conversationUser: UserNgEntity): Boolean {
        return LobbyState.LOBBY_STATE_MODERATORS_ONLY == lobbyState && !canModerate(
                conversationUser
        )
    }

    fun isLobbyViewApplicable(conversationUser: UserNgEntity): Boolean {
        return !canModerate(
                conversationUser
        ) && (type == ConversationType.GROUP_CONVERSATION || type == ConversationType.PUBLIC_CONVERSATION)
    }

    fun isNameEditable(conversationUser: UserNgEntity): Boolean {
        return canModerate(conversationUser) && ConversationType.ONE_TO_ONE_CONVERSATION != type
    }

    fun canLeave(conversationUser: UserNgEntity): Boolean {
        return !canModerate(
                conversationUser
        ) || type != ConversationType.ONE_TO_ONE_CONVERSATION && participants!!.size > 1
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Conversation

        if (databaseId != other.databaseId) return false
        if (databaseUserId != other.databaseUserId) return false
        if (conversationId != other.conversationId) return false
        if (token != other.token) return false
        if (name != other.name) return false
        if (displayName != other.displayName) return false
        if (type != other.type) return false
        if (count != other.count) return false
        if (numberOfGuests != other.numberOfGuests) return false
        if (participants != other.participants) return false
        if (participantType != other.participantType) return false
        if (hasPassword != other.hasPassword) return false
        if (password != other.password) return false
        if (favorite != other.favorite) return false
        if (lastActivity != other.lastActivity) return false
        if (unreadMessages != other.unreadMessages) return false
        if (unreadMention != other.unreadMention) return false
        if (lastMessage != other.lastMessage) return false
        if (objectType != other.objectType) return false
        if (notificationLevel != other.notificationLevel) return false
        if (conversationReadOnlyState != other.conversationReadOnlyState) return false
        if (lobbyState != other.lobbyState) return false
        if (lobbyTimer != other.lobbyTimer) return false
        if (lastReadMessageId != other.lastReadMessageId) return false
        if (canStartCall != other.canStartCall) return false
        if (changing != other.changing) return false

        return true
    }

    override fun hashCode(): Int {
        var result = databaseUserId?.hashCode() ?: 0
        result = 31 * result + (token?.hashCode() ?: 0)
        return result
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

    @Parcel
    enum class ConversationType @ParcelConstructor constructor(val value: Int = 1) {
        ONE_TO_ONE_CONVERSATION(1),
        GROUP_CONVERSATION(2),
        PUBLIC_CONVERSATION(3),
        SYSTEM_CONVERSATION(4)
    }
}
