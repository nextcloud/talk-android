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
public class SpreedCapability {
    @JsonField(name = "features")
    List<String> features;

    @JsonField(name = "config")
    HashMap<String, HashMap<String, String>> config;

    public List<String> getFeatures() {
        return this.features;
    }

    public HashMap<String, HashMap<String, String>> getConfig() {
        return this.config;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }

    public void setConfig(HashMap<String, HashMap<String, String>> config) {
        this.config = config;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SpreedCapability)) {
            return false;
        }
        final SpreedCapability other = (SpreedCapability) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$features = this.getFeatures();
        final Object other$features = other.getFeatures();
        if (this$features == null ? other$features != null : !this$features.equals(other$features)) {
            return false;
        }
        final Object this$config = this.getConfig();
        final Object other$config = other.getConfig();

        return this$config == null ? other$config == null : this$config.equals(other$config);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof SpreedCapability;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $features = this.getFeatures();
        result = result * PRIME + ($features == null ? 43 : $features.hashCode());
        final Object $config = this.getConfig();
        result = result * PRIME + ($config == null ? 43 : $config.hashCode());
        return result;
    }

    public String toString() {
        return "SpreedCapability(features=" + this.getFeatures() + ", config=" + this.getConfig() + ")";
    }
}
