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
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.data.database.model.SendStatus
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.chat.ChatUtils.Companion.getParsedMessage
import com.nextcloud.talk.utils.CapabilitiesUtil
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

// Domain model for chat message. No entries here that are only necessary for the database layer, nor only for UI layer
data class ChatMessage(
    var isGrouped: Boolean = false,

    var isGroupedWithNext: Boolean = false,

    var isOneToOneConversation: Boolean = false,

    var isFormerOneToOneConversation: Boolean = false,

    @Deprecated("should be deleted in long term")
    var activeUser: User? = null,

    var isDeleted: Boolean = false,

    var jsonMessageId: Int = 0,

    var previousMessageId: Int = -1,

    var token: String? = null,

    var threadId: Long? = null,

    var isThread: Boolean = false,

    var threadTitle: String? = null,

    var threadReplies: Int? = 0,

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

    var messageType: String? = null,

    var reactions: LinkedHashMap<String, Int>? = null,

    var reactionsSelf: ArrayList<String>? = null,

    var expirationTimestamp: Int = 0,

    var renderMarkdown: Boolean? = null,

    var lastEditActorDisplayName: String? = null,

    var lastEditActorId: String? = null,

    var lastEditActorType: String? = null,

    var lastEditTimestamp: Long? = 0,

    var incoming: Boolean = false,

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

    var silent: Boolean = false,

    var pinnedActorType: String? = null,

    var pinnedActorId: String? = null,

    var pinnedActorDisplayName: String? = null,

    var pinnedAt: Long? = null,

    var pinnedUntil: Long? = null,

    var sendAt: Int? = null,

    var avatarUrl: String? = null,

    var isUnread: Boolean = false

) {

    val fileParameters by lazy { FileParameters(messageParameters) }
    val geoLocationParameters by lazy { GeoLocationParameters(messageParameters) }
    val pollParameters by lazy { PollParameters(messageParameters) }
    val deckCardParameters by lazy { DeckCardParameters(messageParameters) }

    val hasFileAttachment get() = messageParameters?.containsKey("file") == true
    val hasGeoLocation get() = messageParameters?.get("object")?.get("type") == "geo-location"
    val hasPoll get() = messageParameters?.get("object")?.get("type") == "talk-poll"
    val hasDeckCard get() = messageParameters?.get("object")?.get("type") == "deck-card"

    val isSystemMessage = systemMessageType?.let { it != SystemMessageType.DUMMY } ?: false

    fun getCalculateMessageType(): MessageType =
        when {
            isSystemMessage -> MessageType.SYSTEM_MESSAGE
            isVoiceMessage -> MessageType.VOICE_MESSAGE
            hasFileAttachment -> MessageType.SINGLE_NC_ATTACHMENT_MESSAGE
            hasGeoLocation -> MessageType.SINGLE_NC_GEOLOCATION_MESSAGE
            hasPoll -> MessageType.POLL_MESSAGE
            hasDeckCard -> MessageType.DECK_CARD
            else -> MessageType.REGULAR_TEXT_MESSAGE
        }

    var extractedUrlToPreview: String? = null

    fun extractLinkPreviewUrl(user: User): String? {
        val messageText = message
        if (!CapabilitiesUtil.isLinkPreviewAvailable(user) || messageText == null) {
            return null
        }

        val text: CharSequence = StringBuffer(messageText)
        val regexOptions = setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)

        val regexStringFromServer = user.capabilities?.coreCapability?.referenceRegex
        val regexFromServer = regexStringFromServer?.toRegex(regexOptions)
        val serverMatch = regexFromServer?.find(text)?.groups?.get(0)?.value?.trim()

        return serverMatch ?: REGEX_STRING_DEFAULT.toRegex(regexOptions)
            .find(text)
            ?.groups
            ?.get(0)
            ?.value
            ?.trim()
    }

    fun getRichText(): String =
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

    val createdAt: Date = Date(timestamp * MILLIES)

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

    fun ChatMessage.dateKey(): LocalDate =
        Instant.ofEpochMilli(timestamp * MILLIES)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

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
        THREAD_CREATED,
        MESSAGE_PINNED,
        MESSAGE_UNPINNED
    }

    companion object {
        private const val TAG = "ChatMessage"
        private const val MILLIES: Long = 1000L

        private const val REGEX_STRING_DEFAULT =
            """(\s|\n|^)(https?:\/\/)((?:[-A-Z0-9+_]+\.)+[-A-Z]+(?:\/[-A-Z0-9+&@#%?=~_|!:,.;()]*)*)(\s|\n|$)"""
    }
}
