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
package com.nextcloud.talk.models.json.chat

import android.text.TextUtils
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonIgnore
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.data.models.json.converters.EnumSystemMessageTypeConverter
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.database.UserEntity
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.TextMatchers
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.commons.models.IUser
import com.stfalcon.chatkit.commons.models.MessageContentType
import org.apache.commons.lang3.exception.ExceptionUtils.getMessage
import org.parceler.Parcel
import java.util.*

@Parcel
@JsonObject
class ChatMessage : IMessage, MessageContentType, MessageContentType.Image {
    @JsonIgnore
    var isGrouped: Boolean = false
    @JsonIgnore
    var isOneToOneConversation: Boolean = false
    @JsonIgnore
    var activeUser: UserEntity? = null
    @JsonIgnore
    var selectedIndividualHashMap: Map<String, String>? = null
    @JsonIgnore
    var isLinkPreviewAllowed: Boolean = false
    internal var messageTypesToIgnore = Arrays.asList(MessageType.REGULAR_TEXT_MESSAGE,
            MessageType.SYSTEM_MESSAGE, MessageType.SINGLE_LINK_VIDEO_MESSAGE,
            MessageType.SINGLE_LINK_AUDIO_MESSAGE, MessageType.SINGLE_LINK_MESSAGE)
    @JsonField(name = ["id"])
    var jsonMessageId: Int = 0
    @JsonField(name = ["token"])
    var token: String? = null
    // guests or users
    @JsonField(name = ["actorType"])
    var actorType: String? = null
    @JsonField(name = ["actorId"])
    var actorId: String? = null
    // send when crafting a message
    @JsonField(name = ["actorDisplayName"])
    var actorDisplayName: String? = null
    @JsonField(name = ["timestamp"])
    var timestamp: Long = 0
    // send when crafting a message, max 1000 lines
    @JsonField(name = ["message"])
    var message: String? = null
    @JsonField(name = ["messageParameters"])
    var messageParameters: HashMap<String, HashMap<String, String>>? = null
    @JsonField(name = ["systemMessage"], typeConverter = EnumSystemMessageTypeConverter::class)
    var systemMessageType: SystemMessageType? = null

    val imageUrl: String?
        get() {
            if (messageParameters != null && messageParameters!!.size > 0) {
                for (key in messageParameters!!.keys) {
                    val individualHashMap = messageParameters!![key]
                    if (individualHashMap!!["type"] == "file") {
                        selectedIndividualHashMap = individualHashMap
                        return NextcloudTalkApplication.sharedApplication?.resources?.getDimensionPixelSize(R.dimen.maximum_file_preview_size)?.let{maxPreviewSize : Int ->
                            ApiUtils.getUrlForFilePreviewWithFileId(activeUser!!.getBaseUrl(), individualHashMap["id"], maxPreviewSize)
                        }
                    }
                }
            }

            return if (!messageTypesToIgnore.contains(messageType) && isLinkPreviewAllowed) {
                message!!.trim({ it <= ' ' })
            } else null

        }

    val messageType: MessageType
        get() {
            if (!TextUtils.isEmpty(systemMessage)) {
                return MessageType.SYSTEM_MESSAGE
            }

            return if (hasFileAttachment()) {
                MessageType.SINGLE_NC_ATTACHMENT_MESSAGE
            } else TextMatchers.getMessageTypeFromString(text)

        }

    val id: String
        get() = Integer.toString(jsonMessageId)

    val text: String
        get() = ChatUtils.getParsedMessage(getMessage(), getMessageParameters())

