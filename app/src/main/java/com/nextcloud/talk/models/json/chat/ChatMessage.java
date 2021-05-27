/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.models.json.chat;

import android.text.TextUtils;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonIgnore;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.converters.EnumSystemMessageTypeConverter;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.TextMatchers;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.commons.models.IUser;
import com.stfalcon.chatkit.commons.models.MessageContentType;

import org.parceler.Parcel;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;

@Parcel
@JsonObject
public class ChatMessage implements IMessage, MessageContentType, MessageContentType.Image {
    @JsonIgnore
    public boolean isGrouped;
    @JsonIgnore
    public boolean isOneToOneConversation;
    @JsonIgnore
    public UserEntity activeUser;
    @JsonIgnore
    public Map<String, String> selectedIndividualHashMap;
    @JsonIgnore
    public boolean isLinkPreviewAllowed;
    @JsonIgnore
    public boolean isDeleted;
    @JsonField(name = "id")
    public int jsonMessageId;
    @JsonField(name = "token")
    public String token;
    // guests or users
    @JsonField(name = "actorType")
    public String actorType;
    @JsonField(name = "actorId")
    public String actorId;
    // send when crafting a message
    @JsonField(name = "actorDisplayName")
    public String actorDisplayName;
    @JsonField(name = "timestamp")
    public long timestamp;
    // send when crafting a message, max 1000 lines
    @JsonField(name = "message")
    public String message;
    @JsonField(name = "messageParameters")
    public HashMap<String, HashMap<String, String>> messageParameters;
    @JsonField(name = "systemMessage", typeConverter = EnumSystemMessageTypeConverter.class)
    public SystemMessageType systemMessageType;
    @JsonField(name = "isReplyable")
    public boolean replyable;
    @JsonField(name = "parent")
    public ChatMessage parentMessage;
    public Enum<ReadStatus> readStatus = ReadStatus.NONE;

    @JsonIgnore
    List<MessageType> messageTypesToIgnore = Arrays.asList(MessageType.REGULAR_TEXT_MESSAGE,
            MessageType.SYSTEM_MESSAGE, MessageType.SINGLE_LINK_VIDEO_MESSAGE,
            MessageType.SINGLE_LINK_AUDIO_MESSAGE, MessageType.SINGLE_LINK_MESSAGE);

