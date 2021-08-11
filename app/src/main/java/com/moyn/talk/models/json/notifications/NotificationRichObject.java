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

package com.moyn.talk.models.json.notifications;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class NotificationRichObject {
    @JsonField(name = "id")
    String label;

    @JsonField(name = "type")
    String type;

    @JsonField(name = "name")
    String name;

    public String getLabel() {
        return this.label;
    }

    public String getType() {
        return this.type;
    }

    public String getName() {
        return this.name;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof NotificationRichObject)) {
            return false;
        }
        final NotificationRichObject other = (NotificationRichObject) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$label = this.getLabel();
        final Object other$label = other.getLabel();
        if (this$label == null ? other$label != null : !this$label.equals(other$label)) {
            return false;
        }
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
            return false;
        }
        final Object this$name = this.getName();
        final Object other$name = other.getName();

        return this$name == null ? other$name == null : this$name.equals(other$name);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof NotificationRichObject;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $label = this.getLabel();
        result = result * PRIME + ($label == null ? 43 : $label.hashCode());
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $name = this.getName();
        return result * PRIME + ($name == null ? 43 : $name.hashCode());
    }

    public String toString() {
        return "NotificationRichObject(label=" + this.getLabel() + ", type=" + this.getType() + ", name=" + this.getName() + ")";
    }
}