    val lastMessageDisplayText: String
        get() {
            if (messageType == MessageType.REGULAR_TEXT_MESSAGE || messageType == MessageType.SYSTEM_MESSAGE) {
                return text
            } else {
                if (messageType == MessageType.SINGLE_LINK_GIPHY_MESSAGE
                        || messageType == MessageType.SINGLE_LINK_TENOR_MESSAGE
                        || messageType == MessageType.SINGLE_LINK_GIF_MESSAGE) {
                    return if (getActorId() == getActiveUser()!!.getUserId()) {
                        NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_sent_a_gif_you)
                    } else {
                        String.format(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getString(R.string.nc_sent_a_gif),
                                if (!TextUtils.isEmpty(getActorDisplayName())) getActorDisplayName() else NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest))
                    }
                } else if (messageType == MessageType.SINGLE_NC_ATTACHMENT_MESSAGE) {
                    return if (getActorId() == getActiveUser()!!.getUserId()) {
                        NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_sent_an_attachment_you)
                    } else {
                        String.format(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getString(R.string.nc_sent_an_attachment),
                                if (!TextUtils.isEmpty(getActorDisplayName())) getActorDisplayName() else NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest))
                    }
                } else if (messageType == MessageType.SINGLE_LINK_MESSAGE) {
                    return if (getActorId() == getActiveUser()!!.getUserId()) {
                        NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_sent_a_link_you)
                    } else {
                        String.format(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getString(R.string.nc_sent_a_link),
                                if (!TextUtils.isEmpty(getActorDisplayName())) getActorDisplayName() else NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest))
                    }
                } else if (messageType == MessageType.SINGLE_LINK_AUDIO_MESSAGE) {
                    return if (getActorId() == getActiveUser()!!.getUserId()) {
                        NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_sent_an_audio_you)
                    } else {
                        String.format(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getString(R.string.nc_sent_an_audio),
                                if (!TextUtils.isEmpty(getActorDisplayName())) getActorDisplayName() else NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest))
                    }
                } else if (messageType == MessageType.SINGLE_LINK_VIDEO_MESSAGE) {
                    return if (getActorId() == getActiveUser()!!.getUserId()) {
                        NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_sent_a_video_you)
                    } else {
                        String.format(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getString(R.string.nc_sent_a_video),
                                if (!TextUtils.isEmpty(getActorDisplayName())) getActorDisplayName() else NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest))
                    }
                } else if (messageType == MessageType.SINGLE_LINK_IMAGE_MESSAGE) {
                    return if (getActorId() == getActiveUser()!!.getUserId()) {
                        NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_sent_an_image_you)
                    } else {
                        String.format(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getString(R.string.nc_sent_an_image),
                                if (!TextUtils.isEmpty(getActorDisplayName())) getActorDisplayName() else NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest))
                    }
                }
            }

            return ""
        }

    val user: IUser
        get() = object : IUser() {
            val id: String?
                get() = actorId

            val name: String?
                get() = actorDisplayName

            val avatar: String?
                get() {
                    if (getActorType() == "users") {
                        return ApiUtils.getUrlForAvatarWithName(getActiveUser()!!.getBaseUrl(), actorId, R.dimen.avatar_size)
                    } else if (getActorType() == "guests") {
                        var apiId = NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest)

                        if (!TextUtils.isEmpty(getActorDisplayName())) {
                            apiId = getActorDisplayName()
                        }
                        return ApiUtils.getUrlForAvatarWithNameForGuests(getActiveUser()!!.getBaseUrl(), apiId, R.dimen.avatar_size)
                    } else {
                        return null
                    }
                }
        }

    val createdAt: Date
        get() = Date(timestamp * 1000L)

    val systemMessage: String
        get() = EnumSystemMessageTypeConverter().convertToString(getSystemMessageType())

    private fun hasFileAttachment(): Boolean {
        if (messageParameters != null && messageParameters!!.size > 0) {
            for (key in messageParameters!!.keys) {
                val individualHashMap = messageParameters!![key]
                if (individualHashMap!!["type"] == "file") {
                    return true
                }
            }
        }

        return false
    }

    enum class MessageType {
        REGULAR_TEXT_MESSAGE,
        SYSTEM_MESSAGE,
        SINGLE_LINK_GIPHY_MESSAGE,
        SINGLE_LINK_TENOR_MESSAGE,
        SINGLE_LINK_GIF_MESSAGE,
        SINGLE_LINK_MESSAGE,
        SINGLE_LINK_VIDEO_MESSAGE,
        SINGLE_LINK_IMAGE_MESSAGE,
        SINGLE_LINK_AUDIO_MESSAGE,
        SINGLE_NC_ATTACHMENT_MESSAGE
    }

    enum class SystemMessageType {
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
