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
package com.nextcloud.talk.models.json.conversations;

import android.content.res.Resources;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.models.json.converters.EnumLobbyStateConverter;
import com.nextcloud.talk.models.json.converters.EnumNotificationLevelConverter;
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter;
import com.nextcloud.talk.models.json.converters.EnumReadOnlyConversationConverter;
import com.nextcloud.talk.models.json.converters.EnumRoomTypeConverter;
import com.nextcloud.talk.models.json.participants.Participant;

import org.parceler.Parcel;

import java.util.HashMap;

@Parcel
@JsonObject
public class Conversation {
    @JsonField(name = "id")
    public String roomId;
    @JsonField(name = "token")
    public String token;
    @JsonField(name = "name")
    public String name;
    @JsonField(name = "displayName")
    public String displayName;
    @JsonField(name = "type", typeConverter = EnumRoomTypeConverter.class)
    public ConversationType type;
    @JsonField(name = "lastPing")
    public long lastPing;
    @Deprecated
    @JsonField(name = "participants")
    public HashMap<String, HashMap<String, Object>> participants;
    @JsonField(name = "participantType", typeConverter = EnumParticipantTypeConverter.class)
    public Participant.ParticipantType participantType;
    @JsonField(name = "hasPassword")
    public boolean hasPassword;
    @JsonField(name = "sessionId")
    public String sessionId;
    public String password;
    @JsonField(name = "isFavorite")
    public boolean isFavorite;
    @JsonField(name = "lastActivity")
    public long lastActivity;
    @JsonField(name = "unreadMessages")
    public int unreadMessages;
    @JsonField(name = "unreadMention")
    public boolean unreadMention;
    @JsonField(name = "lastMessage")
    public ChatMessage lastMessage;
    @JsonField(name = "objectType")
    public String objectType;
    @JsonField(name = "notificationLevel", typeConverter = EnumNotificationLevelConverter.class)
    public NotificationLevel notificationLevel;
    @JsonField(name = "readOnly", typeConverter = EnumReadOnlyConversationConverter.class)
    public ConversationReadOnlyState conversationReadOnlyState;
    @JsonField(name = "lobbyState", typeConverter = EnumLobbyStateConverter.class)
    public LobbyState lobbyState;
    @JsonField(name = "lobbyTimer")
    public Long lobbyTimer;
    @JsonField(name = "lastReadMessage")
    public int lastReadMessage;
    @JsonField(name = "callFlag")
    public int callFlag;

    public boolean isPublic() {
        return (ConversationType.ROOM_PUBLIC_CALL.equals(type));
    }

    public boolean isGuest() {
        return (Participant.ParticipantType.GUEST.equals(participantType) ||
                Participant.ParticipantType.GUEST_MODERATOR.equals(participantType) ||
                Participant.ParticipantType.USER_FOLLOWING_LINK.equals(participantType));
    }

    private boolean isLockedOneToOne(UserEntity conversationUser) {
        return (getType() == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL && conversationUser.hasSpreedFeatureCapability("locked-one-to-one-rooms"));
    }

    public boolean canModerate(UserEntity conversationUser) {
        return (isParticipantOwnerOrModerator() && !isLockedOneToOne(conversationUser));
    }

    public boolean isParticipantOwnerOrModerator() {
        return (Participant.ParticipantType.OWNER.equals(participantType) ||
                Participant.ParticipantType.GUEST_MODERATOR.equals(participantType) ||
                Participant.ParticipantType.MODERATOR.equals(participantType));
    }

    public boolean shouldShowLobby(UserEntity conversationUser) {
        return LobbyState.LOBBY_STATE_MODERATORS_ONLY.equals(getLobbyState()) && !canModerate(conversationUser);
    }

    public boolean isLobbyViewApplicable(UserEntity conversationUser) {
        return !canModerate(conversationUser) && (getType() == ConversationType.ROOM_GROUP_CALL || getType() == ConversationType.ROOM_PUBLIC_CALL);
    }

