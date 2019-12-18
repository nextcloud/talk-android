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
import java.util.Objects;

import lombok.Data;

@Parcel
@Data
@JsonObject
public class SpreedCapability {
    @JsonField(name = "features")
    public List<String> features;

    @JsonField(name = "config")
    public HashMap<String, HashMap<String, String>> config;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpreedCapability)) return false;
        SpreedCapability that = (SpreedCapability) o;
        return Objects.equals(features, that.features) &&
                Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(features, config);
    }
}
