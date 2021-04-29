/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.models.json.generic;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class GenericOCS {
    @JsonField(name = "meta")
    public GenericMeta meta;

    public GenericMeta getMeta() {
        return this.meta;
    }

    public void setMeta(GenericMeta meta) {
        this.meta = meta;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof GenericOCS)) {
            return false;
        }
        final GenericOCS other = (GenericOCS) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$meta = this.getMeta();
        final Object other$meta = other.getMeta();

        return this$meta == null ? other$meta == null : this$meta.equals(other$meta);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof GenericOCS;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $meta = this.getMeta();
        result = result * PRIME + ($meta == null ? 43 : $meta.hashCode());
        return result;
    }

    public String toString() {
        return "GenericOCS(meta=" + this.getMeta() + ")";
    }
}
