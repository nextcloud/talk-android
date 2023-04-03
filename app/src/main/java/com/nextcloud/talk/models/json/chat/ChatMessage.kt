/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Tim Krüger
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
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

import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonIgnore
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.chat.ChatUtils.Companion.getParsedMessage
import com.nextcloud.talk.models.json.converters.EnumSystemMessageTypeConverter
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import com.stfalcon.chatkit.commons.models.IUser
import com.stfalcon.chatkit.commons.models.MessageContentType
import kotlinx.parcelize.Parcelize
import java.security.MessageDigest
import java.util.Arrays
import java.util.Date

@Parcelize
@JsonObject
data class ChatMessage(
    @JsonIgnore
    var isGrouped: Boolean = false,

    @JsonIgnore
    var isOneToOneConversation: Boolean = false,

    @JsonIgnore
    var isFormerOneToOneConversation: Boolean = false,

    @JsonIgnore
    var activeUser: User? = null,

    @JsonIgnore
    var selectedIndividualHashMap: Map<String?, String?>? = null,

    @JsonIgnore
    var isDeleted: Boolean = false,

    @JsonField(name = ["id"])
    var jsonMessageId: Int = 0,

    @JsonIgnore
    var previousMessageId: Int = -1,

    @JsonField(name = ["token"])
    var token: String? = null,

    // guests or users
    @JsonField(name = ["actorType"])
    var actorType: String? = null,

    @JsonField(name = ["actorId"])
    var actorId: String? = null,

    // send when crafting a message
    @JsonField(name = ["actorDisplayName"])
    var actorDisplayName: String? = null,

    @JsonField(name = ["timestamp"])
    var timestamp: Long = 0,

    // send when crafting a message, max 1000 lines
    @JsonField(name = ["message"])
    var message: String? = null,

    @JsonField(name = ["messageParameters"])
    var messageParameters: HashMap<String?, HashMap<String?, String?>>? = null,

    @JsonField(name = ["systemMessage"], typeConverter = EnumSystemMessageTypeConverter::class)
    var systemMessageType: SystemMessageType? = null,

    @JsonField(name = ["isReplyable"])
    var replyable: Boolean = false,

    @JsonField(name = ["parent"])
    var parentMessage: ChatMessage? = null,

    var readStatus: Enum<ReadStatus> = ReadStatus.NONE,

    @JsonField(name = ["messageType"])
    var messageType: String? = null,

    @JsonField(name = ["reactions"])
    var reactions: LinkedHashMap<String, Int>? = null,

    @JsonField(name = ["reactionsSelf"])
    var reactionsSelf: ArrayList<String>? = null,

    @JsonField(name = ["expirationTimestamp"])
    var expirationTimestamp: Int = 0,

    var isDownloadingVoiceMessage: Boolean = false,

    var resetVoiceMessage: Boolean = false,

    var isPlayingVoiceMessage: Boolean = false,

    var voiceMessageDuration: Int = 0,

    var voiceMessagePlayedSeconds: Int = 0,

    var voiceMessageDownloadProgress: Int = 0

) : Parcelable, MessageContentType, MessageContentType.Image {

    var extractedUrlToPreview: String? = null

    // messageTypesToIgnore is weird. must be deleted by refactoring!!!
    @JsonIgnore
    var messageTypesToIgnore = Arrays.asList(
        MessageType.REGULAR_TEXT_MESSAGE,
        MessageType.SYSTEM_MESSAGE,
        MessageType.SINGLE_LINK_VIDEO_MESSAGE,
        MessageType.SINGLE_LINK_AUDIO_MESSAGE,
        MessageType.SINGLE_LINK_MESSAGE,
        MessageType.SINGLE_NC_GEOLOCATION_MESSAGE,
        MessageType.VOICE_MESSAGE,
        MessageType.POLL_MESSAGE
    )

    fun hasFileAttachment(): Boolean {
        if (messageParameters != null && messageParameters!!.size > 0) {
            for ((_, individualHashMap) in messageParameters!!) {
                if (isHashMapEntryEqualTo(individualHashMap, "type", "file")) {
                    return true
                }
            }
        }
        return false
    }

    fun hasGeoLocation(): Boolean {
        if (messageParameters != null && messageParameters!!.size > 0) {
            for ((_, individualHashMap) in messageParameters!!) {
                if (isHashMapEntryEqualTo(individualHashMap, "type", "geo-location")) {
                    return true
                }
            }
        }
        return false
    }

    fun isPoll(): Boolean {
        if (messageParameters != null && messageParameters!!.size > 0) {
            for ((_, individualHashMap) in messageParameters!!) {
                if (isHashMapEntryEqualTo(individualHashMap, "type", "talk-poll")) {
                    return true
                }
            }
        }
        return false
    }

    @Suppress("ReturnCount")
    fun isLinkPreview(): Boolean {
        if (CapabilitiesUtilNew.isLinkPreviewAvailable(activeUser!!)) {
            val regexStringFromServer = activeUser?.capabilities?.coreCapability?.referenceRegex

            val regexFromServer = regexStringFromServer?.toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
            val regexDefault = REGEX_STRING_DEFAULT.toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))

            val messageCharSequence: CharSequence = StringBuffer(message!!)

            if (regexFromServer != null) {
                val foundLinkInServerRegex = regexFromServer.containsMatchIn(messageCharSequence)
                if (foundLinkInServerRegex) {
                    extractedUrlToPreview = regexFromServer.find(messageCharSequence)?.groups?.get(0)?.value?.trim()
                    return true
                }
            }

            val foundLinkInDefaultRegex = regexDefault.containsMatchIn(messageCharSequence)
            if (foundLinkInDefaultRegex) {
                extractedUrlToPreview = regexDefault.find(messageCharSequence)?.groups?.get(0)?.value?.trim()
                return true
            }
        }
        return false
    }

    @Suppress("Detekt.NestedBlockDepth")
    override fun getImageUrl(): String? {
        if (messageParameters != null && messageParameters!!.size > 0) {
            for ((_, individualHashMap) in messageParameters!!) {
                if (isHashMapEntryEqualTo(individualHashMap, "type", "file")) {
                    // FIX-ME: this selectedIndividualHashMap stuff needs to be analyzed and most likely be refactored!
                    //  it just feels wrong to fill this here inside getImageUrl()
                    selectedIndividualHashMap = individualHashMap
                    if (!isVoiceMessage) {
                        if (activeUser != null && activeUser!!.baseUrl != null) {
                            return ApiUtils.getUrlForFilePreviewWithFileId(
                                activeUser!!.baseUrl,
                                individualHashMap["id"],
                                sharedApplication!!.resources.getDimensionPixelSize(R.dimen.maximum_file_preview_size)
                            )
                        } else {
                            Log.e(
                                TAG,
                                "activeUser or activeUser.getBaseUrl() were null when trying to getImageUrl()"
                            )
                        }
                    }
                }
            }
        }
        return if (!messageTypesToIgnore.contains(getCalculateMessageType())) {
            message!!.trim { it <= ' ' }
        } else {
            null
        }
    }

    fun getCalculateMessageType(): MessageType {
        return if (!TextUtils.isEmpty(systemMessage)) {
            MessageType.SYSTEM_MESSAGE
        } else if (isVoiceMessage) {
            MessageType.VOICE_MESSAGE
        } else if (hasFileAttachment()) {
            MessageType.SINGLE_NC_ATTACHMENT_MESSAGE
        } else if (hasGeoLocation()) {
            MessageType.SINGLE_NC_GEOLOCATION_MESSAGE
        } else if (isPoll()) {
            MessageType.POLL_MESSAGE
        } else {
            MessageType.REGULAR_TEXT_MESSAGE
        }
    }

    override fun getId(): String {
        return jsonMessageId.toString()
    }

    override fun getText(): String {
        return if (message != null) {
            getParsedMessage(message, messageParameters)!!
        } else {
            ""
        }
    }

    /*} else if (getCalculateMessageType().equals(MessageType.SINGLE_LINK_MESSAGE)) {
                if (actorId.equals(activeUser.getUserId())) {
                    return (
                    NextcloudTalkApplication
                    .Companion.getSharedApplication()
                    .getString(R.string.nc_sent_a_link_you)
                    );
                } else {
                    return (String.format(NextcloudTalkApplication.
                    Companion.
                    getSharedApplication().
                    getResources().
                    getString(R.string.nc_sent_a_link),
                            !TextUtils.isEmpty(actorDisplayName) ? actorDisplayName : NextcloudTalkApplication.
                            Companion.
                            getSharedApplication().
                            getString(R.string.nc_guest))
                            );
                }*/
    val lastMessageDisplayText: String
        get() {
            if (getCalculateMessageType() == MessageType.REGULAR_TEXT_MESSAGE ||
                getCalculateMessageType() == MessageType.SYSTEM_MESSAGE ||
                getCalculateMessageType() == MessageType.SINGLE_LINK_MESSAGE
            ) {
                return text
            } else {
                if (MessageType.SINGLE_LINK_GIPHY_MESSAGE == getCalculateMessageType() ||
                    MessageType.SINGLE_LINK_TENOR_MESSAGE == getCalculateMessageType() ||
                    MessageType.SINGLE_LINK_GIF_MESSAGE == getCalculateMessageType()
                ) {
                    return if (actorId == activeUser!!.userId) {
                        sharedApplication!!.getString(R.string.nc_sent_a_gif_you)
                    } else {
                        String.format(
                            sharedApplication!!.resources.getString(R.string.nc_sent_a_gif),
                            getNullsafeActorDisplayName()
                        )
                    }
                } else if (MessageType.SINGLE_NC_ATTACHMENT_MESSAGE == getCalculateMessageType()) {
                    return if (actorId == activeUser!!.userId) {
                        sharedApplication!!.getString(R.string.nc_sent_an_attachment_you)
                    } else {
                        String.format(
                            sharedApplication!!.resources.getString(R.string.nc_sent_an_attachment),
                            getNullsafeActorDisplayName()
                        )
                    }
                } else if (MessageType.SINGLE_NC_GEOLOCATION_MESSAGE == getCalculateMessageType()) {
                    return if (actorId == activeUser!!.userId) {
                        sharedApplication!!.getString(R.string.nc_sent_location_you)
                    } else {
                        String.format(
                            sharedApplication!!.resources.getString(R.string.nc_sent_location),
                            getNullsafeActorDisplayName()
                        )
                    }
                } else if (MessageType.VOICE_MESSAGE == getCalculateMessageType()) {
                    return if (actorId == activeUser!!.userId) {
                        sharedApplication!!.getString(R.string.nc_sent_voice_you)
                    } else {
                        String.format(
                            sharedApplication!!.resources.getString(R.string.nc_sent_voice),
                            getNullsafeActorDisplayName()
                        )
                    }
                    /*} else if (getCalculateMessageType().equals(MessageType.SINGLE_LINK_MESSAGE)) {
                if (actorId.equals(activeUser.getUserId())) {
                    return (
                    NextcloudTalkApplication
                    .Companion
                    .getSharedApplication()
                    .getString(R.string.nc_sent_a_link_you)
                    );
                } else {
                    return (String.format(
                    NextcloudTalkApplication
                    .Companion
                    .getSharedApplication()
                    .getResources()
                    .getString(R.string.nc_sent_a_link),
                            !TextUtils.isEmpty(actorDisplayName) ? actorDisplayName : NextcloudTalkApplication.
                            Companion.
                            getSharedApplication().
                            getString(R.string.nc_guest))
                            );
                }*/
                } else if (MessageType.SINGLE_LINK_AUDIO_MESSAGE == getCalculateMessageType()) {
                    return if (actorId == activeUser!!.userId) {
                        sharedApplication!!.getString(R.string.nc_sent_an_audio_you)
                    } else {
                        String.format(
                            sharedApplication!!.resources.getString(R.string.nc_sent_an_audio),
                            getNullsafeActorDisplayName()
                        )
                    }
                } else if (MessageType.SINGLE_LINK_VIDEO_MESSAGE == getCalculateMessageType()) {
                    return if (actorId == activeUser!!.userId) {
                        sharedApplication!!.getString(R.string.nc_sent_a_video_you)
                    } else {
                        String.format(
                            sharedApplication!!.resources.getString(R.string.nc_sent_a_video),
                            getNullsafeActorDisplayName()
                        )
                    }
                } else if (MessageType.SINGLE_LINK_IMAGE_MESSAGE == getCalculateMessageType()) {
                    return if (actorId == activeUser!!.userId) {
                        sharedApplication!!.getString(R.string.nc_sent_an_image_you)
                    } else {
                        String.format(
                            sharedApplication!!.resources.getString(R.string.nc_sent_an_image),
                            getNullsafeActorDisplayName()
                        )
                    }
                } else if (MessageType.POLL_MESSAGE == getCalculateMessageType()) {
                    return if (actorId == activeUser!!.userId) {
                        sharedApplication!!.getString(R.string.nc_sent_poll_you)
                    } else {
                        String.format(
                            sharedApplication!!.resources.getString(R.string.nc_sent_an_image),
                            getNullsafeActorDisplayName()
                        )
                    }
                }
            }
            return ""
        }

    private fun getNullsafeActorDisplayName() = if (!TextUtils.isEmpty(actorDisplayName)) {
        actorDisplayName
    } else {
        sharedApplication!!.getString(R.string.nc_guest)
    }

    override fun getUser(): IUser {
        return object : IUser {
            override fun getId(): String {
                return "$actorType/$actorId"
            }

            override fun getName(): String {
                return if (!TextUtils.isEmpty(actorDisplayName)) {
                    actorDisplayName!!
                } else {
                    sharedApplication!!.getString(R.string.nc_guest)
                }
            }

            override fun getAvatar(): String? {
                return when {
                    activeUser == null -> {
                        null
                    }
                    actorType == "users" -> {
                        ApiUtils.getUrlForAvatar(activeUser!!.baseUrl, actorId, true)
                    }
                    actorType == "bridged" -> {
                        ApiUtils.getUrlForAvatar(
                            activeUser!!.baseUrl,
                            "bridge-bot",
                            true
                        )
                    }
                    else -> {
                        var apiId: String? = sharedApplication!!.getString(R.string.nc_guest)
                        if (!TextUtils.isEmpty(actorDisplayName)) {
                            apiId = actorDisplayName
                        }
                        ApiUtils.getUrlForGuestAvatar(activeUser!!.baseUrl, apiId, true)
                    }
                }
            }
        }
    }

    override fun getCreatedAt(): Date {
        return Date(timestamp * MILLIES)
    }

    override fun getSystemMessage(): String {
        return EnumSystemMessageTypeConverter().convertToString(systemMessageType)
    }

    private fun isHashMapEntryEqualTo(map: HashMap<String?, String?>, key: String, searchTerm: String): Boolean {
        return map != null && MessageDigest.isEqual(map[key]!!.toByteArray(), searchTerm.toByteArray())
    }

    val isVoiceMessage: Boolean
        get() = "voice-message" == messageType
    val isCommandMessage: Boolean
        get() = "command" == messageType
    val isDeletedCommentMessage: Boolean
        get() = "comment_deleted" == messageType

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
        SINGLE_NC_ATTACHMENT_MESSAGE,
        SINGLE_NC_GEOLOCATION_MESSAGE,
        POLL_MESSAGE,
        VOICE_MESSAGE
    }

    /**
     * see https://nextcloud-talk.readthedocs.io/en/latest/chat/#system-messages
     */
    enum class SystemMessageType {
        DUMMY, CONVERSATION_CREATED,
        CONVERSATION_RENAMED,
        DESCRIPTION_REMOVED,
        DESCRIPTION_SET,
        CALL_STARTED,
        CALL_JOINED,
        CALL_LEFT,
        CALL_ENDED,
        CALL_ENDED_EVERYONE,
        CALL_MISSED,
        CALL_TRIED,
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
        GROUP_ADDED,
        GROUP_REMOVED,
        CIRCLE_ADDED,
        CIRCLE_REMOVED,
        MODERATOR_PROMOTED,
        MODERATOR_DEMOTED,
        GUEST_MODERATOR_PROMOTED,
        GUEST_MODERATOR_DEMOTED,
        MESSAGE_DELETED,
        FILE_SHARED, OBJECT_SHARED,
        MATTERBRIDGE_CONFIG_ADDED,
        MATTERBRIDGE_CONFIG_EDITED,
        MATTERBRIDGE_CONFIG_REMOVED,
        MATTERBRIDGE_CONFIG_ENABLED,
        MATTERBRIDGE_CONFIG_DISABLED,
        CLEARED_CHAT,
        REACTION,
        REACTION_DELETED,
        REACTION_REVOKED,
        POLL_VOTED,
        POLL_CLOSED,
        MESSAGE_EXPIRATION_ENABLED,
        MESSAGE_EXPIRATION_DISABLED,
        RECORDING_STARTED,
        RECORDING_STOPPED,
        AUDIO_RECORDING_STARTED,
        AUDIO_RECORDING_STOPPED,
        RECORDING_FAILED,
        BREAKOUT_ROOMS_STARTED,
        BREAKOUT_ROOMS_STOPPED
    }

    companion object {
        private const val TAG = "ChatMessage"
        private const val MILLIES: Long = 1000L

        private const val REGEX_STRING_DEFAULT =
            """(\s|\n|^)(https?:\/\/)((?:[-A-Z0-9+_]+\.)+[-A-Z]+(?:\/[-A-Z0-9+&@#%?=~_|!:,.;()]*)*)(\s|\n|$)"""
    }
}
