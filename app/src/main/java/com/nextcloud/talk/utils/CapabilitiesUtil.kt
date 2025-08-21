/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023-2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.util.Log
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.capabilities.SpreedCapability

enum class SpreedFeatures(val value: String) {
    RECORDING_V1("recording-v1"),
    REACTIONS("reactions"),
    RAISE_HAND("raise-hand"),
    DIRECT_MENTION_FLAG("direct-mention-flag"),
    CONVERSATION_CALL_FLAGS("conversation-call-flags"),
    SILENT_SEND("silent-send"),
    MENTION_FLAG("mention-flag"),
    DELETE_MESSAGES("delete-messages"),
    READ_ONLY_ROOMS("read-only-rooms"),
    RICH_OBJECT_LIST_MEDIA("rich-object-list-media"),
    SILENT_CALL("silent-call"),
    MESSAGE_EXPIRATION("message-expiration"),
    WEBINARY_LOBBY("webinary-lobby"),
    VOICE_MESSAGE_SHARING("voice-message-sharing"),
    INVITE_GROUPS_AND_MAILS("invite-groups-and-mails"),
    CIRCLES_SUPPORT("circles-support"),
    LAST_ROOM_ACTIVITY("last-room-activity"),
    NOTIFICATION_LEVELS("notification-levels"),
    CLEAR_HISTORY("clear-history"),
    AVATAR("avatar"),
    LISTABLE_ROOMS("listable-rooms"),
    LOCKED_ONE_TO_ONE_ROOMS("locked-one-to-one-rooms"),
    TEMP_USER_AVATAR_API("temp-user-avatar-api"),
    PHONEBOOK_SEARCH("phonebook-search"),
    GEO_LOCATION_SHARING("geo-location-sharing"),
    TALK_POLLS("talk-polls"),
    FAVORITES("favorites"),
    CHAT_READ_MARKER("chat-read-marker"),
    CHAT_UNREAD("chat-unread"),
    EDIT_MESSAGES("edit-messages"),
    REMIND_ME_LATER("remind-me-later"),
    CHAT_V2("chat-v2"),
    SIP_SUPPORT("sip-support"),
    SIGNALING_V3("signaling-v3"),
    ROOM_DESCRIPTION("room-description"),
    UNIFIED_SEARCH("unified-search"),
    LOCKED_ONE_TO_ONE("locked-one-to-one-rooms"),
    CHAT_PERMISSION("chat-permission"),
    CONVERSATION_PERMISSION("conversation-permissions"),
    FEDERATION_V1("federation-v1"),
    DELETE_MESSAGES_UNLIMITED("delete-messages-unlimited"),
    BAN_V1("ban-v1"),
    EDIT_MESSAGES_NOTE_TO_SELF("edit-messages-note-to-self"),
    ARCHIVE_CONVERSATIONS("archived-conversations-v2"),
    CONVERSATION_CREATION_ALL("conversation-creation-all"),
    UNBIND_CONVERSATION("unbind-conversation"),
    SENSITIVE_CONVERSATIONS("sensitive-conversations"),
    IMPORTANT_CONVERSATIONS("important-conversations"),
    THREADS("threads")
}

@Suppress("TooManyFunctions")
object CapabilitiesUtil {

    //region Version checks
    fun isServerEOL(serverVersion: Int?): Boolean {
        if (serverVersion == null) {
            Log.w(TAG, "serverVersion is unknown. It is assumed that it is up to date")
            return false
        }
        return (serverVersion < SERVER_VERSION_MIN_SUPPORTED)
    }

    fun isServerAlmostEOL(serverVersion: Int?): Boolean {
        if (serverVersion == null) {
            Log.w(TAG, "serverVersion is unknown. It is assumed that it is up to date")
            return false
        }
        return (serverVersion < SERVER_VERSION_SUPPORT_WARNING)
    }

    // endregion

    //region CoreCapabilities

    @JvmStatic
    fun isLinkPreviewAvailable(user: User): Boolean =
        user.capabilities?.coreCapability?.referenceApi != null &&
            user.capabilities?.coreCapability?.referenceApi == "true"

    fun canGeneratePrettyURL(user: User): Boolean = user.capabilities?.coreCapability?.modRewriteWorking == true

    // endregion

    //region SpreedCapabilities

    @JvmStatic
    fun hasSpreedFeatureCapability(spreedCapabilities: SpreedCapability, spreedFeatures: SpreedFeatures): Boolean {
        if (spreedCapabilities.features != null) {
            return spreedCapabilities.features!!.contains(spreedFeatures.value)
        }
        return false
    }