    public boolean isNameEditable(UserEntity conversationUser) {
        return (canModerate(conversationUser) && !ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL.equals(type));
    }

    public boolean canLeave(UserEntity conversationUser) {
        return !canModerate(conversationUser) ||
                (getType() != ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL && getParticipants().size() > 1);
    }

    public String getDeleteWarningMessage() {
        Resources resources = NextcloudTalkApplication.Companion.getSharedApplication().getResources();
        if (getType() == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
            return String.format(resources.getString(R.string.nc_delete_conversation_one2one),
                    getDisplayName());
        } else if (getParticipants().size() > 1) {
            return resources.getString(R.string.nc_delete_conversation_more);
        }

        return resources.getString(R.string.nc_delete_conversation_default);
    }

    public String getRoomId() {
        return this.roomId;
    }

    public String getToken() {
        return this.token;
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public ConversationType getType() {
        return this.type;
    }

    public long getLastPing() {
        return this.lastPing;
    }

    public HashMap<String, HashMap<String, Object>> getParticipants() {
        return this.participants;
    }

    public Participant.ParticipantType getParticipantType() {
        return this.participantType;
    }

    public boolean isHasPassword() {
        return this.hasPassword;
    }

    public String getSessionId() {
        return this.sessionId;
    }

    public String getPassword() {
        return this.password;
    }

    public boolean isFavorite() {
        return this.isFavorite;
    }

    public long getLastActivity() {
        return this.lastActivity;
    }

    public int getUnreadMessages() {
        return this.unreadMessages;
    }

    public boolean isUnreadMention() {
        return this.unreadMention;
    }

    public ChatMessage getLastMessage() {
        return this.lastMessage;
    }

    public String getObjectType() {
        return this.objectType;
    }

    public NotificationLevel getNotificationLevel() {
        return this.notificationLevel;
    }

    public ConversationReadOnlyState getConversationReadOnlyState() {
        return this.conversationReadOnlyState;
    }

    public LobbyState getLobbyState() {
        return this.lobbyState;
    }

    public Long getLobbyTimer() {
        return this.lobbyTimer;
    }

    public int getLastReadMessage() {
        return this.lastReadMessage;
    }

    public int getCallFlag() {
        return this.callFlag;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setType(ConversationType type) {
        this.type = type;
    }

    public void setLastPing(long lastPing) {
        this.lastPing = lastPing;
    }

    public void setParticipants(HashMap<String, HashMap<String, Object>> participants) {
        this.participants = participants;
    }

    public void setParticipantType(Participant.ParticipantType participantType) {
        this.participantType = participantType;
    }

    public void setHasPassword(boolean hasPassword) {
        this.hasPassword = hasPassword;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setFavorite(boolean isFavorite) {
        this.isFavorite = isFavorite;
    }

    public void setLastActivity(long lastActivity) {
        this.lastActivity = lastActivity;
    }

    public void setUnreadMessages(int unreadMessages) {
        this.unreadMessages = unreadMessages;
    }

    public void setUnreadMention(boolean unreadMention) {
        this.unreadMention = unreadMention;
    }

    public void setLastMessage(ChatMessage lastMessage) {
        this.lastMessage = lastMessage;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public void setNotificationLevel(NotificationLevel notificationLevel) {
        this.notificationLevel = notificationLevel;
    }

    public void setConversationReadOnlyState(ConversationReadOnlyState conversationReadOnlyState) {
        this.conversationReadOnlyState = conversationReadOnlyState;
    }

    public void setLobbyState(LobbyState lobbyState) {
        this.lobbyState = lobbyState;
    }

    public void setLobbyTimer(Long lobbyTimer) {
        this.lobbyTimer = lobbyTimer;
    }

    public void setLastReadMessage(int lastReadMessage) {
        this.lastReadMessage = lastReadMessage;
    }

    public void setCallFlag(int callFlag) {
        this.callFlag = callFlag;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Conversation)) {
            return false;
        }
        final Conversation other = (Conversation) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$roomId = this.getRoomId();
        final Object other$roomId = other.getRoomId();
        if (this$roomId == null ? other$roomId != null : !this$roomId.equals(other$roomId)) {
            return false;
        }
        final Object this$token = this.getToken();
        final Object other$token = other.getToken();
        if (this$token == null ? other$token != null : !this$token.equals(other$token)) {
            return false;
        }
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
            return false;
        }
        final Object this$displayName = this.getDisplayName();
        final Object other$displayName = other.getDisplayName();
        if (this$displayName == null ? other$displayName != null : !this$displayName.equals(other$displayName)) {
            return false;
        }
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
            return false;
        }
        if (this.getLastPing() != other.getLastPing()) {
            return false;
        }
        final Object this$participants = this.getParticipants();
        final Object other$participants = other.getParticipants();
        if (this$participants == null ? other$participants != null : !this$participants.equals(other$participants)) {
            return false;
        }
        final Object this$participantType = this.getParticipantType();
        final Object other$participantType = other.getParticipantType();
        if (this$participantType == null ? other$participantType != null : !this$participantType.equals(other$participantType)) {
            return false;
        }
        if (this.isHasPassword() != other.isHasPassword()) {
            return false;
        }
        final Object this$sessionId = this.getSessionId();
        final Object other$sessionId = other.getSessionId();
        if (this$sessionId == null ? other$sessionId != null : !this$sessionId.equals(other$sessionId)) {
            return false;
        }
        final Object this$password = this.getPassword();
        final Object other$password = other.getPassword();
        if (this$password == null ? other$password != null : !this$password.equals(other$password)) {
            return false;
        }
        if (this.isFavorite() != other.isFavorite()) {
            return false;
        }
        if (this.getLastActivity() != other.getLastActivity()) {
            return false;
        }
        if (this.getUnreadMessages() != other.getUnreadMessages()) {
            return false;
        }
        if (this.isUnreadMention() != other.isUnreadMention()) {
            return false;
        }
        final Object this$lastMessage = this.getLastMessage();
        final Object other$lastMessage = other.getLastMessage();
        if (this$lastMessage == null ? other$lastMessage != null : !this$lastMessage.equals(other$lastMessage)) {
            return false;
        }
        final Object this$objectType = this.getObjectType();
        final Object other$objectType = other.getObjectType();
        if (this$objectType == null ? other$objectType != null : !this$objectType.equals(other$objectType)) {
            return false;
        }
        final Object this$notificationLevel = this.getNotificationLevel();
        final Object other$notificationLevel = other.getNotificationLevel();
        if (this$notificationLevel == null ? other$notificationLevel != null : !this$notificationLevel.equals(other$notificationLevel)) {
            return false;
        }
        final Object this$conversationReadOnlyState = this.getConversationReadOnlyState();
        final Object other$conversationReadOnlyState = other.getConversationReadOnlyState();
        if (this$conversationReadOnlyState == null ? other$conversationReadOnlyState != null : !this$conversationReadOnlyState.equals(other$conversationReadOnlyState)) {
            return false;
        }
        final Object this$lobbyState = this.getLobbyState();
        final Object other$lobbyState = other.getLobbyState();
        if (this$lobbyState == null ? other$lobbyState != null : !this$lobbyState.equals(other$lobbyState)) {
            return false;
        }
        final Object this$lobbyTimer = this.getLobbyTimer();
        final Object other$lobbyTimer = other.getLobbyTimer();
        if (this$lobbyTimer == null ? other$lobbyTimer != null : !this$lobbyTimer.equals(other$lobbyTimer)) {
            return false;
        }
        if (this.getLastReadMessage() != other.getLastReadMessage()) {
            return false;
        }

        return this.getCallFlag() == other.getCallFlag();
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Conversation;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $roomId = this.getRoomId();
        result = result * PRIME + ($roomId == null ? 43 : $roomId.hashCode());
        final Object $token = this.getToken();
        result = result * PRIME + ($token == null ? 43 : $token.hashCode());
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $displayName = this.getDisplayName();
        result = result * PRIME + ($displayName == null ? 43 : $displayName.hashCode());
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final long $lastPing = this.getLastPing();
        result = result * PRIME + (int) ($lastPing >>> 32 ^ $lastPing);
        final Object $participants = this.getParticipants();
        result = result * PRIME + ($participants == null ? 43 : $participants.hashCode());
        final Object $participantType = this.getParticipantType();
        result = result * PRIME + ($participantType == null ? 43 : $participantType.hashCode());
        result = result * PRIME + (this.isHasPassword() ? 79 : 97);
        final Object $sessionId = this.getSessionId();
        result = result * PRIME + ($sessionId == null ? 43 : $sessionId.hashCode());
        final Object $password = this.getPassword();
        result = result * PRIME + ($password == null ? 43 : $password.hashCode());
        result = result * PRIME + (this.isFavorite() ? 79 : 97);
        final long $lastActivity = this.getLastActivity();
        result = result * PRIME + (int) ($lastActivity >>> 32 ^ $lastActivity);
        result = result * PRIME + this.getUnreadMessages();
        result = result * PRIME + (this.isUnreadMention() ? 79 : 97);
        final Object $lastMessage = this.getLastMessage();
        result = result * PRIME + ($lastMessage == null ? 43 : $lastMessage.hashCode());
        final Object $objectType = this.getObjectType();
        result = result * PRIME + ($objectType == null ? 43 : $objectType.hashCode());
        final Object $notificationLevel = this.getNotificationLevel();
        result = result * PRIME + ($notificationLevel == null ? 43 : $notificationLevel.hashCode());
        final Object $conversationReadOnlyState = this.getConversationReadOnlyState();
        result = result * PRIME + ($conversationReadOnlyState == null ? 43 : $conversationReadOnlyState.hashCode());
        final Object $lobbyState = this.getLobbyState();
        result = result * PRIME + ($lobbyState == null ? 43 : $lobbyState.hashCode());
        final Object $lobbyTimer = this.getLobbyTimer();
        result = result * PRIME + ($lobbyTimer == null ? 43 : $lobbyTimer.hashCode());
        result = result * PRIME + this.getLastReadMessage();
        result = result * PRIME + this.getCallFlag();
        return result;
    }

