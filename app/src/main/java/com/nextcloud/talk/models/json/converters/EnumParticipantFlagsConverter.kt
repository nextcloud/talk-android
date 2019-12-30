/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.models.json.converters

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter
import com.nextcloud.talk.models.json.participants.Participant

class EnumParticipantFlagsConverter : IntBasedTypeConverter<Participant.ParticipantFlags>() {
    override fun getFromInt(l: Int): Participant.ParticipantFlags {
        return Participant.ParticipantFlags.fromValue(l)
    }

    override fun convertToInt(`object`: Participant.ParticipantFlags?): Int {
        return `object`?.value ?: 0
    }
}
