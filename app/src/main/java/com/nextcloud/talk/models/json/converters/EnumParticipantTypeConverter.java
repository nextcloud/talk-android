/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.converters;

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter;
import com.nextcloud.talk.models.json.participants.ParticipantDto;

public class EnumParticipantTypeConverter extends IntBasedTypeConverter<ParticipantDto.ParticipantType> {
    @Override
    public ParticipantDto.ParticipantType getFromInt(int i) {
        switch (i) {
            case 1:
                return ParticipantDto.ParticipantType.OWNER;
            case 2:
                return ParticipantDto.ParticipantType.MODERATOR;
            case 3:
                return ParticipantDto.ParticipantType.USER;
            case 4:
                return ParticipantDto.ParticipantType.GUEST;
            case 5:
                return ParticipantDto.ParticipantType.USER_FOLLOWING_LINK;
            case 6:
                return ParticipantDto.ParticipantType.GUEST_MODERATOR;
            default:
                return ParticipantDto.ParticipantType.DUMMY;
        }
    }

    @Override
    public int convertToInt(ParticipantDto.ParticipantType object) {
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
            case GUEST_MODERATOR:
                return 6;
            default:
                return 0;
        }
    }
}
