/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022-2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.model

import android.text.TextUtils
import android.util.Log
import com.bluelinelabs.logansquare.annotation.JsonIgnore
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.chat.ChatUtils.Companion.getParsedMessage
import com.nextcloud.talk.models.json.chat.ReadStatus
import com.nextcloud.talk.models.json.converters.EnumSystemMessageTypeConverter
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.stfalcon.chatkit.commons.models.IUser
import com.stfalcon.chatkit.commons.models.MessageContentType
import java.security.MessageDigest
import java.util.Date

data class ChatMessage(
    var isGrouped: Boolean = false,

    var isOneToOneConversation: Boolean = false,

    var isFormerOneToOneConversation: Boolean = false,

    var activeUser: User? = null,

    var selectedIndividualHashMap: Map<String?, String?>? = null,

    var isDeleted: Boolean = false,

    var jsonMessageId: Int = 0,

    var previousMessageId: Int = -1,

    var token: String? = null,

    var threadId: Long? = null,

    var isThread: Boolean = false,

    // guests or users
    var actorType: String? = null,

    var actorId: String? = null,

    // send when crafting a message
    var actorDisplayName: String? = null,

    var timestamp: Long = 0,

    // send when crafting a message, max 1000 lines
    var message: String? = null,

    var messageParameters: HashMap<String?, HashMap<String?, String?>>? = null,

    var systemMessageType: SystemMessageType? = null,

    var replyable: Boolean = false,

    var parentMessageId: Long? = null,

    var readStatus: Enum<ReadStatus> = ReadStatus.NONE,

    var messageType: String? = null,

    var reactions: LinkedHashMap<String, Int>? = null,

    var reactionsSelf: ArrayList<String>? = null,

    var expirationTimestamp: Int = 0,

    var renderMarkdown: Boolean? = null,

    var lastEditActorDisplayName: String? = null,

    var lastEditActorId: String? = null,

    var lastEditActorType: String? = null,

    var lastEditTimestamp: Long? = 0,

    var isDownloadingVoiceMessage: Boolean = false,

    var resetVoiceMessage: Boolean = false,

    var isPlayingVoiceMessage: Boolean = false,

    var wasPlayedVoiceMessage: Boolean = false,

    var voiceMessageDuration: Int = 0,

    var voiceMessagePlayedSeconds: Int = 0,

    var voiceMessageDownloadProgress: Int = 0,

    var voiceMessageSeekbarProgress: Int = 0,

    var voiceMessageFloatArray: FloatArray? = null,

    var expandableParent: Boolean = false,

    var isExpanded: Boolean = false,

    var lastItemOfExpandableGroup: Int = 0,

    var expandableChildrenAmount: Int = 0,

    var hiddenByCollapse: Boolean = false,

    var openWhenDownloaded: Boolean = true,

    var isTemporary: Boolean = false,

    var referenceId: String? = null,

    var sendStatus: SendStatus? = null,

    var silent: Boolean = false

) : MessageContentType,
    MessageContentType.Image {

    var extractedUrlToPreview: String? = null

    // messageTypesToIgnore is weird. must be deleted by refactoring!!!
    @JsonIgnore
    var messageTypesToIgnore = listOf(
        MessageType.REGULAR_TEXT_MESSAGE,
        MessageType.SYSTEM_MESSAGE,
        MessageType.SINGLE_LINK_VIDEO_MESSAGE,
        MessageType.SINGLE_LINK_AUDIO_MESSAGE,
        MessageType.SINGLE_LINK_MESSAGE,
        MessageType.SINGLE_NC_GEOLOCATION_MESSAGE,
        MessageType.VOICE_MESSAGE,
        MessageType.POLL_MESSAGE,
        MessageType.DECK_CARD
    )

    fun isDeckCard(): Boolean {
        if (messageParameters != null && messageParameters!!.size > 0) {
            for ((_, individualHashMap) in messageParameters!!) {
                if (isHashMapEntryEqualTo(individualHashMap, "type", "deck-card")) {
                    return true
                }
            }
        }
        return false
    }

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
        if (CapabilitiesUtil.isLinkPreviewAvailable(activeUser!!)) {
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
                                activeUser!!.baseUrl!!,
                                individualHashMap["id"]!!,
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
            message!!.trim()
        } else {
            null
        }
    }

    fun getCalculateMessageType(): MessageType =
        if (!TextUtils.isEmpty(systemMessage)) {
            MessageType.SYSTEM_MESSAGE
        } else if (isVoiceMessage) {
            MessageType.VOICE_MESSAGE
        } else if (hasFileAttachment()) {
            MessageType.SINGLE_NC_ATTACHMENT_MESSAGE
        } else if (hasGeoLocation()) {
            MessageType.SINGLE_NC_GEOLOCATION_MESSAGE
        } else if (isPoll()) {
            MessageType.POLL_MESSAGE
        } else if (isDeckCard()) {
            MessageType.DECK_CARD
        } else {
            MessageType.REGULAR_TEXT_MESSAGE
        }

    override fun getId(): String = jsonMessageId.toString()

    override fun getText(): String =
        if (message != null) {
            getParsedMessage(message, messageParameters)!!
        } else {
            ""
        }

    fun getNullsafeActorDisplayName() =
        if (!TextUtils.isEmpty(actorDisplayName)) {
            actorDisplayName
        } else {
            sharedApplication!!.getString(R.string.nc_guest)
        }

    override fun getUser(): IUser =
        object : IUser {
            override fun getId(): String = "$actorType/$actorId"

            override fun getName(): String =
                if (!TextUtils.isEmpty(actorDisplayName)) {
                    actorDisplayName!!
                } else {
                    sharedApplication!!.getString(R.string.nc_guest)
                }

            override fun getAvatar(): String? =
                when {
                    activeUser == null -> {
                        null
                    }

                    actorType == "users" -> {
                        ApiUtils.getUrlForAvatar(activeUser!!.baseUrl!!, actorId, true)
                    }

                    actorType == "bridged" -> {
                        ApiUtils.getUrlForAvatar(
                            activeUser!!.baseUrl!!,
                            "bridge-bot",
                            true
                        )
                    }
                    else -> {
                        var apiId: String? = sharedApplication!!.getString(R.string.nc_guest)
                        if (!TextUtils.isEmpty(actorDisplayName)) {
                            apiId = actorDisplayName
                        }
                        ApiUtils.getUrlForGuestAvatar(activeUser!!.baseUrl!!, apiId, true)
                    }
                }
        }

    override fun getCreatedAt(): Date = Date(timestamp * MILLIES)

    override fun getSystemMessage(): String = EnumSystemMessageTypeConverter().convertToString(systemMessageType)

    private fun isHashMapEntryEqualTo(map: HashMap<String?, String?>, key: String, searchTerm: String): Boolean =
        map != null && MessageDigest.isEqual(map[key]!!.toByteArray(), searchTerm.toByteArray())

    // needed a equals and hashcode function to fix detekt errors
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return false
    }

    override fun hashCode(): Int = 0

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
        VOICE_MESSAGE,
        DECK_CARD
    }

    /**
     * see https://nextcloud-talk.readthedocs.io/en/latest/chat/#system-messages
     */
    enum class SystemMessageType {
        DUMMY,
        CONVERSATION_CREATED,
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
        MESSAGE_EDITED,
        FILE_SHARED,
        OBJECT_SHARED,
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
        BREAKOUT_ROOMS_STOPPED,
        AVATAR_SET,
        AVATAR_REMOVED,
        FEDERATED_USER_ADDED,
        FEDERATED_USER_REMOVED,
        PHONE_ADDED,
        THREAD_CREATED
    }

    companion object {
        private const val TAG = "ChatMessage"
        private const val MILLIES: Long = 1000L

        private const val REGEX_STRING_DEFAULT =
            """(\s|\n|^)(https?:\/\/)((?:[-A-Z0-9+_]+\.)+[-A-Z]+(?:\/[-A-Z0-9+&@#%?=~_|!:,.;()]*)*)(\s|\n|$)"""
    }
}
