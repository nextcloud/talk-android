/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
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
package com.nextcloud.talk.utils.database.user

import com.nextcloud.talk.data.user.model.User

@Suppress("TooManyFunctions")
object CapabilitiesUtilNew {
    fun hasNotificationsCapability(user: User, capabilityName: String): Boolean {
        return user.capabilities?.spreedCapability?.features?.contains(capabilityName) == true
    }

    fun hasExternalCapability(user: User, capabilityName: String?): Boolean {
        if (user.capabilities?.externalCapability?.containsKey("v1") == true) {
            return user.capabilities!!.externalCapability!!["v1"]?.contains(capabilityName!!) == true
        }
        return false
    }

    @JvmStatic
    fun isServerEOL(user: User): Boolean {
        // Capability is available since Talk 4 => Nextcloud 14 => Autmn 2018
        return !hasSpreedFeatureCapability(user, "no-ping")
    }

    fun isServerAlmostEOL(user: User): Boolean {
        // Capability is available since Talk 8 => Nextcloud 18 => January 2020
        return !hasSpreedFeatureCapability(user, "chat-replies")
    }

    fun canSetChatReadMarker(user: User): Boolean {
        return hasSpreedFeatureCapability(user, "chat-read-marker")
    }

    fun canMarkRoomAsUnread(user: User): Boolean {
        return hasSpreedFeatureCapability(user, "chat-unread")
    }

    @JvmStatic
    fun hasSpreedFeatureCapability(user: User?, capabilityName: String): Boolean {
        if (user?.capabilities?.spreedCapability?.features != null) {
            return user.capabilities!!.spreedCapability!!.features!!.contains(capabilityName)
        }
        return false
    }

    fun getMessageMaxLength(user: User?): Int {
        if (user?.capabilities?.spreedCapability?.config?.containsKey("chat") == true) {
            val chatConfigHashMap = user.capabilities!!.spreedCapability!!.config!!["chat"]
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

    fun isPhoneBookIntegrationAvailable(user: User): Boolean {
        return user.capabilities?.spreedCapability?.features?.contains("phonebook-search") == true
    }

    fun isReadStatusAvailable(user: User): Boolean {
        if (user.capabilities?.spreedCapability?.config?.containsKey("chat") == true) {
            val map: Map<String, Any>? = user.capabilities!!.spreedCapability!!.config!!["chat"]
            return map != null && map.containsKey("read-privacy")
        }
        return false
    }

    fun isReadStatusPrivate(user: User): Boolean {
        if (user.capabilities?.spreedCapability?.config?.containsKey("chat") == true) {
            val map = user.capabilities!!.spreedCapability!!.config!!["chat"]
            if (map?.containsKey("read-privacy") == true) {
                return (map["read-privacy"]!!.toString()).toInt() == 1
            }
        }

        return false
    }

    @JvmStatic
    fun isCallRecordingAvailable(user: User): Boolean {
        if (hasSpreedFeatureCapability(user, "recording-v1") &&
            user.capabilities?.spreedCapability?.config?.containsKey("call") == true
        ) {
            val map: Map<String, Any>? = user.capabilities!!.spreedCapability!!.config!!["call"]
            if (map != null && map.containsKey("recording")) {
                return (map["recording"].toString()).toBoolean()
            }
        }
        return false
    }

    @JvmStatic
    fun isUserStatusAvailable(user: User): Boolean {
        return user.capabilities?.userStatusCapability?.enabled == true &&
            user.capabilities?.userStatusCapability?.supportsEmoji == true
    }

    @JvmStatic
    fun getAttachmentFolder(user: User): String? {
        if (user.capabilities?.spreedCapability?.config?.containsKey("attachments") == true) {
            val map = user.capabilities!!.spreedCapability!!.config!!["attachments"]
            if (map?.containsKey("folder") == true) {
                return map["folder"].toString()
            }
        }
        return "/Talk"
    }

    fun getServerName(user: User?): String? {
        if (user?.capabilities?.themingCapability != null) {
            return user.capabilities!!.themingCapability!!.name
        }
        return ""
    }

    // TODO later avatar can also be checked via user fields, for now it is in Talk capability
    fun isAvatarEndpointAvailable(user: User): Boolean {
        return user.capabilities?.spreedCapability?.features?.contains("temp-user-avatar-api") == true
    }

    fun isConversationAvatarEndpointAvailable(user: User): Boolean {
        return user.capabilities?.spreedCapability?.features?.contains("avatar") == true
    }

    fun isConversationDescriptionEndpointAvailable(user: User): Boolean {
        return user.capabilities?.spreedCapability?.features?.contains("room-description") == true
    }

    fun canEditScopes(user: User): Boolean {
        return user.capabilities?.provisioningCapability?.accountPropertyScopesVersion != null &&
            user.capabilities!!.provisioningCapability!!.accountPropertyScopesVersion!! > 1
    }

    fun isAbleToCall(user: User?): Boolean {
        if (user?.capabilities != null) {
            val capabilities = user.capabilities
            return if (
                capabilities?.spreedCapability?.config?.containsKey("call") == true &&
                capabilities.spreedCapability!!.config!!["call"] != null &&
                capabilities.spreedCapability!!.config!!["call"]!!.containsKey("enabled")
            ) {
                java.lang.Boolean.parseBoolean(capabilities.spreedCapability!!.config!!["call"]!!["enabled"].toString())
            } else {
                // older nextcloud versions without the capability can't disable the calls
                true
            }
        }
        return false
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

    @JvmStatic
    fun isUnifiedSearchAvailable(user: User): Boolean {
        return hasSpreedFeatureCapability(user, "unified-search")
    }

    @JvmStatic
    fun isLinkPreviewAvailable(user: User): Boolean {
        if (user.capabilities?.coreCapability?.referenceApi != null &&
            user.capabilities?.coreCapability?.referenceApi == "true"
        ) {
            return true
        }
        return false
    }

    fun isTranslationsSupported(user: User?): Boolean {
        if (user?.capabilities != null) {
            val capabilities = user.capabilities
            return capabilities?.spreedCapability?.config?.containsKey("chat") == true &&
                capabilities.spreedCapability!!.config!!["chat"] != null &&
                capabilities.spreedCapability!!.config!!["chat"]!!.containsKey("translations")
        }

        return false
    }

    fun getLanguages(user: User?) : Any? {

        if(isTranslationsSupported(user)) {
            return user!!.capabilities!!.spreedCapability!!.config!!["chat"]!!["translations"]
        }

        return null
    }

    const val DEFAULT_CHAT_SIZE = 1000
}
