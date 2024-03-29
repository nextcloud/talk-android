/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
            case 6:
                return Participant.ParticipantType.GUEST_MODERATOR;
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
            case GUEST_MODERATOR:
                return 6;
            default:
                return 0;
        }
    }
}
