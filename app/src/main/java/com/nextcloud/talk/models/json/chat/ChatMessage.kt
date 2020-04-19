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
import androidx.room.Ignore
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonIgnore
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.json.converters.EnumSystemMessageTypeConverter
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.other.ChatMessageStatus
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.TextMatchers
import com.stfalcon.chatkit.commons.models.IMessage
import com.stfalcon.chatkit.commons.models.IUser
import com.stfalcon.chatkit.commons.models.MessageContentType
import kotlinx.serialization.Serializable
import lombok.Data
import org.parceler.Parcel
import java.util.*

@Parcel
@Data
@JsonObject(serializeNullCollectionElements = true, serializeNullObjects = true)
@Serializable
class ChatMessage : IMessage, MessageContentType, MessageContentType.Image {
    @JvmField
    @JsonIgnore
    @Ignore
    var grouped = false

    @JvmField
    @JsonIgnore
    @Ignore
    var oneToOneConversation = false

    @JvmField
    @JsonIgnore
    @Ignore
    var activeUser: UserNgEntity? = null

    @JvmField
    @JsonIgnore
    @Ignore
    var selectedIndividualHashMap: Map<String, String>? = null

    @JvmField
    @JsonIgnore
    @Ignore
    var isLinkPreviewAllowed = false

    @JvmField
    @JsonIgnore
    var internalMessageId: String? = null

    @JvmField
    @JsonIgnore
    var internalConversationId: String? = null

    @JvmField
    @JsonField(name = ["id"])
    @Ignore
    var jsonMessageId: Long? = null

    @JvmField
    @JsonField(name = ["referenceId"])
    var referenceId: String? = null

    @JvmField
    @JsonField(name = ["token"])
    var token: String? = null

    // guests or users
    @JvmField
    @JsonField(name = ["actorType"])
    var actorType: String? = null

    @JvmField
    @JsonField(name = ["actorId"])
    var actorId: String? = null

    // send when crafting a message
    @JvmField
    @JsonField(name = ["actorDisplayName"])
    var actorDisplayName: String? = null

    @JvmField
    @JsonField(name = ["timestamp"])
    var timestamp: Long = 0

    // send when crafting a message, max 1000 lines
    @JvmField
    @JsonField(name = ["message"])
    var message: String? = null

    @JvmField
    @JsonField(name = ["messageParameters"])
    @Ignore
    var messageParameters: HashMap<String, HashMap<String, String>>? = null

    @JvmField
    @JsonField(name = ["systemMessage"], typeConverter = EnumSystemMessageTypeConverter::class)
    var systemMessageType: SystemMessageType? = null

    @JvmField
    @JsonField(name = ["isReplyable"])
    var replyable = false

    @JvmField
    @JsonField(name = ["parent"])
    var parentMessage: ChatMessage? = null

    @JvmField
    @JsonIgnore
    @Ignore
    var messageTypesToIgnore = Arrays.asList(MessageType.REGULAR_TEXT_MESSAGE,
            MessageType.SYSTEM_MESSAGE, MessageType.SINGLE_LINK_VIDEO_MESSAGE,
            MessageType.SINGLE_LINK_AUDIO_MESSAGE, MessageType.SINGLE_LINK_MESSAGE)

