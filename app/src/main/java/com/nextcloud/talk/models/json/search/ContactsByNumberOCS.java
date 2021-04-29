/*
 * Nextcloud Talk application
 *  
 * @author Tobias Kaminsky
 * Copyright (C) 2020 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
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

package com.nextcloud.talk.models.json.search;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.models.json.generic.GenericOCS;

import org.parceler.Parcel;

import java.util.HashMap;
import java.util.Map;

@Parcel
@JsonObject
public class ContactsByNumberOCS extends GenericOCS {
    @JsonField(name = "data")
    public Map<String, String> map = new HashMap();

    public Map<String, String> getMap() {
        return this.map;
    }

    public void setMap(Map<String, String> map) {
        this.map = map;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ContactsByNumberOCS)) {
            return false;
        }
        final ContactsByNumberOCS other = (ContactsByNumberOCS) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$map = this.getMap();
        final Object other$map = other.getMap();

        return this$map == null ? other$map == null : this$map.equals(other$map);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ContactsByNumberOCS;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $map = this.getMap();
        result = result * PRIME + ($map == null ? 43 : $map.hashCode());
        return result;
    }

    public String toString() {
        return "ContactsByNumberOCS(map=" + this.getMap() + ")";
    }
}
