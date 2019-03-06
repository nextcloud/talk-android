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
package com.nextcloud.talk.models.database;

import android.os.Parcelable;
import android.util.Log;
import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.models.json.capabilities.Capabilities;
import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;
import io.requery.Persistable;

import java.io.IOException;
import java.io.Serializable;

@Entity
public interface User extends Parcelable, Persistable, Serializable {
    static final String TAG = "UserEntity";

    @Key
    @Generated
    long getId();

    String getUserId();

    String getUsername();

    String getBaseUrl();

    String getToken();

    String getDisplayName();

    String getPushConfigurationState();

    String getCapabilities();

    String getClientCertificate();

    String getExternalSignalingServer();

    boolean getCurrent();

    boolean getScheduledForDeletion();

    default boolean hasNotificationsCapability(String capabilityName) {
        if (getCapabilities() != null) {
            try {
                Capabilities capabilities = LoganSquare.parse(getCapabilities(), Capabilities.class);
                if (capabilities.getNotificationsCapability() != null && capabilities.getNotificationsCapability().getFeatures() != null) {
                    return capabilities.getSpreedCapability().getFeatures().contains(capabilityName);
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to get capabilities for the user");
            }
        }
        return false;
    }

    default boolean hasExternalCapability(String capabilityName) {
        if (getCapabilities() != null) {
            try {
                Capabilities capabilities = LoganSquare.parse(getCapabilities(), Capabilities.class);
                if (capabilities.getExternalCapability() != null && capabilities.getExternalCapability().containsKey("v1")) {
                    return capabilities.getExternalCapability().get("v1").contains("capabilityName");
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to get capabilities for the user");
            }
        }
        return false;
    }

    default boolean hasSpreedCapabilityWithName(String capabilityName) {
        if (getCapabilities() != null) {
            try {
                Capabilities capabilities = LoganSquare.parse(getCapabilities(), Capabilities.class);
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
}
