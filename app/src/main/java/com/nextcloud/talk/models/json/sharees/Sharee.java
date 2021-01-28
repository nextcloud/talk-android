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
package com.nextcloud.talk.models.json.sharees;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class Sharee {
    @JsonField(name = "id")
    String id;

    @JsonField(name = "value")
    Value value;

    @JsonField(name = "label")
    String label;

    public Sharee() {
    }

    public String getId() {
        return this.id;
    }

    public Value getValue() {
        return this.value;
    }

    public String getLabel() {
        return this.label;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setValue(Value value) {
        this.value = value;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Sharee)) return false;
        final Sharee other = (Sharee) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$id = this.getId();
        final Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        final Object this$value = this.getValue();
        final Object other$value = other.getValue();
        if (this$value == null ? other$value != null : !this$value.equals(other$value)) return false;
        final Object this$label = this.getLabel();
        final Object other$label = other.getLabel();
        if (this$label == null ? other$label != null : !this$label.equals(other$label)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Sharee;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final Object $value = this.getValue();
        result = result * PRIME + ($value == null ? 43 : $value.hashCode());
        final Object $label = this.getLabel();
        result = result * PRIME + ($label == null ? 43 : $label.hashCode());
        return result;
    }

    public String toString() {
        return "Sharee(id=" + this.getId() + ", value=" + this.getValue() + ", label=" + this.getLabel() + ")";
    }
}
