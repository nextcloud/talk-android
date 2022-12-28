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

package com.nextcloud.talk.events;

public class EventStatus {
    private long userId;
    private EventType eventType;
    private boolean allGood;

    public EventStatus(long userId, EventType eventType, boolean allGood) {
        this.userId = userId;
        this.eventType = eventType;
        this.allGood = allGood;
    }

    public long getUserId() {
        return this.userId;
    }

    public EventType getEventType() {
        return this.eventType;
    }

    public boolean isAllGood() {
        return this.allGood;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public void setAllGood(boolean allGood) {
        this.allGood = allGood;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof EventStatus)) {
            return false;
        }
        final EventStatus other = (EventStatus) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        if (this.getUserId() != other.getUserId()) {
            return false;
        }
        final Object this$eventType = this.getEventType();
        final Object other$eventType = other.getEventType();
        if (this$eventType == null ? other$eventType != null : !this$eventType.equals(other$eventType)) {
            return false;
        }

        return this.isAllGood() == other.isAllGood();
    }

    protected boolean canEqual(final Object other) {
        return other instanceof EventStatus;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final long $userId = this.getUserId();
        result = result * PRIME + (int) ($userId >>> 32 ^ $userId);
        final Object $eventType = this.getEventType();
        result = result * PRIME + ($eventType == null ? 43 : $eventType.hashCode());
        return result * PRIME + (this.isAllGood() ? 79 : 97);
    }

    public String toString() {
        return "EventStatus(userId=" + this.getUserId() + ", eventType=" + this.getEventType() + ", allGood=" + this.isAllGood() + ")";
    }

    public enum EventType {
        PUSH_REGISTRATION, CAPABILITIES_FETCH, SIGNALING_SETTINGS, CONVERSATION_UPDATE, PARTICIPANTS_UPDATE
    }

}