    public boolean hasFileAttachment() {
        if (messageParameters != null && messageParameters.size() > 0) {
            for (String key : messageParameters.keySet()) {
                Map<String, String> individualHashMap = messageParameters.get(key);
                if (individualHashMap.get("type").equals("file")) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    @Override
    public String getImageUrl() {
        if (messageParameters != null && messageParameters.size() > 0) {
            for (String key : messageParameters.keySet()) {
                Map<String, String> individualHashMap = messageParameters.get(key);
                if (individualHashMap.get("type").equals("file")) {
                    selectedIndividualHashMap = individualHashMap;
                    return (ApiUtils.getUrlForFilePreviewWithFileId(getActiveUser().getBaseUrl(),
                            individualHashMap.get("id"), NextcloudTalkApplication.Companion.getSharedApplication().getResources().getDimensionPixelSize(R.dimen.maximum_file_preview_size)));
                }
            }
        }

        if (!messageTypesToIgnore.contains(getMessageType()) && isLinkPreviewAllowed) {
            return getMessage().trim();
        }

        return null;
    }

    public MessageType getMessageType() {
        if (!TextUtils.isEmpty(getSystemMessage())) {
            return MessageType.SYSTEM_MESSAGE;
        }

        if (hasFileAttachment()) {
            return MessageType.SINGLE_NC_ATTACHMENT_MESSAGE;
        }

        return TextMatchers.getMessageTypeFromString(getText());
    }

    public Map<String, String> getSelectedIndividualHashMap() {
        return selectedIndividualHashMap;
    }

    public void setSelectedIndividualHashMap(Map<String, String> selectedIndividualHashMap) {
        this.selectedIndividualHashMap = selectedIndividualHashMap;
    }

    @Override
    public String getId() {
        return Integer.toString(jsonMessageId);
    }

    @Override
    public String getText() {
        return ChatUtils.Companion.getParsedMessage(getMessage(), getMessageParameters());
    }

    public String getLastMessageDisplayText() {
        if (getMessageType().equals(MessageType.REGULAR_TEXT_MESSAGE) || getMessageType().equals(MessageType.SYSTEM_MESSAGE) || getMessageType().equals(MessageType.SINGLE_LINK_MESSAGE)) {
            return getText();
        } else {
            if (getMessageType().equals(MessageType.SINGLE_LINK_GIPHY_MESSAGE)
                    || getMessageType().equals(MessageType.SINGLE_LINK_TENOR_MESSAGE)
                    || getMessageType().equals(MessageType.SINGLE_LINK_GIF_MESSAGE)) {
                if (getActorId().equals(getActiveUser().getUserId())) {
                    return (NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_sent_a_gif_you));
                } else {
                    return (String.format(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getString(R.string.nc_sent_a_gif),
                            !TextUtils.isEmpty(getActorDisplayName()) ? getActorDisplayName() : NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest)));
                }
            } else if (getMessageType().equals(MessageType.SINGLE_NC_ATTACHMENT_MESSAGE)) {
                if (getActorId().equals(getActiveUser().getUserId())) {
                    return (NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_sent_an_attachment_you));
                } else {
                    return (String.format(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getString(R.string.nc_sent_an_attachment),
                            !TextUtils.isEmpty(getActorDisplayName()) ? getActorDisplayName() : NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest)));
                }
            /*} else if (getMessageType().equals(MessageType.SINGLE_LINK_MESSAGE)) {
                if (getActorId().equals(getActiveUser().getUserId())) {
                    return (NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_sent_a_link_you));
                } else {
                    return (String.format(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getString(R.string.nc_sent_a_link),
                            !TextUtils.isEmpty(getActorDisplayName()) ? getActorDisplayName() : NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest)));
                }*/
            } else if (getMessageType().equals(MessageType.SINGLE_LINK_AUDIO_MESSAGE)) {
                if (getActorId().equals(getActiveUser().getUserId())) {
                    return (NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_sent_an_audio_you));
                } else {
                    return (String.format(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getString(R.string.nc_sent_an_audio),
                            !TextUtils.isEmpty(getActorDisplayName()) ? getActorDisplayName() : NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest)));
                }
            } else if (getMessageType().equals(MessageType.SINGLE_LINK_VIDEO_MESSAGE)) {
                if (getActorId().equals(getActiveUser().getUserId())) {
                    return (NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_sent_a_video_you));
                } else {
                    return (String.format(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getString(R.string.nc_sent_a_video),
                            !TextUtils.isEmpty(getActorDisplayName()) ? getActorDisplayName() : NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest)));
                }
            } else if (getMessageType().equals(MessageType.SINGLE_LINK_IMAGE_MESSAGE)) {
                if (getActorId().equals(getActiveUser().getUserId())) {
                    return (NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_sent_an_image_you));
                } else {
                    return (String.format(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getString(R.string.nc_sent_an_image),
                            !TextUtils.isEmpty(getActorDisplayName()) ? getActorDisplayName() : NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest)));
                }
            }
        }

        return "";
    }

    @Override
    public IUser getUser() {
        return new IUser() {
            @Override
            public String getId() {
                return actorId;
            }

            @Override
            public String getName() {
                return actorDisplayName;
            }

            @Override
            public String getAvatar() {
                if (getActorType().equals("users")) {
                    return ApiUtils.getUrlForAvatarWithName(getActiveUser().getBaseUrl(), actorId, R.dimen.avatar_size);
                } else if (getActorType().equals("guests")) {
                    String apiId =
                            NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest);

                    if (!TextUtils.isEmpty(getActorDisplayName())) {
                        apiId = getActorDisplayName();
                    }
                    return ApiUtils.getUrlForAvatarWithNameForGuests(getActiveUser().getBaseUrl(), apiId, R.dimen.avatar_size);
                } else {
                    return null;
                }
            }
        };
    }

    @Override
    public Date getCreatedAt() {
        return new Date(timestamp * 1000L);
    }

    @Override
    public String getSystemMessage() {
        return new EnumSystemMessageTypeConverter().convertToString(getSystemMessageType());
    }

    public boolean isGrouped() {
        return this.isGrouped;
    }

    public boolean isOneToOneConversation() {
        return this.isOneToOneConversation;
    }

    public UserEntity getActiveUser() {
        return this.activeUser;
    }

    public boolean isLinkPreviewAllowed() {
        return this.isLinkPreviewAllowed;
    }

    public boolean isDeleted() {
        return this.isDeleted;
    }

    public int getJsonMessageId() {
        return this.jsonMessageId;
    }

    public String getToken() {
        return this.token;
    }

    public String getActorType() {
        return this.actorType;
    }

    public String getActorId() {
        return this.actorId;
    }

    public String getActorDisplayName() {
        return this.actorDisplayName;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public String getMessage() {
        return this.message;
    }

    public HashMap<String, HashMap<String, String>> getMessageParameters() {
        return this.messageParameters;
    }

    public SystemMessageType getSystemMessageType() {
        return this.systemMessageType;
    }

    public boolean isReplyable() {
        return this.replyable;
    }

    public ChatMessage getParentMessage() {
        return this.parentMessage;
    }

    public Enum<ReadStatus> getReadStatus() {
        return this.readStatus;
    }

    public List<MessageType> getMessageTypesToIgnore() {
        return this.messageTypesToIgnore;
    }

    public void setGrouped(boolean isGrouped) {
        this.isGrouped = isGrouped;
    }

    public void setOneToOneConversation(boolean isOneToOneConversation) {
        this.isOneToOneConversation = isOneToOneConversation;
    }

    public void setActiveUser(UserEntity activeUser) {
        this.activeUser = activeUser;
    }

    public void setLinkPreviewAllowed(boolean isLinkPreviewAllowed) {
        this.isLinkPreviewAllowed = isLinkPreviewAllowed;
    }

    public void setDeleted(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public void setJsonMessageId(int jsonMessageId) {
        this.jsonMessageId = jsonMessageId;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setActorType(String actorType) {
        this.actorType = actorType;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public void setActorDisplayName(String actorDisplayName) {
        this.actorDisplayName = actorDisplayName;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setMessageParameters(HashMap<String, HashMap<String, String>> messageParameters) {
        this.messageParameters = messageParameters;
    }

    public void setSystemMessageType(SystemMessageType systemMessageType) {
        this.systemMessageType = systemMessageType;
    }

    public void setReplyable(boolean replyable) {
        this.replyable = replyable;
    }

    public void setParentMessage(ChatMessage parentMessage) {
        this.parentMessage = parentMessage;
    }

    public void setReadStatus(Enum<ReadStatus> readStatus) {
        this.readStatus = readStatus;
    }

    public void setMessageTypesToIgnore(List<MessageType> messageTypesToIgnore) {
        this.messageTypesToIgnore = messageTypesToIgnore;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ChatMessage)) {
            return false;
        }
        final ChatMessage other = (ChatMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (this.isGrouped() != other.isGrouped()) {
            return false;
        }
        if (this.isOneToOneConversation() != other.isOneToOneConversation()) {
            return false;
        }
        final Object this$activeUser = this.getActiveUser();
        final Object other$activeUser = other.getActiveUser();
        if (this$activeUser == null ? other$activeUser != null : !this$activeUser.equals(other$activeUser)) {
            return false;
        }
        final Object this$selectedIndividualHashMap = this.getSelectedIndividualHashMap();
        final Object other$selectedIndividualHashMap = other.getSelectedIndividualHashMap();
        if (this$selectedIndividualHashMap == null ? other$selectedIndividualHashMap != null : !this$selectedIndividualHashMap.equals(other$selectedIndividualHashMap)) {
            return false;
        }
        if (this.isLinkPreviewAllowed() != other.isLinkPreviewAllowed()) {
            return false;
        }
        if (this.isDeleted() != other.isDeleted()) {
            return false;
        }
        if (this.getJsonMessageId() != other.getJsonMessageId()) {
            return false;
        }
        final Object this$token = this.getToken();
        final Object other$token = other.getToken();
        if (this$token == null ? other$token != null : !this$token.equals(other$token)) {
            return false;
        }
        final Object this$actorType = this.getActorType();
        final Object other$actorType = other.getActorType();
        if (this$actorType == null ? other$actorType != null : !this$actorType.equals(other$actorType)) {
            return false;
        }
        final Object this$actorId = this.getActorId();
        final Object other$actorId = other.getActorId();
        if (this$actorId == null ? other$actorId != null : !this$actorId.equals(other$actorId)) {
            return false;
        }
        final Object this$actorDisplayName = this.getActorDisplayName();
        final Object other$actorDisplayName = other.getActorDisplayName();
        if (this$actorDisplayName == null ? other$actorDisplayName != null : !this$actorDisplayName.equals(other$actorDisplayName)) {
            return false;
        }
        if (this.getTimestamp() != other.getTimestamp()) {
            return false;
        }
        final Object this$message = this.getMessage();
        final Object other$message = other.getMessage();
        if (this$message == null ? other$message != null : !this$message.equals(other$message)) {
            return false;
        }
        final Object this$messageParameters = this.getMessageParameters();
        final Object other$messageParameters = other.getMessageParameters();
        if (this$messageParameters == null ? other$messageParameters != null : !this$messageParameters.equals(other$messageParameters)) {
            return false;
        }
        final Object this$systemMessageType = this.getSystemMessageType();
        final Object other$systemMessageType = other.getSystemMessageType();
        if (this$systemMessageType == null ? other$systemMessageType != null : !this$systemMessageType.equals(other$systemMessageType)) {
            return false;
        }
        if (this.isReplyable() != other.isReplyable()) {
            return false;
        }
        final Object this$parentMessage = this.getParentMessage();
        final Object other$parentMessage = other.getParentMessage();
        if (this$parentMessage == null ? other$parentMessage != null : !this$parentMessage.equals(other$parentMessage)) {
            return false;
        }
        final Object this$readStatus = this.getReadStatus();
        final Object other$readStatus = other.getReadStatus();
        if (this$readStatus == null ? other$readStatus != null : !this$readStatus.equals(other$readStatus)) {
            return false;
        }
        final Object this$messageTypesToIgnore = this.getMessageTypesToIgnore();
        final Object other$messageTypesToIgnore = other.getMessageTypesToIgnore();

        return this$messageTypesToIgnore == null ? other$messageTypesToIgnore == null : this$messageTypesToIgnore.equals(other$messageTypesToIgnore);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ChatMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this.isGrouped() ? 79 : 97);
        result = result * PRIME + (this.isOneToOneConversation() ? 79 : 97);
        final Object $activeUser = this.getActiveUser();
        result = result * PRIME + ($activeUser == null ? 43 : $activeUser.hashCode());
        final Object $selectedIndividualHashMap = this.getSelectedIndividualHashMap();
        result = result * PRIME + ($selectedIndividualHashMap == null ? 43 : $selectedIndividualHashMap.hashCode());
        result = result * PRIME + (this.isLinkPreviewAllowed() ? 79 : 97);
        result = result * PRIME + (this.isDeleted() ? 79 : 97);
        result = result * PRIME + this.getJsonMessageId();
        final Object $token = this.getToken();
        result = result * PRIME + ($token == null ? 43 : $token.hashCode());
        final Object $actorType = this.getActorType();
        result = result * PRIME + ($actorType == null ? 43 : $actorType.hashCode());
        final Object $actorId = this.getActorId();
        result = result * PRIME + ($actorId == null ? 43 : $actorId.hashCode());
        final Object $actorDisplayName = this.getActorDisplayName();
        result = result * PRIME + ($actorDisplayName == null ? 43 : $actorDisplayName.hashCode());
        final long $timestamp = this.getTimestamp();
        result = result * PRIME + (int) ($timestamp >>> 32 ^ $timestamp);
        final Object $message = this.getMessage();
        result = result * PRIME + ($message == null ? 43 : $message.hashCode());
        final Object $messageParameters = this.getMessageParameters();
        result = result * PRIME + ($messageParameters == null ? 43 : $messageParameters.hashCode());
        final Object $systemMessageType = this.getSystemMessageType();
        result = result * PRIME + ($systemMessageType == null ? 43 : $systemMessageType.hashCode());
        result = result * PRIME + (this.isReplyable() ? 79 : 97);
        final Object $parentMessage = this.getParentMessage();
        result = result * PRIME + ($parentMessage == null ? 43 : $parentMessage.hashCode());
        final Object $readStatus = this.getReadStatus();
        result = result * PRIME + ($readStatus == null ? 43 : $readStatus.hashCode());
        final Object $messageTypesToIgnore = this.getMessageTypesToIgnore();
        result = result * PRIME + ($messageTypesToIgnore == null ? 43 : $messageTypesToIgnore.hashCode());
        return result;
    }

    public String toString() {
        return "ChatMessage(isGrouped=" + this.isGrouped() + ", isOneToOneConversation=" + this.isOneToOneConversation() + ", activeUser=" + this.getActiveUser() + ", selectedIndividualHashMap=" + this.getSelectedIndividualHashMap() + ", isLinkPreviewAllowed=" + this.isLinkPreviewAllowed() + ", isDeleted=" + this.isDeleted() + ", jsonMessageId=" + this.getJsonMessageId() + ", token=" + this.getToken() + ", actorType=" + this.getActorType() + ", actorId=" + this.getActorId() + ", actorDisplayName=" + this.getActorDisplayName() + ", timestamp=" + this.getTimestamp() + ", message=" + this.getMessage() + ", messageParameters=" + this.getMessageParameters() + ", systemMessageType=" + this.getSystemMessageType() + ", replyable=" + this.isReplyable() + ", parentMessage=" + this.getParentMessage() + ", readStatus=" + this.getReadStatus() + ", messageTypesToIgnore=" + this.getMessageTypesToIgnore() + ")";
    }

    public enum MessageType {
        REGULAR_TEXT_MESSAGE,
        SYSTEM_MESSAGE,
        SINGLE_LINK_GIPHY_MESSAGE,
        SINGLE_LINK_TENOR_MESSAGE,
        SINGLE_LINK_GIF_MESSAGE,
        SINGLE_LINK_MESSAGE,
        SINGLE_LINK_VIDEO_MESSAGE,
        SINGLE_LINK_IMAGE_MESSAGE,
        SINGLE_LINK_AUDIO_MESSAGE,
        SINGLE_NC_ATTACHMENT_MESSAGE,
    }

    public enum SystemMessageType {
        DUMMY,
        CONVERSATION_CREATED,
        CONVERSATION_RENAMED,
        DESCRIPTION_REMOVED,
        DESCRIPTION_SET,
        CALL_STARTED,
        CALL_JOINED,
        CALL_LEFT,
        CALL_ENDED,
        READ_ONLY_OFF,
        READ_ONLY,
        LISTABLE_NONE,
        LISTABLE_USERS,
        LISTABLE_ALL,
        LOBBY_NONE,
        LOBBY_NON_MODERATORS,
        LOBBY_OPEN_TO_EVERYONE,
        GUESTS_ALLOWED,
        GUESTS_DISALLOWED,
        PASSWORD_SET,
        PASSWORD_REMOVED,
        USER_ADDED,
        USER_REMOVED,
        MODERATOR_PROMOTED,
        MODERATOR_DEMOTED,
        GUEST_MODERATOR_PROMOTED,
        GUEST_MODERATOR_DEMOTED,
        MESSAGE_DELETED,
        FILE_SHARED,
        OBJECT_SHARED,
        MATTERBRIDGE_CONFIG_ADDED,
        MATTERBRIDGE_CONFIG_EDITED,
        MATTERBRIDGE_CONFIG_REMOVED,
        MATTERBRIDGE_CONFIG_ENABLED,
        MATTERBRIDGE_CONFIG_DISABLED
    }
}
