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

import lombok.Data;

@Data
public class EventStatus {
    private long userId;
    private EventType eventType;
    private boolean allGood;

    public EventStatus(long userId, EventType eventType, boolean allGood) {
        this.userId = userId;
        this.eventType = eventType;
        this.allGood = allGood;
    }

    public enum EventType {
        PUSH_REGISTRATION, CAPABILITIES_FETCH, SIGNALING_SETTINGS, CONVERSATION_UPDATE
    }

}