    public String toString() {
        return "Conversation(roomId=" + this.getRoomId() + ", token=" + this.getToken() + ", name=" + this.getName() + ", displayName=" + this.getDisplayName() + ", type=" + this.getType() + ", lastPing=" + this.getLastPing() + ", participants=" + this.getParticipants() + ", participantType=" + this.getParticipantType() + ", hasPassword=" + this.isHasPassword() + ", sessionId=" + this.getSessionId() + ", password=" + this.getPassword() + ", isFavorite=" + this.isFavorite() + ", lastActivity=" + this.getLastActivity() + ", unreadMessages=" + this.getUnreadMessages() + ", unreadMention=" + this.isUnreadMention() + ", lastMessage=" + this.getLastMessage() + ", objectType=" + this.getObjectType() + ", notificationLevel=" + this.getNotificationLevel() + ", conversationReadOnlyState=" + this.getConversationReadOnlyState() + ", lobbyState=" + this.getLobbyState() + ", lobbyTimer=" + this.getLobbyTimer() + ", lastReadMessage=" + this.getLastReadMessage() + ", callFlag=" + this.getCallFlag() + ")";
    }

    public enum NotificationLevel {
        DEFAULT,
        ALWAYS,
        MENTION,
        NEVER
    }

    public enum LobbyState {
        LOBBY_STATE_ALL_PARTICIPANTS,
        LOBBY_STATE_MODERATORS_ONLY
    }

    public enum ConversationReadOnlyState {
        CONVERSATION_READ_WRITE,
        CONVERSATION_READ_ONLY
    }

    @Parcel
    public enum ConversationType {
        DUMMY,
        ROOM_TYPE_ONE_TO_ONE_CALL,
        ROOM_GROUP_CALL,
        ROOM_PUBLIC_CALL,
        ROOM_SYSTEM
    }

}
