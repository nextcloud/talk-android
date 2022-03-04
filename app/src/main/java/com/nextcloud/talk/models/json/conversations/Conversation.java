/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   @author Tim Krüger
 *   Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
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

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.models.database.CapabilitiesUtil;
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
import java.util.Objects;

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
    @JsonField(name = "description")
    public String description;
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
    @JsonField(name = "actorId")
    public String actorId;
    @JsonField(name = "actorType")
    public String actorType;
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
    @JsonField(name = "hasCall")
    public boolean hasCall;
    @JsonField(name = "callFlag")
    public int callFlag;
    @JsonField(name = "canStartCall")
    public boolean canStartCall;

    @JsonField(name = "canLeaveConversation")
    public Boolean canLeaveConversation;

    @JsonField(name = "canDeleteConversation")
    public Boolean canDeleteConversation;

    @JsonField(name = "unreadMentionDirect")
    public Boolean unreadMentionDirect;

    @JsonField(name = "notificationCalls")
    public Integer notificationCalls;

    public boolean isPublic() {
        return (ConversationType.ROOM_PUBLIC_CALL.equals(type));
    }

    public boolean isGuest() {
        return (Participant.ParticipantType.GUEST.equals(participantType) ||
                Participant.ParticipantType.GUEST_MODERATOR.equals(participantType) ||
                Participant.ParticipantType.USER_FOLLOWING_LINK.equals(participantType));
    }

    private boolean isLockedOneToOne(UserEntity conversationUser) {
        return (getType() == ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL &&
                CapabilitiesUtil.hasSpreedFeatureCapability(conversationUser, "locked-one-to-one-rooms"));
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
        if (canLeaveConversation != null) {
            // Available since APIv2
            return canLeaveConversation;
        }
        // Fallback for APIv1
        return !canModerate(conversationUser) ||
            (getType() != ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL && this.participants.size() > 1);
    }

    public boolean canDelete(UserEntity conversationUser) {
        if (canDeleteConversation != null) {
            // Available since APIv2
            return canDeleteConversation;
        }
        // Fallback for APIv1
        return canModerate(conversationUser);
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

    public String getDescription() {
        return this.description;
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

    public Participant.ParticipantType getParticipantType() {
        return this.participantType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getActorType() {
        return actorType;
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

    public boolean getHasCall() {
        return hasCall;
    }

    public int getCallFlag() {
        return this.callFlag;
    }

    public boolean getCanStartCall() {
        return canStartCall;
    }

    public Boolean getUnreadMentionDirect() {
        return unreadMentionDirect;
    }

    public Integer getNotificationCalls() { return notificationCalls; }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
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

    @Deprecated
    public void setParticipants(HashMap<String, HashMap<String, Object>> participants) {
        this.participants = participants;
    }

    public void setParticipantType(Participant.ParticipantType participantType) {
        this.participantType = participantType;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
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

    public void setHasCall(boolean hasCall) {
        this.hasCall = hasCall;
    }

    public void setCallFlag(int callFlag) {
        this.callFlag = callFlag;
    }

    public void setCanStartCall(boolean canStartCall) {
        this.canStartCall = canStartCall;
    }

    public void setUnreadMentionDirect(Boolean unreadMentionDirect) {
        this.unreadMentionDirect = unreadMentionDirect;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Conversation that = (Conversation) o;

        if (lastPing != that.lastPing) {
            return false;
        }
        if (hasPassword != that.hasPassword) {
            return false;
        }
        if (isFavorite != that.isFavorite) {
            return false;
        }
        if (lastActivity != that.lastActivity) {
            return false;
        }
        if (unreadMessages != that.unreadMessages) {
            return false;
        }
        if (unreadMention != that.unreadMention) {
            return false;
        }
        if (lastReadMessage != that.lastReadMessage) {
            return false;
        }
        if (hasCall != that.hasCall) {
            return false;
        }
        if (callFlag != that.callFlag) {
            return false;
        }
        if (canStartCall != that.canStartCall) {
            return false;
        }
        if (!Objects.equals(roomId, that.roomId)) {
            return false;
        }
        if (!token.equals(that.token)) {
            return false;
        }
        if (!Objects.equals(name, that.name)) {
            return false;
        }
        if (!Objects.equals(displayName, that.displayName)) {
            return false;
        }
        if (!Objects.equals(description, that.description)) {
            return false;
        }
        if (type != that.type) {
            return false;
        }
        if (!Objects.equals(participants, that.participants)) {
            return false;
        }
        if (participantType != that.participantType) {
            return false;
        }
        if (!Objects.equals(sessionId, that.sessionId)) {
            return false;
        }
        if (!Objects.equals(actorId, that.actorId)) {
            return false;
        }
        if (!Objects.equals(actorType, that.actorType)) {
            return false;
        }
        if (!Objects.equals(password, that.password)) {
            return false;
        }
        if (!Objects.equals(lastMessage, that.lastMessage)) {
            return false;
        }
        if (!Objects.equals(objectType, that.objectType)) {
            return false;
        }
        if (notificationLevel != that.notificationLevel) {
            return false;
        }
        if (conversationReadOnlyState != that.conversationReadOnlyState) {
            return false;
        }
        if (lobbyState != that.lobbyState) {
            return false;
        }
        if (!Objects.equals(lobbyTimer, that.lobbyTimer)) {
            return false;
        }
        if (!Objects.equals(canLeaveConversation, that.canLeaveConversation)) {
            return false;
        }
        if (!Objects.equals(notificationCalls, that.notificationCalls)) {
            return false;
        }
        return Objects.equals(canDeleteConversation, that.canDeleteConversation);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Conversation;
    }

    @Override
    public int hashCode() {
        int result = roomId != null ? roomId.hashCode() : 0;
        result = 31 * result + token.hashCode();
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (displayName != null ? displayName.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + type.hashCode();
        result = 31 * result + (int) (lastPing ^ (lastPing >>> 32));
        result = 31 * result + (participants != null ? participants.hashCode() : 0);
        result = 31 * result + (participantType != null ? participantType.hashCode() : 0);
        result = 31 * result + (actorId != null ? actorId.hashCode() : 0);
        result = 31 * result + (actorType != null ? actorType.hashCode() : 0);
        result = 31 * result + (hasPassword ? 1 : 0);
        result = 31 * result + (sessionId != null ? sessionId.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (isFavorite ? 1 : 0);
        result = 31 * result + (int) (lastActivity ^ (lastActivity >>> 32));
        result = 31 * result + unreadMessages;
        result = 31 * result + (unreadMention ? 1 : 0);
        result = 31 * result + (lastMessage != null ? lastMessage.hashCode() : 0);
        result = 31 * result + (objectType != null ? objectType.hashCode() : 0);
        result = 31 * result + (notificationLevel != null ? notificationLevel.hashCode() : 0);
        result = 31 * result + (conversationReadOnlyState != null ? conversationReadOnlyState.hashCode() : 0);
        result = 31 * result + (lobbyState != null ? lobbyState.hashCode() : 0);
        result = 31 * result + (lobbyTimer != null ? lobbyTimer.hashCode() : 0);
        result = 31 * result + lastReadMessage;
        result = 31 * result + (hasCall ? 1 : 0);
        result = 31 * result + callFlag;
        result = 31 * result + (canStartCall ? 1 : 0);
        result = 31 * result + (canLeaveConversation != null ? canLeaveConversation.hashCode() : 0);
        result = 31 * result + (canDeleteConversation != null ? canDeleteConversation.hashCode() : 0);
        result = 31 * result + (notificationCalls != null ? notificationCalls.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Conversation{" +
                "roomId='" + roomId + '\'' +
                ", token='" + token + '\'' +
                ", name='" + name + '\'' +
                ", displayName='" + displayName + '\'' +
                ", description='" + description + '\'' +
                ", type=" + type +
                ", lastPing=" + lastPing +
                ", participants=" + participants +
                ", participantType=" + participantType +
                ", actorId=" + actorId +
                ", actorType=" + actorType +
                ", hasPassword=" + hasPassword +
                ", sessionId='" + sessionId + '\'' +
                ", password='" + password + '\'' +
                ", isFavorite=" + isFavorite +
                ", lastActivity=" + lastActivity +
                ", unreadMessages=" + unreadMessages +
                ", unreadMention=" + unreadMention +
                ", lastMessage=" + lastMessage +
                ", objectType='" + objectType + '\'' +
                ", notificationLevel=" + notificationLevel +
                ", conversationReadOnlyState=" + conversationReadOnlyState +
                ", lobbyState=" + lobbyState +
                ", lobbyTimer=" + lobbyTimer +
                ", lastReadMessage=" + lastReadMessage +
                ", hasCall=" + hasCall +
                ", callFlag=" + callFlag +
                ", canStartCall=" + canStartCall +
                ", canLeaveConversation=" + canLeaveConversation +
                ", canDeleteConversation=" + canDeleteConversation +
                ", notificationCalls=" + notificationCalls +
                '}';
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
