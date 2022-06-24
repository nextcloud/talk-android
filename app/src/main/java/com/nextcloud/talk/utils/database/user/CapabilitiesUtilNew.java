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
package com.nextcloud.talk.utils.database.user;

import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.json.capabilities.Capabilities;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;

public abstract class CapabilitiesUtilNew {

    public static boolean hasNotificationsCapability(@Nullable User user, String capabilityName) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            if (capabilities.getNotificationsCapability() != null &&
                capabilities.getNotificationsCapability().getFeatures() != null) {
                return capabilities.getSpreedCapability().getFeatures().contains(capabilityName);
            }
        }
        return false;
    }

    public static boolean hasExternalCapability(@Nullable User user, String capabilityName) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            if (capabilities.getExternalCapability() != null &&
                capabilities.getExternalCapability().containsKey("v1")) {
                return capabilities.getExternalCapability().get("v1").contains(capabilityName);
            }
        }
        return false;
    }

    public static boolean isServerEOL(@Nullable User user) {
        // Capability is available since Talk 4 => Nextcloud 14 => Autmn 2018
        return !hasSpreedFeatureCapability(user, "no-ping");
    }

    public static boolean isServerAlmostEOL(@Nullable User user) {
        // Capability is available since Talk 8 => Nextcloud 18 => January 2020
        return !hasSpreedFeatureCapability(user, "chat-replies");
    }

    public static boolean canSetChatReadMarker(@Nullable User user) {
        return hasSpreedFeatureCapability(user, "chat-read-marker");
    }

    public static boolean hasSpreedFeatureCapability(@Nullable User user, String capabilityName) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            if (capabilities != null && capabilities.getSpreedCapability() != null &&
                capabilities.getSpreedCapability().getFeatures() != null) {
                return capabilities.getSpreedCapability().getFeatures().contains(capabilityName);
            }
        }
        return false;
    }

    public static Integer getMessageMaxLength(@Nullable User user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            if (capabilities != null &&
                capabilities.getSpreedCapability() != null &&
                capabilities.getSpreedCapability().getConfig() != null &&
                capabilities.getSpreedCapability().getConfig().containsKey("chat")) {
                HashMap<String, String> chatConfigHashMap = capabilities
                    .getSpreedCapability()
                    .getConfig()
                    .get("chat");
                if (chatConfigHashMap != null && chatConfigHashMap.containsKey("max-length")) {
                    int chatSize = Integer.parseInt(chatConfigHashMap.get("max-length"));
                    if (chatSize > 0) {
                        return chatSize;
                    } else {
                        return 1000;
                    }
                }
            }
        }
        return 1000;
    }

    public static boolean isPhoneBookIntegrationAvailable(@Nullable User user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            return capabilities != null &&
                capabilities.getSpreedCapability() != null &&
                capabilities.getSpreedCapability().getFeatures() != null &&
                capabilities.getSpreedCapability().getFeatures().contains("phonebook-search");
        }
        return false;
    }

    public static boolean isReadStatusAvailable(@Nullable User user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            if (capabilities != null &&
                capabilities.getSpreedCapability() != null &&
                capabilities.getSpreedCapability().getConfig() != null &&
                capabilities.getSpreedCapability().getConfig().containsKey("chat")) {
                Map<String, String> map = capabilities.getSpreedCapability().getConfig().get("chat");
                return map != null && map.containsKey("read-privacy");
            }
        }
        return false;
    }

    public static boolean isReadStatusPrivate(@Nullable User user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            if (capabilities != null &&
                capabilities.getSpreedCapability() != null &&
                capabilities.getSpreedCapability().getConfig() != null &&
                capabilities.getSpreedCapability().getConfig().containsKey("chat")) {
                HashMap<String, String> map = capabilities.getSpreedCapability().getConfig().get("chat");
                if (map != null && map.containsKey("read-privacy")) {
                    return Integer.parseInt(map.get("read-privacy")) == 1;
                }
            }
        }
        return false;
    }

    public static boolean isUserStatusAvailable(@Nullable User user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            if (capabilities.getUserStatusCapability() != null &&
                capabilities.getUserStatusCapability().getEnabled() &&
                capabilities.getUserStatusCapability().getSupportsEmoji()) {
                return true;
            }
        }
        return false;
    }

    public static String getAttachmentFolder(@Nullable User user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            if (capabilities != null &&
                capabilities.getSpreedCapability() != null &&
                capabilities.getSpreedCapability().getConfig() != null &&
                capabilities.getSpreedCapability().getConfig().containsKey("attachments")) {
                HashMap<String, String> map = capabilities.getSpreedCapability().getConfig().get("attachments");
                if (map != null && map.containsKey("folder")) {
                    return map.get("folder");
                }
            }
        }
        return "/Talk";
    }

    public static String getServerName(@Nullable User user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            if (capabilities != null && capabilities.getThemingCapability() != null) {
                return capabilities.getThemingCapability().getName();
            }
        }
        return "";
    }

    // TODO later avatar can also be checked via user fields, for now it is in Talk capability
    public static boolean isAvatarEndpointAvailable(@Nullable User user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            return (capabilities != null &&
                capabilities.getSpreedCapability() != null &&
                capabilities.getSpreedCapability().getFeatures() != null &&
                capabilities.getSpreedCapability().getFeatures().contains("temp-user-avatar-api"));
        }
        return false;
    }

    public static boolean canEditScopes(@Nullable User user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            return (capabilities != null &&
                capabilities.getProvisioningCapability() != null &&
                capabilities.getProvisioningCapability().getAccountPropertyScopesVersion() != null &&
                capabilities.getProvisioningCapability().getAccountPropertyScopesVersion() > 1);
        }
        return false;
    }

    public static boolean isAbleToCall(@Nullable User user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities = user.getCapabilities();
            if (capabilities != null &&
                capabilities.getSpreedCapability() != null &&
                capabilities.getSpreedCapability().getConfig() != null &&
                capabilities.getSpreedCapability().getConfig().containsKey("call") &&
                capabilities.getSpreedCapability().getConfig().get("call") != null &&
                capabilities.getSpreedCapability().getConfig().get("call").containsKey("enabled")) {
                return Boolean.parseBoolean(
                    capabilities.getSpreedCapability().getConfig().get("call").get("enabled"));
            } else {
                // older nextcloud versions without the capability can't disable the calls
                return true;
            }
        }
        return false;
    }

    public static boolean isUnifiedSearchAvailable(@Nullable final User user) {
        return hasSpreedFeatureCapability(user, "unified-search");
    }
}