    @JsonIgnore
    var chatMessageStatus: ChatMessageStatus = ChatMessageStatus.RECEIVED

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is ChatMessage) return false
        val that = o
        return timestamp == that.timestamp && replyable == that.replyable &&
                jsonMessageId == that.jsonMessageId &&
                actorType == that.actorType &&
                actorId == that.actorId &&
                actorDisplayName == that.actorDisplayName &&
                token == that.token &&
                message == that.message &&
                messageParameters == that.messageParameters && systemMessageType == that.systemMessageType &&
                parentMessage == that.parentMessage
    }

    override fun hashCode(): Int {
        return Objects.hash(jsonMessageId, token, actorType, actorId, actorDisplayName, timestamp, message, messageParameters, systemMessageType, replyable, parentMessage)
    }

    private fun hasFileAttachment(): Boolean {
        if (messageParameters != null && messageParameters!!.size > 0) {
            for (key in messageParameters!!.keys) {
                val individualHashMap: Map<String, String> = messageParameters!![key]!!
                if (individualHashMap["type"] == "file") {
                    return true
                }
            }
        }
        return false
    }

    override fun getImageUrl(): String? {
        if (messageParameters != null && messageParameters!!.size > 0) {
            for (key in messageParameters!!.keys) {
                val individualHashMap: Map<String, String> = messageParameters!![key]!!
                if (individualHashMap["type"] == "file") {
                    selectedIndividualHashMap = individualHashMap
                    if (selectedIndividualHashMap!!.containsKey("preview-available")) {
                        if (selectedIndividualHashMap!!["preview-available"] == "no") {
                            return "no-preview"
                        }
                    }
                    return ApiUtils.getUrlForFilePreviewWithFileId(activeUser!!.baseUrl,
                            individualHashMap["id"], sharedApplication
                    !!.resources
                    !!.getDimensionPixelSize(R.dimen.maximum_file_preview_size))
                }
            }
        }
        return if (!messageTypesToIgnore.contains(messageType) && isLinkPreviewAllowed) {
            message!!.trim { it <= ' ' }
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

    override fun getId(): String {
        return java.lang.Long.toString(jsonMessageId!!)
    }

    override fun getText(): String {
        return ChatUtils.getParsedMessage(message, messageParameters)
    }

    val lastMessageDisplayText: String
        get() {
            if (messageType == MessageType.REGULAR_TEXT_MESSAGE || messageType == MessageType.SYSTEM_MESSAGE || messageType == MessageType.SINGLE_LINK_MESSAGE) {
                return text
            } else {
                if (messageType == MessageType.SINGLE_LINK_GIPHY_MESSAGE || messageType == MessageType.SINGLE_LINK_TENOR_MESSAGE || messageType == MessageType.SINGLE_LINK_GIF_MESSAGE) {
                    return if (actorId.equals(activeUser!!.userId)) {
                        sharedApplication!!.getString(R.string.nc_sent_a_gif_you)
                    } else {
                        String.format(sharedApplication
                        !!.resources
                                .getString(R.string.nc_sent_a_gif),
                                if (!TextUtils.isEmpty(actorDisplayName)) actorDisplayName else sharedApplication
                                !!.getString(R.string.nc_guest))
                    }
                } else if (messageType == MessageType.SINGLE_NC_ATTACHMENT_MESSAGE) {
                    return if (actorId.equals(activeUser!!.userId)) {
                        sharedApplication!!.resources.getString(R.string.nc_sent_an_attachment_you)
                    } else {
                        String.format(sharedApplication
                        !!.resources
                                .getString(R.string.nc_sent_an_attachment),
                                if (!TextUtils.isEmpty(actorDisplayName)) actorDisplayName else sharedApplication
                                !!.getString(R.string.nc_guest))
                    }
                    /*} else if (messageType == MessageType.SINGLE_LINK_MESSAGE) {
                        return if (actorId.equals(activeUser!!.userId)) {
                            sharedApplication!!.resources.getString(R.string.nc_sent_a_link_you)
                        } else {
                            String.format(sharedApplication
                            !!.resources
                                    .getString(R.string.nc_sent_a_link),
                                    if (!TextUtils.isEmpty(actorDisplayName)) actorDisplayName else sharedApplication!!.getString(R.string.nc_guest))
                        }*/
                } else if (messageType == MessageType.SINGLE_LINK_AUDIO_MESSAGE) {
                    return if (actorId.equals(activeUser!!.userId)) {
                        sharedApplication!!.resources.getString(R.string.nc_sent_an_audio_you)
                    } else {
                        String.format(sharedApplication
                        !!.resources
                                .getString(R.string.nc_sent_an_audio),
                                if (!TextUtils.isEmpty(actorDisplayName)) actorDisplayName else sharedApplication
                                !!.getString(R.string.nc_guest))
                    }
                } else if (messageType == MessageType.SINGLE_LINK_VIDEO_MESSAGE) {
                    return if (actorId.equals(activeUser!!.userId)) {
                        sharedApplication!!.resources.getString(R.string.nc_sent_a_video_you)
                    } else {
                        String.format(sharedApplication
                        !!.resources
                                .getString(R.string.nc_sent_a_video),
                                if (!TextUtils.isEmpty(actorDisplayName)) actorDisplayName else sharedApplication
                                !!.getString(R.string.nc_guest))
                    }
                } else if (messageType == MessageType.SINGLE_LINK_IMAGE_MESSAGE) {
                    return if (actorId.equals(activeUser!!.userId)) {
                        sharedApplication!!.getString(R.string.nc_sent_an_image_you)
                    } else {
                        String.format(sharedApplication
                        !!.resources
                                .getString(R.string.nc_sent_an_image),
                                if (!TextUtils.isEmpty(actorDisplayName)) actorDisplayName else sharedApplication
                                !!.getString(R.string.nc_guest))
                    }
                }
            }
            return ""
        }

    override fun getUser(): IUser {
        return object : IUser {
            override fun getId(): String {
                return actorId!!
            }

            override fun getName(): String {
                return actorDisplayName!!
            }

            override fun getAvatar(): String? {
                return when {
                    actorType.equals("users") -> {
                        ApiUtils.getUrlForAvatarWithName(activeUser!!.baseUrl, actorId,
                                R.dimen.avatar_size)
                    }
                    actorType.equals("guests") || actorType.equals("bots") -> {
                        var apiId: String? = sharedApplication!!.getString(R.string.nc_guest)
                        if (!TextUtils.isEmpty(actorDisplayName)) {
                            apiId = actorDisplayName
                        }
                        ApiUtils.getUrlForAvatarWithNameForGuests(activeUser!!.baseUrl, apiId,
                                R.dimen.avatar_size)
                    }
                    else -> {
                        null
                    }
                }
            }
        }
    }

    override fun getCreatedAt(): Date {
        return Date(timestamp * 1000L)
    }

    override fun getSystemMessage(): String {
        return EnumSystemMessageTypeConverter().convertToString(systemMessageType)
    }

    enum class MessageType {
        REGULAR_TEXT_MESSAGE, SYSTEM_MESSAGE, SINGLE_LINK_GIPHY_MESSAGE, SINGLE_LINK_TENOR_MESSAGE, SINGLE_LINK_GIF_MESSAGE, SINGLE_LINK_MESSAGE, SINGLE_LINK_VIDEO_MESSAGE, SINGLE_LINK_IMAGE_MESSAGE, SINGLE_LINK_AUDIO_MESSAGE, SINGLE_NC_ATTACHMENT_MESSAGE
    }

    enum class SystemMessageType {
        DUMMY, CONVERSATION_CREATED, CONVERSATION_RENAMED, CALL_STARTED, CALL_JOINED, CALL_LEFT, CALL_ENDED, GUESTS_ALLOWED, GUESTS_DISALLOWED, PASSWORD_SET, PASSWORD_REMOVED, USER_ADDED, USER_REMOVED, MODERATOR_PROMOTED, MODERATOR_DEMOTED, FILE_SHARED, LOBBY_NONE, LOBBY_NON_MODERATORS, LOBBY_OPEN_TO_EVERYONE
    }
}