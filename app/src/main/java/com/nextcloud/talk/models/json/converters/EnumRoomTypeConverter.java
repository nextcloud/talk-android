/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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

package com.nextcloud.talk.models.json.converters;

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter;
import com.nextcloud.talk.models.json.rooms.Room;

public class EnumRoomTypeConverter extends IntBasedTypeConverter<Room.RoomType> {
    @Override
    public Room.RoomType getFromInt(int i) {
        switch (i) {
            case 1:
                return Room.RoomType.ROOM_TYPE_ONE_TO_ONE_CALL;
            case 2:
                return Room.RoomType.ROOM_GROUP_CALL;
            case 3:
                return Room.RoomType.ROOM_PUBLIC_CALL;
            default:
                return Room.RoomType.DUMMY;
        }
    }

    @Override
    public int convertToInt(Room.RoomType object) {
        switch (object) {
            case DUMMY:
                return 0;
            case ROOM_TYPE_ONE_TO_ONE_CALL:
                return 1;
            case ROOM_GROUP_CALL:
                return 2;
            case ROOM_PUBLIC_CALL:
                return 3;
            default:
                return 0;
        }
    }
}
