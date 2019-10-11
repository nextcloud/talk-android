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

package com.nextcloud.talk.models.json.converters;

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter;
import com.nextcloud.talk.models.json.participants.Participant;

public class EnumParticipantTypeConverter extends IntBasedTypeConverter<Participant.ParticipantType> {
    @Override
    public Participant.ParticipantType getFromInt(int i) {
        switch (i) {
            case 1:
                return Participant.ParticipantType.OWNER;
            case 2:
                return Participant.ParticipantType.MODERATOR;
            case 3:
                return Participant.ParticipantType.USER;
            case 4:
                return Participant.ParticipantType.GUEST;
            case 5:
                return Participant.ParticipantType.USER_FOLLOWING_LINK;
            default:
                return Participant.ParticipantType.DUMMY;
        }

    }

    @Override
    public int convertToInt(Participant.ParticipantType object) {
        switch (object) {
            case DUMMY:
                return 0;
            case OWNER:
                return 1;
            case MODERATOR:
                return 2;
            case USER:
                return 3;
            case GUEST:
                return 4;
            case USER_FOLLOWING_LINK:
                return 5;
            default:
                return 0;
        }
    }
}
