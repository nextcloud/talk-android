/*
 *
 *   Nextcloud Talk application
 *
 *   @author Tobias Kaminsky
 *   Copyright (C) 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
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
package com.moyn.talk.models.json.userprofile;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.moyn.talk.models.json.generic.GenericOCS;

import org.parceler.Parcel;

import java.util.ArrayList;

@Parcel
@JsonObject
public class UserProfileFieldsOCS extends GenericOCS {
    @JsonField(name = "data")
    ArrayList<String> data;

    public ArrayList<String> getData() {
        return this.data;
    }

    public void setData(ArrayList<String> data) {
        this.data = data;
    }

    public String toString() {
        return "UserProfileFieldsOCS(data=" + this.getData() + ")";
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof UserProfileFieldsOCS)) {
            return false;
        }
        final UserProfileFieldsOCS other = (UserProfileFieldsOCS) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final Object this$data = this.getData();
        final Object other$data = other.getData();

        return this$data == null ? other$data == null : this$data.equals(other$data);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof UserProfileFieldsOCS;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = super.hashCode();
        final Object $data = this.getData();
        result = result * PRIME + ($data == null ? 43 : $data.hashCode());
        return result;
    }
}
