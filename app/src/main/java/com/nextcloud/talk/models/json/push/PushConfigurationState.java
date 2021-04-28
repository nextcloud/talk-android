/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.models.json.push;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class PushConfigurationState {
    @JsonField(name = "pushToken")
    public String pushToken;

    @JsonField(name = "deviceIdentifier")
    public String deviceIdentifier;

    @JsonField(name = "deviceIdentifierSignature")
    public String deviceIdentifierSignature;

    @JsonField(name = "userPublicKey")
    public String userPublicKey;

    @JsonField(name = "usesRegularPass")
    public boolean usesRegularPass;

    public PushConfigurationState() {
    }

    public String getPushToken() {
        return this.pushToken;
    }

    public String getDeviceIdentifier() {
        return this.deviceIdentifier;
    }

    public String getDeviceIdentifierSignature() {
        return this.deviceIdentifierSignature;
    }

    public String getUserPublicKey() {
        return this.userPublicKey;
    }

    public boolean isUsesRegularPass() {
        return this.usesRegularPass;
    }

    public void setPushToken(String pushToken) {
        this.pushToken = pushToken;
    }

    public void setDeviceIdentifier(String deviceIdentifier) {
        this.deviceIdentifier = deviceIdentifier;
    }

    public void setDeviceIdentifierSignature(String deviceIdentifierSignature) {
        this.deviceIdentifierSignature = deviceIdentifierSignature;
    }

    public void setUserPublicKey(String userPublicKey) {
        this.userPublicKey = userPublicKey;
    }

    public void setUsesRegularPass(boolean usesRegularPass) {
        this.usesRegularPass = usesRegularPass;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PushConfigurationState)) {
            return false;
        }
        final PushConfigurationState other = (PushConfigurationState) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$pushToken = this.getPushToken();
        final Object other$pushToken = other.getPushToken();
        if (this$pushToken == null ? other$pushToken != null : !this$pushToken.equals(other$pushToken)) {
            return false;
        }
        final Object this$deviceIdentifier = this.getDeviceIdentifier();
        final Object other$deviceIdentifier = other.getDeviceIdentifier();
        if (this$deviceIdentifier == null ? other$deviceIdentifier != null : !this$deviceIdentifier.equals(other$deviceIdentifier)) {
            return false;
        }
        final Object this$deviceIdentifierSignature = this.getDeviceIdentifierSignature();
        final Object other$deviceIdentifierSignature = other.getDeviceIdentifierSignature();
        if (this$deviceIdentifierSignature == null ? other$deviceIdentifierSignature != null : !this$deviceIdentifierSignature.equals(other$deviceIdentifierSignature)) {
            return false;
        }
        final Object this$userPublicKey = this.getUserPublicKey();
        final Object other$userPublicKey = other.getUserPublicKey();
        if (this$userPublicKey == null ? other$userPublicKey != null : !this$userPublicKey.equals(other$userPublicKey)) {
            return false;
        }
        if (this.isUsesRegularPass() != other.isUsesRegularPass()) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof PushConfigurationState;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $pushToken = this.getPushToken();
        result = result * PRIME + ($pushToken == null ? 43 : $pushToken.hashCode());
        final Object $deviceIdentifier = this.getDeviceIdentifier();
        result = result * PRIME + ($deviceIdentifier == null ? 43 : $deviceIdentifier.hashCode());
        final Object $deviceIdentifierSignature = this.getDeviceIdentifierSignature();
        result = result * PRIME + ($deviceIdentifierSignature == null ? 43 : $deviceIdentifierSignature.hashCode());
        final Object $userPublicKey = this.getUserPublicKey();
        result = result * PRIME + ($userPublicKey == null ? 43 : $userPublicKey.hashCode());
        result = result * PRIME + (this.isUsesRegularPass() ? 79 : 97);
        return result;
    }

    public String toString() {
        return "PushConfigurationState(pushToken=" + this.getPushToken() + ", deviceIdentifier=" + this.getDeviceIdentifier() + ", deviceIdentifierSignature=" + this.getDeviceIdentifierSignature() + ", userPublicKey=" + this.getUserPublicKey() + ", usesRegularPass=" + this.isUsesRegularPass() + ")";
    }
}
