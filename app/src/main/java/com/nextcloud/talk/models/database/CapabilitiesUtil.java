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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Deprecated, please migrate to {@link com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew}.
 */
@Deprecated
public abstract class CapabilitiesUtil {
    private static final String TAG = CapabilitiesUtil.class.getSimpleName();

    public static boolean hasSpreedFeatureCapability(@Nullable UserEntity user, String capabilityName) {
        if (user != null && user.getCapabilities() != null) {
            try {
                Capabilities capabilities = parseUserCapabilities(user);
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

    public static boolean isUserStatusAvailable(@Nullable UserEntity user) {
        if (user != null && user.getCapabilities() != null) {
            try {
                Capabilities capabilities = parseUserCapabilities(user);
                if (capabilities.getUserStatusCapability() != null &&
                    capabilities.getUserStatusCapability().getEnabled() &&
                    capabilities.getUserStatusCapability().getSupportsEmoji()) {
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
                Capabilities capabilities = parseUserCapabilities(user);
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
            try {
                Capabilities capabilities = parseUserCapabilities(user);
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
            try {
                Capabilities capabilities = parseUserCapabilities(user);
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
            try {
                Capabilities capabilities = parseUserCapabilities(user);
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

    private static Capabilities parseUserCapabilities(@NonNull final UserEntity user) throws IOException {
        return LoganSquare.parse(user.getCapabilities(), Capabilities.class);
    }
}
