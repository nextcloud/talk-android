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

package com.nextcloud.talk.models.json.signaling.settings;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.models.json.generic.GenericOCS;

@JsonObject
public class SignalingSettingsOcs extends GenericOCS {
    @JsonField(name = "data")
    Settings settings;

    public Settings getSettings() {
        return this.settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof SignalingSettingsOcs)) {
            return false;
        }
        final SignalingSettingsOcs other = (SignalingSettingsOcs) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$settings = this.getSettings();
        final Object other$settings = other.getSettings();

        return this$settings == null ? other$settings == null : this$settings.equals(other$settings);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof SignalingSettingsOcs;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $settings = this.getSettings();
        result = result * PRIME + ($settings == null ? 43 : $settings.hashCode());
        return result;
    }

    public String toString() {
        return "SignalingSettingsOcs(settings=" + this.getSettings() + ")";
    }
}
