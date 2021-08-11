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
package com.moyn.talk.models.json.mention;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.moyn.talk.models.json.generic.GenericOCS;

import org.parceler.Parcel;

import java.util.List;

@Parcel
@JsonObject
public class MentionOCS extends GenericOCS {
    @JsonField(name = "data")
    List<Mention> data;

    public List<Mention> getData() {
        return this.data;
    }

    public void setData(List<Mention> data) {
        this.data = data;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof MentionOCS)) {
            return false;
        }
        final MentionOCS other = (MentionOCS) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$data = this.getData();
        final Object other$data = other.getData();

        return this$data == null ? other$data == null : this$data.equals(other$data);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof MentionOCS;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $data = this.getData();
        result = result * PRIME + ($data == null ? 43 : $data.hashCode());
        return result;
    }

    public String toString() {
        return "MentionOCS(data=" + this.getData() + ")";
    }
}