    fun isSharedItemsAvailable(spreedCapabilities: SpreedCapability): Boolean =
        hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.RICH_OBJECT_LIST_MEDIA)

    fun getMessageMaxLength(spreedCapabilities: SpreedCapability): Int {
        if (spreedCapabilities.config?.containsKey("chat") == true) {
            val chatConfigHashMap = spreedCapabilities.config!!["chat"]
            if (chatConfigHashMap?.containsKey("max-length") == true) {
                val chatSize = (chatConfigHashMap["max-length"]!!.toString()).toInt()
                return if (chatSize > 0) {
                    chatSize
                } else {
                    DEFAULT_CHAT_SIZE
                }
            }
        }

        return DEFAULT_CHAT_SIZE
    }

    fun conversationDescriptionLength(spreedCapabilities: SpreedCapability): Int {
        if (spreedCapabilities.config?.containsKey("conversations") == true) {
            val map: Map<String, Any>? = spreedCapabilities.config!!["conversations"]
            if (map != null && map.containsKey("description-length")) {
                return (map["description-length"].toString().toInt())
            }
        }
        return CONVERSATION_DESCRIPTION_LENGTH_FOR_OLD_SERVER
    }

    fun isReadStatusAvailable(spreedCapabilities: SpreedCapability): Boolean {
        if (spreedCapabilities.config?.containsKey("chat") == true) {
            val map: Map<String, Any>? = spreedCapabilities.config!!["chat"]
            return map != null && map.containsKey("read-privacy")
        }
        return false
    }

    fun retentionOfEventRooms(spreedCapabilities: SpreedCapability): Int {
        if (spreedCapabilities.config?.containsKey("conversations") == true) {
            val map = spreedCapabilities.config!!["conversations"]
            if (map?.containsKey("retention-event") == true) {
                return map["retention-event"].toString().toInt()
            }
        }
        return 0
    }

    fun retentionOfSIPRoom(spreedCapabilities: SpreedCapability): Int {
        if (spreedCapabilities.config?.containsKey("conversations") == true) {
            val map = spreedCapabilities.config!!["conversations"]
            if (map?.containsKey("retention-phone") == true) {
                return map["retention-phone"].toString().toInt()
            }
        }
        return 0
    }

    fun retentionOfInstantMeetingRoom(spreedCapabilities: SpreedCapability): Int {
        if (spreedCapabilities.config?.containsKey("conversations") == true) {
            val map = spreedCapabilities.config!!["conversations"]
            if (map?.containsKey("retention-instant-meetings") == true) {
                return map["retention-instant-meetings"].toString().toInt()
            }
        }
        return 0
    }

    @JvmStatic
    fun isCallRecordingAvailable(spreedCapabilities: SpreedCapability): Boolean {
        if (hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.RECORDING_V1) &&
            spreedCapabilities.config?.containsKey("call") == true
        ) {
            val map: Map<String, Any>? = spreedCapabilities.config!!["call"]
            if (map != null && map.containsKey("recording")) {
                return (map["recording"].toString()).toBoolean()
            }
        }
        return false
    }

    @JvmStatic
    fun getAttachmentFolder(spreedCapabilities: SpreedCapability): String {
        if (spreedCapabilities.config?.containsKey("attachments") == true) {
            val map = spreedCapabilities.config!!["attachments"]
            if (map?.containsKey("folder") == true) {
                return map["folder"].toString()
            }
        }
        return "/Talk"
    }

    fun isConversationDescriptionEndpointAvailable(spreedCapabilities: SpreedCapability): Boolean =
        hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.ROOM_DESCRIPTION)

    fun isUnifiedSearchAvailable(spreedCapabilities: SpreedCapability): Boolean =
        hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.UNIFIED_SEARCH)

    fun isAbleToCall(spreedCapabilities: SpreedCapability): Boolean =
        if (
            spreedCapabilities.config?.containsKey("call") == true &&
            spreedCapabilities.config!!["call"] != null &&
            spreedCapabilities.config!!["call"]!!.containsKey("enabled")
        ) {
            java.lang.Boolean.parseBoolean(spreedCapabilities.config!!["call"]!!["enabled"].toString())
        } else {
            // older nextcloud versions without the capability can't disable the calls
            true
        }

    fun isCallReactionsSupported(user: User?): Boolean {
        if (user?.capabilities != null) {
            val capabilities = user.capabilities
            return capabilities?.spreedCapability?.config?.containsKey("call") == true &&
                capabilities.spreedCapability!!.config!!["call"] != null &&
                capabilities.spreedCapability!!.config!!["call"]!!.containsKey("supported-reactions")
        }
        return false
    }

    fun isTranslationsSupported(spreedCapabilities: SpreedCapability): Boolean =
        spreedCapabilities.config?.containsKey("chat") == true &&
            spreedCapabilities.config!!["chat"] != null &&
            spreedCapabilities.config!!["chat"]!!.containsKey("has-translation-providers") &&
            spreedCapabilities.config!!["chat"]!!["has-translation-providers"] == true

    fun getRecordingConsentType(spreedCapabilities: SpreedCapability): Int {
        if (
            spreedCapabilities.config?.containsKey("call") == true &&
            spreedCapabilities.config!!["call"] != null &&
            spreedCapabilities.config!!["call"]!!.containsKey("recording-consent")
        ) {
            return when (
                spreedCapabilities.config!!["call"]!!["recording-consent"].toString()
                    .toInt()
            ) {
                1 -> RECORDING_CONSENT_REQUIRED
                2 -> RECORDING_CONSENT_DEPEND_ON_CONVERSATION
                else -> RECORDING_CONSENT_NOT_REQUIRED
            }
        }
        return RECORDING_CONSENT_NOT_REQUIRED
    }

    fun isBanningAvailable(spreedCapabilities: SpreedCapability): Boolean =
        hasSpreedFeatureCapability(spreedCapabilities, SpreedFeatures.BAN_V1)

    // endregion

    //region SpreedCapabilities that can't be used with federation as the settings for them are global

    fun isReadStatusPrivate(user: User): Boolean {
        if (user.capabilities?.spreedCapability?.config?.containsKey("chat") == true) {
            val map = user.capabilities!!.spreedCapability!!.config!!["chat"]
            if (map?.containsKey("read-privacy") == true) {
                return (map["read-privacy"]!!.toString()).toInt() == 1
            }
        }
        return false
    }

    fun isTypingStatusAvailable(user: User): Boolean {
        if (user.capabilities?.spreedCapability?.config?.containsKey("chat") == true) {
            val map = user.capabilities!!.spreedCapability!!.config!!["chat"]
            return map != null && map.containsKey("typing-privacy")
        }
        return false
    }

    fun isTypingStatusPrivate(user: User): Boolean {
        if (user.capabilities?.spreedCapability?.config?.containsKey("chat") == true) {
            val map = user.capabilities!!.spreedCapability!!.config!!["chat"]
            if (map?.containsKey("typing-privacy") == true) {
                return (map["typing-privacy"]!!.toString()).toInt() == 1
            }
        }
        return false
    }

    fun isFederationAvailable(user: User): Boolean =
        hasSpreedFeatureCapability(user.capabilities!!.spreedCapability!!, SpreedFeatures.FEDERATION_V1) &&
            user.capabilities!!.spreedCapability!!.config?.containsKey("federation") == true &&
            user.capabilities!!.spreedCapability!!.config!!["federation"] != null &&
            user.capabilities!!.spreedCapability!!.config!!["federation"]!!.containsKey("enabled")

    // endregion

    //region ThemingCapabilities

    fun getServerName(user: User?): String? {
        if (user?.capabilities?.themingCapability != null) {
            return user.capabilities!!.themingCapability!!.name
        }
        return ""
    }

    // endregion

    //region ProvisioningCapabilities

    fun canEditScopes(user: User): Boolean =
        user.capabilities?.provisioningCapability?.accountPropertyScopesVersion != null &&
            user.capabilities!!.provisioningCapability!!.accountPropertyScopesVersion!! > 1

    // endregion

    //region UserStatusCapabilities

    @JvmStatic
    fun isUserStatusAvailable(user: User): Boolean =
        user.capabilities?.userStatusCapability?.enabled == true &&
            user.capabilities?.userStatusCapability?.supportsEmoji == true

    fun isRestoreStatusAvailable(user: User): Boolean = user.capabilities?.userStatusCapability?.restore == true

    // endregion

    private val TAG = CapabilitiesUtil::class.java.simpleName
    const val DEFAULT_CHAT_SIZE = 1000
    const val RECORDING_CONSENT_NOT_REQUIRED = 0
    const val RECORDING_CONSENT_REQUIRED = 1
    const val RECORDING_CONSENT_DEPEND_ON_CONVERSATION = 2
    private const val SERVER_VERSION_MIN_SUPPORTED = 17
    private const val SERVER_VERSION_SUPPORT_WARNING = 26
    private const val CONVERSATION_DESCRIPTION_LENGTH_FOR_OLD_SERVER = 500
}
