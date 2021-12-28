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
package com.nextcloud.talk.models.database;

import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.models.json.capabilities.Capabilities;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;

public abstract class CapabilitiesUtil {
    private static final String TAG = CapabilitiesUtil.class.getSimpleName();

    public static boolean hasNotificationsCapability(@Nullable UserEntity user, String capabilityName) {
        if (user != null && user.getCapabilities() != null) {
            try {
                Capabilities capabilities = LoganSquare.parse(user.getCapabilities(), Capabilities.class);
                if (capabilities.getNotificationsCapability() != null &&
                        capabilities.getNotificationsCapability().getFeatures() != null) {
                    return capabilities.getSpreedCapability().getFeatures().contains(capabilityName);
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to get capabilities for the user");
            }
        }
        return false;
    }

    public static boolean hasExternalCapability(@Nullable UserEntity user, String capabilityName) {
        if (user != null && user.getCapabilities() != null) {
            try {
                Capabilities capabilities = LoganSquare.parse(user.getCapabilities(), Capabilities.class);
                if (capabilities.getExternalCapability() != null &&
                        capabilities.getExternalCapability().containsKey("v1")) {
                    return capabilities.getExternalCapability().get("v1").contains(capabilityName);
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to get capabilities for the user");
            }
        }
        return false;
    }

    public static boolean isServerEOL(@Nullable UserEntity user) {
        // Capability is available since Talk 4 => Nextcloud 14 => Autmn 2018
        return !hasSpreedFeatureCapability(user, "no-ping");
    }

    public static boolean isServerAlmostEOL(@Nullable UserEntity user) {
        // Capability is available since Talk 8 => Nextcloud 18 => January 2020
        return !hasSpreedFeatureCapability(user, "chat-replies");
    }

    public static boolean canSetChatReadMarker(@Nullable UserEntity user) {
        return hasSpreedFeatureCapability(user, "chat-read-marker");
    }

    public static boolean hasSpreedFeatureCapability(@Nullable UserEntity user, String capabilityName) {
        if (user != null && user.getCapabilities() != null) {
            try {
                Capabilities capabilities = LoganSquare.parse(user.getCapabilities(), Capabilities.class);
                if (capabilities != null && capabilities.getSpreedCapability() != null &&
                        capabilities.getSpreedCapability().getFeatures() != null) {
                    return capabilities.getSpreedCapability().getFeatures().contains(capabilityName);
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to get capabilities for the user");
            }
        }
        return false;
    }

    public static Integer getMessageMaxLength(@Nullable UserEntity user) {
        if (user != null && user.getCapabilities() != null) {
            try {
                Capabilities capabilities = LoganSquare.parse(user.getCapabilities(), Capabilities.class);
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
            } catch (IOException e) {
                Log.e(TAG, "Failed to get capabilities for the user");
            }
        }
        return 1000;
    }

    public static boolean isPhoneBookIntegrationAvailable(@Nullable UserEntity user) {
        if (user != null && user.getCapabilities() != null) {
            try {
                Capabilities capabilities = LoganSquare.parse(user.getCapabilities(), Capabilities.class);
                return capabilities != null &&
                        capabilities.getSpreedCapability() != null &&
                        capabilities.getSpreedCapability().getFeatures() != null &&
                        capabilities.getSpreedCapability().getFeatures().contains("phonebook-search");
            } catch (IOException e) {
                Log.e(TAG, "Failed to get capabilities for the user");
            }
        }
        return false;
    }

    public static boolean isReadStatusAvailable(@Nullable UserEntity user) {
        if (user != null && user.getCapabilities() != null) {
            try {
                Capabilities capabilities = LoganSquare.parse(user.getCapabilities(), Capabilities.class);
                if (capabilities != null &&
                        capabilities.getSpreedCapability() != null &&
                        capabilities.getSpreedCapability().getConfig() != null &&
                        capabilities.getSpreedCapability().getConfig().containsKey("chat")) {
                    Map<String, String> map = capabilities.getSpreedCapability().getConfig().get("chat");
                    return map != null && map.containsKey("read-privacy");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to get capabilities for the user");
            }
        }
        return false;
    }

    public static boolean isReadStatusPrivate(@Nullable UserEntity user) {
        if (user != null && user.getCapabilities() != null) {
            try {
                Capabilities capabilities = LoganSquare.parse(user.getCapabilities(), Capabilities.class);
                if (capabilities != null &&
                        capabilities.getSpreedCapability() != null &&
                        capabilities.getSpreedCapability().getConfig() != null &&
                        capabilities.getSpreedCapability().getConfig().containsKey("chat")) {
                    HashMap<String, String> map = capabilities.getSpreedCapability().getConfig().get("chat");
                    if (map != null && map.containsKey("read-privacy")) {
                        return Integer.parseInt(map.get("read-privacy")) == 1;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to get capabilities for the user");
            }
        }
        return false;
    }

    public static boolean isUserStatusAvailable(@Nullable UserEntity user) {
        if (user != null && user.getCapabilities() != null) {
            try {
                Capabilities capabilities = LoganSquare.parse(user.getCapabilities(), Capabilities.class);
                if (capabilities.getUserStatusCapability() != null &&
                    capabilities.getUserStatusCapability().isEnabled() &&
                    capabilities.getUserStatusCapability().isSupportsEmoji()) {
                    return true;
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to get capabilities for the user");
            }
        }
        return false;
    }

    public static String getAttachmentFolder(@Nullable UserEntity user) {
        if (user != null && user.getCapabilities() != null) {
            try {
                Capabilities capabilities = LoganSquare.parse(user.getCapabilities(), Capabilities.class);
                if (capabilities != null &&
                        capabilities.getSpreedCapability() != null &&
                        capabilities.getSpreedCapability().getConfig() != null &&
                        capabilities.getSpreedCapability().getConfig().containsKey("attachments")) {
                    HashMap<String, String> map = capabilities.getSpreedCapability().getConfig().get("attachments");
                    if (map != null && map.containsKey("folder")) {
                        return map.get("folder");
                    }
                }
            } catch (IOException e) {
                Log.e("User.java", "Failed to get attachment folder", e);
            }
        }
        return "/Talk";
    }

    public static String getServerName(@Nullable UserEntity user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities;
            try {
                capabilities = LoganSquare.parse(user.getCapabilities(), Capabilities.class);
                if (capabilities != null && capabilities.getThemingCapability() != null) {
                    return capabilities.getThemingCapability().getName();
                }
            } catch (IOException e) {
                Log.e("User.java", "Failed to get server name", e);
            }
        }
        return "";
    }

    // TODO later avatar can also be checked via user fields, for now it is in Talk capability
    public static boolean isAvatarEndpointAvailable(@Nullable UserEntity user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities;
            try {
                capabilities = LoganSquare.parse(user.getCapabilities(), Capabilities.class);
                return (capabilities != null &&
                        capabilities.getSpreedCapability() != null &&
                        capabilities.getSpreedCapability().getFeatures() != null &&
                        capabilities.getSpreedCapability().getFeatures().contains("temp-user-avatar-api"));
            } catch (IOException e) {
                Log.e("User.java", "Failed to get server name", e);
            }
        }
        return false;
    }

    public static boolean canEditScopes(@Nullable UserEntity user) {
        if (user != null && user.getCapabilities() != null) {
            Capabilities capabilities;
            try {
                capabilities = LoganSquare.parse(user.getCapabilities(), Capabilities.class);
                return (capabilities != null &&
                        capabilities.getProvisioningCapability() != null &&
                        capabilities.getProvisioningCapability().getAccountPropertyScopesVersion() != null &&
                        capabilities.getProvisioningCapability().getAccountPropertyScopesVersion() > 1);
            } catch (IOException e) {
                Log.e("User.java", "Failed to get server name", e);
            }
        }
        return false;
    }
}
