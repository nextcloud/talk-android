/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
 * @author Marcel Hibbe
 * Copyright (C) 2023-2024 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2021 Andy Scherzinger (info@andy-scherzinger.de)
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
package com.nextcloud.talk.utils

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
    TALK_POLLS("talk-polls")
}

@Suppress("TooManyFunctions")
object CapabilitiesUtil {

    //region Version checks
    fun isServerEOL(serverVersion: Int): Boolean {
        return (serverVersion < SERVER_VERSION_MIN_SUPPORTED)
    }

    fun isServerAlmostEOL(serverVersion: Int): Boolean {
        return (serverVersion < SERVER_VERSION_SUPPORT_WARNING)
    }

    // endregion

    //region CoreCapabilities

    @JvmStatic
    fun isLinkPreviewAvailable(user: User): Boolean {
        return user.capabilities?.coreCapability?.referenceApi != null &&
            user.capabilities?.coreCapability?.referenceApi == "true"
    }

    // endregion

    //region SpreedCapabilities

    @JvmStatic
    fun hasSpreedFeatureCapability(spreedCapabilities: SpreedCapability, spreedFeatures: SpreedFeatures): Boolean {
        if (spreedCapabilities.features != null) {
            return spreedCapabilities.features!!.contains(spreedFeatures.value)
        }
        return false
    }

    @JvmStatic
    @Deprecated("Add your capability to Capability enums and use hasSpreedFeatureCapability with enum.")
    fun hasSpreedFeatureCapability(spreedCapabilities: SpreedCapability, capabilityName: String): Boolean {
        if (spreedCapabilities.features != null) {
            return spreedCapabilities.features!!.contains(capabilityName)
        }
        return false
    }

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

    fun isReadStatusAvailable(spreedCapabilities: SpreedCapability): Boolean {
        if (spreedCapabilities.config?.containsKey("chat") == true) {
            val map: Map<String, Any>? = spreedCapabilities.config!!["chat"]
            return map != null && map.containsKey("read-privacy")
        }
        return false
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

    fun isConversationDescriptionEndpointAvailable(spreedCapabilities: SpreedCapability): Boolean {
        return hasSpreedFeatureCapability(spreedCapabilities, "room-description")
    }

    fun isUnifiedSearchAvailable(spreedCapabilities: SpreedCapability): Boolean {
        return hasSpreedFeatureCapability(spreedCapabilities, "unified-search")
    }

    fun isAbleToCall(spreedCapabilities: SpreedCapability): Boolean {
        return if (
            spreedCapabilities.config?.containsKey("call") == true &&
            spreedCapabilities.config!!["call"] != null &&
            spreedCapabilities.config!!["call"]!!.containsKey("enabled")
        ) {
            java.lang.Boolean.parseBoolean(spreedCapabilities.config!!["call"]!!["enabled"].toString())
        } else {
            // older nextcloud versions without the capability can't disable the calls
            true
        }
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

    fun isTranslationsSupported(spreedCapabilities: SpreedCapability): Boolean {
        return spreedCapabilities.config?.containsKey("chat") == true &&
            spreedCapabilities.config!!["chat"] != null &&
            spreedCapabilities.config!!["chat"]!!.containsKey("has-translation-providers") &&
            spreedCapabilities.config!!["chat"]!!["has-translation-providers"] == true
    }

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

    fun canEditScopes(user: User): Boolean {
        return user.capabilities?.provisioningCapability?.accountPropertyScopesVersion != null &&
            user.capabilities!!.provisioningCapability!!.accountPropertyScopesVersion!! > 1
    }

    // endregion

    //region UserStatusCapabilities

    @JvmStatic
    fun isUserStatusAvailable(user: User): Boolean {
        return user.capabilities?.userStatusCapability?.enabled == true &&
            user.capabilities?.userStatusCapability?.supportsEmoji == true
    }

    // endregion

    const val DEFAULT_CHAT_SIZE = 1000
    const val RECORDING_CONSENT_NOT_REQUIRED = 0
    const val RECORDING_CONSENT_REQUIRED = 1
    const val RECORDING_CONSENT_DEPEND_ON_CONVERSATION = 2
    private const val SERVER_VERSION_MIN_SUPPORTED = 14
    private const val SERVER_VERSION_SUPPORT_WARNING = 18
}
