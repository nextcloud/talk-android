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

import androidx.annotation.Nullable;
import androidx.room.Ignore;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonIgnore;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.json.converters.EnumSystemMessageTypeConverter;
import com.nextcloud.talk.newarch.local.models.UserNgEntity;
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
import java.util.Objects;

import lombok.Data;

@Parcel
@Data
@JsonObject(serializeNullCollectionElements = true, serializeNullObjects = true)
public class ChatMessage implements IMessage, MessageContentType, MessageContentType.Image {
    @JsonIgnore
    @Ignore
    public boolean grouped;
    @JsonIgnore
    @Ignore
    public boolean oneToOneConversation;
    @JsonIgnore
    @Ignore
    public UserNgEntity activeUser;
    @JsonIgnore
    @Ignore
    public Map<String, String> selectedIndividualHashMap;
    @JsonIgnore
    @Ignore
    public boolean isLinkPreviewAllowed;
    @JsonIgnore
    public String internalMessageId = null;
    @JsonIgnore
    public String internalConversationId = null;
    @JsonField(name = "id")
    @Ignore
    public Long jsonMessageId;
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
    @Ignore
    public HashMap<String, HashMap<String, String>> messageParameters;
    @JsonField(name = "systemMessage", typeConverter = EnumSystemMessageTypeConverter.class)
    public SystemMessageType systemMessageType;
    @JsonField(name = "isReplyable")
    public boolean replyable;
    @JsonField(name = "parent")
    public ChatMessage parentMessage;
    @JsonIgnore
    @Ignore
    List<MessageType> messageTypesToIgnore = Arrays.asList(MessageType.REGULAR_TEXT_MESSAGE,
            MessageType.SYSTEM_MESSAGE, MessageType.SINGLE_LINK_VIDEO_MESSAGE,
            MessageType.SINGLE_LINK_AUDIO_MESSAGE, MessageType.SINGLE_LINK_MESSAGE);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessage)) return false;
        ChatMessage that = (ChatMessage) o;
        return timestamp == that.timestamp &&
                replyable == that.replyable &&
                Objects.equals(jsonMessageId, that.jsonMessageId) &&
                Objects.equals(actorType, that.actorType) &&
                Objects.equals(actorId, that.actorId) &&
                Objects.equals(actorDisplayName, that.actorDisplayName) &&
                Objects.equals(token, that.token) &&
                Objects.equals(message, that.message) &&
                Objects.equals(messageParameters, that.messageParameters) &&
                systemMessageType == that.systemMessageType &&
                Objects.equals(parentMessage, that.parentMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonMessageId, token, actorType, actorId, actorDisplayName, timestamp, message, messageParameters, systemMessageType, replyable, parentMessage);
    }

    private boolean hasFileAttachment() {
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
                    if (selectedIndividualHashMap.containsKey("preview-available")) {
                        if (selectedIndividualHashMap.get("preview-available").equals("no")) {
                            return "no-preview";
                        }
                    }

                    return (ApiUtils.getUrlForFilePreviewWithFileId(activeUser.getBaseUrl(),
                            individualHashMap.get("id"), NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getResources()
                                    .getDimensionPixelSize(R.dimen.maximum_file_preview_size)));
                }
            }
        }

        if (!messageTypesToIgnore.contains(getMessageType()) && isLinkPreviewAllowed) {
            return message.trim();
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
        return Long.toString(jsonMessageId);
    }

    @Override
    public String getText() {
        return ChatUtils.getParsedMessage(getMessage(), getMessageParameters());
    }

    public String getLastMessageDisplayText() {
        if (getMessageType().equals(MessageType.REGULAR_TEXT_MESSAGE) || getMessageType().equals(
                MessageType.SYSTEM_MESSAGE)) {
            return getText();
        } else {
            if (getMessageType().equals(MessageType.SINGLE_LINK_GIPHY_MESSAGE)
                    || getMessageType().equals(MessageType.SINGLE_LINK_TENOR_MESSAGE)
                    || getMessageType().equals(MessageType.SINGLE_LINK_GIF_MESSAGE)) {
                if (getActorId().equals(getActiveUser().getUserId())) {
                    return (NextcloudTalkApplication.Companion.getSharedApplication()
                            .getString(R.string.nc_sent_a_gif_you));
                } else {
                    return (String.format(NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getResources()
                                    .getString(R.string.nc_sent_a_gif),
                            !TextUtils.isEmpty(getActorDisplayName()) ? getActorDisplayName()
                                    : NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getString(R.string.nc_guest)));
                }
            } else if (getMessageType().equals(MessageType.SINGLE_NC_ATTACHMENT_MESSAGE)) {
                if (getActorId().equals(getActiveUser().getUserId())) {
                    return (NextcloudTalkApplication.Companion.getSharedApplication()
                            .getString(R.string.nc_sent_an_attachment_you));
                } else {
                    return (String.format(NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getResources()
                                    .getString(R.string.nc_sent_an_attachment),
                            !TextUtils.isEmpty(getActorDisplayName()) ? getActorDisplayName()
                                    : NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getString(R.string.nc_guest)));
                }
            } else if (getMessageType().equals(MessageType.SINGLE_LINK_MESSAGE)) {
                if (getActorId().equals(getActiveUser().getUserId())) {
                    return (NextcloudTalkApplication.Companion.getSharedApplication()
                            .getString(R.string.nc_sent_a_link_you));
                } else {
                    return (String.format(NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getResources()
                                    .getString(R.string.nc_sent_a_link),
                            !TextUtils.isEmpty(getActorDisplayName()) ? getActorDisplayName()
                                    : NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getString(R.string.nc_guest)));
                }
            } else if (getMessageType().equals(MessageType.SINGLE_LINK_AUDIO_MESSAGE)) {
                if (getActorId().equals(getActiveUser().getUserId())) {
                    return (NextcloudTalkApplication.Companion.getSharedApplication()
                            .getString(R.string.nc_sent_an_audio_you));
                } else {
                    return (String.format(NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getResources()
                                    .getString(R.string.nc_sent_an_audio),
                            !TextUtils.isEmpty(getActorDisplayName()) ? getActorDisplayName()
                                    : NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getString(R.string.nc_guest)));
                }
            } else if (getMessageType().equals(MessageType.SINGLE_LINK_VIDEO_MESSAGE)) {
                if (getActorId().equals(getActiveUser().getUserId())) {
                    return (NextcloudTalkApplication.Companion.getSharedApplication()
                            .getString(R.string.nc_sent_a_video_you));
                } else {
                    return (String.format(NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getResources()
                                    .getString(R.string.nc_sent_a_video),
                            !TextUtils.isEmpty(getActorDisplayName()) ? getActorDisplayName()
                                    : NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getString(R.string.nc_guest)));
                }
            } else if (getMessageType().equals(MessageType.SINGLE_LINK_IMAGE_MESSAGE)) {
                if (getActorId().equals(getActiveUser().getUserId())) {
                    return (NextcloudTalkApplication.Companion.getSharedApplication()
                            .getString(R.string.nc_sent_an_image_you));
                } else {
                    return (String.format(NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getResources()
                                    .getString(R.string.nc_sent_an_image),
                            !TextUtils.isEmpty(getActorDisplayName()) ? getActorDisplayName()
                                    : NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getString(R.string.nc_guest)));
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
                    return ApiUtils.getUrlForAvatarWithName(getActiveUser().getBaseUrl(), actorId,
                            R.dimen.avatar_size);
                } else if (getActorType().equals("guests")) {
                    String apiId =
                            NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getString(R.string.nc_guest);

                    if (!TextUtils.isEmpty(getActorDisplayName())) {
                        apiId = getActorDisplayName();
                    }
                    return ApiUtils.getUrlForAvatarWithNameForGuests(getActiveUser().getBaseUrl(), apiId,
                            R.dimen.avatar_size);
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
        CALL_STARTED,
        CALL_JOINED,
        CALL_LEFT,
        CALL_ENDED,
        GUESTS_ALLOWED,
        GUESTS_DISALLOWED,
        PASSWORD_SET,
        PASSWORD_REMOVED,
        USER_ADDED,
        USER_REMOVED,
        MODERATOR_PROMOTED,
        MODERATOR_DEMOTED,
        FILE_SHARED,
        LOBBY_NONE,
        LOBBY_NON_MODERATORS,
        LOBBY_OPEN_TO_EVERYONE
    }
}
