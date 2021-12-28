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

package com.nextcloud.talk.models.json.capabilities;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

import java.util.HashMap;
import java.util.List;

@Parcel
@JsonObject
public class Capabilities {
    @JsonField(name = "spreed")
    SpreedCapability spreedCapability;

    @JsonField(name = "notifications")
    NotificationsCapability notificationsCapability;

    @JsonField(name = "theming")
    ThemingCapability themingCapability;

    @JsonField(name = "external")
    HashMap<String, List<String>> externalCapability;

    @JsonField(name = "provisioning_api")
    ProvisioningCapability provisioningCapability;

    @JsonField(name = "user_status")
    UserStatusCapability userStatusCapability;

    public SpreedCapability getSpreedCapability() {
        return this.spreedCapability;
    }

    public NotificationsCapability getNotificationsCapability() {
        return this.notificationsCapability;
    }

    public ThemingCapability getThemingCapability() {
        return this.themingCapability;
    }

    public HashMap<String, List<String>> getExternalCapability() {
        return this.externalCapability;
    }

    public ProvisioningCapability getProvisioningCapability() {
        return this.provisioningCapability;
    }

    public UserStatusCapability getUserStatusCapability() {
        return userStatusCapability;
    }

    public void setSpreedCapability(SpreedCapability spreedCapability) {
        this.spreedCapability = spreedCapability;
    }

    public void setNotificationsCapability(NotificationsCapability notificationsCapability) {
        this.notificationsCapability = notificationsCapability;
    }

    public void setThemingCapability(ThemingCapability themingCapability) {
        this.themingCapability = themingCapability;
    }

    public void setExternalCapability(HashMap<String, List<String>> externalCapability) {
        this.externalCapability = externalCapability;
    }

    public void setProvisioningCapability(ProvisioningCapability provisioningCapability) {
        this.provisioningCapability = provisioningCapability;
    }

    public void setUserStatusCapability(UserStatusCapability userStatusCapability) {
        this.userStatusCapability = userStatusCapability;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Capabilities)) {
            return false;
        }
        final Capabilities other = (Capabilities) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$spreedCapability = this.getSpreedCapability();
        final Object other$spreedCapability = other.getSpreedCapability();
        if (this$spreedCapability == null ? other$spreedCapability != null : !this$spreedCapability.equals(other$spreedCapability)) {
            return false;
        }
        final Object this$notificationsCapability = this.getNotificationsCapability();
        final Object other$notificationsCapability = other.getNotificationsCapability();
        if (this$notificationsCapability == null ? other$notificationsCapability != null : !this$notificationsCapability.equals(other$notificationsCapability)) {
            return false;
        }
        final Object this$themingCapability = this.getThemingCapability();
        final Object other$themingCapability = other.getThemingCapability();
        if (this$themingCapability == null ? other$themingCapability != null : !this$themingCapability.equals(other$themingCapability)) {
            return false;
        }
        final Object this$externalCapability = this.getExternalCapability();
        final Object other$externalCapability = other.getExternalCapability();
        if (this$externalCapability == null ? other$externalCapability != null : !this$externalCapability.equals(other$externalCapability)) {
            return false;
        }
        final Object this$provisioningCapability = this.getProvisioningCapability();
        final Object other$provisioningCapability = other.getProvisioningCapability();

        return this$provisioningCapability == null ? other$provisioningCapability == null : this$provisioningCapability.equals(other$provisioningCapability);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Capabilities;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $spreedCapability = this.getSpreedCapability();
        result = result * PRIME + ($spreedCapability == null ? 43 : $spreedCapability.hashCode());
        final Object $notificationsCapability = this.getNotificationsCapability();
        result = result * PRIME + ($notificationsCapability == null ? 43 : $notificationsCapability.hashCode());
        final Object $themingCapability = this.getThemingCapability();
        result = result * PRIME + ($themingCapability == null ? 43 : $themingCapability.hashCode());
        final Object $externalCapability = this.getExternalCapability();
        result = result * PRIME + ($externalCapability == null ? 43 : $externalCapability.hashCode());
        final Object $provisioningCapability = this.getProvisioningCapability();
        result = result * PRIME + ($provisioningCapability == null ? 43 : $provisioningCapability.hashCode());
        return result;
    }

    public String toString() {
        return "Capabilities(spreedCapability=" + this.getSpreedCapability() + ", notificationsCapability=" + this.getNotificationsCapability() + ", themingCapability=" + this.getThemingCapability() + ", externalCapability=" + this.getExternalCapability() + ", provisioningCapability=" + this.getProvisioningCapability() + ")";
    }
}
