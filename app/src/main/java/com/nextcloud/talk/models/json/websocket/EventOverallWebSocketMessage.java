/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.models.json.websocket;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

import java.util.HashMap;

@Parcel
@JsonObject
public class EventOverallWebSocketMessage {
    @JsonField(name = "type")
    String type;
    @JsonField(name = "event")
    HashMap<String, Object> eventMap;

    public String getType() {
        return this.type;
    }

    public HashMap<String, Object> getEventMap() {
        return this.eventMap;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setEventMap(HashMap<String, Object> eventMap) {
        this.eventMap = eventMap;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof EventOverallWebSocketMessage)) {
            return false;
        }
        final EventOverallWebSocketMessage other = (EventOverallWebSocketMessage) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
            return false;
        }
        final Object this$eventMap = this.getEventMap();
        final Object other$eventMap = other.getEventMap();

        return this$eventMap == null ? other$eventMap == null : this$eventMap.equals(other$eventMap);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof EventOverallWebSocketMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $eventMap = this.getEventMap();
        result = result * PRIME + ($eventMap == null ? 43 : $eventMap.hashCode());
        return result;
    }

    public String toString() {
        return "EventOverallWebSocketMessage(type=" + this.getType() + ", eventMap=" + this.getEventMap() + ")";
    }
}
