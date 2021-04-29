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

package com.nextcloud.talk.models.json.signaling;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

/**
 * Created by mdjanic on 30/10/2017.
 */

@JsonObject
public class Signaling {
    @JsonField(name = "type")
    String type;
    //can be NCMessageWrapper or List<HashMap<String,String>>
    @JsonField(name = "data")
    Object messageWrapper;

    public String getType() {
        return this.type;
    }

    public Object getMessageWrapper() {
        return this.messageWrapper;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setMessageWrapper(Object messageWrapper) {
        this.messageWrapper = messageWrapper;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Signaling)) {
            return false;
        }
        final Signaling other = (Signaling) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
            return false;
        }
        final Object this$messageWrapper = this.getMessageWrapper();
        final Object other$messageWrapper = other.getMessageWrapper();

        return this$messageWrapper == null ? other$messageWrapper == null : this$messageWrapper.equals(other$messageWrapper);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Signaling;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $messageWrapper = this.getMessageWrapper();
        result = result * PRIME + ($messageWrapper == null ? 43 : $messageWrapper.hashCode());
        return result;
    }

    public String toString() {
        return "Signaling(type=" + this.getType() + ", messageWrapper=" + this.getMessageWrapper() + ")";
    }
}
