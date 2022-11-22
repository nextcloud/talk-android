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

package com.nextcloud.talk.events;

public class ProximitySensorEvent {
    private final ProximitySensorEventType proximitySensorEventType;

    public ProximitySensorEvent(ProximitySensorEventType proximitySensorEventType) {
        this.proximitySensorEventType = proximitySensorEventType;
    }

    public ProximitySensorEventType getProximitySensorEventType() {
        return this.proximitySensorEventType;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProximitySensorEvent)) {
            return false;
        }
        final ProximitySensorEvent other = (ProximitySensorEvent) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$proximitySensorEventType = this.getProximitySensorEventType();
        final Object other$proximitySensorEventType = other.getProximitySensorEventType();
        if (this$proximitySensorEventType == null ? other$proximitySensorEventType != null : !this$proximitySensorEventType.equals(other$proximitySensorEventType)) {
            return false;
        }

        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ProximitySensorEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $proximitySensorEventType = this.getProximitySensorEventType();
        result = result * PRIME + ($proximitySensorEventType == null ? 43 : $proximitySensorEventType.hashCode());
        return result;
    }

    public String toString() {
        return "ProximitySensorEvent(proximitySensorEventType=" + this.getProximitySensorEventType() + ")";
    }

    public enum ProximitySensorEventType {
        SENSOR_FAR, SENSOR_NEAR
    }
}
