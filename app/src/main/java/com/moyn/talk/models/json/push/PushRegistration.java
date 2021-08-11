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

package com.moyn.talk.models.json.push;


import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class PushRegistration {
    @JsonField(name = "publicKey")
    String publicKey;

    @JsonField(name = "deviceIdentifier")
    String deviceIdentifier;

    @JsonField(name = "signature")
    String signature;

    public String getPublicKey() {
        return this.publicKey;
    }

    public String getDeviceIdentifier() {
        return this.deviceIdentifier;
    }

    public String getSignature() {
        return this.signature;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public void setDeviceIdentifier(String deviceIdentifier) {
        this.deviceIdentifier = deviceIdentifier;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof PushRegistration)) {
            return false;
        }
        final PushRegistration other = (PushRegistration) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$publicKey = this.getPublicKey();
        final Object other$publicKey = other.getPublicKey();
        if (this$publicKey == null ? other$publicKey != null : !this$publicKey.equals(other$publicKey)) {
            return false;
        }
        final Object this$deviceIdentifier = this.getDeviceIdentifier();
        final Object other$deviceIdentifier = other.getDeviceIdentifier();
        if (this$deviceIdentifier == null ? other$deviceIdentifier != null : !this$deviceIdentifier.equals(other$deviceIdentifier)) {
            return false;
        }
        final Object this$signature = this.getSignature();
        final Object other$signature = other.getSignature();

        return this$signature == null ? other$signature == null : this$signature.equals(other$signature);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof PushRegistration;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $publicKey = this.getPublicKey();
        result = result * PRIME + ($publicKey == null ? 43 : $publicKey.hashCode());
        final Object $deviceIdentifier = this.getDeviceIdentifier();
        result = result * PRIME + ($deviceIdentifier == null ? 43 : $deviceIdentifier.hashCode());
        final Object $signature = this.getSignature();
        result = result * PRIME + ($signature == null ? 43 : $signature.hashCode());
        return result;
    }

    public String toString() {
        return "PushRegistration(publicKey=" + this.getPublicKey() + ", deviceIdentifier=" + this.getDeviceIdentifier() + ", signature=" + this.getSignature() + ")";
    }
}

